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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DarkBackground

enum class SeriesViewMode { LANDING, EPISODES }

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailsScreen(
    series: Channel,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (Channel) -> Unit
) {
    val isLoadingEpisodes by viewModel.isLoadingEpisodes.collectAsState()
    val seasons by viewModel.getSeasonsForSeries(series.seriesName ?: "").collectAsState(initial = emptyList())
    val sortedSeasons = seasons.filter { it > 0 }.sorted()
    var selectedSeason by remember { mutableStateOf(1) }
    var viewMode by remember { mutableStateOf(SeriesViewMode.LANDING) }

    LaunchedEffect(sortedSeasons) {
        if (sortedSeasons.isNotEmpty()) {
            if (selectedSeason !in sortedSeasons) {
                selectedSeason = sortedSeasons.first()
            }
        }
    }

    val episodesFlow = remember(series.seriesName, selectedSeason) {
        viewModel.getEpisodesBySeasonPaged(series.seriesName ?: "", selectedSeason)
    }
    val pagingItems = episodesFlow.collectAsLazyPagingItems()
    val context = LocalContext.current
    val playButtonRequester = remember { FocusRequester() }

    LaunchedEffect(viewMode) {
        if (viewMode == SeriesViewMode.LANDING) {
            try { playButtonRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    BackHandler {
        if (viewMode == SeriesViewMode.EPISODES) {
            viewMode = SeriesViewMode.LANDING
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        AsyncImage(
            model = series.bannerUrl ?: series.logoUrl,
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
                        if (viewMode == SeriesViewMode.EPISODES) {
                            viewMode = SeriesViewMode.LANDING
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.focusProperties { up = FocusRequester.Cancel }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = (series.seriesName ?: series.name).uppercase(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            if (viewMode == SeriesViewMode.LANDING) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 60.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = series.posterUrl?.takeIf { it.isNotEmpty() } ?: series.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text(
                            text = (series.seriesName ?: series.name).uppercase(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 34.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { index ->
                                    val rating = (series.tmdbRating ?: 0.0) / 2
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (index < rating.toInt()) Color.Yellow else Color.DarkGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.1f", series.tmdbRating ?: 0.0),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "•", color = Color.Gray)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = series.tmdbYear ?: "N/A", color = Color.Gray, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "•", color = Color.Gray)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "${sortedSeasons.size} Temporadas", color = Color.Gray, fontSize = 16.sp)
                        }

                        // Sinopse em bloco visual com scroll e fade
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            val scrollState = rememberScrollState()
                            Text(
                                text = series.tmdbSynopsis ?: "Sem sinopse disponível.",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(12.dp)
                            )
                            // Fade inferior indicando mais conteúdo
                            if (scrollState.canScrollForward) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, DarkBackground.copy(alpha = 0.9f))
                                            )
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val gradientBrush = remember { Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))) }

                            var isPlayFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewMode = SeriesViewMode.EPISODES },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .focusRequester(playButtonRequester)
                                    .focusProperties { down = FocusRequester.Cancel; left = FocusRequester.Cancel }
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .then(
                                        if (isPlayFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp))
                                        else Modifier
                                    ),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ASSISTIR EPISÓDIOS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            var isTrailerFocused by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = {
                                    val searchQuery = java.net.URLEncoder.encode("${series.name} trailer oficial", "UTF-8")
                                    val searchUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
                                    val youtubeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri).apply {
                                        setPackage("com.google.android.youtube")
                                    }
                                    try {
                                        context.startActivity(youtubeIntent)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri))
                                    }
                                },
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isTrailerFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .width(140.dp)
                                    .focusProperties { down = FocusRequester.Cancel; right = FocusRequester.Cancel }
                                    .onFocusChanged { isTrailerFocused = it.isFocused }
                                    .then(
                                        if (isTrailerFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                            ) {
                                Text(
                                    "TRAILER",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
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

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sortedSeasons) { seasonNum ->
                                val isSelected = selectedSeason == seasonNum
                                var isFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable(interactionSource = remember { MutableInteractionSource() })
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp &&
                                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                            ) { selectedSeason = seasonNum; true } else false
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
                                        .clickable { selectedSeason = seasonNum }
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = "Temporada $seasonNum",
                                        color = if (isFocused || isSelected) Color.White else Color.Gray,
                                        fontSize = 16.sp,
                                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
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

                        if (isLoadingEpisodes && pagingItems.itemCount == 0) {
                            // Shimmer loading placeholders
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(6) {
                                    ShimmerEpisodeItem()
                                }
                            }
                        } else if (!isLoadingEpisodes && pagingItems.itemCount == 0) {
                            // Empty state — nenhum episódio
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum episódio disponível",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                                            seriesPoster = series.bannerUrl ?: series.logoUrl,
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

@Composable
fun ShimmerEpisodeItem() {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.05f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Synopsis placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun EpisodeItem(episode: com.cinex.player.data.model.Channel, seriesPoster: String?, onClick: () -> Unit) {
    val imageModel = when {
        !episode.bannerUrl.isNullOrBlank() -> episode.bannerUrl
        !episode.logoUrl.isNullOrBlank() -> episode.logoUrl
        !episode.posterUrl.isNullOrBlank() -> episode.posterUrl
        else -> seriesPoster
    }

    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) { onClick(); true } else false
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
                }

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

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E${episode.episodeNumber}: ${episode.name}",
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
