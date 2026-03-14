package com.cinex.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.CineX_SecondaryBackground
import com.cinex.player.ui.theme.TextWhite

@Composable
fun VodPosterItem(
    channel: Channel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f) // Proporção padrão de poster vertical de filmes
            .clip(RoundedCornerShape(8.dp))
            .background(com.cinex.player.ui.theme.CineX_SecondaryBackground)
            .clickable { onClick() }
    ) {
        // Prioritizing List Cover (logoUrl) over TMDB (posterUrl) as requested by the user
        val imageUrl = channel.logoUrl?.takeIf { it.isNotEmpty() } ?: channel.posterUrl
        
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = channel.name,
                contentScale = if (channel.posterUrl.isNullOrEmpty()) ContentScale.Fit else ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = com.cinex.player.R.drawable.logo_cinex)
            )
        } else {
            // Fallback se não tiver nenhuma imagem no banco
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name,
                    color = TextWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Título na parte inferior estilo o layout de referência
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Barra de Progresso Vermelha (estilo Netflix)
            if (channel.resumePosition > 0 && channel.totalDuration > 0) {
                val progress = channel.resumePosition.toFloat() / channel.totalDuration.toFloat()
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = com.cinex.player.ui.theme.DeepRed,
                    trackColor = Color.Transparent,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000)) // Fundo semi-transparente
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = channel.name,
                    color = TextWhite,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
