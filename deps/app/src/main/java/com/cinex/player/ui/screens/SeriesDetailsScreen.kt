package com.cinex.player.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DarkBackground

enum class SeriesViewMode { LANDING, LOADING, EPISODES }

// Remove anos duplicados tipo "(2021) (2021)" → "(2021)"
private fun cleanSeriesTitle(title: String): String {
    val yearPattern = Regex("\\(\\d{4}\\)")
    val matches = yearPattern.findAll(title).toList()
    if (matches.size <= 1) return title
    var count = 0
    val result = yearPattern.replace(title) { m ->
        count++
        if (count == 1) m.value else ""
    }
    return result.replace(Regex("\\s+"), " ").trim()
}

// Remove o prefixo "{SeriesName} (YYYY) S{N} E{N}" do nome do episódio,
// deixando apenas o título real do episódio se existir.
private fun cleanEpisodeName(episodeName: String, seriesName: String?): String {
    var name = episodeName.trim()
    // Remove prefixo do nome da série (com ou sem ano)
    if (seriesName != null) {
        val base = seriesName.trim()
        if (name.startsWith(base, ignoreCase = true)) {
            name = name.removePrefix(base).trim()
        }
    }
    // Remove (YYYY) do início
    name = name.replace(Regex("^\\(\\d{4}\\)\\s*"), "")
    // Remove padrão "S{N} E{N}" ou "S{NN} E{NN}" do início
    name = name.replace(Regex("^S\\d+\\s*E\\d+\\s*[-–]?\\s*"), "")
    // Remove ano entre parênteses solto no início
    name = name.replace(Regex("^\\(\\d{4}\\)\\s*"), "")
    return if (name.isBlank()) episodeName else name.trim()
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailsScreen(
    series: Channel,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (Channel) -> Unit
) {
    val hydratedSeries by viewModel.observeChannelByRemoteId(series.remoteId).collectAsState(initial = null)
    val currentSeries = hydratedSeries ?: series
    val seriesName = currentSeries.seriesName ?: currentSeries.name
    val isLoadingEpisodes by viewModel.isLoadingEpisodes.collectAsState()
    val seasons by viewModel.getSeasonsForSeries(seriesName).collectAsState(initial = emptyList())
    val sortedSeasons = seasons.mapNotNull { it }.filter { it > 0 }.sorted()
    var selectedSeason by remember(series.remoteId) { mutableStateOf(1) }
    var viewMode by remember(series.remoteId) { mutableStateOf(SeriesViewMode.LOADING) }
    var pendingOpenEpisodes by remember(series.remoteId) { mutableStateOf(false) }
    val hasEpisodesReady = sortedSeasons.isNotEmpty()
    // Abre assim que qualquer temporada está no banco — TMDB roda em background sem bloquear
    val canOpenEpisodes = hasEpisodesReady

    val context = LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    // Só permite requisições de foco quando esta tela está visível (RESUMED).
    // Quando está no back stack atrás de VideoPlayerScreen, fica em STARTED e
    // não deve roubar o foco de dialogs do player.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    var isScreenResumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            isScreenResumed = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(series.remoteId) {
        viewModel.ensureSeriesDetailsReady(currentSeries, skipTmdb = isTv)
    }

    LaunchedEffect(sortedSeasons) {
        if (sortedSeasons.isNotEmpty()) {
            if (selectedSeason !in sortedSeasons) {
                selectedSeason = sortedSeasons.first()
            }
        }
    }

    // Gerencia as transições a partir da tela de LOADING garantida no início.
    // Observa também sortedSeasons para desbloquear quando o Room emitir dados
    // mesmo que isLoadingEpisodes nunca tenha mudado (séries com cache local).
    LaunchedEffect(viewMode, canOpenEpisodes, pendingOpenEpisodes, isLoadingEpisodes, sortedSeasons) {
        if (viewMode == SeriesViewMode.LOADING) {
            if (!isLoadingEpisodes) {
                // Terminou de carregar (ou cache local já disponível): sai do LOADING
                if (pendingOpenEpisodes && canOpenEpisodes) {
                    pendingOpenEpisodes = false
                    viewMode = SeriesViewMode.EPISODES
                } else {
                    viewMode = SeriesViewMode.LANDING
                }
            } else if (canOpenEpisodes && !pendingOpenEpisodes) {
                // Ainda baixando em background, mas temporadas já chegaram do banco
                viewMode = SeriesViewMode.LANDING
            } else {
                // Timeout de segurança: nunca fica preso mais de 8s
                kotlinx.coroutines.delay(8000)
                if (viewMode == SeriesViewMode.LOADING) {
                    pendingOpenEpisodes = false
                    viewMode = SeriesViewMode.LANDING
                }
            }
        }
    }


    LaunchedEffect(series.remoteId, selectedSeason, viewMode, isLoadingEpisodes, sortedSeasons, pendingOpenEpisodes) {
        if (
            (viewMode == SeriesViewMode.EPISODES || (viewMode == SeriesViewMode.LOADING && pendingOpenEpisodes)) &&
            !isLoadingEpisodes &&
            selectedSeason > 0 &&
            sortedSeasons.contains(selectedSeason)
        ) {
            viewModel.enrichSeriesSeasonIfNeeded(currentSeries, selectedSeason, skipTmdb = isTv)
        }
    }

    // TV: lista em memória (instantâneo ao trocar temporada)
    // Mobile: Paging (economia de memória)
    var episodesList by remember { mutableStateOf<List<Channel>>(emptyList()) }
    LaunchedEffect(seriesName, selectedSeason) {
        if (seriesName.isBlank() || selectedSeason <= 0) {
            episodesList = emptyList()
        } else {
            viewModel.observeEpisodesBySeason(seriesName, selectedSeason).collect {
                // Ao trocar de temporada local, se vier vazio rápido (antes do Room responder),
                // só limpa se a ViewModel realmente for recarregar da API.
                // Isso evita o 'piscar' vazio entre as transições de temporada via banco de dados.
                if (it.isNotEmpty() || !viewModel.isLoadingEpisodes.value) {
                    episodesList = it
                }
            }
        }
    }

    val episodesFlow = remember(seriesName, selectedSeason) {
        if (!isTv && seriesName.isNotBlank() && selectedSeason > 0) {
            viewModel.getEpisodesBySeasonPaged(seriesName, selectedSeason)
        } else {
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())
        }
    }
    val pagingItems = episodesFlow.collectAsLazyPagingItems()

    val episodeCount = if (isTv) episodesList.size else pagingItems.itemCount
    val hasSelectedSeason = selectedSeason > 0 && sortedSeasons.contains(selectedSeason)
    val canShowSelectedSeasonEpisodes = canOpenEpisodes && hasSelectedSeason && episodeCount > 0
    val playButtonRequester = remember { FocusRequester() }
    val selectedSeasonFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    val backArrowFocusRequester = remember { FocusRequester() }

    // Contadores usados para disparar foco de forma segura via LaunchedEffect
    var focusEpisodesRequest by remember { mutableIntStateOf(0) }
    var focusSeasonRequest by remember { mutableIntStateOf(0) }
    var focusBackArrowRequest by remember { mutableIntStateOf(0) }
    var suppressLandingAutoFocus by remember(series.remoteId) { mutableStateOf(false) }
    var pendingEpisodeFocus by remember(series.remoteId) { mutableStateOf(false) }

    LaunchedEffect(focusEpisodesRequest, episodeCount, viewMode) {
        if (focusEpisodesRequest > 0 && viewMode == SeriesViewMode.EPISODES && episodeCount > 0 && isScreenResumed) {
            kotlinx.coroutines.delay(80)
            try { firstEpisodeFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(pendingEpisodeFocus, episodeCount, viewMode, selectedSeason) {
        if (pendingEpisodeFocus && viewMode == SeriesViewMode.EPISODES && episodeCount > 0 && isScreenResumed) {
            kotlinx.coroutines.delay(120)
            try { firstEpisodeFocusRequester.requestFocus() } catch (_: Exception) {}
            pendingEpisodeFocus = false
        }
    }

    LaunchedEffect(viewMode, pendingOpenEpisodes, canShowSelectedSeasonEpisodes) {
        if (viewMode == SeriesViewMode.LOADING && pendingOpenEpisodes && canShowSelectedSeasonEpisodes) {
            viewMode = SeriesViewMode.EPISODES
            pendingOpenEpisodes = false
        }
    }

    LaunchedEffect(focusSeasonRequest) {
        if (focusSeasonRequest > 0 && isScreenResumed) {
            kotlinx.coroutines.delay(80)
            try { selectedSeasonFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(focusBackArrowRequest) {
        if (focusBackArrowRequest > 0 && isScreenResumed) {
            kotlinx.coroutines.delay(80)
            try { backArrowFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(viewMode) {
        when (viewMode) {
            SeriesViewMode.LANDING -> if (isTv && isScreenResumed) {
                kotlinx.coroutines.delay(200)
                if (suppressLandingAutoFocus) {
                    suppressLandingAutoFocus = false
                    try { backArrowFocusRequester.requestFocus() } catch (_: Exception) {}
                } else {
                    try { playButtonRequester.requestFocus() } catch (_: Exception) {}
                }
            }
            SeriesViewMode.LOADING -> {} // Sem foco especial na tela de loading
            SeriesViewMode.EPISODES -> if (isTv && sortedSeasons.isNotEmpty() && isScreenResumed) {
                kotlinx.coroutines.delay(150)
                try { selectedSeasonFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    BackHandler {
        when (viewMode) {
            SeriesViewMode.EPISODES, SeriesViewMode.LOADING -> {
                suppressLandingAutoFocus = true
                viewMode = SeriesViewMode.LANDING
            }
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Otimização: .size(1280, 720) evita decodificar imagens full-size
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(currentSeries.bannerUrl ?: currentSeries.logoUrl)
                .size(1280, 720)
                .diskCacheKey(currentSeries.bannerUrl ?: currentSeries.logoUrl)
                .memoryCacheKey(currentSeries.bannerUrl ?: currentSeries.logoUrl)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DarkBackground),
                        startY = 0f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(DarkBackground, Color.Transparent),
                        endX = 1200f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        when (viewMode) {
                            SeriesViewMode.EPISODES, SeriesViewMode.LOADING -> {
                                suppressLandingAutoFocus = true
                                viewMode = SeriesViewMode.LANDING
                            }
                            else -> onBack()
                        }
                    },
                    modifier = Modifier
                        .then(if (isTv) Modifier.focusRequester(backArrowFocusRequester) else Modifier)
                        .onKeyEvent { event ->
                            if (!isTv || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (viewMode == SeriesViewMode.EPISODES) focusSeasonRequest++
                                    else try { playButtonRequester.requestFocus() } catch (_: Exception) {}
                                    true
                                }
                                Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }

            if (viewMode == SeriesViewMode.LANDING) {
                if (isTv) {
                    // === LAYOUT TV — usa logoUrl (M3U) como capa, sem chamadas TMDB ===
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 60.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Poster com redimensionamento otimizado
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentSeries.logoUrl)
                                .size(320, 480)
                                .diskCacheKey(currentSeries.logoUrl)
                                .memoryCacheKey(currentSeries.logoUrl)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(32.dp))

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Text(
                                text = cleanSeriesTitle(seriesName).uppercase(),
                                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Text(text = "${sortedSeasons.size} Temporadas", color = Color.Gray, fontSize = 18.sp)
                            }

                            if (!currentSeries.tmdbSynopsis.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 12.dp)
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                ) {
                                    val scrollState = rememberScrollState()
                                    Text(
                                        text = currentSeries.tmdbSynopsis!!,
                                        color = Color.LightGray, fontSize = 14.sp, lineHeight = 21.sp,
                                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(12.dp)
                                    )
                                    if (scrollState.canScrollForward) {
                                        Box(modifier = Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, DarkBackground.copy(alpha = 0.9f)))))
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val gradientBrush = remember { Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))) }
                                var isPlayFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        when {
                                            canShowSelectedSeasonEpisodes -> {
                                                pendingOpenEpisodes = false
                                                viewMode = SeriesViewMode.EPISODES
                                            }
                                            else -> {
                                                pendingOpenEpisodes = true
                                                viewMode = SeriesViewMode.LOADING
                                                viewModel.ensureSeriesDetailsReady(currentSeries, skipTmdb = isTv)
                                            }
                                        }
                                    },
                                    enabled = true,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, disabledContainerColor = Color.White.copy(alpha = 0.75f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp)
                                        .focusRequester(playButtonRequester)
                                        .onFocusChanged { isPlayFocused = it.isFocused }
                                        .onKeyEvent { event ->
                                            if (!isTv || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                            when (event.key) {
                                                Key.DirectionUp -> { focusBackArrowRequest++; true }
                                                Key.DirectionDown, Key.DirectionLeft -> true
                                                else -> false
                                            }
                                        }
                                        .then(if (isPlayFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp)) else Modifier),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    if (isLoadingEpisodes && !canOpenEpisodes) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        when {
                                            canOpenEpisodes -> "ASSISTIR EPISODIOS"
                                            isLoadingEpisodes || pendingOpenEpisodes -> "PREPARANDO EPISODIOS..."
                                            else -> "ASSISTIR EPISODIOS"
                                        },
                                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                var isTrailerFocused by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = {
                                        val searchQuery = java.net.URLEncoder.encode("${series.name} trailer oficial", "UTF-8")
                                        val searchUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
                                        val youtubeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri).apply { setPackage("com.google.android.youtube") }
                                        try { context.startActivity(youtubeIntent) } catch (e: android.content.ActivityNotFoundException) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri)) }
                                    },
                                    border = BorderStroke(2.dp, Color.White),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isTrailerFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp).width(140.dp)
                                        .onFocusChanged { isTrailerFocused = it.isFocused }
                                        .onKeyEvent { event ->
                                            if (!isTv || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                            when (event.key) {
                                                Key.DirectionUp -> { focusBackArrowRequest++; true }
                                                Key.DirectionDown, Key.DirectionRight -> true
                                                else -> false
                                            }
                                        }
                                        .then(if (isTrailerFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp)) else Modifier)
                                ) {
                                    Text("TRAILER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else {
                    // === LAYOUT MOBILE (horizontal como TV, ajustado pra caber) ===
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        AsyncImage(
                            model = currentSeries.posterUrl?.takeIf { it.isNotEmpty() } ?: currentSeries.logoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Text(
                                text = cleanSeriesTitle(seriesName).uppercase(),
                                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 22.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    repeat(5) { index ->
                                        val rating = (currentSeries.tmdbRating ?: 0.0) / 2
                                        Icon(Icons.Default.Star, contentDescription = null, tint = if (index < rating.toInt()) Color.Yellow else Color.DarkGray, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(String.format("%.1f", currentSeries.tmdbRating ?: 0.0), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "•", color = Color.Gray)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = currentSeries.tmdbYear ?: "N/A", color = Color.Gray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "•", color = Color.Gray)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "${sortedSeasons.size} Temp.", color = Color.Gray, fontSize = 14.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                            ) {
                                val scrollState = rememberScrollState()
                                Text(
                                    text = currentSeries.tmdbSynopsis ?: "Preparando detalhes da serie...",
                                    color = Color.LightGray, fontSize = 13.sp, lineHeight = 19.sp,
                                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(10.dp)
                                )
                                if (scrollState.canScrollForward) {
                                    Box(modifier = Modifier.fillMaxWidth().height(24.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, DarkBackground.copy(alpha = 0.9f)))))
                                }
                            }

                            Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        when {
                                            canOpenEpisodes -> viewMode = SeriesViewMode.EPISODES
                                            else -> { pendingOpenEpisodes = true; viewModel.ensureSeriesDetailsReady(currentSeries) }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(44.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    if (isLoadingEpisodes && !canOpenEpisodes) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (canOpenEpisodes) "EPISÓDIOS" else "PREPARANDO...",
                                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        val searchQuery = java.net.URLEncoder.encode("${series.name} trailer oficial", "UTF-8")
                                        val searchUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
                                        val youtubeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri).apply { setPackage("com.google.android.youtube") }
                                        try { context.startActivity(youtubeIntent) } catch (e: android.content.ActivityNotFoundException) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri)) }
                                    },
                                    border = BorderStroke(2.dp, Color.White),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(44.dp).width(110.dp)
                                ) {
                                    Text("TRAILER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            } else if (viewMode == SeriesViewMode.LOADING) {
                // TELA DE CARREGAMENTO (TV)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparando detalhes da série...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // SELEÇÃO DE EPISÓDIOS (Painel Duplo)
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
                    // Temporadas
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "TEMPORADAS",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            sortedSeasons.forEachIndexed { seasonIndex, seasonNum ->
                                val isFirst = seasonIndex == 0
                                val isSelected = selectedSeason == seasonNum
                                var isFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isTv && isSelected) Modifier.focusRequester(selectedSeasonFocusRequester) else Modifier)
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable(interactionSource = remember { MutableInteractionSource() })
                                        .onKeyEvent { event ->
                                            when (event.type) {
                                                KeyEventType.KeyDown -> when (event.key) {
                                                    Key.DirectionRight -> {
                                                        if (episodeCount > 0) {
                                                            focusEpisodesRequest++
                                                            pendingEpisodeFocus = false
                                                        } else {
                                                            pendingEpisodeFocus = true
                                                            pendingOpenEpisodes = true
                                                            viewMode = SeriesViewMode.LOADING
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionLeft -> true
                                                    // Primeira temporada: sobe para a seta de voltar
                                                    Key.DirectionUp -> if (isFirst) { focusBackArrowRequest++; true } else false
                                                    else -> false
                                                }
                                                KeyEventType.KeyUp -> when (event.key) {
                                                    Key.DirectionCenter, Key.Enter -> {
                                                        if (selectedSeason != seasonNum) {
                                                            selectedSeason = seasonNum
                                                        }
                                                        focusEpisodesRequest = 0 // evita auto-foco no 1º episódio durante troca de temporada
                                                        pendingEpisodeFocus = false
                                                        pendingOpenEpisodes = true
                                                        viewMode = SeriesViewMode.LOADING
                                                        true
                                                    }
                                                    else -> false
                                                }
                                                else -> false
                                            }
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (isFocused) Modifier
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .border(2.dp, Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))), RoundedCornerShape(8.dp))
                                            else if (isSelected) Modifier
                                                .background(Color.White.copy(alpha = 0.2f))
                                            else Modifier
                                                .background(Color.Transparent)
                                        )
                                        .clickable {
                                            if (selectedSeason != seasonNum) {
                                                selectedSeason = seasonNum
                                            }
                                            focusEpisodesRequest = 0
                                            pendingEpisodeFocus = false
                                            pendingOpenEpisodes = true
                                            viewMode = SeriesViewMode.LOADING
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = "Temporada $seasonNum",
                                        color = if (isFocused || isSelected) Color.White else Color.Gray,
                                        fontSize = 16.sp,
                                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (seasonIndex < sortedSeasons.lastIndex) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Episódios
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text(
                            text = "EPISÓDIOS - TEMPORADA $selectedSeason",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp, top = 16.dp)
                        )

                        if (isTv) {
                            // TV: lista em memória — instantâneo
                            if (episodesList.isEmpty() && isLoadingEpisodes) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(48.dp),
                                            strokeWidth = 4.dp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Carregando episódios...",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else if (episodesList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Temporada indisponível", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(episodesList, key = { _, ep -> ep.id }) { index, episode ->
                                        EpisodeItem(
                                            episode = episode,
                                            seriesPoster = currentSeries.posterUrl,
                                            seriesName = seriesName,
                                            modifier = if (index == 0) Modifier.focusRequester(firstEpisodeFocusRequester) else Modifier,
                                            onKeyLeft = {
                                                pendingEpisodeFocus = false
                                                focusSeasonRequest++
                                            },
                                            blockUp = index == 0,
                                            onClick = { onPlayEpisode(episode) }
                                        )
                                    }
                                }
                            }
                        } else {
                            // Mobile: Paging
                            // Usa apenas isPagingLoading — isLoadingEpisodes é sobre download de API
                            // e não reflete o estado do banco. Confiar no Paging evita spinner infinito.
                            val isPagingLoading = pagingItems.loadState.refresh is androidx.paging.LoadState.Loading
                            if (pagingItems.itemCount == 0 && isPagingLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(48.dp),
                                            strokeWidth = 4.dp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Carregando episódios...",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else if (pagingItems.itemCount == 0) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Temporada indisponível", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(
                                        count = pagingItems.itemCount,
                                        key = pagingItems.itemKey { it.id }
                                    ) { index ->
                                        pagingItems[index]?.let { episode ->
                                            EpisodeItem(
                                                episode = episode,
                                                seriesPoster = currentSeries.posterUrl,
                                                seriesName = seriesName,
                                                onClick = { onPlayEpisode(episode) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EpisodeItem(episode: com.cinex.player.data.model.Channel, seriesPoster: String?, seriesName: String? = null, modifier: Modifier = Modifier, onKeyLeft: (() -> Unit)? = null, blockUp: Boolean = false, onClick: () -> Unit) {
    val genericSeriesBackdrop = episode.bannerUrl?.contains("/original/") == true
    val imageModel = when {
        !episode.bannerUrl.isNullOrBlank() && !genericSeriesBackdrop -> episode.bannerUrl
        !seriesPoster.isNullOrBlank() -> seriesPoster
        else -> null
    }

    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> when (event.key) {
                        Key.DirectionLeft -> { onKeyLeft?.invoke(); true }
                        Key.DirectionRight -> false
                        // Primeiro episódio: bloqueia UP (não pode vazar para fora)
                        Key.DirectionUp -> if (blockUp) true else false
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (event.key) {
                        Key.DirectionCenter, Key.Enter -> { onClick(); true }
                        else -> false
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isFocused) Modifier
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(2.dp, Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))), RoundedCornerShape(12.dp))
                else Modifier
                    .background(Color.White.copy(alpha = 0.05f))
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF23262B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "EP ${episode.episodeNumber ?: "?"}",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (imageModel != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E${episode.episodeNumber ?: "?"}: ${cleanEpisodeName(episode.name, seriesName ?: episode.seriesName)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                if (!episode.tmdbSynopsis.isNullOrBlank()) {
                    Text(
                        text = episode.tmdbSynopsis!!,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
