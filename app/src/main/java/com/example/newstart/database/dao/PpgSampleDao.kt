package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.PpgSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * PPG 波形采样 DAO
 */
@Dao
interface PpgSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: PpgSampleEntity): Long

    @Query(
        "SELECT * FROM ppg_samples " +
            "WHERE timestamp BETWEEN :startTime AND :endTime " +
            "ORDER BY timestamp DESC"
    )
    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<PpgSampleEntity>>

    @Query("DELETE FROM ppg_samples")
    suspend fun deleteAll()
}
