package com.example.newstart.database.dao

import androidx.room.*
import com.example.newstart.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 设备DAO
 */
@Dao
interface DeviceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity): Long
    
    @Update
    suspend fun update(device: DeviceEntity)
    
    @Delete
    suspend fun delete(device: DeviceEntity)
    
    /**
     * 根据设备ID查询
     */
    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    suspend fun getById(deviceId: String): DeviceEntity?
    
    /**
     * 根据MAC地址查询
     */
    @Query("SELECT * FROM devices WHERE macAddress = :macAddress")
    suspend fun getByMacAddress(macAddress: String): DeviceEntity?
    
    /**
     * 获取主设备
     */
    @Query("SELECT * FROM devices WHERE isPrimary = 1 LIMIT 1")
    fun getPrimaryDevice(): Flow<DeviceEntity?>
    
    /**
     * 获取所有设备
     */
    @Query("SELECT * FROM devices ORDER BY updatedAt DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>
    
    /**
     * 设置主设备
     */
    @Transaction
    suspend fun setPrimaryDevice(deviceId: String) {
        // 先取消所有设备的主设备状态
        clearPrimaryDevices()
        // 设置指定设备为主设备
        updatePrimaryStatus(deviceId, true)
    }
    
    @Query("UPDATE devices SET isPrimary = 0")
    suspend fun clearPrimaryDevices()
    
    @Query("UPDATE devices SET isPrimary = :isPrimary WHERE deviceId = :deviceId")
    suspend fun updatePrimaryStatus(deviceId: String, isPrimary: Boolean)
    
    /**
     * 删除所有设备
     */
    @Query("DELETE FROM devices")
    suspend fun deleteAll()
}
