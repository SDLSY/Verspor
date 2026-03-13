package com.example.newstart.database.dao

import androidx.room.*
import com.example.newstart.database.entity.HealthMetricsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 健康指标DAO
 */
@Dao
interface HealthMetricsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metrics: HealthMetricsEntity): Long
    
    @Update
    suspend fun update(metrics: HealthMetricsEntity)
    
    @Delete
    suspend fun delete(metrics: HealthMetricsEntity)
    
    /**
     * 根据睡眠记录ID查询健康指标
     */
    @Query("SELECT * FROM health_metrics WHERE sleepRecordId = :sleepRecordId")
    suspend fun getBySleepRecordId(sleepRecordId: String): HealthMetricsEntity?
    
    /**
     * 获取最近的健康指标
     */
    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<HealthMetricsEntity?>

    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestOnce(): HealthMetricsEntity?
    
    /**
     * 获取指定日期范围的健康指标
     */
    @Query("SELECT * FROM health_metrics WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<HealthMetricsEntity>>
    
    /**
     * 删除所有记录
     */
    @Query("DELETE FROM health_metrics")
    suspend fun deleteAll()
}
