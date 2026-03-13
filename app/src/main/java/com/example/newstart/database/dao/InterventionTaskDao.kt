package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.InterventionTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterventionTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: InterventionTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<InterventionTaskEntity>)

    @Query("SELECT * FROM intervention_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): InterventionTaskEntity?

    @Query("SELECT * FROM intervention_tasks ORDER BY plannedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<InterventionTaskEntity>

    @Query("SELECT * FROM intervention_tasks WHERE date BETWEEN :startDate AND :endDate ORDER BY plannedAt DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<InterventionTaskEntity>>

    @Query("SELECT * FROM intervention_tasks WHERE status IN ('PENDING','RUNNING') ORDER BY plannedAt ASC LIMIT 1")
    suspend fun getNextPendingTask(): InterventionTaskEntity?

    @Query(
        """
        UPDATE intervention_tasks
        SET status = :status, updatedAt = :updatedAt
        WHERE id = :taskId
        """
    )
    suspend fun updateStatus(taskId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedCount,
            COUNT(*) AS totalCount
        FROM intervention_tasks
        WHERE date BETWEEN :startDate AND :endDate
        """
    )
    fun getCompletionSummary(startDate: Long, endDate: Long): Flow<InterventionCompletionSummary>
}

data class InterventionCompletionSummary(
    val completedCount: Int = 0,
    val totalCount: Int = 0
)
