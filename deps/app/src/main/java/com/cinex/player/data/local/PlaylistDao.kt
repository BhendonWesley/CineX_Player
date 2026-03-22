package com.cinex.player.data.local

import androidx.room.*
import com.cinex.player.data.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY lastUsed DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE url = :url LIMIT 1")
    suspend fun getPlaylistByUrl(url: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET lastSyncTime = :time WHERE url = :url")
    suspend fun updateLastSyncTime(url: String, time: Long)

    @Query("DELETE FROM playlists")
    suspend fun clearAll()
}
