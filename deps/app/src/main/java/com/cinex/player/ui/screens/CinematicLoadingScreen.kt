package com.cinex.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.R

@Composable
fun CinematicLoadingScreen(
    statusMessage: String = "Sintonizando canais",
    tvProgress: Float = 0.78f,
    moviesProgress: Float = 0.45f,
    seriesProgress: Float = 1.0f
) {
    // Background gradient: #282B30 -> #1E2125 -> #15171A (Cor padrão atual do app)
    val bgGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF282B30),
            Color(0xFF1E2125),
            Color(0xFF15171A)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.bg_loading),
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT SIDE: Logo Area
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Radial glow exactly behind the main logo
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFD36B).copy(alpha = 0.15f), Color.Transparent)
                                )
                            )
                            .blur(60.dp)
                    )

                    // Glowing Loading Ring Animation
                    val infiniteTransition = rememberInfiniteTransition(label = "ring_transition")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ring_rotation"
                    )

                    Canvas(modifier = Modifier.size(170.dp)) {
                        val strokeWidth = 6.dp.toPx()
                        val innerRadius = (size.minDimension - strokeWidth) / 2
                        
                        // Faint trace ring inside
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            radius = innerRadius,
                            style = Stroke(width = strokeWidth)
                        )

                        // Animated glow gradient ring
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFFF59E0B), Color(0xFFFFD36B), Color(0xFFF59E0B)),
                                center = Offset(size.width / 2, size.height / 2)
                            ),
                            startAngle = rotation,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            size = Size(innerRadius * 2, innerRadius * 2),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.logo_cinex),
                        contentDescription = "CineX Logo",
                        modifier = Modifier.size(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Rotating Status Message with Smooth Transition
                AnimatedContent(
                    targetState = statusMessage,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(600)) + slideInVertically(animationSpec = tween(600)) { height -> height / 2 })
                            .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutVertically(animationSpec = tween(400)) { height -> -height / 2 })
                    },
                    label = "status_rotation",
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { targetMessage ->
                    Text(
                        text = targetMessage,
                        color = Color(0xFFE5E7EB),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 380.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Aguarde enquanto preparamos seu portal",
                    color = Color(0xFF9CA3AF).copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(48.dp))

            // RIGHT SIDE: Loading Status Cards
            Column(
                modifier = Modifier.weight(1f).padding(end = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProgressCard(title = "TV AO VIVO", progress = tvProgress)
                ProgressCard(title = "FILMES", progress = moviesProgress)
                ProgressCard(title = "SÉRIES", progress = seriesProgress)
            }
        }
    }
}

@Composable
fun ProgressCard(title: String, progress: Float) {
    Surface(
        color = Color(0xFFFFFFFF).copy(alpha = 0.04f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color(0xFFFFD36B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (progress >= 1f) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Complete",
                            tint = Color(0xFFFFD36B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar Container with Glass effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.15f))
            ) {
                // Glow layer (softly blurred beneath)
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .fillMaxHeight()
                            .blur(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFE11D2E).copy(alpha=0.6f), Color(0xFFF59E0B).copy(alpha=0.6f))
                                )
                            )
                    )
                }

                // Solid Gradient Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))
                            )
                        )
                )
            }
        }
    }
}
