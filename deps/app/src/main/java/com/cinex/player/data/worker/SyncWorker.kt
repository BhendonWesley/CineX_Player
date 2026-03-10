package com.cinex.player.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cinex.player.data.repository.ChannelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ChannelRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString("playlist_url") ?: return Result.failure()
        
        return try {
            val result = repository.syncPlaylistDelta(playlistUrl)
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
