package com.cinex.player.data.parser

import android.util.Xml
import com.cinex.player.data.model.EpgChannel
import com.cinex.player.data.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class EpgParser {
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(inputStream: InputStream, playlistUrl: String): Pair<List<EpgChannel>, List<EpgProgram>> {
        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgProgram>()
        
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "channel" -> channels.add(parseChannel(parser, playlistUrl))
                        "programme" -> programs.add(parseProgram(parser, playlistUrl))
                    }
                }
            }
            eventType = parser.next()
        }
        return channels to programs
    }

    private fun parseChannel(parser: XmlPullParser, playlistUrl: String): EpgChannel {
        val id = parser.getAttributeValue(null, "id")
        var displayName: String? = null
        
        while (parser.next() != XmlPullParser.END_TAG || parser.name != "channel") {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "display-name") {
                displayName = parser.nextText()
            }
        }
        return EpgChannel(id = id, name = displayName, playlistUrl = playlistUrl)
    }

    private fun parseProgram(parser: XmlPullParser, playlistUrl: String): EpgProgram {
        val channelId = parser.getAttributeValue(null, "channel")
        val start = parser.getAttributeValue(null, "start")
        val stop = parser.getAttributeValue(null, "stop")
        
        var title = ""
        var desc: String? = null

        while (parser.next() != XmlPullParser.END_TAG || parser.name != "programme") {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> title = parser.nextText()
                    "desc" -> desc = parser.nextText()
                }
            }
        }

        return EpgProgram(
            channelId = channelId,
            title = title,
            description = desc,
            startTime = parseDate(start),
            endTime = parseDate(stop),
            playlistUrl = playlistUrl
        )
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            // XMLTV dates are often "20231027120000 +0000"
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
