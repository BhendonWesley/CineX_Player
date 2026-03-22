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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
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
import androidx.paging.compose.collectAsLazyPagingItems

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

    val continueWatching by viewModel.continueWatching.collectAsState()
    val isDeviceBlocked by viewModel.isDeviceBlocked.collectAsState()
    val livePagingItems = viewModel.liveTvPagingData.collectAsLazyPagingItems()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Simplificamos a lógica de seleção de playlist: se não houver playlist ativa
    val showPlaylistSelectionDashboard = currentPlaylist == null || isServerSwapOpen

    // Para o Live TV sempre que sair do contexto principal (playlist removida, bloqueio, etc.)
    LaunchedEffect(showPlaylistSelectionDashboard, isDeviceBlocked) {
        if (showPlaylistSelectionDashboard || isDeviceBlocked) {
            viewModel.stopLiveTv()
        }
    }

    val isLiveHidden by viewModel.isLiveTvHidden.collectAsState()
    val isMoviesHidden by viewModel.isMoviesHidden.collectAsState()
    val isSeriesHidden by viewModel.isSeriesHidden.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncCompletedEvent.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.deltaSyncMessage.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Para o player ao trocar de aba (imediato, sem depender do onDispose do LiveTvScreen)
    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) {
            viewModel.stopLiveTv()
        }
    }

    // Para o player ao abrir tela de detalhes (filme/série) — o usuário saiu do Live TV
    LaunchedEffect(selectedDetailsChannel) {
        if (selectedDetailsChannel != null) {
            viewModel.stopLiveTv()
        }
    }

    // Para o player quando o app vai para background (botão Home, notificações, etc.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopLiveTv()
            }
            if (event == Lifecycle.Event.ON_RESUME && (selectedTab == 1 || (playingChannel != null && playingChannel?.category == "LIVE_TV"))) {
                viewModel.resumeLiveTv()
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
    } else if (isLoading && isSyncing) {
        CinematicLoadingScreen(
            statusMessage = syncStatus,
            tvProgress = liveProgress / 100f,
            moviesProgress = movieProgress / 100f,
            seriesProgress = seriesProgress / 100f
        )
    } else if (isLoading) {
        // Loading simples (não é sync) — tela preta com indicador
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color(0xFFB21E2B),
                modifier = Modifier.size(48.dp)
            )
        }
    } else if (isInitializing) {
        AnimatedSplashScreen()
    } else if (playingChannel != null && playingChannel!!.category != "LIVE_TV") {
        // Para filmes/séries, substitui a tela inteira
        VideoPlayerScreen(
            channel = playingChannel!!,
            onBack = { playingChannel = null },
            onPlayNext = { nextChannel ->
                playingChannel = nextChannel
            }
        )
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
        Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            if (selectedTab != 0 || searchQuery.isNotEmpty()) {
                TopNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        viewModel.updateSearchQuery("")
                    },
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.updateSearchQuery(it) },
                    onMenuClick = { isSettingsOpen = true },
                    showLive = !isLiveHidden,
                    showMovies = !isMoviesHidden,
                    showSeries = !isSeriesHidden
                )
            }
            
            val featuredMovies by viewModel.featuredMovies.collectAsState()
            val isHomeReady by viewModel.homeReady.collectAsState()

            Box(modifier = Modifier.weight(1f).clipToBounds()) {
                if (searchQuery.isNotEmpty()) {
                    // TELA DE BUSCA GLOBAL
                    VodScreen(
                        channels = searchResults,
                        type = "SEARCH",
                        viewModel = viewModel,
                        title = "RESULTADOS PARA: ${searchQuery.uppercase()}",
                        continueWatching = emptyList(),
                        onVideoClick = { channel ->
                            if (channel.category == "LIVE_TV") {
                                // Leva direto para o preview na aba TV ao Vivo, na categoria correta
                                if (channel.categoryId.isNotEmpty()) {
                                    viewModel.setLiveCategory(channel.categoryId)
                                }
                                viewModel.updateSelectedChannel(channel)
                                viewModel.playLiveChannel(channel)
                                viewModel.updateSearchQuery("")
                                selectedTab = 1
                            } else {
                                viewModel.stopLiveTv()
                                viewModel.selectChannelForDetails(channel)
                            }
                        }
                    )
                } else {
                    // Mantém todas as telas compostas para preservar o estado do Paging.
                    // Apenas a aba ativa é visível e recebe input.
                    fun Modifier.tabVisibility(tabIndex: Int): Modifier = this
                        .alpha(if (selectedTab == tabIndex) 1f else 0f)
                        .then(if (selectedTab != tabIndex) Modifier.size(0.dp) else Modifier.fillMaxSize())

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.tabVisibility(0)) {
                            HomeScreen(
                                featuredMovies = featuredMovies,
                                isHomeReady = isHomeReady,
                                onHomeReady = { viewModel.setHomeReady(true) },
                                onNavigate = { selectedTab = it },
                                onSettingsClick = { isSettingsOpen = true },
                                onRefresh = { viewModel.refreshPlaylist() },
                                accountInfo = accountInfo,
                                onAccountOpen = { viewModel.refreshAccountFromPanel() },
                                isActive = selectedTab == 0
                            )
                        }
                        Box(modifier = Modifier.tabVisibility(1)) {
                            LiveTvScreen(
                                viewModel = viewModel,
                                onChannelExpand = { playingChannel = it },
                                isActive = selectedTab == 1
                            )
                        }
                        Box(modifier = Modifier.tabVisibility(2)) {
                            VodScreen(
                                type = "MOVIE",
                                viewModel = viewModel,
                                title = "FILMES",
                                continueWatching = continueWatching.filter { it.category == "MOVIE" },
                                onVideoClick = { viewModel.selectChannelForDetails(it) },
                                onPlayDirect = { playingChannel = it }
                            )
                        }
                        Box(modifier = Modifier.tabVisibility(3)) {
                            VodScreen(
                                type = "SERIES",
                                viewModel = viewModel,
                                title = "SÉRIES",
                                continueWatching = continueWatching.filter { it.category == "SERIES" },
                                onVideoClick = { viewModel.selectChannelForDetails(it) },
                                onPlayDirect = { playingChannel = it }
                            )
                        }
                    }
                }
            }
        }

        // Detalhes de filme/série: overlay para manter tabs compostas por baixo
        if (selectedDetailsChannel != null) {
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
        }

        // Live TV fullscreen: overlay sobre o conteúdo para não destruir o LiveTvScreen
        if (playingChannel != null && playingChannel!!.category == "LIVE_TV") {
            VideoPlayerScreen(
                channel = playingChannel!!,
                onBack = { playingChannel = null },
                onPlayNext = { nextChannel ->
                    playingChannel = nextChannel
                },
                onPreviousChannel = {
                    val current = playingChannel ?: return@VideoPlayerScreen
                    // Encontra o canal anterior na lista paginada
                    for (i in 0 until livePagingItems.itemCount) {
                        if (livePagingItems.peek(i)?.id == current.id && i > 0) {
                            livePagingItems[i - 1]?.let { prevChannel ->
                                viewModel.updateSelectedChannel(prevChannel)
                                viewModel.playLiveChannel(prevChannel)
                                playingChannel = prevChannel
                            }
                            break
                        }
                    }
                },
                onNextChannel = {
                    val current = playingChannel ?: return@VideoPlayerScreen
                    // Encontra o próximo canal na lista paginada
                    for (i in 0 until livePagingItems.itemCount) {
                        if (livePagingItems.peek(i)?.id == current.id && i < livePagingItems.itemCount - 1) {
                            livePagingItems[i + 1]?.let { nextChannel ->
                                viewModel.updateSelectedChannel(nextChannel)
                                viewModel.playLiveChannel(nextChannel)
                                playingChannel = nextChannel
                            }
                            break
                        }
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            snackbar = { data ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = data.visuals.message,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier
                            .background(Color(0xFF282B30), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        )
        } // fim Box
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
                .blur(4.dp),
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
