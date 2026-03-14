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

    init {
        generateAccountInfo()
        viewModelScope.launch {
            val playlists = repository.allPlaylists.first()
            if (playlists.isNotEmpty()) {
                playlists.maxByOrNull { it.lastUsed }?.let { lastUsed ->
                    _currentPlaylist.value = lastUsed
                    repository.activatePlaylist(lastUsed.url)
                    fetchRealAccountInfo(lastUsed.url)
                }
            } else {
                syncFromPanel()
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _syncStatus = MutableStateFlow("Iniciando...")
    val syncStatus = _syncStatus.asStateFlow()

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

    fun getPagedChannelsByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedChannelsByCategory(group).cachedIn(viewModelScope)

    fun getPagedMoviesByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedMoviesByCategory(group).cachedIn(viewModelScope)

    fun getPagedSeriesByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedSeriesByCategory(group).cachedIn(viewModelScope)

    fun onChannelVisible(channel: Channel) {
        if (channel.category != "LIVE_TV" && (channel.posterUrl.isNullOrEmpty() || channel.tmdbSynopsis.isNullOrEmpty())) {
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

    fun updateSelectedChannel(channel: Channel?) {
        _selectedChannelTvgId.value = channel?.tvgId
        if (channel != null && channel.tvgId == null) {
            val streamId = try { channel.remoteId.replace("live_", "").toInt() } catch (e: Exception) { -1 }
            if (streamId != -1) fetchEpg(streamId)
        } else {
            _epgListings.value = emptyList()
        }
    }

    fun fetchEpg(streamId: Int) {
        viewModelScope.launch {
            repository.getShortEpg(streamId).onSuccess { response ->
                _epgListings.value = response.epg_listings ?: emptyList()
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

    private val _accountInfo = MutableStateFlow<AccountInfo?>(null)
    val accountInfo = _accountInfo.asStateFlow()

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
                        val expDateStr = if (info.exp_date != null && info.exp_date != "null") {
                            try {
                                val timestamp = info.exp_date.toLong() * 1000
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
                            } catch (e: Exception) {
                                "N/A"
                            }
                        } else {
                            "ILIMITADO"
                        }

                        _accountInfo.value = _accountInfo.value?.copy(
                            accountStatus = info.status?.uppercase() ?: "ATIVADO",
                            playlistExpiration = expDateStr
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateAccountInfo() {
        try {
            val simulatedMac = deviceMacAddress
            val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
            val deviceKey = (androidId.hashCode().toLong() and 0xFFFFFF).toString()

            _accountInfo.value = AccountInfo(
                macAddress = simulatedMac,
                deviceKey = deviceKey,
                accountStatus = "ATIVADO (PREMIUM)",
                activationDate = "2026-11-10",
                playlistExpiration = "03/05/2027"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val PANEL_BASE_URL = "https://gerencia-cine-x.vercel.app"
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
            repository.enrichChannelWithTmdb(channel)
        }
    }

    fun getSeasonsForSeries(seriesName: String): Flow<List<Int>> {
        return repository.getSeasonsForSeries(seriesName)
    }

    fun getEpisodesBySeasonPaged(seriesName: String, season: Int): Flow<PagingData<Channel>> {
        return repository.getEpisodesBySeasonPaged(seriesName, season)
    }

    fun stopLiveTv() {
        if (liveTvPlayer.isPlaying) {
            liveTvPlayer.pause()
            liveTvPlayer.stop()
            liveTvPlayer.clearMediaItems()
        }
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
        val playlist = _currentPlaylist.value ?: return
        loadPlaylist(playlist.url)
    }

    fun loadPlaylist(url: String) {
        viewModelScope.launch {
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
}
