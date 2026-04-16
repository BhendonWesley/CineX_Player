package com.cinex.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cinex.player.data.model.Channel
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)

    /** 
     * Insere todos os canais em uma única transação — muito mais rápido que múltiplas transações.
     * Usa chunks de 500 para evitar problemas de memória com listas muito grandes.
     */
    @Transaction
    suspend fun insertAllInTransaction(channels: List<Channel>) {
        channels.chunked(500).forEach { chunk -> insertAll(chunk) }
    }

    /**
     * OTIMIZAÇÃO: Versão que acumula todos os canais e insere em batch único.
     * Recebe uma lista de listas (uma por categoria) e insere tudo em uma única transação.
     * Isso evita o overhead de N transações quando há muitas categorias.
     */
    @Transaction
    suspend fun insertAllChannelsBatch(allChannels: List<List<Channel>>) {
        // Achata todas as listas e insere em chunks de 1000
        allChannels.flatten().chunked(1000).forEach { chunk ->
            insertAll(chunk)
        }
    }

    @Query("SELECT * FROM channels WHERE category = :category AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByCategory(category: String, url: String): PagingSource<Int, Channel>

    // === OTIMIZAÇÃO DE PERFORMANCE (PAGING) ===
    // Consultas específicas por tipo de ordenação para evitar que o SQLite faça um table scan 
    // avaliando ORDER BY CASE WHEN... para cada item, o que travava a interface no Paging 3.

    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY name COLLATE NOCASE ASC")
    fun getMoviesPagedAZ(url: String, categoryId: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY name COLLATE NOCASE DESC")
    fun getMoviesPagedZA(url: String, categoryId: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY CAST(COALESCE(tmdbRating, 0) AS REAL) DESC, orderIndex ASC")
    fun getMoviesPagedRating(url: String, categoryId: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY syncedAt DESC, CAST(SUBSTR(remoteId, INSTR(remoteId, '_') + 1) AS INTEGER) DESC, orderIndex DESC")
    fun getMoviesPagedRecent(url: String, categoryId: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'MOVIE' AND playlistUrl = :url ORDER BY name COLLATE NOCASE ASC")
    fun getMoviesFavoritesPagedAZ(url: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'MOVIE' AND playlistUrl = :url ORDER BY name COLLATE NOCASE DESC")
    fun getMoviesFavoritesPagedZA(url: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'MOVIE' AND playlistUrl = :url ORDER BY CAST(COALESCE(tmdbRating, 0) AS REAL) DESC, orderIndex ASC")
    fun getMoviesFavoritesPagedRating(url: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'MOVIE' AND playlistUrl = :url ORDER BY syncedAt DESC, CAST(SUBSTR(remoteId, INSTR(remoteId, '_') + 1) AS INTEGER) DESC, orderIndex DESC")
    fun getMoviesFavoritesPagedRecent(url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY COALESCE(seriesName, name, '') COLLATE NOCASE ASC")
    fun getSeriesPagedAZ(url: String, categoryId: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY COALESCE(seriesName, name, '') COLLATE NOCASE DESC")
    fun getSeriesPagedZA(url: String, categoryId: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY CAST(COALESCE(tmdbRating, 0) AS REAL) DESC, orderIndex ASC")
    fun getSeriesPagedRating(url: String, categoryId: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url AND (:categoryId = 'Tudo' OR categoryId = :categoryId) ORDER BY syncedAt DESC, CAST(SUBSTR(remoteId, INSTR(remoteId, '_') + 1) AS INTEGER) DESC, orderIndex DESC")
    fun getSeriesPagedRecent(url: String, categoryId: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY COALESCE(seriesName, name, '') COLLATE NOCASE ASC")
    fun getSeriesFavoritesPagedAZ(url: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY COALESCE(seriesName, name, '') COLLATE NOCASE DESC")
    fun getSeriesFavoritesPagedZA(url: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY CAST(COALESCE(tmdbRating, 0) AS REAL) DESC, orderIndex ASC")
    fun getSeriesFavoritesPagedRating(url: String): PagingSource<Int, Channel>
    
    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY syncedAt DESC, CAST(SUBSTR(remoteId, INSTR(remoteId, '_') + 1) AS INTEGER) DESC, orderIndex DESC")
    fun getSeriesFavoritesPagedRecent(url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = :category AND playlistUrl = :url")
    suspend fun getChannelsByCategoryList(category: String, url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE category = 'LIVE_TV' AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun observeAllLiveChannels(url: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun observeAllMovies(url: String): Flow<List<Channel>>

    @Query("""
        SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url
        AND (:categoryId = 'Tudo' OR categoryId = :categoryId)
        ORDER BY orderIndex ASC
    """)
    fun observeMoviesByCategory(url: String, categoryId: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun observeAllSeries(url: String): Flow<List<Channel>>

    @Query("""
        SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url
        AND (:categoryId = 'Tudo' OR categoryId = :categoryId)
        ORDER BY orderIndex ASC
    """)
    fun observeSeriesByCategory(url: String, categoryId: String): Flow<List<Channel>>

    @Query("""
        SELECT * FROM channels WHERE category = :type AND playlistUrl = :url
        AND (:categoryId = 'Tudo' OR categoryId = :categoryId)
        ORDER BY orderIndex ASC LIMIT :limit
    """)
    suspend fun getFirstChannelsByCategory(type: String, url: String, categoryId: String, limit: Int): List<Channel>

    @Query("SELECT COUNT(*) FROM channels WHERE category = 'SERIES' AND seasonNumber IS NOT NULL AND playlistUrl = :url")
    suspend fun countEpisodesOnly(url: String): Int

    @Query("""
        SELECT * FROM channels WHERE category = 'MOVIE' AND playlistUrl = :url
        AND (
            tmdbSynopsis IS NULL OR tmdbSynopsis = ''
            OR bannerUrl IS NULL OR bannerUrl = ''
            OR bannerUrl NOT LIKE '%/original/%'
        )
    """)
    suspend fun getMoviesToEnrich(url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE groupTitle = :groupTitle AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByGroupPaged(groupTitle: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getChannelsByCategoryIdPaged(categoryId: String, url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'LIVE_TV' AND categoryId = :categoryId AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getLiveChannelsByCategoryIdPaged(categoryId: String, url: String): PagingSource<Int, Channel>

    // Para UI de Grid de Séries: usa seasonNumber IS NULL para garantir que só a linha-pai da série aparece (nunca episódios)
    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getUniqueSeries(url: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url")
    suspend fun getUniqueSeriesList(url: String): List<Channel>

    @Query("""
        SELECT * FROM channels WHERE category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url
        AND (
            tmdbSynopsis IS NULL OR tmdbSynopsis = ''
            OR bannerUrl IS NULL OR bannerUrl = ''
            OR bannerUrl NOT LIKE '%/original/%'
        )
    """)
    suspend fun getSeriesToEnrich(url: String): List<Channel>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND category = 'SERIES' AND seasonNumber IS NULL AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getUniqueSeriesByCategoryId(categoryId: String, url: String): PagingSource<Int, Channel>

    @Query("""
        SELECT * FROM channels
        WHERE id IN (
            SELECT MIN(id) FROM channels
            WHERE category IN ('MOVIE', 'SERIES')
            AND playlistUrl = :url
            AND (
                -- Opção 1: Tem dados TMDB completos (banner original + sinopse)
                (bannerUrl IS NOT NULL AND bannerUrl != '' AND bannerUrl NOT LIKE '%null'
                 AND bannerUrl LIKE '%/original/%'
                 AND tmdbSynopsis IS NOT NULL AND tmdbSynopsis != '')
                OR
                -- Opção 2: Tem pelo menos poster do Xtream (fallback visual)
                (posterUrl IS NOT NULL AND posterUrl != '' AND posterUrl NOT LIKE '%null')
            )
            GROUP BY CASE WHEN category = 'SERIES' THEN seriesName ELSE name END
        )
        ORDER BY 
            CASE 
                WHEN bannerUrl LIKE '%/original/%' THEN tmdbRating 
                ELSE 0 
            END DESC,
            orderIndex ASC
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

    /**
     * MIGRAÇÃO: limpa bannerUrls com resolução /w1280/ (formato antigo).
     * Esses URLs não passam no filtro getFeaturedContent (LIKE '%/original/%'),
     * causando capas do Xtream no lugar dos backdrops TMDB.
     * Após limpar, o background enrichment re-busca com /original/.
     */
    @Query("UPDATE channels SET bannerUrl = NULL WHERE bannerUrl LIKE '%/t/p/w1280%'")
    suspend fun clearStaleW1280Banners()

    /**
     * MIGRAÇÃO: limpa sinopses que são a string literal "null" (vindo de JSON malformado).
     * Esses itens têm tmdbSynopsis = "null" que passa no isNullOrEmpty() mas exibe "null" na UI.
     * Após limpar, o background enrichment re-busca a sinopse correta.
     */
    @Query("UPDATE channels SET tmdbSynopsis = NULL WHERE LOWER(TRIM(tmdbSynopsis)) = 'null'")
    suspend fun clearNullStringSynopsis()

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

    @Query("DELETE FROM channels WHERE category = :category AND playlistUrl = :url")
    suspend fun clearByCategory(category: String, url: String)

    @Query("SELECT COUNT(*) FROM channels WHERE category = :type AND playlistUrl = :url")
    suspend fun countByType(type: String, url: String): Int

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

    @Query("""
        UPDATE channels
        SET categoryId = CASE
            WHEN category = 'LIVE_TV' AND categoryId GLOB '[0-9]*' AND categoryId NOT LIKE 'live_%' THEN 'live_' || categoryId
            WHEN category = 'MOVIE' AND categoryId GLOB '[0-9]*' AND categoryId NOT LIKE 'vod_%' THEN 'vod_' || categoryId
            WHEN category = 'SERIES' AND categoryId GLOB '[0-9]*' AND categoryId NOT LIKE 'series_%' THEN 'series_' || categoryId
            ELSE categoryId
        END
        WHERE playlistUrl = :url
    """)
    suspend fun normalizeLegacyCategoryIds(url: String)

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
