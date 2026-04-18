package com.cinex.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.flow.debounce
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import com.cinex.player.ui.components.TvVodGridAdapter
import com.cinex.player.ui.components.GridSpacingItemDecoration

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
    navDownTrigger: Int = 0,
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

    // Pré-carregamento de imagens: quando o foco pousa em uma categoria por ≥200ms,
    // enfileira até 8 posters no Coil antes do usuário confirmar a seleção.
    // Na TV, o limite de 3 requests simultâneos do Coil torna esse prefetch essencial
    // para que as capas apareçam rapidamente ao trocar de categoria.
    var prefocusedCategoryId by remember { mutableStateOf<String?>(null) }
    val coilImageLoader = if (isTv) remember { coil.Coil.imageLoader(context) } else null
    LaunchedEffect(prefocusedCategoryId) {
        val catId = prefocusedCategoryId ?: return@LaunchedEffect
        if (!isTv || coilImageLoader == null) return@LaunchedEffect
        kotlinx.coroutines.delay(200L)
        if (prefocusedCategoryId != catId) return@LaunchedEffect
        val imageUrls = viewModel.getFirstImageUrlsForCategory(type, catId)
        imageUrls.forEach { url ->
            coilImageLoader.enqueue(
                coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(200, 300)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .build()
            )
        }
    }

    // HABILITADO PARA MOBILE TAMBÉM: usa cache em memória para troca instantânea de categoria
    val tvChannelsRawPair by (when {
        channels == null && type == "MOVIE" -> viewModel.moviesByCategory
        channels == null && type == "SERIES" -> viewModel.seriesByCategory
        else -> kotlinx.coroutines.flow.MutableStateFlow("Tudo" to emptyList<com.cinex.player.data.model.Channel>())
    }).collectAsState()

    val loadedCategoryId = tvChannelsRawPair.first
    val tvChannelsRaw = tvChannelsRawPair.second

    // Aplica ordenação na lista em memória. As chaves de ordenação são pré-computadas
    // numa única passada O(n) para evitar regex/lowercase dentro do comparador —
    // crítico com catálogos grandes (16k+ itens) no thread de composição.
    val tvChannels = remember(tvChannelsRaw, currentSort) {
        if (tvChannelsRaw.isEmpty()) return@remember tvChannelsRaw
        val digitsOnly = Regex("[^0-9]")
        when (currentSort) {
            "AZ" -> {
                val keys = Array(tvChannelsRaw.size) { tvChannelsRaw[it].name.lowercase() }
                val indices = (0 until tvChannelsRaw.size).sortedBy { keys[it] }
                indices.map { tvChannelsRaw[it] }
            }
            "ZA" -> {
                val keys = Array(tvChannelsRaw.size) { tvChannelsRaw[it].name.lowercase() }
                val indices = (0 until tvChannelsRaw.size).sortedByDescending { keys[it] }
                indices.map { tvChannelsRaw[it] }
            }
            "RATING" -> tvChannelsRaw.sortedByDescending { it.tmdbRating ?: 0.0 }
            "RECENT" -> {
                val orderIndex = IntArray(tvChannelsRaw.size) { tvChannelsRaw[it].orderIndex }
                val indices = (0 until tvChannelsRaw.size).sortedBy { orderIndex[it] }
                indices.map { tvChannelsRaw[it] }
            }
            else -> tvChannelsRaw.sortedBy { it.orderIndex }
        }
    }

    val useTvMemory = channels == null

    // Paging 3: Agora só é ativado caso o caller passe um flow (ex: tela de Busca).
    // OTIMIZAÇÃO GLOBAL MÁXIMA: O app agora usa a engenharia de RAM do Compose 
    // com uma lista fixa no StateFlow tanto pra TV quanto para Mobile, garantindo
    // tempo de resposta de ~10ms em invés de utilizar a engine pesada do Paging 3
    // que apaga os dados a cada ping no banco de dados.
    // OTIMIZAÇÃO: A key inclui apenas o necessário para não recriar o Pager sem necessidade
    val pagingFlow = if (useTvMemory) {
        null
    } else {
        remember(selectedCategory, type, currentSort) {
            channels ?: if (type == "MOVIE") viewModel.getPagedMoviesByCategory(selectedCategory)
            else viewModel.getPagedSeriesByCategory(selectedCategory)
        }
    }
    val pagingItems = (pagingFlow ?: flowOf(PagingData.empty())).collectAsLazyPagingItems()

    val itemCount = if (useTvMemory) tvChannels.size else pagingItems.itemCount

    val firstCategoryFocusRequester = remember { FocusRequester() }
    val selectedCatFocusRequester   = remember { FocusRequester() }
    val firstGridItemFocusRequester = remember { FocusRequester() }
    val firstSortChipFocusRequester = remember { FocusRequester() }
    val areSortChipsVisible = type != "SEARCH" && selectedCategory != "Continuar Assistindo"
    val focusScope = rememberCoroutineScope()

    // CORREÇÃO: Scroll ao topo SOMENTE ao trocar de categoria ou ordenação
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val recyclerRef = remember { mutableStateOf<androidx.recyclerview.widget.RecyclerView?>(null) }
    LaunchedEffect(selectedCategory, currentSort) {
        if (isTv && useTvMemory) recyclerRef.value?.scrollToPosition(0)
        else gridState.scrollToItem(0)
    }

    fun requestGridFocus() {
        focusScope.launch {
            if (isTv && useTvMemory) {
                val rv = recyclerRef.value ?: return@launch
                for (delayMs in listOf(0L, 60L, 150L, 300L)) {
                    if (delayMs > 0L) delay(delayMs)
                    val v = rv.layoutManager?.findViewByPosition(0)
                    if (v != null) { v.requestFocus(); return@launch }
                }
            } else {
                for (delayMs in listOf(0L, 50L, 120L)) {
                    if (delayMs > 0L) delay(delayMs)
                    try { firstGridItemFocusRequester.requestFocus(); return@launch } catch (_: Exception) {}
                }
                if (gridState.firstVisibleItemIndex != 0) {
                    try { gridState.scrollToItem(0) } catch (_: Exception) {}
                }
                for (delayMs in listOf(80L, 160L, 260L)) {
                    delay(delayMs)
                    try { firstGridItemFocusRequester.requestFocus(); return@launch } catch (_: Exception) {}
                }
            }
        }
    }

    // Restaura foco na categoria selecionada ao entrar na tela ou ao trocar de categoria.
    // Igual ao padrão do LiveTvScreen: selectedCatFocusRequester segue a categoria ativa.
    LaunchedEffect(isTv, isActive, selectedCategory, categories) {
        if (isTv && isActive && categories.isNotEmpty()) {
            for (delayMs in listOf(100L, 200L, 350L)) {
                kotlinx.coroutines.delay(delayMs)
                try {
                    selectedCatFocusRequester.requestFocus()
                    break
                } catch (_: Exception) {}
            }
        }
    }

    // Ao pressionar DOWN na TopNavigationBar, desce o foco para as categorias (ou grid se sem categorias)
    LaunchedEffect(navDownTrigger) {
        if (navDownTrigger > 0 && isTv && isActive) {
            kotlinx.coroutines.delay(50L)
            try {
                if (categories.isNotEmpty()) firstCategoryFocusRequester.requestFocus()
                else requestGridFocus()
            } catch (_: Exception) {}
        }
    }

    // Foco ao voltar do overlay de detalhes: observa a transição não-nulo → nulo e
    // força foco no primeiro item do grid (onde o usuário estava navegando antes).
    // Observar diretamente o StateFlow é mais confiável do que depender de isActive.
    val detailsChannel by viewModel.selectedChannelForDetails.collectAsState()
    var prevDetailsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(detailsChannel) {
        val nowOpen = detailsChannel != null
        val wasOpen = prevDetailsOpen
        prevDetailsOpen = nowOpen
        if (isTv && wasOpen && !nowOpen && isActive && itemCount > 0) {
            if (useTvMemory) {
                recyclerRef.value?.scrollToPosition(0)
                for (delayMs in listOf(100L, 200L, 400L)) {
                    kotlinx.coroutines.delay(delayMs)
                    val v = recyclerRef.value?.layoutManager?.findViewByPosition(0)
                    if (v != null) { v.requestFocus(); break }
                }
            } else {
                gridState.scrollToItem(0)
                for (delayMs in listOf(100L, 200L, 400L)) {
                    kotlinx.coroutines.delay(delayMs)
                    try { firstGridItemFocusRequester.requestFocus(); break } catch (_: Exception) {}
                }
            }
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isTv) Modifier.blur(4.dp) else Modifier),
                contentScale = ContentScale.Crop,
                alpha = if (isTv) 0.55f else 0.18f
            )
            // Overlay escuro para garantir legibilidade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CineX_BackgroundBlue.copy(alpha = 0.85f))
            )
        }

        // Gate de carregamento inicial — aguarda dados antes de exibir o grid
        val isMoviesReady by viewModel.isMoviesReady.collectAsState()
        val isSeriesReady by viewModel.isSeriesReady.collectAsState()
        val isContentReady = when {
            type == "SEARCH" -> true
            useTvMemory && type == "MOVIE" -> isMoviesReady
            useTvMemory && type == "SERIES" -> isSeriesReady
            else -> true
        }

        var initialLoadComplete by remember { mutableStateOf(false) }
        if (!initialLoadComplete) {
            if (type == "SEARCH") {
                val refreshState = pagingItems.loadState.refresh
                if (refreshState is androidx.paging.LoadState.NotLoading) {
                    initialLoadComplete = true
                }
            } else if (categories.isNotEmpty() && isContentReady) {
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
                if (isTv && selectedItemOffset > 0) {
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
                    .then(
                        if (isTv) Modifier.onPreviewKeyEvent { event ->
                            // Garante que LEFT nunca escapa do container de categorias na TV
                            event.key == Key.DirectionLeft
                        } else Modifier
                    )
                    .padding(start = 16.dp, end = 8.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0DFFFFFF))
                    .gradientBorder(
                        brush = Brush.linearGradient(listOf(AccentRed, AccentGold)),
                        borderWidth = 1.5.dp,
                        cornerRadius = 16.dp
                    )
                    .padding(vertical = 16.dp)
                    .verticalScrollbar(sidebarScrollState)
                    .verticalScroll(sidebarScrollState)
                    .padding(horizontal = 16.dp)
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
                        modifier = if (isTv) {
                            Modifier.onGloballyPositioned { coords ->
                                if (isContinueSelected) {
                                    selectedItemOffset = coords.positionInParent().y.toInt()
                                }
                            }
                        } else Modifier
                    ) {
                        CategoryItem(
                            name = "Continue Assistindo",
                            count = continueWatching.size,
                            isSelected = isContinueSelected,
                            onClick = {
                                if (type == "MOVIE") viewModel.setMovieCategory("Continuar Assistindo")
                                else viewModel.setSeriesCategory("Continuar Assistindo")
                            },
                            modifier = if (isTv) {
                                Modifier
                                    .then(if (isContinueSelected) Modifier.focusRequester(selectedCatFocusRequester) else Modifier)
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                            requestGridFocus()
                                            true
                                        } else false
                                    }
                                    .onFocusChanged { if (it.isFocused) prefocusedCategoryId = "Continuar Assistindo" }
                            } else Modifier
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
                        modifier = if (isTv) {
                            Modifier.onGloballyPositioned { coords ->
                                if (isSelected) {
                                    selectedItemOffset = coords.positionInParent().y.toInt()
                                }
                            }
                        } else Modifier
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
                            modifier = when {
                                isTv -> Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstCategoryFocusRequester) else Modifier)
                                    .then(if (isSelected) Modifier.focusRequester(selectedCatFocusRequester) else Modifier)
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                            requestGridFocus()
                                            true
                                        } else false
                                    }
                                    .onFocusChanged { if (it.isFocused) prefocusedCategoryId = category.id }
                                else -> Modifier
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ── GRID DE CONTEÚDO ──────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            // Chips de ordenação (ocultos em busca e Continuar Assistindo)
            if (areSortChipsVisible) {
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
                        val (sortKey, label) = sortOptions[idx]
                        val isSelected = currentSort == sortKey
                        var isChipFocused by remember { mutableStateOf(false) }
                        if (idx > 0) Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .then(
                                    if (isTv) {
                                        Modifier
                                            .then(if (idx == 0) Modifier.focusRequester(firstSortChipFocusRequester) else Modifier)
                                            .onKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                                    requestGridFocus()
                                                    true
                                                } else false
                                            }
                                            .onFocusChanged { isChipFocused = it.isFocused }
                                            .focusable(interactionSource = remember { MutableInteractionSource() })
                                            .onKeyEvent { event ->
                                                // OTIMIZADO: KeyDown ao invés de KeyUp para resposta instantânea
                                                if (event.type == KeyEventType.KeyDown &&
                                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                                ) {
                                                    if (type == "MOVIE") viewModel.setMovieSortOrder(sortKey)
                                                    else viewModel.setSeriesSortOrder(sortKey)
                                                    true
                                                } else false
                                            }
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .then(
                                    if (isChipFocused) {
                                        Modifier.border(2.dp, Brush.linearGradient(listOf(AccentRed, AccentGold)), RoundedCornerShape(20.dp))
                                    } else Modifier
                                )
                                .background(
                                    when {
                                        isSelected -> AccentGold
                                        isChipFocused -> Color.White.copy(alpha = 0.12f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    }
                                )
                                .clickable {
                                    if (type == "MOVIE") viewModel.setMovieSortOrder(sortKey)
                                    else viewModel.setSeriesSortOrder(sortKey)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = when {
                                    isSelected -> Color.Black
                                    isChipFocused -> Color.White
                                    else -> Color.White.copy(alpha = 0.7f)
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        // focusedGridChannel é atualizado pelo VodPosterItem.onFocused → usado para detectar coluna 0
        var focusedGridChannel by remember(type, selectedCategory) { mutableStateOf<Channel?>(null) }

        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    // Compose LEFT-intercept só para TV+search (RecyclerView cuida do useTvMemory)
                    if (isTv && !useTvMemory) Modifier.onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionLeft) {
                            val focusedIndex = tvChannels.indexOfFirst { it.id == focusedGridChannel?.id }
                            val isLeftmostColumn = focusedIndex >= 0 && focusedIndex % 4 == 0
                            if (isLeftmostColumn) {
                                if (event.type == KeyEventType.KeyDown)
                                    try { selectedCatFocusRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else false
                        } else false
                    } else Modifier
                )
        ) {
            var settledGridChannel by remember(type, selectedCategory) { mutableStateOf<Channel?>(null) }

            LaunchedEffect(focusedGridChannel?.id, isTv, useTvMemory) {
                if (!isTv || !useTvMemory) return@LaunchedEffect
                val channel = focusedGridChannel ?: return@LaunchedEffect
                // OTIMIZADO: delay reduzido de 220ms para 120ms para resposta mais rápida
                delay(120)  // OTIMIZADO: 220ms → 120ms
                if (focusedGridChannel?.id == channel.id) {
                    settledGridChannel = channel
                }
            }

            LaunchedEffect(settledGridChannel?.id, isTv, useTvMemory) {
                if (!isTv || !useTvMemory) return@LaunchedEffect
                settledGridChannel?.let { channel ->
                    if (channel.posterUrl.isNullOrEmpty() && channel.tmdbSynopsis.isNullOrEmpty()) {
                        viewModel.onChannelVisible(channel)
                    }
                }
            }

            // Paging 3 loading state (Apenas para mobile)
            val isLoadingPaging = !useTvMemory && pagingItems.loadState.refresh is androidx.paging.LoadState.Loading

            val isReallyEmpty = if (useTvMemory) {
                // Na TV, a transição entre as categorias nativas no SQLite é praticamente garantida quase instantaneamente (50ms).
                // Ao invés de colocar uma segunda tela de load visual por 50ms, esperamos para validar se realmente está vazio
                tvChannels.isEmpty() && selectedCategory == loadedCategoryId
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

            if (isTv && useTvMemory) {
                // ── TV: RecyclerView nativo (GridLayoutManager) ──────────
                // Evita o overhead do Compose por item — igual ao IBO Player
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val spacing = (8 * ctx.resources.displayMetrics.density).toInt()
                        val hPad   = (12 * ctx.resources.displayMetrics.density).toInt()
                        val bPad   = (32 * ctx.resources.displayMetrics.density).toInt()
                        androidx.recyclerview.widget.RecyclerView(ctx).apply {
                            layoutManager = GridLayoutManager(ctx, 4).apply {
                                recycleChildrenOnDetach = true
                            }
                            adapter = TvVodGridAdapter()
                            addItemDecoration(GridSpacingItemDecoration(4, spacing))
                            setPadding(hPad, 0, hPad, bPad)
                            clipToPadding = false
                            setHasFixedSize(false)
                            recyclerRef.value = this
                        }
                    },
                    update = { rv ->
                        val adapter = rv.adapter as? TvVodGridAdapter ?: return@AndroidView
                        adapter.onItemClick = { channel ->
                            if (selectedCategory == "Continuar Assistindo") onPlayDirect(channel)
                            else onVideoClick(channel)
                        }
                        adapter.onNavigateLeft = {
                            try { selectedCatFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        adapter.onNavigateUp = {
                            try { firstSortChipFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        adapter.showProgress = selectedCategory == "Continuar Assistindo"
                        adapter.areSortChipsVisible = areSortChipsVisible
                        adapter.items = tvChannels
                    }
                )
            } else {
                // ── Mobile / Search: LazyVerticalGrid Compose ────────────
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
                        items(
                            count = tvChannels.size,
                            key = { index -> "vod_${tvChannels[index].id}" },
                            contentType = { "vod_poster" }
                        ) { index ->
                            val channel = tvChannels[index]
                            VodPosterItem(
                                channel = channel,
                                modifier = Modifier,
                                showProgress = selectedCategory == "Continuar Assistindo",
                                onFocused = { focusedGridChannel = it },
                                onClick = {
                                    if (selectedCategory == "Continuar Assistindo") onPlayDirect(channel)
                                    else onVideoClick(channel)
                                }
                            )
                        }
                    } else {
                        items(
                            count = pagingItems.itemCount,
                            key = pagingItems.itemKey { it.id },
                            contentType = { "vod_poster" }
                        ) { index ->
                            pagingItems[index]?.let { channel ->
                                VodPosterItem(
                                    channel = channel,
                                    modifier = Modifier,
                                    showProgress = selectedCategory == "Continuar Assistindo",
                                    onClick = {
                                        if (selectedCategory == "Continuar Assistindo") onPlayDirect(channel)
                                        else onVideoClick(channel)
                                    }
                                )
                            }
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
