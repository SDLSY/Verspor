package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.MedicalReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: MedicalReportEntity)

    @Query("SELECT * FROM medical_reports ORDER BY reportDate DESC LIMIT 1")
    fun getLatestFlow(): Flow<MedicalReportEntity?>

    @Query("SELECT * FROM medical_reports ORDER BY reportDate DESC LIMIT 1")
    suspend fun getLatest(): MedicalReportEntity?

    @Query("SELECT * FROM medical_reports WHERE id = :reportId LIMIT 1")
    suspend fun getById(reportId: String): MedicalReportEntity?
}

