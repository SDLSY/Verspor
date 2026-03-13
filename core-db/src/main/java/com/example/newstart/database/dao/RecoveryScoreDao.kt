package com.example.newstart.database.dao

import androidx.room.*
import com.example.newstart.database.entity.RecoveryScoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * 恢复指数DAO
 */
@Dao
interface RecoveryScoreDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: RecoveryScoreEntity): Long
    
    @Update
    suspend fun update(score: RecoveryScoreEntity)
    
    @Delete
    suspend fun delete(score: RecoveryScoreEntity)
    
    /**
     * 根据睡眠记录ID查询恢复指数
     */
    @Query("SELECT * FROM recovery_scores WHERE sleepRecordId = :sleepRecordId")
    suspend fun getBySleepRecordId(sleepRecordId: String): RecoveryScoreEntity?
    
    /**
     * 获取最新的恢复指数
     */
    @Query("SELECT * FROM recovery_scores ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<RecoveryScoreEntity?>
    
    /**
     * 获取最近N天的恢复指数
     */
    @Query("SELECT * FROM recovery_scores ORDER BY date DESC LIMIT :days")
    fun getLastNDays(days: Int): Flow<List<RecoveryScoreEntity>>
    
    /**
     * 获取指定日期范围的恢复指数
     */
    @Query("SELECT * FROM recovery_scores WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<RecoveryScoreEntity>>
    
    /**
     * 获取平均恢复指数（最近N天）
     */
    @Query(
        """
        SELECT AVG(score)
        FROM (
            SELECT score
            FROM recovery_scores
            ORDER BY date DESC
            LIMIT :days
        )
        """
    )
    suspend fun getAverageScore(days: Int): Float?
    
    /**
     * 删除所有记录
     */
    @Query("DELETE FROM recovery_scores")
    suspend fun deleteAll()
}
