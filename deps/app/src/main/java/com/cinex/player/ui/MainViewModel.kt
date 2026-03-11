package com.cinex.player.ui

import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinex.player.data.model.Channel
import com.cinex.player.data.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import com.cinex.player.data.network.XtreamCodesApi
import okhttp3.OkHttpClient // Novo
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // Novo
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
    private val okHttpClient: OkHttpClient, // Alterado
    val liveTvPlayer: ExoPlayer
) : ViewModel() {
    
    init {
        generateAccountInfo()
        // Carrega automaticamente a última playlist usada
        viewModelScope.launch {
            repository.allPlaylists.first().maxByOrNull { it.lastUsed }?.let { lastUsed ->
                selectPlaylist(lastUsed)
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _syncStatus = MutableStateFlow("Iniciando...")
    val syncStatus = _syncStatus.asStateFlow()

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

    private val _featuredMovies = MutableStateFlow<List<Channel>>(emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val featuredMovies: StateFlow<List<Channel>> = _featuredMovies.asStateFlow()

    private fun loadFeaturedMovies() {
        viewModelScope.launch {
            _featuredMovies.value = repository.getFeaturedContent()
        }
    }

    val misc: Flow<PagingData<Channel>> = repository.miscChannels

    val liveCategories: Flow<List<com.cinex.player.data.model.Category>> = repository.liveCategories
    val movieCategories: Flow<List<com.cinex.player.data.model.Category>> = repository.movieCategories
    val seriesCategories: Flow<List<com.cinex.player.data.model.Category>> = repository.seriesCategories

    fun getPagedChannelsByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedChannelsByCategory(group).cachedIn(viewModelScope)

    fun getPagedMoviesByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedMoviesByCategory(group).cachedIn(viewModelScope)

    fun getPagedSeriesByCategory(group: String): Flow<PagingData<Channel>> = 
        repository.getPagedSeriesByCategory(group).cachedIn(viewModelScope)

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

    private val _currentPlaylist = MutableStateFlow<com.cinex.player.data.model.Playlist?>(null)
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _selectedChannelForDetails = MutableStateFlow<Channel?>(null)
    val selectedChannelForDetails = _selectedChannelForDetails.asStateFlow()

    private val _accountInfo = MutableStateFlow<AccountInfo?>(null)
    val accountInfo = _accountInfo.asStateFlow()

    private fun fetchRealAccountInfo(playlistUrl: String) {
        viewModelScope.launch {
            try {
                // Tenta extrair baseUrl, username e password da URL m3u
                // Ex: http://server:port/get.php?username=XXX&password=YYY...
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

                        // Atualiza as informações mantendo o MAC e Key do dispositivo
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
            val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
            
            // Simulamos um MAC determinístico baseado no Android ID para apps de IPTV
            val simulatedMac = androidId.chunked(2).take(6).joinToString(":").uppercase()
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
        private const val PANEL_BASE_URL = "https://cine-x-player.vercel.app"
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
                // Gerar MAC diretamente em vez de depender do accountInfo
                val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "000000000000"
                val mac = androidId.chunked(2).take(6).joinToString(":").uppercase()

                // Buscar a configuração de playlist do painel web (em IO thread)
                val apiUrl = "$PANEL_BASE_URL/api/device/${mac}"
                val (responseCode, body) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(apiUrl).get().build()
                    val response = okHttpClient.newCall(request).execute()
                    Pair(response.code, response.body?.string() ?: "")
                }

                if (responseCode != 200 || body.contains("not_found")) {
                    _errorMessage.value = "Dispositivo não cadastrado no painel. Contate seu revendedor.\nMAC: $mac"
                    _isLoading.value = false
                    rotateJob.cancel()
                    return@launch
                }

                // Parsear JSON de resposta
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
                            _currentPlaylist.value = com.cinex.player.data.model.Playlist(
                                name = "CineX Panel", url = url, lastUsed = System.currentTimeMillis()
                            )
                            loadFeaturedMovies()
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
                            loadFeaturedMovies()
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
            
            // Corrotina para frases rotativas
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
                loadFeaturedMovies() // Atualiza destaques
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
        viewModelScope.launch {
            _isLoading.value = true
            _syncStatus.value = "Sincronizando mudanças..."
            
            // Trigger SyncWorker
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.cinex.player.data.worker.SyncWorker>()
                .setInputData(androidx.work.workDataOf("playlist_url" to playlist.url))
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build())
                .build()
            
            androidx.work.WorkManager.getInstance(app).enqueue(workRequest)
            
            // Para feedback imediato na UI, podemos opcionalmente observar o status do worker
            // mas aqui vamos apenas simular que iniciou e esperar o repository (ou deixar em background total)
            _syncStatus.value = "Atualização iniciada em segundo plano"
            kotlinx.coroutines.delay(2000)
            _isLoading.value = false
        }
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
                loadFeaturedMovies() // Atualiza destaques
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
        _currentPlaylist.value = null
        _isLoading.value = false
        _errorMessage.value = null
    }

    // ── Settings update functions ──
    fun updateLiveTvVisibility(hidden: Boolean) {
        _isLiveTvHidden.value = hidden
    }

    fun updateMoviesVisibility(hidden: Boolean) {
        _isMoviesHidden.value = hidden
    }

    fun updateSeriesVisibility(hidden: Boolean) {
        _isSeriesHidden.value = hidden
    }

    fun updateTimeFormat(is24Hour: Boolean) {
        _is24HourFormat.value = is24Hour
    }

    fun updateParentalControl(enabled: Boolean) {
        _isParentalControlEnabled.value = enabled
    }
}
