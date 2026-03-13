package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.DoctorMessageEntity

@Dao
interface DoctorMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DoctorMessageEntity)

    @Query("SELECT * FROM doctor_messages ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<DoctorMessageEntity>

    @Query("SELECT * FROM doctor_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getRecentForSession(sessionId: String, limit: Int): List<DoctorMessageEntity>

    @Query(
        """
        DELETE FROM doctor_messages
        WHERE id NOT IN (
            SELECT id
            FROM doctor_messages
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
        """
    )
    suspend fun trimToLatest(keepCount: Int)

    @Query("DELETE FROM doctor_messages")
    suspend fun deleteAll()
}
