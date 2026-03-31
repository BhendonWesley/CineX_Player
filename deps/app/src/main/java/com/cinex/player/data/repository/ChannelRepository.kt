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
import com.cinex.player.data.network.LiveStreamItem
import com.cinex.player.data.network.VodStreamItem
import com.cinex.player.data.network.SeriesItem
import com.cinex.player.data.network.XtreamCodesApi
import com.cinex.player.data.network.EpisodeExtraInfo
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

    fun observeAllLiveChannels(): Flow<List<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(emptyList())
            else channelDao.observeAllLiveChannels(url)
        }
    }

    fun observeAllMovies(): Flow<List<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(emptyList())
            else channelDao.observeAllMovies(url)
        }
    }

    fun observeAllSeries(): Flow<List<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(emptyList())
            else channelDao.observeAllSeries(url)
        }
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

    suspend fun hasEpisodesForSeries(seriesName: String): Boolean {
        val url = _activePlaylistUrl.value ?: return false
        return channelDao.countEpisodesForSeries(seriesName, url) > 0
    }

    suspend fun hasEpisodesWithoutStill(seriesName: String): Boolean {
        val url = _activePlaylistUrl.value ?: return false
        return channelDao.countEpisodesWithoutStill(seriesName, url) > 0
    }

    suspend fun hasEpisodesWithoutStill(seriesName: String, season: Int): Boolean {
        val url = _activePlaylistUrl.value ?: return false
        return channelDao.countEpisodesWithoutStillForSeason(seriesName, season, url) > 0
    }

    fun observeChannelByRemoteId(remoteId: String): Flow<Channel?> {
        val url = _activePlaylistUrl.value ?: return flowOf(null)
        return channelDao.observeChannelByRemoteId(remoteId, url)
    }

    fun observeEpisodesWithoutStillForSeason(seriesName: String, season: Int): Flow<Int> {
        val url = _activePlaylistUrl.value ?: return flowOf(0)
        return channelDao.observeEpisodesWithoutStillForSeason(seriesName, season, url)
    }

    fun getEpisodesBySeasonPaged(seriesName: String, season: Int): Flow<PagingData<Channel>> {
        val url = _activePlaylistUrl.value ?: return flowOf(PagingData.empty())
        return Pager(PagingConfig(pageSize = 50)) {
            channelDao.getEpisodesBySeasonPaged(seriesName, season, url)
        }.flow.cachedIn(repositoryScope)
    }

    fun observeEpisodesBySeason(seriesName: String, season: Int): Flow<List<Channel>> {
        val url = _activePlaylistUrl.value ?: return flowOf(emptyList())
        return channelDao.observeEpisodesBySeason(seriesName, season, url)
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
            else Pager(PagingConfig(pageSize = 50, initialLoadSize = 50, prefetchDistance = 10)) {
                when (categoryId) {
                    "Tudo" -> channelDao.getChannelsByCategory("MOVIE", url)
                    "Favorito", "Favoritos" -> channelDao.getFavoritesPaged("MOVIE", url)
                    "Continuar Assistindo" -> channelDao.getContinueWatchingPaged("MOVIE", url)
                    else -> channelDao.getChannelsByCategoryIdPaged(categoryId, url)
                }
            }.flow
        }
    }

    fun getPagedSeriesByCategory(categoryId: String): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50, initialLoadSize = 50, prefetchDistance = 10)) {
                when (categoryId) {
                    "Tudo" -> channelDao.getUniqueSeries(url)
                    "Favorito", "Favoritos" -> channelDao.getFavoriteSeriesPaged(url)
                    "Continuar Assistindo" -> channelDao.getContinueWatchingPaged("SERIES", url)
                    else -> channelDao.getUniqueSeriesByCategoryId(categoryId, url)
                }
            }.flow
        }
    }

    fun getFeaturedContent(url: String): Flow<List<Channel>> {
        return channelDao.getFeaturedContent(url)
    }

    suspend fun getTmdbDiagnostics(url: String): List<Channel> = withContext(Dispatchers.IO) {
        channelDao.getAllByPlaylist(url)
    }

    suspend fun countMoviesToEnrich(url: String): Int = withContext(Dispatchers.IO) {
        channelDao.getMoviesToEnrich(url).size
    }

    /**
     * Enriquece apenas N filmes aleatórios para popular a Home rapidamente.
     * Não tenta enriquecer o catálogo inteiro — só o necessário para mostrar banners.
     */
    suspend fun enrichRandomForHome(url: String, count: Int = 20) = withContext(Dispatchers.IO) {
        val movies = channelDao.getMoviesToEnrich(url).shuffled().take(count / 2)
        val series = channelDao.getSeriesToEnrich(url).shuffled().take(count / 2)
        val toEnrich = (movies + series).shuffled()
        android.util.Log.d("CineX-Home", "Enriquecendo ${toEnrich.size} conteúdos aleatórios (filmes+séries) para a Home...")
        toEnrich.chunked(10).forEach { chunk ->
            coroutineScope {
                chunk.forEach { channel ->
                    launch { enrichChannelWithTmdb(channel) }
                }
            }
        }
        val featuredAfter = channelDao.getFeaturedContent(url).first().size
        android.util.Log.d("CineX-Home", "Featured após enriquecimento rápido: $featuredAfter")
    }

    fun triggerBackgroundEnrichment(url: String) {
        launchBackgroundEnrichment(url) { _, _, _, status ->
            android.util.Log.d("CineX-Home", "Enrichment: $status")
        }
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
                    // Dispara enriquecimento TMDB em background (mesmo do path M3U)
                    launchBackgroundEnrichment(url, onProgress)
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
                
                // Preservar dados do usuário E dados TMDB existentes
                // TMDB será re-enriquecido em background, mas os dados antigos
                // garantem que a Home mostre banners imediatamente após resync
                val existingUserData = channelDao.getTmdbAndUserDataByPlaylist(url).associateBy { it.remoteId }

                onProgress(50, 50, 50, "Limpando dados antigos...")
                channelDao.clearByPlaylist(url)
                categoryDao.clearByPlaylist(url)

                val channelsWithOldData = parsedChannels.mapIndexed { index, channel ->
                    val old = existingUserData[channel.remoteId]
                    channel.copy(
                        orderIndex = index,
                        categoryId = channel.groupTitle,
                        resumePosition = old?.resumePosition ?: 0L,
                        totalDuration = old?.totalDuration ?: 0L,
                        isFavorite = old?.isFavorite ?: false,
                        tmdbRating = old?.tmdbRating,
                        tmdbSynopsis = old?.tmdbSynopsis,
                        posterUrl = old?.posterUrl ?: channel.posterUrl,
                        bannerUrl = old?.bannerUrl ?: channel.bannerUrl,
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
                playlistDao.updateLastSyncTime(url, System.currentTimeMillis())

                // Enriquecer 20 conteúdos aleatórios (filmes+séries) para a Home ter banners
                val featured = channelDao.getFeaturedContent(url).first()
                if (featured.isEmpty()) {
                    onProgress(100, 100, 100, "Buscando capas oficiais...")
                    val seedMovies = channelDao.getMoviesToEnrich(url).shuffled().take(10)
                    val seedSeries = channelDao.getSeriesToEnrich(url).shuffled().take(10)
                    (seedMovies + seedSeries).shuffled().chunked(10).forEach { chunk ->
                        coroutineScope {
                            chunk.forEach { channel ->
                                launch { enrichChannelWithTmdb(channel) }
                            }
                        }
                    }
                }

                onProgress(100, 100, 100, "Iniciando em segundo plano...")
            } else {
                return@withContext Result.failure(Exception("Nenhum conteúdo encontrado na lista. Verifique a URL."))
            }

            launchBackgroundEnrichment(url, onProgress)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Enriquecimento TMDB em background: primeiro os destaques da Home, depois filmes e séries.
     * Roda em repositoryScope para não bloquear o sync.
     */
    private fun launchBackgroundEnrichment(
        url: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ) {
        repositoryScope.launch {
            // Prioridade 1: enriquecer os featured da Home primeiro
            val initialFeatured = channelDao.getFeaturedContent(url).first().take(5)
            if (initialFeatured.isNotEmpty()) {
                initialFeatured.forEach { channel ->
                    enrichChannelWithTmdb(channel)
                }
            } else {
                // Sem featured ainda (dados TMDB vazios) — enriquecer os primeiros filmes
                // com alta paralelização para que a Home tenha banners o mais rápido possível
                android.util.Log.d("CineX-Home", "Seed: enriquecendo primeiros 50 filmes com paralelismo 10...")
                val seedMovies = channelDao.getMoviesToEnrich(url).take(50)
                seedMovies.chunked(10).forEach { chunk ->
                    coroutineScope {
                        chunk.forEach { channel ->
                            launch { enrichChannelWithTmdb(channel) }
                        }
                    }
                }
                android.util.Log.d("CineX-Home", "Seed concluído. Verificando featured...")
                val postSeedFeatured = channelDao.getFeaturedContent(url).first()
                android.util.Log.d("CineX-Home", "Featured após seed: ${postSeedFeatured.size}")
            }

            // Resto em background com blocos de 10 (mais rápido)
            val moviesToEnrich = channelDao.getMoviesToEnrich(url)
            val seriesToEnrich = channelDao.getSeriesToEnrich(url)

            val totalMovies = moviesToEnrich.size
            if (totalMovies > 0) {
                val movieCount = AtomicInteger(0)
                moviesToEnrich.chunked(10).forEach { chunk ->
                    coroutineScope {
                        chunk.forEach { channel ->
                            launch {
                                enrichChannelWithTmdb(channel)
                                val current = movieCount.incrementAndGet()
                                if (current % 20 == 0 || current == totalMovies) {
                                    val pct = ((current.toDouble() / totalMovies.toDouble()) * 100.0).toInt()
                                    onProgress(100, pct, 0, "Segundo plano: Filmes $pct%")
                                }
                            }
                        }
                    }
                    delay(50)
                }
            }

            val totalSeries = seriesToEnrich.size
            if (totalSeries > 0) {
                val seriesCount = AtomicInteger(0)
                seriesToEnrich.chunked(10).forEach { chunk ->
                    coroutineScope {
                        chunk.forEach { channel ->
                            launch {
                                enrichChannelWithTmdb(channel)
                                val current = seriesCount.incrementAndGet()
                                if (current % 10 == 0 || current == totalSeries) {
                                    val pct = ((current.toDouble() / totalSeries.toDouble()) * 100.0).toInt()
                                    onProgress(100, 100, pct, "Segundo plano: Séries $pct%")
                                }
                            }
                        }
                    }
                    delay(50)
                }
            }
        }
    }

    /**
     * Delta sync: baixa a playlist, compara com o banco local e só insere novos / remove deletados.
     * Retorna a quantidade de canais novos adicionados.
     */
    suspend fun syncPlaylistDelta(): Result<Int> = withContext(Dispatchers.IO) {
        val url = _activePlaylistUrl.value ?: return@withContext Result.failure(Exception("No active playlist"))
        try {
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            val host = uri.host
            val scheme = uri.scheme
            val port = uri.port

            val newChannels: List<Channel> = if (username != null && password != null && host != null) {
                val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                fetchXtreamChannels(baseUrl, username, password, url)
            } else {
                fetchM3UChannels(url)
            }

            if (newChannels.isEmpty()) return@withContext Result.success(0)

            // Comparar com o banco local
            val existingIds = channelDao.getAllRemoteIds(url).toSet()
            val newIds = newChannels.map { it.remoteId }.toSet()

            // Proteção: se o servidor retornou menos de 50% dos canais existentes,
            // provavelmente foi uma resposta parcial/erro — NÃO deletar nada
            val removedIds = existingIds - newIds
            val safeToDelete = removedIds.size < existingIds.size * 0.5
            if (safeToDelete && removedIds.isNotEmpty()) {
                removedIds.chunked(500).forEach { chunk ->
                    channelDao.deleteMultipleByRemoteId(url, chunk)
                }
            }

            // Canais novos que não existem localmente
            val addedIds = newIds - existingIds
            val channelsToInsert = newChannels.filter { it.remoteId in addedIds }

            if (channelsToInsert.isNotEmpty()) {
                channelsToInsert.chunked(500).forEach { chunk ->
                    channelDao.insertAll(chunk)
                }

                // Atualizar categorias (pode ter novas categorias)
                val newCategoriesByGroup = channelsToInsert.groupBy { it.groupTitle }
                val existingCategories = categoryDao.getAllByPlaylist(url).map { it.id }.toSet()
                val newCategories = newCategoriesByGroup.entries
                    .filter { it.key !in existingCategories }
                    .mapIndexed { index, (catName, channels) ->
                        val type = channels.groupBy { it.category }
                            .maxByOrNull { it.value.size }?.key ?: "LIVE_TV"
                        com.cinex.player.data.model.Category(
                            id = catName,
                            name = catName,
                            type = type,
                            playlistUrl = url,
                            orderIndex = 1000 + index
                        )
                    }
                if (newCategories.isNotEmpty()) {
                    categoryDao.insertAll(newCategories)
                }

                // Enriquecer novos canais com TMDB em background
                repositoryScope.launch {
                    val moviesToEnrich = channelsToInsert.filter { it.category == "MOVIE" }
                    val seriesToEnrich = channelsToInsert.filter { it.category == "SERIES" }
                        .distinctBy { it.seriesName }

                    (moviesToEnrich + seriesToEnrich).chunked(5).forEach { chunk ->
                        coroutineScope {
                            chunk.forEach { channel ->
                                launch { enrichChannelWithTmdb(channel) }
                            }
                        }
                        delay(200)
                    }
                }
            }

            // Atualizar timestamp do último sync
            playlistDao.updateLastSyncTime(url, System.currentTimeMillis())

            Result.success(channelsToInsert.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Baixa canais via Xtream API sem salvar — retorna lista para comparação delta.
     */
    private suspend fun fetchXtreamChannels(
        baseUrl: String, user: String, pass: String, playlistUrl: String
    ): List<Channel> = coroutineScope {
        try {
            val gson = com.google.gson.GsonBuilder().setLenient().create()
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(XtreamCodesApi::class.java)

            val liveCatsDeferred = async { try { api.getLiveCategories(user, pass) } catch (_: Exception) { emptyList() } }
            val vodCatsDeferred = async { try { api.getVodCategories(user, pass) } catch (_: Exception) { emptyList() } }
            val seriesCatsDeferred = async { try { api.getSeriesCategories(user, pass) } catch (_: Exception) { emptyList() } }

            val liveCats = liveCatsDeferred.await().associateBy { it.category_id }
            val vodCats = vodCatsDeferred.await().associateBy { it.category_id }
            val seriesCats = seriesCatsDeferred.await().associateBy { it.category_id }

            val liveDeferred = async { api.getLiveStreams(user, pass) }
            val vodDeferred = async { api.getVodStreams(user, pass) }
            val seriesDeferred = async { api.getSeries(user, pass) }

            val channels = mutableListOf<Channel>()

            liveDeferred.await().forEachIndexed { index, stream ->
                channels.add(Channel(
                    name = stream.name,
                    logoUrl = stream.stream_icon,
                    groupTitle = liveCats[stream.category_id]?.category_name ?: "Live",
                    categoryId = stream.category_id,
                    streamUrl = "${baseUrl}live/$user/$pass/${stream.stream_id}.ts",
                    category = "LIVE_TV",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "live_${stream.stream_id}",
                    tvgId = stream.epg_channel_id
                ))
            }

            vodDeferred.await().forEachIndexed { index, m ->
                val ext = m.container_extension ?: "mp4"
                channels.add(Channel(
                    name = m.name,
                    logoUrl = m.stream_icon,
                    groupTitle = vodCats[m.category_id]?.category_name ?: "VOD",
                    categoryId = m.category_id,
                    streamUrl = "${baseUrl}movie/$user/$pass/${m.stream_id}.$ext",
                    category = "MOVIE",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "vod_${m.stream_id}"
                ))
            }

            seriesDeferred.await().forEachIndexed { index, s ->
                channels.add(Channel(
                    name = s.name,
                    logoUrl = s.cover,
                    groupTitle = seriesCats[s.category_id]?.category_name ?: "SÉRIES",
                    categoryId = s.category_id,
                    streamUrl = "",
                    category = "SERIES",
                    seriesName = s.name,
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "series_${s.series_id}"
                ))
            }

            channels
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Baixa canais via M3U sem salvar — retorna lista para comparação delta.
     */
    private suspend fun fetchM3UChannels(url: String): List<Channel> {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body ?: return emptyList()
            val (parsed, _) = body.charStream().buffered().use { reader -> parser.parse(reader, url) }
            parsed
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun syncXtream(
        baseUrl: String,
        user: String,
        pass: String,
        playlistUrl: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = coroutineScope {
        try {
            val gson = com.google.gson.GsonBuilder().setLenient().create()
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(XtreamCodesApi::class.java)
            
            onProgress(10, 10, 10, "Conectando ao servidor...")
            
            // Preservar dados do usuário E dados TMDB existentes
            // TMDB será re-enriquecido em background, mas preservar garante banners imediatos
            val existingTmdbData = withContext(Dispatchers.IO) {
                channelDao.getTmdbAndUserDataByPlaylist(playlistUrl).associateBy { it.remoteId }
            }

            suspend fun <T> fetchListRelaxed(action: String, catId: String?, type: java.lang.reflect.Type): List<T> {
                val url = "${baseUrl}player_api.php?username=$user&password=$pass&action=$action${if (catId != null) "&category_id=$catId" else ""}"
                val request = Request.Builder().url(url).build()
                return try {
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) return emptyList()
                    var jsonStr = response.body?.string() ?: ""
                    jsonStr = jsonStr.trim()
                    if (jsonStr.startsWith("[") && !jsonStr.endsWith("]")) {
                        val lastBracket = jsonStr.lastIndexOf("}")
                        if (lastBracket > 0) {
                            jsonStr = jsonStr.substring(0, lastBracket + 1) + "]"
                        } else {
                            jsonStr += "]"
                        }
                    }
                    gson.fromJson(jsonStr, type) ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("CineX-Sync", "Relaxed fetch for $action failed: ${e.message}")
                    emptyList()
                }
            }

            suspend fun fetchCategoriesRelaxed(action: String): List<com.cinex.player.data.network.XtreamCategory> {
                val type = object : com.google.gson.reflect.TypeToken<List<com.cinex.player.data.network.XtreamCategory>>() {}.type
                return fetchListRelaxed(action, null, type)
            }

            suspend fun fetchLiveStreamsRelaxed(catId: String? = null): List<com.cinex.player.data.network.LiveStreamItem> {
                val type = object : com.google.gson.reflect.TypeToken<List<com.cinex.player.data.network.LiveStreamItem>>() {}.type
                return fetchListRelaxed("get_live_streams", catId, type)
            }

            suspend fun fetchVodStreamsRelaxed(catId: String? = null): List<com.cinex.player.data.network.VodStreamItem> {
                val type = object : com.google.gson.reflect.TypeToken<List<com.cinex.player.data.network.VodStreamItem>>() {}.type
                return fetchListRelaxed("get_vod_streams", catId, type)
            }

            suspend fun fetchSeriesRelaxed(catId: String? = null): List<com.cinex.player.data.network.SeriesItem> {
                val type = object : com.google.gson.reflect.TypeToken<List<com.cinex.player.data.network.SeriesItem>>() {}.type
                return fetchListRelaxed("get_series", catId, type)
            }


            // BUSCA SEQUENCIAL DE CATEGORIAS com retry (JSON pode truncar na primeira tentativa)
            suspend fun <T> fetchWithRetry(label: String, maxRetries: Int = 3, action: String = "", block: suspend () -> T): T? {
                repeat(maxRetries) { attempt ->
                    try {
                        return block()
                    } catch (e: Exception) {
                        android.util.Log.e("CineX-Sync", "$label attempt ${attempt + 1} failed: ${e.message}")
                        if (action.isNotEmpty() && (e is java.io.EOFException || e.message?.contains("End of input") == true || e.message?.contains("malformed") == true)) {
                            android.util.Log.d("CineX-Sync", "Using relaxed parse for $label...")
                            val relaxed = fetchCategoriesRelaxed(action)
                            if (relaxed.isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST")
                                return relaxed as T
                            }
                        }
                        if (attempt < maxRetries - 1) kotlinx.coroutines.delay(1000)
                    }
                }
                return null
            }

            val liveCats = fetchWithRetry("Live cats", action = "get_live_categories") { api.getLiveCategories(user, pass) } ?: emptyList()
            val vodCats = fetchWithRetry("VOD cats", action = "get_vod_categories") { api.getVodCategories(user, pass) } ?: emptyList()
            val seriesCats = fetchWithRetry("Series cats", action = "get_series_categories") { api.getSeriesCategories(user, pass) } ?: emptyList()
            android.util.Log.d("CineX-Sync", "Live cats: ${liveCats.size}, VOD cats: ${vodCats.size}, Series cats: ${seriesCats.size}")

            onProgress(20, 20, 20, "Limpando cache antigo...")
            withContext(Dispatchers.IO) {
                channelDao.clearByPlaylist(playlistUrl)
                categoryDao.clearByPlaylist(playlistUrl)

                val allCats = mutableListOf<com.cinex.player.data.model.Category>()
                allCats += liveCats.mapIndexed { index, cat -> com.cinex.player.data.model.Category(cat.category_id, cat.category_name, "LIVE_TV", playlistUrl, orderIndex = index) }
                allCats += vodCats.mapIndexed { index, cat -> com.cinex.player.data.model.Category(cat.category_id, cat.category_name, "MOVIE", playlistUrl, orderIndex = index) }
                allCats += seriesCats.mapIndexed { index, cat -> com.cinex.player.data.model.Category(cat.category_id, cat.category_name, "SERIES", playlistUrl, orderIndex = index) }
                if (allCats.isNotEmpty()) categoryDao.insertAll(allCats)
            }

            val liveCatsMap = liveCats.associateBy { it.category_id }
            val vodCatsMap = vodCats.associateBy { it.category_id }
            val seriesCatsMap = seriesCats.associateBy { it.category_id }

            onProgress(30, 30, 30, "Baixando conteúdo...")

            // 1. Live Streams
            var liveStreams = try { api.getLiveStreams(user, pass) } catch (e: Exception) { 
                android.util.Log.e("CineX-Sync", "LIVE bulk failed: ${e.message}")
                fetchLiveStreamsRelaxed()
            }
            if (liveStreams.isEmpty() && liveCats.isNotEmpty()) {
                android.util.Log.d("CineX-Sync", "Fallback: fetching live streams by category...")
                val byCategory = mutableListOf<LiveStreamItem>()
                liveCats.forEachIndexed { i, cat ->
                    try {
                        byCategory.addAll(api.getLiveStreams(user, pass, cat.category_id))
                    } catch (e: Exception) { 
                        android.util.Log.e("CineX-Sync", "Live cat ${cat.category_name} failed: ${e.message}")
                        byCategory.addAll(fetchLiveStreamsRelaxed(cat.category_id))
                    }
                    onProgress(30 + (i * 10 / liveCats.size), 30, 30, "TV ao vivo: ${cat.category_name}...")
                }
                liveStreams = byCategory
            }
            android.util.Log.d("CineX-Sync", "Live streams total: ${liveStreams.size}")

            // 2. VOD Streams
            onProgress(40, 30, 30, "Baixando filmes...")
            var vodStreams = try { api.getVodStreams(user, pass) } catch (e: Exception) { 
                android.util.Log.e("CineX-Sync", "VOD bulk failed: ${e.message}")
                fetchVodStreamsRelaxed()
            }
            if (vodStreams.isEmpty() && vodCats.isNotEmpty()) {
                android.util.Log.d("CineX-Sync", "Fallback: fetching VOD by category...")
                val byCategory = mutableListOf<VodStreamItem>()
                vodCats.forEachIndexed { i, cat ->
                    try {
                        byCategory.addAll(api.getVodStreams(user, pass, cat.category_id))
                    } catch (e: Exception) { 
                        android.util.Log.e("CineX-Sync", "VOD cat ${cat.category_name} failed: ${e.message}")
                        byCategory.addAll(fetchVodStreamsRelaxed(cat.category_id))
                    }
                    onProgress(40, 30 + (i * 10 / vodCats.size), 30, "Filmes: ${cat.category_name}...")
                }
                vodStreams = byCategory
            }
            android.util.Log.d("CineX-Sync", "VOD streams total: ${vodStreams.size}")

            // 3. Series
            onProgress(50, 40, 30, "Baixando séries...")
            var seriesList = try { api.getSeries(user, pass) } catch (e: Exception) { 
                android.util.Log.e("CineX-Sync", "Series bulk failed: ${e.message}")
                fetchSeriesRelaxed()
            }
            if (seriesList.isEmpty() && seriesCats.isNotEmpty()) {
                android.util.Log.d("CineX-Sync", "Fallback: fetching series by category...")
                val byCategory = mutableListOf<SeriesItem>()
                seriesCats.forEachIndexed { i, cat ->
                    try {
                        byCategory.addAll(api.getSeries(user, pass, cat.category_id))
                    } catch (e: Exception) { 
                        android.util.Log.e("CineX-Sync", "Series cat ${cat.category_name} failed: ${e.message}")
                        byCategory.addAll(fetchSeriesRelaxed(cat.category_id))
                    }
                    onProgress(50, 40, 30 + (i * 10 / seriesCats.size), "Séries: ${cat.category_name}...")
                }
                seriesList = byCategory
            }
            android.util.Log.d("CineX-Sync", "Series total: ${seriesList.size}")

            // Flags para saber se precisamos extrair categorias dos streams depois
            val needsLiveCatsFromStreams = liveCats.isEmpty() && liveStreams.isNotEmpty()
            val needsVodCatsFromStreams = vodCats.isEmpty() && vodStreams.isNotEmpty()

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
                    isFavorite = old?.isFavorite ?: false
                )
            }
            if (liveChannels.isNotEmpty()) {
                withContext(Dispatchers.IO) { liveChannels.chunked(500).forEach { channelDao.insertAll(it) } }
            }

            // Se as categorias live vieram vazias da API, extrair dos canais mapeados
            if (needsLiveCatsFromStreams && liveChannels.isNotEmpty()) {
                val extractedCats = liveChannels
                    .groupBy { it.categoryId }
                    .entries
                    .mapIndexed { index, (catId, channels) ->
                        com.cinex.player.data.model.Category(
                            id = catId,
                            name = channels.first().groupTitle ?: "Grupo $catId",
                            type = "LIVE_TV",
                            playlistUrl = playlistUrl,
                            orderIndex = index
                        )
                    }
                withContext(Dispatchers.IO) { categoryDao.insertAll(extractedCats) }
                android.util.Log.d("CineX-Sync", "Extracted ${extractedCats.size} live categories from streams")
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
                    resumePosition = old?.resumePosition ?: 0L,
                    totalDuration = old?.totalDuration ?: 0L,
                    isFavorite = old?.isFavorite ?: false,
                    tmdbRating = old?.tmdbRating,
                    tmdbSynopsis = old?.tmdbSynopsis,
                    posterUrl = old?.posterUrl ?: m.stream_icon,
                    bannerUrl = old?.bannerUrl,
                    tmdbYear = old?.tmdbYear,
                    castMembers = old?.castMembers,
                    trailerUrl = old?.trailerUrl
                )
            }
            if (movieStreams.isNotEmpty()) {
                withContext(Dispatchers.IO) { movieStreams.chunked(500).forEach { chunk -> channelDao.insertAll(chunk) } }
            }

            // Se as categorias VOD vieram vazias da API, extrair dos filmes mapeados
            if (needsVodCatsFromStreams && movieStreams.isNotEmpty()) {
                val extractedCats = movieStreams
                    .groupBy { it.categoryId }
                    .entries
                    .mapIndexed { index, (catId, channels) ->
                        com.cinex.player.data.model.Category(
                            id = catId,
                            name = channels.first().groupTitle ?: "Grupo $catId",
                            type = "MOVIE",
                            playlistUrl = playlistUrl,
                            orderIndex = index
                        )
                    }
                withContext(Dispatchers.IO) { categoryDao.insertAll(extractedCats) }
                android.util.Log.d("CineX-Sync", "Extracted ${extractedCats.size} VOD categories from streams")
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
                    resumePosition = old?.resumePosition ?: 0L,
                    totalDuration = old?.totalDuration ?: 0L,
                    isFavorite = old?.isFavorite ?: false,
                    tmdbRating = old?.tmdbRating ?: s.rating?.toDoubleOrNull(),
                    tmdbSynopsis = old?.tmdbSynopsis ?: s.plot,
                    posterUrl = old?.posterUrl ?: s.cover,
                    bannerUrl = old?.bannerUrl,
                    tmdbYear = old?.tmdbYear,
                    castMembers = old?.castMembers ?: s.cast,
                    trailerUrl = old?.trailerUrl
                )
            }
            if (seriesStreams.isNotEmpty()) {
                withContext(Dispatchers.IO) { seriesStreams.chunked(500).forEach { chunk -> channelDao.insertAll(chunk) } }
            }

            playlistDao.updateLastSyncTime(playlistUrl, System.currentTimeMillis())

            // Enriquecer 20 conteúdos aleatórios (filmes+séries) para a Home ter banners
            val featured = channelDao.getFeaturedContent(playlistUrl).first()
            if (featured.isEmpty()) {
                onProgress(100, 100, 100, "Buscando capas oficiais...")
                val seedMovies = withContext(Dispatchers.IO) {
                    channelDao.getMoviesToEnrich(playlistUrl).shuffled().take(10)
                }
                val seedSeries = withContext(Dispatchers.IO) {
                    channelDao.getSeriesToEnrich(playlistUrl).shuffled().take(10)
                }
                (seedMovies + seedSeries).shuffled().chunked(10).forEach { chunk ->
                    coroutineScope {
                        chunk.forEach { channel ->
                            launch { enrichChannelWithTmdb(channel) }
                        }
                    }
                }
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
        // Propaga a capa da série para o episódio (para "Continuar Assistindo" mostrar a capa correta)
        propagateSeriesPoster(channelId)
    }

    private suspend fun propagateSeriesPoster(channelId: Int) {
        val url = _activePlaylistUrl.value ?: return
        val channel = channelDao.getChannelById(channelId) ?: return
        if (channel.category != "SERIES" || channel.seriesName.isNullOrEmpty()) return
        if (!channel.posterUrl.isNullOrEmpty()) return

        val posterUrl = channelDao.getSeriesPosterUrl(channel.seriesName, url) ?: return
        channelDao.updateTmdbInfo(
            channelId = channelId,
            rating = channel.tmdbRating,
            synopsis = channel.tmdbSynopsis,
            posterUrl = posterUrl,
            bannerUrl = channel.bannerUrl,
            year = channel.tmdbYear,
            cast = channel.castMembers,
            trailer = channel.trailerUrl
        )
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

            // Normaliza texto para comparação (remove acentos, pontuação, minúsculo)
            fun normalize(s: String): String = java.text.Normalizer
                .normalize(s, java.text.Normalizer.Form.NFD)
                .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            // Verifica se o resultado do TMDB realmente corresponde ao título buscado
            fun isNameMatch(result: com.cinex.player.data.network.TmdbMovieResult, queryStr: String): Boolean {
                val nq = normalize(queryStr)
                if (nq.isBlank()) return false
                val resultNames = listOfNotNull(result.title, result.name, result.original_title, result.original_name)
                return resultNames.any { name ->
                    val nr = normalize(name)
                    if (nr.isBlank()) return@any false
                    // Match exato
                    if (nr == nq) return@any true
                    // Um contém o outro (mas só se o menor tiver pelo menos 4 chars)
                    if (nq.length >= 4 && Regex("\\b" + nq.split(" ").filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) } + "\\b").containsMatchIn(nr)) return@any true
                    if (nr.length >= 4 && Regex("\\b" + nr.split(" ").filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) } + "\\b").containsMatchIn(nq)) return@any true
                    // Comparação por palavras: TODAS as palavras significativas devem bater
                    val queryWords = nq.split(" ").filter { it.length > 2 }
                    val resultWords = nr.split(" ").filter { it.length > 2 }
                    if (queryWords.isEmpty() || resultWords.isEmpty()) return@any false
                    val matchCount = queryWords.count { qw -> resultWords.any { rw -> rw == qw } }
                    // Exige 100% das palavras da query no resultado
                    matchCount == queryWords.size && queryWords.size >= 2
                }
            }

            val effectiveQuery = if (isMovie) query else seriesQuery

            // Busca o resultado com ano exato E nome compatível
            fun findExactYear(results: List<com.cinex.player.data.network.TmdbMovieResult>): com.cinex.player.data.network.TmdbMovieResult? =
                results.find { res ->
                    val resYear = (res.release_date ?: res.first_air_date)?.take(4)
                    resYear == extractedYear && isNameMatch(res, effectiveQuery)
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
                // Sem ano: valida que o nome do resultado bate com a query
                searchResponse.results.firstOrNull { isNameMatch(it, effectiveQuery) }
            }

            android.util.Log.d("CineX-TMDB", "Search '${channel.name}' → ${searchResponse.results.size} results, matched=${tmdbResult != null}")

            tmdbResult?.let { bestMatch ->
                val details = if (channel.category == "MOVIE") {
                    tmdbApi.getMovieDetails(bestMatch.id, tmdbApiKey)
                } else {
                    tmdbApi.getTvDetails(bestMatch.id, tmdbApiKey)
                }

                val posterUrl = details.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                val backdropUrl = details.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                android.util.Log.d("CineX-TMDB", "  → backdrop=$backdropUrl, synopsis=${details.overview?.take(30)}")
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
                    // Atualiza o canal representativo da série
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
                    // Propaga backdrop e poster para TODOS os episódios da série
                    // (garante que getFeaturedContent sempre encontre /original/ em qualquer linha do grupo)
                    channelDao.propagateSeriesBackdrop(
                        channel.seriesName!!,
                        channel.playlistUrl,
                        tmdbResult.vote_average,
                        posterUrl,
                        backdropUrl,
                        year,
                        cast
                    )

                    // Busca thumbnails e sinopses específicas de episódios em uma única passada
                    details.seasons?.forEach { season ->
                        try {
                            val seasonDetails = tmdbApi.getSeasonDetails(bestMatch.id, season.season_number, tmdbApiKey)
                            seasonDetails.episodes.forEach { tmdbEp ->
                                val stillUrl = tmdbEp.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                                channelDao.updateEpisodeStillAndSynopsis(
                                    channel.seriesName!!,
                                    season.season_number,
                                    tmdbEp.episode_number,
                                    stillUrl ?: "",
                                    tmdbEp.overview,
                                    channel.playlistUrl
                                )
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
        } catch (e: Exception) {
            android.util.Log.e("CineX-TMDB", "Enrich FAILED for '${channel.name}': ${e.message}")
        }
    }

    suspend fun enrichSeriesMetadataWithTmdb(channel: Channel) = withContext(Dispatchers.IO) {
        if (channel.category != "SERIES" || channel.seriesName.isNullOrBlank()) return@withContext

        try {
            val rawName = channel.name
            val yearMatch = Regex("(?i)\\(?(\\d{4})\\)?").find(rawName)
            val extractedYear = yearMatch?.groupValues?.get(1)

            val query = rawName
                .replace(Regex("(?i)\\(?\\d{4}\\)?"), "")
                .replace(Regex("(?i)\\b(fhd|hd|sd|4k|dual|legendado|dublado|multi|brrip|hdtv|web-dl|bluray|h264|h265|x264|x265|1080p|720p|480p)\\b"), "")
                .replace(Regex("[|\\-\\[\\]]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

            val seriesQuery = (channel.seriesName ?: query)
                .replace(Regex("(?i)s\\d+e\\d+.*"), "")
                .replace(Regex("(?i)\\(?\\d{4}\\)?"), "")
                .trim()

            fun normalize(s: String): String = java.text.Normalizer
                .normalize(s, java.text.Normalizer.Form.NFD)
                .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            fun isNameMatch(result: com.cinex.player.data.network.TmdbMovieResult, queryStr: String): Boolean {
                val nq = normalize(queryStr)
                if (nq.isBlank()) return false
                val resultNames = listOfNotNull(result.title, result.name, result.original_title, result.original_name)
                return resultNames.any { name ->
                    val nr = normalize(name)
                    if (nr.isBlank()) return@any false
                    if (nr == nq) return@any true
                    if (nq.length >= 4 && Regex("\\b" + nq.split(" ").filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) } + "\\b").containsMatchIn(nr)) return@any true
                    if (nr.length >= 4 && Regex("\\b" + nr.split(" ").filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) } + "\\b").containsMatchIn(nq)) return@any true
                    val queryWords = nq.split(" ").filter { it.length > 2 }
                    val resultWords = nr.split(" ").filter { it.length > 2 }
                    if (queryWords.isEmpty() || resultWords.isEmpty()) return@any false
                    queryWords.count { qw -> resultWords.any { rw -> rw == qw } } == queryWords.size && queryWords.size >= 2
                }
            }

            fun findExactYear(results: List<com.cinex.player.data.network.TmdbMovieResult>): com.cinex.player.data.network.TmdbMovieResult? =
                results.find { res ->
                    val resYear = (res.release_date ?: res.first_air_date)?.take(4)
                    resYear == extractedYear && isNameMatch(res, seriesQuery)
                }

            val searchResponse = tmdbApi.searchSeries(tmdbApiKey, seriesQuery, year = extractedYear)
            val tmdbResult = if (extractedYear != null) {
                findExactYear(searchResponse.results)
                    ?: findExactYear(tmdbApi.searchSeries(tmdbApiKey, seriesQuery).results)
            } else {
                searchResponse.results.firstOrNull { isNameMatch(it, seriesQuery) }
            }

            if (tmdbResult == null) {
                channelDao.clearSeriesTmdbMetadata(channel.seriesName!!, channel.playlistUrl)
                channelDao.clearEpisodeTmdbStillImages(channel.seriesName!!, channel.playlistUrl)
                return@withContext
            }

            val details = tmdbApi.getTvDetails(tmdbResult.id, tmdbApiKey)
            val posterUrl = details.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            val backdropUrl = details.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
            val cast = details.credits?.cast?.take(10)?.joinToString(", ") { it.name }
            val year = tmdbResult.release_date?.take(4) ?: tmdbResult.first_air_date?.take(4)

            val allVideos = try {
                tmdbApi.getTvVideos(tmdbResult.id, tmdbApiKey).results
            } catch (_: Exception) {
                details.videos?.results ?: emptyList()
            }
            val trailerUrl = pickBestTrailer(allVideos)

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

            channelDao.propagateSeriesBackdrop(
                channel.seriesName!!,
                channel.playlistUrl,
                tmdbResult.vote_average,
                posterUrl,
                backdropUrl,
                year,
                cast
            )
        } catch (e: Exception) {
            android.util.Log.e("CineX-TMDB", "Series metadata enrich FAILED for '${channel.name}': ${e.message}")
        }
    }

    suspend fun enrichSeriesSeasonWithTmdb(channel: Channel, seasonNumber: Int) = withContext(Dispatchers.IO) {
        if (channel.category != "SERIES" || channel.seriesName.isNullOrBlank() || seasonNumber <= 0) return@withContext

        try {
            val rawName = channel.name
            val yearMatch = Regex("(?i)\\(?(\\d{4})\\)?").find(rawName)
            val extractedYear = yearMatch?.groupValues?.get(1)

            val query = rawName
                .replace(Regex("(?i)\\(?\\d{4}\\)?"), "")
                .replace(Regex("(?i)\\b(fhd|hd|sd|4k|dual|legendado|dublado|multi|brrip|hdtv|web-dl|bluray|h264|h265|x264|x265|1080p|720p|480p)\\b"), "")
                .replace(Regex("[|\\-\\[\\]]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

            val seriesQuery = (channel.seriesName ?: query)
                .replace(Regex("(?i)s\\d+e\\d+.*"), "")
                .replace(Regex("(?i)\\(?\\d{4}\\)?"), "")
                .trim()

            fun normalize(s: String): String = java.text.Normalizer
                .normalize(s, java.text.Normalizer.Form.NFD)
                .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            fun isNameMatch(result: com.cinex.player.data.network.TmdbMovieResult, queryStr: String): Boolean {
                val nq = normalize(queryStr)
                if (nq.isBlank()) return false
                val resultNames = listOfNotNull(result.title, result.name, result.original_title, result.original_name)
                return resultNames.any { name ->
                    val nr = normalize(name)
                    if (nr.isBlank()) return@any false
                    if (nr == nq) return@any true
                    if (nq.length >= 4 && Regex("\\b" + nq.split(" ").filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) } + "\\b").containsMatchIn(nr)) return@any true
                    if (nr.length >= 4 && Regex("\\b" + nr.split(" ").filter { it.isNotBlank() }.joinToString("\\s+") { Regex.escape(it) } + "\\b").containsMatchIn(nq)) return@any true
                    val queryWords = nq.split(" ").filter { it.length > 2 }
                    val resultWords = nr.split(" ").filter { it.length > 2 }
                    if (queryWords.isEmpty() || resultWords.isEmpty()) return@any false
                    queryWords.count { qw -> resultWords.any { rw -> rw == qw } } == queryWords.size && queryWords.size >= 2
                }
            }

            fun findExactYear(results: List<com.cinex.player.data.network.TmdbMovieResult>): com.cinex.player.data.network.TmdbMovieResult? =
                results.find { res ->
                    val resYear = (res.release_date ?: res.first_air_date)?.take(4)
                    resYear == extractedYear && isNameMatch(res, seriesQuery)
                }

            val searchResponse = tmdbApi.searchSeries(tmdbApiKey, seriesQuery, year = extractedYear)
            val tmdbResult = if (extractedYear != null) {
                findExactYear(searchResponse.results)
                    ?: findExactYear(tmdbApi.searchSeries(tmdbApiKey, seriesQuery).results)
            } else {
                searchResponse.results.firstOrNull { isNameMatch(it, seriesQuery) }
            }

            if (tmdbResult == null) {
                channelDao.clearEpisodeTmdbStillImagesForSeason(channel.seriesName!!, seasonNumber, channel.playlistUrl)
                return@withContext
            }

            val seasonDetails = tmdbApi.getSeasonDetails(tmdbResult.id, seasonNumber, tmdbApiKey)
            seasonDetails.episodes.forEach { tmdbEp ->
                val stillUrl = tmdbEp.still_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                channelDao.updateEpisodeStillAndSynopsis(
                    channel.seriesName!!,
                    seasonNumber,
                    tmdbEp.episode_number,
                    stillUrl,
                    tmdbEp.overview,
                    channel.playlistUrl
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("CineX-TMDB", "Season enrich FAILED for '${channel.name}' S$seasonNumber: ${e.message}")
        }
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
        } catch (e: Throwable) {
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

        // Se já tem episódios no banco, não re-busca (evita sobrescrever dados)
        if (channelDao.countEpisodesForSeries(seriesName, url) > 1) return@withContext

        try {
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            val host = uri.host
            val scheme = uri.scheme
            val port = uri.port

            if (username != null && password != null && host != null) {
                val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                val gson = com.google.gson.GsonBuilder()
                    .setLenient()
                    .registerTypeAdapter(
                        EpisodeExtraInfo::class.java,
                        com.google.gson.JsonDeserializer<EpisodeExtraInfo?> { json, _, _ ->
                            // Xtream APIs frequentemente retornam info como "" ao invés de objeto
                            if (json == null || json.isJsonNull || !json.isJsonObject) {
                                null
                            } else {
                                val obj = json.asJsonObject
                                fun readString(key: String): String? {
                                    val value = obj.get(key) ?: return null
                                    if (value.isJsonNull || !value.isJsonPrimitive) return null
                                    val text = value.asString ?: return null
                                    return text.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                }

                                EpisodeExtraInfo(
                                    movie_image = readString("movie_image"),
                                    plot = readString("plot"),
                                    duration = readString("duration"),
                                    release_date = readString("release_date"),
                                    rating = readString("rating")
                                )
                            }
                        }
                    )
                    .create()
                val api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                    .create(XtreamCodesApi::class.java)

                val response = api.getSeriesInfo(username, password, seriesId)

                // Preservar dados do usuário (progresso) e imagens existentes
                val existingEpisodes = channelDao.getEpisodesForSeriesList(seriesName, url)
                    .associateBy { it.remoteId }

                response.episodes?.forEach { (seasonNumStr, episodeList) ->
                    val seasonNum = seasonNumStr.toIntOrNull() ?: 1
                    val channels = episodeList.map { ep ->
                        val remoteId = "series_ep_${ep.id}"
                        val old = existingEpisodes[remoteId]
                        val apiImage = ep.info?.movie_image?.takeIf { it.isNotBlank() }
                        Channel(
                            name = ep.title,
                            logoUrl = apiImage ?: old?.logoUrl,
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
                            posterUrl = old?.posterUrl,
                            bannerUrl = old?.bannerUrl,
                            resumePosition = old?.resumePosition ?: 0L,
                            totalDuration = old?.totalDuration ?: 0L,
                            isFavorite = old?.isFavorite ?: false
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
