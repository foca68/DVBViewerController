package org.dvbviewer.controller.data.xmltv

import java.util.Date

/**
 * Represents a single programme entry from a standard XMLTV file.
 *
 * @param channelId    Raw channel id attribute from <programme channel="...">.
 * @param channelName  Resolved display-name (from <channel><display-name>).
 * @param episodeNum   Content of <episode-num system="onscreen">, e.g. "S07E17". Empty if absent.
 */
data class XmltvEntry(
    val channelId: String,
    val channelName: String,
    var start: Date,
    var end: Date,
    var title: String = "",
    var subTitle: String = "",
    var description: String = "",
    var episodeNum: String = ""
)
