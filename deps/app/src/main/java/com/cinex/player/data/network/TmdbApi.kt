package com.cinex.player.data.network

import retrofit2.http.GET
import retrofit2.http.Query

// Entidades da API TMDB
data class TmdbSearchResponse(
    val results: List<TmdbMovieResult>
)

data class TmdbMovieResult(
    val id: Int,
    val title: String?,
    val name: String?, // Para TV
    val overview: String,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String?,
    val first_air_date: String?,
    val vote_average: Double
)

data class TmdbDetailsResponse(
    val id: Int,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val credits: TmdbCredits?,
    val videos: TmdbVideoResponse?
)

data class TmdbVideoResponse(
    val results: List<TmdbVideo>
)

data class TmdbVideo(
    val key: String,
    val site: String,
    val type: String
)

data class TmdbCredits(
    val cast: List<TmdbCastMember>
)

data class TmdbCastMember(
    val name: String,
    val character: String?,
    val profile_path: String?
)

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "pt-BR"
    ): TmdbSearchResponse
    
    @GET("search/tv")
    suspend fun searchSeries(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "pt-BR"
    ): TmdbSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @retrofit2.http.Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos",
        @Query("language") language: String = "pt-BR"
    ): TmdbDetailsResponse

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos",
        @Query("language") language: String = "pt-BR"
    ): TmdbDetailsResponse
}
