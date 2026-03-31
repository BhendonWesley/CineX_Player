package com.cinex.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.DarkBackground

private val FocusRed = Color(0xFFE11D2E)
private val FocusGold = Color(0xFFF59E0B)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MovieDetailsScreen(
    movie: Channel,
    onBack: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val context = LocalContext.current
    val playButtonRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { playButtonRequester.requestFocus() } catch (e: Exception) {}
    }

    BackHandler { onBack() }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {}
    ) {

        // Backdrop com gradientes cinematográficos
        AsyncImage(
            model = movie.bannerUrl ?: movie.logoUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, DarkBackground), startY = 0f)
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(DarkBackground, Color.Transparent), endX = 1200f)
            )
        )

        val isTv = remember {
            val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.focusProperties { up = FocusRequester.Cancel }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }

            if (isTv) {
                // === LAYOUT TV (horizontal) ===
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 60.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = movie.posterUrl?.takeIf { it.isNotEmpty() } ?: movie.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text(
                            text = movie.name.replace(Regex("(?i)\\s*\\(\\d{4}\\)\\s*"), " ").trim().uppercase(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { index ->
                                    val rating = (movie.tmdbRating ?: 0.0) / 2
                                    Icon(Icons.Default.Star, contentDescription = null, tint = if (index < rating.toInt()) Color.Yellow else Color.DarkGray, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(String.format("%.1f", movie.tmdbRating ?: 0.0), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("•", color = Color.Gray)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(movie.tmdbYear ?: "N/A", color = Color.Gray, fontSize = 16.sp)
                            if (!movie.groupTitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("•", color = Color.Gray)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(movie.groupTitle, color = Color.Gray, fontSize = 16.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(180.dp)
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            val scrollState = rememberScrollState()
                            Text(
                                text = movie.tmdbSynopsis ?: "Sem sinopse disponível.",
                                color = Color.LightGray, fontSize = 14.sp, lineHeight = 21.sp,
                                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(12.dp)
                            )
                            if (scrollState.canScrollForward) {
                                Box(modifier = Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, DarkBackground.copy(alpha = 0.9f)))))
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val gradientBrush = remember { Brush.linearGradient(listOf(FocusRed, FocusGold)) }
                            var isPlayFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { onPlay(movie) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(52.dp).focusRequester(playButtonRequester)
                                    .focusProperties { down = FocusRequester.Cancel; left = FocusRequester.Cancel }
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .then(if (isPlayFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp)) else Modifier),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ASSISTA AGORA", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            var isTrailerFocused by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = {
                                    val searchQuery = java.net.URLEncoder.encode("${movie.name} trailer oficial", "UTF-8")
                                    val searchUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
                                    val youtubeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri).apply { setPackage("com.google.android.youtube") }
                                    try { context.startActivity(youtubeIntent) } catch (e: android.content.ActivityNotFoundException) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri)) }
                                },
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isTrailerFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(52.dp).width(140.dp)
                                    .focusProperties { down = FocusRequester.Cancel; right = FocusRequester.Cancel }
                                    .onFocusChanged { isTrailerFocused = it.isFocused }
                                    .then(if (isTrailerFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp)) else Modifier)
                            ) {
                                Text("TRAILER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                // === LAYOUT MOBILE (horizontal como TV, ajustado pra caber) ===
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = movie.posterUrl?.takeIf { it.isNotEmpty() } ?: movie.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text(
                            text = movie.name.replace(Regex("(?i)\\s*\\(\\d{4}\\)\\s*"), " ").trim().uppercase(),
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { index ->
                                    val rating = (movie.tmdbRating ?: 0.0) / 2
                                    Icon(Icons.Default.Star, contentDescription = null, tint = if (index < rating.toInt()) Color.Yellow else Color.DarkGray, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(String.format("%.1f", movie.tmdbRating ?: 0.0), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("•", color = Color.Gray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(movie.tmdbYear ?: "N/A", color = Color.Gray, fontSize = 14.sp)
                            if (!movie.groupTitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("•", color = Color.Gray)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(movie.groupTitle, color = Color.Gray, fontSize = 13.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            val scrollState = rememberScrollState()
                            Text(
                                text = movie.tmdbSynopsis ?: "Sem sinopse disponível.",
                                color = Color.LightGray, fontSize = 13.sp, lineHeight = 19.sp,
                                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(10.dp)
                            )
                            if (scrollState.canScrollForward) {
                                Box(modifier = Modifier.fillMaxWidth().height(24.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, DarkBackground.copy(alpha = 0.9f)))))
                            }
                        }

                        Row(
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onPlay(movie) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ASSISTIR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val searchQuery = java.net.URLEncoder.encode("${movie.name} trailer oficial", "UTF-8")
                                    val searchUri = android.net.Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
                                    val youtubeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri).apply { setPackage("com.google.android.youtube") }
                                    try { context.startActivity(youtubeIntent) } catch (e: android.content.ActivityNotFoundException) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, searchUri)) }
                                },
                                border = BorderStroke(2.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp).width(110.dp)
                            ) {
                                Text("TRAILER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
