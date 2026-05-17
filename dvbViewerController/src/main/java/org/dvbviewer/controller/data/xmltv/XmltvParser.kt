package org.dvbviewer.controller.data.xmltv

import android.util.Log
import android.util.Xml
import org.dvbviewer.controller.data.entities.EpgEntry
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SAX parser for the standard XMLTV format.
 *
 * Bug-fix: uses `localName.ifEmpty { qName }` because Android's SAX
 * implementation may deliver an empty localName when the XML document
 * has no namespace declarations (which is the norm for XMLTV).
 */
class XmltvParser : DefaultHandler() {

    private val channelMap = mutableMapOf<String, String>()
    private val result     = mutableMapOf<String, MutableList<EpgEntry>>()

    private var currentChannelId:   String?        = null
    private var currentChannelName: StringBuilder? = null
    private var insideDisplayName = false

    private var currentProgramme: EpgEntry?       = null
    private var currentText:      StringBuilder?  = null
    private var insideTitle      = false
    private var insideSubTitle   = false
    private var insideDesc       = false
    private var insideEpisodeNum = false   // only true for system="onscreen"

    // XMLTV date: "20240101120000 +0000" or "20240101120000 +0200" etc.
    private val dateFormatTz   = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val dateFormatNoTz = SimpleDateFormat("yyyyMMddHHmmss",   Locale.US)

    // ------------------------------------------------------------------ SAX callbacks

    override fun startDocument() {
        Log.d(TAG, "startDocument — beginning XMLTV parse")
    }

    override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
        // FIX: localName is empty when Android SAX runs without namespace processing.
        // qName is always populated regardless of namespace mode.
        val tag = localName.ifEmpty { qName }.lowercase()

        when (tag) {
            "channel" -> {
                val id = attrs.getValue("id")
                Log.d(TAG, "  <channel id=\"$id\">")
                if (id == null) { Log.w(TAG, "  <channel> missing id attr — skipped"); return }
                currentChannelId   = id
                currentChannelName = StringBuilder()
            }
            "display-name" -> {
                if (currentChannelId != null) {
                    currentChannelName = StringBuilder()
                    insideDisplayName  = true
                }
            }
            "programme" -> {
                val channelId = attrs.getValue("channel")
                val startStr  = attrs.getValue("start")
                val stopStr   = attrs.getValue("stop")
                if (channelId == null || startStr == null || stopStr == null) {
                    Log.w(TAG, "  <programme> missing required attrs — skipped")
                    return
                }
                val entry = EpgEntry()
                entry.start   = parseDate(startStr)
                entry.end     = parseDate(stopStr)
                entry.channel = channelId          // temporarily store id; resolved in endElement
                currentProgramme = entry
                currentText      = StringBuilder()
            }
            "title"       -> if (currentProgramme != null) { currentText = StringBuilder(); insideTitle      = true }
            "sub-title"   -> if (currentProgramme != null) { currentText = StringBuilder(); insideSubTitle   = true }
            "desc"        -> if (currentProgramme != null) { currentText = StringBuilder(); insideDesc       = true }
            "episode-num" -> if (currentProgramme != null && attrs.getValue("system") == "onscreen") {
                currentText = StringBuilder(); insideEpisodeNum = true
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val text = String(ch, start, length)
        when {
            insideDisplayName -> currentChannelName?.append(text)
            insideTitle       -> currentText?.append(text)
            insideSubTitle    -> currentText?.append(text)
            insideDesc        -> currentText?.append(text)
            insideEpisodeNum  -> currentText?.append(text)
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        val tag = localName.ifEmpty { qName }.lowercase()

        when (tag) {
            "channel" -> {
                val id   = currentChannelId                    ?: return
                val name = currentChannelName?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    channelMap[id] = name
                    Log.d(TAG, "  Channel mapped: \"$id\" → \"$name\"")
                } else {
                    Log.w(TAG, "  Channel \"$id\" has no display-name — skipped")
                }
                currentChannelId   = null
                currentChannelName = null
            }
            "display-name" -> {
                insideDisplayName = false
            }
            "programme" -> {
                val prog         = currentProgramme ?: return
                val rawChannelId = prog.channel
                val channelName  = channelMap[rawChannelId]
                if (channelName == null) {
                    Log.w(TAG, "  No display-name for channel id \"$rawChannelId\" — using id as name")
                }
                prog.channel = channelName ?: rawChannelId
                result.getOrPut(prog.channel) { mutableListOf() }.add(prog)
                currentProgramme = null
            }
            "title" -> {
                currentProgramme?.title = currentText?.toString()?.trim() ?: ""
                insideTitle = false
            }
            "sub-title" -> {
                currentProgramme?.subTitle = currentText?.toString()?.trim() ?: ""
                insideSubTitle = false
            }
            "desc" -> {
                currentProgramme?.description = currentText?.toString()?.trim() ?: ""
                insideDesc = false
            }
            "episode-num" -> {
                if (insideEpisodeNum) {
                    currentProgramme?.episodeNum = currentText?.toString()?.trim() ?: ""
                    insideEpisodeNum = false
                }
            }
        }
    }

    override fun endDocument() {
        val totalEntries = result.values.sumOf { it.size }
        Log.i(TAG, "━━━ XMLTV parse complete ━━━")
        Log.i(TAG, "Channels in <channel> map : ${channelMap.size}")
        Log.i(TAG, "Channels with programmes  : ${result.size}")
        Log.i(TAG, "Total programme entries   : $totalEntries")

        if (channelMap.isEmpty()) {
            Log.w(TAG, "WARNING: no <channel> elements parsed — is the XMLTV file valid?")
        }
        if (result.isEmpty()) {
            Log.w(TAG, "WARNING: no <programme> elements parsed")
        }

        // ── Numbered list of all XMLTV channel names (for comparison with DVBViewer) ──
        Log.i(TAG, "━━━ ALL XMLTV channel names (search for mismatches with DVBViewer) ━━━")
        result.entries
            .sortedBy { it.key }
            .forEachIndexed { index, (name, entries) ->
                val hex = name.map { "U+%04X".format(it.code) }.joinToString(" ")
                Log.i(TAG, "  [${index + 1}] \"$name\"  len=${name.length}  entries=${entries.size}")
                Log.d(TAG, "      hex: $hex")
            }
        Log.i(TAG, "━━━ end of channel list ━━━")
    }

    fun getResult(): Map<String, List<EpgEntry>> = result

    // ------------------------------------------------------------------ helpers

    private fun parseDate(raw: String): Date {
        val s = raw.trim()
        return try {
            dateFormatTz.parse(s) ?: Date().also {
                Log.w(TAG, "dateFormatTz.parse returned null for: \"$s\"")
            }
        } catch (e1: Exception) {
            try {
                dateFormatNoTz.parse(s.take(14)) ?: Date().also {
                    Log.w(TAG, "dateFormatNoTz.parse returned null for: \"${s.take(14)}\"")
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Cannot parse XMLTV date: \"$raw\" — using Date()")
                Date()
            }
        }
    }

    // ------------------------------------------------------------------ companion

    companion object {
        const val TAG = "XmltvParser"

        /**
         * Parses the XMLTV [inputStream] and returns channelName → entries map.
         * Logs detailed diagnostics at each step.
         */
        fun parse(inputStream: InputStream): Map<String, List<EpgEntry>> {
            Log.d(TAG, "parse() starting SAX parsing")
            val handler = XmltvParser()
            try {
                Xml.parse(inputStream, Xml.Encoding.UTF_8, handler)
            } catch (e: Exception) {
                Log.e(TAG, "SAX parse threw exception — partial results may be available", e)
            }
            return handler.getResult()
        }
    }
}
