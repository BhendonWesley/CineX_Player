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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

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

    private fun buildUnsafeOkHttpClient(isTv: Boolean): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = if (isTv) 12 else 16
            maxRequestsPerHost = if (isTv) 6 else 8
        }
        return try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            // "TLS" é universalmente suportado — "SSL" é deprecated e pode falhar em TVs antigas
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .build()
        } catch (e: Exception) {
            // Fallback seguro: OkHttpClient padrão sem bypass de SSL
            android.util.Log.e("CineX-Coil", "SSL init failed, using default OkHttpClient: ${e.message}")
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .build()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        val isTv = isTvDevice()

        // Igual ao IBO Player: aceita qualquer certificado SSL para imagens.
        // Servidores Xtream frequentemente usam certificados auto-assinados ou SSL inválido.
        // Usa "TLS" (não "SSL") — universalmente suportado em todas as TVs/Android versions.
        // Fallback para OkHttpClient padrão se a inicialização SSL falhar em algum firmware.
        val okHttpClient = buildUnsafeOkHttpClient(isTv)

        val builder = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .fetcherDispatcher(
                if (isTv) Dispatchers.IO.limitedParallelism(8)
                else Dispatchers.IO
            )
            .decoderDispatcher(
                if (isTv) Dispatchers.IO.limitedParallelism(4)
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
