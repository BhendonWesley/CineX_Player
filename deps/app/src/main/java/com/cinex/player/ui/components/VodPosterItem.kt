package com.cinex.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import android.content.res.Configuration as AndroidConfig
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.theme.CineX_SecondaryBackground
import com.cinex.player.ui.theme.DeepRed
import com.cinex.player.ui.theme.TextWhite

private val CardRed = Color(0xFFE11D2E)
private val CardGold = Color(0xFFF59E0B)

@Composable
fun VodPosterItem(
    channel: Channel,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    val gradientBrush = remember { Brush.linearGradient(listOf(CardRed, CardGold)) }
    val overlayGradient = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == AndroidConfig.UI_MODE_TYPE_TELEVISION
    }

    val showOverlay = isPressed || isFocused

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (showOverlay) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(CineX_SecondaryBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        fun String?.asValidImageUrl(): String? {
            val value = this?.trim()
            if (value.isNullOrEmpty()) return null
            if (value.equals("null", ignoreCase = true)) return null
            return value
        }

        val imageCandidates = listOfNotNull(
            channel.logoUrl.asValidImageUrl(),
            channel.posterUrl.asValidImageUrl()
        ).distinct()

        var imageIndex by remember(channel.id, imageCandidates) { mutableIntStateOf(0) }
        val imageUrl = imageCandidates.getOrNull(imageIndex)

        if (imageUrl != null) {
            val imageRequest = remember(imageUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(300)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .size(320, 480)
                    .build()
            }

            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
                success = {
                    SubcomposeAsyncImageContent()
                },
                loading = {
                    if (isTv) {
                        // Na TV, placeholder estático para evitar sobrecarga de GPU
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    } else {
                        val transition = rememberInfiniteTransition(label = "shimmer")
                        val translateAnim by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1000f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "shimmer_translate"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.05f),
                                            Color.White.copy(alpha = 0.15f),
                                            Color.White.copy(alpha = 0.05f)
                                        ),
                                        start = Offset(translateAnim - 200f, 0f),
                                        end = Offset(translateAnim, 0f)
                                    )
                                )
                        )
                    }
                },
                error = {
                    if (imageIndex < imageCandidates.lastIndex) {
                        LaunchedEffect(imageIndex, imageCandidates) {
                            imageIndex++
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(0.55f),
                                contentScale = ContentScale.Fit,
                                alpha = 0.5f
                            )
                        }
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.55f),
                    contentScale = ContentScale.Fit,
                    alpha = 0.5f
                )
            }
        }

        if (showOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(2.dp, CardGold, CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = CardGold,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(overlayGradient)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(
                        channel.groupTitle.takeIf { it.isNotBlank() },
                        channel.tmdbYear?.takeIf { it.isNotBlank() }
                    ).joinToString(" | ")
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = meta,
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                if (showProgress && channel.resumePosition > 0 && channel.totalDuration > 0) {
                    val progress = channel.resumePosition.toFloat() / channel.totalDuration.toFloat()
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = DeepRed,
                        trackColor = Color.DarkGray.copy(alpha = 0.6f),
                        strokeCap = StrokeCap.Square
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x99000000))
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
}
