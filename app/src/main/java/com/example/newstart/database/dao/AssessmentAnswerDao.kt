package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.AssessmentAnswerEntity

@Dao
interface AssessmentAnswerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(answers: List<AssessmentAnswerEntity>)

    @Query("SELECT * FROM assessment_answers WHERE sessionId = :sessionId ORDER BY itemOrder ASC")
    suspend fun getBySession(sessionId: String): List<AssessmentAnswerEntity>

    @Query("DELETE FROM assessment_answers WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
