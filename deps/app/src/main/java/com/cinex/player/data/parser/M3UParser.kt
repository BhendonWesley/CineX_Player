package com.cinex.player.data.parser

import com.cinex.player.data.model.Channel

class M3UParser {
    fun parse(reader: java.io.BufferedReader, playlistUrl: String = ""): Pair<List<Channel>, String?> {
        val channels = mutableListOf<Channel>()
        var epgUrl: String? = null
        val seriesRegex = Regex("(?i)[Ss](\\d{2})\\s?[Ee](\\d{2})")

        var currentExtInf: String? = null
        var line: String? = reader.readLine()

        while (line != null) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                line = reader.readLine()
                continue
            }

            if (trimmedLine.startsWith("#EXTM3U")) {
                // Procura por url-tvg ou x-tvg-url no cabeçalho
                if (trimmedLine.contains("url-tvg=\"")) {
                    val start = trimmedLine.indexOf("url-tvg=\"") + 9
                    val end = trimmedLine.indexOf("\"", start)
                    if (end != -1) epgUrl = trimmedLine.substring(start, end)
                } else if (trimmedLine.contains("x-tvg-url=\"")) {
                    val start = trimmedLine.indexOf("x-tvg-url=\"") + 11
                    val end = trimmedLine.indexOf("\"", start)
                    if (end != -1) epgUrl = trimmedLine.substring(start, end)
                }
            } else if (trimmedLine.startsWith("#EXTINF:")) {
                currentExtInf = trimmedLine
            } else if (!trimmedLine.startsWith("#") && currentExtInf != null) {
                val extInf = currentExtInf
                val streamUrl = trimmedLine

                // Fast metadata extraction without Regex
                val lastCommaIndex = extInf.lastIndexOf(',')
                val finalDisplayName = if (lastCommaIndex != -1) extInf.substring(lastCommaIndex + 1).trim() else "Unknown"
                
                var logoUrl: String? = null
                var groupTitle = "Outros"
                
                // Manual extraction for performance
                if (extInf.contains("tvg-logo=\"")) {
                    val start = extInf.indexOf("tvg-logo=\"") + 10
                    val end = extInf.indexOf("\"", start)
                    if (end != -1) logoUrl = extInf.substring(start, end)
                }
                
                if (extInf.contains("group-title=\"")) {
                    val start = extInf.indexOf("group-title=\"") + 13
                    val end = extInf.indexOf("\"", start)
                    if (end != -1) groupTitle = extInf.substring(start, end)
                }

                var tvgId: String? = null
                if (extInf.contains("tvg-id=\"")) {
                    val start = extInf.indexOf("tvg-id=\"") + 8
                    val end = extInf.indexOf("\"", start)
                    if (end != -1) tvgId = extInf.substring(start, end)
                }

                val lowerName = finalDisplayName.lowercase()
                val lowerGroup = groupTitle.lowercase()
                val seriesMatch = seriesRegex.find(finalDisplayName)
                
                var category: String
                var seriesName: String? = null
                var season: Int? = null
                var ep: Int? = null

                if (lowerName.startsWith("canais |") || lowerGroup.contains("tv") || lowerGroup.contains("aberto") || lowerGroup.contains("esporte")) {
                    category = "LIVE_TV"
                } else if (lowerGroup.contains("filme") || lowerGroup.contains("movie") || lowerGroup.contains("vod")) {
                    category = "MOVIE"
                } else if (lowerGroup.contains("serie") || seriesMatch != null) {
                    category = "SERIES"
                    if (seriesMatch != null) {
                        season = seriesMatch.groupValues[1].toIntOrNull()
                        ep = seriesMatch.groupValues[2].toIntOrNull()
                        seriesName = finalDisplayName.substring(0, seriesMatch.range.first).trim().removeSuffix("-").trim()
                    }
                } else {
                    category = "MOVIE"
                }

                val remoteId = java.util.UUID.nameUUIDFromBytes((finalDisplayName + streamUrl).toByteArray()).toString()

                channels.add(
                    Channel(
                        name = finalDisplayName,
                        logoUrl = logoUrl,
                        groupTitle = groupTitle,
                        streamUrl = streamUrl,
                        category = category,
                        seriesName = seriesName ?: finalDisplayName,
                        seasonNumber = season,
                        episodeNumber = ep,
                        playlistUrl = playlistUrl,
                        remoteId = remoteId,
                        tvgId = tvgId
                    )
                )
                currentExtInf = null 
            }
            line = reader.readLine()
        }
        return channels to epgUrl
    }
}
