package com.cinex.player.ui.util

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

private val FocusBorderColor = Color(0xFFF59E0B) // Gold

/**
 * Modifier that makes a composable focusable for D-pad navigation on TV.
 * Shows a gold border when focused and triggers [onClick] on DPAD_CENTER / ENTER.
 * 
 * OTIMIZAÇÃO: Usa KeyDown ao invés de KeyUp para resposta mais rápida.
 * A navegação por D-pad no Compose já é processada no KeyDown, então
 * responder no mesmo evento reduz a latência percebida.
 */
fun Modifier.tvClickable(
    onClick: () -> Unit,
    borderRadius: Int = 12
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) Modifier.border(2.dp, FocusBorderColor, RoundedCornerShape(borderRadius.dp))
            else Modifier
        )
        .focusable(interactionSource = remember { MutableInteractionSource() })
        .onKeyEvent { event ->
            // OTIMIZADO: KeyDown ao invés de KeyUp para resposta instantânea
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
}
