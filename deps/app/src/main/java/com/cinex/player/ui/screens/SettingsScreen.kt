package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DarkBackground
import com.cinex.player.ui.theme.DeepRed
import com.cinex.player.ui.theme.TextWhite

data class SettingItem(
    val title: String,
    val icon: ImageVector,
    val description: String? = null,
    val action: () -> Unit
)

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onServerSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiveHidden by viewModel.isLiveTvHidden.collectAsState()
    val isMoviesHidden by viewModel.isMoviesHidden.collectAsState()
    val isSeriesHidden by viewModel.isSeriesHidden.collectAsState()
    val is24HourFormat by viewModel.is24HourFormat.collectAsState()
    val isParentalControlEnabled by viewModel.isParentalControlEnabled.collectAsState()

    val settingsItems = listOf(
        SettingItem("Adicionar lista", Icons.AutoMirrored.Filled.PlaylistAdd) {
            viewModel.swapServer()
            onServerSwap()
        },
        SettingItem(
            if (isParentalControlEnabled) "Desativar Controle dos Pais" else "Ativar Controle dos Pais",
            if (isParentalControlEnabled) Icons.Default.LockOpen else Icons.Default.Lock
        ) {
            viewModel.updateParentalControl(!isParentalControlEnabled)
        },
        SettingItem("Apagar Lista Atual", Icons.Default.Delete) {
            viewModel.swapServer()
            onServerSwap()
        },
        SettingItem("Mudar idioma", Icons.Default.Language) { /* Lang selection */ },
        SettingItem("Limpar histórico", Icons.Default.DeleteSweep) {
            viewModel.clearHistory()
        },
        SettingItem(
            if (isLiveHidden) "Mostrar Categorias ao Vivo" else "Ocultar Categorias ao Vivo",
            if (isLiveHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
        ) {
            viewModel.updateLiveTvVisibility(!isLiveHidden)
        },
        SettingItem(
            if (isMoviesHidden) "Mostrar Categorias Vod" else "Ocultar Categorias Vod",
            if (isMoviesHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
        ) {
            viewModel.updateMoviesVisibility(!isMoviesHidden)
        },
        SettingItem(
            if (isSeriesHidden) "Mostrar Categorias Series" else "Ocultar Categorias Series",
            if (isSeriesHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
        ) {
            viewModel.updateSeriesVisibility(!isSeriesHidden)
        },
        SettingItem("Formato de vídeo", Icons.Default.LiveTv) { /* Format selection */ },
        SettingItem("Player externo", Icons.Default.PlayCircleOutline) { /* Toggle player */ },
        SettingItem(
            if (is24HourFormat) "Formato 12h" else "Formato 24h",
            Icons.Default.AccessTime
        ) {
            viewModel.updateTimeFormat(!is24HourFormat)
        },
        SettingItem("Legendas", Icons.Default.Subtitles) { /* Sub settings */ },
        SettingItem("Tipo de dispositivo", Icons.Default.Devices) { /* TV/Mobile toggle */ },
        SettingItem("Atualizar app", Icons.Default.Update) { /* Check update */ }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Configurações",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1.2f))
        }

        // Grid of Settings Controls
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), // 4 columns like in image
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(settingsItems) { item ->
                SettingsTile(item)
            }
        }

        // Bottom Info
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "endereço MAC: 79:77:0C:0E:46:38", // Placeholder MAC based on image
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsTile(item: SettingItem) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), // Dark tile background
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable { item.action() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
