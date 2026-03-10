package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.DarkBackground

@Composable
fun MovieDetailsScreen(
    movie: Channel,
    onBack: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Backdrop em tela cheia com Scrim
        AsyncImage(
            model = movie.bannerUrl ?: movie.logoUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DarkBackground),
                        startY = 0f
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(DarkBackground, Color.Transparent),
                        endX = 1000f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Poster à esquerda
                AsyncImage(
                    model = movie.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(2/3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(32.dp))

                // Informações à direita
                Column(modifier = Modifier.weight(1f)) {
                    // Título
                    Text(
                        text = "${movie.name.uppercase()}${if (movie.tmdbYear != null) " (${movie.tmdbYear})" else ""}",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 36.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Metadados + Estrelas + Rating (Linha Única para economizar espaço)
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = movie.tmdbYear ?: "N/A", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "|", color = Color.DarkGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = movie.groupTitle, color = Color.Gray, fontSize = 14.sp)
                        
                        Spacer(modifier = Modifier.width(24.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(5) { index ->
                                val rating = (movie.tmdbRating ?: 0.0) / 2
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (index < rating.toInt()) Color.Yellow else Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format("%.1f", movie.tmdbRating ?: 0.0),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "ADICIONADO EM: 08/03/2026",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Botões de Ação
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { onPlay(movie) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ASSISTA AGORA", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        
                        if (movie.trailerUrl != null) {
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            TextButton(
                                onClick = { 
                                    movie.trailerUrl.let { url ->
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TRAILER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sinopse
                    Text(
                        text = movie.tmdbSynopsis ?: "Sem sinopse disponível.",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 6, // Limita para garantir que caiba caso não role
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )

                    if (!movie.castMembers.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Elenco", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val castList = movie.castMembers.split(", ").take(6)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(castList) { actor ->
                                CastItem(name = actor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CastItem(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = name, color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
