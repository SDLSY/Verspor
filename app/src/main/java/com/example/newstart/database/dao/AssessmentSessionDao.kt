package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.AssessmentSessionEntity

@Dao
interface AssessmentSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: AssessmentSessionEntity)

    @Query("SELECT * FROM assessment_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): AssessmentSessionEntity?

    @Query("SELECT * FROM assessment_sessions WHERE completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestCompleted(): AssessmentSessionEntity?

    @Query("SELECT * FROM assessment_sessions WHERE scaleCode = :scaleCode AND completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestCompletedByScale(scaleCode: String): AssessmentSessionEntity?

    @Query("SELECT * FROM assessment_sessions WHERE completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT :limit")
    suspend fun getRecentCompleted(limit: Int): List<AssessmentSessionEntity>

    @Query("SELECT * FROM assessment_sessions WHERE completedAt IS NOT NULL AND freshnessUntil >= :now")
    suspend fun getFreshSessions(now: Long): List<AssessmentSessionEntity>
}
