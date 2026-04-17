package com.cinex.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
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
import coil.compose.AsyncImage
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
    onFocused: ((Channel) -> Unit)? = null,
    onPrefetch: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    // Prefetch ao focar por 400ms (TV): pré-carrega episódios antes do clique
    LaunchedEffect(isFocused) {
        if (isFocused && onPrefetch != null) {
            kotlinx.coroutines.delay(400L)
            if (isFocused) onPrefetch()
        }
    }

    val gradientBrush = remember { Brush.linearGradient(listOf(CardRed, CardGold)) }
    val overlayGradient = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == AndroidConfig.UI_MODE_TYPE_TELEVISION
    }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val cellPx = remember(screenWidthDp, isTv) {
        if (isTv) 200
        else {
            val cellDp = (screenWidthDp - 24 - 24) / 4
            ((cellDp / 8 + 1) * 8)
        }
    }
    val cellHeightPx = remember(cellPx) { (cellPx * 1.5f).toInt() }

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "vodFocusScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (isTv) 4.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        onFocused?.invoke(channel)
                    }
                }
                .focusable(interactionSource = interactionSource)
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
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                    shape = RoundedCornerShape(12.dp)
                    clip = true
                }
                .then(
                    if (isFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .background(CineX_SecondaryBackground)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
        val imageCandidates = remember(channel.id, channel.logoUrl, channel.posterUrl) {
            fun String?.asValidImageUrl(): String? {
                val value = this?.trim()
                if (value.isNullOrEmpty()) return null
                if (value.equals("null", ignoreCase = true)) return null
                return value
            }
            listOfNotNull(
                channel.logoUrl.asValidImageUrl(),
                channel.posterUrl.asValidImageUrl()
            ).distinct()
        }

        var imageIndex by remember(channel.id) { mutableIntStateOf(0) }
        val imageUrl = imageCandidates.getOrNull(imageIndex)

        if (imageUrl != null) {
            val imageRequest = remember(imageUrl, isTv) {
                coil.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(if (isTv) 0 else 120)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .size(cellPx, cellHeightPx)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                error = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                onError = {
                    if (imageIndex < imageCandidates.lastIndex) imageIndex++
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

        if (isFocused && !isTv) {
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
}
