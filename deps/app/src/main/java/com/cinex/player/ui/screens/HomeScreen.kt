package com.cinex.player.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ══════════════════════════════════════════════════════════════
//  CineX Home Screen — Netflix/TV-Style Landscape Interface
// ══════════════════════════════════════════════════════════════

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

    // Clock
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CineX_BackgroundBlue)
    ) {
        // ── LAYER 1: FULL-SCREEN BACKDROP ──
        HeroBackdrop(
            movies = featuredMovies,
            modifier = Modifier.fillMaxSize()
        )

        // ── LAYER 2: CONTENT OVERLAY ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── TOP: HEADER BAR ──
            HeaderBar(
                onSettingsClick = onSettingsClick,
                onRefresh = onRefresh,
                onServerSwap = onServerSwap
            )

            // ── CENTER: MOVIE INFO (left-aligned) ──
            HeroMovieInfo(
                movies = featuredMovies,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .weight(1f)
                    .padding(vertical = 16.dp)
            )

            // ── BOTTOM: NAVIGATION CARDS ROW ──
            Column {
                NavigationCardsRow(
                    onNavigate = onNavigate,
                    onAccountClick = { showAccountDialog = true }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rede Conectada",
                        color = CineX_TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = currentTime,
                        color = CineX_PremiumGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Account Dialog
        if (showAccountDialog && accountInfo != null) {
            AccountInfoDialog(
                accountInfo = accountInfo,
                onDismiss = { showAccountDialog = false }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  HERO BACKDROP — Full-Screen Auto-Rotating Backdrop
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBackdrop(
    movies: List<Channel>,
    modifier: Modifier = Modifier
) {
    val validMovies = remember(movies) {
        movies.take(8)
    }

    if (validMovies.isEmpty()) {
        Box(
            modifier = modifier.background(
                Brush.radialGradient(
                    colors = listOf(
                        CineX_DeepRed.copy(alpha = 0.15f),
                        CineX_BackgroundBlue
                    )
                )
            )
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { validMovies.size })

    // Auto-scroll every 7 seconds
    LaunchedEffect(pagerState) {
        while (true) {
            delay(7000)
            val next = (pagerState.currentPage + 1) % validMovies.size
            pagerState.animateScrollToPage(
                next,
                animationSpec = tween(1000, easing = LinearEasing)
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = false
    ) { page ->
        val movie = validMovies[page]
        val imageUrl = movie.bannerUrl?.takeIf { it.isNotEmpty() }
            ?: movie.logoUrl?.takeIf { it.isNotEmpty() }
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(
                                CineX_DeepRed.copy(alpha = 0.2f),
                                CineX_BackgroundBlue
                            )
                        )
                    )
                )
            }

            // Dark gradient overlays for readability
            // Left gradient (for text)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            ),
                            endX = 1200f
                        )
                    )
            )
            // Bottom gradient (for cards)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                CineX_BackgroundBlue.copy(alpha = 0.9f)
                            ),
                            startY = 300f
                        )
                    )
            )
            // Top gradient (for header)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            ),
                            endY = 200f
                        )
                    )
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  HERO MOVIE INFO — Left-Aligned Title + Synopsis + Metadata
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroMovieInfo(
    movies: List<Channel>,
    modifier: Modifier = Modifier
) {
    val validMovies = remember(movies) {
        movies.take(8)
    }

    if (validMovies.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            Column {
                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = "CineX",
                    modifier = Modifier.height(80.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Sincronize sua lista para ver conteúdos em destaque",
                    color = CineX_TextMuted,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { validMovies.size })

    // Sync with backdrop pager
    LaunchedEffect(Unit) {
        while (true) {
            delay(7000)
            val next = (pagerState.currentPage + 1) % validMovies.size
            pagerState.animateScrollToPage(
                next,
                animationSpec = tween(1000, easing = LinearEasing)
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = false
    ) { page ->
        val movie = validMovies[page]

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            // "ORIGINAL" pill + subtitle
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
                    text = movie.groupTitle
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

            // Movie Title (split into two colors for effect)
            Text(
                text = movie.name.uppercase(),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 40.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Synopsis
            if (!movie.tmdbSynopsis.isNullOrEmpty()) {
                Text(
                    text = movie.tmdbSynopsis,
                    color = CineX_TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Metadata Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!movie.tmdbYear.isNullOrEmpty()) {
                    MetadataPill(text = movie.tmdbYear)
                }
                val rating = movie.tmdbRating
                if (rating != null && rating > 0) {
                    MetadataPill(
                        text = "⭐ ${String.format("%.1f", rating)}",
                        highlight = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Carousel Dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                validMovies.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (isSelected) 24.dp else 8.dp,
                                height = 4.dp
                            )
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isSelected) CineX_DeepRed
                                else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  METADATA PILL
// ══════════════════════════════════════════════════════════════

@Composable
private fun MetadataPill(text: String, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                if (highlight) CineX_PremiumGold.copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (highlight) CineX_PremiumGold.copy(alpha = 0.3f)
                else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (highlight) CineX_LightGold else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ══════════════════════════════════════════════════════════════
//  HEADER BAR — Logo + Action Icons
// ══════════════════════════════════════════════════════════════

@Composable
private fun HeaderBar(
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    onServerSwap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                contentDescription = "CineX",
                modifier = Modifier.height(36.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "CINE",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text(
                text = "X",
                color = CineX_DeepRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        // Action Icons
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Search, "Buscar", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, "Config", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            }
            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CineX_PremiumGold.copy(alpha = 0.3f))
                    .border(1.5.dp, CineX_PremiumGold.copy(alpha = 0.5f), CircleShape)
                    .clickable { onServerSwap() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "Perfil", tint = CineX_LightGold, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  NAVIGATION CARDS — Horizontal Row (4 Cards)
// ══════════════════════════════════════════════════════════════

@Composable
private fun NavigationCardsRow(
    onNavigate: (Int) -> Unit,
    onAccountClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NavCard(
            icon = Icons.Default.LiveTv,
            subtitle = "AO VIVO",
            label = "TV ao Vivo",
            isActive = true,
            onClick = { onNavigate(1) },
            modifier = Modifier.weight(1f)
        )
        NavCard(
            icon = Icons.Default.Movie,
            subtitle = "CINEMA",
            label = "Filmes",
            onClick = { onNavigate(2) },
            modifier = Modifier.weight(1f)
        )
        NavCard(
            icon = Icons.Default.Tv,
            subtitle = "MARATONA",
            label = "Séries",
            onClick = { onNavigate(3) },
            modifier = Modifier.weight(1f)
        )
        NavCard(
            icon = Icons.Default.Person,
            subtitle = "PERFIL",
            label = "Conta",
            onClick = onAccountClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NavCard(
    icon: ImageVector,
    subtitle: String,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) CineX_PremiumGold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f),
        animationSpec = tween(300),
        label = "border"
    )

    Box(modifier = modifier.height(140.dp)) {
        // Glow behind active card
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .blur(20.dp)
                    .background(CineX_DeepRed.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            )
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CineX_SecondaryBackground.copy(alpha = if (isActive) 0.8f else 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = borderColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isActive) CineX_DeepRed.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isActive) CineX_HighlightRed else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Text
                Column {
                    Text(
                        text = subtitle,
                        color = CineX_TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = label,
                        color = if (isActive) Color.White else CineX_TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  ACCOUNT INFO DIALOG
// ══════════════════════════════════════════════════════════════

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
                .clip(RoundedCornerShape(16.dp))
                .background(CineX_SecondaryBackground)
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CineX_DeepRed.copy(alpha = 0.3f), Color.Transparent)
                        )
                    )
                    .padding(20.dp),
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
                AccountRow(label = "Endereço MAC", value = accountInfo.macAddress)
                AccountRow(label = "Chave do dispositivo", value = accountInfo.deviceKey)
                AccountRow(label = "Estado da conta", value = accountInfo.accountStatus, valueColor = Color.Green)
                AccountRow(label = "Data de validade", value = accountInfo.activationDate)
                AccountRow(label = "Expiração da lista", value = accountInfo.playlistExpiration)

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
fun AccountRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = CineX_TextMuted, fontSize = 14.sp)
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
