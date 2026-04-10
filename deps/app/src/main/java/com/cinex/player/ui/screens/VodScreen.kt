package com.cinex.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.components.VodPosterItem
import com.cinex.player.ui.components.CategoryItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.cinex.player.ui.theme.CineX_BackgroundBlue
import androidx.paging.PagingData
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas

private val AccentGold = Color(0xFFD8A63A)
private val AccentRed  = Color(0xFFE11D2E)

private fun Modifier.gradientBorder(
    brush: Brush,
    borderWidth: Dp = 1.5.dp,
    cornerRadius: Dp = 16.dp
): Modifier = drawWithContent {
    drawContent()
    val strokeW = borderWidth.toPx()
    drawRoundRect(
        brush = brush,
        topLeft = Offset(strokeW / 2, strokeW / 2),
        size = Size(size.width - strokeW, size.height - strokeW),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
}

private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    trackColor: Color = Color.White.copy(alpha = 0.08f),
    thumbColor: Color = AccentGold,
    width: Dp = 3.dp,
    minThumbHeight: Dp = 32.dp
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue.toFloat()
    if (maxScroll <= 0f) return@drawWithContent

    val viewportH = size.height
    val totalH = maxScroll + viewportH
    val thumbH = (viewportH / totalH * viewportH).coerceAtLeast(minThumbHeight.toPx())
    val thumbTop = (scrollState.value / maxScroll) * (viewportH - thumbH)
    val trackW = width.toPx()
    val x = size.width - trackW - 2.dp.toPx()
    val r = CornerRadius(trackW / 2f)

    // Track
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(x, 0f),
        size = Size(trackW, viewportH),
        cornerRadius = r
    )
    // Thumb
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(x, thumbTop),
        size = Size(trackW, thumbH),
        cornerRadius = r
    )
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodScreen(
    type: String = "MOVIE",
    viewModel: com.cinex.player.ui.MainViewModel,
    title: String,
    channels: Flow<PagingData<Channel>>? = null,
    continueWatching: List<Channel> = emptyList(),
    onVideoClick: (Channel) -> Unit,
    onPlayDirect: (Channel) -> Unit = onVideoClick,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val categoriesFlow = when (type) {
        "MOVIE"  -> viewModel.movieCategories
        "SERIES" -> viewModel.seriesCategories
        else     -> flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val adultUnlocked by viewModel.adultUnlocked.collectAsState()
    var showParentalDialog by remember { mutableStateOf(false) }
    var pendingAdultCategoryId by remember { mutableStateOf<String?>(null) }

    val selectedMovieCategory  by viewModel.selectedMovieCategory.collectAsState()
    val selectedSeriesCategory by viewModel.selectedSeriesCategory.collectAsState()
    val selectedCategory = when (type) {
        "MOVIE"  -> selectedMovieCategory
        "SERIES" -> selectedSeriesCategory
        else     -> "Tudo"
    }

    val movieSortOrder  by viewModel.movieSortOrder.collectAsState()
    val seriesSortOrder by viewModel.seriesSortOrder.collectAsState()
    val currentSort = when (type) {
        "MOVIE"  -> movieSortOrder
        "SERIES" -> seriesSortOrder
        else     -> "RECENT"
    }

    val context = LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    // TV: usa cache em memória para troca instantânea de categoria
    val tvChannelsRaw by (when {
        isTv && channels == null && type == "MOVIE" -> viewModel.moviesByCategory
        isTv && channels == null && type == "SERIES" -> viewModel.seriesByCategory
        else -> kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }).collectAsState()

    // Aplica ordenação na lista em memória (instantâneo, <1ms para 500 itens)
    val tvChannels = remember(tvChannelsRaw, currentSort) {
        when (currentSort) {
            "AZ"     -> tvChannelsRaw.sortedBy { it.name.lowercase() }
            "ZA"     -> tvChannelsRaw.sortedByDescending { it.name.lowercase() }
            "RATING" -> tvChannelsRaw.sortedByDescending { it.tmdbRating ?: 0.0 }
            "RECENT" -> tvChannelsRaw.sortedWith(compareByDescending<Channel> { it.syncedAt }
                .thenByDescending { it.remoteId.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }
                .thenByDescending { it.orderIndex })
            else     -> tvChannelsRaw.sortedBy { it.orderIndex }
        }
    }

    val useTvMemory = isTv && channels == null

    // Paging: sempre coletado (regra do Compose), mas só usado no mobile
    val pagingFlow = remember(selectedCategory, type, currentSort) {
        channels ?: if (type == "MOVIE") viewModel.getPagedMoviesByCategory(selectedCategory)
        else viewModel.getPagedSeriesByCategory(selectedCategory)
    }
    val pagingItems = pagingFlow.collectAsLazyPagingItems()

    val itemCount = if (useTvMemory) tvChannels.size else pagingItems.itemCount

    val firstCategoryFocusRequester = remember { FocusRequester() }

    // Request focus on the first category when screen becomes visible on TV
    LaunchedEffect(isTv, isActive, categories) {
        if (isTv && isActive && categories.isNotEmpty()) {
            kotlinx.coroutines.delay(200)
            try { firstCategoryFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val isSearchWithNoResults = type == "SEARCH" && itemCount == 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (!isSearchWithNoResults) Modifier.background(CineX_BackgroundBlue) else Modifier)
    ) {
        if (!isSearchWithNoResults) {
            // Imagem de fundo com blur (mesma da tela de loading)
            Image(
                painter = painterResource(id = com.cinex.player.R.drawable.bg_loading),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(4.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.55f
            )
            // Overlay escuro para garantir legibilidade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CineX_BackgroundBlue.copy(alpha = 0.85f))
            )
        }

        // Gate de carregamento inicial — aguarda dados antes de exibir o grid
        var initialLoadComplete by remember { mutableStateOf(false) }
        if (!initialLoadComplete) {
            if (type == "SEARCH") {
                val refreshState = pagingItems.loadState.refresh
                if (refreshState is androidx.paging.LoadState.NotLoading) {
                    initialLoadComplete = true
                }
            } else if (categories.isNotEmpty() && itemCount > 0) {
                initialLoadComplete = true
            }
        }
        val isDataReady = initialLoadComplete

        if (!isDataReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val transition = rememberInfiniteTransition(label = "vodLoading")
                    val rotation by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000)),
                        label = "rotation"
                    )
                    Canvas(modifier = Modifier.size(40.dp)) {
                        drawArc(
                            color = Color(0xFFC62828),
                            startAngle = rotation,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (type == "MOVIE") "Carregando Filmes..." else "Carregando Séries...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (isDataReady) Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
        ) {
        if (type != "SEARCH") {
            // ── SIDEBAR DE CATEGORIAS ──────────────────────────────
            val sidebarScrollState = rememberScrollState()
            var selectedItemOffset by remember { mutableIntStateOf(0) }

            // Auto-scroll para a categoria selecionada
            LaunchedEffect(selectedCategory, selectedItemOffset) {
                if (selectedItemOffset > 0) {
                    val viewportHeight = sidebarScrollState.viewportSize
                    // Centraliza o item selecionado no viewport
                    val targetScroll = (selectedItemOffset - viewportHeight / 3).coerceAtLeast(0)
                    sidebarScrollState.animateScrollTo(targetScroll)
                }
            }

            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .focusProperties { left = FocusRequester.Cancel }
                    .padding(start = 16.dp, end = 8.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0DFFFFFF))
                    .gradientBorder(
                        brush = Brush.linearGradient(listOf(AccentRed, AccentGold)),
                        borderWidth = 1.5.dp,
                        cornerRadius = 16.dp
                    )
                    .verticalScrollbar(sidebarScrollState)
                    .verticalScroll(sidebarScrollState)
                    .padding(16.dp)
            ) {
                // Cabeçalho do sidebar
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )

                val counts    by viewModel.categoryCounts.collectAsState()
                val typeCounts by viewModel.typeCounts.collectAsState()
                val favCounts  by viewModel.favoriteCounts.collectAsState()

                // "Continue Assistindo" no topo da sidebar
                if (continueWatching.isNotEmpty()) {
                    val isContinueSelected = selectedCategory == "Continuar Assistindo"
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            if (isContinueSelected) {
                                selectedItemOffset = coords.positionInParent().y.toInt()
                            }
                        }
                    ) {
                        CategoryItem(
                            name = "Continue Assistindo",
                            count = continueWatching.size,
                            isSelected = isContinueSelected,
                            onClick = {
                                if (type == "MOVIE") viewModel.setMovieCategory("Continuar Assistindo")
                                else viewModel.setSeriesCategory("Continuar Assistindo")
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                categories.forEachIndexed { index, category ->
                    val isSelected = selectedCategory == category.id
                    val count = when (category.id) {
                        "Tudo"     -> typeCounts[type] ?: 0
                        "Favorito" -> favCounts[type] ?: 0
                        else       -> counts[category.id] ?: 0
                    }
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            if (isSelected) {
                                selectedItemOffset = coords.positionInParent().y.toInt()
                            }
                        }
                    ) {
                        CategoryItem(
                            name = category.name,
                            count = count,
                            isSelected = isSelected,
                            onClick = {
                                if (viewModel.isAdultCategory(category.name) && !adultUnlocked) {
                                    pendingAdultCategoryId = category.id
                                    showParentalDialog = true
                                } else {
                                    if (type == "MOVIE") viewModel.setMovieCategory(category.id)
                                    else viewModel.setSeriesCategory(category.id)
                                }
                            },
                            modifier = if (index == 0 && isTv) Modifier.focusRequester(firstCategoryFocusRequester) else Modifier
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ── GRID DE CONTEÚDO ──────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            // Chips de ordenação (ocultos em busca e Continuar Assistindo)
            if (type != "SEARCH" && selectedCategory != "Continuar Assistindo") {
                val sortOptions = listOf(
                    "RECENT" to "Recentes",
                    "AZ"     to "A-Z",
                    "ZA"     to "Z-A",
                    "RATING" to "Avaliacao"
                )
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    items(sortOptions.size) { idx ->
                        val (key, label) = sortOptions[idx]
                        val isSelected = currentSort == key
                        if (idx > 0) Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) AccentGold else Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    if (type == "MOVIE") viewModel.setMovieSortOrder(key)
                                    else viewModel.setSeriesSortOrder(key)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        Box(modifier = Modifier.weight(1f)) {
            val isReallyEmpty = if (useTvMemory) {
                tvChannels.isEmpty()
            } else {
                itemCount == 0 && pagingItems.loadState.refresh is androidx.paging.LoadState.NotLoading
            }

            if (isReallyEmpty && type != "SEARCH") {
                // Empty state (não para busca — deixa transparente para o grid aparecer por baixo)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val (icon, message) = when {
                            selectedCategory == "Favorito" -> Icons.Default.FavoriteBorder to "Nenhum favorito adicionado"
                            selectedCategory == "Continuar Assistindo" -> Icons.Default.VideoLibrary to "Nenhum conteúdo em andamento"
                            else -> Icons.Default.VideoLibrary to "Nenhum conteúdo nesta categoria"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = message,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LaunchedEffect(currentSort) {
                gridState.scrollToItem(0)
            }

            // Viewport-aware enrichment: só enriquece itens visíveis na tela
            // Evita OOM em TVs com 16k+ itens
            LaunchedEffect(gridState, tvChannels, useTvMemory) {
                if (!useTvMemory) return@LaunchedEffect

                snapshotFlow { gridState.layoutInfo }
                    .distinctUntilChanged()
                    .collect { layoutInfo ->
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) return@collect

                        // Enriquece itens visíveis
                        visibleItems.forEach { item ->
                            val index = item.index
                            if (index in tvChannels.indices) {
                                val channel = tvChannels[index]
                                // Enriquece apenas se ainda não tem dados TMDB
                                if (channel.posterUrl.isNullOrEmpty() && channel.tmdbSynopsis.isNullOrEmpty()) {
                                    viewModel.onChannelVisible(channel)
                                }
                            }
                        }

                        // Prefetch: enriquece próximos 8 itens (ainda não visíveis)
                        val lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: 0
                        val prefetchCount = 8
                        for (i in 1..prefetchCount) {
                            val prefetchIndex = lastVisibleIndex + i
                            if (prefetchIndex in tvChannels.indices) {
                                val channel = tvChannels[prefetchIndex]
                                if (channel.posterUrl.isNullOrEmpty() && channel.tmdbSynopsis.isNullOrEmpty()) {
                                    viewModel.onChannelVisible(channel)
                                }
                            }
                        }
                    }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 0.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (useTvMemory) {
                    // TV: usa lista em memória para troca instantânea
                    // REMOVIDO: LaunchedEffect por item (causava OOM)
                    // Novo: enrichment controlado pelo snapshotFlow acima
                    items(
                        count = tvChannels.size,
                        key = { tvChannels[it].id }
                    ) { index ->
                        val channel = tvChannels[index]
                        VodPosterItem(
                            channel = channel,
                            showProgress = selectedCategory == "Continuar Assistindo",
                            onClick = {
                                if (selectedCategory == "Continuar Assistindo") {
                                    onPlayDirect(channel)
                                } else {
                                    onVideoClick(channel)
                                }
                            }
                        )
                    }
                } else {
                    // Mobile: usa Paging
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.id }
                    ) { index ->
                        pagingItems[index]?.let { channel ->
                            // Não enriquece durante busca para evitar writes no DB
                            // que invalidariam o PagingSource e quebrariam o scroll
                            if (type != "SEARCH") {
                                LaunchedEffect(channel.id) {
                                    viewModel.onChannelVisible(channel)
                                }
                            }
                            VodPosterItem(
                                channel = channel,
                                showProgress = selectedCategory == "Continuar Assistindo",
                                onClick = {
                                    if (selectedCategory == "Continuar Assistindo") {
                                        onPlayDirect(channel)
                                    } else {
                                        onVideoClick(channel)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } // fecha Box interno do grid
        } // fecha Column do grid
        } // fecha Row

        // Dialog de controle parental
        if (showParentalDialog) {
            com.cinex.player.ui.components.ParentalPinDialog(
                onDismiss = {
                    showParentalDialog = false
                    pendingAdultCategoryId = null
                },
                onPinVerified = {
                    showParentalDialog = false
                    pendingAdultCategoryId?.let { categoryId ->
                        if (type == "MOVIE") viewModel.setMovieCategory(categoryId)
                        else viewModel.setSeriesCategory(categoryId)
                    }
                    pendingAdultCategoryId = null
                },
                verifyPin = { viewModel.verifyParentalPin(it) }
            )
        }
    } // fecha Box
}
