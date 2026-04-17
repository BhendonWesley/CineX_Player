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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicBoolean
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
    private val tmdbApi: TmdbApi,
    private val application: android.app.Application
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Detecta Android TV para serializar downloads e evitar OOM/travamento na sync.
    // No mobile, o comportamento paralelo é mantido.
    private val isTv: Boolean by lazy {
        val uiModeManager = application.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    private val _activePlaylistUrl = MutableStateFlow<String?>(null)
    val activePlaylistUrl = _activePlaylistUrl.asStateFlow()

    /** Flags thread-safe para controle de sync (evita race condition) */
    private val deltaSyncAborted = AtomicBoolean(false)
    private val isFullSyncRunning = AtomicBoolean(false)

    /** TTL em memória por sessão: série → timestamp da última busca de episódios */
    private val seriesEpisodesFetchedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val SERIES_EPISODES_TTL_MS = 12L * 60 * 60 * 1000 // 12 horas

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

        // Detectar se é Xtream ou M3U
        val uri = android.net.Uri.parse(playlist.url)
        val username = uri.getQueryParameter("username")
        val password = uri.getQueryParameter("password")
        val host = uri.host

        if (username != null && password != null && host != null) {
            // Xtream: sync categorias → bloqueia tela de carregamento até conteúdo completo
            val scheme = uri.scheme
            val port = uri.port
            val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
            val result = syncXtream(baseUrl, username, password, playlist.url, onProgress)
            if (result.isSuccess) {
                _activePlaylistUrl.value = playlist.url
                // Paralelo: canais, filmes e séries baixam ao mesmo tempo.
                // Só retorna quando os 3 estão prontos — loading screen fica visível até lá.
                onProgress(5, 5, 5, "Carregando conteúdo...")
                val lp = AtomicInteger(5); val mp = AtomicInteger(5); val sp = AtomicInteger(5)
                coroutineScope {
                    awaitAll(
                        async { ensureTypeLoaded("LIVE_TV", onProgress = { pct ->
                            lp.set(pct); onProgress(pct, mp.get(), sp.get(), "Canais ao vivo...")
                        })},
                        async { ensureTypeLoaded("MOVIE", onProgress = { pct ->
                            mp.set(pct); onProgress(lp.get(), pct, sp.get(), "Filmes...")
                        })},
                        async { ensureTypeLoaded("SERIES", onProgress = { pct ->
                            sp.set(pct); onProgress(lp.get(), mp.get(), pct, "Séries...")
                        })}
                    )
                }
                // Retry se filmes falharam (OOM, timeout de rede, etc.)
                if (channelDao.countByType("MOVIE", playlist.url) == 0) {
                    android.util.Log.w("CineX-Sync", "MOVIE: nenhum filme no banco após sync — retentando...")
                    onProgress(100, 5, 100, "Recarregando filmes...")
                    mp.set(5)
                    ensureTypeLoaded("MOVIE", onProgress = { pct ->
                        mp.set(pct); onProgress(100, pct, 100, "Filmes...")
                    })
                }
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Falha ao sincronizar"))
            }
        } else {
            // M3U: sync tradicional
            syncPlaylist(playlist.url, onProgress)
        }
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

    fun observeMoviesByCategory(categoryId: String): Flow<List<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(emptyList())
            else channelDao.observeMoviesByCategory(url, categoryId)
        }
    }

    fun observeSeriesByCategory(categoryId: String): Flow<List<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(emptyList())
            else channelDao.observeSeriesByCategory(url, categoryId)
        }
    }

    suspend fun getFirstImageUrlsForCategory(type: String, categoryId: String, limit: Int = 8): List<String> {
        val url = _activePlaylistUrl.value ?: return emptyList()
        return channelDao.getFirstChannelsByCategory(type, url, categoryId, limit)
            .mapNotNull { ch ->
                val poster = ch.posterUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val logo = ch.logoUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                poster ?: logo
            }
    }

    fun observeAllSeries(): Flow<List<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) {
                android.util.Log.d("CineX-Series", "observeAllSeries: URL da playlist é null")
                flowOf(emptyList())
            } else {
                android.util.Log.d("CineX-Series", "observeAllSeries: Buscando séries para playlist...")
                channelDao.observeAllSeries(url).onEach { series ->
                    android.util.Log.d("CineX-Series", "observeAllSeries: DAO retornou ${series.size} séries (seasonNumber IS NULL)")
                    if (series.isEmpty()) {
                        // Verificar se há séries sem o filtro seasonNumber
                        val allSeriesCount = channelDao.countByType("SERIES", url)
                        val episodesCount = channelDao.countEpisodesOnly(url)
                        android.util.Log.d("CineX-Series", "observeAllSeries: DEBUG - Total SERIES no banco: $allSeriesCount, Episódios: $episodesCount")
                        if (allSeriesCount == 0) {
                            android.util.Log.e("CineX-Series", "observeAllSeries: ⚠️ NENHUMA SÉRIE NO BANCO! Sync falhou ou não foi executado.")
                        }
                    }
                }
            }
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

    /**
     * Revalida episódios em background se o TTL de 12h tiver expirado nesta sessão.
     * Nunca bloqueia o caller — o usuário vê o cache imediatamente.
     */
    fun refreshSeriesInBackground(seriesId: Int, seriesName: String) {
        if (seriesId == -1) return
        repositoryScope.launch {
            val lastFetched = seriesEpisodesFetchedAt[seriesName] ?: 0L
            // Se já buscamos nesta sessão e ainda está dentro do TTL, não re-busca
            if (lastFetched > 0L && System.currentTimeMillis() - lastFetched < SERIES_EPISODES_TTL_MS) return@launch
            try { fetchAndStoreEpisodes(seriesId, seriesName, forceRefresh = true) } catch (_: Exception) {}
        }
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
        return Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false)) {
            channelDao.searchChannels(query, url)
        }.flow
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
                    else -> channelDao.getLiveChannelsByCategoryIdPaged(categoryId, url)
                }
            }.flow
        }
    }

    fun getPagedMoviesByCategory(categoryId: String, sort: String = "RECENT"): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50, initialLoadSize = 50, prefetchDistance = 10)) {
                when (categoryId) {
                    "Favorito", "Favoritos" -> {
                        when (sort) {
                            "AZ" -> channelDao.getMoviesFavoritesPagedAZ(url)
                            "ZA" -> channelDao.getMoviesFavoritesPagedZA(url)
                            "RATING" -> channelDao.getMoviesFavoritesPagedRating(url)
                            else -> channelDao.getMoviesFavoritesPagedRecent(url)
                        }
                    }
                    "Continuar Assistindo" -> channelDao.getContinueWatchingPaged("MOVIE", url)
                    else -> {
                        when (sort) {
                            "AZ" -> channelDao.getMoviesPagedAZ(url, categoryId)
                            "ZA" -> channelDao.getMoviesPagedZA(url, categoryId)
                            "RATING" -> channelDao.getMoviesPagedRating(url, categoryId)
                            else -> channelDao.getMoviesPagedRecent(url, categoryId)
                        }
                    }
                }
            }.flow
        }
    }

    fun getPagedSeriesByCategory(categoryId: String, sort: String = "RECENT"): Flow<PagingData<Channel>> {
        return _activePlaylistUrl.flatMapLatest { url ->
            if (url == null) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 50, initialLoadSize = 50, prefetchDistance = 10)) {
                when (categoryId) {
                    "Favorito", "Favoritos" -> {
                        when (sort) {
                            "AZ" -> channelDao.getSeriesFavoritesPagedAZ(url)
                            "ZA" -> channelDao.getSeriesFavoritesPagedZA(url)
                            "RATING" -> channelDao.getSeriesFavoritesPagedRating(url)
                            else -> channelDao.getSeriesFavoritesPagedRecent(url)
                        }
                    }
                    "Continuar Assistindo" -> channelDao.getContinueWatchingPaged("SERIES", url)
                    else -> {
                        when (sort) {
                            "AZ" -> channelDao.getSeriesPagedAZ(url, categoryId)
                            "ZA" -> channelDao.getSeriesPagedZA(url, categoryId)
                            "RATING" -> channelDao.getSeriesPagedRating(url, categoryId)
                            else -> channelDao.getSeriesPagedRecent(url, categoryId)
                        }
                    }
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
        // MIGRAÇÕES: limpa dados ruins antes de enriquecer
        channelDao.clearStaleW1280Banners()       // bannerUrl /w1280/ → NULL, re-enriquece com /original/
        channelDao.clearNullStringSynopsis()       // tmdbSynopsis = "null" → NULL, re-enriquece sinopse real

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

    /**
     * OTIMIZAÇÃO: Garante que a Home tenha pelo menos minFeatured banners antes de retornar.
     * Agora AGUARDA o enriquecimento completar antes de verificar o resultado.
     * Antes: lançava corrotinas e verificava imediatamente (sempre via 0 itens).
     */
    private suspend fun ensureHomeFeaturedSeed(
        url: String,
        minFeatured: Int = 3,
        initialBatchSize: Int = 24,
        maxAttempts: Int = 4
    ): Int = withContext(Dispatchers.IO) {
        var featuredCount = channelDao.getFeaturedContent(url).first().size
        if (featuredCount >= minFeatured) return@withContext featuredCount

        val candidates = (channelDao.getMoviesToEnrich(url).shuffled() + channelDao.getSeriesToEnrich(url).shuffled())
            .distinctBy { it.id }

        var offset = 0
        var batchSize = initialBatchSize

        repeat(maxAttempts) { attempt ->
            if (featuredCount >= minFeatured || offset >= candidates.size) return@repeat

            val batch = candidates.drop(offset).take(batchSize)
            if (batch.isEmpty()) return@repeat

            android.util.Log.d("CineX-Home", "Seed attempt ${attempt + 1}: enriquecendo ${batch.size} itens para a Home")

            // CORREÇÃO: Aguarda TODOS os enriquecimentos completarem antes de verificar
            coroutineScope {
                batch.forEach { channel ->
                    launch { enrichChannelWithTmdb(channel) }
                }
            }

            // Agora sim verifica - os dados TMDB já estão no banco
            featuredCount = channelDao.getFeaturedContent(url).first().size
            android.util.Log.d("CineX-Home", "Seed attempt ${attempt + 1}: featuredCount=$featuredCount")
            
            offset += batch.size
            batchSize += 12
        }

        featuredCount
    }

    suspend fun syncPlaylist(
        url: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // AtomicBoolean evita race condition entre threads
        if (!isFullSyncRunning.compareAndSet(false, true)) {
            android.util.Log.w("CineX-Sync", "Sync já em andamento, ignorando chamada duplicada")
            return@withContext Result.failure(Exception("Sincronização já em andamento"))
        }
        // Sinaliza ao delta sync para abortar — full sync tem prioridade
        deltaSyncAborted.set(true)
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
            
            // Se tem username+password na URL, usa Xtream API (mais rápido e correto).
            // M3U só é usado quando não há credenciais.
            if (username != null && password != null && host != null) {
                val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                val syncResult = syncXtream(baseUrl, username, password, url, onProgress)
                if (syncResult.isSuccess) {
                    _activePlaylistUrl.value = url

                    // Bloqueia a tela de carregamento até todo conteúdo estar no banco.
                    // Paralelo: canais, filmes e séries baixam ao mesmo tempo.
                    onProgress(5, 5, 5, "Carregando conteúdo...")
                    val lp = AtomicInteger(5); val mp = AtomicInteger(5); val sp = AtomicInteger(5)
                    coroutineScope {
                        awaitAll(
                            async { ensureTypeLoaded("LIVE_TV", onProgress = { pct ->
                                lp.set(pct); onProgress(pct, mp.get(), sp.get(), "Canais ao vivo...")
                            })},
                            async { ensureTypeLoaded("MOVIE", onProgress = { pct ->
                                mp.set(pct); onProgress(lp.get(), pct, sp.get(), "Filmes...")
                            })},
                            async { ensureTypeLoaded("SERIES", onProgress = { pct ->
                                sp.set(pct); onProgress(lp.get(), mp.get(), pct, "Séries...")
                            })}
                        )
                    }
                    // Retry se filmes falharam (OOM, timeout, etc.)
                    if (channelDao.countByType("MOVIE", url) == 0) {
                        android.util.Log.w("CineX-Sync", "MOVIE: nenhum filme no banco — retentando...")
                        onProgress(100, 5, 100, "Recarregando filmes...")
                        mp.set(5)
                        ensureTypeLoaded("MOVIE", onProgress = { pct ->
                            mp.set(pct); onProgress(100, pct, 100, "Filmes...")
                        })
                    }

                    // Logs de debug para rastrear perda de conteúdo
                    val liveCount = channelDao.countByType("LIVE_TV", url)
                    val movieCount = channelDao.countByType("MOVIE", url)
                    val seriesCount = channelDao.countByType("SERIES", url)
                    android.util.Log.d("CineX-Sync", "✅ SYNC COMPLETO! Live: $liveCount, Movies: $movieCount, Series: $seriesCount")

                    // TMDB enrichment em background — não bloqueia o usuário
                    repositoryScope.launch {
                        try {
                            val candidatesForPrefetch = (
                                channelDao.getMoviesToEnrich(url).shuffled().take(30) +
                                channelDao.getSeriesToEnrich(url).shuffled().take(30)
                            )
                            if (candidatesForPrefetch.isNotEmpty()) {
                                try { prefetchTmdbCache(candidatesForPrefetch) } catch (_: Exception) {}
                            }
                            enrichRandomForHome(url, 20)
                            triggerBackgroundEnrichment(url)
                        } catch (e: Exception) {
                            android.util.Log.e("CineX-Sync", "Enrichment background falhou: ${e.message}")
                        }
                    }

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

                onProgress(55, 55, 55, "Processando categorias...")
                val channelsByGroup = parsedChannels.groupBy { it.groupTitle }
                val m3uCategories = channelsByGroup.entries.mapIndexed { index, (catName, channels) ->
                    val type = channels.groupBy { it.category }
                        .maxByOrNull { it.value.size }?.key ?: "LIVE_TV"
                    com.cinex.player.data.model.Category(
                        id = "${type.lowercase()}_$catName",
                        name = catName,
                        type = type,
                        playlistUrl = url,
                        orderIndex = index
                    )
                }
                // Reconstrói categorias do zero — remove órfãs que o servidor removeu
                if (m3uCategories.isNotEmpty()) {
                    categoryDao.clearByPlaylist(url)
                    categoryDao.insertAll(m3uCategories)
                }

                if (epgUrl != null) {
                    val currentPlaylist = playlistDao.getPlaylistByUrl(url)
                    if (currentPlaylist != null) {
                        playlistDao.insertPlaylist(currentPlaylist.copy(epgUrl = epgUrl))
                    }
                }

                val totalChannels = parsedChannels.size
                onProgress(60, 60, 60, "Salvando canais...")

                // Insere em chunks para evitar OOM e manter progresso visível na UI.
                // Uma única transação com 277k linhas segura o lock SQLite por tempo demais
                // e mantém todos os objetos na RAM simultaneamente.
                val chunkedChannels = parsedChannels.chunked(1000)
                var processed = 0
                chunkedChannels.forEach { chunk ->
                    val mappedChunk = chunk.mapIndexed { chunkIndex, channel ->
                        val old = existingUserData[channel.remoteId]
                        channel.copy(
                            orderIndex = processed + chunkIndex,
                            categoryId = "${channel.category.lowercase()}_${channel.groupTitle}",
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
                    channelDao.insertAll(mappedChunk)
                    processed += chunk.size
                    val pct = 60 + (40 * processed / totalChannels)
                    onProgress(pct, pct, pct, "Salvando canais ($processed/$totalChannels)...")
                }
                onProgress(100, 100, 100, "Sincronização concluída!")

                // EPG sync em background — NÃO bloqueia o sync principal
                // Arquivos XMLTV podem ser 50MB+ e levar 30s+ para parsear
                // O EPG será sincronizado pelo EpgSyncWorker periódico ou na próxima navegação
                if (epgUrl != null) {
                    android.util.Log.d("CineX-Sync", "EPG URL encontrado, sync será feito em background: $epgUrl")
                    // Salvar epgUrl na playlist para sync posterior
                    val currentPlaylist = playlistDao.getPlaylistByUrl(url)
                    if (currentPlaylist != null && currentPlaylist.epgUrl.isNullOrEmpty()) {
                        playlistDao.insertPlaylist(currentPlaylist.copy(epgUrl = epgUrl))
                    }
                }

                _activePlaylistUrl.value = url
                playlistDao.updateLastSyncTime(url, System.currentTimeMillis())

                // NÃO bloqueia sync para TMDB - a Home aparece imediatamente com posters do Xtream
                // TMDB enrichment roda 100% em background
                onProgress(100, 100, 100, "Concluído!")
            } else {
                return@withContext Result.failure(Exception("Nenhum conteúdo encontrado na lista. Verifique a URL."))
            }

            launchBackgroundEnrichment(url, onProgress)
            Result.success(Unit)
        } catch (e: Throwable) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            isFullSyncRunning.set(false)
            deltaSyncAborted.set(false)
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
            // === PRIORIDADE 1: Seed rápido da Home (3-5 filmes com TMDB) ===
            // Isso faz os banners aparecerem na Home poucos segundos após o app abrir
            val featuredCount = channelDao.getFeaturedContent(url).first().size
            if (featuredCount < 3) {
                android.util.Log.d("CineX-Home", "Iniciando seed rápido da Home em background...")
                try {
                    ensureHomeFeaturedSeed(url, minFeatured = 3)
                    android.util.Log.d("CineX-Home", "Seed da Home concluído")
                } catch (e: Exception) {
                    android.util.Log.e("CineX-Home", "Seed falhou: ${e.message}")
                }
            }

            // === PRIORIDADE 2: Pre-fetch Supabase cache (batch) ===
            // PROTEÇÃO TÉRMICA: Limitar a 500 itens por sessão para não fritar o celular
            val allToEnrich = (channelDao.getMoviesToEnrich(url).take(300) + channelDao.getSeriesToEnrich(url).take(200))
            if (allToEnrich.isNotEmpty()) {
                android.util.Log.d("CineX-Cache", "Pre-fetching TMDB cache for ${allToEnrich.size} items...")
                allToEnrich.chunked(100).forEach { batch ->
                    try { prefetchTmdbCache(batch) } catch (_: Exception) {}
                    delay(500) // Pausa para rede/GPU respirar
                }
            }

            // === PRIORIDADE 3: Enriquecer o resto em background ===
            // PROTEÇÃO TÉRMICA: Nunca processar os 16k filmes de uma vez.
            val moviesToEnrich = channelDao.getMoviesToEnrich(url).take(200)
            val seriesToEnrich = channelDao.getSeriesToEnrich(url).take(100)

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
                    delay(2000) // 2s de pausa térmica para o celular não aquecer (antes: 50ms)
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
                    delay(2000) // 2s pausa térmica
                }
            }

            flushTmdbCacheQueue()
        }
    }

    /**
     * Delta sync: baixa a playlist, compara com o banco local e só insere novos / remove deletados.
     * Retorna a quantidade de canais novos adicionados.
     */
    suspend fun syncPlaylistDelta(): Result<Int> = withContext(Dispatchers.IO) {
        // Se full sync está rodando ou sinalizou abort, desiste
        if (isFullSyncRunning.get() || deltaSyncAborted.get()) return@withContext Result.success(0)
        try {
            val url = _activePlaylistUrl.value ?: return@withContext Result.success(0)
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

            if (newChannels.isEmpty() || deltaSyncAborted.get()) return@withContext Result.success(0)

            // Comparar com o banco local
            val existingIds = channelDao.getAllRemoteIds(url).toSet()
            val newIds = newChannels.map { it.remoteId }.toSet()
            val newChannelsByRemoteId = newChannels.associateBy { it.remoteId }

            if (deltaSyncAborted.get()) return@withContext Result.success(0)

            // Proteção: só bloqueia deleção se o servidor retornou menos de 50 canais
            // (resposta vazia/parcial por erro de rede). Se retornou 50+, é lista real
            // e pode ter encolhido legitimamente — deletar os removidos é o comportamento correto.
            val removedIds = existingIds - newIds
            val safeToDelete = newChannels.size >= 50
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

            // Atualizar canais Live TV existentes que mudaram (nome, grupo — ex: "Jogos do Dia" muda todo dia)
            // Só Live TV porque filmes/séries raramente mudam nome e carregar 16k+ canais é pesado
            var updatedCount = 0
            val liveChannelsToCheck = newChannels.filter { it.category == "LIVE_TV" && it.remoteId in existingIds }
            if (liveChannelsToCheck.isNotEmpty()) {
                val existingLive = channelDao.getChannelsByCategoryList("LIVE_TV", url)
                    .associateBy { it.remoteId }
                liveChannelsToCheck.forEach { fresh ->
                    val existing = existingLive[fresh.remoteId] ?: return@forEach
                    if (existing.name != fresh.name || existing.groupTitle != fresh.groupTitle) {
                        channelDao.updateVolatileFields(
                            playlistUrl = url,
                            remoteId = fresh.remoteId,
                            name = fresh.name,
                            groupTitle = fresh.groupTitle,
                            streamUrl = fresh.streamUrl,
                            categoryId = fresh.categoryId
                        )
                        updatedCount++
                    }
                }
            }

            // Atualizar timestamp do último sync
            playlistDao.updateLastSyncTime(url, System.currentTimeMillis())

            // Reconstruir categorias a partir da lista atual do servidor:
            // remove categorias órfãs (ex: "CineX Turbo" removida do servidor)
            // e garante que categorias novas apareçam sem precisar de sync completo.
            rebuildCategoriesFromChannels(url, newChannels)

            // Retorna total de mudanças: novos + atualizados + removidos
            // Isso garante que o cache de paging seja limpo sempre que houver qualquer alteração
            val removedCount = if (safeToDelete) removedIds.size else 0
            Result.success(channelsToInsert.size + updatedCount + removedCount)
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
            android.util.Log.d("CineX-Sync", "=== FETCH XTREAM CHANNELS ===")
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

            android.util.Log.d("CineX-Sync", "Live cats: ${liveCats.size}, VOD cats: ${vodCats.size}, Series cats: ${seriesCats.size}")

            val liveDeferred = async { api.getLiveStreams(user, pass) }
            val vodDeferred = async { api.getVodStreams(user, pass) }
            val seriesDeferred = async { api.getSeries(user, pass) }

            val channels = mutableListOf<Channel>()

            val liveStreams = liveDeferred.await()
            android.util.Log.d("CineX-Sync", "Live streams (bulk): ${liveStreams.size}")
            liveStreams.forEachIndexed { index, stream ->
                val safeName = stream.name.orEmpty().ifBlank { "Canal #${stream.stream_id}" }
                channels.add(Channel(
                    name = safeName,
                    logoUrl = stream.stream_icon,
                    groupTitle = liveCats[stream.category_id]?.category_name ?: "Live",
                    categoryId = buildStoredCategoryId("LIVE_TV", stream.category_id),
                    streamUrl = "${baseUrl}live/$user/$pass/${stream.stream_id}.ts",
                    category = "LIVE_TV",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "live_${stream.stream_id}",
                    tvgId = stream.epg_channel_id
                ))
            }

            val vodStreams = vodDeferred.await()
            android.util.Log.d("CineX-Sync", "VOD streams (bulk): ${vodStreams.size}")
            vodStreams.forEachIndexed { index, m ->
                val ext = m.container_extension ?: "mp4"
                val safeName = m.name.orEmpty().ifBlank { "Filme #${m.stream_id}" }
                channels.add(Channel(
                    name = safeName,
                    logoUrl = m.stream_icon,
                    groupTitle = vodCats[m.category_id]?.category_name ?: "VOD",
                    categoryId = buildStoredCategoryId("MOVIE", m.category_id),
                    streamUrl = "${baseUrl}movie/$user/$pass/${m.stream_id}.$ext",
                    category = "MOVIE",
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "vod_${m.stream_id}",
                    syncedAt = m.addedTimestamp()
                ))
            }

            val seriesList = seriesDeferred.await()
            android.util.Log.d("CineX-Sync", "Series (bulk): ${seriesList.size}")
            seriesList.forEachIndexed { index, s ->
                val safeName = s.name.orEmpty().ifBlank { "Série #${s.series_id}" }
                channels.add(Channel(
                    name = safeName,
                    logoUrl = s.cover,
                    groupTitle = seriesCats[s.category_id]?.category_name ?: "SÉRIES",
                    categoryId = buildStoredCategoryId("SERIES", s.category_id),
                    streamUrl = "",
                    category = "SERIES",
                    seriesName = s.name,
                    playlistUrl = playlistUrl,
                    orderIndex = index,
                    remoteId = "series_${s.series_id}",
                    syncedAt = s.lastModifiedTimestamp()
                ))
            }

            android.util.Log.d("CineX-Sync", "=== TOTAL FETCH === Live: ${liveStreams.size}, VOD: ${vodStreams.size}, Series: ${seriesList.size}, Total: ${channels.size}")
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
            parsed.map { channel ->
                channel.copy(categoryId = buildStoredCategoryId(channel.category, channel.groupTitle))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun buildStoredCategoryId(category: String, rawIdOrGroup: String): String {
        val isNumericId = rawIdOrGroup.all { it.isDigit() }
        return when (category) {
            "LIVE_TV" -> when {
                rawIdOrGroup.startsWith("live_") || rawIdOrGroup.startsWith("live_tv_") -> rawIdOrGroup
                isNumericId -> "live_$rawIdOrGroup"
                else -> "live_tv_$rawIdOrGroup"
            }
            "MOVIE" -> when {
                rawIdOrGroup.startsWith("vod_") || rawIdOrGroup.startsWith("movie_") -> rawIdOrGroup
                isNumericId -> "vod_$rawIdOrGroup"
                else -> "movie_$rawIdOrGroup"
            }
            "SERIES" -> if (rawIdOrGroup.startsWith("series_")) rawIdOrGroup else "series_$rawIdOrGroup"
            else -> rawIdOrGroup
        }
    }

    private suspend fun rebuildCategoriesFromChannels(playlistUrl: String, channels: List<Channel>) {
        val rebuiltCategories = channels
            .filter { it.categoryId.isNotBlank() }
            .groupBy { it.categoryId }
            .entries
            .sortedBy { (_, groupedChannels) -> groupedChannels.minOfOrNull { it.orderIndex } ?: Int.MAX_VALUE }
            .mapIndexed { index, (categoryId, groupedChannels) ->
                val type = groupedChannels
                    .groupingBy { it.category }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: groupedChannels.first().category
                val name = groupedChannels
                    .groupingBy { it.groupTitle }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: groupedChannels.first().groupTitle

                com.cinex.player.data.model.Category(
                    id = categoryId,
                    name = name,
                    type = type,
                    playlistUrl = playlistUrl,
                    orderIndex = index
                )
            }

        categoryDao.clearByPlaylist(playlistUrl)
        if (rebuiltCategories.isNotEmpty()) {
            categoryDao.insertAll(rebuiltCategories)
        }
    }

    /** Tipos já carregados nesta sessão — evita re-download desnecessário */
    @Volatile private var loadedTypes = mutableSetOf<String>()

    /** Mutexes por tipo — evita downloads duplicados quando prefetch e tab-click coincidem */
    private val typeMutexes = mapOf(
        "LIVE_TV" to Mutex(),
        "MOVIE" to Mutex(),
        "SERIES" to Mutex()
    )

    /** Limita HTTP requests paralelos ao servidor Xtream (compartilhado entre os 3 tipos) */
    private val fetchSemaphore = Semaphore(6)

    /**
     * syncXtream agora só busca CATEGORIAS (~1 segundo).
     * O conteúdo (streams) é carregado sob demanda por ensureTypeLoaded().
     */
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

            // Extrair credenciais para o fetch
            val uri = android.net.Uri.parse(playlistUrl)
            val username = uri.getQueryParameter("username") ?: ""
            val password = uri.getQueryParameter("password") ?: ""

            android.util.Log.d("CineX-Sync", "=== SYNC XTREAM (LAZY) — SÓ CATEGORIAS ===")
            onProgress(20, 20, 20, "Buscando categorias...")

            val liveCats = fetchListRelaxed(baseUrl, username, password, "get_live_categories", null, com.cinex.player.data.network.XtreamCategory::class.java)
            android.util.Log.d("CineX-Sync", "Live cats: ${liveCats.size}")
            
            val vodCats = fetchListRelaxed(baseUrl, username, password, "get_vod_categories", null, com.cinex.player.data.network.XtreamCategory::class.java)
            android.util.Log.d("CineX-Sync", "VOD cats: ${vodCats.size}")
            
            val seriesCats = fetchListRelaxed(baseUrl, username, password, "get_series_categories", null, com.cinex.player.data.network.XtreamCategory::class.java)
            android.util.Log.d("CineX-Sync", "Series cats: ${seriesCats.size}")

            // decodeUnicodeEscapes removido daqui, usando o top-level decodeUnicode

            // Reconstrói categorias do zero: limpa as antigas e insere as atuais do servidor.
            // Isso remove categorias órfãs (ex: "CineX Turbo" que o servidor removeu).
            withContext(Dispatchers.IO) {
                val allCats = mutableListOf<com.cinex.player.data.model.Category>()
                allCats += liveCats.mapIndexed { index, cat ->
                    com.cinex.player.data.model.Category(
                        "live_${cat.category_id}",
                        decodeUnicode(cat.category_name) ?: "Categoria",
                        "LIVE_TV",
                        playlistUrl,
                        orderIndex = index
                    )
                }
                allCats += vodCats.mapIndexed { index, cat ->
                    com.cinex.player.data.model.Category(
                        "vod_${cat.category_id}",
                        decodeUnicode(cat.category_name) ?: "Filmes",
                        "MOVIE",
                        playlistUrl,
                        orderIndex = index
                    )
                }
                allCats += seriesCats.mapIndexed { index, cat ->
                    com.cinex.player.data.model.Category(
                        "series_${cat.category_id}",
                        decodeUnicode(cat.category_name) ?: "Séries",
                        "SERIES",
                        playlistUrl,
                        orderIndex = index
                    )
                }
                if (allCats.isNotEmpty()) {
                    categoryDao.clearByPlaylist(playlistUrl)
                    categoryDao.insertAll(allCats)
                }
            }

            // Limpa canais antigos — remove dados corrompidos de syncs anteriores.
            // TMDB e favoritos são preservados dentro do ensureTypeLoaded (carregados antes de inserir).
            channelDao.clearByPlaylist(playlistUrl)
            android.util.Log.d("CineX-Sync", "Canais limpos — conteúdo será re-baixado por demanda")

            // Limpa flags de tipos carregados — forçar re-download na próxima navegação
            loadedTypes.clear()

            playlistDao.updateLastSyncTime(playlistUrl, System.currentTimeMillis())

            onProgress(100, 100, 100, "Concluído!")
            android.util.Log.d("CineX-Sync", "=== SYNC XTREAM (LAZY) CONCLUÍDO — categorias salvas, conteúdo será carregado por demanda ===")
            Result.success(Unit)
        } catch (e: Throwable) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * SYNC COMPLETO (como TiviMate/XCIPTV fazem):
     * Baixa TODOS os canais/filmes/séries de TODAS as categorias.
     * Usado no primeiro sync ou quando usuário força "Sincronizar tudo".
     */
    suspend fun syncXtreamFull(
        baseUrl: String,
        user: String,
        pass: String,
        playlistUrl: String,
        onProgress: (livePct: Int, moviePct: Int, seriesPct: Int, status: String) -> Unit
    ): Result<Unit> = coroutineScope {
        try {
            // Primeiro sync das categorias
            val catResult = syncXtream(baseUrl, user, pass, playlistUrl, onProgress)
            if (catResult.isFailure) return@coroutineScope catResult

            onProgress(10, 10, 10, "Baixando TODOS os canais...")

            // TV: serializa os 3 tipos — 3 tipos em paralelo × 6 categorias simultâneas =
            // até 18 conexões HTTP concorrentes saturam o servidor IPTV e o heap da TV.
            // Mobile: mantém paralelismo para velocidade máxima.
            if (isTv) {
                ensureTypeLoaded("LIVE_TV", forceRefresh = true) { p -> onProgress(p, 10, 10, "Baixando TV ao vivo...") }
                onProgress(100, 10, 10, "TV ao vivo baixada!")

                ensureTypeLoaded("MOVIE", forceRefresh = true) { p -> onProgress(100, p/2 + 10, 10, "Baixando Filmes...") }
                onProgress(100, 100, 10, "Filmes baixados!")

                ensureTypeLoaded("SERIES", forceRefresh = true) { p -> onProgress(100, 100, p/2 + 10, "Baixando Séries...") }
                onProgress(100, 100, 100, "Séries baixadas!")
            } else {
                coroutineScope {
                    val liveDeferred = async { ensureTypeLoaded("LIVE_TV", forceRefresh = true) { p -> onProgress(p, 10, 10, "Canais...") } }
                    val movieDeferred = async { ensureTypeLoaded("MOVIE", forceRefresh = true) { p -> onProgress(100, p, 10, "Filmes...") } }
                    val seriesDeferred = async { ensureTypeLoaded("SERIES", forceRefresh = true) { p -> onProgress(100, 100, p, "Séries...") } }

                    liveDeferred.await()
                    movieDeferred.await()
                    seriesDeferred.await()
                }
            }

            val liveCount = channelDao.countByType("LIVE_TV", playlistUrl)
            val movieCount = channelDao.countByType("MOVIE", playlistUrl)
            val seriesCount = channelDao.countByType("SERIES", playlistUrl)
            android.util.Log.d("CineX-Sync", "=== SYNC COMPLETO === Live: $liveCount, Movies: $movieCount, Series: $seriesCount")

            onProgress(100, 100, 100, "Concluído! $liveCount canais, $movieCount filmes, $seriesCount séries")
            Result.success(Unit)
        } catch (e: Throwable) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Carrega conteúdo de um tipo (LIVE_TV, MOVIE, SERIES).
     * Mutex por tipo evita downloads duplicados (sync + tab-click simultâneo).
     * Categorias são baixadas em paralelo (5 simultâneas) para máxima velocidade.
     */
    suspend fun ensureTypeLoaded(type: String, forceRefresh: Boolean = false, onProgress: ((Int) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val url = _activePlaylistUrl.value ?: return@withContext Result.failure(Exception("Nenhuma playlist ativa"))

        // Mutex: se outra coroutine já está carregando este tipo, espera ela terminar
        val mutex = typeMutexes[type] ?: return@withContext Result.failure(Exception("Tipo desconhecido: $type"))
        mutex.withLock {
            if (!forceRefresh) {
                val existingCount = channelDao.countByType(type, url)
                if (existingCount > 0) {
                    loadedTypes.add(type)
                    android.util.Log.d("CineX-Sync", "ensureTypeLoaded($type): $existingCount já no banco, skip")
                    return@withContext Result.success(Unit)
                }
            } else {
                android.util.Log.d("CineX-Sync", "ensureTypeLoaded($type): forceRefresh=true, baixando tudo do servidor...")
            }

            android.util.Log.d("CineX-Sync", "ensureTypeLoaded($type): carregando do servidor...")
            val syncStartTime = System.currentTimeMillis()

        try {
            // Extrair credenciais Xtream da URL da playlist
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username") ?: return@withContext Result.failure(Exception("URL sem credenciais Xtream"))
            val password = uri.getQueryParameter("password") ?: return@withContext Result.failure(Exception("URL sem credenciais Xtream"))
            val host = uri.host ?: return@withContext Result.failure(Exception("URL inválida"))
            val scheme = uri.scheme
            val port = uri.port
            val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"

            // Preservar dados do usuário existentes (favoritos, resume, TMDB)
            val existingTmdbData = channelDao.getTmdbAndUserDataByPlaylist(url)
                .filter { it.remoteId.startsWith(when(type) { "LIVE_TV" -> "live_"; "MOVIE" -> "vod_"; "SERIES" -> "series_"; else -> "" }) }
                .associateBy { it.remoteId }

            // forceRefresh: limpa dados antigos APÓS salvar os dados TMDB/usuário.
            // Isso remove itens corrompidos/orphaned de syncs anteriores quebrados.
            if (forceRefresh) {
                channelDao.clearByCategory(type, url)
                android.util.Log.d("CineX-Sync", "ensureTypeLoaded($type): banco limpo (${existingTmdbData.size} registros TMDB preservados)")
            }

            // Buscar categorias do banco para mapear nomes
            val categories = categoryDao.getAllByPlaylist(url)

            // Estimate total por tipo para barra de progresso (generoso para não travar em 99%)
            val estimatedTotal = when (type) { "LIVE_TV" -> 3000; "MOVIE" -> 20000; else -> 10000 }

            when (type) {
                "LIVE_TV" -> {
                    val catsMap = categories.filter { it.type == "LIVE_TV" }.associateBy { it.id.removePrefix("live_") }
                    android.util.Log.d("CineX-Sync", "LIVE: Streaming fetch iniciado (${catsMap.size} categorias)")
                    val inserted = streamingFetch(
                        baseUrl, username, password, "get_live_streams", null,
                        LiveStreamItem::class.java, "stream_id", estimatedTotal, onProgress = onProgress
                    ) { stream, index ->
                        val old = existingTmdbData["live_${stream.stream_id}"]
                        Channel(
                            name = stream.name.orEmpty().ifBlank { "Canal #${stream.stream_id}" },
                            logoUrl = stream.stream_icon,
                            groupTitle = decodeUnicode(catsMap[stream.category_id]?.name) ?: "Live",
                            categoryId = buildStoredCategoryId("LIVE_TV", stream.category_id),
                            streamUrl = "${baseUrl}live/$username/$password/${stream.stream_id}.ts",
                            category = "LIVE_TV",
                            playlistUrl = url,
                            orderIndex = index,
                            remoteId = "live_${stream.stream_id}",
                            tvgId = stream.epg_channel_id,
                            isFavorite = old?.isFavorite ?: false
                        )
                    }
                    android.util.Log.d("CineX-Sync", "LIVE: ✅ $inserted canais inseridos")
                    if (inserted == 0) android.util.Log.w("CineX-Sync", "LIVE: ⚠️ Nenhum canal inserido")
                }
                "MOVIE" -> {
                    val catsMap = categories.filter { it.type == "MOVIE" }.associateBy { it.id.removePrefix("vod_") }
                    android.util.Log.d("CineX-Sync", "MOVIE: Streaming fetch iniciado (${catsMap.size} categorias)")
                    val inserted = streamingFetch(
                        baseUrl, username, password, "get_vod_streams", null,
                        VodStreamItem::class.java, "stream_id", estimatedTotal, onProgress = onProgress
                    ) { m, index ->
                        val ext = m.container_extension ?: "mp4"
                        val old = existingTmdbData["vod_${m.stream_id}"]
                        Channel(
                            name = m.name.orEmpty().ifBlank { "Filme #${m.stream_id}" },
                            logoUrl = m.stream_icon,
                            groupTitle = decodeUnicode(catsMap[m.category_id]?.name) ?: "VOD",
                            categoryId = buildStoredCategoryId("MOVIE", m.category_id),
                            streamUrl = "${baseUrl}movie/$username/$password/${m.stream_id}.$ext",
                            category = "MOVIE",
                            playlistUrl = url,
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
                            trailerUrl = old?.trailerUrl,
                            syncedAt = m.addedTimestamp()
                        )
                    }
                    android.util.Log.d("CineX-Sync", "MOVIE: ✅ $inserted filmes inseridos")
                    if (inserted == 0) android.util.Log.w("CineX-Sync", "MOVIE: ⚠️ Nenhum filme inserido — servidor pode ter JSON inválido")
                }
                "SERIES" -> {
                    val catsMap = categories.filter { it.type == "SERIES" }.associateBy { it.id.removePrefix("series_") }
                    android.util.Log.d("CineX-Sync", "SERIES: Streaming fetch iniciado (${catsMap.size} categorias)")
                    val inserted = streamingFetch(
                        baseUrl, username, password, "get_series", null,
                        SeriesItem::class.java, "series_id", estimatedTotal, onProgress = onProgress
                    ) { s, index ->
                        val old = existingTmdbData["series_${s.series_id}"]
                        Channel(
                            name = s.name.orEmpty().ifBlank { "Série #${s.series_id}" },
                            logoUrl = s.cover,
                            groupTitle = decodeUnicode(catsMap[s.category_id]?.name) ?: "SÉRIES",
                            categoryId = buildStoredCategoryId("SERIES", s.category_id),
                            streamUrl = "",
                            category = "SERIES",
                            seriesName = s.name,
                            playlistUrl = url,
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
                            trailerUrl = old?.trailerUrl,
                            syncedAt = s.lastModifiedTimestamp()
                        )
                    }
                    android.util.Log.d("CineX-Sync", "SERIES: ✅ $inserted séries inseridas")
                    if (inserted == 0) android.util.Log.w("CineX-Sync", "SERIES: ⚠️ Nenhuma série inserida")
                }
            }

            loadedTypes.add(type)
            val elapsed = System.currentTimeMillis() - syncStartTime
            val finalCount = channelDao.countByType(type, url)
            android.util.Log.d("CineX-Sync", "✅ ensureTypeLoaded($type): concluído! $finalCount itens em ${elapsed}ms")
            Result.success(Unit)
        } catch (e: Throwable) {
            android.util.Log.e("CineX-Sync", "❌ ensureTypeLoaded($type) falhou: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
        } // fim mutex.withLock
    }

    private val tmdbApiKey = "4f4a90cce11b368ad0235f2b82ba672a"

    // === SUPABASE TMDB CACHE ===
    private val PANEL_BASE_URL = "https://gerencia-cine-x.vercel.app"
    private val TMDB_CACHE_TTL_DAYS = 30L

    // Cache em memória para evitar chamadas repetidas ao Supabase na mesma sessão
    private data class TmdbCacheEntry(
        val posterUrl: String?,
        val bannerUrl: String?,
        val synopsis: String?,
        val rating: Double?,
        val year: String?,
        val trailerUrl: String?,
        val castMembers: String?
    )
    private val tmdbMemoryCache = java.util.concurrent.ConcurrentHashMap<String, TmdbCacheEntry?>()

    /**
     * Normaliza o title para gerar uma cache key universal.
     * Ex: "FHD | Barbie (2023) DUBLADO" → "movie:barbie 2023"
     */
    private fun generateCacheKey(channel: Channel): String {
        val isMovie = channel.category == "MOVIE"
        val prefix = if (isMovie) "movie" else "tv"

        val rawName = if (!isMovie && !channel.seriesName.isNullOrBlank()) {
            channel.seriesName
        } else {
            channel.name
        }

        // Extrair ano se presente
        val yearMatch = Regex("(?i)\\(?(\\d{4})\\)?").find(rawName)
        val year = yearMatch?.groupValues?.get(1) ?: ""

        val cleaned = rawName
            .replace(Regex("(?i)\\(?\\d{4}\\)?"), "")
            .replace(Regex("(?i)\\b(fhd|hd|sd|4k|dual|legendado|dublado|multi|brrip|hdtv|web-dl|bluray|h264|h265|x264|x265|1080p|720p|480p)\\b"), "")
            .replace(Regex("(?i)s\\d+e\\d+.*"), "")
            .replace(Regex("[|\\-\\[\\]()]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        val normalized = java.text.Normalizer
            .normalize(cleaned, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (year.isNotEmpty()) "$prefix:$normalized $year" else "$prefix:$normalized"
    }

    /**
     * Busca dados TMDB do cache Supabase em batch.
     * Popula o tmdbMemoryCache com os resultados.
     */
    private suspend fun prefetchTmdbCache(channels: List<Channel>) {
        try {
            val keysToFetch = channels
                .filter { it.category != "LIVE_TV" }
                .map { generateCacheKey(it) }
                .distinct()
                .filter { !tmdbMemoryCache.containsKey(it) }
                .take(100)

            if (keysToFetch.isEmpty()) return

            val keysParam = java.net.URLEncoder.encode(keysToFetch.joinToString(","), "UTF-8")
            val url = "$PANEL_BASE_URL/api/tmdb-cache?keys=$keysParam"
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = org.json.JSONObject(bodyStr)
                val data = json.optJSONObject("data")

                if (data != null) {
                    for (key in keysToFetch) {
                        val entry = data.optJSONObject(key)
                        if (entry != null) {
                            tmdbMemoryCache[key] = TmdbCacheEntry(
                                posterUrl = entry.optString("poster_url", "").ifEmpty { null },
                                bannerUrl = entry.optString("banner_url", "").ifEmpty { null },
                                synopsis = entry.optString("synopsis", "").ifEmpty { null },
                                rating = entry.optDouble("rating").takeIf { !it.isNaN() },
                                year = entry.optString("year", "").ifEmpty { null },
                                trailerUrl = entry.optString("trailer_url", "").ifEmpty { null },
                                castMembers = entry.optString("cast_members", "").ifEmpty { null }
                            )
                        } else {
                            // Marca como "não existe no cache" para não buscar de novo
                            tmdbMemoryCache[key] = null
                        }
                    }
                }

                android.util.Log.d("CineX-Cache", "Prefetch: ${keysToFetch.size} keys, ${data?.length() ?: 0} hits")
            }
        } catch (e: Exception) {
            android.util.Log.e("CineX-Cache", "Prefetch failed: ${e.message}")
        }
    }

    /**
     * Salva dados TMDB no cache Supabase em batch.
     */
    private val tmdbCacheSaveQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, TmdbCacheEntry>>()

    private fun queueTmdbCacheSave(cacheKey: String, entry: TmdbCacheEntry) {
        tmdbCacheSaveQueue.add(cacheKey to entry)
        // Flush quando acumular 20 entradas
        if (tmdbCacheSaveQueue.size >= 20) {
            repositoryScope.launch { flushTmdbCacheQueue() }
        }
    }

    private suspend fun flushTmdbCacheQueue() {
        val batch = mutableListOf<Pair<String, TmdbCacheEntry>>()
        while (tmdbCacheSaveQueue.isNotEmpty() && batch.size < 50) {
            tmdbCacheSaveQueue.poll()?.let { batch.add(it) }
        }
        if (batch.isEmpty()) return

        try {
            val entries = org.json.JSONArray()
            for ((key, entry) in batch) {
                entries.put(org.json.JSONObject().apply {
                    put("cache_key", key)
                    put("poster_url", entry.posterUrl ?: org.json.JSONObject.NULL)
                    put("banner_url", entry.bannerUrl ?: org.json.JSONObject.NULL)
                    put("synopsis", entry.synopsis ?: org.json.JSONObject.NULL)
                    put("rating", entry.rating ?: org.json.JSONObject.NULL)
                    put("year", entry.year ?: org.json.JSONObject.NULL)
                    put("trailer_url", entry.trailerUrl ?: org.json.JSONObject.NULL)
                    put("cast_members", entry.castMembers ?: org.json.JSONObject.NULL)
                })
            }

            val body = org.json.JSONObject().put("entries", entries).toString()
            val request = Request.Builder()
                .url("$PANEL_BASE_URL/api/tmdb-cache")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().close()
            android.util.Log.d("CineX-Cache", "Saved ${batch.size} entries to Supabase cache")
        } catch (e: Exception) {
            android.util.Log.e("CineX-Cache", "Save to Supabase failed: ${e.message}")
            // Re-enfileirar as entradas que falharam
            batch.forEach { tmdbCacheSaveQueue.add(it) }
        }
    }

    suspend fun updateFavorite(channelId: Int, isFav: Boolean) = withContext(Dispatchers.IO) {
        channelDao.updateFavorite(channelId, isFav)
    }

    suspend fun updateResumePosition(channelId: Int, position: Long, duration: Long) {
        channelDao.updateResumePosition(channelId, position, duration)
        // Propaga a capa da série para o episódio (para "Continuar Assistindo" mostrar a capa correta)
        propagateSeriesPoster(channelId)
        // Marca a série pai como assistida para aparecer no "Continuar Assistindo"
        val url = _activePlaylistUrl.value ?: return
        val episode = channelDao.getChannelById(channelId) ?: return
        if (episode.category == "SERIES" && !episode.seriesName.isNullOrEmpty()) {
            channelDao.markSeriesParentAsWatched(episode.seriesName, url)
        }
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
            // === SUPABASE CACHE CHECK ===
            val cacheKey = generateCacheKey(channel)
            var cached = tmdbMemoryCache[cacheKey]
            // Invalida cache se o bannerUrl ainda usa /w1280/ (formato antigo, não passa no filtro getFeaturedContent)
            if (cached != null && cached.bannerUrl?.contains("/t/p/w1280") == true) {
                android.util.Log.d("CineX-Cache", "[STALE] '$cacheKey' → banner w1280, invalidando cache")
                tmdbMemoryCache.remove(cacheKey)
                cached = null
            }
            if (cached != null && (cached.posterUrl != null || cached.synopsis != null)) {
                android.util.Log.d("CineX-Cache", "[HIT] '$cacheKey' → poster=${cached.posterUrl != null}")
                if (channel.category == "SERIES" && channel.seriesName != null) {
                    channelDao.updateTmdbInfo(
                        channel.id,
                        cached.rating,
                        cached.synopsis,
                        cached.posterUrl,
                        cached.bannerUrl,
                        cached.year,
                        cached.castMembers,
                        cached.trailerUrl
                    )
                    channelDao.propagateSeriesBackdrop(
                        channel.seriesName!!,
                        channel.playlistUrl,
                        cached.rating,
                        cached.posterUrl,
                        cached.bannerUrl,
                        cached.year,
                        cached.castMembers
                    )
                } else {
                    channelDao.updateTmdbInfo(
                        channel.id,
                        cached.rating,
                        cached.synopsis,
                        cached.posterUrl,
                        cached.bannerUrl,
                        cached.year,
                        cached.castMembers,
                        cached.trailerUrl
                    )
                }
                return@withContext
            }
            // === END CACHE CHECK ===

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
                // CRÍTICO: usa /original/ para que getFeaturedContent (LIKE '%/original/%') aceite este backdrop
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

                    // NOTA: thumbnails/sinopses por episódio são carregados lazy em
                    // enrichSeriesSeasonWithTmdb() quando o usuário abre uma temporada.
                    // Manter o loop aqui gerava 1 write por episódio (centenas por série),
                    // disparando re-emissões de observeAllSeries() e travando a TV.
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

                // === SALVAR NO CACHE SUPABASE ===
                val cacheEntry = TmdbCacheEntry(
                    posterUrl = posterUrl,
                    bannerUrl = backdropUrl,
                    synopsis = details.overview,
                    rating = tmdbResult.vote_average,
                    year = year,
                    trailerUrl = trailerUrl,
                    castMembers = cast
                )
                tmdbMemoryCache[cacheKey] = cacheEntry
                queueTmdbCacheSave(cacheKey, cacheEntry)
                // === END CACHE SAVE ===
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

    suspend fun fetchAndStoreEpisodes(seriesId: Int, seriesName: String, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val url = _activePlaylistUrl.value ?: return@withContext

        // Se já tem episódios no banco e não é refresh forçado, retorna imediatamente
        if (!forceRefresh && channelDao.countEpisodesForSeries(seriesName, url) > 1) return@withContext

        try {
            val uri = android.net.Uri.parse(url)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            val host = uri.host
            val scheme = uri.scheme
            val port = uri.port

            if (username == null || password == null || host == null) return@withContext

            val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
            val fetchUrl = "${baseUrl}player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"

            // Cliente dedicado: callTimeout garante tempo TOTAL máximo (readTimeout é só por pacote)
            val seriesClient = okHttpClient.newBuilder()
                .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(fetchUrl).build()

            // Preservar progresso/imagens existentes antes de inserir
            val existingEpisodes = channelDao.getEpisodesForSeriesList(seriesName, url)
                .associateBy { it.remoteId }

            seriesClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val body = response.body ?: return@withContext

                // Streaming parser: processa o JSON conforme os bytes chegam, insere cada
                // temporada no banco assim que terminar de lê-la. O Room emite no Flow e
                // a UI mostra a primeira temporada em poucos segundos — não precisa esperar
                // o JSON inteiro (AHS tem ~12 temporadas / 150+ episódios).
                val reader = com.google.gson.stream.JsonReader(body.charStream().buffered())
                reader.isLenient = true

                try {
                    if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_OBJECT) return@withContext
                    reader.beginObject()

                    while (reader.hasNext()) {
                        val name = try { reader.nextName() } catch (_: Exception) {
                            try { reader.skipValue() } catch (_: Exception) {}
                            continue
                        }
                        if (name == "episodes" && reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val seasonKey = try { reader.nextName() } catch (_: Exception) {
                                    try { reader.skipValue() } catch (_: Exception) {}
                                    continue
                                }
                                val seasonNum = seasonKey.toIntOrNull() ?: 1
                                if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                                    try { reader.skipValue() } catch (_: Exception) {}
                                    continue
                                }
                                reader.beginArray()
                                val seasonChannels = ArrayList<Channel>()
                                while (reader.hasNext()) {
                                    val ch = parseEpisodeToChannel(
                                        reader = reader,
                                        seriesId = seriesId,
                                        seriesName = seriesName,
                                        seasonNum = seasonNum,
                                        baseUrl = baseUrl,
                                        username = username,
                                        password = password,
                                        playlistUrl = url,
                                        existingEpisodes = existingEpisodes
                                    )
                                    if (ch != null) seasonChannels.add(ch)
                                }
                                try { reader.endArray() } catch (_: Exception) {}
                                // Insert por temporada → Flow emite imediatamente → UI aparece
                                if (seasonChannels.isNotEmpty()) {
                                    channelDao.insertAll(seasonChannels)
                                }
                            }
                            try { reader.endObject() } catch (_: Exception) {}
                        } else {
                            try { reader.skipValue() } catch (_: Exception) {}
                        }
                    }
                    try { reader.endObject() } catch (_: Exception) {}
                } finally {
                    try { reader.close() } catch (_: Exception) {}
                }

                // Grava timestamp de sucesso para controle de TTL
                seriesEpisodesFetchedAt[seriesName] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Lê UM objeto-episódio do JsonReader (streaming) e mapeia para Channel.
     * Tolera campos ausentes/malformados — pula o que não entende.
     */
    private fun parseEpisodeToChannel(
        reader: com.google.gson.stream.JsonReader,
        seriesId: Int,
        seriesName: String,
        seasonNum: Int,
        baseUrl: String,
        username: String,
        password: String,
        playlistUrl: String,
        existingEpisodes: Map<String, Channel>
    ): Channel? {
        if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
            try { reader.skipValue() } catch (_: Exception) {}
            return null
        }
        var id: String? = null
        var title: String = ""
        var episodeNum: Int = 0
        var containerExt: String? = "mp4"
        var movieImage: String? = null
        var plot: String? = null
        var rating: String? = null
        var releaseDate: String? = null

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val field = try { reader.nextName() } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                    continue
                }
                try {
                    when (field) {
                        "id" -> id = readStringLoose(reader)
                        "title" -> title = readStringLoose(reader) ?: ""
                        "episode_num" -> episodeNum = readIntLoose(reader)
                        "container_extension" -> containerExt = readStringLoose(reader) ?: "mp4"
                        "info" -> {
                            if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val infoField = try { reader.nextName() } catch (_: Exception) {
                                        try { reader.skipValue() } catch (_: Exception) {}
                                        continue
                                    }
                                    when (infoField) {
                                        "movie_image" -> movieImage = readStringLoose(reader)
                                        "plot" -> plot = readStringLoose(reader)
                                        "rating" -> rating = readStringLoose(reader)
                                        "release_date" -> releaseDate = readStringLoose(reader)
                                        else -> try { reader.skipValue() } catch (_: Exception) {}
                                    }
                                }
                                try { reader.endObject() } catch (_: Exception) {}
                            } else {
                                try { reader.skipValue() } catch (_: Exception) {}
                            }
                        }
                        else -> try { reader.skipValue() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                }
            }
            try { reader.endObject() } catch (_: Exception) {}
        } catch (_: Exception) {
            return null
        }

        val epId = id ?: return null
        val remoteId = "series_ep_$epId"
        val old = existingEpisodes[remoteId]
        val apiImage = movieImage?.takeIf { it.isNotBlank() }
        return Channel(
            name = title,
            logoUrl = apiImage ?: old?.logoUrl,
            groupTitle = "Episódios",
            categoryId = "series_$seriesId",
            streamUrl = "${baseUrl}series/$username/$password/$epId.${containerExt ?: "mp4"}",
            category = "SERIES",
            seriesName = seriesName,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            playlistUrl = playlistUrl,
            remoteId = remoteId,
            tmdbSynopsis = plot ?: old?.tmdbSynopsis,
            tmdbRating = rating?.toDoubleOrNull() ?: old?.tmdbRating,
            tmdbYear = releaseDate?.take(4) ?: old?.tmdbYear,
            posterUrl = old?.posterUrl,
            bannerUrl = old?.bannerUrl,
            resumePosition = old?.resumePosition ?: 0L,
            totalDuration = old?.totalDuration ?: 0L,
            isFavorite = old?.isFavorite ?: false
        )
    }

    private fun readStringLoose(reader: com.google.gson.stream.JsonReader): String? {
        return try {
            when (reader.peek()) {
                com.google.gson.stream.JsonToken.STRING -> {
                    val s = reader.nextString()
                    s.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                }
                com.google.gson.stream.JsonToken.NUMBER -> reader.nextString()
                com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); null }
                else -> { reader.skipValue(); null }
            }
        } catch (_: Exception) {
            try { reader.skipValue() } catch (_: Exception) {}
            null
        }
    }

    private fun readIntLoose(reader: com.google.gson.stream.JsonReader): Int {
        return try {
            when (reader.peek()) {
                com.google.gson.stream.JsonToken.NUMBER -> reader.nextInt()
                com.google.gson.stream.JsonToken.STRING -> reader.nextString().toIntOrNull() ?: 0
                com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); 0 }
                else -> { reader.skipValue(); 0 }
            }
        } catch (_: Exception) {
            try { reader.skipValue() } catch (_: Exception) {}
            0
        }
    }

    private fun decodeUnicode(text: String?): String? {
        if (text == null) return null
        return try {
            text.replace(Regex("""\\u([0-9a-fA-F]{4})""")) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
        } catch (_: Exception) {
            text
        }
    }

    /**
     * Lê um objeto JSON campo-a-campo tolerando campos malformados.
     * Quando um campo tem sintaxe inválida (ex: rating_5based:3.4), pula só aquele campo
     * e preserva o objeto com os demais campos — igual a TiviMate/XCIPTV.
     */
    private fun readObjectLenient(reader: com.google.gson.stream.JsonReader): String {
        val sb = StringBuilder("{")
        var first = true
        try { reader.beginObject() } catch (_: Exception) { return "{}" }
        while (true) {
            try {
                if (!reader.hasNext()) break
                val name = try { reader.nextName() } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                    continue
                }
                val value: String? = try {
                    when (reader.peek()) {
                        com.google.gson.stream.JsonToken.STRING -> {
                            val s = reader.nextString()
                            "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                        }
                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextString()
                        com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                        com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); "null" }
                        com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                            val arr = StringBuilder("[")
                            reader.beginArray()
                            var af = true
                            while (reader.hasNext()) {
                                try {
                                    val v = when (reader.peek()) {
                                        com.google.gson.stream.JsonToken.STRING -> "\"${reader.nextString()}\""
                                        com.google.gson.stream.JsonToken.NUMBER -> reader.nextString()
                                        else -> { reader.skipValue(); null }
                                    }
                                    if (v != null) { if (!af) arr.append(","); arr.append(v); af = false }
                                } catch (_: Exception) { try { reader.skipValue() } catch (_: Exception) { break } }
                            }
                            try { reader.endArray() } catch (_: Exception) {}
                            arr.append("]"); arr.toString()
                        }
                        else -> { reader.skipValue(); null }
                    }
                } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                    null
                }
                if (value != null) {
                    if (!first) sb.append(",")
                    sb.append("\"${name.replace("\"", "\\\"")}\":$value")
                    first = false
                }
            } catch (_: Exception) {
                try { reader.skipValue() } catch (_: Exception) { break }
            }
        }
        try { reader.endObject() } catch (_: Exception) {}
        sb.append("}")
        return sb.toString()
    }

    /**
     * Streaming fetch direto da rede — nunca carrega o JSON inteiro na memória.
     * Lê um objeto JSON por vez da resposta HTTP, mapeia para Channel e insere em batches.
     * Usa readObjectLenient para tolerar JSON malformado (campos inválidos, truncamentos).
     * Pico de RAM: ~[batchSize] objetos Channel (~500), não os 16k+ do catálogo completo.
     *
     * @return total de itens inseridos
     */
    private suspend fun <T : Any> streamingFetch(
        baseUrl: String,
        user: String,
        pass: String,
        action: String,
        catId: String?,
        itemClass: Class<T>,
        idFieldName: String,
        estimatedTotal: Int,
        batchSize: Int = 500,
        onProgress: ((Int) -> Unit)? = null,
        mapper: (T, Int) -> Channel
    ): Int = withContext(Dispatchers.IO) {
        val fetchUrl = "${baseUrl}player_api.php?username=$user&password=$pass&action=$action${if (catId != null) "&category_id=$catId" else ""}"
        val request = Request.Builder().url(fetchUrl).build()
        val gson = com.google.gson.GsonBuilder().setLenient().create()

        var totalInserted = 0
        val batch = ArrayList<Channel>(batchSize)
        val seenIds = mutableSetOf<String>()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("CineX-Stream", "$action: HTTP ${response.code}")
                    return@withContext 0
                }
                val body = response.body ?: return@withContext 0

                val reader = com.google.gson.stream.JsonReader(body.charStream().buffered())
                reader.isLenient = true

                try {
                    if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                        android.util.Log.w("CineX-Stream", "$action: resposta não é array JSON")
                        return@withContext 0
                    }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        try {
                            val objJson = readObjectLenient(reader)
                            val item = try { gson.fromJson<T>(objJson, itemClass) } catch (_: Exception) { null }
                            if (item != null) {
                                val id = try {
                                    itemClass.getDeclaredField(idFieldName)
                                        .apply { isAccessible = true }
                                        .get(item)?.toString()
                                } catch (_: Exception) { null }

                                if (id != null && seenIds.add(id)) {
                                    batch.add(mapper(item, totalInserted + batch.size))
                                    if (batch.size >= batchSize) {
                                        channelDao.insertAll(batch.toList())
                                        totalInserted += batch.size
                                        batch.clear()
                                        val pct = (totalInserted * 100 / estimatedTotal).coerceIn(1, 99)
                                        onProgress?.invoke(pct)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            try { reader.skipValue() } catch (_: Exception) { break }
                        }
                    }
                    try { reader.endArray() } catch (_: Exception) {}
                } finally {
                    try { reader.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CineX-Stream", "$action: erro de streaming: ${e.message}")
        }

        // Flush último batch parcial
        if (batch.isNotEmpty()) {
            try {
                channelDao.insertAll(batch.toList())
                totalInserted += batch.size
            } catch (e: Exception) {
                android.util.Log.e("CineX-Stream", "$action: erro no batch final: ${e.message}")
            }
        }
        if (totalInserted > 0) onProgress?.invoke(100)

        totalInserted
    }

    /**
     * Helper para fetch com fallback relaxed (JSON truncado/malformado via Streaming)
     * Utiliza Brute-Force Regex para recuperar itens que quebram o parser JSON.
     */
    private suspend fun <T : Any> fetchListRelaxed(
        baseUrl: String,
        user: String,
        pass: String,
        action: String,
        catId: String?,
        itemClass: Class<T>
    ): List<T> {
        val fetchUrl = "${baseUrl}player_api.php?username=$user&password=$pass&action=$action${if (catId != null) "&category_id=$catId" else ""}"
        val request = Request.Builder().url(fetchUrl).build()
        val gson = com.google.gson.GsonBuilder().setLenient().create()
        val resultList = mutableListOf<T>()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("CineX-Sync", "$action: HTTP ${response.code}")
                return emptyList()
            }

            var jsonStr = response.body?.string() ?: ""
            jsonStr = jsonStr.trim()

            if (jsonStr.isEmpty()) {
                android.util.Log.w("CineX-Sync", "$action: Resposta vazia")
                return emptyList()
            }

            // PRÉ-PROCESSAMENTO: Corrigir problemas em JSON de servidores Xtream
            android.util.Log.d("CineX-Sync", "$action: Analisando JSON (${jsonStr.length} chars)...")

            // SOMENTE corrigir backslashes soltos (não escapados) que quebram o parser
            // NÃO tocar em unicode escapes (\uXXXX) - eles são válidos mesmo se incompletos no meio do truncamento
            // Regex: encontra \ que não está escapando nada válido
            val beforeLength = jsonStr.length
            jsonStr = jsonStr.replace(Regex("""(?<!\\)\\(?!["\\/bfnrtu\\])""")) { match ->
                android.util.Log.d("CineX-Sync", "$action: Backslash solto escapado")
                "\\\\"
            }

            if (jsonStr.length != beforeLength) {
                android.util.Log.d("CineX-Sync", "$action: JSON modificado (${beforeLength} -> ${jsonStr.length} chars)")
            }

            // Reparo básico de truncagem no final da string
            if (jsonStr.startsWith("[") && !jsonStr.endsWith("]")) {
                val lastBrace = jsonStr.lastIndexOf('}')
                if (lastBrace > 0) {
                    jsonStr = jsonStr.substring(0, lastBrace + 1) + "]"
                    android.util.Log.d("CineX-Sync", "$action: JSON truncado reparado")
                } else {
                    jsonStr += "]"
                }
            }

            val isSeries = itemClass.simpleName == "SeriesItem"
            val isCategory = itemClass.simpleName == "XtreamCategory"
            val idField = when {
                isSeries -> "series_id"
                isCategory -> "category_id"
                else -> "stream_id"
            }
            val seenIds = mutableSetOf<String>()
            var streamingFailed = false

            // Parser campo-a-campo via readObjectLenient (membro da classe)
            try {
                val reader = com.google.gson.stream.JsonReader(java.io.StringReader(jsonStr))
                reader.isLenient = true

                if (jsonStr.startsWith("[")) {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        try {
                            val objJson = readObjectLenient(reader)
                            val item = gson.fromJson<T>(objJson, itemClass)
                            if (item != null) {
                                val id = try {
                                    itemClass.getDeclaredField(idField).apply { isAccessible = true }.get(item).toString()
                                } catch (_: Exception) { null }
                                if (id != null && seenIds.add(id)) resultList.add(item)
                            }
                        } catch (e: Exception) {
                            try { reader.skipValue() } catch (_: Exception) {
                                streamingFailed = true
                                break
                            }
                        }
                    }
                    if (!streamingFailed) try { reader.endArray() } catch (_: Exception) {}
                } else {
                    val item = gson.fromJson(jsonStr, itemClass)
                    if (item != null) resultList.add(item)
                }
                reader.close()
            } catch (e: Exception) {
                android.util.Log.e("CineX-Sync", "$action: Parser falhou: ${e.message}")
                streamingFailed = true
            }

            // TENTA 2: Se streaming falhou, usa recovery OTIMIZADA por chunks
            if (streamingFailed) {
                val recoveryStartTime = System.currentTimeMillis()
                android.util.Log.w("CineX-Sync", "$action: Streaming falhou, tentando recovery por CHUNKS otimizada...")
                android.util.Log.d("CineX-Sync", "$action: Já recuperado ${resultList.size} itens antes da falha...")
                
                try {
                    // Estratégia rápida: encontra objetos JSON por scan linear (sem regex pesado)
                    val idFieldPattern = if (isSeries) "series_id" else "stream_id"
                    val objectBoundaries = mutableListOf<Pair<Int, Int>>()
                    
                    // Scan linear para encontrar limites de objetos { ... }
                    var braceCount = 0
                    var objectStart = -1
                    var inString = false
                    var escapeNext = false
                    
                    for (i in jsonStr.indices) {
                        val c = jsonStr[i]
                        
                        if (escapeNext) {
                            escapeNext = false
                            continue
                        }
                        if (c == '\\') {
                            escapeNext = true
                            continue
                        }
                        if (c == '"') {
                            inString = !inString
                            continue
                        }
                        if (inString) continue
                        
                        when (c) {
                            '{' -> {
                                if (braceCount == 0) objectStart = i
                                braceCount++
                            }
                            '}' -> {
                                braceCount--
                                if (braceCount == 0 && objectStart >= 0) {
                                    objectBoundaries.add(objectStart to i)
                                    objectStart = -1
                                }
                            }
                        }
                    }
                    
                    android.util.Log.d("CineX-Sync", "$action: Encontrados ${objectBoundaries.size} objetos")
                    
                    // Processa objetos que ainda não foram adicionados
                    var recoveredCount = 0
                    var failedCount = 0
                    val startIndex = resultList.size.coerceAtMost(objectBoundaries.size)
                    
                    for (idx in startIndex until objectBoundaries.size) {
                        // Yield a cada 100 itens para evitar ANR
                        if (idx % 100 == 0) {
                            kotlinx.coroutines.yield()
                        }
                        
                        val (start, end) = objectBoundaries[idx]
                        val objJson = jsonStr.substring(start, end + 1)
                        
                        try {
                            val item = gson.fromJson(objJson, itemClass)
                            if (item != null) {
                                val id = try {
                                    itemClass.getDeclaredField(idField).apply { isAccessible = true }.get(item).toString()
                                } catch (_: Exception) { null }
                                
                                if (id != null && !seenIds.contains(id)) {
                                    resultList.add(item)
                                    seenIds.add(id)
                                    recoveredCount++
                                }
                            }
                        } catch (_: Exception) {
                            failedCount++
                        }
                    }
                    
                    val recoveryTime = System.currentTimeMillis() - recoveryStartTime
                    android.util.Log.d("CineX-Sync", "$action: ✅ Recovery concluída em ${recoveryTime}ms: ${resultList.size} itens totais (+$recoveredCount recuperados, $failedCount falhas)")
                } catch (e: Exception) {
                    android.util.Log.e("CineX-Sync", "$action: Recovery falhou: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CineX-Sync", "$action: Erro fatal no fetch relaxed: ${e.message}")
            e.printStackTrace()
        }

        return resultList
    }

    // Helpers de recovery DESABILITADOS - causavam ANR/OOM em JSON > 8MB
    // @Suppress("unused")
    // private fun findObjectStart(json: String, pos: Int): Int { ... }
    // private fun findObjectEnd(json: String, pos: Int): Int { ... }
}
