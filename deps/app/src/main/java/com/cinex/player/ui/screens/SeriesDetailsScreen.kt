package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DarkBackground

@Composable
fun SeriesDetailsScreen(
    series: Channel,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (Channel) -> Unit
) {
    val seasons by viewModel.getSeasonsForSeries(series.seriesName ?: "").collectAsState(initial = emptyList())
    var selectedSeason by remember { mutableStateOf(1) }
    
    // Auto-select first season when available
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && selectedSeason !in seasons) {
            selectedSeason = seasons.first()
        }
    }

    val episodesFlow = remember(series.seriesName, selectedSeason) {
        viewModel.getEpisodesBySeasonPaged(series.seriesName ?: "", selectedSeason)
    }
    val pagingItems = episodesFlow.collectAsLazyPagingItems()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Backdrop em tela cheia com Scrim
        AsyncImage(
            model = series.bannerUrl ?: series.logoUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
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
                        endX = 1000f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Info Lado Esquerdo (Fixo)
                Column(modifier = Modifier.weight(1f)) {
                    // Botão de Trailer se disponível
                    if (series.trailerUrl != null) {
                        TextButton(
                            onClick = { 
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(series.trailerUrl))
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Assista ao Trailer", color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = (series.seriesName ?: series.name).uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 36.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
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
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = "|", color = Color.DarkGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = series.tmdbYear ?: "N/A", color = Color.Gray, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sinopse com scroll interno
                    Text(
                        text = series.tmdbSynopsis ?: "Sem sinopse disponível.",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 8,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )

                    if (!series.castMembers.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("ELENCO", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val castList = series.castMembers.split(", ").take(6)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(castList) { actor ->
                                CastItem(name = actor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(64.dp))

                // Episódios Lado Direito
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "EPISÓDIOS",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    ScrollableTabRow(
                        selectedTabIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0),
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        seasons.sorted().forEach { seasonNum ->
                            Tab(
                                selected = selectedSeason == seasonNum,
                                onClick = { selectedSeason = seasonNum },
                                text = { Text("Temporada $seasonNum", fontSize = 16.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(
                            count = pagingItems.itemCount,
                            key = pagingItems.itemKey { it.id }
                        ) { index ->
                            pagingItems[index]?.let { episode ->
                                EpisodeItem(episode = episode, onClick = { onPlayEpisode(episode) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(episode: Channel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1AFFFFFF))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(width = 100.dp, height = 60.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = "E${episode.episodeNumber}: ${episode.name}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "Assista agora", color = Color.Gray, fontSize = 12.sp)
        }
    }
}
