package org.dvbviewer.controller.data.epg

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.dvbviewer.controller.data.entities.EpgEntry
import org.dvbviewer.controller.data.xmltv.XmltvRepository
import java.util.Date

class ChannelEpgViewModel(
    private val repository: EPGRepository,
    private val xmltvRepository: XmltvRepository? = null
) : ViewModel() {

    private val data: MutableLiveData<List<EpgEntry>> = MutableLiveData()

    fun getChannelEPG(
        channel: Long,
        channelName: String,
        start: Date,
        end: Date,
        force: Boolean = false
    ): MutableLiveData<List<EpgEntry>> {
        Log.d(TAG, "getChannelEPG(epgId=$channel, name=\"$channelName\", " +
                "start=$start, end=$end, force=$force)")
        Log.d(TAG, "  xmltvRepository=${if (xmltvRepository != null) "present" else "null (XMLTV disabled)"}")
        if (data.value == null || force) {
            fetchChannelEPG(channel, channelName, start, end)
        } else {
            Log.d(TAG, "  returning cached ${data.value?.size} entries (force=false)")
        }
        return data
    }

    private fun fetchChannelEPG(channel: Long, channelName: String, start: Date, end: Date) {
        viewModelScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) {
            val result: MutableList<EpgEntry> = mutableListOf()

            async(Dispatchers.IO) {
                // ── 1. Primary: DVBViewer Recording Service ──────────────────
                Log.d(TAG, "  [1] Fetching from DVBViewer (epgId=$channel) …")
                try {
                    val dvbEntries = repository.getChannelEPG(channel, start, end)
                    Log.d(TAG, "  [1] DVBViewer returned ${dvbEntries?.size ?: "null"} entries")
                    dvbEntries?.let { result.addAll(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "  [1] DVBViewer fetch failed for \"$channelName\"", e)
                }

                // ── 2. Fallback: XMLTV SQLite cache ──────────────────────────
                val useXmltvFallback = result.isEmpty() && xmltvRepository != null
                Log.d(TAG, "  [2] XMLTV fallback needed=$useXmltvFallback " +
                        "(dvb_empty=${result.isEmpty()}, xmltv_repo_available=${xmltvRepository != null})")

                if (useXmltvFallback) {
                    try {
                        val xmltvEntries = xmltvRepository!!.getEpgForChannel(channelName, start, end)
                        Log.d(TAG, "  [2] XMLTV returned ${xmltvEntries.size} entries for \"$channelName\"")
                        if (xmltvEntries.isNotEmpty()) {
                            result.addAll(xmltvEntries)
                        } else {
                            Log.w(TAG, "  [2] XMLTV cache also empty for \"$channelName\" — " +
                                    "check that the worker downloaded data and channel names match")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "  [2] XMLTV fallback threw for \"$channelName\"", e)
                    }
                }
            }.await()

            Log.d(TAG, "fetchChannelEPG() done — posting ${result.size} entries to LiveData")
            data.value = result
        }
    }

    companion object {
        private const val TAG = "ChannelEpgViewModel"
    }
}
