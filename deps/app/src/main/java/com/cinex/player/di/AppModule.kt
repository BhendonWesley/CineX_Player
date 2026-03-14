package com.cinex.player.di

import android.app.Application
import androidx.room.Room
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            "cinex_database"
        ).fallbackToDestructiveMigration().build()
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
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
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
    fun provideLiveTvPlayer(app: Application): androidx.media3.exoplayer.ExoPlayer {
        return androidx.media3.exoplayer.ExoPlayer.Builder(app).build().apply {
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
}
