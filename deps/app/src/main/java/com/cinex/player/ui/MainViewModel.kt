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
    val liveTvPlayer: ExoPlayer
) : ViewModel() {

    private val _currentPlaylist = MutableStateFlow<com.cinex.player.data.model.Playlist?>(null)
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing = _isInitializing.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _syncStatus = MutableStateFlow("Iniciando...")
    val syncStatus = _syncStatus.asStateFlow()

    private val _syncCompletedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val syncCompletedEvent: SharedFlow<String> = _syncCompletedEvent.asSharedFlow()

    private val _isDeviceBlocked = MutableStateFlow(false)
    val isDeviceBlocked = _isDeviceBlocked.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes = _isLoadingEpisodes.asStateFlow()

    private val _homeReady = MutableStateFlow(false)
    val homeReady = _homeReady.asStateFlow()

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
    private val maxLiveRetries = 3

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
                        stallDetectorJob?.cancel()
                    }
                    Player.STATE_BUFFERING -> {
                        // Detecta stall: se ficar em buffering por mais de 8s, força seek ao live edge
                        stallDetectorJob?.cancel()
                        stallDetectorJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(8_000)
                            // Ainda em buffering após 8s — tenta seek ao live edge
                            if (liveTvPlayer.playbackState == Player.STATE_BUFFERING) {
                                liveTvPlayer.seekToDefaultPosition()
                                // Se continuar em stall após mais 6s, reconecta o canal
                                kotlinx.coroutines.delay(6_000)
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
            val playlists = repository.allPlaylists.first()
            if (playlists.isNotEmpty()) {
                playlists.maxByOrNull { it.lastUsed }?.let { lastUsed ->
                    _currentPlaylist.value = lastUsed
                    repository.activatePlaylist(lastUsed.url)
                    fetchRealAccountInfo(lastUsed.url)
                }
                // Verifica com o painel se o dispositivo ainda tem acesso antes de liberar o app
                validateDeviceAccess()
                _isInitializing.value = false
            } else {
                // Sem playlists locais — mostra a tela de ativação para o usuário clicar em Sincronizar
                _isInitializing.value = false
            }
        }

        // Verificação periódica a cada 30 minutos — corta acesso se MAC removido ou bloqueado
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30 * 60 * 1000L) // 30 minutos
                validateDeviceAccess()
            }
        }
    }


    private val phrases = listOf(
        "Carregando seus conteúdos...",
        "Preparando o catálogo...",
        "Organizando seus filmes...",
        "Sincronizando as séries...",
        "Ajustando os canais ao vivo...",
        "Buscando as capas oficiais...",
        "Quase lá! Deixando tudo pronto para você..."
    )

    val liveTvChannels: Flow<PagingData<Channel>> = repository.liveTvChannels
    val movies: Flow<PagingData<Channel>> = repository.movieChannels
    val series: Flow<PagingData<Channel>> = repository.seriesChannels

    @OptIn(ExperimentalCoroutinesApi::class)
    val featuredMovies: StateFlow<List<Channel>> = _currentPlaylist.flatMapLatest { playlist ->
        _homeReady.value = false
        if (playlist == null) flowOf(emptyList())
        else repository.getFeaturedContent(playlist.url)
            .transformLatest { list ->
                emit(list)
                    kotlinx.coroutines.delay(1000)
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

    fun getPagedMoviesByCategory(group: String): Flow<PagingData<Channel>> =
        movieFlowCache.getOrPut(group) {
            if (movieFlowCache.size >= MAX_CACHE_SIZE) {
                movieFlowCache.remove(movieFlowCache.keys.first())
            }
            repository.getPagedMoviesByCategory(group).cachedIn(viewModelScope)
        }

    fun getPagedSeriesByCategory(group: String): Flow<PagingData<Channel>> =
        seriesFlowCache.getOrPut(group) {
            if (seriesFlowCache.size >= MAX_CACHE_SIZE) {
                seriesFlowCache.remove(seriesFlowCache.keys.first())
            }
            repository.getPagedSeriesByCategory(group).cachedIn(viewModelScope)
        }

    fun clearPagingCaches() {
        movieFlowCache.clear()
        seriesFlowCache.clear()
        enrichingIds.clear()
    }

    private val enrichingIds = mutableSetOf<Int>()

    fun onChannelVisible(channel: Channel) {
        if (channel.category != "LIVE_TV"
            && channel.tmdbSynopsis.isNullOrEmpty()
            && enrichingIds.add(channel.id)
        ) {
            viewModelScope.launch {
                repository.enrichChannelWithTmdb(channel)
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
        _epgListings.value = emptyList()
        // Busca EPG com cache
        if (channel != null) {
            val streamId = try {
                channel.remoteId.replace("live_", "").toInt()
            } catch (e: Exception) { -1 }
            if (streamId != -1) fetchEpg(streamId)
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
                _epgListings.value = listings
                epgCache[streamId] = EpgCacheEntry(listings, System.currentTimeMillis())
                // Limpa entradas antigas para não crescer infinitamente
                if (epgCache.size > 50) {
                    val oldest = epgCache.entries.minByOrNull { it.value.timestamp }?.key
                    oldest?.let { epgCache.remove(it) }
                }
            }.onFailure {
                _epgListings.value = emptyList()
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
            val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
            return androidId.chunked(2).take(6).joinToString(":").uppercase()
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
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
        return androidId.chunked(2).take(6).joinToString(":").uppercase()
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

            if (responseCode == 404 || (responseCode != 200 && responseCode != 403) || body.contains("not_found")) {
                revokeAccess()
                return
            }

            if (responseCode == 200) {
                val json = org.json.JSONObject(body)
                val status = json.optString("status", "")
                if (status == "Bloqueado") {
                    _isDeviceBlocked.value = true
                    _accountInfo.value = _accountInfo.value?.copy(accountStatus = "BLOQUEADO")
                } else {
                    _isDeviceBlocked.value = false
                    val hasPlaylist = json.has("playlist") && !json.isNull("playlist")
                    if (!hasPlaylist) {
                        revokeAccess()
                    }
                }
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
                    val message = if (body.contains("blocked")) {
                        org.json.JSONObject(body).optString("message", "Dispositivo bloqueado. Contate seu revendedor.")
                    } else {
                        "Dispositivo bloqueado. Contate seu revendedor."
                    }
                    _errorMessage.value = message
                    _isLoading.value = false
                    rotateJob.cancel()
                    return@launch
                }

                if (responseCode != 200 || body.contains("not_found")) {
                    _errorMessage.value = "Dispositivo não cadastrado no painel.\nContate seu revendedor."
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
                            _currentPlaylist.value = savedPlaylist ?: com.cinex.player.data.model.Playlist(
                                name = "CineX Panel", url = url, lastUsed = System.currentTimeMillis()
                            )
                            fetchRealAccountInfo(url)
                        }.onFailure {
                            _errorMessage.value = it.message ?: "Erro ao carregar lista M3U"
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
                            _currentPlaylist.value = com.cinex.player.data.model.Playlist(
                                name = "CineX Panel (Xtream)", url = xtreamUrl, lastUsed = System.currentTimeMillis()
                            )
                            fetchRealAccountInfo(xtreamUrl)
                        }.onFailure {
                            _errorMessage.value = it.message ?: "Erro ao carregar lista Xtream"
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
                _isInitializing.value = false
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: Flow<PagingData<Channel>> = _searchQuery
        .debounce(500)
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
            enrichChannelMetadata(channel)
        }
    }

    private fun enrichChannelMetadata(channel: Channel) {
        viewModelScope.launch {
            if (channel.category == "SERIES") {
                _isLoadingEpisodes.value = true
                // Primeiro carrega episódios do servidor, DEPOIS enriquece com TMDB
                val seriesId = try { channel.remoteId.replace("series_", "").toInt() } catch (e: Exception) { -1 }
                if (seriesId != -1) {
                    repository.fetchAndStoreEpisodes(seriesId, channel.seriesName ?: channel.name)
                }
                repository.enrichChannelWithTmdb(channel)
                _isLoadingEpisodes.value = false
            } else {
                repository.enrichChannelWithTmdb(channel)
            }
        }
    }

    fun getSeasonsForSeries(seriesName: String): Flow<List<Int>> {
        return repository.getSeasonsForSeries(seriesName)
    }

    fun getEpisodesBySeasonPaged(seriesName: String, season: Int): Flow<PagingData<Channel>> {
        return repository.getEpisodesBySeasonPaged(seriesName, season)
    }

    private fun reconnectLiveChannel() {
        val lastChannel = _selectedLiveChannel.value
        if (lastChannel != null && liveRetryCount < maxLiveRetries) {
            liveRetryCount++
            viewModelScope.launch {
                kotlinx.coroutines.delay(1500L * liveRetryCount) // backoff progressivo
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
        }
    }

    fun stopLiveTv() {
        stallDetectorJob?.cancel()
        liveTvPlayer.playWhenReady = false
        liveTvPlayer.pause()
        liveTvPlayer.stop()
        liveTvPlayer.clearMediaItems()
    }

    override fun onCleared() {
        super.onCleared()
        liveTvPlayer.release()
    }

    fun updateLiveTvVisibility(hidden: Boolean) { _isLiveTvHidden.value = hidden }
    fun updateMoviesVisibility(hidden: Boolean) { _isMoviesHidden.value = hidden }
    fun updateSeriesVisibility(hidden: Boolean) { _isSeriesHidden.value = hidden }
    fun updateTimeFormat(is24Hour: Boolean) { _is24HourFormat.value = is24Hour }
    fun updateParentalControl(enabled: Boolean) { _isParentalControlEnabled.value = enabled }

    fun selectPlaylist(playlist: com.cinex.player.data.model.Playlist) {
        viewModelScope.launch {
            _isLoading.value = true
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
                _errorMessage.value = it.message ?: "Erro ao conectar ao servidor"
            }
            
            rotateJob.cancel()
            _isLoading.value = false
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
        val playlist = _currentPlaylist.value ?: return
        loadPlaylist(playlist.url)
    }

    fun loadPlaylist(url: String) {
        viewModelScope.launch {
            clearPagingCaches()
            _isLoading.value = true
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
                _errorMessage.value = it.message ?: "Erro desconhecido ao carregar lista"
            }

            rotateJob.cancel()
            _isLoading.value = false
        }
    }

    fun updateFavorite(channelId: Int, isFav: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(channelId, isFav)
            // Atualiza o canal selecionado localmente para refletir na UI
            val current = _selectedLiveChannel.value
            if (current != null && current.id == channelId) {
                _selectedLiveChannel.value = current.copy(isFavorite = isFav)
            }
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
