package org.dvbviewer.controller.data.xmltv

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.dvbviewer.controller.data.DbHelper
import org.dvbviewer.controller.data.api.io.SSLUtil
import org.dvbviewer.controller.data.entities.EpgEntry
import java.util.Date
import java.util.concurrent.TimeUnit

class XmltvRepository(private val context: Context) {

    private val dbHelper: DbHelper       by lazy { DbHelper(context) }
    private val channelMapper: XmltvChannelMapper by lazy { XmltvChannelMapper(context) }

    // ── Download + persist ────────────────────────────────────────────────────

    fun downloadAndParse(url: String) {
        Log.d(TAG, "downloadAndParse() URL=\"$url\"")
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            Log.d(TAG, "HTTP ${response.code} from ${response.request.url}")
            if (!response.isSuccessful)
                throw IllegalStateException("XMLTV download failed: HTTP ${response.code} — $url")
            val body = response.body
                ?: throw IllegalStateException("Empty XMLTV response body from $url")
            val entriesByChannel = XmltvParser.parse(body.byteStream())
            val total = entriesByChannel.values.sumOf { it.size }
            if (entriesByChannel.isEmpty()) {
                Log.w(TAG, "Parser returned 0 channels — nothing saved")
                return@use
            }
            dbHelper.replaceAllXmltvEntries(entriesByChannel)
            Log.i(TAG, "Saved ${entriesByChannel.size} channels, $total entries to xmltv_epg")
        }
    }

    // ── Query with matching ───────────────────────────────────────────────────

    /**
     * Returns EPG entries for [channelName] in the given time window.
     *
     * Resolution order:
     *  1. Manual mapping  (user-configured in Settings → Channel Name Mapping)
     *  2. Exact match     (case-insensitive)
     *  3. Fuzzy match     (word-set Jaccard ≥ 80%)
     */
    fun getEpgForChannel(channelName: String, start: Long, end: Long): List<EpgEntry> {
        Log.d(TAG, "getEpgForChannel(\"$channelName\")")

        val available = dbHelper.getAllXmltvChannelNames()
        val resolved  = channelMapper.resolve(channelName, available)

        if (resolved == null) {
            if (available.isEmpty()) {
                Log.w(TAG, "  XMLTV table empty — worker has not run yet")
            } else {
                Log.w(TAG, "  No match for \"$channelName\". " +
                        "First 5 available: ${available.take(5).joinToString { "\"$it\"" }}")
                Log.w(TAG, "  Tip: add a manual mapping in Settings → External EPG → Channel Name Mapping")
            }
            return emptyList()
        }

        val entries = dbHelper.getXmltvEntries(resolved.xmltvName, start, end)
        Log.d(TAG, "  [${resolved.type}] \"$channelName\" → \"${resolved.xmltvName}\" — ${entries.size} entries")
        return entries
    }

    fun getEpgForChannel(channelName: String, start: Date, end: Date): List<EpgEntry> =
        getEpgForChannel(channelName, start.time, end.time)

    // ── Mapping delegation (used by XmltvMappingActivity) ────────────────────

    fun loadChannelMapping(): Map<String, String>          = channelMapper.loadMapping()
    fun saveChannelMapping(mapping: Map<String, String>)   = channelMapper.saveMapping(mapping)

    companion object {
        private const val TAG = "XmltvRepository"

        /**
         * Dedicated OkHttpClient — NO DMSInterceptor.
         * DMSInterceptor rewrites every URL to ServerConsts.REC_SERVICE_HOST (DVBViewer),
         * which would redirect XMLTV downloads to the wrong server.
         */
        private val httpClient: OkHttpClient by lazy {
            val tm = SSLUtil.trustAllTrustManager
            OkHttpClient.Builder()
                .sslSocketFactory(SSLUtil.getSSLServerSocketFactory(tm), tm)
                .hostnameVerifier(SSLUtil.VerifyAllHostnameVerifiyer())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
