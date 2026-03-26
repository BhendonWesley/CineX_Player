package com.cinex.player.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.R
import com.cinex.player.data.model.Playlist
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.*

@Composable
fun ServerProfileScreen(
    viewModel: MainViewModel,
    onServerSelected: () -> Unit
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf<Playlist?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1E))
    ) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(6.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xDD0A0F1E),
                            Color(0xFF0A0F1E)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo_cinex),
                contentDescription = "CineX",
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SELECIONE O SERVIDOR",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Escolha qual lista deseja utilizar",
                color = CineX_TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Server Cards
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(playlists) { playlist ->
                    val isActive = currentPlaylist?.url == playlist.url
                    ServerCard(
                        playlist = playlist,
                        isActive = isActive,
                        onClick = {
                            viewModel.enterPlatform(playlist)
                            onServerSelected()
                        },
                        onDelete = {
                            showDeleteConfirm = playlist
                        }
                    )
                }

                // Botão + Adicionar
                item {
                    AddServerCard(
                        onClick = { viewModel.syncFromPanel() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "MAC: ${viewModel.deviceMacAddress}",
                color = CineX_TextMuted,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = Color(0xFF1A1D21),
            title = {
                Text(
                    "Remover servidor?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "\"${showDeleteConfirm!!.name}\" será removido.\nSeus dados de canais e favoritos dessa lista serão apagados.",
                    color = CineX_TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(showDeleteConfirm!!)
                    showDeleteConfirm = null
                }) {
                    Text("REMOVER", color = CineX_HighlightRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("CANCELAR", color = CineX_TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ServerCard(
    playlist: Playlist,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200)
    )

    val borderColor = when {
        isActive -> CineX_PremiumGold
        isFocused -> CineX_DeepRed
        else -> Color.White.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .scale(scale)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF1A1510) else CineX_SecondaryBackground
            ),
            border = BorderStroke(
                if (isActive || isFocused) 2.dp else 1.dp,
                borderColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .onFocusChanged { isFocused = it.isFocused }
                .focusRequester(focusRequester)
                .focusable(interactionSource = remember { MutableInteractionSource() })
                .onKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                            onClick(); true
                        }
                        event.type == KeyEventType.KeyUp &&
                                (event.key == Key.Delete || event.key == Key.Backspace) -> {
                            onDelete(); true
                        }
                        else -> false
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ícone do servidor
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) Brush.linearGradient(listOf(CineX_DeepRed, CineX_DarkRed))
                            else Brush.linearGradient(listOf(Color(0xFF2A2D32), Color(0xFF1E2125)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (isActive) CineX_PremiumGold else CineX_TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Nome do servidor
                Text(
                    text = playlist.name,
                    color = if (isActive) CineX_PremiumGold else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Badge Ativo
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .background(CineX_PremiumGold.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "ATIVO",
                            color = CineX_PremiumGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Text(
                        text = "Toque para ativar",
                        color = CineX_TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Botão X para remover (canto superior direito)
        if (!isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CineX_HighlightRed)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remover",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun AddServerCard(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            if (isFocused) CineX_DeepRed else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) { onClick(); true } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, CineX_TextMuted.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar servidor",
                    tint = CineX_TextMuted,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Adicionar Servidor",
                color = CineX_TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sincronizar",
                color = CineX_TextMuted.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}
