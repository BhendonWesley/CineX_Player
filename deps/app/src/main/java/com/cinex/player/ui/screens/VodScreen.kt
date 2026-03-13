package com.cinex.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.cinex.player.data.model.Channel
import com.cinex.player.ui.components.VodPosterItem
import com.cinex.player.ui.components.CategoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.paging.PagingData

@Composable
fun VodScreen(
    type: String = "MOVIE", // "MOVIE", "SERIES" ou "SEARCH"
    viewModel: com.cinex.player.ui.MainViewModel,
    title: String,
    channels: Flow<PagingData<Channel>>? = null, // Adicionado para suportar busca
    continueWatching: List<Channel> = emptyList(),
    onVideoClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoriesFlow = when (type) {
        "MOVIE" -> viewModel.movieCategories
        "SERIES" -> viewModel.seriesCategories
        else -> flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    
    var selectedCategory by remember { mutableStateOf("Tudo") }
    
    val pagingItems = if (channels != null) {
        channels.collectAsLazyPagingItems()
    } else {
        remember(selectedCategory, type) {
            if (type == "MOVIE") viewModel.getPagedMoviesByCategory(selectedCategory)
            else viewModel.getPagedSeriesByCategory(selectedCategory)
        }.collectAsLazyPagingItems()
    }
    
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Só mostramos a coluna de categorias se não for uma busca global
        if (type != "SEARCH") {
            // Coluna Esquerda: Categorias / Menu Vertical (COM SCROLL)
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(Color(0x1AFFFFFF))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color.White, 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                categories.forEach { category ->
                    CategoryItem(
                        name = category.name, 
                        count = 0, 
                        isSelected = selectedCategory == category.id,
                        onClick = { selectedCategory = category.id }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Opção "Continuar Assistindo" no final se houver itens (opcional, ou manter no topo)
                if (continueWatching.isNotEmpty()) {
                    CategoryItem(
                        name = "Visualizados recentemente", 
                        count = continueWatching.size, 
                        isSelected = selectedCategory == "Continuar Assistindo",
                        onClick = { selectedCategory = "Continuar Assistindo" }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            // Se for busca, podemos mostrar apenas o título no topo da grade
            // Ou o MainScreen já trata o título. No VodScreen atual o título está na barra lateral.
            // Para "SEARCH", vamos adicionar um título no topo se necessário, mas o LazyVerticalGrid ocupará tudo.
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id }
            ) { index ->
                pagingItems[index]?.let { channel ->
                    VodPosterItem(
                        channel = channel,
                        modifier = Modifier.padding(8.dp),
                        onClick = { onVideoClick(channel) }
                    )
                }
            }
        }
    }
}
