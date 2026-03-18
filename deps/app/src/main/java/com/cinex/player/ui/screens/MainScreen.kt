package com.cinex.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val isInitializing by viewModel.isInitializing.collectAsState()
    val searchResults = viewModel.searchResults // Passamos o Flow diretamente
    val accountInfo by viewModel.accountInfo.collectAsState()

    // Simplificamos a lógica de seleção de playlist: se não houver playlist ativa
    val showPlaylistSelectionDashboard = currentPlaylist == null || isServerSwapOpen

    val isLiveHidden by viewModel.isLiveTvHidden.collectAsState()
    val isMoviesHidden by viewModel.isMoviesHidden.collectAsState()
    val isSeriesHidden by viewModel.isSeriesHidden.collectAsState()

    val continueWatching by viewModel.continueWatching.collectAsState()
    val isDeviceBlocked by viewModel.isDeviceBlocked.collectAsState()

    // Para o player quando o app vai para background (botão Home, notificações, etc.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopLiveTv()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    if (isDeviceBlocked) {
        DeviceBlockedScreen(
            macAddress = accountInfo?.macAddress ?: "",
            onRetry = { viewModel.refreshAccountFromPanel() }
        )
    } else if (isInitializing || isLoading) {
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
                        onAccountOpen = { viewModel.refreshAccountFromPanel() }
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

@Composable
fun DeviceBlockedScreen(macAddress: String, onRetry: () -> Unit = {}) {
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(isChecking) {
        if (isChecking) {
            kotlinx.coroutines.delay(2000)
            isChecking = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Background image
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(12.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.2f
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = Color(0xFFC62828),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DISPOSITIVO BLOQUEADO",
                color = Color(0xFFC62828),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Seu dispositivo foi desativado pelo seu revendedor.\nEntre em contato para mais informações.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "MAC: $macAddress",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .widthIn(min = 280.dp)
                    .background(
                        if (isChecking) Color(0xFF888888) else Color(0xFFC62828),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !isChecking) {
                        isChecking = true
                        onRetry()
                    }
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isChecking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "VERIFICANDO...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Text(
                        text = "VERIFICAR NOVAMENTE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
