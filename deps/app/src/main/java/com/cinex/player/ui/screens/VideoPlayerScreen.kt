package com.cinex.player.ui.screens

import android.app.Activity
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DeepRed
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    
    val isLiveTv = channel.category == "LIVE_TV"
    
    val localPlayer = remember {
        if (!isLiveTv) {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
            }
        } else null
    }

    val exoPlayer = if (isLiveTv) viewModel.liveTvPlayer else localPlayer!!

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    
    var currentVolume by remember { 
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }
    var currentBrightness by remember { 
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f)
    }

    var showResumeDialog by remember { mutableStateOf(channel.resumePosition > 0L && !isLiveTv) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(channel) {
        if (!isLiveTv) {
            val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            
            if (showResumeDialog) {
                exoPlayer.seekTo(channel.resumePosition)
            } else {
                exoPlayer.play()
            }
        }
        // Para Live TV, o player já deve estar tocando do preview
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isLiveTv) {
                val currentPos = exoPlayer.currentPosition
                val totalDur = exoPlayer.duration
                if (currentPos > 10000) {
                    viewModel.saveResumePosition(channel.id, currentPos, if (totalDur > 0) totalDur else 0L)
                }
                exoPlayer.release()
            }
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !showResumeDialog && !showExitDialog) {
            delay(5000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (showResumeDialog || showExitDialog) return@detectVerticalDragGestures
                    isControlsVisible = true
                    val isLeftHalf = change.position.x < (size.width / 2)
                    if (isLeftHalf) {
                        val newBrightness = (currentBrightness - (dragAmount / size.height)).coerceIn(0f, 1f)
                        currentBrightness = newBrightness
                        activity?.let {
                            val lp = it.window.attributes
                            lp.screenBrightness = newBrightness
                            it.window.attributes = lp
                        }
                    } else {
                        val newVolume = (currentVolume - (dragAmount / size.height)).coerceIn(0f, 1f)
                        currentVolume = newVolume
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (newVolume * maxVol).toInt(), 0)
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { 
                if (!showResumeDialog && !showExitDialog) {
                    isControlsVisible = !isControlsVisible 
                }
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false 
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { 
                it.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )
        
        AnimatedVisibility(
            visible = isControlsVisible && !showResumeDialog && !showExitDialog,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000))) {
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        if (isLiveTv) onBack() else showExitDialog = true 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Text(
                        text = channel.name.uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Mostrar info se houver */ }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        
                        var isFav by remember { mutableStateOf(channel.isFavorite) }
                        IconButton(onClick = { 
                            isFav = !isFav
                            viewModel.updateFavorite(channel.id, isFav)
                        }) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = if (isFav) Color.Red else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        IconButton(onClick = {
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) 
                                AspectRatioFrameLayout.RESIZE_MODE_FILL 
                            else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exoPlayer.seekBack() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                    }

                    IconButton(
                        onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    IconButton(onClick = { exoPlayer.seekForward() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                }

                // Sliders de Brilho (Esquerda) e Volume (Direita) — Grandes, como app profissional
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 80.dp) // Padding do topo/base para não sobrepor botões
                ) {
                    // Brilho (Esquerda)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.LightMode, contentDescription = "Brilho", tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight(0.75f)
                                .width(48.dp)
                        ) {
                            Slider(
                                value = currentBrightness,
                                onValueChange = {
                                    currentBrightness = it
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = it
                                        act.window.attributes = lp
                                    }
                                },
                                modifier = Modifier
                                    .requiredWidth(500.dp)
                                    .height(48.dp)
                                    .graphicsLayer { rotationZ = -90f },
                                colors = SliderDefaults.colors(
                                    thumbColor = DeepRed,
                                    activeTrackColor = DeepRed,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    // Volume (Direita)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight(0.75f)
                                .width(48.dp)
                        ) {
                            Slider(
                                value = currentVolume,
                                onValueChange = {
                                    currentVolume = it
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).toInt(), 0)
                                },
                                modifier = Modifier
                                    .requiredWidth(500.dp)
                                    .height(48.dp)
                                    .graphicsLayer { rotationZ = -90f },
                                colors = SliderDefaults.colors(
                                    thumbColor = DeepRed,
                                    activeTrackColor = DeepRed,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(thumbColor = DeepRed, activeTrackColor = DeepRed)
                    )
                    Text(formatTime(duration), color = Color.White, fontSize = 14.sp)
                }
            }
        }

        if (showResumeDialog) {
            PlayerDialog(
                title = "Retomar",
                description = "Deseja retomar a reprodução de onde parou?",
                onConfirm = {
                    showResumeDialog = false
                    exoPlayer.play()
                },
                onCancel = {
                    showResumeDialog = false
                    exoPlayer.seekTo(0)
                    exoPlayer.play()
                }
            )
        }

        if (showExitDialog) {
            PlayerDialog(
                title = "Parar reprodução?",
                description = "Clique em SIM para sair da reprodução. Clique em NÃO para cancelar",
                onConfirm = { onBack() },
                onCancel = { showExitDialog = false }
            )
        }
    }
}

@Composable
fun PlayerDialog(
    title: String,
    description: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(com.cinex.player.ui.theme.CineX_SecondaryBackground, RoundedCornerShape(12.dp))
                .border(2.dp, com.cinex.player.ui.theme.CineX_DeepRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = description,
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(com.cinex.player.ui.theme.DeepRed, RoundedCornerShape(8.dp))
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SIM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
