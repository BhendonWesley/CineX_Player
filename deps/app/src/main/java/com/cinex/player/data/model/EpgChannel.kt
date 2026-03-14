package com.cinex.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_channels")
data class EpgChannel(
    @PrimaryKey val id: String, // tvg-id
    val name: String?,
    val playlistUrl: String
)
