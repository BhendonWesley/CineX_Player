package com.cinex.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    featuredMovies: List<Channel>,
    isHomeReady: Boolean,
    onHomeReady: () -> Unit,
    onNavigate: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    accountInfo: com.cinex.player.ui.AccountInfo?,
    macAddress: String = "",
    modifier: Modifier = Modifier
) {
    // ESTADOS ESSENCIAIS (Corrigindo Unresolved Reference)
    var showInitialLoading by remember { mutableStateOf(!isHomeReady && featuredMovies.isEmpty()) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val validMovies = remember(featuredMovies) {
        featuredMovies.filter { !it.bannerUrl.isNullOrEmpty() && !it.bannerUrl.endsWith("null") }.take(10)
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { validMovies.size }
    )

    LaunchedEffect(pagerState, validMovies) {
        if (validMovies.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(7000)
            val next = (pagerState.currentPage + 1) % validMovies.size
            pagerState.animateScrollToPage(next, animationSpec = tween(1000, easing = LinearEasing))
        }
    }

    LaunchedEffect(featuredMovies) {
        if (isHomeReady) {
            showInitialLoading = false
            return@LaunchedEffect
        }
        if (featuredMovies.isNotEmpty()) {
            showInitialLoading = false
            onHomeReady()
        } else {
            delay(8000)
            showInitialLoading = false
            onHomeReady()
        }
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
                pagerState = pagerState,
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
                    onAccountClick = { showAccountDialog = true }
                )

                HeroMovieInfo(
                    movies = validMovies,
                    pagerState = pagerState,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .weight(1f)
                        .padding(vertical = 16.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NavigationCardsRow(
                        onNavigate = onNavigate
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (macAddress.isNotEmpty()) {
                        Text(
                            text = macAddress,
                            color = CineX_TextMuted.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBackdrop(
    movies: List<Channel>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) {
        Box(
            modifier = modifier.background(
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
        return
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = false,
        key = { page -> if (page < movies.size) movies[page].id else page }
    ) { page ->
        val movie = movies[page]
        val imageUrl = movie.bannerUrl

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

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroMovieInfo(
    movies: List<Channel>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    val currentPage = pagerState.currentPage
    val movie = remember(currentPage, movies) { 
        if (currentPage < movies.size) movies[currentPage] else null 
    }

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
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = currentMovie.groupTitle
                        .replace("MOVIE |", "")
                        .replace("SERIES |", "")
                        .trim()
                        .ifEmpty { "CineX Original" },
                    color = CineX_PremiumGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentMovie.name.uppercase(),
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 42.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            val synopsis = currentMovie.tmdbSynopsis?.trim()
            Text(
                text = if (synopsis.isNullOrEmpty()) "Explora este conteúdo incrível no CineX Player. Descubra mais detalhes assistindo agora." else synopsis,
                color = CineX_TextSecondary,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!currentMovie.tmdbYear.isNullOrEmpty()) {
                    MetadataPill(text = currentMovie.tmdbYear)
                }
                val rating = currentMovie.tmdbRating
                if (rating != null && rating > 0) {
                    MetadataPill(
                        text = "⭐ ${"%.1f".format(rating)}",
                        highlight = true
                    )
                }
                // Badge de Qualidade HD/4K sutil
                MetadataPill(text = "4K ULTRA HD", highlight = false)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // INDICADORES DE BARRA PREMIUM
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                movies.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 40.dp else 12.dp,
                        animationSpec = tween(500),
                        label = "indicator_width"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isSelected) CineX_PremiumGold
                                else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataPill(text: String, highlight: Boolean = false) {
    val backgroundColor = if (highlight) CineX_DeepRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)
    val borderColor = if (highlight) CineX_DeepRed.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.12f)
    val textColor = if (highlight) CineX_PremiumGold else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
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
            onClick = { onNavigate(2) },
            modifier = Modifier.weight(1f)
        )
        NavCard(
            icon = Icons.Default.Tv,
            label = "SÉRIES",
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
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) CineX_DeepRed else CineX_DeepRed.copy(alpha = 0.4f),
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
            .background(Color(0xAA000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A2E))
                .border(1.dp, Color(0xFF2D2D44), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141426))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONTA",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp
                )
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                AccountRow(label = "endereço MAC", value = accountInfo.macAddress)
                AccountRow(label = "Chave do dispositivo", value = accountInfo.deviceKey)
                AccountRow(label = "Estado da conta", value = accountInfo.accountStatus)
                AccountRow(label = "data de validade", value = accountInfo.activationDate)
                AccountRow(label = "Data de expiração da lista de reprodução", value = accountInfo.playlistExpiration)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CineX_DeepRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AccountRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label, 
            color = Color(0xFFAAAAAA),
            fontSize = 15.sp,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = value, 
            color = Color.White, 
            fontSize = 15.sp, 
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
