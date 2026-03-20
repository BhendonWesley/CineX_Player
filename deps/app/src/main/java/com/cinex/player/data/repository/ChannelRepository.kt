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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    private val categoryDao: com.cinex.player.data.local.CategoryDao,
    private val parser: M3UParser,
    private val epgDao: com.cinex.player.data.local.EpgDao,
    private val epgParser: com.cinex.player.data.parser.EpgParser,
    private val okHttpClient: OkHttpClient,
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

    fun activatePlaylist(url: String) {
        _activePlaylistUrl.value = url
        // Enriquecimento lazy: feito por demanda via onChannelVisible no ViewModel
        // O enriquecimento em massa acontece apenas durante syncPlaylist (primeiro uso)
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
            else Pager(PagingConfig(
                pageSize = 30,
                initialLoadSize = 30,
                prefetchDistance = 10,
                enablePlaceholders = false
            )) {
                when (categoryId) {
                    "Tudo" -> channelDao.getChannelsByCategory("LIVE_TV", url)
                    "Favorito", "Favoritos" -> channelDao.getFavoritesPaged("LIVE_TV", url)
                    else -> channelDao.getChannelsByCategoryIdPaged(categoryId, url)
                }
            }.flow
        }
    }

    fun getPagedMoviesByCategory(categoryId: String): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50)) {
                when (categoryId) {
                    "Tudo" -> channelDao.getChannelsByCategory("MOVIE", url)
                    "Favorito", "Favoritos" -> channelDao.getFavoritesPaged("MOVIE", url)
                    else -> channelDao.getChannelsByCategoryIdPaged(categoryId, url)
                }
            }.flow
        }
    }

    fun getPagedSeriesByCategory(categoryId: String): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50)) {
                when (categoryId) {
                    "Tudo" -> channelDao.getUniqueSeries(url)
                    "Favorito", "Favoritos" -> channelDao.getFavoriteSeriesPaged(url)
                    else -> channelDao.getUniqueSeriesByCategoryId(categoryId, url)
                }
            }.flow
        }
    }

    fun getFeaturedContent(url: String): Flow<List<Channel>> {
        return channelDao.getFeaturedContent(url)
    }

    suspend fun syncPlaylist(
        url: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _activePlaylistUrl.value = null 
            onProgress(10, 10, 10, "Conectando ao servidor...")
            onProgress(10, 10, 10, "Baixando lista de reprodução...")
            
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

            onProgress(10, 10, 10, "Baixando lista de reprodução...")
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
            val body = response.body ?: return@withContext Result.failure(Exception("Empty Body"))
            
            val (parsedChannels, epgUrl) = body.charStream().buffered().use { reader -> parser.parse(reader, url) }
            
            if (parsedChannels.isNotEmpty()) {
                onProgress(50, 50, 50, "Sincronizando metadados...")
                
                // Preservar dados TMDB existentes
                val existingTmdbData = channelDao.getAllByPlaylist(url).associateBy({ it.remoteId }, { it })

                onProgress(50, 50, 50, "Limpando dados antigos...")
                channelDao.clearByPlaylist(url)
                categoryDao.clearByPlaylist(url) 
                
                val channelsWithOldData = parsedChannels.mapIndexed { index, channel ->
                    val old = existingTmdbData[channel.remoteId]
                    channel.copy(
                        orderIndex = index,
                        categoryId = channel.groupTitle,
                        tmdbRating = old?.tmdbRating,
                        tmdbSynopsis = old?.tmdbSynopsis,
                        posterUrl = old?.posterUrl,
                        bannerUrl = old?.bannerUrl,
                        tmdbYear = old?.tmdbYear,
                        castMembers = old?.castMembers,
                        trailerUrl = old?.trailerUrl
                    )
                }

                if (epgUrl != null) {
                    val currentPlaylist = playlistDao.getPlaylistByUrl(url)
                    if (currentPlaylist != null) {
                        playlistDao.insertPlaylist(currentPlaylist.copy(epgUrl = epgUrl))
                    }
                }

                val channelsByGroup = parsedChannels.groupBy { it.groupTitle }
                val m3uCategories = channelsByGroup.entries.mapIndexed { index, (catName, channels) ->
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

                channelsWithOldData.chunked(1000).forEach { chunk -> channelDao.insertAll(chunk) }

                epgUrl?.let { syncEpg(it) }

                _activePlaylistUrl.value = url
                onProgress(100, 100, 100, "Iniciando em segundo plano...")
            } else {
                return@withContext Result.failure(Exception("Nenhum conteúdo encontrado na lista. Verifique a URL."))
            }

            repositoryScope.launch {
                val initialFeatured = channelDao.getFeaturedContent(url).first().take(5)
                initialFeatured.forEach { channel ->
                    enrichChannelWithTmdb(channel)
                }
                
                val moviesToEnrich = channelDao.getMoviesToEnrich(url)
                val seriesToEnrich = channelDao.getSeriesToEnrich(url)
                
                val totalMovies = moviesToEnrich.size
                if (totalMovies > 0) {
                    val movieCount = AtomicInteger(0)
                    // Processamento em blocos de 5 para não estourar memória
                    moviesToEnrich.chunked(5).forEach { chunk ->
                        coroutineScope {
                            chunk.forEach { channel ->
                                launch {
                                    enrichChannelWithTmdb(channel)
                                    val current = movieCount.incrementAndGet()
                                    if (current % 10 == 0 || current == totalMovies) {
                                        val pct = ((current.toDouble() / totalMovies.toDouble()) * 100.0).toInt()
                                        onProgress(100, pct, 0, "Segundo plano: Filmes $pct%")
                                    }
                                }
                            }
                        }
                        delay(200) // Pequeno fôlego entre blocos
                    }
                }

                val totalSeries = seriesToEnrich.size
                if (totalSeries > 0) {
                    val seriesCount = AtomicInteger(0)
                    seriesToEnrich.chunked(5).forEach { chunk ->
                        coroutineScope {
                            chunk.forEach { channel ->
                                launch {
                                    enrichChannelWithTmdb(channel)
                                    val current = seriesCount.incrementAndGet()
                                    if (current % 5 == 0 || current == totalSeries) {
                                        val pct = ((current.toDouble() / totalSeries.toDouble()) * 100.0).toInt()
                                        onProgress(100, 100, pct, "Segundo plano: Séries $pct%")
                                    }
                                }
                            }
                        }
                        delay(200)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun syncPlaylistDelta(): Result<Unit> = withContext(Dispatchers.IO) {
        Result.success(Unit)
    }

    suspend fun syncXtream(
        baseUrl: String,
        user: String,
        pass: String,
        playlistUrl: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = coroutineScope {
        try {
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(XtreamCodesApi::class.java)
            
            onProgress(10, 10, 10, "Conectando ao servidor...")
            
            // Busca metadados existentes para preservar capas
            val existingTmdbData = withContext(Dispatchers.IO) {
                channelDao.getAllByPlaylist(playlistUrl).associateBy({ it.remoteId }, { it })
            }

            // BUSCA PARALELA DE CATEGORIAS
            val liveCatsDeferred = async { try { api.getLiveCategories(user, pass) } catch (_: Exception) { emptyList() } }
            val vodCatsDeferred = async { try { api.getVodCategories(user, pass) } catch (_: Exception) { emptyList() } }
            val seriesCatsDeferred = async { try { api.getSeriesCategories(user, pass) } catch (_: Exception) { emptyList() } }

            val liveCats = liveCatsDeferred.await()
            val vodCats = vodCatsDeferred.await()
            val seriesCats = seriesCatsDeferred.await()

            onProgress(20, 20, 20, "Limpando cache antigo...")
            withContext(Dispatchers.IO) {
                channelDao.clearByPlaylist(playlistUrl)
                categoryDao.clearByPlaylist(playlistUrl)
                
                val allCats = mutableListOf<com.cinex.player.data.model.Category>()
                allCats += liveCats.mapIndexed { index, cat -> com.cinex.player.data.model.Category(cat.category_id, cat.category_name, "LIVE_TV", playlistUrl, orderIndex = index) }
                allCats += vodCats.mapIndexed { index, cat -> com.cinex.player.data.model.Category(cat.category_id, cat.category_name, "MOVIE", playlistUrl, orderIndex = index) }
                allCats += seriesCats.mapIndexed { index, cat -> com.cinex.player.data.model.Category(cat.category_id, cat.category_name, "SERIES", playlistUrl, orderIndex = index) }
                categoryDao.insertAll(allCats)
            }

            val liveCatsMap = liveCats.associateBy { it.category_id }
            val vodCatsMap = vodCats.associateBy { it.category_id }
            val seriesCatsMap = seriesCats.associateBy { it.category_id }

            onProgress(30, 30, 30, "Baixando conteúdo...")

            // BUSCA PARALELA DE CONTEÚDO (Otimização Real de Velocidade)
            val liveStreamsDeferred = async { api.getLiveStreams(user, pass) }
            val vodStreamsDeferred = async { api.getVodStreams(user, pass) }
            val seriesListDeferred = async { api.getSeries(user, pass) }

            val liveStreams = liveStreamsDeferred.await()
            val vodStreams = vodStreamsDeferred.await()
            val seriesList = seriesListDeferred.await()

            // MAPEAMENTO E INSERÇÃO (Sequencial para evitar lock de DB, mas os dados já estão na memória)
            
            // 1. Canais ao Vivo
            onProgress(50, 40, 40, "Salvando TV ao vivo...")
            val liveChannels = liveStreams.mapIndexed { index, stream ->
                val old = existingTmdbData["live_${stream.stream_id}"]
                Channel(
                    name = stream.name,
                    logoUrl = stream.stream_icon,
                    groupTitle = liveCatsMap[stream.category_id]?.category_name ?: "Live",
                    categoryId = stream.category_id,
                    streamUrl = "${baseUrl}live/$user/$pass/${stream.stream_id}.ts",
                    category = "LIVE_TV",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "live_${stream.stream_id}",
                    tvgId = stream.epg_channel_id,
                    tmdbRating = old?.tmdbRating,
                    tmdbSynopsis = old?.tmdbSynopsis,
                    posterUrl = old?.posterUrl,
                    bannerUrl = old?.bannerUrl,
                    tmdbYear = old?.tmdbYear,
                    castMembers = old?.castMembers,
                    trailerUrl = old?.trailerUrl
                )
            }
            if (liveChannels.isNotEmpty()) {
                withContext(Dispatchers.IO) { liveChannels.chunked(500).forEach { channelDao.insertAll(it) } }
            }

            // 2. Filmes (VOD)
            onProgress(100, 70, 60, "Salvando Filmes...")
                val movieStreams = vodStreams.mapIndexed { index, m ->
                val ext = m.container_extension ?: "mp4"
                val old = existingTmdbData["vod_${m.stream_id}"]
                Channel(
                    name = m.name,
                    logoUrl = m.stream_icon,
                    groupTitle = vodCatsMap[m.category_id]?.category_name ?: "VOD",
                    categoryId = m.category_id,
                    streamUrl = "${baseUrl}movie/$user/$pass/${m.stream_id}.$ext",
                    category = "MOVIE",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "vod_${m.stream_id}",
                    tmdbRating = old?.tmdbRating,
                    tmdbSynopsis = old?.tmdbSynopsis,
                    posterUrl = old?.posterUrl,
                    bannerUrl = old?.bannerUrl,
                    tmdbYear = old?.tmdbYear,
                    castMembers = old?.castMembers,
                    trailerUrl = old?.trailerUrl
                )
            }
            if (movieStreams.isNotEmpty()) {
                withContext(Dispatchers.IO) { movieStreams.chunked(500).forEach { chunk -> channelDao.insertAll(chunk) } }
            }

            // 3. Séries
            onProgress(100, 100, 90, "Salvando Séries...")
            val seriesStreams = seriesList.mapIndexed { index, s ->
                val old = existingTmdbData["series_${s.series_id}"]
                Channel(
                    name = s.name,
                    logoUrl = s.cover,
                    groupTitle = seriesCatsMap[s.category_id]?.category_name ?: "SÉRIES",
                    categoryId = s.category_id,
                    streamUrl = "",
                    category = "SERIES",
                    seriesName = s.name,
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "series_${s.series_id}",
                    tmdbRating = old?.tmdbRating,
                    tmdbSynopsis = old?.tmdbSynopsis,
                    posterUrl = old?.posterUrl,
                    bannerUrl = old?.bannerUrl,
                    tmdbYear = old?.tmdbYear,
                    castMembers = old?.castMembers,
                    trailerUrl = old?.trailerUrl
                )
            }
            if (seriesStreams.isNotEmpty()) {
                withContext(Dispatchers.IO) { seriesStreams.chunked(500).forEach { chunk -> channelDao.insertAll(chunk) } }
            }

            onProgress(100, 100, 100, "Concluído!")
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private val tmdbApiKey = "4f4a90cce11b368ad0235f2b82ba672a"

    suspend fun updateFavorite(channelId: Int, isFav: Boolean) = withContext(Dispatchers.IO) {
        channelDao.updateFavorite(channelId, isFav)
    }

    suspend fun updateResumePosition(channelId: Int, position: Long, duration: Long) {
        channelDao.updateResumePosition(channelId, position, duration)
    }

    private fun pickBestTrailer(videos: List<com.cinex.player.data.network.TmdbVideo>): String? {
        val yt = videos.filter { it.site == "YouTube" }
        // 1. Trailer dublado (PT-BR)
        yt.find { it.type == "Trailer" && it.name.contains("dublado", ignoreCase = true) }
            ?.let { return "https://www.youtube.com/watch?v=${it.key}" }
        // 2. Qualquer vídeo dublado
        yt.find { it.name.contains("dublado", ignoreCase = true) }
            ?.let { return "https://www.youtube.com/watch?v=${it.key}" }
        // 3. Trailer em PT-BR pelo código de idioma
        yt.find { it.type == "Trailer" && it.iso_639_1 == "pt" }
            ?.let { return "https://www.youtube.com/watch?v=${it.key}" }
        // 4. Trailer legendado
        yt.find { it.type == "Trailer" && it.name.contains("legendado", ignoreCase = true) }
            ?.let { return "https://www.youtube.com/watch?v=${it.key}" }
        // 5. Qualquer trailer
        yt.find { it.type == "Trailer" }
            ?.let { return "https://www.youtube.com/watch?v=${it.key}" }
        // 6. Qualquer vídeo YouTube como fallback
        return yt.firstOrNull()?.let { "https://www.youtube.com/watch?v=${it.key}" }
    }

    suspend fun enrichChannelWithTmdb(channel: Channel) = withContext(Dispatchers.IO) {
        try {
            val rawName = channel.name
            
            // Extrair ano (ex: 2003) se presente no título
            val yearMatch = Regex("(?i)\\(?(\\d{4})\\)?").find(rawName)
            val extractedYear = yearMatch?.groupValues?.get(1)

            // Limpeza de query IPTV
            val query = rawName
                .replace(Regex("(?i)\\(?\\d{4}\\)?"), "") // Remove ano do título para a query de texto
                .replace(Regex("(?i)\\b(fhd|hd|sd|4k|dual|legendado|dublado|multi|brrip|hdtv|web-dl|bluray|h264|h265|x264|x265|1080p|720p|480p)\\b"), "")
                .replace(Regex("[|\\-\\[\\]]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

            val isMovie = channel.category == "MOVIE"
            val seriesQuery = if (!isMovie) {
                (channel.seriesName ?: query)
                    .replace(Regex("(?i)s\\d+e\\d+.*"), "")
                    .replace(Regex("(?i)\\(?\\d{4}\\)?"), "")
                    .trim()
            } else query

            val searchResponse = if (isMovie) {
                tmdbApi.searchMovie(tmdbApiKey, query, year = extractedYear)
            } else {
                tmdbApi.searchSeries(tmdbApiKey, seriesQuery, year = extractedYear)
            }

            // Busca o resultado com ano exato
            fun findExactYear(results: List<com.cinex.player.data.network.TmdbMovieResult>): com.cinex.player.data.network.TmdbMovieResult? =
                results.find { res ->
                    val resYear = (res.release_date ?: res.first_air_date)?.take(4)
                    resYear == extractedYear
                }

            val tmdbResult = if (extractedYear != null) {
                // 1ª tentativa: busca com filtro de ano (TMDB pode ignorar o filtro)
                findExactYear(searchResponse.results)
                    ?: run {
                        // 2ª tentativa: busca sem filtro de ano + filtragem rigorosa no cliente
                        val retryResponse = if (isMovie) {
                            tmdbApi.searchMovie(tmdbApiKey, query)
                        } else {
                            tmdbApi.searchSeries(tmdbApiKey, seriesQuery)
                        }
                        findExactYear(retryResponse.results)
                        // Se ainda não encontrou, retorna null — melhor sem dados que com dados errados
                    }
            } else {
                searchResponse.results.firstOrNull()
            }

            tmdbResult?.let { bestMatch ->
                val details = if (channel.category == "MOVIE") {
                    tmdbApi.getMovieDetails(bestMatch.id, tmdbApiKey)
                } else {
                    tmdbApi.getTvDetails(bestMatch.id, tmdbApiKey)
                }

                val posterUrl = details.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                val backdropUrl = details.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                val cast = details.credits?.cast?.take(10)?.joinToString(", ") { it.name }
                val year = tmdbResult.release_date?.take(4) ?: tmdbResult.first_air_date?.take(4)
                
                // Busca vídeos com pt-BR + en-US para melhor cobertura de trailers
                val allVideos = try {
                    if (channel.category == "MOVIE") {
                        tmdbApi.getMovieVideos(bestMatch.id, tmdbApiKey).results
                    } else {
                        tmdbApi.getTvVideos(bestMatch.id, tmdbApiKey).results
                    }
                } catch (_: Exception) {
                    details.videos?.results ?: emptyList()
                }
                val trailerUrl = pickBestTrailer(allVideos)

                if (channel.category == "SERIES" && channel.seriesName != null) {
                    val episodes = channelDao.getEpisodesForSeriesList(channel.seriesName, channel.playlistUrl)
                    episodes.forEach { ep ->
                        channelDao.updateTmdbInfo(
                            ep.id,
                            ep.tmdbRating ?: tmdbResult.vote_average,
                            ep.tmdbSynopsis ?: details.overview, // preserva sinopse individual do episódio
                            ep.posterUrl ?: posterUrl, // preserva poster individual se existir
                            ep.bannerUrl, // preserva o still individual do episódio
                            ep.tmdbYear ?: year,
                            cast,
                            trailerUrl
                        )
                    }
                    
                    // Busca thumbnails e sinopses específicas de episódios
                    details.seasons?.forEach { season ->
                        try {
                            val seasonDetails = tmdbApi.getSeasonDetails(bestMatch.id, season.season_number, tmdbApiKey)
                            seasonDetails.episodes.forEach { tmdbEp ->
                                val stillUrl = tmdbEp.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                                if (stillUrl != null) {
                                    channelDao.updateEpisodeStillAndSynopsis(
                                        channel.seriesName!!,
                                        season.season_number,
                                        tmdbEp.episode_number,
                                        stillUrl,
                                        tmdbEp.overview,
                                        channel.playlistUrl
                                    )
                                } else if (!tmdbEp.overview.isNullOrBlank()) {
                                    // Mesmo sem still, salva a sinopse do episódio
                                    channelDao.updateEpisodeStillAndSynopsis(
                                        channel.seriesName!!,
                                        season.season_number,
                                        tmdbEp.episode_number,
                                        "",
                                        tmdbEp.overview,
                                        channel.playlistUrl
                                    )
                                }
                            }
                        } catch (_: Exception) {}
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
        } catch (ignored: Exception) {}
    }

    suspend fun syncEpg(url: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext
            
            val body = response.body ?: return@withContext
            val (epgChannels, epgPrograms) = body.byteStream().use { epgParser.parse(it, url) }
            
            if (epgChannels.isNotEmpty()) {
                epgDao.clearChannelsByPlaylist(url)
                epgDao.insertChannels(epgChannels)
            }
            
            if (epgPrograms.isNotEmpty()) {
                epgDao.clearProgramsByPlaylist(url)
                epgDao.insertPrograms(epgPrograms)
                epgDao.clearOldPrograms(System.currentTimeMillis() - 86400000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentProgram(tvgId: String): Flow<com.cinex.player.data.model.EpgProgram?> {
        return epgDao.getCurrentProgram(tvgId, System.currentTimeMillis())
    }

    fun getUpcomingPrograms(tvgId: String): Flow<List<com.cinex.player.data.model.EpgProgram>> {
        return epgDao.getUpcomingPrograms(tvgId, System.currentTimeMillis())
    }

    suspend fun getShortEpg(streamId: Int): Result<com.cinex.player.data.network.XtreamEpgResponse> = withContext(Dispatchers.IO) {
        val url = _activePlaylistUrl.value ?: return@withContext Result.failure(Exception("No active playlist"))
        try {
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            val host = uri.host
            val scheme = uri.scheme
            val port = uri.port
            
            if (username != null && password != null && host != null) {
                val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                val api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(XtreamCodesApi::class.java)
                
                Result.success(api.getShortEpg(username, password, streamId))
            } else {
                Result.failure(Exception("Not an Xtream playlist"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun clearHistory() {
        channelDao.resetAllResumePositions()
    }

    /** Limpa todos os dados locais (canais, playlists, categorias) — usado quando dispositivo é removido do painel */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        channelDao.clearAll()
        playlistDao.clearAll()
        categoryDao.clearAll()
    }

    suspend fun getNextEpisode(channel: Channel): Channel? {
        if (channel.category != "SERIES" || channel.seriesName == null) return null
        return channelDao.getNextEpisode(
            seriesName = channel.seriesName,
            currentSeason = channel.seasonNumber ?: 1,
            currentEpisode = channel.episodeNumber ?: 0,
            url = channel.playlistUrl
        )
    }

    suspend fun fetchAndStoreEpisodes(seriesId: Int, seriesName: String) = withContext(Dispatchers.IO) {
        val url = _activePlaylistUrl.value ?: return@withContext
        try {
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            val host = uri.host
            val scheme = uri.scheme
            val port = uri.port
            
            if (username != null && password != null && host != null) {
                val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                val api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(XtreamCodesApi::class.java)
                
                val response = api.getSeriesInfo(username, password, seriesId)

                // Preservar dados TMDB existentes (stills, posters, etc.)
                val existingEpisodes = channelDao.getEpisodesForSeriesList(seriesName, url)
                    .associateBy { it.remoteId }

                response.episodes?.forEach { (seasonNumStr, episodeList) ->
                    val seasonNum = seasonNumStr.toIntOrNull() ?: 1
                    val channels = episodeList.map { ep ->
                        val remoteId = "series_ep_${ep.id}"
                        val old = existingEpisodes[remoteId]
                        Channel(
                            name = ep.title,
                            logoUrl = ep.info?.movie_image,
                            groupTitle = "Episódios",
                            categoryId = "series_$seriesId",
                            streamUrl = "${baseUrl}series/$username/$password/${ep.id}.${ep.container_extension ?: "mp4"}",
                            category = "SERIES",
                            seriesName = seriesName,
                            seasonNumber = seasonNum,
                            episodeNumber = ep.episode_num,
                            playlistUrl = url,
                            remoteId = remoteId,
                            tmdbSynopsis = ep.info?.plot ?: old?.tmdbSynopsis,
                            tmdbRating = ep.info?.rating?.toDoubleOrNull() ?: old?.tmdbRating,
                            tmdbYear = ep.info?.release_date?.take(4) ?: old?.tmdbYear,
                            bannerUrl = old?.bannerUrl,
                            posterUrl = old?.posterUrl,
                            castMembers = old?.castMembers,
                            trailerUrl = old?.trailerUrl
                        )
                    }

                    // Inserir episódios (conflito REPLACE para atualizar metadados se já existirem)
                    channelDao.insertAll(channels)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
