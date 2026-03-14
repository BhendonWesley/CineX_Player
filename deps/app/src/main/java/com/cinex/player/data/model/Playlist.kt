package com.cinex.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val status: String = "Disconnected", // e.g., Connected, Disconnected
    val macAddress: String? = null,
    val epgUrl: String? = null,
    val lastUsed: Long = System.currentTimeMillis()
)
