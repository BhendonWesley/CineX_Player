package com.cinex.player.ui.screens

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.util.Base64
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.focusGroup
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.components.CategoryItem
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import coil.compose.AsyncImage

private val LiveGold = Color(0xFFD8A63A)

private fun Modifier.lazyScrollbar(
    listState: LazyListState,
    trackColor: Color = Color.White.copy(alpha = 0.08f),
    thumbColor: Color = LiveGold,
    width: Dp = 3.dp,
    minThumbHeight: Dp = 32.dp
): Modifier = drawWithContent {
    drawContent()
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) return@drawWithContent
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return@drawWithContent

    val viewportH = size.height
    val firstVisible = listState.firstVisibleItemIndex
    val firstOffset = listState.firstVisibleItemScrollOffset
    // Usa apenas itens totalmente dentro do viewport para evitar flutuação da altura
    // ao incluir itens parcialmente visíveis nas bordas (causava salto no polegar).
    val fullItems = visibleItems.filter { it.offset >= 0 && (it.offset + it.size) <= viewportH.toInt() }
    val refItems = if (fullItems.size >= 2) fullItems else visibleItems
    val itemH = refItems.sumOf { it.size }.toFloat() / refItems.size
    val totalContentH = itemH * totalItems
    if (totalContentH <= viewportH) return@drawWithContent

    val scrolled = firstVisible * itemH + firstOffset
    val maxScroll = totalContentH - viewportH
    val fraction = (scrolled / maxScroll).coerceIn(0f, 1f)

    val thumbH = (viewportH / totalContentH * viewportH).coerceAtLeast(minThumbHeight.toPx())
    val thumbTop = fraction * (viewportH - thumbH)
    val trackW = width.toPx()
    val x = size.width - trackW - 2.dp.toPx()
    val r = CornerRadius(trackW / 2f)

    drawRoundRect(color = trackColor, topLeft = Offset(x, 0f), size = Size(trackW, viewportH), cornerRadius = r)
    drawRoundRect(color = thumbColor, topLeft = Offset(x, thumbTop), size = Size(trackW, thumbH), cornerRadius = r)
}

@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    viewModel: MainViewModel,
    onChannelExpand: (Channel) -> Unit, // reserved for future use
    isActive: Boolean = false,
    navDownTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    // Detecta se está rodando em Android TV
    val context = LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val previewPlayerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    val surfaceRefresh by viewModel.liveTvSurfaceRefresh.collectAsState()

    // Quando sai do fullscreen, força o preview a reclamar a surface do player
    LaunchedEffect(surfaceRefresh) {
        if (surfaceRefresh > 0) {
            previewPlayerViewRef.value?.let { view ->
                view.player = null
                view.player = viewModel.liveTvPlayer
            }
        }
    }

    val categories by viewModel.liveCategories.collectAsState(initial = emptyList())
    val selectedCategory by viewModel.liveCategoryId.collectAsState()
    val selectedCatFocusRequester = remember { FocusRequester() }
    val channelFocusRequester = remember { FocusRequester() }
    // Foco de entrada via nav bar (DOWN): vai ao primeiro canal VISÍVEL, não ao selecionado
    val navEntryChannelFocusRequester = remember { FocusRequester() }
    var navEntryChannelIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val favFocusRequester = remember { FocusRequester() }

    // Na TV, restaura foco na categoria ao entrar na tela ou ao trocar de categoria.
    // Retry com backoff: o Compose pode ainda estar reorganizando a árvore ao voltar.
    LaunchedEffect(isTv, isActive, categories, selectedCategory) {
        if (isTv && isActive && categories.isNotEmpty()) {
            for (delayMs in listOf(100L, 200L, 350L)) {
                delay(delayMs)
                try {
                    selectedCatFocusRequester.requestFocus()
                    break
                } catch (_: Exception) {}
            }
        }
    }

    // Rastreia se o último foco estava nos canais (true) ou nas categorias (false)
    var lastFocusWasInChannels by remember { mutableStateOf(false) }
    // Trigger para navegar direto para os canais via navDownTrigger (quando lastFocusWasInChannels=true)
    var navToChannelsTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Ao pressionar DOWN na TopNavigationBar, volta para onde o usuário estava antes de subir
    LaunchedEffect(navDownTrigger) {
        if (navDownTrigger > 0 && isTv && isActive && categories.isNotEmpty()) {
            delay(50L)
            if (lastFocusWasInChannels) {
                navToChannelsTrigger++
            } else {
                try { selectedCatFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
    val adultUnlocked by viewModel.adultUnlocked.collectAsState()
    var showParentalDialog by remember { mutableStateOf(false) }
    var pendingAdultCategoryId by remember { mutableStateOf<String?>(null) }
    
    // Na TV: usa cache em memória (filtragem instantânea)
    // TV + Mobile: lista in-memory — troca de categoria instantânea sem Paging overhead
    val liveChannels by viewModel.liveTvChannels.collectAsState()

    // Canal selecionado vive no ViewModel para persistir entre fullscreen ↔ preview
    val selectedChannel by viewModel.selectedLiveChannel.collectAsState()

    val currentProgram by viewModel.currentProgram.collectAsState()
    val upcomingPrograms by viewModel.upcomingPrograms.collectAsState()
    val epgListings by viewModel.epgListings.collectAsState() // Fallback Xtream
    val is24Hour by viewModel.is24HourFormat.collectAsState()

    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val typeCounts by viewModel.typeCounts.collectAsState()
    val favoriteCounts by viewModel.favoriteCounts.collectAsState()

    val channelCount = liveChannels.size
    val preferredTurboCategory = remember(categories) {
        categories.firstOrNull { it.name.equals("CineX Turbo", ignoreCase = true) }
    }

    LaunchedEffect(isActive, categories, selectedCategory) {
        val turboCategoryId = preferredTurboCategory?.id
        if (
            isActive &&
            turboCategoryId != null &&
            selectedCategory != turboCategoryId &&
            (selectedCategory == "Tudo" || categories.none { it.id == selectedCategory })
        ) {
            viewModel.setLiveCategory(turboCategoryId)
        }
    }

    // Auto-seleciona o primeiro canal apenas na abertura inicial (nenhum canal selecionado ainda)
    // Troca de categoria NÃO deve mudar o canal — o usuário clica explicitamente no canal desejado
    LaunchedEffect(channelCount, isActive, selectedCategory) {
        // Já há um canal selecionado — nunca sobrescreve automaticamente
        if (selectedChannel != null) return@LaunchedEffect
        if (!isActive || channelCount == 0) return@LaunchedEffect

        // Se existe categoria preferida (CineX Turbo), aguarda ela ser ativada antes de auto-selecionar
        // Evita pegar o primeiro canal de "Tudo" (ex: BBB) antes da categoria correta carregar
        val turboCategoryId = preferredTurboCategory?.id
        if (turboCategoryId != null && selectedCategory != turboCategoryId) return@LaunchedEffect

        liveChannels.firstOrNull()?.let { viewModel.updateSelectedChannel(it) }
    }

    // Toca o canal SOMENTE quando a aba Live TV está ativa
    // Usa o ID do canal como key para não re-triggerar ao mudar favorito (que altera o objeto mas não o canal)
    LaunchedEffect(selectedChannel?.id, isActive) {
        if (isActive) {
            selectedChannel?.let { channel ->
                if (channel.streamUrl.isNotEmpty()) {
                    viewModel.playLiveChannel(channel)
                }
            }
        }
    }

    // Para o player IMEDIATAMENTE quando sai da aba Live TV
    LaunchedEffect(isActive) {
        if (!isActive) {
            viewModel.stopLiveTv()
        }
    }

    // Gate de carregamento inicial — só aparece uma vez até os dados carregarem pela primeira vez
    var initialLoadComplete by remember { mutableStateOf(false) }
    if (!initialLoadComplete && categories.isNotEmpty() && channelCount > 0) {
        initialLoadComplete = true
    }
    val isDataReady = initialLoadComplete

    Box(modifier = modifier.fillMaxSize()) {
        // Imagem de fundo com blur
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(4.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.55f
        )
        // Overlay escuro
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB101010))
        )

        if (!isDataReady) {
            // Loading centralizado enquanto categorias e canais carregam
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val transition = rememberInfiniteTransition(label = "screenLoading")
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
                        text = "Carregando Canais...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Conteúdo por cima do fundo — só visível quando dados estão prontos
    if (isDataReady) {
        Row(modifier = Modifier.fillMaxSize()) {
        // Trigger para navegar para a coluna de canais com retry robusto
        var enterChannelsTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        // Trigger para voltar às categorias com retry robusto (LEFT nos canais)
        var returnToCatTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }

        // 1. Coluna de Categorias
        val catListState = rememberLazyListState()

        // Scroll inicial: só posiciona na categoria selecionada ao entrar na tela pela primeira vez
        // (não faz scroll a cada mudança de categoria — o LazyColumn mantém o foco visível nativamente)
        LaunchedEffect(Unit) {
            val index = categories.indexOfFirst { it.id == selectedCategory }
            if (index >= 0) catListState.scrollToItem(index)
        }

        // Ao pressionar LEFT nos canais: só rola se a categoria selecionada estiver fora da viewport
        LaunchedEffect(returnToCatTrigger) {
            if (returnToCatTrigger > 0) {
                val index = categories.indexOfFirst { it.id == selectedCategory }
                if (index >= 0) {
                    val visibleIndices = catListState.layoutInfo.visibleItemsInfo.map { it.index }
                    if (index !in visibleIndices) catListState.scrollToItem(index)
                }
                for (delayMs in listOf(50L, 100L, 200L)) {
                    delay(delayMs)
                    try { selectedCatFocusRequester.requestFocus(); break } catch (_: Exception) {}
                }
            }
        }

        LazyColumn(
            state = catListState,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xCC141414))
                .lazyScrollbar(catListState)
                .padding(8.dp)
                .then(
                    if (isTv) Modifier.onPreviewKeyEvent { event ->
                        when (event.key) {
                            // LEFT: não tem para onde ir — consome e pronto
                            Key.DirectionLeft -> true
                            // RIGHT: dispara trigger com retry em vez de requestFocus() direto
                            // (evita falha quando channelFocusRequester ainda está desanexado durante recomposição)
                            Key.DirectionRight -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    enterChannelsTrigger++
                                    lastFocusWasInChannels = true
                                }
                                true
                            }
                            else -> false
                        }
                    } else Modifier
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(categories) { index, category ->
                val countByCat = when (category.id) {
                    "Tudo" -> typeCounts["LIVE_TV"] ?: 0
                    "Favorito" -> favoriteCounts["LIVE_TV"] ?: 0
                    else -> categoryCounts[category.id] ?: 0
                }

                CategoryItem(
                    name = category.name,
                    count = countByCat,
                    isSelected = selectedCategory == category.id,
                    onClick = {
                        if (viewModel.isAdultCategory(category.name) && !adultUnlocked) {
                            pendingAdultCategoryId = category.id
                            showParentalDialog = true
                        } else {
                            viewModel.setLiveCategory(category.id)
                        }
                    },
                    modifier = Modifier
                        .then(
                            if (isTv && category.id == selectedCategory)
                                Modifier.focusRequester(selectedCatFocusRequester)
                            else Modifier
                        )
                        .then(
                            if (isTv) Modifier.focusProperties { right = channelFocusRequester }
                            else Modifier
                        )
                )
            }
        }
        
        
        // 2. Coluna de Canais
        Box(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(Color(0xCC1A1A1A))
        ) {
            if (channelCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val transition = rememberInfiniteTransition(label = "channelLoading")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000)),
                            label = "rotation"
                        )
                        Canvas(modifier = Modifier.size(28.dp)) {
                            drawArc(
                                color = Color(0xFFC62828),
                                startAngle = rotation,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (selectedCategory == "Favorito") "Nenhum favorito" else "Carregando canais...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            val channelListState = rememberLazyListState()
            val selectedChannelScrollIndex = remember(liveChannels, selectedChannel) {
                liveChannels.indexOfFirst { it.id == selectedChannel?.id }.takeIf { it >= 0 } ?: 0
            }

            // Quando o usuário pressiona RIGHT nas categorias, foca o canal selecionado.
            // Só rola se o canal estiver fora da viewport — evita snap ao topo quando já está visível.
            LaunchedEffect(enterChannelsTrigger) {
                if (enterChannelsTrigger > 0) {
                    val visibleIndices = channelListState.layoutInfo.visibleItemsInfo.map { it.index }
                    if (selectedChannelScrollIndex !in visibleIndices) {
                        channelListState.scrollToItem(selectedChannelScrollIndex)
                    }
                    for (delayMs in listOf(50L, 100L, 200L)) {
                        delay(delayMs)
                        try { channelFocusRequester.requestFocus(); break } catch (_: Exception) {}
                    }
                }
            }

            // Ao pressionar DOWN na nav bar: foca o primeiro canal VISÍVEL no viewport (topo da lista),
            // sem rolar — o usuário vê exatamente o que está na tela e navega de cima para baixo.
            LaunchedEffect(navToChannelsTrigger) {
                if (navToChannelsTrigger > 0) {
                    navEntryChannelIndex = channelListState.firstVisibleItemIndex
                    for (delayMs in listOf(50L, 100L, 200L)) {
                        delay(delayMs)
                        try { navEntryChannelFocusRequester.requestFocus(); break } catch (_: Exception) {}
                    }
                }
            }

            LazyColumn(
                state = channelListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyScrollbar(channelListState, thumbColor = Color(0xFFC62828))
                    .padding(end = 8.dp)
                    .then(
                        if (isTv) Modifier.onPreviewKeyEvent { event ->
                            // LEFT: vai para categorias via trigger (robusto mesmo se item estiver fora da viewport)
                            if (event.key == Key.DirectionLeft) {
                                if (event.type == KeyEventType.KeyDown) {
                                    returnToCatTrigger++
                                    lastFocusWasInChannels = false
                                }
                                true
                            } else false
                        } else Modifier
                    )
            ) {
                if (isTv) {
                    // TV: lista em memória — troca de categoria instantânea
                    itemsIndexed(liveChannels, key = { _, ch -> ch.id }) { index, channel ->
                        val isSelected = selectedChannel?.id == channel.id
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == selectedChannelScrollIndex) Modifier.focusRequester(channelFocusRequester)
                                    else Modifier
                                )
                                .then(
                                    if (index == navEntryChannelIndex) Modifier.focusRequester(navEntryChannelFocusRequester)
                                    else Modifier
                                )
                                .focusProperties {
                                    left = selectedCatFocusRequester
                                    right = favFocusRequester
                                }
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                }
                                .then(
                                    if (isFocused) Modifier.border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .focusable(interactionSource = remember { MutableInteractionSource() })
                                .onKeyEvent { event ->
                                    when {
                                        // LEFT: fallback caso onPreviewKeyEvent não tenha consumido
                                        event.key == Key.DirectionLeft -> {
                                            if (event.type == KeyEventType.KeyDown) returnToCatTrigger++
                                            true
                                        }
                                        event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                            if (isSelected) onChannelExpand(channel)
                                            else viewModel.updateSelectedChannel(channel)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .background(
                                    if (isSelected) Color(0xFFC62828).copy(alpha = 0.2f)
                                    else if (isFocused) Color.White.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (isSelected) onChannelExpand(channel)
                                    else viewModel.updateSelectedChannel(channel)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            ChannelListRow(index = index, channel = channel, isSelected = isSelected)
                        }
                    }
                } else {
                    // Mobile: usa liveChannels in-memory (mesmo que TV — troca de categoria instantânea)
                    itemsIndexed(liveChannels, key = { _, ch -> ch.id }) { index, channel ->
                        val isSelected = selectedChannel?.id == channel.id
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .then(
                                    if (isFocused) Modifier.border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .focusable(interactionSource = remember { MutableInteractionSource() })
                                .background(
                                    if (isSelected) Color(0xFFC62828).copy(alpha = 0.2f)
                                    else if (isFocused) Color.White.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (isSelected) onChannelExpand(channel)
                                    else viewModel.updateSelectedChannel(channel)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            ChannelListRow(index = index, channel = channel, isSelected = isSelected)
                        }
                    }
                }
            }
        }

        // 3. Coluna de Conteúdo (Player + EPG + Botões)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xBB0E0E0E))
        ) {
            // Player (proporção fixa, sem consumir toda a altura)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(Color.Black)
                    .then(
                        if (!isTv) Modifier
                            .onFocusChanged { if (it.isFocused) try { favFocusRequester.requestFocus() } catch (_: Exception) {} }
                            .focusable(interactionSource = remember { MutableInteractionSource() })
                            .clickable { selectedChannel?.let { onChannelExpand(it) } }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selectedChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            val layoutRes = if (isTv) com.cinex.player.R.layout.player_view_surface
                                else com.cinex.player.R.layout.player_view_texture
                            (LayoutInflater.from(ctx).inflate(
                                layoutRes, null
                            ) as PlayerView).apply {
                                player = viewModel.liveTvPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                previewPlayerViewRef.value = this
                            }
                        },
                        update = { playerView ->
                            // Re-anexa o player ao preview quando volta do fullscreen
                            if (playerView.player !== viewModel.liveTvPlayer) {
                                playerView.player = viewModel.liveTvPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay de erro quando o canal falha após todas as tentativas
                val liveError by viewModel.livePlayerError.collectAsState()
                if (liveError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable { viewModel.retryLiveChannel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "CANAL INDISPONÍVEL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Toque para tentar novamente",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Spinner de buffering estilo Netflix
                val isBuffering by viewModel.isLiveBuffering.collectAsState()
                if (isBuffering && !liveError) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val transition = rememberInfiniteTransition(label = "buffering")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000)
                            ),
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
                    }
                }

                // Botão favorito no canto superior direito do preview
                var isFavLocal by remember(selectedChannel?.id) {
                    mutableStateOf(selectedChannel?.isFavorite == true)
                }
                var isFavFocused by remember { mutableStateOf(false) }
                var restoreFavFocusTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                LaunchedEffect(restoreFavFocusTrigger) {
                    if (restoreFavFocusTrigger > 0) {
                        for (delayMs in listOf(50L, 100L, 200L)) {
                            delay(delayMs)
                            try { favFocusRequester.requestFocus(); break } catch (_: Exception) {}
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(if (isFavFocused) 36.dp else 28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = if (isFavFocused) 0.8f else 0.5f))
                        .then(
                            if (isFavFocused) Modifier.border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(50))
                            else Modifier
                        )
                        .focusRequester(favFocusRequester)
                        .focusProperties { left = channelFocusRequester }
                        .onFocusChanged { isFavFocused = it.isFocused }
                        .focusable(interactionSource = remember { MutableInteractionSource() })
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) {
                                selectedChannel?.let {
                                    isFavLocal = !isFavLocal
                                    viewModel.updateFavorite(it.id, isFavLocal)
                                    restoreFavFocusTrigger++
                                }
                                true
                            } else false
                        }
                        .clickable {
                            selectedChannel?.let {
                                isFavLocal = !isFavLocal
                                viewModel.updateFavorite(it.id, isFavLocal)
                                restoreFavFocusTrigger++
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavLocal) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavLocal) "Remover favorito" else "Adicionar favorito",
                        tint = if (isFavLocal) Color(0xFFC62828) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // EPG + Título (ocupa o espaço restante)
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Título do Canal
                Text(
                    text = (selectedChannel?.name ?: "SELECIONE UM CANAL").uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )

                val hasEpg = (currentProgram != null) || epgListings.isNotEmpty() || upcomingPrograms.isNotEmpty()

                // EPG ou MODO 24H
                if (selectedChannel != null && !hasEpg) {
                    // Fallback - Canal sem EPG
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MODO 24H",
                            color = Color(0xFFFFD700),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFFFFD700).copy(alpha = 0.8f))
                        )
                        Text(
                            text = "Este canal não fornece guia de programação ou transmite conteúdo contínuo 24 horas por dia.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                } else if (hasEpg) {
                    val epgListState = rememberLazyListState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // CABEÇALHO TRAVADO (Programa Atual) - Não recua com o scroll
                        if (currentProgram != null) {
                            var isFocusedEpg by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .then(
                                        if (isTv) Modifier
                                            .onFocusChanged { isFocusedEpg = it.isFocused }
                                            .focusable(interactionSource = remember { MutableInteractionSource() })
                                            .onKeyEvent { event ->
                                                // Bloqueia UP no item atual para não escapar pro player
                                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
                                            }
                                            .background(
                                                if (isFocusedEpg) Color.White.copy(alpha = 0.08f)
                                                else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                        else Modifier
                                    )
                            ) {
                                EpgItem(program = currentProgram!!, isCurrent = true, is24Hour = is24Hour)
                                val total = (currentProgram!!.endTime - currentProgram!!.startTime).coerceAtLeast(1)
                                val passed = (System.currentTimeMillis() - currentProgram!!.startTime).coerceIn(0, total)
                                val progress = passed.toFloat() / total.toFloat()
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .background(Color(0xFFFFD700))
                                    )
                                }
                            }
                        } else if (epgListings.isNotEmpty()) {
                            val first = epgListings[0]
                            val title = first.title.decodeBase64IfNeeded()
                            val timeRange = formatEpgTime(first, is24Hour)
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Text(
                                    text = timeRange,
                                    color = Color(0xFFFFD700),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFFFD700).copy(alpha = 0.4f)))
                            }
                        }

                        // PRÓXIMOS PROGRAMAS (Lista Rolável)
                        LazyColumn(
                            state = epgListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            if (upcomingPrograms.isNotEmpty()) {
                                items(upcomingPrograms.take(10)) { program ->
                                var isFocusedEpg by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isTv) Modifier
                                                .onFocusChanged { isFocusedEpg = it.isFocused }
                                                .focusable(interactionSource = remember { MutableInteractionSource() })
                                                .background(
                                                    if (isFocusedEpg) Color.White.copy(alpha = 0.08f)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                            else Modifier
                                        )
                                ) {
                                    EpgItem(program = program, isCurrent = false, is24Hour = is24Hour)
                                }
                            }
                        } else if (epgListings.size > 1) {
                            items(epgListings.drop(1).take(10)) { epg ->
                                val title = epg.title.decodeBase64IfNeeded()
                                val timeRange = formatEpgTime(epg, is24Hour)
                                var isFocusedEpg by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .then(
                                            if (isTv) Modifier
                                                .onFocusChanged { isFocusedEpg = it.isFocused }
                                                .focusable(interactionSource = remember { MutableInteractionSource() })
                                                .background(
                                                    if (isFocusedEpg) Color.White.copy(alpha = 0.08f)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                            else Modifier
                                        )
                                ) {
                                    Text(
                                        text = timeRange,
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = title,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } // Fecha LazyColumn
                } // Fecha Column (Wrapper do EPG)
            } // Fecha box ou row do LiveTvScreen
        } // ...
    } // ...
    } // fim Row + isDataReady
    }

    // Fullscreen agora é via VideoPlayerScreen (MainScreen.playingChannel)

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
                    viewModel.setLiveCategory(categoryId)
                }
                pendingAdultCategoryId = null
            },
            verifyPin = { viewModel.verifyParentalPin(it) }
        )
    }
    } // fim Box
}

@Composable
fun EpgItem(program: com.cinex.player.data.model.EpgProgram, isCurrent: Boolean, is24Hour: Boolean = true) {
    val start = com.cinex.player.utils.EpgTimeHelper.formatTime(program.startTime, is24Hour)
    val end = com.cinex.player.utils.EpgTimeHelper.formatTime(program.endTime, is24Hour)
    
    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = "$start - $end",
            color = if (isCurrent) Color(0xFFFFD700) else Color.Gray,
            fontSize = if (isCurrent) 14.sp else 13.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            text = program.title,
            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = if (isCurrent) 15.sp else 13.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ActionButton(text: String, color: Color, onClick: () -> Unit = {}) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) { onClick(); true } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .then(
                if (isFocused) Modifier.border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text.uppercase(), 
            color = Color.White, 
            fontSize = 12.sp, 
            fontWeight = FontWeight.ExtraBold
        )
    }
}

fun String.decodeBase64IfNeeded(): String {
    return try {
        val trimmed = this.trim()
        if (trimmed.isEmpty()) return this
        // Tenta decodificar Base64 (Xtream envia títulos codificados)
        val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
        val decoded = String(decodedBytes, Charsets.UTF_8)
        // Verifica se o resultado é texto legível (sem caracteres de controle)
        if (decoded.all { it.code >= 32 || it == '\n' || it == '\r' }) {
            decoded.trim()
        } else {
            this
        }
    } catch (e: Exception) {
        this
    }
}

// Formata horários do EPG Xtream
fun formatEpgTime(epg: com.cinex.player.data.network.EpgListing, is24Hour: Boolean): String {
    return com.cinex.player.utils.EpgTimeHelper.formatEpgRange(epg, is24Hour)
}

@Composable
private fun ChannelListRow(index: Int, channel: Channel, isSelected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Número do canal
        Text(
            text = "${index + 1}",
            color = if (isSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(28.dp)
        )
        // Logo do canal
        val hasLogo = !channel.logoUrl.isNullOrBlank()
        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(if (hasLogo) channel.logoUrl else com.cinex.player.R.drawable.logo_cinex)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            placeholder = androidx.compose.ui.res.painterResource(id = com.cinex.player.R.drawable.logo_cinex),
            error = androidx.compose.ui.res.painterResource(id = com.cinex.player.R.drawable.logo_cinex)
        )
        Spacer(Modifier.width(8.dp))
        // Nome do canal
        Text(
            text = channel.name,
            color = if (isSelected) Color(0xFFE50914) else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
