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
    val videos: TmdbVideoResponse?,
    val seasons: List<TmdbSeason>? = null
)

data class TmdbSeason(
    val id: Int,
    val season_number: Int,
    val episode_count: Int,
    val poster_path: String?
)

data class TmdbVideoResponse(
    val results: List<TmdbVideo>
)

data class TmdbVideo(
    val key: String,
    val site: String,
    val type: String,
    val name: String = "",
    val iso_639_1: String = ""
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
        @Query("language") language: String = "pt-BR",
        @Query("year") year: String? = null
    ): TmdbSearchResponse
    
    @GET("search/tv")
    suspend fun searchSeries(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "pt-BR",
        @Query("first_air_date_year") year: String? = null
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

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @retrofit2.http.Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "pt-BR",
        @Query("include_video_language") includeLanguages: String = "pt-BR,en-US"
    ): TmdbVideoResponse

    @GET("tv/{tv_id}/videos")
    suspend fun getTvVideos(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "pt-BR",
        @Query("include_video_language") includeLanguages: String = "pt-BR,en-US"
    ): TmdbVideoResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @retrofit2.http.Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "pt-BR"
    ): TmdbSeasonResponse
}

data class TmdbSeasonResponse(
    val id: Int,
    val episodes: List<TmdbEpisode>
)

data class TmdbEpisode(
    val id: Int,
    val episode_number: Int,
    val name: String,
    val overview: String?,
    val still_path: String?,
    val vote_average: Double
)
