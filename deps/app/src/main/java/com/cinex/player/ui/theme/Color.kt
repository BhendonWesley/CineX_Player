package com.cinex.player.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Cinematic Color Palette (CineX Brand)
val CineX_DeepRed = Color(0xFFB21E2B)
val CineX_DarkRed = Color(0xFF7A0F18)
val CineX_HighlightRed = Color(0xFFE23A3A)

val CineX_PremiumGold = Color(0xFFD8A63A)
val CineX_LightGold = Color(0xFFF2D27A)

val CineX_BackgroundBlue = Color(0xFF282B30) // Updated to #282b30
val CineX_SecondaryBackground = Color(0xFF1E2125) // Slightly darker variant for depth
val CineX_GlowHighlight = Color(0xFFFF7A2F)

// Text Colors
val CineX_TextPrimary = Color(0xFFFFFFFF)
val CineX_TextSecondary = Color(0xFFA0A7B5)
val CineX_TextMuted = Color(0xFF6E768A)

// Highlight Colors
val SelectedText = CineX_PremiumGold

// Legacy aliases (to avoid breaking other screens immediately)
val DeepRed = CineX_DeepRed
val DarkBackground = CineX_BackgroundBlue
val CineGold = CineX_PremiumGold
val SurfaceColor = CineX_SecondaryBackground
val TextWhite = CineX_TextPrimary
val TextGray = CineX_TextSecondary
val CineX__DeepRed = CineX_DeepRed // For Theme.kt compatibility
val DarkBurgundy = CineX_DarkRed // Legacy alias to fix build
