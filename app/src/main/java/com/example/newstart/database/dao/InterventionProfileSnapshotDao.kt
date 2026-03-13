package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.InterventionProfileSnapshotEntity

@Dao
interface InterventionProfileSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: InterventionProfileSnapshotEntity)

    @Query("SELECT * FROM intervention_profile_snapshots ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatest(): InterventionProfileSnapshotEntity?

    @Query("SELECT * FROM intervention_profile_snapshots WHERE id = :snapshotId LIMIT 1")
    suspend fun getById(snapshotId: String): InterventionProfileSnapshotEntity?
}
