package com.cinex.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cinex.player.data.model.Channel
import com.cinex.player.data.model.ChannelTmdbMetadata
import com.cinex.player.data.model.Playlist
import com.cinex.player.data.model.Category
import com.cinex.player.data.model.EpgChannel
import com.cinex.player.data.model.EpgProgram

@Database(
    entities = [Channel::class, ChannelTmdbMetadata::class, Playlist::class, Category::class, EpgChannel::class, EpgProgram::class],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun channelTmdbDao(): ChannelTmdbDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS channel_tmdb_metadata (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId TEXT NOT NULL,
                        playlistUrl TEXT NOT NULL,
                        tmdbRating REAL,
                        tmdbSynopsis TEXT,
                        posterUrl TEXT,
                        bannerUrl TEXT,
                        tmdbYear TEXT,
                        castMembers TEXT,
                        trailerUrl TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_channel_tmdb_metadata_remoteId_playlistUrl
                    ON channel_tmdb_metadata (remoteId, playlistUrl)
                """.trimIndent())
            }
        }
    }
}
