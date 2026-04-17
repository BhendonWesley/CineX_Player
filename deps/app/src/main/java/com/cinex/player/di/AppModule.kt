package com.cinex.player.di

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cinex.player.data.local.AppDatabase
import com.cinex.player.data.local.ChannelDao
import com.cinex.player.data.local.EpgDao
import com.cinex.player.data.parser.M3UParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.inject.Named
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN lastSyncTime INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            "cinex_database"
        ).addMigrations(MIGRATION_11_12, AppDatabase.MIGRATION_12_13)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    @Singleton
    fun provideChannelDao(db: AppDatabase): ChannelDao {
        return db.channelDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(db: AppDatabase): com.cinex.player.data.local.PlaylistDao {
        return db.playlistDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(db: AppDatabase): com.cinex.player.data.local.CategoryDao {
        return db.categoryDao()
    }

    @Provides
    @Singleton
    fun provideEpgDao(db: AppDatabase): EpgDao {
        return db.epgDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))

        // Em debug, aceita qualquer certificado SSL (resolve emuladores com certs desatualizados)
        if (com.cinex.player.BuildConfig.DEBUG) {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(okHttpClient: OkHttpClient): com.cinex.player.data.network.TmdbApi {
        return retrofit2.Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.cinex.player.data.network.TmdbApi::class.java)
    }

    @Provides
    @Singleton
    @Named("liveTv")
    fun provideLiveTvPlayer(app: Application): ExoPlayer {
        // LoadControl otimizado para live streaming — buffers generosos para evitar tela preta/loop/travamento
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */        20_000,   // buffer mínimo de 20s — mais margem para instabilidade
                /* maxBufferMs */        90_000,   // buffer máximo de 90s — absorve picos de latência
                /* bufferForPlaybackMs */  1_500,  // começa a reproduzir rápido (1.5s)
                /* bufferForPlaybackAfterRebufferMs */ 4_000  // após rebuffer, espera 4s antes de retomar
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .build()

        // DataSource com timeouts ajustados para streams IPTV
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setUserAgent("CineXPlayer/1.0")

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        // BandwidthMeter para estimativa de rede e adaptação de qualidade
        val bandwidthMeter = DefaultBandwidthMeter.Builder(app)
            .setResetOnNetworkTypeChange(true)
            .build()

        return ExoPlayer.Builder(app)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .build().apply {
                playWhenReady = true
            }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    @Named("vod")
    fun provideVodPlayer(app: Application): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 90_000, 1_500, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setUserAgent("CineXPlayer/1.0")

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        return ExoPlayer.Builder(app)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    @Provides
    @Singleton
    fun provideM3UParser(): M3UParser {
        return M3UParser()
    }

    @Provides
    @Singleton
    fun provideEpgParser(): com.cinex.player.data.parser.EpgParser {
        return com.cinex.player.data.parser.EpgParser()
    }

    @Provides
    @Singleton
    fun provideGitHubApi(okHttpClient: OkHttpClient): com.cinex.player.data.network.GitHubApi {
        return retrofit2.Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.cinex.player.data.network.GitHubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateManager(
        app: Application,
        gitHubApi: com.cinex.player.data.network.GitHubApi
    ): com.cinex.player.util.UpdateManager {
        return com.cinex.player.util.UpdateManager(app, gitHubApi)
    }
}
