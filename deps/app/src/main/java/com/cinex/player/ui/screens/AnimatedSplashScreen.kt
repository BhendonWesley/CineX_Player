package com.cinex.player.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cinex.player.R
import com.cinex.player.ui.theme.*

@Composable
fun AnimatedSplashScreen() {
    val logoAlpha   = remember { Animatable(0f) }
    val logoScale   = remember { Animatable(0.95f) }
    val taglineAlpha  = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo — fade + scale suave simultâneos
        launch { logoScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        logoAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))

        // Textos em cascata
        taglineAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        subtitleAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    // Glow pulsante sutil
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue  = 0.5f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Glow cinematográfico — Canvas fullscreen, sempre circular
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B0000).copy(alpha = 0.5f * glowAlpha),
                        Color(0xFF8B0000).copy(alpha = 0.15f * glowAlpha),
                        Color(0xFF1A0A0A).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.6f
                ),
                center = center,
                radius = size.minDimension * 0.6f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_cinex),
                contentDescription = "CineX",
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        this.alpha  = logoAlpha.value
                        this.scaleX = logoScale.value
                        this.scaleY = logoScale.value
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tagline principal
            Text(
                text = "O CINEMA COMEÇA AQUI...",
                color = CineX_LightGold.copy(alpha = 0.85f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(taglineAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = "Inicializando sua experiência...",
                color = CineX_TextSecondary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )
        }
    }
}
