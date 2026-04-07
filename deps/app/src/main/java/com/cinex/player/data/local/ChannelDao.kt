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

    @Query("""
        SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url
        AND (:categoryId = 'Tudo' OR categoryId = :categoryId)
        ORDER BY
            CASE WHEN :sort = 'AZ'     THEN LOWER(COALESCE(name, '')) END ASC,
            CASE WHEN :sort = 'ZA'     THEN LOWER(COALESCE(name, '')) END DESC,
            CASE WHEN :sort = 'RATING' THEN CAST(COALESCE(tmdbRating, 0) AS REAL) END DESC,
            CASE WHEN :sort = 'RECENT' THEN syncedAt END DESC,
            CASE WHEN :sort = 'RECENT' THEN CAST(SUBSTR(remoteId, INSTR(remoteId, '_') + 1) AS INTEGER) END DESC,
            CASE WHEN :sort = 'RECENT' THEN orderIndex END DESC,
            orderIndex ASC
    """)
    fun getMoviesPaged(url: String, categoryId: String, sort: String): PagingSource<Int, Channel>

    @Query("""
        SELECT * FROM channels WHERE isFavorite = 1 AND category = 'MOVIE' AND playlistUrl = :url
        ORDER BY
            CASE WHEN :sort = 'AZ'     THEN LOWER(COALESCE(name, '')) END ASC,
            CASE WHEN :sort = 'ZA'     THEN LOWER(COALESCE(name, '')) END DESC,
            CASE WHEN :sort = 'RATING' THEN CAST(COALESCE(tmdbRating, 0) AS REAL) END DESC,
            orderIndex ASC
    """)
    fun getMoviesFavoritesPaged(url: String, sort: String): PagingSource<Int, Channel>

    @Query("""
        SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url
        AND (:categoryId = 'Tudo' OR categoryId = :categoryId)
        ORDER BY
            CASE WHEN :sort = 'AZ'     THEN LOWER(COALESCE(seriesName, name, '')) END ASC,
            CASE WHEN :sort = 'ZA'     THEN LOWER(COALESCE(seriesName, name, '')) END DESC,
            CASE WHEN :sort = 'RATING' THEN CAST(COALESCE(tmdbRating, 0) AS REAL) END DESC,
            CASE WHEN :sort = 'RECENT' THEN syncedAt END DESC,
            CASE WHEN :sort = 'RECENT' THEN CAST(SUBSTR(remoteId, INSTR(remoteId, '_') + 1) AS INTEGER) END DESC,
            CASE WHEN :sort = 'RECENT' THEN orderIndex END DESC,
            orderIndex ASC
    """)
    fun getSeriesPaged(url: String, categoryId: String, sort: String): PagingSource<Int, Channel>

    @Query("""
        SELECT * FROM channels WHERE isFavorite = 1 AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url
        ORDER BY
            CASE WHEN :sort = 'AZ'     THEN LOWER(COALESCE(seriesName, name, '')) END ASC,
            CASE WHEN :sort = 'ZA'     THEN LOWER(COALESCE(seriesName, name, '')) END DESC,
            CASE WHEN :sort = 'RATING' THEN CAST(COALESCE(tmdbRating, 0) AS REAL) END DESC,
            orderIndex ASC
    """)
    fun getSeriesFavoritesPaged(url: String, sort: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = :category AND playlistUrl = :url")
    suspend fun getChannelsByCategoryList(category: String, url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE category = 'LIVE_TV' AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun observeAllLiveChannels(url: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun observeAllMovies(url: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun observeAllSeries(url: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url AND (tmdbSynopsis IS NULL OR tmdbSynopsis = '')")
    suspend fun getMoviesToEnrich(url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE groupTitle = :groupTitle AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByGroupPaged(groupTitle: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByCategoryIdPaged(categoryId: String, url: String): PagingSource<Int, Channel>

    // Para UI de Grid de Séries: usa seasonNumber IS NULL para garantir que só a linha-pai da série aparece (nunca episódios)
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getUniqueSeries(url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url")
    suspend fun getUniqueSeriesList(url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url AND (tmdbSynopsis IS NULL OR tmdbSynopsis = '')")
    suspend fun getSeriesToEnrich(url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getUniqueSeriesByCategoryId(categoryId: String, url: String): PagingSource<Int, Channel>

    @Query("""
        SELECT * FROM channels
        WHERE id IN (
            SELECT MIN(id) FROM channels
            WHERE category IN ('MOVIE', 'SERIES')
            AND playlistUrl = :url
            AND bannerUrl IS NOT NULL AND bannerUrl != '' AND bannerUrl NOT LIKE '%null'
            AND bannerUrl LIKE '%/original/%'
            AND tmdbSynopsis IS NOT NULL AND tmdbSynopsis != ''
            GROUP BY CASE WHEN category = 'SERIES' THEN seriesName ELSE name END
        )
        ORDER BY tmdbRating DESC
        LIMIT 40
    """)
    fun getFeaturedContent(url: String): Flow<List<Channel>>

    // Retorna todos os episódios de uma série específica, ordenados
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForSeries(seriesName: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url")
    suspend fun getEpisodesForSeriesList(seriesName: String, url: String): List<Channel>

    @Query("SELECT COUNT(*) FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url LIMIT 1")
    suspend fun countEpisodesForSeries(seriesName: String, url: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url AND seasonNumber IS NOT NULL AND (bannerUrl IS NULL OR bannerUrl = '' OR bannerUrl LIKE '%/original/%')")
    suspend fun countEpisodesWithoutStill(seriesName: String, url: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND seasonNumber = :season AND playlistUrl = :url AND (bannerUrl IS NULL OR bannerUrl = '' OR bannerUrl LIKE '%/original/%')")
    suspend fun countEpisodesWithoutStillForSeason(seriesName: String, season: Int, url: String): Int

    @Query("SELECT DISTINCT seasonNumber FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url AND seasonNumber IS NOT NULL ORDER BY seasonNumber ASC")
    fun getSeasonsForSeries(seriesName: String, url: String): Flow<List<Int>>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND seasonNumber = :season AND playlistUrl = :url GROUP BY episodeNumber ORDER BY episodeNumber ASC")
    fun getEpisodesBySeasonPaged(seriesName: String, season: Int, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND seasonNumber = :season AND playlistUrl = :url GROUP BY episodeNumber ORDER BY episodeNumber ASC")
    fun observeEpisodesBySeason(seriesName: String, season: Int, url: String): Flow<List<Channel>>

    @Query("""
        SELECT * FROM channels
        WHERE playlistUrl = :url
        AND seasonNumber IS NULL
        AND LOWER(COALESCE(seriesName, name)) LIKE '%' || LOWER(:query) || '%'
        ORDER BY orderIndex ASC
    """)
    fun searchChannels(query: String, url: String): PagingSource<Int, Channel>

    // ---- Funções Premium (XC / IBO) ---- //
    @Query("SELECT posterUrl FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url AND posterUrl IS NOT NULL AND posterUrl != '' LIMIT 1")
    suspend fun getSeriesPosterUrl(seriesName: String, url: String): String?

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: Int): Channel?

    @Query("SELECT * FROM channels WHERE remoteId = :remoteId AND playlistUrl = :url LIMIT 1")
    fun observeChannelByRemoteId(remoteId: String, url: String): Flow<Channel?>

    @Query("SELECT COUNT(*) FROM channels WHERE category = 'SERIES' AND seriesName = :seriesName AND seasonNumber = :season AND playlistUrl = :url AND (bannerUrl IS NULL OR bannerUrl = '' OR bannerUrl LIKE '%/original/%')")
    fun observeEpisodesWithoutStillForSeason(seriesName: String, season: Int, url: String): Flow<Int>

    @Query("UPDATE channels SET resumePosition = :position, totalDuration = :duration WHERE id = :channelId")
    suspend fun updateResumePosition(channelId: Int, position: Long, duration: Long)

    @Query("UPDATE channels SET resumePosition = 1 WHERE category = 'SERIES' AND seasonNumber IS NULL AND seriesName = :seriesName AND playlistUrl = :url")
    suspend fun markSeriesParentAsWatched(seriesName: String, url: String)

    @Query("SELECT * FROM channels WHERE resumePosition > 0 AND category IN ('MOVIE', 'SERIES') AND (category = 'MOVIE' OR seasonNumber IS NOT NULL) AND playlistUrl = :url ORDER BY id DESC LIMIT 20")
    fun getContinueWatching(url: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE resumePosition > 0 AND category = :category AND (:category != 'SERIES' OR seasonNumber IS NOT NULL) AND playlistUrl = :url ORDER BY id DESC")
    fun getContinueWatchingPaged(category: String, url: String): PagingSource<Int, Channel>

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :channelId")
    suspend fun updateFavorite(channelId: Int, isFav: Boolean)

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND playlistUrl = :url")
    fun getFavorites(url: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = :category AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getFavoritesPaged(category: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getFavoriteSeriesPaged(url: String): PagingSource<Int, Channel>

    // Atualiza metadados do TMDB em uma linha específica
    @Query("UPDATE channels SET tmdbRating = :rating, tmdbSynopsis = :synopsis, posterUrl = :posterUrl, bannerUrl = :bannerUrl, tmdbYear = :year, castMembers = :cast, trailerUrl = :trailer WHERE id = :channelId")
    suspend fun updateTmdbInfo(channelId: Int, rating: Double?, synopsis: String?, posterUrl: String?, bannerUrl: String?, year: String?, cast: String?, trailer: String?)

    @Query("""UPDATE channels SET
        tmdbRating = NULL,
        tmdbSynopsis = NULL,
        bannerUrl = NULL,
        tmdbYear = NULL,
        castMembers = NULL,
        trailerUrl = NULL,
        posterUrl = CASE WHEN logoUrl IS NOT NULL AND logoUrl != '' THEN logoUrl ELSE NULL END
        WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url AND seasonNumber IS NULL""")
    suspend fun clearSeriesTmdbMetadata(seriesName: String, url: String)

    @Query("""UPDATE channels SET
        bannerUrl = NULL
        WHERE category = 'SERIES'
        AND seriesName = :seriesName
        AND playlistUrl = :url
        AND seasonNumber IS NOT NULL
        AND bannerUrl LIKE '%image.tmdb.org/t/p/%'""")
    suspend fun clearEpisodeTmdbStillImages(seriesName: String, url: String)

    @Query("""UPDATE channels SET
        bannerUrl = NULL
        WHERE category = 'SERIES'
        AND seriesName = :seriesName
        AND seasonNumber = :season
        AND playlistUrl = :url
        AND bannerUrl LIKE '%image.tmdb.org/t/p/%'""")
    suspend fun clearEpisodeTmdbStillImagesForSeason(seriesName: String, season: Int, url: String)

    // Propaga o backdrop da série para TODOS os episódios que ainda não têm bannerUrl (/original/)
    // Garante que getFeaturedContent sempre encontre uma linha com backdrop válido para a série
    @Query("""UPDATE channels SET
        tmdbRating = :rating,
        tmdbYear = :year,
        castMembers = :cast,
        posterUrl = CASE WHEN posterUrl IS NULL OR posterUrl = '' THEN :posterUrl ELSE posterUrl END,
        bannerUrl = CASE WHEN bannerUrl IS NULL OR bannerUrl = '' OR bannerUrl NOT LIKE '%/original/%' THEN :bannerUrl ELSE bannerUrl END
        WHERE category = 'SERIES' AND seriesName = :seriesName AND playlistUrl = :url""")
    suspend fun propagateSeriesBackdrop(seriesName: String, url: String, rating: Double?, posterUrl: String?, bannerUrl: String?, year: String?, cast: String?)

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

    @Query("""
        UPDATE channels SET
            name = :name,
            groupTitle = :groupTitle,
            streamUrl = :streamUrl,
            categoryId = :categoryId
        WHERE playlistUrl = :playlistUrl AND remoteId = :remoteId
    """)
    suspend fun updateVolatileFields(
        playlistUrl: String, remoteId: String,
        name: String, groupTitle: String, streamUrl: String, categoryId: String
    )
    
    @Query("SELECT * FROM channels WHERE playlistUrl = :url")
    suspend fun getAllByPlaylist(url: String): List<Channel>

    @Query("SELECT remoteId, tmdbRating, tmdbSynopsis, posterUrl, bannerUrl, tmdbYear, castMembers, trailerUrl, resumePosition, totalDuration, isFavorite FROM channels WHERE playlistUrl = :url")
    suspend fun getTmdbAndUserDataByPlaylist(url: String): List<ChannelPreserveData>

    @Query("SELECT categoryId, COUNT(*) as count FROM channels WHERE playlistUrl = :url GROUP BY categoryId")
    fun getCategoryCounts(url: String): Flow<List<CategoryCount>>

    @Query("SELECT category, COUNT(*) as count FROM channels WHERE playlistUrl = :url GROUP BY category")
    fun getTypeCounts(url: String): Flow<List<TypeCount>>

    @Query("SELECT category, COUNT(*) as count FROM channels WHERE playlistUrl = :url AND isFavorite = 1 GROUP BY category")
    fun getFavoriteCounts(url: String): Flow<List<TypeCount>>

    @Query("UPDATE channels SET bannerUrl = :stillUrl WHERE seriesName = :seriesName AND seasonNumber = :season AND episodeNumber = :episode AND playlistUrl = :url")
    suspend fun updateEpisodeStill(seriesName: String, season: Int, episode: Int, stillUrl: String, url: String)

    @Query("""UPDATE channels SET
        bannerUrl = CASE
            WHEN :stillUrl IS NOT NULL AND :stillUrl != ''
             AND (bannerUrl IS NULL OR bannerUrl = '' OR bannerUrl LIKE '%/original/%')
            THEN :stillUrl ELSE bannerUrl END,
        tmdbSynopsis = CASE WHEN :synopsis IS NOT NULL AND :synopsis != '' THEN :synopsis ELSE tmdbSynopsis END
        WHERE seriesName = :seriesName AND seasonNumber = :season AND episodeNumber = :episode AND playlistUrl = :url""")
    suspend fun updateEpisodeStillAndSynopsis(seriesName: String, season: Int, episode: Int, stillUrl: String?, synopsis: String?, url: String)

    @Query("""
        SELECT * FROM channels
        WHERE category = 'SERIES'
        AND seriesName = :seriesName
        AND playlistUrl = :url
        AND (
            (seasonNumber = :currentSeason AND episodeNumber = :currentEpisode + 1) OR
            (seasonNumber = :currentSeason + 1 AND episodeNumber = 1)
        )
        ORDER BY seasonNumber ASC, episodeNumber ASC
        LIMIT 1
    """)
    suspend fun getNextEpisode(seriesName: String, currentSeason: Int, currentEpisode: Int, url: String): Channel?
}

data class ChannelPreserveData(
    val remoteId: String,
    val tmdbRating: Double?,
    val tmdbSynopsis: String?,
    val posterUrl: String?,
    val bannerUrl: String?,
    val tmdbYear: String?,
    val castMembers: String?,
    val trailerUrl: String?,
    val resumePosition: Long,
    val totalDuration: Long,
    val isFavorite: Boolean
)

data class CategoryCount(
    val categoryId: String,
    val count: Int
)

data class TypeCount(
    val category: String, // "LIVE_TV", "MOVIE", "SERIES"
    val count: Int
)
