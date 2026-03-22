package com.cinex.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cinex.player.data.model.Channel
import com.cinex.player.data.model.Playlist
import com.cinex.player.data.model.Category
import com.cinex.player.data.model.EpgChannel
import com.cinex.player.data.model.EpgProgram

@Database(
    entities = [Channel::class, Playlist::class, Category::class, EpgChannel::class, EpgProgram::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao
}
