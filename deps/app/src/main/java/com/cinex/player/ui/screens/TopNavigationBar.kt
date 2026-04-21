package com.cinex.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TopNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMenuClick: () -> Unit = {},
    onSearchFocusChange: (Boolean) -> Unit = {},
    onNavigateDown: () -> Unit = {},
    modifier: Modifier = Modifier,
    showLive: Boolean = true,
    showMovies: Boolean = true,
    showSeries: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val allTabs = listOf(
        "INÍCIO"     to 0,
        "CANAIS"     to 1,
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val searchBoxRequester = remember { androidx.compose.ui.focus.FocusRequester() }

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
                var isFocused by remember { mutableStateOf(false) }

                val tabShape = RoundedCornerShape(8.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .focusProperties {
                            if (index == 0) left = androidx.compose.ui.focus.FocusRequester.Cancel
                            if (index == visibleTabs.size - 1) {
                                right = if (isTv) searchBoxRequester else androidx.compose.ui.focus.FocusRequester.Default
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            when {
                                event.key == Key.DirectionUp -> true
                                event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                                    onNavigateDown(); true
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable(interactionSource = remember { MutableInteractionSource() })
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onTabSelected(id); true } else false
                        }
                        .then(
                            if (isFocused) Modifier
                                .border(2.dp, Brush.linearGradient(listOf(Color(0xFFE11D2E), NavGold)), tabShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            else Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(id) }
                ) {
                    Text(
                        text = title,
                        fontFamily = Montserrat,
                        color = if (isSelected) NavGold else if (isFocused) Color.White else NavInactive,
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
        // Na TV, o teclado só abre após pressionar Enter (evita abrir ao receber foco acidental)
        val searchFieldRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        var isBoxFocused by remember { mutableStateOf(false) }
        var isTextFieldFocused by remember { mutableStateOf(false) }
        val isSearchHighlighted = isBoxFocused || isTextFieldFocused
        // Estado local para digitação — evita recomposição pesada ao apagar última letra
        var localQuery by remember { mutableStateOf(searchQuery) }
        // Sincroniza quando o valor externo muda (ex: limpar busca ao trocar de aba)
        LaunchedEffect(searchQuery) { if (!isTextFieldFocused) localQuery = searchQuery }

        // BackHandler: intercepta o botão VOLTAR do controle remoto enquanto o teclado está aberto.
        // onPreviewKeyEvent NÃO é confiável para Key.Back na TV — o sistema processa o Back antes
        // do Compose, fechando a Activity. BackHandler é a API correta para este cenário.
        val scope = rememberCoroutineScope()
        if (isTv) {
            BackHandler(enabled = isTextFieldFocused) {
                scope.launch {
                    try { searchBoxRequester.requestFocus() } catch (_: Exception) {}
                    // Aguarda o teclado recolher (animação ~300ms) antes de mover foco pro grid
                    delay(350L)
                    onNavigateDown()
                }
            }
        }

        Box(
            modifier = Modifier
                .width(260.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x14FFFFFF))
                .then(
                    if (isSearchHighlighted && isTv) Modifier.border(2.dp, NavGold, RoundedCornerShape(20.dp))
                    else Modifier
                )
                .padding(horizontal = 14.dp)
                .then(
                    if (isTv) {
                        Modifier
                            .focusRequester(searchBoxRequester)
                            .onFocusChanged { isBoxFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { event ->
                                when {
                                    event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                        try { searchFieldRequester.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                                        onNavigateDown(); true
                                    }
                                    else -> false
                                }
                            }
                            .focusProperties { right = FocusRequester.Cancel }
                    } else {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            try { searchFieldRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Pesquisar",
                    tint = if (isSearchHighlighted) NavGold else NavInactive,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (localQuery.isEmpty() && !isTextFieldFocused) {
                        Text(
                            text = "Buscar filme ou série...",
                            color = NavInactive,
                            fontSize = 12.sp
                        )
                    }
                    // Propaga imediatamente — o debounce de 400ms fica só no ViewModel
                    LaunchedEffect(localQuery) {
                        if (localQuery != searchQuery) {
                            onSearchChange(localQuery)
                        }
                    }
                    BasicTextField(
                        value = localQuery,
                        onValueChange = { localQuery = it },
                        textStyle = LocalTextStyle.current.copy(
                            color = NavWhite,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(NavWhite),
                        singleLine = true,
                        // Mudamos para Done para adotar o comportamento "Pro" de busca em tempo real.
                        // Ao terminar de digitar e dar Enter, evitamos usar o keyboardController?.hide() na TV
                        // pois ele causa crash em algumas BoxTv ao tentar limpar o proxy. 
                        // Mover o foco de volta para a Box já esconde o teclado nativamente.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (isTv) {
                                scope.launch {
                                    try { searchBoxRequester.requestFocus() } catch (_: Exception) {}
                                    delay(350L)
                                    onNavigateDown()
                                }
                            } else {
                                keyboardController?.hide()
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldRequester)
                            .onFocusChanged {
                                isTextFieldFocused = it.isFocused
                                onSearchFocusChange(it.isFocused)
                            }
                            .then(
                                if (isTv) Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && event.key == androidx.compose.ui.input.key.Key.DirectionDown) {
                                        onNavigateDown()
                                        true
                                    } else false
                                } else Modifier
                            )
                    )
                }
                if (localQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar",
                        tint = NavWhite,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                localQuery = ""
                                onSearchChange("")
                            }
                    )
                }
            }
        }
    }
}
