package com.cinex.player.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "channels",
    indices = [
        androidx.room.Index(value = ["remoteId", "playlistUrl"], unique = true),
        androidx.room.Index(value = ["categoryId", "playlistUrl"]),
        androidx.room.Index(value = ["category", "playlistUrl"]),
        androidx.room.Index(value = ["isFavorite", "category"]),
        androidx.room.Index(value = ["seriesName", "playlistUrl"]),
        androidx.room.Index(value = ["resumePosition"])
    ]
)
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    val category: String, // "LIVE_TV", "MOVIE", "SERIES", "MISC"
    val categoryId: String = "",         // ID Real do Servidor (XC category_id)
    val fullStreamName: String? = null, // Nome original completo (ex: S01E01) para evitar perda de dados
    val orderIndex: Int = 0,             // Ordem original do servidor/M3U
    
    // Para séries - null para os demais tipos
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    // Funcionalidades Premium (Ibo Player / XCIPTV)
    val resumePosition: Long = 0L,         // Posição salva em milissegundos para "Continuar Assistindo"
    val totalDuration: Long = 0L,          // Duração total em milissegundos
    val isFavorite: Boolean = false,       // Sistema de Favoritos
    val tmdbRating: Double? = null,        // Nota do TMDB (Ex: 8.5)
    val tmdbSynopsis: String? = null,      // Sinopse do TMDB
    val bannerUrl: String? = null,         // Banner (backdrop) da API TMDB
    val tmdbYear: String? = null,          // Ano do filme/série
    val castMembers: String? = null,       // Membros do elenco (separados por vírgula)
    val playlistUrl: String = "",          // URL da playlist à qual este canal pertence
    val remoteId: String = "",             // ID Único do Servidor (stream_id no Xtream, Hash no M3U)
    val trailerUrl: String? = null,        // URL do trailer (ex: YouTube)
    val posterUrl: String? = null,         // Poster de alta qualidade do TMDB
    val tvgId: String? = null,             // ID para mapeamento de EPG (tvg-id no M3U)
    val syncedAt: Long = 0L               // Timestamp de quando foi adicionado via delta sync (0 = conteúdo original)
)
