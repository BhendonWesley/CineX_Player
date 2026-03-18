package com.cinex.player.ui.screens

import androidx.compose.foundation.BorderStroke
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

enum class SeriesViewMode { LANDING, EPISODES }

@Composable
fun SeriesDetailsScreen(
    series: Channel,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (Channel) -> Unit
) {
    val seasons by viewModel.getSeasonsForSeries(series.seriesName ?: "").collectAsState(initial = emptyList())
    val sortedSeasons = seasons.filter { it > 0 }.sorted()
    var selectedSeason by remember { mutableStateOf(1) }
    var viewMode by remember { mutableStateOf(SeriesViewMode.LANDING) }
    
    // Auto-select first season when available
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
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Backdrop em tela cheia com Scrim
        AsyncImage(
            model = series.bannerUrl ?: series.logoUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )
        
        // Degradês Cinematográficos
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
            // Barra Superior de Navegação
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (viewMode == SeriesViewMode.EPISODES) {
                        viewMode = SeriesViewMode.LANDING
                    } else {
                        onBack()
                    }
                }) {
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
                // PASSO 1: LANDING PAGE (Estilo Netflix/Filmes)
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Poster - Redimensionado para caber melhor
                    AsyncImage(
                        model = series.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(220.dp) // Reduzido de 280.dp
                            .aspectRatio(2/3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(32.dp)) // Reduzido de 48.dp

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (series.seriesName ?: series.name).uppercase(),
                            color = Color.White,
                            fontSize = 32.sp, // Reduzido de 42.sp
                            fontWeight = FontWeight.Black,
                            lineHeight = 38.sp
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

                        Text(
                            text = series.tmdbSynopsis ?: "Sem sinopse disponível.",
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 5,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        Row(modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)) {
                            Button(
                                onClick = { viewMode = SeriesViewMode.EPISODES },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ASSISTIR EPISÓDIOS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            
                            if (series.trailerUrl != null) {
                                Spacer(modifier = Modifier.width(16.dp))
                                OutlinedButton(
                                    onClick = { 
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(series.trailerUrl))
                                        context.startActivity(intent)
                                    },
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(48.dp).width(140.dp)
                                ) {
                                    Text("TRAILER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // PASSO 2: SELEÇÃO DE EPISÓDIOS (Painel Duplo)
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
                    // LADO ESQUERDO: Lista de Temporadas (Vertical)
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { selectedSeason = seasonNum }
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = "Temporada $seasonNum",
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 16.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // LADO DIREITO: Lista de Episódios
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text(
                            text = "EPISÓDIOS - TEMPORADA $selectedSeason",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp, top = 16.dp)
                        )

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

@Composable
fun EpisodeItem(episode: com.cinex.player.data.model.Channel, seriesPoster: String?, onClick: () -> Unit) {
    // Hierarquia de imagens: Still do Episódio (bannerUrl) > Poster da Série (posterUrl) > Logo original > Fallback passado
    val imageModel = when {
        !episode.bannerUrl.isNullOrBlank() -> episode.bannerUrl
        !episode.posterUrl.isNullOrBlank() -> episode.posterUrl
        !episode.logoUrl.isNullOrBlank() -> episode.logoUrl
        else -> seriesPoster
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail do Episódio
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Overlay de Play
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
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
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
