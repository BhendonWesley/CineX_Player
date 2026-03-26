package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinex.player.ui.MainViewModel
import com.cinex.player.ui.theme.DarkBackground
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
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsState()

    // 9 items → 3 linhas de 3 colunas (mais equilibrado e cabe sem scroll)
    val row1 = listOf(
        SettingItem("Trocar servidor", Icons.Default.SwapHoriz) { onServerSwap() },
        SettingItem(
            if (isParentalControlEnabled) "Desativar Controle dos Pais" else "Ativar Controle dos Pais",
            if (isParentalControlEnabled) Icons.Default.LockOpen else Icons.Default.Lock
        ) { viewModel.updateParentalControl(!isParentalControlEnabled) },
        SettingItem("Apagar Lista Atual", Icons.Default.Delete) {
            viewModel.swapServer()
            onServerSwap()
        }
    )

    val row2 = listOf(
        SettingItem(
            if (isLiveHidden) "Mostrar Categorias ao Vivo" else "Ocultar Categorias ao Vivo",
            if (isLiveHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
        ) { viewModel.updateLiveTvVisibility(!isLiveHidden) },
        SettingItem(
            if (isMoviesHidden) "Mostrar Categorias Vod" else "Ocultar Categorias Vod",
            if (isMoviesHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
        ) { viewModel.updateMoviesVisibility(!isMoviesHidden) },
        SettingItem(
            if (isSeriesHidden) "Mostrar Categorias Series" else "Ocultar Categorias Series",
            if (isSeriesHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
        ) { viewModel.updateSeriesVisibility(!isSeriesHidden) }
    )

    val row3 = listOf(
        SettingItem(
            if (is24HourFormat) "Formato 12h" else "Formato 24h",
            Icons.Default.AccessTime
        ) { viewModel.updateTimeFormat(!is24HourFormat) },
        SettingItem("Limpar histórico", Icons.Default.DeleteSweep) { viewModel.clearHistory() },
        SettingItem(
            when (updateCheckStatus) {
                "checking" -> "Verificando..."
                "up_to_date" -> "App atualizado ✓"
                else -> "Atualizar app"
            },
            Icons.Default.SystemUpdate
        ) { viewModel.checkForUpdate() }
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) { "1.0" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        // Grid 3x3
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            SettingsRow(row1)
            Spacer(Modifier.height(10.dp))
            SettingsRow(row2)
            Spacer(Modifier.height(10.dp))
            SettingsRow(row3, updateCheckStatus)
        }

        // Bottom Info — MAC + Versão
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ENDEREÇO MAC: ${viewModel.deviceMacAddress}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "CineX Player v$versionName",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SettingsRow(
    items: List<SettingItem>,
    updateCheckStatus: String = "idle"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isUpdateButton = item.icon == Icons.Default.SystemUpdate
            Box(modifier = Modifier.weight(1f)) {
                SettingsTile(
                    item = item,
                    isLoading = isUpdateButton && updateCheckStatus == "checking",
                    isSuccess = isUpdateButton && updateCheckStatus == "up_to_date"
                )
            }
        }
    }
}

@Composable
fun SettingsTile(
    item: SettingItem,
    isLoading: Boolean = false,
    isSuccess: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isSuccess -> Color(0xFF1B3A2A)
        isFocused -> Color(0xFF2A2A2A)
        else -> Color(0xFF1E1E1E)
    }

    val borderColor = when {
        isSuccess -> Color(0xFF4ADE80)
        isFocused -> Color(0xFFF59E0B)
        else -> Color.Transparent
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .then(
                if (isFocused || isSuccess) Modifier.border(2.dp, borderColor, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(enabled = !isLoading) { item.action() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) { item.action(); true } else false
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (isSuccess) Color(0xFF4ADE80) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.title,
                color = if (isSuccess) Color(0xFF4ADE80) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
