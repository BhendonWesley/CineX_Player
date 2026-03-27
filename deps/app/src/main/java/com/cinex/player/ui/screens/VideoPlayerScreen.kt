package com.cinex.player.ui.screens

import android.app.Activity
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import com.cinex.player.data.model.Channel
import kotlinx.coroutines.delay
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DeepRed

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    channel: Channel,
    onBack: () -> Unit,
    onPlayNext: (Channel) -> Unit = {},
    onPreviousChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    
    // Ativa modo imersivo (tela cheia real, sem barras de sistema)
    DisposableEffect(activity) {
        activity?.let { act ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                act.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                act.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        }
        onDispose {
            activity?.let { act ->
                // Restaura brilho automático
                val lp = act.window.attributes
                lp.screenBrightness = -1f
                act.window.attributes = lp
                // Re-aplica modo imersivo (MainActivity já esconde as barras, garantimos que continua)
                val controller = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isLiveTv = channel.category == "LIVE_TV"
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    val exoPlayer = if (isLiveTv) viewModel.liveTvPlayer else viewModel.vodPlayer

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(false) }
    // Live TV: FIT para não cortar a imagem. VOD: FILL para preencher tela
    var resizeMode by remember { mutableIntStateOf(
        if (isLiveTv) AspectRatioFrameLayout.RESIZE_MODE_FIT
        else AspectRatioFrameLayout.RESIZE_MODE_FILL
    ) }
    
    var currentVolume by remember { 
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }
    var currentBrightness by remember {
        val windowBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
        val systemBrightness = try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } catch (_: Exception) { 0.5f }
        mutableFloatStateOf(windowBrightness ?: systemBrightness)
    }

    // Aplica o brilho inicial na janela para garantir que o slider tenha efeito
    LaunchedEffect(Unit) {
        activity?.let { act ->
            if (act.window.attributes.screenBrightness < 0) {
                val lp = act.window.attributes
                lp.screenBrightness = currentBrightness
                act.window.attributes = lp
            }
        }
    }

    var showInfoPanel by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(channel.resumePosition > 0L && !isLiveTv) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showPlaybackError by remember(channel.id) { mutableStateOf(false) }
    var isVideoEnded by remember(channel.id) { mutableStateOf(false) }
    var showNextEpisodeOverlay by remember(channel.id) { mutableStateOf(false) }
    var nextEpisode by remember(channel.id) { mutableStateOf<Channel?>(null) }

    // Pré-busca o próximo episódio assim que o vídeo começa (para ter pronto)
    LaunchedEffect(channel.id) {
        if (channel.category == "SERIES") {
            nextEpisode = viewModel.getNextEpisode(channel)
        }
    }

    // Detecta fim do vídeo e erros de reprodução
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !isLiveTv) {
                    isVideoEnded = true
                    showNextEpisodeOverlay = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (!isLiveTv) {
                    showPlaybackError = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Detecção de créditos: mostra overlay quando faltam ~90s ou 95% do vídeo
    LaunchedEffect(channel.id) {
        if (isLiveTv || channel.category != "SERIES") return@LaunchedEffect
        while (true) {
            delay(2000)
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 120_000 && pos > 0) { // só se vídeo tem mais de 2 min
                val remaining = dur - pos
                val percentage = pos.toFloat() / dur.toFloat()
                if (remaining <= 90_000 || percentage >= 0.95f) {
                    if (nextEpisode != null && !showNextEpisodeOverlay && !isVideoEnded) {
                        showNextEpisodeOverlay = true
                    }
                    break
                }
            }
        }
    }

    LaunchedEffect(channel) {
        if (!isLiveTv) {
            val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
            exoPlayer.setMediaItem(mediaItem)

            if (showResumeDialog) {
                // Pausa ANTES de preparar para não auto-tocar atrás do dialog
                exoPlayer.playWhenReady = false
                exoPlayer.prepare()
                exoPlayer.seekTo(channel.resumePosition)
            } else {
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
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
                // Singleton — não dá release, só limpa o media
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }
    }

    // Auto-hide dos controles após 5 segundos
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible && !showResumeDialog && !showExitDialog) {
            delay(5000)
            isControlsVisible = false
        }
    }

    // Só pollar posição quando os controles estão visíveis e não é Live TV
    LaunchedEffect(isControlsVisible, isLiveTv) {
        if (isLiveTv) return@LaunchedEffect
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            if (!isControlsVisible) break
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

    val playerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Quando um dialog está visível, não consumir eventos — deixar o dialog tratar
                if (showResumeDialog || showExitDialog || showPlaybackError || showNextEpisodeOverlay) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        isControlsVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!isLiveTv) exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10_000))
                        isControlsVisible = true
                        true
                    }
                    Key.DirectionRight -> {
                        if (!isLiveTv) exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000))
                        isControlsVisible = true
                        true
                    }
                    Key.DirectionUp -> {
                        onNextChannel?.invoke()
                        true
                    }
                    Key.DirectionDown -> {
                        onPreviousChannel?.invoke()
                        true
                    }
                    Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            }
            .then(
                if (!isTv) Modifier.pointerInput(Unit) {
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
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { 
                if (!showResumeDialog && !showExitDialog) {
                    isControlsVisible = !isControlsVisible 
                }
            }
    ) {
        val fullscreenPlayerViewRef = remember { mutableStateOf<PlayerView?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                // Desconecta o player da surface fullscreen para que o preview reclaim imediatamente
                if (isLiveTv) {
                    fullscreenPlayerViewRef.value?.player = null
                    viewModel.refreshLiveTvSurface()
                }
            }
        }

        AndroidView(
            factory = { context ->
                val view = android.view.LayoutInflater.from(context)
                    .inflate(com.cinex.player.R.layout.player_view_texture, null, false) as PlayerView
                view.apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    fullscreenPlayerViewRef.value = this
                }
            },
            update = {
                it.resizeMode = resizeMode
                if (it.player !== exoPlayer) it.player = exoPlayer
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
                        IconButton(onClick = { showInfoPanel = !showInfoPanel }) {
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
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL)
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            else AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }) {
                            Icon(
                                imageVector = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) Icons.Default.FitScreen else Icons.Default.Fullscreen,
                                contentDescription = "Tela Cheia", tint = Color.White, modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLiveTv) {
                        // Live TV: canal anterior
                        if (onPreviousChannel != null) {
                            IconButton(onClick = onPreviousChannel, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Canal anterior", tint = Color.White, modifier = Modifier.fillMaxSize())
                            }
                        }

                        IconButton(
                            onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Live TV: próximo canal
                        if (onNextChannel != null) {
                            IconButton(onClick = onNextChannel, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Próximo canal", tint = Color.White, modifier = Modifier.fillMaxSize())
                            }
                        }
                    } else {
                        // VOD: seek -10s / play / seek +10s
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
                }

                // Sliders de Brilho (Esquerda) e Volume (Direita) — somente mobile
                if (!isTv) BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val sliderHeight = maxHeight * 0.45f

                    // Brilho (Esquerda)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.LightMode,
                            contentDescription = "Brilho",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(sliderHeight)
                                .width(44.dp)
                        ) {
                            VerticalSlider(
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
                                    .fillMaxHeight()
                                    .width(44.dp),
                                thumbColor = DeepRed,
                                activeTrackColor = DeepRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Volume (Direita)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(sliderHeight)
                                .width(44.dp)
                        ) {
                            VerticalSlider(
                                value = currentVolume,
                                onValueChange = {
                                    currentVolume = it
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).toInt(), 0)
                                },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(44.dp),
                                thumbColor = DeepRed,
                                activeTrackColor = DeepRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Barra de progresso (só para VOD)
                if (!isLiveTv) {
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
        }

        // Painel de informações (estilo Smart TV)
        AnimatedVisibility(
            visible = showInfoPanel,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Auto-hide após 5 segundos
            LaunchedEffect(showInfoPanel) {
                if (showInfoPanel) {
                    delay(5000)
                    showInfoPanel = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showInfoPanel = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name.uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Categoria / Qualidade
                        val quality = when {
                            channel.name.contains("FHD", ignoreCase = true) -> "FULL HD"
                            channel.name.contains("HD", ignoreCase = true) -> "HD"
                            channel.name.contains("SD", ignoreCase = true) -> "SD"
                            channel.name.contains("4K", ignoreCase = true) -> "4K"
                            else -> null
                        }
                        Row {
                            Text(
                                text = channel.groupTitle ?: channel.category,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                            if (quality != null) {
                                Text("  •  ", color = Color.Gray, fontSize = 13.sp)
                                Text(
                                    text = quality,
                                    color = com.cinex.player.ui.theme.CineX_PremiumGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // EPG info se disponível
                        if (!isLiveTv) {
                            if (!channel.tmdbSynopsis.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = channel.tmdbSynopsis,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    // Rating se disponível
                    if (channel.tmdbRating != null && channel.tmdbRating > 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(start = 24.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Yellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = String.format("%.1f", channel.tmdbRating),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Overlay de Próximo Episódio (estilo Netflix)
        if (showNextEpisodeOverlay && nextEpisode != null) {
            NextEpisodeOverlay(
                nextEpisode = nextEpisode!!,
                isVideoEnded = isVideoEnded,
                onPlayNext = {
                    showNextEpisodeOverlay = false
                    onPlayNext(nextEpisode!!)
                },
                onDismiss = {
                    showNextEpisodeOverlay = false
                },
                onBack = onBack
            )
        }

        if (showResumeDialog) {
            PlayerDialog(
                title = "Retomar",
                description = "Deseja retomar a reprodução de onde parou?",
                onConfirm = {
                    showResumeDialog = false
                    exoPlayer.playWhenReady = true
                },
                onCancel = {
                    showResumeDialog = false
                    exoPlayer.seekTo(0)
                    exoPlayer.playWhenReady = true
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

        if (showPlaybackError) {
            PlaybackErrorOverlay(
                onExit = { onBack() },
                onRetry = {
                    showPlaybackError = false
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                },
                onPlayNext = if (nextEpisode != null) {
                    {
                        showPlaybackError = false
                        onPlayNext(nextEpisode!!)
                    }
                } else null
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
    val confirmRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { confirmRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionCenter, Key.Enter, Key.Back -> false
                    else -> true
                }
            }
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
                var isConfirmFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .focusRequester(confirmRequester)
                        .onFocusChanged { isConfirmFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onConfirm(); true } else false
                        }
                        .background(com.cinex.player.ui.theme.DeepRed, RoundedCornerShape(8.dp))
                        .then(
                            if (isConfirmFocused) Modifier.border(2.dp, Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))), RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SIM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                var isCancelFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onCancel(); true } else false
                        }
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .then(
                            if (isCancelFocused) Modifier.border(2.dp, Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))), RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun PlaybackErrorOverlay(
    onExit: () -> Unit,
    onRetry: () -> Unit,
    onPlayNext: (() -> Unit)? = null
) {
    val retryRequester = remember { FocusRequester() }
    val gradientBrush = remember { Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))) }

    LaunchedEffect(Unit) {
        try { retryRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                // Permite navegação D-pad dentro do dialog, bloqueia todo o resto de vazar
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionCenter, Key.Enter, Key.Back -> false
                    else -> true
                }
            }
            .background(Color(0xDD000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .background(com.cinex.player.ui.theme.CineX_SecondaryBackground, RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFFC62828).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFC62828),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ERRO DE REPRODUÇÃO",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Não foi possível reproduzir este conteúdo.\nVerifique sua conexão ou tente novamente.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var isExitFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .onFocusChanged { isExitFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onExit(); true } else false
                        }
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .then(
                            if (isExitFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onExit() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SAIR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                var isRetryFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(retryRequester)
                        .onFocusChanged { isRetryFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onRetry(); true } else false
                        }
                        .background(com.cinex.player.ui.theme.DeepRed, RoundedCornerShape(8.dp))
                        .then(
                            if (isRetryFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onRetry() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("TENTAR NOVAMENTE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (onPlayNext != null) {
                Spacer(modifier = Modifier.height(12.dp))
                var isNextFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .onFocusChanged { isNextFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onPlayNext(); true } else false
                        }
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .then(
                            if (isNextFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onPlayNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("PRÓXIMO EPISÓDIO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun NextEpisodeOverlay(
    nextEpisode: Channel,
    isVideoEnded: Boolean,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val autoPlayDuration = if (isVideoEnded) 10_000 else 30_000 // 10s se acabou, 30s durante créditos

    LaunchedEffect(isVideoEnded) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = autoPlayDuration, easing = LinearEasing)
        )
        onPlayNext()
    }

    val seasonEp = buildString {
        nextEpisode.seasonNumber?.let { append("T$it") }
        nextEpisode.episodeNumber?.let {
            if (isNotEmpty()) append(" · ")
            append("E$it")
        }
    }

    val accentRed = Color(0xFFE11D2E)
    val accentGold = Color(0xFFF59E0B)

    if (isVideoEnded) {
        // Tela cheia quando o vídeo terminou
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
                    contentDescription = "CineX",
                    modifier = Modifier.height(80.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = nextEpisode.seriesName ?: nextEpisode.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (seasonEp.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = seasonEp, color = accentGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(32.dp))
                NextEpisodeButton(progress = progress.value, accentColor = accentRed, onClick = onPlayNext)
                Spacer(modifier = Modifier.height(16.dp))
                var isBackFocused by remember { mutableStateOf(false) }
                Text(
                    text = "Voltar",
                    color = if (isBackFocused) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .onFocusChanged { isBackFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onBack(); true } else false
                        }
                        .clickable { onBack() }
                )
            }
        }
    } else {
        // Mini overlay no canto inferior direito durante os créditos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 32.dp, bottom = 80.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .border(1.dp, accentRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(Color(0xF0101010), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = nextEpisode.seriesName ?: nextEpisode.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (seasonEp.isNotEmpty()) {
                            Text(text = seasonEp, color = accentGold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = if (isCloseFocused) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(18.dp)
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) { onDismiss(); true } else false
                            }
                            .clickable { onDismiss() }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                NextEpisodeButton(progress = progress.value, accentColor = accentRed, onClick = onPlayNext)
            }
        }
    }
}

@Composable
private fun NextEpisodeButton(progress: Float, accentColor: Color, onClick: () -> Unit) {
    val btnRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val gradientBrush = remember { Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B))) }

    LaunchedEffect(Unit) {
        try { btnRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusRequester(btnRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) { onClick(); true } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .then(
                if (isFocused) Modifier.border(2.dp, gradientBrush, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        // Barra de progresso com cor do sistema
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(accentColor.copy(alpha = 0.7f))
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Próximo episódio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color
) {
    var heightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (heightPx > 0) {
                            val newValue = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    if (heightPx > 0) {
                        val newValue = 1f - (change.position.y / heightPx).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (heightPx > 0) {
                            val newValue = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    }
                )
            }
    ) {
        val trackWidth = 4.dp.toPx()
        val thumbRadius = 10.dp.toPx()
        val centerY = size.width / 2

        // Fundo (Barra inativa)
        drawLine(
            color = inactiveTrackColor,
            start = Offset(centerY, thumbRadius),
            end = Offset(centerY, size.height - thumbRadius),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )

        // Posição Y do thumb
        val trackHeight = size.height - (2 * thumbRadius)
        // Y cresce para baixo, então inverte
        val thumbY = thumbRadius + trackHeight * (1f - value)

        // Barra ativa (da base até o thumb)
        drawLine(
            color = activeTrackColor,
            start = Offset(centerY, size.height - thumbRadius),
            end = Offset(centerY, thumbY),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )

        // Bolinha (Thumb)
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(centerY, thumbY)
        )
    }
}
