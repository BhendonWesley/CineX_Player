package com.cinex.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ActiveRed  = Color(0xFFE11D2E)
private val ActiveGold = Color(0xFFF59E0B)
private val ActiveGradient = listOf(ActiveRed, ActiveGold)
private val ActiveBrush = Brush.linearGradient(ActiveGradient)

@Composable
fun CategoryItem(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) { onClick(); true } else false
            }
            .clip(shape)
            .then(
                if (isFocused)
                    Modifier
                        .background(Color(0x1AFFFFFF))
                        .border(2.dp, ActiveBrush, shape)
                else if (isSelected)
                    Modifier.background(Color(0x0DFFFFFF))
                else
                    Modifier.background(Color.Transparent)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            @OptIn(ExperimentalTextApi::class)
            Text(
                text = name,
                style = if (isSelected && !isFocused) TextStyle(
                    brush = ActiveBrush,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ) else TextStyle(
                    color = if (isFocused) Color.White else Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                @OptIn(ExperimentalTextApi::class)
                Text(
                    text = count.toString(),
                    style = if (isSelected && !isFocused) TextStyle(
                        brush = ActiveBrush,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ) else TextStyle(
                        color = if (isFocused) ActiveGold else Color(0xFF6B7280),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
