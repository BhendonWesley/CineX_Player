package com.cinex.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cinex.player.data.model.Category
import com.cinex.player.data.model.Channel
import com.cinex.player.data.model.Playlist

@Database(entities = [Channel::class, Playlist::class, Category::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
}
