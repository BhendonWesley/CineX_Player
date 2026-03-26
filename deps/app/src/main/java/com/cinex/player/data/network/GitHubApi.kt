package com.cinex.player.data.network

import com.cinex.player.data.network.dto.GitHubReleaseResponse
import retrofit2.http.GET

interface GitHubApi {

    @GET("repos/BhendonWesley/CineX_Player/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseResponse
}
