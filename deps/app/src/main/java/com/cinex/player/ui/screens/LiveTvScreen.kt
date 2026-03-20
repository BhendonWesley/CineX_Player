package com.cinex.player.ui.screens

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.util.Base64
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

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
    val avgItemH = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
    val totalContentH = avgItemH * totalItems
    if (totalContentH <= viewportH) return@drawWithContent

    val scrolled = firstVisible * avgItemH + firstOffset
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
    modifier: Modifier = Modifier
) {
    val previewPlayerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    val categories by viewModel.liveCategories.collectAsState(initial = emptyList())
    val selectedCategory by viewModel.liveCategoryId.collectAsState()
    
    val pagingItems = viewModel.liveTvPagingData.collectAsLazyPagingItems()

    // Canal selecionado vive no ViewModel para persistir entre fullscreen ↔ preview
    val selectedChannel by viewModel.selectedLiveChannel.collectAsState()

    val currentProgram by viewModel.currentProgram.collectAsState()
    val upcomingPrograms by viewModel.upcomingPrograms.collectAsState()
    val epgListings by viewModel.epgListings.collectAsState() // Fallback Xtream
    val is24Hour by viewModel.is24HourFormat.collectAsState()

    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val typeCounts by viewModel.typeCounts.collectAsState()
    val favoriteCounts by viewModel.favoriteCounts.collectAsState()

    // Auto-seleciona o primeiro canal apenas quando a aba Live TV está visível
    LaunchedEffect(pagingItems.itemCount, isActive) {
        if (isActive && selectedChannel == null && pagingItems.itemCount > 0) {
            pagingItems[0]?.let { firstChannel ->
                viewModel.updateSelectedChannel(firstChannel)
            }
        }
    }

    // Toca o canal somente quando o usuário seleciona manualmente
    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { channel ->
            if (channel.streamUrl.isNotEmpty()) {
                viewModel.playLiveChannel(channel)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Imagem de fundo com blur
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(2.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.55f
        )
        // Overlay escuro
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB101010))
        )
        // Conteúdo por cima do fundo
    Row(modifier = Modifier.fillMaxSize()) {
        // 1. Coluna de Categorias
        val catListState = rememberLazyListState()
        LazyColumn(
            state = catListState,
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(Color(0xCC141414))
                .lazyScrollbar(catListState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
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
                        viewModel.setLiveCategory(category.id)
                    }
                )
            }
        }
        
        
        // 2. Coluna de Canais
        Box(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xCC1A1A1A))
        ) {
            if (pagingItems.itemCount == 0) {
                // Loading indicator
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    color = Color(0xFFC62828),
                    strokeWidth = 3.dp
                )
            }
            val channelListState = rememberLazyListState()
            LazyColumn(
                state = channelListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyScrollbar(channelListState, thumbColor = Color(0xFFC62828))
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
                    .clickable { selectedChannel?.let { onChannelExpand(it) } },
                contentAlignment = Alignment.Center
            ) {
                if (selectedChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            (LayoutInflater.from(ctx).inflate(
                                com.cinex.player.R.layout.player_view_texture, null
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

                // Botão favorito no canto superior direito do preview
                val isFavorite = selectedChannel?.isFavorite == true
                IconButton(
                    onClick = { selectedChannel?.let { viewModel.updateFavorite(it.id, !isFavorite) } },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remover favorito" else "Adicionar favorito",
                        tint = if (isFavorite) Color(0xFFC62828) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // Programa Atual (Destaque Amarelo + Progresso)
                        if (currentProgram != null) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
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
                            }
                        } else if (epgListings.isNotEmpty()) {
                            item {
                                val first = epgListings[0]
                                val title = first.title.decodeBase64IfNeeded()
                                val timeRange = formatEpgTime(first, is24Hour)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = timeRange,
                                            color = Color(0xFFFFD700),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(if (is24Hour) 110.dp else 160.dp)
                                        )
                                        Text(
                                            text = title,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFFFD700).copy(alpha = 0.4f)))
                                }
                            }
                        }

                        // Próximos Programas
                        if (upcomingPrograms.isNotEmpty()) {
                            items(upcomingPrograms.take(10)) { program ->
                                EpgItem(program = program, isCurrent = false, is24Hour = is24Hour)
                            }
                        } else if (epgListings.size > 1) {
                            items(epgListings.drop(1).take(10)) { epg ->
                                val title = epg.title.decodeBase64IfNeeded()
                                val timeRange = formatEpgTime(epg, is24Hour)
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = timeRange,
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(if (is24Hour) 110.dp else 160.dp)
                                    )
                                    Text(
                                        text = title,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } // fim Row

    // Fullscreen agora é via VideoPlayerScreen (MainScreen.playingChannel)
    } // fim Box
}

@Composable
fun EpgItem(program: com.cinex.player.data.model.EpgProgram, isCurrent: Boolean, is24Hour: Boolean = true) {
    val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
    val timeFormat = SimpleDateFormat(pattern, Locale.getDefault())
    val start = timeFormat.format(Date(program.startTime))
    val end = timeFormat.format(Date(program.endTime))
    
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$start - $end",
            color = if (isCurrent) Color(0xFFFFD700) else Color.Gray,
            fontSize = if (isCurrent) 15.sp else 14.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.width(if (is24Hour) 110.dp else 160.dp)
        )
        Text(
            text = program.title,
            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = if (isCurrent) 17.sp else 14.sp,
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

// Formata horários do EPG Xtream usando timestamps Unix (fonte confiável)
fun formatEpgTime(epg: com.cinex.player.data.network.EpgListing, is24Hour: Boolean): String {
    val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
    val outputFormat = SimpleDateFormat(pattern, Locale.getDefault())
    
    // Prioridade 1: Usar start_timestamp / stop_timestamp (Unix epoch em segundos)
    val startTs = epg.start_timestamp?.toLongOrNull()
    val stopTs = epg.stop_timestamp?.toLongOrNull()
    
    if (startTs != null && stopTs != null) {
        val startStr = outputFormat.format(Date(startTs * 1000))
        val endStr = outputFormat.format(Date(stopTs * 1000))
        return "$startStr - $endStr"
    }
    
    // Prioridade 2: Tentar parsing do campo start/end como data formatada
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val startStr = outputFormat.format(inputFormat.parse(epg.start)!!)
        val endStr = outputFormat.format(inputFormat.parse(epg.end)!!)
        "$startStr - $endStr"
    } catch (e: Exception) {
        // Fallback: extrair hora do campo start
        try {
            val startOnly = epg.start.takeLast(8).take(5)
            startOnly
        } catch (e2: Exception) { "" }
    }
}
