package com.cinex.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.ImageResult
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen(
    featuredMovies: List<Channel>,
    isHomeReady: Boolean,
    onHomeReady: () -> Unit,
    onNavigate: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    accountInfo: com.cinex.player.ui.AccountInfo?,
    onAccountOpen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ESTADOS ESSENCIAIS (Corrigindo Unresolved Reference)
    var showInitialLoading by remember { mutableStateOf(!isHomeReady) }
    var isFirstBannerReady by remember { mutableStateOf(isHomeReady) }
    var showAccountDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val validMovies = remember(featuredMovies) {
        featuredMovies
            .filter { !it.bannerUrl.isNullOrEmpty() && !it.bannerUrl.endsWith("null") }
            .distinctBy { it.seriesName ?: cleanSeriesTitle(it.name) }
            .take(10)
    }

    // Auto-scroll do carousel
    var currentIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(validMovies.size) {
        if (validMovies.size <= 1) return@LaunchedEffect
        while (true) {
            delay(8000)
            currentIndex = (currentIndex + 1) % validMovies.size
        }
    }

    LaunchedEffect(featuredMovies) {
        // Se já estava pronto antes (re-composição), sai direto
        if (isHomeReady && isFirstBannerReady) {
            showInitialLoading = false
            return@LaunchedEffect
        }

        if (validMovies.isNotEmpty()) {
            // Pré-carrega os primeiros banners em paralelo (não sequencial)
            val bannersToPreload = validMovies.take(3).mapNotNull { it.bannerUrl }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.coroutineScope {
                    bannersToPreload.map { url ->
                        async {
                            try {
                                val request = ImageRequest.Builder(context)
                                    .data(url)
                                    .size(coil.size.Size.ORIGINAL)
                                    .build()
                                context.imageLoader.execute(request)
                            } catch (_: Exception) {}
                        }
                    }.awaitAll()
                }
            }
            isFirstBannerReady = true
            showInitialLoading = false
            onHomeReady()
        }
        // Sem timeout — a LoadingScreen fica visível até os banners carregarem
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CineX_BackgroundBlue)
    ) {
        if (showInitialLoading) {
            LoadingScreen(statusMessage = "Preparando sua biblioteca...")
        } else {
            HeroBackdrop(
                movies = validMovies,
                currentIndex = currentIndex,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                HeaderBar(
                    onSettingsClick = onSettingsClick,
                    onRefresh = onRefresh,
                    onAccountClick = {
                        onAccountOpen()
                        showAccountDialog = true
                    }
                )

                HeroMovieInfo(
                    movies = validMovies,
                    currentIndex = currentIndex,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .weight(1f)
                        .padding(vertical = 16.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NavigationCardsRow(
                        onNavigate = onNavigate
                    )
                }
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
private fun HeroBackdrop(
    movies: List<Channel>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (movies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CineX_BackgroundBlue, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            ) {
                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(120.dp).alpha(0.1f),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            val movie = movies.getOrNull(currentIndex) ?: movies.first()

            AnimatedContent(
                targetState = movie,
                transitionSpec = {
                    fadeIn(animationSpec = tween(1500)) togetherWith
                        fadeOut(animationSpec = tween(1500))
                },
                modifier = Modifier.fillMaxSize(),
                label = "hero_backdrop"
            ) { currentMovie ->
                val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(12000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "zoom"
                )
                val translationX by infiniteTransition.animateFloat(
                    initialValue = -20f,
                    targetValue = 20f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(15000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pan"
                )

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentMovie.bannerUrl)
                        .crossfade(true)
                        .crossfade(800)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = translationX
                        ),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Sombras Estáticas (Ficam acima do Pager para não "descolar" na transição)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f),
                            CineX_BackgroundBlue.copy(alpha = 0.6f),
                            CineX_BackgroundBlue
                        ),
                        startY = 200f
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        endX = 1400f
                    )
                )
        )
    }
}

@Composable
private fun HeroMovieInfo(
    movies: List<Channel>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    val movie = movies.getOrNull(currentIndex)

    AnimatedContent(
        targetState = movie,
        transitionSpec = {
            fadeIn(animationSpec = tween(600)) + slideInHorizontally(
                animationSpec = tween(600),
                initialOffsetX = { 20 }
            ) togetherWith fadeOut(animationSpec = tween(400))
        },
        modifier = modifier,
        label = "hero_info"
    ) { currentMovie ->
        if (currentMovie == null) return@AnimatedContent

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(CineX_DeepRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "DESTAQUE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                val group = currentMovie.groupTitle
                    .replace("MOVIE |", "")
                    .replace("SERIES |", "")
                    .trim()
                
                if (group.isNotEmpty() && group != "Episódios" && group != "Episodes") {
                    Text(
                        text = group.uppercase(),
                        color = CineX_PremiumGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (currentMovie.category == "SERIES") {
                    Text(
                        text = "SÉRIE",
                        color = CineX_PremiumGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = (currentMovie.seriesName ?: cleanSeriesTitle(currentMovie.name)).uppercase(),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 34.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val synopsis = currentMovie.tmdbSynopsis?.trim()
            if (!synopsis.isNullOrEmpty()) {
                Text(
                    text = synopsis,
                    color = CineX_TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
            
        }
    }
}

@Composable
private fun HeaderBar(
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    onAccountClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                contentDescription = "CineX",
                modifier = Modifier.height(42.dp),
                contentScale = ContentScale.Fit
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CineX_DeepRed)
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A))
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Settings, "Config", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CineX_PremiumGold)
                    .clickable { onAccountClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "Perfil", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NavigationCardsRow(
    onNavigate: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavCard(
            icon = Icons.Default.LiveTv,
            label = "TV AO VIVO",
            isActive = true,
            onClick = { onNavigate(1) },
            modifier = Modifier.weight(1f)
        )
        NavCard(
            icon = Icons.Default.Movie,
            label = "FILMES",
            isActive = true,
            onClick = { onNavigate(2) },
            modifier = Modifier.weight(1f)
        )
        NavCard(
            icon = Icons.Default.Tv,
            label = "SÉRIES",
            isActive = true,
            onClick = { onNavigate(3) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NavCard(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                color = if (isActive) Color.White.copy(alpha = 0.10f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 0.8.dp,
                color = if (isActive) CineX_HighlightRed else CineX_DeepRed.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isActive) Color.White.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) CineX_PremiumGold else CineX_PremiumGold.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
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
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CineX_BackgroundBlue)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
        ) {
            // Header do Dialog
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CineX_DeepRed, CineX_DeepRed.copy(alpha = 0.8f))
                        )
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONTA",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                AccountRow(label = "ENDEREÇO MAC", value = accountInfo.macAddress)
                AccountRow(label = "CHAVE DO DISPOSITIVO", value = accountInfo.deviceKey)
                AccountRow(label = "ESTADO DA CONTA", value = accountInfo.accountStatus, isHighlight = true)
                AccountRow(label = "DATA DE ATIVAÇÃO", value = accountInfo.activationDate)
                AccountRow(label = "DATA DE VALIDADE", value = accountInfo.playlistExpiration)

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CineX_DeepRed.copy(alpha = 0.5f))
                ) {
                    Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun AccountRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = CineX_TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
        Text(
            text = value.uppercase(),
            color = if (isHighlight) CineX_PremiumGold else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}

private fun cleanSeriesTitle(title: String): String {
    // Regex mais abrangente para capturar variações de Temporada e Episódio
    val patterns = listOf(
        Regex("\\s+[ST]\\d+.*", RegexOption.IGNORE_CASE), // S01, T01
        Regex("\\s+E\\d+.*", RegexOption.IGNORE_CASE),   // E01
        Regex("\\s+CAP\\.?.*", RegexOption.IGNORE_CASE), // CAP, CAP.
        Regex("\\s+TEMP\\.?.*", RegexOption.IGNORE_CASE) // TEMP, TEMP.
    )
    var cleanedTitle = title
    patterns.forEach { regex ->
        cleanedTitle = cleanedTitle.replace(regex, "")
    }
    return cleanedTitle.trim()
}
