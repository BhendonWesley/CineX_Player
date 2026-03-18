package com.cinex.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.ui.theme.DarkBackground
import com.cinex.player.ui.theme.DeepRed
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(
    statusMessage: String = "Preparando o catálogo...",
    modifier: Modifier = Modifier
) {
    val phrases = remember {
        listOf(
            "Carregando seus conteúdos...",
            "Preparando o catálogo...",
            "Organizando seus filmes...",
            "Sincronizando as séries...",
            "Ajustando os canais ao vivo...",
            "Buscando as capas oficiais...",
            "Quase lá! Deixando tudo pronto para você..."
        )
    }

    var currentPhrase by remember { mutableStateOf(statusMessage) }

    // Efeito para frases rotativas internas
    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            delay(3000)
            currentPhrase = phrases[index]
            index = (index + 1) % phrases.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Background image
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.bg_loading),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )

        // Dark overlay for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.7f)),
                        center = Offset.Unspecified,
                        radius = 2000f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Premium Golden Ring + Logo
            Box(contentAlignment = Alignment.Center) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFD36B).copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                )

                // Animated Ring
                val infiniteTransition = rememberInfiniteTransition(label = "premium_loading")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ring_rotation"
                )

                androidx.compose.foundation.Canvas(modifier = Modifier.size(170.dp)) {
                    val strokeWidth = 5.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    
                    // Trace ring
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius,
                        style = Stroke(width = strokeWidth)
                    )

                    // Golden Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFFF59E0B), Color(0xFFFFD36B), Color(0xFFF59E0B)),
                            center = Offset(size.width / 2, size.height / 2)
                        ),
                        startAngle = rotation,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
                    )
                }

                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = "CineX Logo",
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Rotating Status Message
            AnimatedContent(
                targetState = currentPhrase,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(600)) + slideInVertically { it / 2 })
                        .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutVertically { -it / 2 })
                },
                label = "status_anim"
            ) { msg ->
                Text(
                    text = msg.uppercase(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Aguarde enquanto preparamos sua experiência CineX",
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
