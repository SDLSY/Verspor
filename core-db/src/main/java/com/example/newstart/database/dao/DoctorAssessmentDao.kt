package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.DoctorAssessmentEntity

@Dao
interface DoctorAssessmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: DoctorAssessmentEntity)

    @Query("SELECT * FROM doctor_assessments WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForSession(sessionId: String): DoctorAssessmentEntity?

    @Query("DELETE FROM doctor_assessments")
    suspend fun deleteAll()
}
