package com.blockveil.tracker.remover.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanedLinkDao {

    @Insert
    suspend fun insert(entity: CleanedLinkEntity)

    @Query("SELECT * FROM cleaned_links ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<CleanedLinkEntity>>

    @Query("SELECT COUNT(*) FROM cleaned_links")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM cleaned_links")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(removedParamsCount), 0) FROM cleaned_links")
    fun observeTotalTrackersRemoved(): Flow<Int>

    @Query("DELETE FROM cleaned_links")
    suspend fun clearAll()

    @Delete
    suspend fun delete(entity: CleanedLinkEntity)
}
