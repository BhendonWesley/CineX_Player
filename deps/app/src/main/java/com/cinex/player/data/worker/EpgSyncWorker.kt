package com.cinex.player.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cinex.player.data.repository.ChannelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ChannelRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val epgUrl = inputData.getString("epg_url") ?: return Result.failure()
        
        return try {
            repository.syncEpg(epgUrl)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
