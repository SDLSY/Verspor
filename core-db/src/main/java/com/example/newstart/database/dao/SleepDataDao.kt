package com.example.newstart.database.dao

import androidx.room.*
import com.example.newstart.database.entity.SleepDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * 睡眠数据DAO
 */
@Dao
interface SleepDataDao {
    
    /**
     * 插入睡眠记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sleepData: SleepDataEntity): Long
    
    /**
     * 批量插入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sleepDataList: List<SleepDataEntity>)
    
    /**
     * 更新睡眠记录
     */
    @Update
    suspend fun update(sleepData: SleepDataEntity)
    
    /**
     * 删除睡眠记录
     */
    @Delete
    suspend fun delete(sleepData: SleepDataEntity)
    
    /**
     * 根据ID查询
     */
    @Query("SELECT * FROM sleep_records WHERE id = :id")
    suspend fun getById(id: String): SleepDataEntity?
    
    /**
     * 获取最近的睡眠记录
     */
    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<SleepDataEntity?>
    
    /**
     * 获取最近N天的睡眠记录
     */
    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT :days")
    fun getLastNDays(days: Int): Flow<List<SleepDataEntity>>
    
    /**
     * 获取指定日期范围的睡眠记录
     */
    @Query("SELECT * FROM sleep_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<SleepDataEntity>>
    
    /**
     * 获取所有睡眠记录
     */
    @Query("SELECT * FROM sleep_records ORDER BY date DESC")
    fun getAll(): Flow<List<SleepDataEntity>>
    
    /**
     * 获取总记录数
     */
    @Query("SELECT COUNT(*) FROM sleep_records")
    suspend fun getCount(): Int
    
    /**
     * 删除所有记录
     */
    @Query("DELETE FROM sleep_records")
    suspend fun deleteAll()
    
    /**
     * 获取平均睡眠时长（最近N天）
     */
    @Query(
        """
        SELECT AVG(totalSleepMinutes) 
        FROM (
            SELECT totalSleepMinutes
            FROM sleep_records
            ORDER BY date DESC
            LIMIT :days
        )
        """
    )
    suspend fun getAverageSleepDuration(days: Int): Float?

    /**
     * 获取平均深睡占比（最近N天）
     */
    @Query(
        """
        SELECT CASE
            WHEN SUM(totalSleepMinutes) = 0 THEN NULL
            ELSE SUM(deepSleepMinutes) * 100.0 / SUM(totalSleepMinutes)
        END
        FROM (
            SELECT deepSleepMinutes, totalSleepMinutes
            FROM sleep_records
            ORDER BY date DESC
            LIMIT :days
        )
        """
    )
    suspend fun getAverageDeepSleepPercentage(days: Int): Float?

    /**
     * 获取平均睡眠效率（最近N天）
     */
    @Query(
        """
        SELECT AVG(sleepEfficiency)
        FROM (
            SELECT sleepEfficiency
            FROM sleep_records
            ORDER BY date DESC
            LIMIT :days
        )
        """
    )
    suspend fun getAverageSleepEfficiency(days: Int): Float?

    /**
     * 获取最佳睡眠日期（最近N天，按睡眠效率最高）
     */
    @Query(
        """
        SELECT recent.date
        FROM (
            SELECT id, date
            FROM sleep_records
            ORDER BY date DESC
            LIMIT :days
        ) AS recent
        INNER JOIN recovery_scores rs ON rs.sleepRecordId = recent.id
        ORDER BY rs.score DESC, recent.date DESC
        LIMIT 1
        """
    )
    suspend fun getBestSleepDate(days: Int): Long?
}
