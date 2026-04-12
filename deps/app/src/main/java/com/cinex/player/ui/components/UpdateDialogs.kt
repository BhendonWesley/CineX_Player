package com.cinex.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val CineXRed = Color(0xFFB21E2B)
private val CineXDarkRed = Color(0xFF8B1621)
private val CardBackground = Color(0xFF1A1D21)
private val SurfaceDark = Color(0xFF111316)
private val FocusGold = Color(0xFFF59E0B)

@Composable
fun UpdatePromptDialog(
    newVersion: String,
    apkSizeMb: String,
    status: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloading = status == "downloading"
    val updateFocusRequester = remember { FocusRequester() }
    val dismissFocusRequester = remember { FocusRequester() }

    // Foca no botão ATUALIZAR ao abrir o dialog
    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            for (delayMs in listOf(100L, 200L, 350L)) {
                delay(delayMs)
                try { updateFocusRequester.requestFocus(); break } catch (_: Exception) {}
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackground)
                .border(1.dp, CineXRed.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = CineXRed,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = CineXRed,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isDownloading) "BAIXANDO..." else "NOVA VERSÃO DISPONÍVEL",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "v$newVersion",
                    color = CineXRed,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = apkSizeMb,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botão Atualizar
                var isUpdateFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .focusRequester(updateFocusRequester)
                        .onFocusChanged { isUpdateFocused = it.isFocused }
                        .then(
                            if (isUpdateFocused) Modifier.border(2.dp, FocusGold, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .focusable(interactionSource = remember { MutableInteractionSource() })
                        .onKeyEvent { event ->
                            if (!isDownloading && event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onUpdate(); true } else false
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isDownloading) Modifier.background(Color.Gray.copy(alpha = 0.3f))
                            else Modifier.background(Brush.horizontalGradient(listOf(CineXRed, CineXDarkRed)))
                        )
                        .then(
                            if (!isDownloading) Modifier.clickable { onUpdate() }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDownloading) "AGUARDE..." else "ATUALIZAR AGORA",
                        color = if (isDownloading) Color.White.copy(alpha = 0.5f) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botão Depois
                var isDismissFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .focusRequester(dismissFocusRequester)
                        .onFocusChanged { isDismissFocused = it.isFocused }
                        .then(
                            if (isDismissFocused) Modifier.border(2.dp, FocusGold, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .focusable(interactionSource = remember { MutableInteractionSource() })
                        .onKeyEvent { event ->
                            if (!isDownloading && event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onDismiss(); true } else false
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isDownloading) Color.Transparent else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(12.dp)
                        )
                        .then(
                            if (!isDownloading) Modifier.clickable { onDismiss() }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isDownloading) {
                        Text(
                            text = "DEPOIS",
                            color = if (isDismissFocused) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChangelogDialog(
    version: String,
    changelog: String,
    onDismiss: () -> Unit
) {
    val dismissFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        for (delayMs in listOf(100L, 200L, 350L)) {
            delay(delayMs)
            try { dismissFocusRequester.requestFocus(); break } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .heightIn(max = 480.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackground)
                .border(1.dp, Color(0xFF2ECC71).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = Color(0xFF2ECC71),
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "APP ATUALIZADO!",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Versão $version",
                    color = Color(0xFF2ECC71),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                if (changelog.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Divider(color = Color.White.copy(alpha = 0.08f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "O QUE MUDOU:",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = changelog,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                var isFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(dismissFocusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .then(
                            if (isFocused) Modifier.border(2.dp, FocusGold, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .focusable(interactionSource = remember { MutableInteractionSource() })
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onDismiss(); true } else false
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2ECC71))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ENTENDI",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
