package org.dvbviewer.controller.data.xmltv

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.dvbviewer.controller.data.entities.DVBViewerPreferences

/**
 * Resolves DVBViewer channel names to XMLTV channel names using three strategies
 * in priority order:
 *
 *  1. **Manual mapping** — user-configured pairs stored in SharedPreferences as JSON.
 *  2. **Exact match** — case-insensitive string equality.
 *  3. **Fuzzy match** — Jaccard word-set similarity ≥ [FUZZY_THRESHOLD].
 *     e.g. "Two and A Half Men" vs "Two Men and A Half" → same word set → 100%.
 */
class XmltvChannelMapper(context: Context) {

    private val prefs = context.getSharedPreferences(DVBViewerPreferences.PREFS, Context.MODE_PRIVATE)
    private val gson  = Gson()

    enum class MatchType { MANUAL, EXACT, FUZZY }

    data class ResolveResult(
        val xmltvName: String,
        val type: MatchType,
        val score: Double = 1.0
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Finds the best XMLTV name for [dvbViewerName] among [availableXmltvNames].
     * Returns null when no match exceeds the quality threshold.
     */
    fun resolve(dvbViewerName: String, availableXmltvNames: List<String>): ResolveResult? {
        if (availableXmltvNames.isEmpty()) {
            Log.d(TAG, "resolve(\"$dvbViewerName\") — no XMLTV names available in SQLite")
            return null
        }

        // 1. Manual mapping (highest priority)
        val mapping = loadMapping()
        mapping[dvbViewerName]?.let { configured ->
            val found = availableXmltvNames.find { it.equals(configured, ignoreCase = true) }
            if (found != null) {
                Log.d(TAG, "MANUAL  \"$dvbViewerName\" → \"$found\"")
                return ResolveResult(found, MatchType.MANUAL)
            }
            Log.w(TAG, "MANUAL mapping \"$dvbViewerName\"→\"$configured\" exists but " +
                    "\"$configured\" not in XMLTV data — falling through to auto-match")
        }

        // 2. Exact match (case-insensitive)
        availableXmltvNames.find { it.equals(dvbViewerName, ignoreCase = true) }?.let {
            Log.d(TAG, "EXACT   \"$dvbViewerName\" → \"$it\"")
            return ResolveResult(it, MatchType.EXACT)
        }

        // 3. Fuzzy word-set match (Jaccard)
        var bestScore = 0.0
        var bestName: String? = null
        for (name in availableXmltvNames) {
            val s = wordSetSimilarity(dvbViewerName, name)
            if (s > bestScore) { bestScore = s; bestName = name }
        }
        if (bestScore >= FUZZY_THRESHOLD && bestName != null) {
            Log.d(TAG, "FUZZY   \"$dvbViewerName\" → \"$bestName\" " +
                    "(${"%.0f".format(bestScore * 100)}% word-set Jaccard)")
            return ResolveResult(bestName, MatchType.FUZZY, bestScore)
        }

        Log.d(TAG, "NO MATCH for \"$dvbViewerName\" " +
                "(best fuzzy=${"%.0f".format(bestScore * 100)}% < ${(FUZZY_THRESHOLD * 100).toInt()}%)")
        return null
    }

    // ── Mapping persistence ───────────────────────────────────────────────────

    fun loadMapping(): Map<String, String> {
        val json = prefs.getString(DVBViewerPreferences.KEY_XMLTV_CHANNEL_MAPPING, "{}") ?: "{}"
        return try {
            gson.fromJson<Map<String, String>>(
                json, object : TypeToken<Map<String, String>>() {}.type
            ) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot parse channel mapping JSON", e)
            emptyMap()
        }
    }

    fun saveMapping(mapping: Map<String, String>) {
        prefs.edit()
            .putString(DVBViewerPreferences.KEY_XMLTV_CHANNEL_MAPPING, gson.toJson(mapping))
            .apply()
        Log.d(TAG, "Saved ${mapping.size} channel mapping(s)")
    }

    // ── Fuzzy logic ───────────────────────────────────────────────────────────

    /**
     * Jaccard similarity on word sets: |A ∩ B| / |A ∪ B|.
     * Words shorter than 2 characters are ignored to reduce noise.
     */
    private fun wordSetSimilarity(a: String, b: String): Double {
        val setA = tokenize(a)
        val setB = tokenize(b)
        if (setA.isEmpty() && setB.isEmpty()) return 1.0
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val intersection = setA.intersect(setB).size.toDouble()
        val union        = (setA + setB).toSet().size.toDouble()
        return intersection / union
    }

    /** Lowercase, split on whitespace/punctuation, discard 1-char tokens. */
    private fun tokenize(s: String): Set<String> =
        s.lowercase()
            .split(Regex("[\\s\\-_./\\\\,;:!?|()'\"&+]+"))
            .filter { it.length > 1 }
            .toSet()

    companion object {
        private const val TAG = "XmltvChannelMapper"
        const val FUZZY_THRESHOLD = 0.8   // 80%
    }
}
