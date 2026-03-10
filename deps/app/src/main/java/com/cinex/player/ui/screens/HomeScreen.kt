package com.cinex.player.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.DarkBackground
import com.cinex.player.ui.theme.DeepRed

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    featuredMovies: List<Channel>,
    onNavigate: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onServerSwap: () -> Unit,
    onRefresh: () -> Unit,
    accountInfo: com.cinex.player.ui.AccountInfo?,
    modifier: Modifier = Modifier
) {
    var showAccountDialog by remember { mutableStateOf(false) }
    
    // Animação infinita e "imparável" para o fundo
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6000f, // Valor mais alto para scroll mais longo
        animationSpec = infiniteRepeatable(
            animation = tween(90000, easing = LinearEasing), // 90 segundos (Mais rápido)
            repeatMode = RepeatMode.Restart
        ),
        label = "xOffset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- LAYER 1: BACKGROUND POSTERS ---
        if (featuredMovies.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = xOffset }, // Animação via camada gráfica (imparável)
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filtramos novamente por segurança e limitamos para garantir performance
                val validPosters = featuredMovies.filter { !it.logoUrl.isNullOrEmpty() || !it.bannerUrl.isNullOrEmpty() }
                
                if (validPosters.isNotEmpty()) {
                    val displayList = List(20) { validPosters }.flatten()
                    displayList.forEach { movie -> 
                        AsyncImage(
                            model = movie.logoUrl ?: movie.bannerUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(300.dp)
                                .clip(RoundedCornerShape(0.dp)),
                            contentScale = ContentScale.Crop,
                            alpha = 0.4f
                        )
                    }
                }
            }
        }

        // --- LAYER 2: DARK GRADIENT OVERLAY ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.9f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )
        // Overlay extra de vinheta
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        // --- LAYER 3: FOREGROUND CONTENT ---
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(60.dp))
                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = "CineX Logo",
                    modifier = Modifier
                        .height(120.dp)
                        .padding(horizontal = 32.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Center: Main Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeIcon(label = "TV AO VIVO", icon = Icons.Default.LiveTv) { onNavigate(1) }
                Spacer(modifier = Modifier.width(32.dp))
                HomeIcon(label = "FILMES", icon = Icons.Default.Movie) { onNavigate(2) }
                Spacer(modifier = Modifier.width(32.dp))
                HomeIcon(label = "SÉRIES", icon = Icons.Default.Tv) { onNavigate(3) }
                Spacer(modifier = Modifier.width(32.dp))
                HomeIcon(label = "CONTA", icon = Icons.Default.Person) { showAccountDialog = true }
            }

            // Bottom Spacer
            Spacer(modifier = Modifier.height(60.dp))
        }
        
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar Lista", tint = Color.White)
            }
            IconButton(
                onClick = onServerSwap,
                modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
            ) {
                Icon(Icons.Default.Dns, contentDescription = "Trocar Servidor", tint = Color.White)
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.background(Color(0x33FFFFFF), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = Color.White)
            }
        }

        if (showAccountDialog && accountInfo != null) {
            AccountInfoDialog(
                accountInfo = accountInfo,
                onDismiss = { showAccountDialog = false }
            )
        }
    }
}

@Composable
fun AccountInfoDialog(
    accountInfo: com.cinex.player.ui.AccountInfo,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(450.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(com.cinex.player.ui.theme.CineX_SecondaryBackground)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1A000000))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONTA",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }

            Column(modifier = Modifier.padding(24.dp)) {
                AccountRow(label = "endereço MAC", value = accountInfo.macAddress)
                AccountRow(label = "Chave do dispositivo", value = accountInfo.deviceKey)
                AccountRow(label = "Estado da conta", value = accountInfo.accountStatus, valueColor = Color.Green)
                AccountRow(label = "data de validade", value = accountInfo.activationDate)
                AccountRow(label = "Data de expiração da lista de reprodução", value = accountInfo.playlistExpiration)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AccountRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.LightGray.copy(alpha = 0.8f), fontSize = 15.sp)
        Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HomePosterCard(movie: Channel, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(130.dp)
            .height(180.dp)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        AsyncImage(
            model = movie.logoUrl ?: movie.bannerUrl,
            contentDescription = movie.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun HomeIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
