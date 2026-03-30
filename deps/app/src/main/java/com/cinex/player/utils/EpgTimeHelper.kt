package com.cinex.player.utils

import com.cinex.player.data.network.EpgListing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object EpgTimeHelper {
    
    // O dono do app solicitou que o horário seja chumbado para o Brasil
    // A API do XTREAM exporta a data de formas divergentes: 
    // - O timestamp inteiro é quebrado em +4h de diferença local
    // - A String Xtream é quebrada em +1h (se lida em UTC)
    val timestampShiftHours: Int = -4
    val stringShiftHours: Int = -1

    /**
     * Extrai a data correta do EpgListing da Xtream API em milissegundos, ignorando
     * o unix timestamp quebrado do painel, e usando a string de tempo como base UTC.
     */
    fun parseXtreamEpgTime(dateString: String?, timestampString: String?): Long? {
        // Prioridade 1: Unix Timestamp (O timestamp inteiro mantem a duração relativa correta entre os programas, diferentemente da String)
        val ts = timestampString?.toLongOrNull()
        if (ts != null && ts > 0) {
            val shiftMillisTs = timestampShiftHours * 3600 * 1000L
            return (ts * 1000L) + shiftMillisTs
        }

        // Prioridade 2 (Fallback de Segurança): Parsear a string 'yyyy-MM-dd HH:mm:ss'
        if (!dateString.isNullOrBlank()) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(dateString)
                if (date != null) {
                    val shiftMillisStr = stringShiftHours * 3600 * 1000L
                    return date.time + shiftMillisStr
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        return null
    }

    /**
     * Retorna a string formatada "HH:mm" ou "hh:mm a" daquele momento em milissegundos.
     */
    fun formatTime(timeMillis: Long, is24Hour: Boolean = true): String {
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        return format.format(Date(timeMillis))
    }

    /**
     * Formata um EpgListing diretamente para "Inicio - Fim" (ex: "14:45 - 16:45")
     */
    fun formatEpgRange(epg: EpgListing, is24Hour: Boolean = true): String {
        val startMs = parseXtreamEpgTime(epg.start, epg.start_timestamp)
        val endMs = parseXtreamEpgTime(epg.end, epg.stop_timestamp)
        
        if (startMs != null && endMs != null) {
            return "${formatTime(startMs, is24Hour)} - ${formatTime(endMs, is24Hour)}"
        }
        return ""
    }
}
