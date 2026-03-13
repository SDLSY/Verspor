package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.PrescriptionItemEntity

@Dao
interface PrescriptionItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PrescriptionItemEntity>)

    @Query("SELECT * FROM prescription_items WHERE bundleId = :bundleId ORDER BY sequenceOrder ASC")
    suspend fun getByBundleId(bundleId: String): List<PrescriptionItemEntity>

    @Query("DELETE FROM prescription_items WHERE bundleId = :bundleId")
    suspend fun deleteByBundleId(bundleId: String)
}
