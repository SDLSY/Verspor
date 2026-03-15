package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.MedicationAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MedicationAnalysisEntity)

    @Query("SELECT * FROM medication_analysis_records ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatest(): MedicationAnalysisEntity?

    @Query("SELECT * FROM medication_analysis_records ORDER BY capturedAt DESC LIMIT 1")
    fun getLatestFlow(): Flow<MedicationAnalysisEntity?>

    @Query("SELECT * FROM medication_analysis_records WHERE capturedAt >= :since ORDER BY capturedAt DESC")
    suspend fun getRecentSince(since: Long): List<MedicationAnalysisEntity>

    @Query("SELECT * FROM medication_analysis_records WHERE syncState != 'SYNCED' ORDER BY capturedAt DESC")
    suspend fun getPendingSyncRecords(): List<MedicationAnalysisEntity>
}
