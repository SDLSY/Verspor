package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.InterventionExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterventionExecutionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(execution: InterventionExecutionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(executions: List<InterventionExecutionEntity>)

    @Query("SELECT * FROM intervention_executions WHERE taskId = :taskId ORDER BY endedAt DESC")
    fun getByTask(taskId: String): Flow<List<InterventionExecutionEntity>>

    @Query("SELECT * FROM intervention_executions ORDER BY endedAt DESC LIMIT 1")
    suspend fun getLatest(): InterventionExecutionEntity?

    @Query("SELECT * FROM intervention_executions ORDER BY endedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<InterventionExecutionEntity>

    @Query(
        """
        SELECT
            COUNT(*) AS count,
            CAST(COALESCE(AVG(effectScore), 0) AS REAL) AS avgEffectScore,
            CAST(COALESCE(AVG(beforeStress - afterStress), 0) AS REAL) AS avgStressDrop
        FROM intervention_executions
        WHERE endedAt BETWEEN :startTime AND :endTime
        """
    )
    fun getExecutionSummary(startTime: Long, endTime: Long): Flow<InterventionExecutionSummary>
}

data class InterventionExecutionSummary(
    val count: Int = 0,
    val avgEffectScore: Float = 0f,
    val avgStressDrop: Float = 0f
)
