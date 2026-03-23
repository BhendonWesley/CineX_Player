package com.cinex.player.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamCodesApi {
    @GET("player_api.php")
    suspend fun getAccountInfo(
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAccountResponse

    @GET("player_api.php?action=get_live_categories")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_vod_categories")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_series_categories")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_live_streams")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null
    ): List<LiveStreamItem>

    @GET("player_api.php?action=get_vod_streams")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null
    ): List<VodStreamItem>

    @GET("player_api.php?action=get_series")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null
    ): List<SeriesItem>

    @GET("player_api.php?action=get_short_epg")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int
    ): XtreamEpgResponse

    @GET("player_api.php?action=get_series_info")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Int
    ): SeriesInfoResponse
}

data class SeriesInfoResponse(
    val seasons: List<SeasonInfo>? = null,
    val episodes: Map<String, List<EpisodeItem>>? = null,
    val info: SeriesExtraInfo? = null
)

data class SeasonInfo(
    val season_number: Int,
    val name: String? = null,
    val episode_count: Int = 0
)

data class EpisodeItem(
    val id: String,
    val episode_num: Int,
    val title: String,
    val container_extension: String? = "mp4",
    val info: EpisodeExtraInfo? = null
)

data class EpisodeExtraInfo(
    val movie_image: String? = null,
    val plot: String? = null,
    val duration: String? = null,
    val release_date: String? = null,
    val rating: String? = null
)

data class SeriesExtraInfo(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val youtube_trailer: String? = null,
    val backdrop_path: List<String>? = null
)

data class XtreamEpgResponse(
    val epg_listings: List<EpgListing>? = null
)

data class EpgListing(
    val id: String? = null,
    val epg_id: String? = null,
    val title: String,
    val lang: String? = null,
    val start: String, // Formato "yyyy-MM-dd HH:mm:ss"
    val end: String,   // Formato "yyyy-MM-dd HH:mm:ss"
    val description: String? = null,
    val channel_id: String? = null,
    val start_timestamp: String? = null,
    val stop_timestamp: String? = null
)

data class XtreamAccountResponse(
    val user_info: UserInfo? = null,
    val server_info: ServerInfo? = null
)

data class UserInfo(
    val username: String? = null,
    val status: String? = null,
    val exp_date: String? = null,
    val created_at: String? = null,
    val active_cons: String? = null,
    val max_connections: String? = null
)

data class ServerInfo(
    val timezone: String? = null,
    val timestamp_now: Long? = null,
    val time_now: String? = null
)

data class XtreamCategory(
    val category_id: String,
    val category_name: String,
    val parent_id: String? = null
)

data class LiveStreamItem(
    val num: String? = null,
    val name: String,
    val stream_type: String? = null,
    val stream_id: Int,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val category_id: String
)

data class VodStreamItem(
    val num: String? = null,
    val name: String,
    val stream_type: String? = null,
    val stream_id: Int,
    val stream_icon: String? = null,
    val rating: String? = null,
    val category_id: String,
    val container_extension: String? = "mp4"
)

data class SeriesItem(
    val num: String? = null,
    val name: String,
    val series_id: Int,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val rating: String? = null,
    val category_id: String
)
