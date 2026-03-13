package com.cinex.player.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
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
    
    LaunchedEffect(selectedCategory) {
        // Se mudou de categoria, seleciona o primeiro canal da nova lista para o preview
        if (pagingItems.itemCount > 0) {
            selectedChannel = pagingItems[0]
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
        // 1. Coluna de Categorias (Mais estreita)
        LazyColumn(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .background(Color(0xFF1A1A1A))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                CategoryItem(
                    name = category.name,
                    count = 0,
                    isSelected = selectedCategory == category.id,
                    onClick = { selectedCategory = category.id }
                )
            }
        }
        
        // 2. Coluna de Canais (Média)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF222222))
        ) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id }
            ) { index ->
                val channel = pagingItems[index]
                if (channel != null) {
                    val isSelected = selectedChannel?.id == channel.id
                    Text(
                        text = "${index + 1}   ${channel.name}",
                        color = if (isSelected) Color.Yellow else Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFF444444) else Color.Transparent)
                            .clickable { selectedChannel = channel } 
                            .padding(16.dp)
                    )
                }
            }
        }

        // 3. Coluna de Conteúdo (Maior - Player + EPG + Botões)
        Column(
            modifier = Modifier
                .weight(2.3f)
                .fillMaxHeight()
                .background(Color(0xFF121212))
        ) {
            // Player Area (16:9 ou similar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp) // Aumentamos para destacar o player
                    .background(Color.Black)
                    .clickable { selectedChannel?.let { onChannelExpand(it) } },
                contentAlignment = Alignment.Center
            ) {
                if (selectedChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = previewPlayer
                                useController = false // Sem controles no preview
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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
            
            // Área de Detalhes e EPG
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(24.dp)
            ) {
                Text(
                    text = (selectedChannel?.name ?: "NOME DO CANAL").uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Programação (EPG)
                Text("05:00 AM ~ 06:50 AM  ${selectedChannel?.name ?: "Programação Atual"}", color = Color.Yellow, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("06:50 AM ~ 08:40 AM  Próximo Programa", color = Color.LightGray, fontSize = 14.sp)
                Text("08:40 AM ~ 10:45 AM  Filme da Manhã", color = Color.LightGray, fontSize = 14.sp)
                Text("10:45 AM ~ 01:35 PM  Telejornal Local", color = Color.LightGray, fontSize = 14.sp)
            }

            // Barra de Botões Inferior (Estilo IBO PRO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Spacer(modifier = Modifier.weight(1f))
                
                ActionButton(text = "Guia de Jogos", color = Color(0xFF6200EE))
                ActionButton(text = "Adicionar aos favoritos", color = Color(0xFF6200EE))
                ActionButton(text = "procurar", color = Color(0xFF6200EE))
            }
        }
    }
}

@Composable
fun ActionButton(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .clickable { /* Ação */ }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text.uppercase(), 
            color = Color.White, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.ExtraBold
        )
    }
}
