package com.cinex.player.ui

import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinex.player.data.model.Channel
import com.cinex.player.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.cinex.player.data.network.XtreamCodesApi
import com.cinex.player.util.UpdateInfo
import com.cinex.player.util.UpdateManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AccountInfo(
    val macAddress: String,
    val deviceKey: String,
    val accountStatus: String,
    val activationDate: String,
    val playlistExpiration: String
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val app: android.app.Application,
    private val okHttpClient: OkHttpClient,
    @javax.inject.Named("liveTv") val liveTvPlayer: ExoPlayer,
    @javax.inject.Named("vod") val vodPlayer: ExoPlayer,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _currentPlaylist = MutableStateFlow<com.cinex.player.data.model.Playlist?>(null)
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing = _isInitializing.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _syncStatus = MutableStateFlow("Iniciando...")
    val syncStatus = _syncStatus.asStateFlow()

    private val _syncCompletedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val syncCompletedEvent: SharedFlow<String> = _syncCompletedEvent.asSharedFlow()

    private val _isDeviceBlocked = MutableStateFlow(false)
    val isDeviceBlocked = _isDeviceBlocked.asStateFlow()

    private val _deltaSyncMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val deltaSyncMessage: SharedFlow<String> = _deltaSyncMessage.asSharedFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.asStateFlow()

    private val _homeReady = MutableStateFlow(false)
    val homeReady = _homeReady.asStateFlow()

    // Controle parental — sessão adulta desbloqueada até fechar o app
    private val _adultUnlocked = MutableStateFlow(false)
    val adultUnlocked = _adultUnlocked.asStateFlow()
    private val parentalPin = "0000"

    // --- Auto Update ---
    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog = _showUpdateDialog.asStateFlow()

    private val _showChangelog = MutableStateFlow(false)
    val showChangelog = _showChangelog.asStateFlow()

    private val _changelogText = MutableStateFlow("")
    val changelogText = _changelogText.asStateFlow()

    // Status: "idle", "checking", "up_to_date", "error"
    private val _updateCheckStatus = MutableStateFlow("idle")
    val updateCheckStatus: StateFlow<String> = _updateCheckStatus.asStateFlow()

    fun isAdultCategory(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("xxx") || lower.contains("adulto") || lower.contains("adult")
                || lower.contains("+18") || lower.contains("18+")
    }

    fun verifyParentalPin(pin: String): Boolean {
        val correct = pin == parentalPin
        if (correct) _adultUnlocked.value = true
        return correct
    }

    fun setHomeReady(ready: Boolean) {
        _homeReady.value = ready
    }

    private val _liveProgress = MutableStateFlow(0)
    val liveProgress = _liveProgress.asStateFlow()

    private val _movieProgress = MutableStateFlow(0)
    val movieProgress = _movieProgress.asStateFlow()

    private val _seriesProgress = MutableStateFlow(0)
    val seriesProgress = _seriesProgress.asStateFlow()

    private val _accountInfo = MutableStateFlow<AccountInfo?>(generateAccountInfoInternal())
    val accountInfo = _accountInfo.asStateFlow()

    private var liveRetryCount = 0
    private val maxLiveRetries = 5  // Aumentado de 2 para 5 (backoff exponencial)

    private val _livePlayerError = MutableStateFlow(false)
    val livePlayerError = _livePlayerError.asStateFlow()

    private val _isLiveBuffering = MutableStateFlow(false)
    val isLiveBuffering = _isLiveBuffering.asStateFlow()

    // Sinaliza que o preview deve reclamar a surface do player (após sair do fullscreen)
    private val _liveTvSurfaceRefresh = MutableStateFlow(0)
    val liveTvSurfaceRefresh = _liveTvSurfaceRefresh.asStateFlow()

    fun refreshLiveTvSurface() {
        _liveTvSurfaceRefresh.value++
    }

    private var stallDetectorJob: kotlinx.coroutines.Job? = null

    init {
        // Listener para recuperação automática do player de Live TV
        liveTvPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Reconecta automaticamente ao mesmo canal
                reconnectLiveChannel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // Reset retry counter quando volta a reproduzir com sucesso
                        liveRetryCount = 0
                        _isLiveBuffering.value = false
                        stallDetectorJob?.cancel()
                    }
                    Player.STATE_BUFFERING -> {
                        _isLiveBuffering.value = true
                        // Detecta stall: se ficar em buffering por mais de 3s, força seek ao live edge
                        stallDetectorJob?.cancel()
                        stallDetectorJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(3_000)
                            // Ainda em buffering após 3s — tenta seek ao live edge
                            if (liveTvPlayer.playbackState == Player.STATE_BUFFERING) {
                                liveTvPlayer.seekToDefaultPosition()
                                // Se continuar em stall após mais 2s, reconecta o canal
                                kotlinx.coroutines.delay(2_000)
                                if (liveTvPlayer.playbackState == Player.STATE_BUFFERING) {
                                    reconnectLiveChannel()
                                }
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        // Live streams não devem terminar — reconecta
                        val lastChannel = _selectedLiveChannel.value
                        if (lastChannel != null) {
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(500)
                                liveTvPlayer.seekToDefaultPosition()
                                liveTvPlayer.play()
                            }
                        }
                    }
                    else -> {}
                }
            }
        })

        generateAccountInfo()
        viewModelScope.launch {
            val splashStart = System.currentTimeMillis()
            val playlists = repository.allPlaylists.first()
            if (playlists.isNotEmpty()) {
                playlists.maxByOrNull { it.lastUsed }?.let { lastUsed ->
                    _currentPlaylist.value = lastUsed
                    repository.activatePlaylist(lastUsed.url)
                    fetchRealAccountInfo(lastUsed.url)
                }
                // Verifica com o painel se o dispositivo ainda tem acesso antes de liberar o app
                validateDeviceAccess()
            }
            // Garante que a splash animada seja exibida por pelo menos 3s
            val elapsed = System.currentTimeMillis() - splashStart
            if (elapsed < 3000) kotlinx.coroutines.delay(3000 - elapsed)
            _isInitializing.value = false

            // Se a Home não tem banners e já tem filmes no banco, enriquece para a Home
            _currentPlaylist.value?.let { pl ->
                val featured = repository.getFeaturedContent(pl.url).first()
                if (featured.isEmpty()) {
                    // Tenta enriquecer se já tem conteúdo no banco de sessão anterior
                    launch {
                        repository.ensureTypeLoaded("MOVIE")
                        repository.ensureTypeLoaded("SERIES") // ✅ Carregar séries também!
                        repository.enrichRandomForHome(pl.url, 20)
                        repository.triggerBackgroundEnrichment(pl.url)
                    }
                } else {
                    // Mesmo se já tem featured content, garantir que séries estejam carregadas
                    launch {
                        repository.ensureTypeLoaded("SERIES")
                    }
                }
            }
        }

        // Verificação periódica a cada 30 minutos — corta acesso se MAC removido ou bloqueado
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30 * 60 * 1000L) // 30 minutos
                validateDeviceAccess()
            }
        }

        // Checagem de atualização ao abrir o app
        checkForUpdate()

        // Mostra changelog se acabou de atualizar
        if (updateManager.shouldShowChangelog()) {
            val changelog = updateManager.getChangelogForCurrentVersion()
            if (changelog != null) {
                _changelogText.value = changelog
                _showChangelog.value = true
                updateManager.markChangelogSeen()
            }
        }
    }


    // --- Update Actions ---
    fun checkForUpdate() {
        viewModelScope.launch {
            _updateCheckStatus.value = "checking"
            val update = updateManager.checkForUpdate()
            if (update != null) {
                _updateAvailable.value = update
                _showUpdateDialog.value = true
                updateManager.saveChangelogForVersion(update.newVersion, update.changelog)
                _updateCheckStatus.value = "idle"
            } else {
                _updateCheckStatus.value = "up_to_date"
                // Auto-dismiss após 3s
                kotlinx.coroutines.delay(3000)
                _updateCheckStatus.value = "idle"
            }
        }
    }

    fun acceptUpdate() {
        val update = _updateAvailable.value ?: return
        // Em vez de piscar a tela fechando a dialog diretamente, vamos mudar o status para dar feedback do download
        _updateCheckStatus.value = "downloading"
        
        viewModelScope.launch {
            val success = updateManager.downloadAndInstall(update)
            if (success) {
                _showUpdateDialog.value = false
            } else {
                _updateCheckStatus.value = "error"
            }
        }
    }

    fun dismissUpdate() {
        _showUpdateDialog.value = false
    }

    fun dismissChangelog() {
        _showChangelog.value = false
    }


    private val phrases = listOf(
        "Carregando seus conteúdos...",
        "Preparando o catálogo...",
        "Organizando seus filmes...",
        "Sincronizando as séries...",
        "Ajustando os canais ao vivo...",
        "Buscando as capas oficiais...",
        "Quase lá! Finalizando..."
    )

    val movies: Flow<PagingData<Channel>> = repository.movieChannels
    val series: Flow<PagingData<Channel>> = repository.seriesChannels

    @OptIn(ExperimentalCoroutinesApi::class)
    val featuredMovies: StateFlow<List<Channel>> = _currentPlaylist.flatMapLatest { playlist ->
        _homeReady.value = false
        if (playlist == null) flowOf(emptyList())
        else {
            val seed = playlist.url.hashCode().toLong()
            var locked = false
            repository.getFeaturedContent(playlist.url)
                .map { channels ->
                    channels.filter { isValidSynopsis(it.tmdbSynopsis) }
                        .shuffled(kotlin.random.Random(seed))
                        .take(10)
                }
                .distinctUntilChanged { old, new ->
                    // Só trava quando tiver 3+ itens com banner TMDB real (/original/).
                    // Travar com itens Xtream impedia o TMDB de substituí-los depois.
                    if (locked) true
                    else {
                        val tmdbCount = old.count {
                            !it.bannerUrl.isNullOrEmpty() && it.bannerUrl.contains("/original/")
                        }
                        if (tmdbCount >= 3) { locked = true; true }
                        else false
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val misc: Flow<PagingData<Channel>> = repository.miscChannels

    val liveCategories: Flow<List<com.cinex.player.data.model.Category>> = repository.liveCategories.map { cats ->
        val playlistUrl = _currentPlaylist.value?.url ?: ""
        val specials = listOf(
            com.cinex.player.data.model.Category(id = "Favorito", name = "Favorito", type = "LIVE_TV", playlistUrl = playlistUrl, orderIndex = -2),
            com.cinex.player.data.model.Category(id = "Tudo", name = "Tudo", type = "LIVE_TV", playlistUrl = playlistUrl, orderIndex = -1)
        )
        specials + cats
    }

    val movieCategories: Flow<List<com.cinex.player.data.model.Category>> = repository.movieCategories.map { cats ->
        val playlistUrl = _currentPlaylist.value?.url ?: ""
        val specials = listOf(
            com.cinex.player.data.model.Category(id = "Favorito", name = "Favorito", type = "MOVIE", playlistUrl = playlistUrl, orderIndex = -2),
            com.cinex.player.data.model.Category(id = "Tudo", name = "Tudo", type = "MOVIE", playlistUrl = playlistUrl, orderIndex = -1)
        )
        specials + cats
    }

    val seriesCategories: Flow<List<com.cinex.player.data.model.Category>> = repository.seriesCategories.map { cats ->
        val playlistUrl = _currentPlaylist.value?.url ?: ""
        val specials = listOf(
            com.cinex.player.data.model.Category(id = "Favorito", name = "Favorito", type = "SERIES", playlistUrl = playlistUrl, orderIndex = -2),
            com.cinex.player.data.model.Category(id = "Tudo", name = "Tudo", type = "SERIES", playlistUrl = playlistUrl, orderIndex = -1)
        )
        specials + cats
    }

    // Categoria ao vivo selecionada — usar StateFlow para troca instantânea
    private val _liveCategoryId = MutableStateFlow("Tudo")
    val liveCategoryId = _liveCategoryId.asStateFlow()

    fun setLiveCategory(categoryId: String) {
        _liveCategoryId.value = categoryId
    }

    // Cache em memória de todos os canais ao vivo — filtragem por categoria é instantânea
    // debounce coalesca rajadas de writes (enrichment, sync) em uma única emissão por 150ms,
    // evitando storms de recomposição no grid TV.
    private val _allLiveChannels = repository.observeAllLiveChannels()
        .debounce(250)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveTvChannels: StateFlow<List<Channel>> = combine(_liveCategoryId, _allLiveChannels) { categoryId, allChannels ->
        when (categoryId) {
            "Tudo" -> allChannels
            "Favorito", "Favoritos" -> allChannels.filter { it.isFavorite }
            else -> allChannels.filter { it.categoryId == categoryId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Compatibilidade: mantém o Paging como fallback para quem ainda usa
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveTvPagingData: Flow<PagingData<Channel>> = _liveCategoryId.flatMapLatest { group ->
        repository.getPagedChannelsByCategory(group)
    }.cachedIn(viewModelScope)

    fun getPagedChannelsByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedChannelsByCategory(group).cachedIn(viewModelScope)

    private val movieFlowCache = LinkedHashMap<String, Flow<PagingData<Channel>>>()
    private val seriesFlowCache = LinkedHashMap<String, Flow<PagingData<Channel>>>()
    private val MAX_CACHE_SIZE = 50

    private val _selectedMovieCategory = MutableStateFlow("Tudo")
    val selectedMovieCategory = _selectedMovieCategory.asStateFlow()

    private val _selectedSeriesCategory = MutableStateFlow("Tudo")
    val selectedSeriesCategory = _selectedSeriesCategory.asStateFlow()

    fun setMovieCategory(id: String) { _selectedMovieCategory.value = id }
    fun setSeriesCategory(id: String) { _selectedSeriesCategory.value = id }

    // Filtra filmes por categoria DIRETAMENTE no banco — nunca carrega 16k itens na memória.
    // Quando o usuário muda de categoria, o flatMapLatest cancela a query anterior e inicia nova.
    private val _allMoviesFlow = _selectedMovieCategory
        .flatMapLatest { categoryId ->
            when (categoryId) {
                "Favorito", "Favoritos" -> repository.observeMoviesByCategory("Tudo")
                    .map { list -> categoryId to list.filter { it.isFavorite } }
                "Continuar Assistindo" -> repository.observeMoviesByCategory("Tudo")
                    .map { list -> categoryId to list.filter { it.resumePosition > 0 } }
                else -> repository.observeMoviesByCategory(categoryId)
                    .map { list -> categoryId to list }
            }
        }
        .debounce(50) // TV OTIMIZAÇÃO: Debounce muito menor (50ms) para carregar filmes instantaneamente na TV, em vez de segurar por meio segundo.

    private val _allMovies = _allMoviesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tudo" to emptyList())

    val isMoviesReady: StateFlow<Boolean> = _allMoviesFlow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val moviesByCategory: StateFlow<Pair<String, List<Channel>>> = _allMovies

    // Filtra séries por categoria diretamente no banco — nunca carrega 8k itens na memória.
    private val _allSeriesFlow = _selectedSeriesCategory
        .flatMapLatest { categoryId ->
            when (categoryId) {
                "Favorito", "Favoritos" -> repository.observeSeriesByCategory("Tudo")
                    .map { list -> categoryId to list.filter { it.isFavorite } }
                "Continuar Assistindo" -> repository.observeSeriesByCategory("Tudo")
                    .map { list -> categoryId to list.filter { it.resumePosition > 0 } }
                else -> repository.observeSeriesByCategory(categoryId)
                    .map { list -> categoryId to list }
            }
        }
        .debounce(50) // TV OTIMIZAÇÃO: 50ms para responsividade instantânea no D-pad.

    private val _allSeries = _allSeriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tudo" to emptyList())

    val isSeriesReady: StateFlow<Boolean> = _allSeriesFlow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val seriesByCategory: StateFlow<Pair<String, List<Channel>>> = _allSeries

    private val _movieSortOrder = MutableStateFlow("RECENT")
    val movieSortOrder = _movieSortOrder.asStateFlow()

    private val _seriesSortOrder = MutableStateFlow("RECENT")
    val seriesSortOrder = _seriesSortOrder.asStateFlow()

    fun setMovieSortOrder(sort: String) {
        _movieSortOrder.value = sort
    }

    fun setSeriesSortOrder(sort: String) {
        _seriesSortOrder.value = sort
    }

    // === LAZY LOADING POR ABA ===
    private val _isLoadingType = MutableStateFlow<String?>(null)
    val isLoadingType = _isLoadingType.asStateFlow()

    /** Carrega conteúdo de um tipo sob demanda (chamado ao navegar para aba) */
    fun ensureTypeLoaded(type: String) {
        viewModelScope.launch {
            _isLoadingType.value = type
            val result = repository.ensureTypeLoaded(type)
            result.onSuccess {
                // Após carregar MOVIE ou SERIES pela primeira vez, enriquecer para Home
                if (type == "MOVIE" || type == "SERIES") {
                    _currentPlaylist.value?.let { pl ->
                        launch { repository.enrichRandomForHome(pl.url, 20) }
                    }
                }
            }.onFailure {
                _errorMessage.value = "Erro ao carregar conteúdo: ${it.message}"
            }
            _isLoadingType.value = null
        }
    }

    fun getPagedMoviesByCategory(group: String): Flow<PagingData<Channel>> {
        val sort = _movieSortOrder.value
        // Não usa cache por sort — sempre cria flow fresco para garantir ordem correta
        return repository.getPagedMoviesByCategory(group, sort)
            .cachedIn(viewModelScope)
    }

    fun getPagedSeriesByCategory(group: String): Flow<PagingData<Channel>> {
        val sort = _seriesSortOrder.value
        return repository.getPagedSeriesByCategory(group, sort)
            .cachedIn(viewModelScope)
    }

    private fun isValidSynopsis(synopsis: String?): Boolean {
        if (synopsis.isNullOrBlank()) return false
        val clean = synopsis.trim().lowercase()
        return clean != "null"
    }

    fun clearPagingCaches() {
        movieFlowCache.clear()
        seriesFlowCache.clear()
        enrichingIds.clear()
        enrichingSeriesSeasons.clear()
    }

    private val enrichingIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<Int, Boolean>()
    )

    private val enrichingSeriesSeasons = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    // Semáforo para limitar enriquecimentos TMDB simultâneos (evita travamento na TV)
    private val enrichSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    private fun shouldEnrichChannelMetadata(channel: Channel): Boolean {
        if (channel.category == "LIVE_TV") return false

        return channel.tmdbSynopsis.isNullOrEmpty() ||
            channel.posterUrl.isNullOrEmpty() ||
            channel.bannerUrl.isNullOrEmpty() ||
            channel.tmdbYear.isNullOrEmpty() ||
            channel.tmdbRating == null ||
            channel.tmdbRating <= 0.0
    }

    private fun shouldEnrichChannelVisualFallback(channel: Channel): Boolean {
        if (channel.category == "LIVE_TV") return false

        val hasTmdbPoster = !channel.posterUrl.isNullOrEmpty()

        // Séries: sempre enriquecer se não tem poster TMDB, pois o logoUrl do M3U
        // pode ser inválido/quebrado e o poster TMDB é a imagem principal no grid
        if (channel.category == "SERIES") return !hasTmdbPoster

        val hasDefaultImage = !channel.logoUrl.isNullOrEmpty()
        return !hasDefaultImage && !hasTmdbPoster
    }

    fun onChannelVisible(channel: Channel) {
        if (shouldEnrichChannelVisualFallback(channel) && enrichingIds.add(channel.id)) {
            viewModelScope.launch {
                // Limita a no máximo 3 enriquecimentos simultâneos para não travar a UI
                enrichSemaphore.acquire()
                try {
                    repository.enrichChannelWithTmdb(channel)
                } finally {
                    enrichSemaphore.release()
                }
            }
        }
    }

    val continueWatching: StateFlow<List<Channel>> = repository.continueWatching.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLiveTvHidden = MutableStateFlow(false)
    val isLiveTvHidden = _isLiveTvHidden.asStateFlow()

    private val _isMoviesHidden = MutableStateFlow(false)
    val isMoviesHidden = _isMoviesHidden.asStateFlow()

    private val _isSeriesHidden = MutableStateFlow(false)
    val isSeriesHidden = _isSeriesHidden.asStateFlow()

    private val _is24HourFormat = MutableStateFlow(true)
    val is24HourFormat = _is24HourFormat.asStateFlow()

    private val _isParentalControlEnabled = MutableStateFlow(false)
    val isParentalControlEnabled = _isParentalControlEnabled.asStateFlow()

    val allPlaylists: StateFlow<List<com.cinex.player.data.model.Playlist>> = repository.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categoryCounts: StateFlow<Map<String, Int>> = repository.categoryCounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val typeCounts: StateFlow<Map<String, Int>> = repository.typeCounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val favoriteCounts: StateFlow<Map<String, Int>> = repository.favoriteCounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Canal atualmente selecionado no Live TV (persiste entre recomposições do LiveTvScreen)
    private val _selectedLiveChannel = MutableStateFlow<Channel?>(null)
    val selectedLiveChannel = _selectedLiveChannel.asStateFlow()

    private val _selectedChannelTvgId = MutableStateFlow<String?>(null)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentProgram: StateFlow<com.cinex.player.data.model.EpgProgram?> = _selectedChannelTvgId.flatMapLatest { tvgId ->
        if (tvgId == null) flowOf(null)
        else repository.getCurrentProgram(tvgId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val upcomingPrograms: StateFlow<List<com.cinex.player.data.model.EpgProgram>> = _selectedChannelTvgId.flatMapLatest { tvgId ->
        if (tvgId == null) flowOf(emptyList())
        else repository.getUpcomingPrograms(tvgId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _epgListings = MutableStateFlow<List<com.cinex.player.data.network.EpgListing>>(emptyList())
    val epgListings = _epgListings.asStateFlow()

    // Cache de EPG por streamId com TTL de 30 minutos
    private data class EpgCacheEntry(
        val listings: List<com.cinex.player.data.network.EpgListing>,
        val timestamp: Long
    )
    private val epgCache = mutableMapOf<Int, EpgCacheEntry>()
    private val EPG_CACHE_TTL = 30 * 60 * 1000L // 30 minutos

    fun updateSelectedChannel(channel: Channel?) {
        _selectedLiveChannel.value = channel
        _selectedChannelTvgId.value = channel?.tvgId
        if (channel != null) {
            val streamId = try {
                channel.remoteId.replace("live_", "").toInt()
            } catch (e: Exception) { -1 }
            if (streamId != -1) {
                val cached = epgCache[streamId]
                _epgListings.value = if (cached != null && (System.currentTimeMillis() - cached.timestamp) < EPG_CACHE_TTL) {
                    cached.listings
                } else {
                    emptyList()
                }
                fetchEpg(streamId)
            } else {
                _epgListings.value = emptyList()
            }
        } else {
            _epgListings.value = emptyList()
        }
    }

    fun fetchEpg(streamId: Int) {
        // Verifica cache primeiro
        val cached = epgCache[streamId]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < EPG_CACHE_TTL) {
            _epgListings.value = cached.listings
            return
        }

        viewModelScope.launch {
            repository.getShortEpg(streamId).onSuccess { response ->
                val listings = response.epg_listings ?: emptyList()
                epgCache[streamId] = EpgCacheEntry(listings, System.currentTimeMillis())
                // Limpa entradas antigas para não crescer infinitamente
                if (epgCache.size > 50) {
                    val oldest = epgCache.entries.minByOrNull { it.value.timestamp }?.key
                    oldest?.let { epgCache.remove(it) }
                }
                
                // Previne condição de corrida: Só atualiza EPG se o usuário não mudou de canal durante o delay da requisição
                val currentStreamId = _selectedLiveChannel.value?.remoteId?.replace("live_", "")?.toIntOrNull() ?: -1
                if (currentStreamId == streamId) {
                    _epgListings.value = listings
                }
            }.onFailure {
                val currentStreamId = _selectedLiveChannel.value?.remoteId?.replace("live_", "")?.toIntOrNull() ?: -1
                if (currentStreamId == streamId) {
                    _epgListings.value = emptyList()
                }
            }
        }
    }

    private fun scheduleEpgSync(epgUrl: String) {
        val data = androidx.work.Data.Builder()
            .putString("epg_url", epgUrl)
            .build()
        
        val request = androidx.work.PeriodicWorkRequestBuilder<com.cinex.player.data.worker.EpgSyncWorker>(
            12, java.util.concurrent.TimeUnit.HOURS
        ).setInputData(data).build()

        androidx.work.WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "epg_sync",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        
        viewModelScope.launch { repository.syncEpg(epgUrl) }
    }


    val deviceMacAddress: String
        get() {
            val prefs = app.getSharedPreferences("cinex_device", android.content.Context.MODE_PRIVATE)
            val saved = prefs.getString("device_mac", null)
            if (saved != null) return saved

            val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
            val mac = androidId.chunked(2).take(6).joinToString(":").uppercase()
            prefs.edit().putString("device_mac", mac).apply()
            return mac
        }

    private fun fetchRealAccountInfo(playlistUrl: String) {
        viewModelScope.launch {
            try {
                val uri = android.net.Uri.parse(playlistUrl)
                val username = uri.getQueryParameter("username")
                val password = uri.getQueryParameter("password")
                val scheme = uri.scheme
                val host = uri.host
                val port = uri.port

                if (username != null && password != null && host != null) {
                    val baseUrl = "$scheme://$host${if (port != -1) ":$port" else ""}/"
                    val api = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(XtreamCodesApi::class.java)

                    val response = api.getAccountInfo(username, password)
                    response.user_info?.let { info ->
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                        val createdDateStr = if (info.created_at != null && info.created_at != "null") {
                            try {
                                val timestamp = info.created_at.toLong() * 1000
                                dateFormat.format(Date(timestamp))
                            } catch (e: Exception) { "N/A" }
                        } else "N/A"

                        val expDateStr = if (info.exp_date != null && info.exp_date != "null") {
                            try {
                                val timestamp = info.exp_date.toLong() * 1000
                                dateFormat.format(Date(timestamp))
                            } catch (e: Exception) { "N/A" }
                        } else "ILIMITADO"

                        _accountInfo.value = _accountInfo.value?.copy(
                            accountStatus = info.status?.uppercase() ?: "ATIVADO",
                            activationDate = createdDateStr,
                            playlistExpiration = expDateStr
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateAccountInfoInternal(): AccountInfo {
        return try {
            val simulatedMac = getDeviceMacAddressInternal()
            val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
            val deviceKey = (androidId.hashCode().toLong() and 0xFFFFFF).toString()

            AccountInfo(
                macAddress = simulatedMac,
                deviceKey = deviceKey,
                accountStatus = "CONECTANDO...",
                activationDate = "...",
                playlistExpiration = "..."
            )
        } catch (e: Exception) {
            AccountInfo("00:00:00:00:00:00", "000000", "ERRO", "N/A", "N/A")
        }
    }

    private fun getDeviceMacAddressInternal(): String {
        return deviceMacAddress
    }

    private fun generateAccountInfo() {
        _accountInfo.value = generateAccountInfoInternal()
    }

    companion object {
        private const val PANEL_BASE_URL = "https://gerencia-cine-x.vercel.app"
    }

    // Verifica o status do dispositivo no painel do revendedor (bloqueio e existência)
    fun refreshAccountFromPanel() {
        viewModelScope.launch {
            try {
                val mac = deviceMacAddress
                val apiUrl = "$PANEL_BASE_URL/api/device/${mac}"
                val (responseCode, body) = withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(apiUrl).get().build()
                    val response = okHttpClient.newCall(request).execute()
                    Pair(response.code, response.body?.string() ?: "")
                }

                if (responseCode == 403) {
                    _isDeviceBlocked.value = true
                    _accountInfo.value = _accountInfo.value?.copy(
                        accountStatus = "BLOQUEADO"
                    )
                    return@launch
                }

                // Dispositivo não encontrado no painel — removido pelo revendedor
                if (responseCode == 404 || (responseCode != 200 && responseCode != 403) || body.contains("not_found")) {
                    revokeAccess()
                    return@launch
                }

                if (responseCode == 200) {
                    val json = org.json.JSONObject(body)
                    val status = json.optString("status", "")

                    if (status == "Bloqueado") {
                        _isDeviceBlocked.value = true
                        _accountInfo.value = _accountInfo.value?.copy(
                            accountStatus = "BLOQUEADO"
                        )
                    } else {
                        _isDeviceBlocked.value = false

                        // Valida se o dispositivo ainda tem playlist atribuída
                        val hasPlaylist = json.has("playlist") && !json.isNull("playlist")
                        if (!hasPlaylist) {
                            revokeAccess()
                            return@launch
                        }
                    }
                }

                // Atualiza datas da conta via Xtream API (fonte real)
                if (!_isDeviceBlocked.value) {
                    _currentPlaylist.value?.url?.let { fetchRealAccountInfo(it) }
                }
            } catch (e: Exception) {
                // Em caso de erro de rede, não revogar — manter cache para uso offline
                e.printStackTrace()
            }
        }
    }

    /** Valida o dispositivo no painel de forma síncrona (suspend) — chamado no init antes de liberar o app */
    private suspend fun validateDeviceAccess() {
        try {
            val mac = deviceMacAddress
            val apiUrl = "$PANEL_BASE_URL/api/device/${mac}"
            val (responseCode, body) = withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(apiUrl).get().build()
                val response = okHttpClient.newCall(request).execute()
                Pair(response.code, response.body?.string() ?: "")
            }

            if (responseCode == 403) {
                _isDeviceBlocked.value = true
                _accountInfo.value = _accountInfo.value?.copy(accountStatus = "BLOQUEADO")
                return
            }

            // Só revoga se o servidor explicitamente confirmar que o dispositivo não existe
            if (responseCode == 404) {
                revokeAccess()
                return
            }

            // Qualquer outro erro não-200 (500, timeout, Vercel cold start, etc.) — mantém acesso offline
            if (responseCode != 200) return

            val json = org.json.JSONObject(body)

            // Checa bloqueio explícito
            val syncStatus = json.optString("sync_status", "")
            val status = json.optString("status", "")
            if (syncStatus == "blocked" || status == "Bloqueado") {
                _isDeviceBlocked.value = true
                _accountInfo.value = _accountInfo.value?.copy(accountStatus = "BLOQUEADO")
                return
            }

            _isDeviceBlocked.value = false

            // Se não tem playlist configurada no painel, revoga
            val playlistObj = json.optJSONObject("playlist")
            val playlistType = playlistObj?.optString("type", "")
            if (playlistObj == null || playlistType.isNullOrBlank()) {
                revokeAccess()
            }
        } catch (e: Exception) {
            // Erro de rede — permite uso offline com cache local
            e.printStackTrace()
        }
    }

    /** Remove acesso local — limpa playlists e canais do banco, forçando nova ativação */
    private suspend fun revokeAccess() {
        stopLiveTv()
        repository.clearAllData()
        _currentPlaylist.value = null
        _isDeviceBlocked.value = false
        _accountInfo.value = generateAccountInfoInternal()
        _errorMessage.value = "Dispositivo não cadastrado no painel.\nContate seu revendedor."
    }

    fun syncFromPanel() {
        viewModelScope.launch {
            _isLoading.value = true
            _isSyncing.value = true
            _errorMessage.value = null
            _syncStatus.value = "Conectando ao painel CineX..."

            val rotateJob = launch {
                var phraseIndex = 0
                while (_isLoading.value) {
                    _syncStatus.value = phrases[phraseIndex]
                    phraseIndex = (phraseIndex + 1) % phrases.size
                    kotlinx.coroutines.delay(3000)
                }
            }

            try {
                val mac = deviceMacAddress
                val apiUrl = "$PANEL_BASE_URL/api/device/${mac}"
                val (responseCode, body) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(apiUrl).get().build()
                    val response = okHttpClient.newCall(request).execute()
                    Pair(response.code, response.body?.string() ?: "")
                }

                if (responseCode == 403 || body.contains("blocked")) {
                    revokeAccess()
                    _isDeviceBlocked.value = true
                    _errorMessage.value = if (body.contains("blocked")) {
                        org.json.JSONObject(body).optString("message", "Dispositivo bloqueado. Contate seu revendedor.")
                    } else {
                        "Dispositivo bloqueado. Contate seu revendedor."
                    }
                    _isLoading.value = false
                    rotateJob.cancel()
                    return@launch
                }

                if (responseCode != 200 || body.contains("not_found")) {
                    revokeAccess()
                    _isLoading.value = false
                    rotateJob.cancel()
                    return@launch
                }

                val json = org.json.JSONObject(body)

                if (!json.has("playlist") || json.isNull("playlist")) {
                    _errorMessage.value = "Nenhuma playlist encontrada!\nSeu revendedor ainda não atribuiu uma lista ao seu dispositivo."
                    _isLoading.value = false
                    rotateJob.cancel()
                    return@launch
                }

                val playlist = json.getJSONObject("playlist")
                val type = playlist.getString("type")

                when (type) {
                    "m3u" -> {
                        val url = playlist.getString("url")

                        if (url.isBlank()) {
                            _errorMessage.value = "URL da lista M3U não configurada no painel."
                            _isLoading.value = false
                            rotateJob.cancel()
                            return@launch
                        }
                        repository.addPlaylist("CineX Panel", url)
                        val result = repository.syncPlaylist(url) { l, m, s, _ ->
                            _liveProgress.value = l
                            _movieProgress.value = m
                            _seriesProgress.value = s
                        }
                        result.onSuccess {
                            _syncStatus.value = "Lista carregada com sucesso!"
                            val savedPlaylist = repository.allPlaylists.first().find { it.url == url }
                            savedPlaylist?.epgUrl?.let { scheduleEpgSync(it) }
                            // NÃO seta _currentPlaylist aqui — o usuário clica em "ACESSAR PLATAFORMA"
                            fetchRealAccountInfo(url)
                        }.onFailure {
                            if (it.message != "Sincronização já em andamento") {
                                _errorMessage.value = it.message ?: "Erro ao carregar lista M3U"
                            }
                        }
                    }
                    "xtream" -> {
                        val dns = playlist.getString("dns")
                        val user = playlist.getString("user")
                        val pass = playlist.getString("pass")

                        if (dns.isBlank() || user.isBlank() || pass.isBlank()) {
                            _errorMessage.value = "Credenciais Xtream não configuradas no painel."
                            _isLoading.value = false
                            rotateJob.cancel()
                            return@launch
                        }
                        val xtreamUrl = "$dns/get.php?username=$user&password=$pass&type=m3u_plus"
                        repository.addPlaylist("CineX Panel (Xtream)", xtreamUrl)
                        val result = repository.syncPlaylist(xtreamUrl) { l, m, s, _ ->
                            _liveProgress.value = l
                            _movieProgress.value = m
                            _seriesProgress.value = s
                        }
                        result.onSuccess {
                            _syncStatus.value = "Lista Xtream carregada com sucesso!"
                            // NÃO seta _currentPlaylist aqui — o usuário clica em "ACESSAR PLATAFORMA"
                            fetchRealAccountInfo(xtreamUrl)
                        }.onFailure {
                            if (it.message != "Sincronização já em andamento") {
                                _errorMessage.value = it.message ?: "Erro ao carregar lista Xtream"
                            }
                        }
                    }
                    else -> {
                        _errorMessage.value = "Tipo de lista não reconhecido: $type"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro de conexão com o painel: ${e.message}"
            } finally {
                rotateJob.cancel()
                _isLoading.value = false
                _isSyncing.value = false
                _isInitializing.value = false
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: Flow<PagingData<Channel>> = _searchQuery
        .debounce(400)
        .flatMapLatest { query ->
            if (query.isEmpty()) flowOf(PagingData.empty())
            else repository.searchChannels(query)
        }
        .cachedIn(viewModelScope)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _selectedChannelForDetails = MutableStateFlow<Channel?>(null)
    val selectedChannelForDetails = _selectedChannelForDetails.asStateFlow()

    fun selectChannelForDetails(channel: Channel?) {
        _selectedChannelForDetails.value = channel
        if (channel != null) {
            // Séries sempre precisam buscar episódios, independente do estado TMDB
            if (channel.category == "SERIES") {
                enrichChannelMetadata(channel)
            } else if (shouldEnrichChannelMetadata(channel)) {
                enrichChannelMetadata(channel)
            }
        }
    }

    private fun enrichChannelMetadata(channel: Channel, skipTmdb: Boolean = false) {
        viewModelScope.launch {
            if (channel.category == "SERIES") {
                val seriesName = channel.seriesName ?: channel.name
                val seriesId = try { channel.remoteId.replace("series_", "").toInt() } catch (e: Exception) { -1 }
                // Verifica se já tem episódios no banco
                val hasEpisodes = repository.hasEpisodesForSeries(seriesName)
                if (!hasEpisodes) {
                    _isLoadingEpisodes.value = true
                }
                try {
                    if (seriesId != -1) {
                        repository.fetchAndStoreEpisodes(seriesId, seriesName)
                    }
                } finally {
                    // Sempre libera o loading, inclusive se fetchAndStoreEpisodes retornou cedo (cache hit)
                    _isLoadingEpisodes.value = false
                }
                // Em TV, pula o TMDB para não bloquear carregamento com chamadas de rede desnecessárias
                if (!skipTmdb && shouldEnrichChannelMetadata(channel)) {
                    repository.enrichSeriesMetadataWithTmdb(channel)
                }
            } else {
                if (shouldEnrichChannelMetadata(channel)) {
                    repository.enrichChannelWithTmdb(channel)
                }
            }
        }
    }

    fun getSeasonsForSeries(seriesName: String): Flow<List<Int>> {
        return repository.getSeasonsForSeries(seriesName)
    }

    fun observeEpisodesBySeason(seriesName: String, season: Int): Flow<List<Channel>> {
        return repository.observeEpisodesBySeason(seriesName, season)
    }

    fun getEpisodesBySeasonPaged(seriesName: String, season: Int): Flow<PagingData<Channel>> {
        return repository.getEpisodesBySeasonPaged(seriesName, season)
    }

    fun ensureSeriesDetailsReady(series: Channel, skipTmdb: Boolean = false) {
        if (series.category == "SERIES") {
            enrichChannelMetadata(series, skipTmdb)
        }
    }

    fun observeChannelByRemoteId(remoteId: String): Flow<Channel?> {
        return repository.observeChannelByRemoteId(remoteId)
    }

    fun observeEpisodesWithoutStillForSeason(seriesName: String, season: Int): Flow<Int> {
        return repository.observeEpisodesWithoutStillForSeason(seriesName, season)
    }

    fun enrichSeriesSeasonIfNeeded(series: Channel, season: Int, skipTmdb: Boolean = false) {
        if (skipTmdb) return
        if (series.category != "SERIES" || season <= 0) return
        val seriesName = series.seriesName ?: series.name
        val key = "${series.remoteId}:$season"
        if (!enrichingSeriesSeasons.add(key)) return

        viewModelScope.launch {
            try {
                if (repository.hasEpisodesWithoutStill(seriesName, season)) {
                    enrichSemaphore.acquire()
                    try {
                        repository.enrichSeriesSeasonWithTmdb(series, season)
                    } finally {
                        enrichSemaphore.release()
                    }
                }
            } finally {
                enrichingSeriesSeasons.remove(key)
            }
        }
    }

    private fun reconnectLiveChannel() {
        val lastChannel = _selectedLiveChannel.value
        if (lastChannel != null && liveRetryCount < maxLiveRetries) {
            liveRetryCount++
            // Backoff exponencial com jitter: 1s → 2s → 4s → 8s → 16s (máx 30s)
            val baseDelay = kotlin.math.min(1000L * (1L shl (liveRetryCount - 1)), 30000L)
            val jitter = (baseDelay * 0.25).toLong()  // ±25% jitter
            val actualDelay = baseDelay + (kotlin.random.Random.nextLong(-jitter, jitter + 1))

            android.util.Log.d("CineX-Live", "Reconectando canal (retry $liveRetryCount/$maxLiveRetries), delay: ${actualDelay}ms")

            viewModelScope.launch {
                kotlinx.coroutines.delay(actualDelay)
                liveTvPlayer.stop()
                liveTvPlayer.clearMediaItems()
                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(lastChannel.streamUrl)
                    .setLiveConfiguration(
                        androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.04f)
                            .setMaxOffsetMs(12_000)
                            .setMinOffsetMs(3_000)
                            .setTargetOffsetMs(6_000)
                            .build()
                    )
                    .build()
                liveTvPlayer.setMediaItem(mediaItem)
                liveTvPlayer.prepare()
                liveTvPlayer.play()
            }
        } else if (lastChannel != null) {
            _isLiveBuffering.value = false
            _livePlayerError.value = true
            android.util.Log.w("CineX-Live", "Máximo de retries atingido ($maxLiveRetries), exibindo erro")
        }
    }

    fun retryLiveChannel() {
        _livePlayerError.value = false
        liveRetryCount = 0
        _selectedLiveChannel.value?.let { playLiveChannel(it) }
    }

    fun stopLiveTv() {
        stallDetectorJob?.cancel()
        liveTvPlayer.playWhenReady = false
        liveTvPlayer.pause()
        liveTvPlayer.stop()
        liveTvPlayer.clearMediaItems()
    }

    fun stopVodPlayback() {
        vodPlayer.playWhenReady = false
        vodPlayer.pause()
        vodPlayer.stop()
        vodPlayer.clearMediaItems()
    }

    fun stopAllPlayback() {
        stopLiveTv()
        stopVodPlayback()
    }

    fun resumeLiveTv() {
        _selectedLiveChannel.value?.let { playLiveChannel(it) }
    }

    override fun onCleared() {
        super.onCleared()
        // Os players são singletons do app. Se a Activity/ViewModel morrer sem o processo morrer,
        // dar release aqui deixa a próxima abertura com instâncias inválidas e tela preta.
        stopAllPlayback()
    }

    fun updateLiveTvVisibility(hidden: Boolean) { _isLiveTvHidden.value = hidden }
    fun updateMoviesVisibility(hidden: Boolean) { _isMoviesHidden.value = hidden }
    fun updateSeriesVisibility(hidden: Boolean) { _isSeriesHidden.value = hidden }
    fun updateTimeFormat(is24Hour: Boolean) { _is24HourFormat.value = is24Hour }
    fun updateParentalControl(enabled: Boolean) { _isParentalControlEnabled.value = enabled }

    fun selectPlaylist(playlist: com.cinex.player.data.model.Playlist) {
        viewModelScope.launch {
            _isLoading.value = true
            _isSyncing.value = true
            _errorMessage.value = null
            _currentPlaylist.value = playlist
            
            val rotateJob = launch {
                var phraseIndex = 0
                while (_isLoading.value) {
                    _syncStatus.value = phrases[phraseIndex]
                    phraseIndex = (phraseIndex + 1) % phrases.size
                    kotlinx.coroutines.delay(3000)
                }
            }

            val result = repository.selectPlaylist(playlist) { l, m, s, _ ->
                _liveProgress.value = l
                _movieProgress.value = m
                _seriesProgress.value = s
            }
            
            fetchRealAccountInfo(playlist.url)
            
            result.onSuccess {
                _syncStatus.value = "Lista carregada com sucesso!"
                playlist.epgUrl?.let { scheduleEpgSync(it) }
            }.onFailure {
                if (it.message != "Sincronização já em andamento") {
                    _errorMessage.value = it.message ?: "Erro ao conectar ao servidor"
                }
            }
            
            rotateJob.cancel()
            _isLoading.value = false
            _isSyncing.value = false
        }
    }

    // Ativa uma playlist já sincronizada (sem re-baixar) — usado pelo botão "ACESSAR PLATAFORMA"
    fun enterPlatform(playlist: com.cinex.player.data.model.Playlist) {
        viewModelScope.launch {
            _currentPlaylist.value = playlist
            repository.activatePlaylist(playlist.url)
            fetchRealAccountInfo(playlist.url)
            playlist.epgUrl?.let { scheduleEpgSync(it) }
        }
    }

    fun addPlaylist(url: String) {
        viewModelScope.launch {
            repository.addPlaylist("Servidor #${System.currentTimeMillis() % 1000}", url)
        }
    }

    fun refreshPlaylist() {
        refreshAccountFromPanel()
        clearPagingCaches()
        _homeReady.value = false
        val playlist = _currentPlaylist.value ?: return
        loadPlaylist(playlist.url)
    }

    fun loadPlaylist(url: String) {
        viewModelScope.launch {
            clearPagingCaches()
            _isLoading.value = true
            _isSyncing.value = true
            _errorMessage.value = null
            
            val rotateJob = launch {
                var phraseIndex = 0
                while (_isLoading.value) {
                    _syncStatus.value = phrases[phraseIndex]
                    phraseIndex = (phraseIndex + 1) % phrases.size
                    kotlinx.coroutines.delay(3000)
                }
            }
            
            val result = repository.syncPlaylist(url) { l, m, s, _ ->
                _liveProgress.value = l
                _movieProgress.value = m
                _seriesProgress.value = s
            }
            
            result.onSuccess {
                _syncStatus.value = "Finalizado!"
                fetchRealAccountInfo(url)
                _syncCompletedEvent.tryEmit("Servidor resincronizado com sucesso!")
            }.onFailure {
                // Ignorar silenciosamente se outro sync já estava rodando
                if (it.message != "Sincronização já em andamento") {
                    _errorMessage.value = it.message ?: "Erro desconhecido ao carregar lista"
                }
            }

            rotateJob.cancel()
            _isLoading.value = false
            _isSyncing.value = false
        }
    }

    fun updateFavorite(channelId: Int, isFav: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateFavorite(channelId, isFav)
        }
    }

    fun saveResumePosition(channelId: Int, position: Long, duration: Long) {
        viewModelScope.launch {
            repository.updateResumePosition(channelId, position, duration)
        }
    }

    fun deletePlaylist(playlist: com.cinex.player.data.model.Playlist) {
        viewModelScope.launch {
            if (_currentPlaylist.value?.url == playlist.url) {
                _currentPlaylist.value = null
            }
            repository.clearChannels(playlist.url)
            repository.deletePlaylist(playlist)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun swapServer() {
        val currentUrl = _currentPlaylist.value?.url
        val currentModel = _currentPlaylist.value

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mac = deviceMacAddress
                val apiUrl = "$PANEL_BASE_URL/api/device/${mac}"

                val request = okhttp3.Request.Builder().url(apiUrl).delete().build()
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (currentUrl != null) {
                repository.clearChannels(currentUrl)
            }
            if (currentModel != null) {
                repository.deletePlaylist(currentModel)
            }
            
            withContext(Dispatchers.Main) {
                _currentPlaylist.value = null
                _isLoading.value = false
                _errorMessage.value = null
            }
        }
    }
    private fun formatPanelDate(dateStr: String): String {
        return try {
            if (dateStr == "N/A") return "N/A"
            // Exemplo: 2026-03-06T00:07:00.000Z -> 06/03/2026
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            dateStr.split("T").firstOrNull() ?: dateStr
        }
    }

    suspend fun getNextEpisode(channel: Channel): Channel? {
        return repository.getNextEpisode(channel)
    }

    fun playLiveChannel(channel: Channel) {
        _livePlayerError.value = false
        val currentMediaItem = liveTvPlayer.currentMediaItem
        val newUri = android.net.Uri.parse(channel.streamUrl)

        // Só recarrega se o canal for diferente
        if (currentMediaItem?.localConfiguration?.uri != newUri) {
            liveRetryCount = 0
            liveTvPlayer.stop()
            liveTvPlayer.clearMediaItems()

            // MediaItem com LiveConfiguration para manter o player perto do live edge
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(newUri)
                .setLiveConfiguration(
                    androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.04f)   // acelera suavemente para alcançar o live edge
                        .setMaxOffsetMs(12_000)        // máximo 12s atrás do live
                        .setMinOffsetMs(3_000)         // mínimo 3s atrás (margem de segurança)
                        .setTargetOffsetMs(6_000)      // alvo: 6s atrás do live edge
                        .build()
                )
                .build()

            liveTvPlayer.setMediaItem(mediaItem)
            liveTvPlayer.prepare()
            liveTvPlayer.play()
        } else if (!liveTvPlayer.isPlaying) {
            // Mesmo canal — retoma e busca o live edge para não ficar dessincronizado
            liveTvPlayer.seekToDefaultPosition()
            liveTvPlayer.play()
        }
    }
}
