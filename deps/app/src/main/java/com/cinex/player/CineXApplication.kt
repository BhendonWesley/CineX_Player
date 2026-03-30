package com.cinex.player

import android.app.Application
import android.content.res.Configuration as AndroidConfig
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class CineXApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == AndroidConfig.UI_MODE_TYPE_TELEVISION
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        val isTv = isTvDevice()

        // Na TV, limitar requisições simultâneas para evitar travamentos
        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = if (isTv) 4 else 16
                maxRequestsPerHost = if (isTv) 2 else 8
            })
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .fetcherDispatcher(
                if (isTv) Dispatchers.IO.limitedParallelism(4)
                else Dispatchers.IO
            )
            .decoderDispatcher(
                if (isTv) Dispatchers.IO.limitedParallelism(2)
                else Dispatchers.IO
            )
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isTv) 0.20 else 0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizePercent(0.15)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
