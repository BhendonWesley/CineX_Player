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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.ExperimentalComposeUiApi
import com.cinex.player.data.model.Channel
import kotlinx.coroutines.delay
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DeepRed
import kotlin.OptIn

private enum class PlayerTopFocusTarget { BACK, INFO, FAVORITE, ASPECT }

private fun moveTopFocus(
    target: PlayerTopFocusTarget,
    backRequester: FocusRequester,
    infoRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    aspectRequester: FocusRequester
): Boolean {
    return try {
        when (target) {
            PlayerTopFocusTarget.BACK -> backRequester.requestFocus()
            PlayerTopFocusTarget.INFO -> infoRequester.requestFocus()
            PlayerTopFocusTarget.FAVORITE -> favoriteRequester.requestFocus()
            PlayerTopFocusTarget.ASPECT -> aspectRequester.requestFocus()
        }
        true
    } catch (_: Exception) {
        false
    }
}

@OptIn(UnstableApi::class, ExperimentalComposeUiApi::class)
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
    val currentProgram by viewModel.currentProgram.collectAsState()
    val upcomingPrograms by viewModel.upcomingPrograms.collectAsState()
    val epgListings by viewModel.epgListings.collectAsState()

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
                isPlaying = exoPlayer.isPlaying
                if (playbackState == Player.STATE_ENDED && !isLiveTv) {
                    isVideoEnded = true
                    showNextEpisodeOverlay = true
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isLiveTv) {
                    showPlaybackError = true
                }
            }
        }
        isPlaying = exoPlayer.isPlaying
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
    val backButtonFocusRequester = remember { FocusRequester() }
    val infoButtonFocusRequester = remember { FocusRequester() }
    val favoriteButtonFocusRequester = remember { FocusRequester() }
    val aspectButtonFocusRequester = remember { FocusRequester() }
    var pendingTopFocus by remember { mutableStateOf<PlayerTopFocusTarget?>(null) }
    var focusedTopControl by remember { mutableStateOf<PlayerTopFocusTarget?>(null) }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }

    LaunchedEffect(isControlsVisible, pendingTopFocus, isTv) {
        if (!isTv) return@LaunchedEffect
        if (!isControlsVisible) {
            pendingTopFocus = null
            focusedTopControl = null
            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
            return@LaunchedEffect
        }
        when (pendingTopFocus) {
            PlayerTopFocusTarget.BACK -> {
                delay(80)
                try { backButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                pendingTopFocus = null
            }
            PlayerTopFocusTarget.INFO -> {
                delay(80)
                try { infoButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                pendingTopFocus = null
            }
            PlayerTopFocusTarget.FAVORITE -> {
                delay(80)
                try { favoriteButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                pendingTopFocus = null
            }
            PlayerTopFocusTarget.ASPECT -> {
                delay(80)
                try { aspectButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                pendingTopFocus = null
            }
            null -> Unit
        }
    }

    BackHandler(enabled = !showResumeDialog && !showExitDialog && !showPlaybackError && !showNextEpisodeOverlay) {
        when {
            showInfoPanel -> showInfoPanel = false
            isControlsVisible -> {
                pendingTopFocus = null
                focusedTopControl = null
                isControlsVisible = false
                try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            else -> onBack()
        }
    }

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
                if (focusedTopControl != null) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (!isControlsVisible) {
                            // Primeira pressão: apenas mostra os controles
                            isControlsVisible = true
                        } else if (isLiveTv) {
                            // Live TV não deve pausar no OK. Mantém o canal tocando e alterna o painel detalhado.
                            showInfoPanel = !showInfoPanel
                        } else {
                            // Controles já visíveis: pausar/retomar
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (isLiveTv) {
                            // Live TV: mostra controles; se já visíveis, foca o botão Voltar
                            if (!isControlsVisible) {
                                isControlsVisible = true
                            } else {
                                try { backButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        } else {
                            exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10_000))
                            isControlsVisible = true
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (!isLiveTv) {
                            exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000))
                        }
                        isControlsVisible = true
                        true
                    }
                    Key.DirectionUp -> {
                        if (isTv && !isLiveTv) {
                            pendingTopFocus = PlayerTopFocusTarget.BACK
                            isControlsVisible = true
                        } else if (isTv && isControlsVisible) {
                            pendingTopFocus = PlayerTopFocusTarget.BACK
                            isControlsVisible = true
                        } else {
                            onNextChannel?.invoke()
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        onPreviousChannel?.invoke()
                        true
                    }
                    Key.Back -> {
                        if (showInfoPanel) {
                            showInfoPanel = false
                        } else if (isControlsVisible) {
                            pendingTopFocus = null
                            focusedTopControl = null
                            isControlsVisible = false
                            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                        } else {
                            onBack()
                        }
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
                val layoutRes = if (isTv) com.cinex.player.R.layout.player_view_surface
                    else com.cinex.player.R.layout.player_view_texture
                val view = android.view.LayoutInflater.from(context)
                    .inflate(layoutRes, null, false) as PlayerView
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
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!showResumeDialog && !showExitDialog) {
                    isControlsVisible = !isControlsVisible
                }
            }
        )

        AnimatedVisibility(
            visible = isControlsVisible && !showResumeDialog && !showExitDialog,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isControlsVisible = false }) {
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val topButtonFocusBorder = remember {
                        Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B)))
                    }

                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { if (isLiveTv) onBack() else showExitDialog = true },
                        modifier = Modifier
                            .focusRequester(backButtonFocusRequester)
                            .focusProperties {
                                right = infoButtonFocusRequester
                                down = playerFocusRequester
                                up = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                            }
                            .onFocusChanged {
                                isBackFocused = it.isFocused
                                if (it.isFocused) focusedTopControl = PlayerTopFocusTarget.BACK
                                else if (focusedTopControl == PlayerTopFocusTarget.BACK) focusedTopControl = null
                            }
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionRight -> moveTopFocus(
                                        PlayerTopFocusTarget.INFO,
                                        backButtonFocusRequester,
                                        infoButtonFocusRequester,
                                        favoriteButtonFocusRequester,
                                        aspectButtonFocusRequester
                                    )
                                    Key.DirectionLeft, Key.DirectionUp -> true
                                    Key.DirectionDown -> {
                                        focusedTopControl = null
                                        try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .then(
                                if (isBackFocused) Modifier.border(2.dp, topButtonFocusBorder, RoundedCornerShape(999.dp))
                                else Modifier
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = channel.name.uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Badge de qualidade do stream
                        val streamQuality = when {
                            channel.name.contains("4K", ignoreCase = true) || channel.name.contains("UHD", ignoreCase = true) -> "4K"
                            channel.name.contains("FHD", ignoreCase = true) || channel.name.contains("1080", ignoreCase = true) -> "FHD"
                            channel.name.contains("HD", ignoreCase = true) || channel.name.contains("720", ignoreCase = true) -> "HD"
                            channel.name.contains("SD", ignoreCase = true) -> "SD"
                            else -> null
                        }
                        if (isLiveTv && streamQuality != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (streamQuality) {
                                            "4K" -> Color(0xFFF59E0B)
                                            "FHD" -> Color(0xFF10B981)
                                            "HD" -> Color(0xFF3B82F6)
                                            else -> Color.Gray
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = streamQuality,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Indicador AO VIVO
                        if (isLiveTv) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFDC2626))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                )
                                Text(
                                    text = "AO VIVO",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isInfoFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showInfoPanel = !showInfoPanel },
                            modifier = Modifier
                                .focusRequester(infoButtonFocusRequester)
                                .focusProperties {
                                    left = backButtonFocusRequester
                                    right = favoriteButtonFocusRequester
                                    down = playerFocusRequester
                                    up = FocusRequester.Cancel
                                }
                                .onFocusChanged {
                                    isInfoFocused = it.isFocused
                                    if (it.isFocused) focusedTopControl = PlayerTopFocusTarget.INFO
                                    else if (focusedTopControl == PlayerTopFocusTarget.INFO) focusedTopControl = null
                                }
                                .onKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> moveTopFocus(
                                            PlayerTopFocusTarget.BACK,
                                            backButtonFocusRequester,
                                            infoButtonFocusRequester,
                                            favoriteButtonFocusRequester,
                                            aspectButtonFocusRequester
                                        )
                                        Key.DirectionRight -> moveTopFocus(
                                            PlayerTopFocusTarget.FAVORITE,
                                            backButtonFocusRequester,
                                            infoButtonFocusRequester,
                                            favoriteButtonFocusRequester,
                                            aspectButtonFocusRequester
                                        )
                                        Key.DirectionUp -> true
                                        Key.DirectionDown -> {
                                            focusedTopControl = null
                                            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .then(
                                    if (isInfoFocused) Modifier.border(2.dp, topButtonFocusBorder, RoundedCornerShape(999.dp))
                                    else Modifier
                                )
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        
                        var isFav by remember { mutableStateOf(channel.isFavorite) }
                        var isFavoriteFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                isFav = !isFav
                                viewModel.updateFavorite(channel.id, isFav)
                            },
                            modifier = Modifier
                                .focusRequester(favoriteButtonFocusRequester)
                                .focusProperties {
                                    left = infoButtonFocusRequester
                                    right = aspectButtonFocusRequester
                                    down = playerFocusRequester
                                    up = FocusRequester.Cancel
                                }
                                .onFocusChanged {
                                    isFavoriteFocused = it.isFocused
                                    if (it.isFocused) focusedTopControl = PlayerTopFocusTarget.FAVORITE
                                    else if (focusedTopControl == PlayerTopFocusTarget.FAVORITE) focusedTopControl = null
                                }
                                .onKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> moveTopFocus(
                                            PlayerTopFocusTarget.INFO,
                                            backButtonFocusRequester,
                                            infoButtonFocusRequester,
                                            favoriteButtonFocusRequester,
                                            aspectButtonFocusRequester
                                        )
                                        Key.DirectionRight -> moveTopFocus(
                                            PlayerTopFocusTarget.ASPECT,
                                            backButtonFocusRequester,
                                            infoButtonFocusRequester,
                                            favoriteButtonFocusRequester,
                                            aspectButtonFocusRequester
                                        )
                                        Key.DirectionUp -> true
                                        Key.DirectionDown -> {
                                            focusedTopControl = null
                                            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .then(
                                    if (isFavoriteFocused) Modifier.border(2.dp, topButtonFocusBorder, RoundedCornerShape(999.dp))
                                    else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = if (isFav) Color.Red else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        var isAspectFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL)
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else AspectRatioFrameLayout.RESIZE_MODE_FILL
                            },
                            modifier = Modifier
                                .focusRequester(aspectButtonFocusRequester)
                                .focusProperties {
                                    left = favoriteButtonFocusRequester
                                    down = playerFocusRequester
                                    up = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                                .onFocusChanged {
                                    isAspectFocused = it.isFocused
                                    if (it.isFocused) focusedTopControl = PlayerTopFocusTarget.ASPECT
                                    else if (focusedTopControl == PlayerTopFocusTarget.ASPECT) focusedTopControl = null
                                }
                                .onKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> moveTopFocus(
                                            PlayerTopFocusTarget.FAVORITE,
                                            backButtonFocusRequester,
                                            infoButtonFocusRequester,
                                            favoriteButtonFocusRequester,
                                            aspectButtonFocusRequester
                                        )
                                        Key.DirectionRight, Key.DirectionUp -> true
                                        Key.DirectionDown -> {
                                            focusedTopControl = null
                                            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .then(
                                    if (isAspectFocused) Modifier.border(2.dp, topButtonFocusBorder, RoundedCornerShape(999.dp))
                                    else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) Icons.Default.FitScreen else Icons.Default.Fullscreen,
                                contentDescription = "Tela Cheia", tint = Color.White, modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Controles centrais — apenas para VOD (Live TV fica limpo)
                if (!isLiveTv) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeekButton(icon = Icons.Default.Replay10, description = "-10s", size = 56.dp) {
                            exoPlayer.seekBack()
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

                        SeekButton(icon = Icons.Default.Forward10, description = "+10s", size = 56.dp) {
                            exoPlayer.seekForward()
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

                if (!isLiveTv) {
                    // Barra de progresso (só para VOD)
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
                } else {
                    // Mini EPG — programa atual (só para Live TV)
                    val nextProgram = upcomingPrograms.firstOrNull()

                    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

                    // Dados do programa atual (via EPG local ou fallback Xtream)
                    val programTitle: String?
                    val programStart: Long?
                    val programEnd: Long?
                    val nextTitle: String?
                    val nextStart: Long?

                    if (currentProgram != null) {
                        programTitle = currentProgram!!.title
                        programStart = currentProgram!!.startTime
                        programEnd = currentProgram!!.endTime
                        nextTitle = nextProgram?.title
                        nextStart = nextProgram?.startTime
                    } else if (epgListings.isNotEmpty()) {
                        // Fallback: EPG do Xtream
                        val first = epgListings[0]
                        val startTs = com.cinex.player.utils.EpgTimeHelper.parseXtreamEpgTime(first.start, first.start_timestamp)
                        val stopTs = com.cinex.player.utils.EpgTimeHelper.parseXtreamEpgTime(first.end, first.stop_timestamp)
                        
                        programTitle = first.title.let {
                            try {
                                val decoded = String(android.util.Base64.decode(it.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
                                if (decoded.all { c -> c.code >= 32 || c == '\n' }) decoded.trim() else it
                            } catch (_: Exception) { it }
                        }
                        programStart = startTs
                        programEnd = stopTs
                        val second = epgListings.getOrNull(1)
                        nextTitle = second?.title?.let {
                            try {
                                val decoded = String(android.util.Base64.decode(it.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
                                if (decoded.all { c -> c.code >= 32 || c == '\n' }) decoded.trim() else it
                            } catch (_: Exception) { it }
                        }
                        nextStart = second?.let {
                            com.cinex.player.utils.EpgTimeHelper.parseXtreamEpgTime(it.start, it.start_timestamp)
                        }
                    } else {
                        programTitle = null
                        programStart = null
                        programEnd = null
                        nextTitle = null
                        nextStart = null
                    }

                    if (programTitle != null && programStart != null && programEnd != null) {
                        val now = System.currentTimeMillis()
                        val totalDuration = (programEnd - programStart).coerceAtLeast(1L)
                        val elapsed = (now - programStart).coerceIn(0L, totalDuration)
                        val progress = elapsed.toFloat() / totalDuration.toFloat()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                    )
                                )
                                .padding(horizontal = 32.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = timeFormat.format(java.util.Date(programStart)),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = programTitle,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = timeFormat.format(java.util.Date(programEnd)),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = DeepRed,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )

                            if (nextTitle != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "A seguir:",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (nextStart != null) "${timeFormat.format(java.util.Date(nextStart))}  $nextTitle" else nextTitle,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
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
                        } else {
                            val nextProgram = upcomingPrograms.firstOrNull()
                            val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                            val programTitle: String?
                            val programStart: Long?
                            val programEnd: Long?
                            val nextTitle: String?
                            val nextStart: Long?

                            if (currentProgram != null) {
                                programTitle = currentProgram!!.title
                                programStart = currentProgram!!.startTime
                                programEnd = currentProgram!!.endTime
                                nextTitle = nextProgram?.title
                                nextStart = nextProgram?.startTime
                            } else if (epgListings.isNotEmpty()) {
                                val first = epgListings[0]
                                val startTs = com.cinex.player.utils.EpgTimeHelper.parseXtreamEpgTime(first.start, first.start_timestamp)
                                val stopTs = com.cinex.player.utils.EpgTimeHelper.parseXtreamEpgTime(first.end, first.stop_timestamp)
                                programTitle = first.title.let {
                                    try {
                                        val decoded = String(android.util.Base64.decode(it.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
                                        if (decoded.all { c -> c.code >= 32 || c == '\n' }) decoded.trim() else it
                                    } catch (_: Exception) { it }
                                }
                                programStart = startTs
                                programEnd = stopTs
                                val second = epgListings.getOrNull(1)
                                nextTitle = second?.title?.let {
                                    try {
                                        val decoded = String(android.util.Base64.decode(it.trim(), android.util.Base64.DEFAULT), Charsets.UTF_8)
                                        if (decoded.all { c -> c.code >= 32 || c == '\n' }) decoded.trim() else it
                                    } catch (_: Exception) { it }
                                }
                                nextStart = second?.let {
                                    com.cinex.player.utils.EpgTimeHelper.parseXtreamEpgTime(it.start, it.start_timestamp)
                                }
                            } else {
                                programTitle = null
                                programStart = null
                                programEnd = null
                                nextTitle = null
                                nextStart = null
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            if (programTitle != null && programStart != null && programEnd != null) {
                                Text(
                                    text = "${timeFormat.format(java.util.Date(programStart))} - ${timeFormat.format(java.util.Date(programEnd))}",
                                    color = com.cinex.player.ui.theme.CineX_PremiumGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = programTitle,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                if (!nextTitle.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (nextStart != null) "A seguir ${timeFormat.format(java.util.Date(nextStart))}: $nextTitle" else "A seguir: $nextTitle",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            } else {
                                Text(
                                    text = "MODO 24H",
                                    color = com.cinex.player.ui.theme.CineX_PremiumGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Este canal nÃ£o fornece guia de programaÃ§Ã£o ou transmite conteÃºdo contÃ­nuo 24 horas por dia.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    maxLines = 3
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerDialog(
    title: String,
    description: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val confirmRequester = remember { FocusRequester() }
    val cancelRequester = remember { FocusRequester() }
    val dialogFocusBorder = remember {
        Brush.linearGradient(listOf(Color(0xFFE11D2E), Color(0xFFF59E0B)))
    }

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
                text = title.uppercase(),
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
                        .focusProperties {
                            right = cancelRequester
                            left = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .onFocusChanged { isConfirmFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                    cancelRequester.requestFocus()
                                    true
                                }
                                event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionLeft || event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> true
                                event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                    onConfirm()
                                    true
                                }
                                else -> false
                            }
                        }
                        .background(com.cinex.player.ui.theme.DeepRed, RoundedCornerShape(8.dp))
                        .then(
                            if (isConfirmFocused) Modifier.border(2.dp, dialogFocusBorder, RoundedCornerShape(8.dp))
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
                        .focusRequester(cancelRequester)
                        .focusProperties {
                            left = confirmRequester
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                    confirmRequester.requestFocus()
                                    true
                                }
                                event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionRight || event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> true
                                event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                    onCancel()
                                    true
                                }
                                else -> false
                            }
                        }
                        .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        .then(
                            if (isCancelFocused) Modifier.border(2.dp, dialogFocusBorder, RoundedCornerShape(8.dp))
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
private fun SeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(durationMillis = 100), label = "seekScale"
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        interactionSource = interactionSource
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.fillMaxSize())
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
