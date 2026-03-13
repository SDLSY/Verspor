package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.DoctorSessionEntity

@Dao
interface DoctorSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DoctorSessionEntity)

    @Query("SELECT * FROM doctor_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): DoctorSessionEntity?

    @Query("SELECT * FROM doctor_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): DoctorSessionEntity?

    @Query("SELECT * FROM doctor_sessions ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<DoctorSessionEntity>

    @Query("DELETE FROM doctor_sessions")
    suspend fun deleteAll()
}
