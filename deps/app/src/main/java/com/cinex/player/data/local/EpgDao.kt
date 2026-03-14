package com.cinex.player.data.local

import androidx.room.*
import com.cinex.player.data.model.EpgChannel
import com.cinex.player.data.model.EpgProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannel>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    @Query("SELECT * FROM epg_programs WHERE channelId = :tvgId AND endTime > :currentTime ORDER BY startTime ASC LIMIT :limit")
    fun getUpcomingPrograms(tvgId: String, currentTime: Long, limit: Int = 5): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :tvgId AND startTime <= :currentTime AND endTime >= :currentTime LIMIT 1")
    fun getCurrentProgram(tvgId: String, currentTime: Long): Flow<EpgProgram?>

    @Query("DELETE FROM epg_programs WHERE playlistUrl = :url")
    suspend fun clearProgramsByPlaylist(url: String)

    @Query("DELETE FROM epg_channels WHERE playlistUrl = :url")
    suspend fun clearChannelsByPlaylist(url: String)

    @Query("DELETE FROM epg_programs WHERE endTime < :currentTime")
    suspend fun clearOldPrograms(currentTime: Long)
}
