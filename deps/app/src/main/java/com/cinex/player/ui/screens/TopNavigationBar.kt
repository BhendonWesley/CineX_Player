package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.ui.theme.CineX_BackgroundBlue
import com.cinex.player.ui.theme.SelectedText
import com.cinex.player.ui.theme.TextWhite

@Composable
fun TopNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showLive: Boolean = true,
    showMovies: Boolean = true,
    showSeries: Boolean = true
) {
    val allTabs = listOf(
        "Início" to 0,
        "TV ao Vivo" to 1,
        "Filmes" to 2,
        "Séries" to 3
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
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            visibleTabs.forEachIndexed { index, (title, id) ->
                val isSelected = selectedTab == id
                Text(
                    text = title.uppercase(),
                    color = if (isSelected) SelectedText else TextWhite,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onTabSelected(id) }
                )
                
                if (index < visibleTabs.size - 1) {
                    Text(text = "|", color = Color.Gray.copy(alpha = 0.5f), fontSize = 16.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 32.dp)
                .height(40.dp)
                .width(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Pesquisar",
                    tint = TextWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "BUSCAR FILME OU SÉRIE...",
                            color = TextWhite.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar",
                        tint = TextWhite,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onSearchChange("") }
                    )
                }
            }
        }
    }
}
