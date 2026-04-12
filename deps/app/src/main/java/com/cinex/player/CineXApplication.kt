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
                // TV: 3 requests simultâneos (vs 16 no mobile)
                // Evita OOM por excesso de downloads simultâneos
                maxRequests = if (isTv) 3 else 16
                maxRequestsPerHost = if (isTv) 2 else 8
            })
            .build()

        val builder = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .fetcherDispatcher(
                if (isTv) Dispatchers.IO.limitedParallelism(3)  // TV: 3 fetchers
                else Dispatchers.IO
            )
            .decoderDispatcher(
                if (isTv) Dispatchers.IO.limitedParallelism(2)  // TV: 2 decoders
                else Dispatchers.IO
            )
            .memoryCache {
                MemoryCache.Builder(this)
                    // TV: 25% da memória (vs 20% antes)
                    // Mobile: 30%
                    .maxSizePercent(if (isTv) 0.25 else 0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizePercent(0.15)  // 15% do storage disponível
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)  // Ignorar cache headers para melhor hit rate

        // TV: RGB_565 corta uso de memória por bitmap pela metade (2 bytes/pixel vs 4).
        // Mobile mantém ARGB_8888 padrão para qualidade máxima.
        if (isTv) {
            builder.bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
        }

        return builder.build()
    }
}
