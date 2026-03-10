package com.cinex.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    viewModel: MainViewModel,
    onAddPlaylistClick: () -> Unit
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val accountInfo by viewModel.accountInfo.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CineX_BackgroundBlue, CineX_SecondaryBackground)
                )
            )
    ) {
        // Lado Esquerdo: Ação Principal de Sincronização
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .padding(40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo com Glow
                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Player",
                    color = CineX_TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Novo Action Card: Sincronizar Conteúdo
            SyncActionCard(
                onClick = { viewModel.syncFromPanel() },
                isLoading = isLoading
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "O CineX não vende listas de reprodução ou assinaturas.\nO CineX é um reprodutor de mídia geral e não inclui nenhum conteúdo ou lista de reprodução.",
                color = CineX_TextMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Barra Divisória Sutil Cinemática
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.7f)
                .align(Alignment.CenterVertically)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x33FFFFFF), Color.Transparent)
                    )
                )
        )

        // Lado Direito: Info do Dispositivo CineX
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Button(
                onClick = { 
                    // Se houver uma lista já sincronizada, permite ir direto
                    if (playlists.isNotEmpty()) viewModel.selectPlaylist(playlists.first()) 
                },
                enabled = playlists.isNotEmpty() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CineX_DeepRed,
                    contentColor = Color.White,
                    disabledContainerColor = CineX_TextMuted
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = if (playlists.isNotEmpty()) 8.dp else 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = CineX_HighlightRed,
                        spotColor = CineX_HighlightRed
                    )
            ) {
                Text(
                    "ENTRAR", 
                    color = Color.White, 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "", 
                    color = CineX_HighlightRed, 
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Dispositivo CineX", 
                color = CineX_TextPrimary, 
                fontSize = 22.sp, 
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Bem-vindo", color = CineX_TextSecondary, fontSize = 16.sp)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("ENDEREÇO MAC", color = CineX_TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = accountInfo?.macAddress ?: "79:77:0C:0E:46:38", 
                color = CineX_LightGold, 
                fontSize = 18.sp, 
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Ação Secundária: Gerenciar no Site
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://cinex.play/manage"))
                    context.startActivity(intent)
                },
                border = BorderStroke(1.dp, CineX_PremiumGold.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(45.dp)
            ) {
                Icon(Icons.Default.Launch, contentDescription = null, tint = CineX_LightGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gerenciar no Site", color = CineX_LightGold, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Text("v5.0", color = CineX_TextMuted, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun SyncActionCard(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CineX_SecondaryBackground),
        border = BorderStroke(1.dp, CineX_PremiumGold.copy(alpha = 0.6f)),
        modifier = Modifier
            .size(width = 320.dp, height = 200.dp)
            .clickable(enabled = !isLoading) { onClick() }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = CineX_HighlightRed.copy(alpha = 0.4f),
                spotColor = CineX_HighlightRed.copy(alpha = 0.4f)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = CineX_DeepRed, modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Sync, 
                    contentDescription = null, 
                    tint = CineX_LightGold, 
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sincronizar Conteúdo", 
                color = Color.White, 
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Buscar biblioteca do servidor", 
                color = CineX_TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
