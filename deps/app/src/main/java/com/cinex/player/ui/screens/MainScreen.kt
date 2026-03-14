package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DarkBackground

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    


    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var playingChannel by remember { mutableStateOf<com.cinex.player.data.model.Channel?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isAddingPlaylist by remember { mutableStateOf(false) }
    var isServerSwapOpen by remember { mutableStateOf(false) }
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    
    val selectedDetailsChannel by viewModel.selectedChannelForDetails.collectAsState()
    
    val liveProgress by viewModel.liveProgress.collectAsState()
    val movieProgress by viewModel.movieProgress.collectAsState()
    val seriesProgress by viewModel.seriesProgress.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults = viewModel.searchResults // Passamos o Flow diretamente
    val accountInfo by viewModel.accountInfo.collectAsState()

    // Simplificamos a lógica de seleção de playlist: se não houver playlist ativa
    val showPlaylistSelectionDashboard = currentPlaylist == null || isServerSwapOpen

    val isLiveHidden by viewModel.isLiveTvHidden.collectAsState()
    val isMoviesHidden by viewModel.isMoviesHidden.collectAsState()
    val isSeriesHidden by viewModel.isSeriesHidden.collectAsState()

    val continueWatching by viewModel.continueWatching.collectAsState()
    
    // LOGICA PARA PARAR O SOM AO SAIR DA TV AO VIVO
    LaunchedEffect(selectedTab, playingChannel) {
        if (selectedTab != 1 && playingChannel == null) {
            viewModel.stopLiveTv()
        }
    }
    if (isLoading) {
        // RESTORED: Initial Sync Screen (TV, Movies, Series cards)
        CinematicLoadingScreen(
            statusMessage = syncStatus,
            tvProgress = liveProgress / 100f,
            moviesProgress = movieProgress / 100f,
            seriesProgress = seriesProgress / 100f
        )
    } else if (playingChannel != null) {
        VideoPlayerScreen(
            channel = playingChannel!!,
            onBack = { playingChannel = null }
        )
    } else if (selectedDetailsChannel != null) {
        // Tela de Detalhes (Premium)
        if (selectedDetailsChannel!!.category == "SERIES") {
            SeriesDetailsScreen(
                series = selectedDetailsChannel!!,
                viewModel = viewModel,
                onBack = { viewModel.selectChannelForDetails(null) },
                onPlayEpisode = { 
                    playingChannel = it
                    viewModel.selectChannelForDetails(null) 
                }
            )
        } else {
            MovieDetailsScreen(
                movie = selectedDetailsChannel!!,
                onBack = { viewModel.selectChannelForDetails(null) },
                onPlay = { 
                    playingChannel = it
                    viewModel.selectChannelForDetails(null)
                }
            )
        }
    } else if (showPlaylistSelectionDashboard) {
        if (isAddingPlaylist) {
            LoginScreen(
                isLoading = false,
                errorMessage = errorMessage,
                onLoginClick = { url ->
                    viewModel.addPlaylist(url)
                    isAddingPlaylist = false
                }
            )
        } else {
            PlaylistSelectionScreen(
                viewModel = viewModel
            )
        }
        
        // Resetamos isServerSwapOpen quando uma playlist for carregada
        LaunchedEffect(currentPlaylist) {
            if (currentPlaylist != null) {
                isServerSwapOpen = false
            }
        }
    } else if (isSettingsOpen) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { isSettingsOpen = false },
            onServerSwap = { 
                isServerSwapOpen = true
                isSettingsOpen = false 
            }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            if (selectedTab != 0 || searchQuery.isNotEmpty()) {
                TopNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { 
                        selectedTab = it
                        viewModel.updateSearchQuery("") // Limpa busca ao trocar aba
                    },
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.updateSearchQuery(it) },
                    showLive = !isLiveHidden,
                    showMovies = !isMoviesHidden,
                    showSeries = !isSeriesHidden
                )
            }
            
            val featuredMovies by viewModel.featuredMovies.collectAsState()
            val isHomeReady by viewModel.homeReady.collectAsState()

            if (searchQuery.isNotEmpty()) {
                // TELA DE BUSCA GLOBAL
                VodScreen(
                    channels = searchResults,
                    type = "SEARCH", // Opcional: tratar como busca
                    viewModel = viewModel,
                    title = "RESULTADOS PARA: ${searchQuery.uppercase()}",
                    continueWatching = emptyList(),
                    onVideoClick = { viewModel.selectChannelForDetails(it) }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(
                        featuredMovies = featuredMovies,
                        isHomeReady = isHomeReady,
                        onHomeReady = { viewModel.setHomeReady(true) },
                        onNavigate = { selectedTab = it },
                        onSettingsClick = { isSettingsOpen = true },
                        onRefresh = { viewModel.refreshPlaylist() },
                        accountInfo = accountInfo,
                        macAddress = accountInfo?.macAddress ?: ""
                    )
                    1 -> LiveTvScreen(
                        viewModel = viewModel,
                        onChannelExpand = { playingChannel = it }
                    )
                    2 -> VodScreen(
                        type = "MOVIE",
                        viewModel = viewModel,
                        title = "FILMES",
                        continueWatching = continueWatching,
                        onVideoClick = { viewModel.selectChannelForDetails(it) }
                    )
                    3 -> VodScreen(
                        type = "SERIES",
                        viewModel = viewModel,
                        title = "SÉRIES",
                        onVideoClick = { viewModel.selectChannelForDetails(it) }
                    )
                }
            }
        }
    }
}
