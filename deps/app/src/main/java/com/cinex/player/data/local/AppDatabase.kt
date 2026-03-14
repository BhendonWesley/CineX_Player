package com.cinex.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cinex.player.data.model.EpgChannel
import com.cinex.player.data.model.EpgProgram

@Database(
    entities = [Channel::class, Playlist::class, Category::class, EpgChannel::class, EpgProgram::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao
}
