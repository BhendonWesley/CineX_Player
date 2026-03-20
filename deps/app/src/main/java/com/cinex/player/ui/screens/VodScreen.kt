package com.cinex.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.components.VodPosterItem
import com.cinex.player.ui.components.CategoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.paging.PagingData

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

@Composable
fun VodScreen(
    type: String = "MOVIE",
    viewModel: com.cinex.player.ui.MainViewModel,
    title: String,
    channels: Flow<PagingData<Channel>>? = null,
    continueWatching: List<Channel> = emptyList(),
    onVideoClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoriesFlow = when (type) {
        "MOVIE"  -> viewModel.movieCategories
        "SERIES" -> viewModel.seriesCategories
        else     -> flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())

    val selectedMovieCategory  by viewModel.selectedMovieCategory.collectAsState()
    val selectedSeriesCategory by viewModel.selectedSeriesCategory.collectAsState()
    val selectedCategory = when (type) {
        "MOVIE"  -> selectedMovieCategory
        "SERIES" -> selectedSeriesCategory
        else     -> "Tudo"
    }

    val pagingItems = if (channels != null) {
        channels.collectAsLazyPagingItems()
    } else {
        remember(selectedCategory, type) {
            if (type == "MOVIE") viewModel.getPagedMoviesByCategory(selectedCategory)
            else viewModel.getPagedSeriesByCategory(selectedCategory)
        }.collectAsLazyPagingItems()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // Imagem de fundo com blur (mesma da tela de loading)
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(2.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.55f
        )
        // Overlay escuro para garantir legibilidade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB0A0F1E))
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
        ) {
        if (type != "SEARCH") {
            // ── SIDEBAR DE CATEGORIAS ──────────────────────────────
            val sidebarScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
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

                categories.forEach { category ->
                    val count = when (category.id) {
                        "Tudo"     -> typeCounts[type] ?: 0
                        "Favorito" -> favCounts[type] ?: 0
                        else       -> counts[category.id] ?: 0
                    }
                    CategoryItem(
                        name = category.name,
                        count = count,
                        isSelected = selectedCategory == category.id,
                        onClick = {
                            if (type == "MOVIE") viewModel.setMovieCategory(category.id)
                            else viewModel.setSeriesCategory(category.id)
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (continueWatching.isNotEmpty()) {
                    CategoryItem(
                        name = "Visualizados recentemente",
                        count = continueWatching.size,
                        isSelected = selectedCategory == "Continuar Assistindo",
                        onClick = {
                            if (type == "MOVIE") viewModel.setMovieCategory("Continuar Assistindo")
                            else viewModel.setSeriesCategory("Continuar Assistindo")
                        }
                    )
                }
            }
        }

        // ── GRID DE CONTEÚDO ──────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 0.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id }
            ) { index ->
                pagingItems[index]?.let { channel ->
                    LaunchedEffect(channel.id) {
                        viewModel.onChannelVisible(channel)
                    }
                    VodPosterItem(
                        channel = channel,
                        onClick = { onVideoClick(channel) }
                    )
                }
            }
        }
        } // fecha Row
    } // fecha Box
}
