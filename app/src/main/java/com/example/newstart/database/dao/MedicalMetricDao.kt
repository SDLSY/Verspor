package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.MedicalMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: MedicalMetricEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<MedicalMetricEntity>)

    @Query("SELECT * FROM medical_metrics WHERE reportId = :reportId ORDER BY metricName ASC")
    fun getByReport(reportId: String): Flow<List<MedicalMetricEntity>>

    @Query("SELECT * FROM medical_metrics WHERE reportId = :reportId ORDER BY metricName ASC")
    suspend fun getByReportOnce(reportId: String): List<MedicalMetricEntity>

    @Query("SELECT * FROM medical_metrics WHERE isAbnormal = 1 ORDER BY confidence DESC")
    suspend fun getAbnormalMetrics(): List<MedicalMetricEntity>
}

