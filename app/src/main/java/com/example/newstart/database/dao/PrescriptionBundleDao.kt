package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.PrescriptionBundleEntity

@Dao
interface PrescriptionBundleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bundle: PrescriptionBundleEntity)

    @Query("SELECT * FROM prescription_bundles WHERE id = :bundleId LIMIT 1")
    suspend fun getById(bundleId: String): PrescriptionBundleEntity?

    @Query("SELECT * FROM prescription_bundles WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestActive(): PrescriptionBundleEntity?

    @Query("SELECT * FROM prescription_bundles ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PrescriptionBundleEntity>

    @Query("UPDATE prescription_bundles SET status = 'ARCHIVED' WHERE status = 'ACTIVE'")
    suspend fun archiveActive()
}
