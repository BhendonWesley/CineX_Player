package com.cinex.player.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalContext
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

    val context = LocalContext.current
    val isTV = remember {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

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
            .border(
                2.dp,
                when {
                    isFocused -> ActiveBrush
                    isSelected && !isTV -> ActiveBrush
                    else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
                shape
            )
            .background(
                when {
                    isFocused -> Color(0x1AFFFFFF)
                    isSelected -> Color(0x0DFFFFFF)
                    else -> Color.Transparent
                }
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
            Text(
                text = name,
                style = if (isFocused) TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ) else if (isSelected) TextStyle(
                    color = ActiveGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ) else TextStyle(
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = if (isFocused) TextStyle(
                        color = ActiveGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ) else if (isSelected) TextStyle(
                        color = ActiveGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ) else TextStyle(
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
