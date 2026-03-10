package com.cinex.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String, // XC category_id
    val name: String,
    val type: String, // "LIVE_TV", "MOVIE", "SERIES"
    val playlistUrl: String,
    val orderIndex: Int = 0, // Ordem original do servidor
    val parentId: String? = null
)
