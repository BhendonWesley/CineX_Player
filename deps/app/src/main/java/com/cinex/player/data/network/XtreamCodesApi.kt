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
}

data class XtreamAccountResponse(
    val user_info: UserInfo? = null
)

data class UserInfo(
    val username: String? = null,
    val status: String? = null,
    val exp_date: String? = null,
    val active_cons: String? = null,
    val max_connections: String? = null
)

data class XtreamCategory(
    val category_id: String,
    val category_name: String,
    val parent_id: Int = 0
)

data class LiveStreamItem(
    val num: Int? = null,
    val name: String,
    val stream_type: String? = null,
    val stream_id: Int,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val category_id: String
)

data class VodStreamItem(
    val num: Int? = null,
    val name: String,
    val stream_type: String? = null,
    val stream_id: Int,
    val stream_icon: String? = null,
    val rating: String? = null,
    val category_id: String,
    val container_extension: String? = "mp4"
)

data class SeriesItem(
    val num: Int? = null,
    val name: String,
    val series_id: Int,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val rating: String? = null,
    val category_id: String
)
