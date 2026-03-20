package com.cinex.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.ui.theme.CineX_BackgroundBlue
import com.cinex.player.ui.theme.Montserrat

private val NavGold     = Color(0xFFF59E0B)
private val NavInactive = Color(0xFF9CA3AF)
private val NavWhite    = Color(0xFFE5E7EB)

@Composable
fun TopNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showLive: Boolean = true,
    showMovies: Boolean = true,
    showSeries: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val allTabs = listOf(
        "INÍCIO"     to 0,
        "TV AO VIVO" to 1,
        "FILMES"     to 2,
        "SÉRIES"     to 3
    )

    val visibleTabs = allTabs.filter { (_, id) ->
        when (id) {
            1 -> showLive
            2 -> showMovies
            3 -> showSeries
            else -> true
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CineX_BackgroundBlue)
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── ESQUERDA: Logo ───────────────────────────────────────
        Image(
            painter = painterResource(id = com.cinex.player.R.drawable.logo_cinex),
            contentDescription = "CineX Logo",
            modifier = Modifier.size(34.dp)
        )

        // ── CENTRO: Abas com weight(1f) ─────────────────────────
        // weight(1f) preenche o espaço ENTRE logo e search bar.
        // Arrangement.Center centraliza os tabs nesse espaço,
        // garantindo que a distância logo↔INÍCIO = SÉRIES↔search
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleTabs.forEachIndexed { index, (title, id) ->
                val isSelected = selectedTab == id

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onTabSelected(id) }
                ) {
                    Text(
                        text = title,
                        fontFamily = Montserrat,
                        color = if (isSelected) NavGold else NavInactive,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    if (isSelected) {
                        Box(modifier = Modifier.padding(top = 2.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(44.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                NavGold.copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .width(28.dp)
                                    .align(Alignment.Center)
                                    .background(NavGold, RoundedCornerShape(1.dp))
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (index < visibleTabs.size - 1) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "|",
                        color = Color(0xFF4B5563),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
        }

        // ── DIREITA: Barra de busca ──────────────────────────────
        Box(
            modifier = Modifier
                .width(260.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Pesquisar",
                    tint = NavInactive,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar filme ou série...",
                            color = NavInactive,
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = LocalTextStyle.current.copy(
                            color = NavWhite,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(NavWhite),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar",
                        tint = NavWhite,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSearchChange("") }
                    )
                }
            }
        }
    }
}
