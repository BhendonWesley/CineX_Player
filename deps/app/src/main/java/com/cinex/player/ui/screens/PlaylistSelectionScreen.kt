package com.cinex.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.scale
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
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0F1E))) {
        // Background image com blur
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(4.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.7f
        )
        // Overlay escuro — mesmo padrão da CinematicLoadingScreen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x990A0F1E))
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Logo centralizada na metade superior
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
            contentDescription = "CineX Logo",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Row com os dois cards alinhados horizontalmente
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Lado Esquerdo: Sincronizar + Botão
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val syncFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { syncFocusRequester.requestFocus() }
                SyncActionCard(
                    onClick = { viewModel.syncFromPanel() },
                    isLoading = isLoading,
                    isSynced = playlists.isNotEmpty(),
                    focusRequester = syncFocusRequester
                )

                Spacer(modifier = Modifier.height(10.dp))

                val isReady = playlists.isNotEmpty() && !isLoading

                if (isReady) {
                    var isButtonFocused by remember { mutableStateOf(false) }
                    val enterFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { enterFocusRequester.requestFocus() }
                    Card(
                        onClick = { viewModel.enterPlatform(playlists.first()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CineX_DeepRed),
                        border = BorderStroke(
                            if (isButtonFocused) 2.dp else 1.dp,
                            if (isButtonFocused) CineX_PremiumGold else CineX_HighlightRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .focusRequester(enterFocusRequester)
                            .onFocusChanged { isButtonFocused = it.isFocused }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACESSAR PLATAFORMA",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CineX_SecondaryBackground),
                        border = BorderStroke(1.dp, CineX_TextMuted),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AGUARDANDO LISTA...",
                                color = CineX_TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = CineX_HighlightRed,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 5
                    )
                }
            }

            // Barra Divisória
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x33FFFFFF), Color.Transparent)
                        )
                    )
            )

            // Lado Direito: MAC + info
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cartão MAC — mesma altura do SyncActionCard
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CineX_SecondaryBackground,
                    border = BorderStroke(1.dp, CineX_PremiumGold),
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
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

                Text(
                    text = "O CineX Player é um reprodutor de mídia geral e não vende, fornece ou inclui nenhuma lista, assinatura ou conteúdo.",
                    color = CineX_TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        val context = androidx.compose.ui.platform.LocalContext.current
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) { "1.0" }
        }
        Text(
            text = "CineX Player v$versionName",
            color = CineX_TextMuted.copy(alpha = 0.5f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    } // fim Column
    } // fim Box
}

@Composable
fun SyncActionCard(
    onClick: () -> Unit,
    isLoading: Boolean,
    isSynced: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = { if (!isLoading) onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CineX_SecondaryBackground),
        border = BorderStroke(
            if (isFocused) 2.dp else 1.dp,
            if (isFocused) CineX_PremiumGold
            else if (isSynced) CineX_PremiumGold
            else CineX_TextMuted
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
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
                text = if (isSynced) "CONTEÚDO SINCRONIZADO!" else "SINCRONIZAR SERVIDOR",
                color = if (isSynced) CineX_PremiumGold else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
