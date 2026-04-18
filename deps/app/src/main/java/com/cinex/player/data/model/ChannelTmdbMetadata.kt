package com.cinex.player.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channel_tmdb_metadata",
    indices = [Index(value = ["remoteId", "playlistUrl"], unique = true)]
)
data class ChannelTmdbMetadata(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val remoteId: String,
    val playlistUrl: String,
    val tmdbRating: Double? = null,
    val tmdbSynopsis: String? = null,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val tmdbYear: String? = null,
    val castMembers: String? = null,
    val trailerUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
