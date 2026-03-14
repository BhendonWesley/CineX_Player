package com.cinex.player.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["channelId", "startTime", "endTime"])]
)
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String, // Corresponde ao tvgId do canal
    val title: String,
    val description: String?,
    val startTime: Long, // Timestamp em milissegundos
    val endTime: Long,
    val playlistUrl: String
)
