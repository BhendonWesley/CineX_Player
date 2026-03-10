package com.cinex.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cinex.player.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Query("SELECT * FROM categories WHERE type = :type AND playlistUrl = :url ORDER BY orderIndex ASC")
    fun getCategoriesByType(type: String, url: String): Flow<List<Category>>

    @Query("DELETE FROM categories WHERE playlistUrl = :url")
    suspend fun clearByPlaylist(url: String)

    @Query("SELECT * FROM categories WHERE playlistUrl = :url")
    suspend fun getAllByPlaylist(url: String): List<Category>
}
