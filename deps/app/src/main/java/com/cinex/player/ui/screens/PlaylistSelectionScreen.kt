package com.cinex.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.data.model.Playlist
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.*

@Composable
fun PlaylistSelectionScreen(
    viewModel: MainViewModel
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val accountInfo by viewModel.accountInfo.collectAsState()
    
    val liveProgress by viewModel.liveProgress.collectAsState()
    val movieProgress by viewModel.movieProgress.collectAsState()
    val seriesProgress by viewModel.seriesProgress.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState() // Nova adição

    if (isLoading) {
        CinematicLoadingScreen(
            statusMessage = syncStatus,
            tvProgress = liveProgress / 100f,
            moviesProgress = movieProgress / 100f,
            seriesProgress = seriesProgress / 100f
        )
        return
    }

    // Layout Original Restaurado (Single-Screen Horizontal)
    Box(modifier = Modifier.fillMaxSize().background(CineX_BackgroundBlue)) {
        // Background image com blur (mesmo padrão do app)
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(4.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.5f
        )
        // Overlay escuro
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CineX_BackgroundBlue.copy(alpha = 0.85f),
                            CineX_SecondaryBackground.copy(alpha = 0.9f)
                        )
                    )
                )
        )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Lado Esquerdo: Ações (Logo, Sincronizar, Entrar)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Centralizado
            Image(
                painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                contentDescription = "CineX Logo",
                modifier = Modifier
                    .size(48.dp)
                    .shadow(8.dp, CircleShape, spotColor = CineX_HighlightRed.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Card: Sincronizar Conteúdo
            SyncActionCard(
                onClick = { viewModel.syncFromPanel() },
                isLoading = isLoading,
                isSynced = playlists.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botão Entrar
            Button(
                onClick = { 
                    if (playlists.isNotEmpty() && !isLoading) {
                        viewModel.selectPlaylist(playlists.first()) 
                    }
                },
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playlists.isNotEmpty() && !isLoading) CineX_DeepRed else CineX_SecondaryBackground,
                    contentColor = if (playlists.isNotEmpty() && !isLoading) Color.White else CineX_TextSecondary
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp, 
                    if (playlists.isNotEmpty() && !isLoading) CineX_HighlightRed else CineX_TextMuted
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(
                        elevation = if (playlists.isNotEmpty()) 8.dp else 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = CineX_HighlightRed,
                        spotColor = CineX_HighlightRed
                    )
            ) {
                Text(
                    text = if (playlists.isNotEmpty() && !isLoading) "ENTRAR" else "AGUARDANDO LISTA...", 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "", 
                    color = CineX_HighlightRed, 
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }

        // Barra Divisória
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.8f)
                .align(Alignment.CenterVertically)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x33FFFFFF), Color.Transparent)
                    )
                )
        )

        // Lado Direito: Boas-vindas e MAC
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Text(
                text = "Bem-vindo (a)!", 
                color = Color.White, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Envie o código MAC abaixo para o seu revendedor oficial para ativar seu dispositivo.",
                color = CineX_TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Cartão Premium do MAC Formato Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CineX_SecondaryBackground,
                border = BorderStroke(1.dp, CineX_PremiumGold),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SEU ENDEREÇO MAC", 
                        color = CineX_TextSecondary, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold, 
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = accountInfo?.macAddress ?: viewModel.deviceMacAddress, 
                        color = CineX_LightGold, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Aviso legal
            Text(
                text = "O CineX Player não vende playlists ou assinaturas.\nO CineX Player é um reprodutor de mídia geral e não inclui nenhum conteúdo ou playlists.",
                color = CineX_TextMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }
    } // fim Row
    } // fim Box
}

@Composable
fun SyncActionCard(
    onClick: () -> Unit,
    isLoading: Boolean,
    isSynced: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CineX_SecondaryBackground),
        border = BorderStroke(1.dp, if (isSynced) CineX_PremiumGold else CineX_PremiumGold.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clickable(enabled = !isLoading) { onClick() }
            .shadow(
                elevation = if (isSynced) 8.dp else 4.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = if (isSynced) CineX_PremiumGold.copy(alpha = 0.2f) else CineX_HighlightRed.copy(alpha = 0.4f),
                spotColor = if (isSynced) CineX_PremiumGold.copy(alpha = 0.2f) else CineX_HighlightRed.copy(alpha = 0.4f)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = CineX_DeepRed, modifier = Modifier.size(32.dp))
            } else {
                Icon(
                    imageVector = if (isSynced) Icons.Default.CheckCircle else Icons.Default.Sync, 
                    contentDescription = null, 
                    tint = CineX_LightGold, 
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSynced) "Conteúdo sincronizado!" else "Sincronizar Conteúdo", 
                color = if (isSynced) CineX_PremiumGold else Color.White, 
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSynced) "Clique no botão ENTRAR abaixo" else "Buscar biblioteca do servidor", 
                color = CineX_TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
