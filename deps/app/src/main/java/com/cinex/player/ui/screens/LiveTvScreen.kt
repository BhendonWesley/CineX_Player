package com.cinex.player.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import android.util.Base64
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.CineX_SecondaryBackground
import com.cinex.player.ui.components.CategoryItem
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    viewModel: com.cinex.player.ui.MainViewModel,
    onChannelExpand: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.liveCategories.collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf("Tudo") }
    
    val pagingItems = remember(selectedCategory) {
        viewModel.getPagedChannelsByCategory(selectedCategory)
    }.collectAsLazyPagingItems()

    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    val context = LocalContext.current
    val previewPlayer = remember { ExoPlayer.Builder(context).build() }

    // Libera o player ao sair da tela
    DisposableEffect(Unit) {
        onDispose {
            previewPlayer.release()
        }
    }
    
    val currentProgram by viewModel.currentProgram.collectAsState()
    val upcomingPrograms by viewModel.upcomingPrograms.collectAsState()
    val epgListings by viewModel.epgListings.collectAsState() // Fallback Xtream

    // Auto-play: seleciona o primeiro canal quando a lista carrega
    LaunchedEffect(pagingItems.itemCount, selectedCategory) {
        if (pagingItems.itemCount > 0 && selectedChannel == null) {
            selectedChannel = pagingItems[0]
            viewModel.updateSelectedChannel(pagingItems[0])
        }
    }

    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            if (channel.streamUrl.isNotEmpty()) {
                val mediaItem = MediaItem.fromUri(channel.streamUrl)
                previewPlayer.setMediaItem(mediaItem)
                previewPlayer.prepare()
                previewPlayer.play()
            }
        } ?: run {
            previewPlayer.stop()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // 1. Coluna de Categorias
        LazyColumn(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(Color(0xFF151515))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val counts by viewModel.categoryCounts.collectAsState()
                val typeCounts by viewModel.typeCounts.collectAsState()
                val favoriteCounts by viewModel.favoriteCounts.collectAsState()
                
                val countByCat = when (category.id) {
                    "Tudo" -> typeCounts["LIVE_TV"] ?: 0
                    "Favorito" -> favoriteCounts["LIVE_TV"] ?: 0
                    else -> counts[category.id] ?: 0
                }

                CategoryItem(
                    name = category.name,
                    count = countByCat,
                    isSelected = selectedCategory == category.id,
                    onClick = { selectedCategory = category.id }
                )
            }
        }
        
        // 2. Coluna de Canais
        LazyColumn(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF1A1A1A))
        ) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id }
            ) { index ->
                val channel = pagingItems[index]
                if (channel != null) {
                    val isSelected = selectedChannel?.id == channel.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFFC62828).copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { 
                                selectedChannel = channel
                                viewModel.updateSelectedChannel(channel)
                            } 
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "${index + 1}   ${channel.name}",
                            color = if (isSelected) Color(0xFFE50914) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Coluna de Conteúdo (Maior - Player + EPG + Botões)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0D0D0D))
        ) {
            // Player Area (Professional 16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .clickable { selectedChannel?.let { onChannelExpand(it) } },
                contentAlignment = Alignment.Center
            ) {
                if (selectedChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = previewPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // 1. Título do Canal (Sutil)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = (selectedChannel?.name ?: "NOME DO CANAL").uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 2. EPG Section (Prioritária)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp)
            ) {
                val hasEpg = currentProgram != null || epgListings.isNotEmpty()
                
                if (!hasEpg) {
                    Text(
                        "Guia de programação não disponível para este canal.", 
                        color = Color.White.copy(alpha = 0.3f), 
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Programa Atual com Destaque Amarelo (Estilo Profissional)
                        if (currentProgram != null) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    EpgItem(program = currentProgram!!, isCurrent = true)
                                    
                                    // Barra de Progresso Sutil
                                    val total = (currentProgram!!.endTime - currentProgram!!.startTime).coerceAtLeast(1)
                                    val passed = (System.currentTimeMillis() - currentProgram!!.startTime).coerceIn(0, total)
                                    val progress = passed.toFloat() / total.toFloat()
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.Gray.copy(alpha = 0.2f))) {
                                        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFFFFD700))) // Amarelo
                                    }
                                }
                            }
                        } else if (epgListings.isNotEmpty()) {
                            item {
                                val first = epgListings[0]
                                Text(
                                    text = "AGORA: ${first.title.decodeBase64IfNeeded()}", 
                                    color = Color(0xFFFFD700), 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Lista a Seguir (Mais Densa)
                        if (upcomingPrograms.isNotEmpty()) {
                            items(upcomingPrograms.take(10)) { program ->
                                EpgItem(program = program, isCurrent = false)
                            }
                        } else if (epgListings.size > 1) {
                            items(epgListings.drop(1).take(10)) { epg ->
                                val title = epg.title.decodeBase64IfNeeded()
                                val time = epg.start.takeLast(8).take(5)
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(text = time, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                                    Text(text = title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Barra de Ações na Base (Discreta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFavorite = selectedChannel?.isFavorite == true
                
                // Botão de Favorito Compacto
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isFavorite) Color.White.copy(alpha = 0.1f) else Color(0xFFC62828).copy(alpha = 0.1f))
                        .clickable { selectedChannel?.let { viewModel.updateFavorite(it.id, !isFavorite) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isFavorite) "REMOVER FAVORITO" else "ADICIONAR FAVORITO",
                            color = if (isFavorite) Color.White else Color(0xFFC62828),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpgItem(program: com.cinex.player.data.model.EpgProgram, isCurrent: Boolean) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val start = timeFormat.format(Date(program.startTime))
    val end = timeFormat.format(Date(program.endTime))
    
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$start - $end",
            color = if (isCurrent) Color.Yellow else Color.Gray,
            fontSize = 14.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = program.title,
            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = if (isCurrent) 16.sp else 14.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun ActionButton(text: String, color: Color, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
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
        // Verifica se parece base64 (caracteres válidos e comprimento múltiplo de 4)
        if (this.length > 4 && this.contains(Regex("[a-zA-Z0-9+/=]"))) {
            val decodedBytes = Base64.decode(this, Base64.DEFAULT)
            String(decodedBytes)
        } else {
            this
        }
    } catch (e: Exception) {
        this
    }
}
