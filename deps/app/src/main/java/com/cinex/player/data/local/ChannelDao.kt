package com.cinex.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinex.player.data.model.Channel
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)

    @Query("SELECT * FROM channels WHERE category = :category AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByCategory(category: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = :category AND playlistUrl = :url")
    suspend fun getChannelsByCategoryList(category: String, url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE groupTitle = :groupTitle AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByGroupPaged(groupTitle: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByCategoryIdPaged(categoryId: String, url: String): PagingSource<Int, Channel>

    // Para UI de Grid de Séries: Agrupa as séries pelo nome da série, retornando o primeiro episódio para usar como capa da série.
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND playlistUrl = :url GROUP BY seriesName ORDER BY orderIndex ASC")
    fun getUniqueSeries(url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND playlistUrl = :url GROUP BY seriesName")
    suspend fun getUniqueSeriesList(url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND category = 'SERIES' AND playlistUrl = :url GROUP BY seriesName ORDER BY orderIndex ASC")
    fun getUniqueSeriesByCategoryId(categoryId: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category IN ('MOVIE', 'SERIES') AND playlistUrl = :url ORDER BY CASE WHEN bannerUrl IS NOT NULL AND bannerUrl != '' THEN 0 WHEN logoUrl IS NOT NULL AND logoUrl != '' THEN 1 ELSE 2 END, id DESC LIMIT 20")
    suspend fun getFeaturedContent(url: String): List<Channel>

    // Retorna todos os episódios de uma série específica, ordenados
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForSeries(seriesName: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url")
    suspend fun getEpisodesForSeriesList(seriesName: String, url: String): List<Channel>

    @Query("SELECT DISTINCT seasonNumber FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url ORDER BY seasonNumber ASC")
    fun getSeasonsForSeries(seriesName: String, url: String): Flow<List<Int>>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND seasonNumber = :season AND playlistUrl = :url ORDER BY episodeNumber ASC")
    fun getEpisodesBySeasonPaged(seriesName: String, season: Int, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun searchChannels(query: String, url: String): PagingSource<Int, Channel>

    // ---- Funções Premium (XC / IBO) ---- //
    @Query("UPDATE channels SET resumePosition = :position, totalDuration = :duration WHERE id = :channelId")
    suspend fun updateResumePosition(channelId: Int, position: Long, duration: Long)

    @Query("SELECT * FROM channels WHERE resumePosition > 0 AND category IN ('MOVIE', 'SERIES') AND playlistUrl = :url ORDER BY id DESC LIMIT 20")
    fun getContinueWatching(url: String): Flow<List<Channel>>

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :channelId")
    suspend fun updateFavorite(channelId: Int, isFav: Boolean)

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND playlistUrl = :url")
    fun getFavorites(url: String): Flow<List<Channel>>

    // Atualiza metadados do TMDB
    @Query("UPDATE channels SET tmdbRating = :rating, tmdbSynopsis = :synopsis, logoUrl = :posterUrl, bannerUrl = :bannerUrl, tmdbYear = :year, castMembers = :cast, trailerUrl = :trailer WHERE id = :channelId")
    suspend fun updateTmdbInfo(channelId: Int, rating: Double?, synopsis: String?, posterUrl: String?, bannerUrl: String?, year: String?, cast: String?, trailer: String?)

    @Query("DELETE FROM channels WHERE playlistUrl = :url")
    suspend fun clearByPlaylist(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM channels WHERE playlistUrl = :url LIMIT 1)")
    suspend fun hasChannels(url: String): Boolean

    @Query("UPDATE channels SET resumePosition = 0")
    suspend fun resetAllResumePositions()

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    @Query("SELECT remoteId FROM channels WHERE playlistUrl = :url")
    suspend fun getAllRemoteIds(url: String): List<String>

    @Query("DELETE FROM channels WHERE playlistUrl = :url AND remoteId IN (:remoteIds)")
    suspend fun deleteMultipleByRemoteId(url: String, remoteIds: List<String>)
    
    @Query("SELECT * FROM channels WHERE playlistUrl = :url")
    suspend fun getAllByPlaylist(url: String): List<Channel>
}
