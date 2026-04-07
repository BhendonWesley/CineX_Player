package com.cinex.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cinex.player.data.model.Channel
import com.cinex.player.data.model.Playlist
import com.cinex.player.data.model.Category
import com.cinex.player.data.model.EpgChannel
import com.cinex.player.data.model.EpgProgram

@Database(
    entities = [Channel::class, Playlist::class, Category::class, EpgChannel::class, EpgProgram::class],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
