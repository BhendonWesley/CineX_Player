package com.cinex.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinex.player.data.model.ChannelTmdbMetadata

@Dao
interface ChannelTmdbDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: ChannelTmdbMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metadataList: List<ChannelTmdbMetadata>)

    /**
     * Copia todos os metadados TMDB da tabela de staging para a tabela channels em uma única
     * operação SQL. Chamado uma vez ao ativar a playlist — não durante a navegação.
     * Isso garante que writes de enriquecimento em background nunca invalidem o PagingSource.
     */
    @Query("""
        UPDATE channels SET
            tmdbRating    = (SELECT tmdbRating    FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl),
            tmdbSynopsis  = (SELECT tmdbSynopsis  FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl),
            posterUrl     = (SELECT posterUrl     FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl),
            bannerUrl     = (SELECT bannerUrl     FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl),
            tmdbYear      = (SELECT tmdbYear      FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl),
            castMembers   = (SELECT castMembers   FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl),
            trailerUrl    = (SELECT trailerUrl    FROM channel_tmdb_metadata m WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl)
        WHERE playlistUrl = :playlistUrl
        AND EXISTS (
            SELECT 1 FROM channel_tmdb_metadata m
            WHERE m.remoteId = channels.remoteId AND m.playlistUrl = channels.playlistUrl
        )
    """)
    suspend fun applyToChannels(playlistUrl: String)

    @Query("SELECT COUNT(*) FROM channel_tmdb_metadata WHERE playlistUrl = :playlistUrl")
    suspend fun countForPlaylist(playlistUrl: String): Int

    @Query("DELETE FROM channel_tmdb_metadata WHERE playlistUrl = :playlistUrl")
    suspend fun clearForPlaylist(playlistUrl: String)
}
