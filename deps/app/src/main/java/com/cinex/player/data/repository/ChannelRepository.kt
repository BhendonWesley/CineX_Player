package com.cinex.player.data.repository

import com.cinex.player.data.local.ChannelDao
import com.cinex.player.data.model.Channel
import com.cinex.player.data.network.TmdbApi
import com.cinex.player.data.parser.M3UParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.cinex.player.data.network.XtreamCodesApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val playlistDao: com.cinex.player.data.local.PlaylistDao,
    private val categoryDao: com.cinex.player.data.local.CategoryDao, // Novo
    private val parser: M3UParser,
    private val okHttpClient: OkHttpClient, // Injetado
    private val tmdbApi: TmdbApi
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _activePlaylistUrl = MutableStateFlow<String?>(null)
    val activePlaylistUrl = _activePlaylistUrl.asStateFlow()

    val liveTvChannels: Flow<PagingData<Channel>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(PagingData.empty()) 
        else Pager(PagingConfig(pageSize = 50, enablePlaceholders = true)) {
            channelDao.getChannelsByCategory("LIVE_TV", url)
        }.flow
    }.cachedIn(repositoryScope)

    val movieChannels: Flow<PagingData<Channel>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(PagingData.empty()) 
        else Pager(PagingConfig(pageSize = 50, enablePlaceholders = true)) {
            channelDao.getChannelsByCategory("MOVIE", url)
        }.flow
    }.cachedIn(repositoryScope)

    val seriesChannels: Flow<PagingData<Channel>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(PagingData.empty()) 
        else Pager(PagingConfig(pageSize = 50, enablePlaceholders = true)) {
            channelDao.getUniqueSeries(url)
        }.flow
    }.cachedIn(repositoryScope)

    val miscChannels: Flow<PagingData<Channel>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(PagingData.empty()) 
        else Pager(PagingConfig(pageSize = 50, enablePlaceholders = true)) {
            channelDao.getChannelsByCategory("MISC", url)
        }.flow
    }.cachedIn(repositoryScope)

    val liveCategories: Flow<List<com.cinex.player.data.model.Category>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyList()) else categoryDao.getCategoriesByType("LIVE_TV", url)
    }
    val movieCategories: Flow<List<com.cinex.player.data.model.Category>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyList()) else categoryDao.getCategoriesByType("MOVIE", url)
    }
    val seriesCategories: Flow<List<com.cinex.player.data.model.Category>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyList()) else categoryDao.getCategoriesByType("SERIES", url)
    }

    val continueWatching: Flow<List<Channel>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyList()) else channelDao.getContinueWatching(url)
    }

    val allPlaylists: Flow<List<com.cinex.player.data.model.Playlist>> = playlistDao.getAllPlaylists()

    val categoryCounts: Flow<Map<String, Int>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyMap())
        else channelDao.getCategoryCounts(url).map { list -> list.associate { it.categoryId to it.count } }
    }

    val typeCounts: Flow<Map<String, Int>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyMap())
        else channelDao.getTypeCounts(url).map { list -> list.associate { it.category to it.count } }
    }

    val favoriteCounts: Flow<Map<String, Int>> = _activePlaylistUrl.flatMapLatest { url ->
        if (url == null) flowOf(emptyMap())
        else channelDao.getFavoriteCounts(url).map { list -> list.associate { it.category to it.count } }
    }

    suspend fun addPlaylist(name: String, url: String) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(com.cinex.player.data.model.Playlist(name = name, url = url))
    }

    suspend fun selectPlaylist(
        playlist: com.cinex.player.data.model.Playlist,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(playlist.copy(lastUsed = System.currentTimeMillis()))
        syncPlaylist(playlist.url, onProgress)
    }

    fun getEpisodesForSeries(seriesName: String): Flow<PagingData<Channel>> {
        val url = _activePlaylistUrl.value ?: return flowOf(PagingData.empty())
        return Pager(PagingConfig(pageSize = 50)) {
            channelDao.getEpisodesForSeries(seriesName, url)
        }.flow.cachedIn(repositoryScope)
    }

    fun getSeasonsForSeries(seriesName: String): Flow<List<Int>> {
        val url = _activePlaylistUrl.value ?: return flowOf(emptyList())
        return channelDao.getSeasonsForSeries(seriesName, url)
    }

    fun getEpisodesBySeasonPaged(seriesName: String, season: Int): Flow<PagingData<Channel>> {
        val url = _activePlaylistUrl.value ?: return flowOf(PagingData.empty())
        return Pager(PagingConfig(pageSize = 50)) {
            channelDao.getEpisodesBySeasonPaged(seriesName, season, url)
        }.flow.cachedIn(repositoryScope)
    }

    fun searchChannels(query: String): Flow<PagingData<Channel>> {
        val url = _activePlaylistUrl.value ?: return flowOf(PagingData.empty())
        return Pager(PagingConfig(pageSize = 50)) {
            channelDao.searchChannels(query, url)
        }.flow.cachedIn(repositoryScope)
    }

    fun getPagedChannelsByCategory(categoryId: String): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50)) {
                if (categoryId == "Tudo" || categoryId == "Favorito" || categoryId == "Favoritos") {
                    channelDao.getChannelsByCategory("LIVE_TV", url)
                } else {
                    channelDao.getChannelsByCategoryIdPaged(categoryId, url)
                }
            }.flow
        }.cachedIn(repositoryScope)
    }

    fun getPagedMoviesByCategory(categoryId: String): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50)) {
                if (categoryId == "Tudo" || categoryId == "Favorito" || categoryId == "Favoritos") {
                    channelDao.getChannelsByCategory("MOVIE", url)
                } else {
                    channelDao.getChannelsByCategoryIdPaged(categoryId, url)
                }
            }.flow
        }.cachedIn(repositoryScope)
    }

    fun getPagedSeriesByCategory(categoryId: String): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50)) {
                if (categoryId == "Tudo" || categoryId == "Favorito" || categoryId == "Favoritos") {
                    channelDao.getUniqueSeries(url)
                } else {
                    channelDao.getUniqueSeriesByCategoryId(categoryId, url)
                }
            }.flow
        }.cachedIn(repositoryScope)
    }

    suspend fun getFeaturedContent(): List<Channel> {
        val url = _activePlaylistUrl.value ?: return emptyList()
        return channelDao.getFeaturedContent(url)
    }

    suspend fun syncPlaylist(
        url: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Reset reatividade UI
            _activePlaylistUrl.value = null 
            
            // Download progress start
            onProgress(10, 10, 10, "Conectando ao servidor...")

            onProgress(10, 10, 10, "Baixando lista de reprodução...")
            
            // TENTA XTREAM CODES PRIMEIRO (Para pegar organização do ADM)
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            val host = uri.host
            val scheme = uri.scheme
            val port = uri.port

            if (username != null && password != null && host != null) {
                val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                val syncResult = syncXtream(baseUrl, username, password, url, onProgress)
                if (syncResult.isSuccess) {
                    _activePlaylistUrl.value = url
                    return@withContext Result.success(Unit)
                }
            }

            // FALLBACK PARA M3U
            onProgress(10, 10, 10, "Baixando lista de reprodução...")
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
            val body = response.body ?: return@withContext Result.failure(Exception("Empty Body"))
            
            val parsedChannels = body.charStream().buffered().use { reader -> parser.parse(reader, url) }
            
            if (parsedChannels.isNotEmpty()) {
                onProgress(50, 50, 50, "Limpando dados antigos...")
                channelDao.clearByPlaylist(url)
                categoryDao.clearByPlaylist(url) 

                // Extrai categorias do M3U — detecta tipo baseado no conteúdo
                val channelsByGroup = parsedChannels.groupBy { it.groupTitle }
                val m3uCategories = channelsByGroup.entries.mapIndexed { index, (catName, channels) ->
                    // Determina o tipo da categoria baseado na maioria dos canais
                    val type = channels.groupBy { it.category }
                        .maxByOrNull { it.value.size }?.key ?: "LIVE_TV"
                    com.cinex.player.data.model.Category(
                        id = catName,
                        name = catName,
                        type = type,
                        playlistUrl = url,
                        orderIndex = index
                    )
                }
                categoryDao.insertAll(m3uCategories)

                val channelsWithOrder = parsedChannels.mapIndexed { index, channel ->
                    channel.copy(
                        orderIndex = index,
                        categoryId = channel.groupTitle // No M3U, ID = Nome
                    )
                }
                channelsWithOrder.chunked(1000).forEach { chunk -> channelDao.insertAll(chunk) }

                _activePlaylistUrl.value = url
                onProgress(100, 100, 100, "Iniciando em segundo plano...")
            } else {
                return@withContext Result.failure(Exception("Nenhum conteúdo encontrado na lista. Verifique a URL."))
            }

                // PHASE 2: Background Enrichment (Does NOT block the user)
                repositoryScope.launch {
                    val moviesToEnrich = channelDao.getChannelsByCategoryList("MOVIE", url)
                    val seriesToEnrich = channelDao.getUniqueSeriesList(url)
                    
                    val semaphore = Semaphore(10)
                    
                    // Filmes
                    val totalMovies = moviesToEnrich.size
                    if (totalMovies > 0) {
                        val movieCount = AtomicInteger(0)
                        moviesToEnrich.forEach { channel ->
                            launch {
                                semaphore.withPermit {
                                    enrichChannelWithTmdb(channel)
                                    val current = movieCount.incrementAndGet()
                                    if (current % 10 == 0 || current == totalMovies) {
                                        val pct = ((current.toDouble() / totalMovies.toDouble()) * 100.0).toInt()
                                        onProgress(100, pct, 0, "Segundo plano: Filmes $pct%")
                                    }
                                }
                            }
                        }
                    }

                    // Séries
                    val totalSeries = seriesToEnrich.size
                    if (totalSeries > 0) {
                        val seriesCount = AtomicInteger(0)
                        seriesToEnrich.forEach { channel ->
                            launch {
                                semaphore.withPermit {
                                    enrichChannelWithTmdb(channel)
                                    val current = seriesCount.incrementAndGet()
                                    if (current % 5 == 0 || current == totalSeries) {
                                        val pct = ((current.toDouble() / totalSeries.toDouble()) * 100.0).toInt()
                                        onProgress(100, 100, pct, "Segundo plano: Séries $pct%")
                                    }
                                }
                            }
                        }
                    }
            }

            // Retornamos sucesso IMEDIATAMENTE após salvar a lista inicial no banco
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun syncXtream(
        baseUrl: String,
        user: String,
        pass: String,
        playlistUrl: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(XtreamCodesApi::class.java)
            
            onProgress(20, 20, 20, "Sincronizando categorias do painel...")
            
            val liveCats = try { api.getLiveCategories(user, pass) } catch (_: Exception) { emptyList() }
            val vodCats = try { api.getVodCategories(user, pass) } catch (_: Exception) { emptyList() }
            val seriesCats = try { api.getSeriesCategories(user, pass) } catch (_: Exception) { emptyList() }

            channelDao.clearByPlaylist(playlistUrl)
            categoryDao.clearByPlaylist(playlistUrl)

            // Salva as categorias reais no DB
            val allCats = mutableListOf<com.cinex.player.data.model.Category>()
            allCats += liveCats.mapIndexed { index, it -> com.cinex.player.data.model.Category(it.category_id, it.category_name, "LIVE_TV", playlistUrl, orderIndex = index) }
            allCats += vodCats.mapIndexed { index, it -> com.cinex.player.data.model.Category(it.category_id, it.category_name, "MOVIE", playlistUrl, orderIndex = index) }
            allCats += seriesCats.mapIndexed { index, it -> com.cinex.player.data.model.Category(it.category_id, it.category_name, "SERIES", playlistUrl, orderIndex = index) }
            categoryDao.insertAll(allCats)

            val liveCatsMap = liveCats.associateBy { it.category_id }
            val vodCatsMap = vodCats.associateBy { it.category_id }
            val seriesCatsMap = seriesCats.associateBy { it.category_id }
            onProgress(40, 20, 20, "Buscando canais ao vivo...")
            val liveStreams = api.getLiveStreams(user, pass)
            
            val liveChannels = mutableListOf<Channel>()

            liveStreams.forEachIndexed { index, stream ->
                val catName = liveCatsMap[stream.category_id]?.category_name ?: "Live"
                
                liveChannels.add(
                    Channel(
                        name = stream.name,
                        logoUrl = stream.stream_icon,
                        groupTitle = catName,
                        categoryId = stream.category_id,
                        streamUrl = "${baseUrl}live/$user/$pass/${stream.stream_id}.ts",
                        category = "LIVE_TV",
                        playlistUrl = playlistUrl,
                        orderIndex = index,
                        remoteId = "live_${stream.stream_id}"
                    )
                )
            }

            if (liveChannels.isNotEmpty()) {
                liveChannels.chunked(500).forEach { channelDao.insertAll(it) }
            }

            // 2. Filmes (VOD)
            onProgress(100, 40, 20, "Buscando filmes...")
            val vodStreams = api.getVodStreams(user, pass)
            val movieChannels = vodStreams.mapIndexed { index, it ->
                val ext = it.container_extension ?: "mp4"
                Channel(
                    name = it.name,
                    logoUrl = it.stream_icon,
                    groupTitle = vodCatsMap[it.category_id]?.category_name ?: "VOD",
                    categoryId = it.category_id,
                    streamUrl = "${baseUrl}movie/$user/$pass/${it.stream_id}.$ext",
                    category = "MOVIE",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "vod_${it.stream_id}"
                )
            }
            movieChannels.chunked(500).forEach { channelDao.insertAll(it) }

            // 3. Séries
            onProgress(100, 100, 40, "Buscando séries...")
            val seriesList = api.getSeries(user, pass)
            val seriesChannels = seriesList.mapIndexed { index, it ->
                Channel(
                    name = it.name,
                    logoUrl = it.cover,
                    groupTitle = seriesCatsMap[it.category_id]?.category_name ?: "Séries",
                    categoryId = it.category_id,
                    streamUrl = "", // Séries precisam de busca de episódios depois
                    category = "SERIES",
                    seriesName = it.name,
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "series_${it.series_id}"
                )
            }
            seriesChannels.chunked(500).forEach { channelDao.insertAll(it) }

            Result.success(Unit)
        } catch (_: Exception) {
            Result.failure(Exception("Failed to sync Xtream Codes playlist"))
        }
    }

    private val tmdbApiKey = "4f4a90cce11b368ad0235f2b82ba672a"

    suspend fun updateFavorite(channelId: Int, isFav: Boolean) = withContext(Dispatchers.IO) {
        channelDao.updateFavorite(channelId, isFav)
    }

    suspend fun updateResumePosition(channelId: Int, position: Long, duration: Long) {
        channelDao.updateResumePosition(channelId, position, duration)
    }

    suspend fun enrichChannelWithTmdb(channel: Channel) = withContext(Dispatchers.IO) {
        try {
            val query = channel.name.replace(Regex("\\(\\d{4}\\)"), "").trim()
            val result = if (channel.category == "MOVIE") {
                tmdbApi.searchMovie(tmdbApiKey, query).results.firstOrNull()
            } else {
                tmdbApi.searchSeries(tmdbApiKey, channel.seriesName ?: query).results.firstOrNull()
            }

            result?.let { tmdbResult ->
                val details = if (channel.category == "MOVIE") {
                    tmdbApi.getMovieDetails(tmdbResult.id, tmdbApiKey)
                } else {
                    tmdbApi.getTvDetails(tmdbResult.id, tmdbApiKey)
                }

                val posterUrl = "https://image.tmdb.org/t/p/w500${details.poster_path}"
                val backdropUrl = "https://image.tmdb.org/t/p/original${details.backdrop_path}"
                val cast = details.credits?.cast?.take(10)?.joinToString(", ") { it.name }
                val year = tmdbResult.release_date?.take(4) ?: tmdbResult.first_air_date?.take(4)
                
                // Busca o primeiro trailer do YouTube
                val trailerKey = details.videos?.results?.find { it.site == "YouTube" && it.type == "Trailer" }?.key 
                    ?: details.videos?.results?.find { it.site == "YouTube" }?.key
                val trailerUrl = if (trailerKey != null) "https://www.youtube.com/watch?v=$trailerKey" else null

                if (channel.category == "SERIES" && channel.seriesName != null) {
                    // PagingSource doesn't have first(), we need a List or Flow. 
                    // Use a direct query for episodes list during enrichment.
                    val episodes = channelDao.getEpisodesForSeriesList(channel.seriesName, channel.playlistUrl)
                    episodes.forEach { ep ->
                        channelDao.updateTmdbInfo(
                            ep.id,
                            tmdbResult.vote_average,
                            details.overview,
                            posterUrl,
                            backdropUrl,
                            year,
                            cast,
                            trailerUrl
                        )
                    }
                } else {
                    channelDao.updateTmdbInfo(
                        channel.id,
                        tmdbResult.vote_average,
                        details.overview,
                        posterUrl,
                        backdropUrl,
                        year,
                        cast,
                        trailerUrl
                    )
                }
            }
        } catch (ignored: Exception) {
            // Ignorado propositalmente para não travar o sync
        }
    }

    suspend fun deletePlaylist(playlist: com.cinex.player.data.model.Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun clearChannels(url: String? = null) = withContext(Dispatchers.IO) {
        if (url != null) {
            channelDao.clearByPlaylist(url)
        } else {
            channelDao.clearAll()
        }
    }

    suspend fun syncPlaylistDelta(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Existing Data from DB
            val existingChannels = channelDao.getAllByPlaylist(url)
            val existingCategories = categoryDao.getAllByPlaylist(url)
            
            val existingChannelMap = existingChannels.associateBy { it.remoteId }
            val existingCategoryMap = existingCategories.associateBy { it.id }

            // 2. Clear IDs for internal comparisons
            // 3. Fetch New Data from Server
            val (newCategories, newChannels) = fetchNewData(url) ?: return@withContext Result.failure(Exception("Failed to fetch data"))

            // 4. Diff Categories
            val categoriesToInsert = mutableListOf<com.cinex.player.data.model.Category>()
            val seenCategoryIds = mutableSetOf<String>()

            newCategories.forEach { newCat ->
                seenCategoryIds.add(newCat.id)
                val existingCat = existingCategoryMap[newCat.id]
                if (existingCat == null || existingCat.name != newCat.name || existingCat.orderIndex != newCat.orderIndex) {
                    categoriesToInsert.add(newCat)
                }
            }
            val categoriesToDelete = existingCategories.filter { it.id !in seenCategoryIds }.map { it.id }

            // 5. Diff Channels
            val channelsToUpsert = mutableListOf<Channel>()
            val seenRemoteIds = mutableSetOf<String>()

            newChannels.forEach { newChannel ->
                seenRemoteIds.add(newChannel.remoteId)
                val existing = existingChannelMap[newChannel.remoteId]
                if (existing == null) {
                    channelsToUpsert.add(newChannel)
                } else {
                    // Check if metadata changed
                    val metadataChanged = existing.name != newChannel.name ||
                            existing.streamUrl != newChannel.streamUrl ||
                            existing.categoryId != newChannel.categoryId ||
                            existing.logoUrl != newChannel.logoUrl ||
                            existing.orderIndex != newChannel.orderIndex

                    if (metadataChanged) {
                        // Preserve local fields (isFavorite, resumePosition, etc)
                        channelsToUpsert.add(newChannel.copy(
                            id = existing.id,
                            isFavorite = existing.isFavorite,
                            resumePosition = existing.resumePosition,
                            totalDuration = existing.totalDuration,
                            tmdbRating = existing.tmdbRating,
                            tmdbSynopsis = existing.tmdbSynopsis,
                            bannerUrl = existing.bannerUrl,
                            tmdbYear = existing.tmdbYear,
                            castMembers = existing.castMembers,
                            trailerUrl = existing.trailerUrl
                        ))
                    }
                }
            }
            val channelsToDelete = existingChannels.filter { it.remoteId !in seenRemoteIds }.map { it.remoteId }

            // 6. Apply Changes in Transaction (Simplified for now)
            if (categoriesToDelete.isNotEmpty()) categoryDao.clearByPlaylist(url) // Simplified: could be more granular but for safety
            if (categoriesToInsert.isNotEmpty() || categoriesToDelete.isNotEmpty()) {
                 // Resaving all categories to ensure orderIndex is correct if many changed
                 categoryDao.insertAll(newCategories)
            }

            if (channelsToDelete.isNotEmpty()) {
                channelDao.deleteMultipleByRemoteId(url, channelsToDelete)
            }
            if (channelsToUpsert.isNotEmpty()) {
                channelsToUpsert.chunked(500).forEach { channelDao.insertAll(it) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun fetchNewData(url: String): Pair<List<com.cinex.player.data.model.Category>, List<Channel>>? {
        val uri = android.net.Uri.parse(url)
        val username = uri.getQueryParameter("username")
        val password = uri.getQueryParameter("password")
        val host = uri.host
        val scheme = uri.scheme
        val port = uri.port

        if (username != null && password != null && host != null) {
            val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
            return fetchXtreamData(baseUrl, username, password, url)
        } else {
            return fetchM3UData(url)
        }
    }

    private suspend fun fetchXtreamData(baseUrl: String, user: String, pass: String, playlistUrl: String): Pair<List<com.cinex.player.data.model.Category>, List<Channel>>? {
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamCodesApi::class.java)

        val liveCats = try { api.getLiveCategories(user, pass) } catch (e: Exception) { emptyList() }
        val vodCats = try { api.getVodCategories(user, pass) } catch (e: Exception) { emptyList() }
        val seriesCats = try { api.getSeriesCategories(user, pass) } catch (e: Exception) { emptyList() }

        val allCatsArr = mutableListOf<com.cinex.player.data.model.Category>()
        allCatsArr += liveCats.mapIndexed { index, it -> com.cinex.player.data.model.Category(it.category_id, it.category_name, "LIVE_TV", playlistUrl, orderIndex = index) }
        allCatsArr += vodCats.mapIndexed { index, it -> com.cinex.player.data.model.Category(it.category_id, it.category_name, "MOVIE", playlistUrl, orderIndex = index) }
        allCatsArr += seriesCats.mapIndexed { index, it -> com.cinex.player.data.model.Category(it.category_id, it.category_name, "SERIES", playlistUrl, orderIndex = index) }

        val liveCatsMap = liveCats.associateBy { it.category_id }
        val vodCatsMap = vodCats.associateBy { it.category_id }
        val seriesCatsMap = seriesCats.associateBy { it.category_id }

        val liveStreams = try { api.getLiveStreams(user, pass) } catch (e: Exception) { emptyList() }
        val vodStreams = try { api.getVodStreams(user, pass) } catch (e: Exception) { emptyList() }
        val seriesList = try { api.getSeries(user, pass) } catch (e: Exception) { emptyList() }

        val allChannels = mutableListOf<Channel>()
        
        allChannels += liveStreams.mapIndexed { index, stream ->
            val catName = liveCatsMap[stream.category_id]?.category_name ?: "Live"
            Channel(
                name = stream.name,
                logoUrl = stream.stream_icon,
                groupTitle = catName,
                categoryId = stream.category_id,
                streamUrl = "${baseUrl}live/$user/$pass/${stream.stream_id}.ts",
                category = "LIVE_TV",
                playlistUrl = playlistUrl,
                orderIndex = index,
                remoteId = "live_${stream.stream_id}"
            )
        }

        allChannels += vodStreams.mapIndexed { index, it ->
            val ext = it.container_extension ?: "mp4"
            Channel(
                name = it.name,
                logoUrl = it.stream_icon,
                groupTitle = vodCatsMap[it.category_id]?.category_name ?: "VOD",
                categoryId = it.category_id,
                streamUrl = "${baseUrl}movie/$user/$pass/${it.stream_id}.$ext",
                category = "MOVIE",
                playlistUrl = playlistUrl,
                orderIndex = index,
                remoteId = "vod_${it.stream_id}"
            )
        }

        allChannels += seriesList.mapIndexed { index, it ->
            Channel(
                name = it.name,
                logoUrl = it.cover,
                groupTitle = seriesCatsMap[it.category_id]?.category_name ?: "Séries",
                categoryId = it.category_id,
                streamUrl = "",
                category = "SERIES",
                seriesName = it.name,
                playlistUrl = playlistUrl,
                orderIndex = index,
                remoteId = "series_${it.series_id}"
            )
        }

        return Pair(allCatsArr, allChannels)
    }

    private suspend fun fetchM3UData(url: String): Pair<List<com.cinex.player.data.model.Category>, List<Channel>>? {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        
        val parsedChannels = body.charStream().buffered().use { reader -> parser.parse(reader, url) }
        
        if (parsedChannels.isEmpty()) {
            return null // Treat empty list of channels as a failure
        }

        val m3uCategories = parsedChannels.map { it.groupTitle }.distinct().mapIndexed { index, catName ->
            com.cinex.player.data.model.Category(
                id = catName,
                name = catName,
                type = "LIVE_TV",
                playlistUrl = url,
                orderIndex = index
            )
        }

        return Pair(m3uCategories, parsedChannels)
    }

    suspend fun clearHistory() {
        channelDao.resetAllResumePositions()
    }
}
