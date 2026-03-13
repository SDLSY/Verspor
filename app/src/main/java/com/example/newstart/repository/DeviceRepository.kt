package com.example.newstart.repository

import com.example.newstart.data.ConnectionState
import com.example.newstart.data.DeviceInfo
import com.example.newstart.database.dao.DeviceDao
import com.example.newstart.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设备数据仓库
 */
class DeviceRepository(
    private val deviceDao: DeviceDao
) {
    
    /**
     * 获取主设备
     */
    fun getPrimaryDevice(): Flow<DeviceInfo?> {
        return deviceDao.getPrimaryDevice().map { entity ->
            entity?.toDeviceInfo()
        }
    }
    
    /**
     * 获取所有设备
     */
    fun getAllDevices(): Flow<List<DeviceInfo>> {
        return deviceDao.getAllDevices().map { entities ->
            entities.map { it.toDeviceInfo() }
        }
    }
    
    /**
     * 保存设备信息
     */
    suspend fun saveDevice(deviceInfo: DeviceInfo): Long {
        val entity = deviceInfo.toEntity()
        return deviceDao.insert(entity)
    }
    
    /**
     * 更新设备信息
     */
    suspend fun updateDevice(deviceInfo: DeviceInfo) {
        val entity = deviceInfo.toEntity()
        deviceDao.update(entity)
    }
    
    /**
     * 设置主设备
     */
    suspend fun setPrimaryDevice(deviceId: String) {
        deviceDao.setPrimaryDevice(deviceId)
    }
    
    /**
     * 根据MAC地址查询设备
     */
    suspend fun getDeviceByMacAddress(macAddress: String): DeviceInfo? {
        return deviceDao.getByMacAddress(macAddress)?.toDeviceInfo()
    }
    
    /**
     * 删除设备
     */
    suspend fun deleteDevice(deviceInfo: DeviceInfo) {
        val entity = deviceInfo.toEntity()
        deviceDao.delete(entity)
    }
}

// ========== 扩展函数 ==========

private fun DeviceEntity.toDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        deviceId = this.deviceId,
        deviceName = this.deviceName,
        macAddress = this.macAddress,
        batteryLevel = this.batteryLevel,
        firmwareVersion = this.firmwareVersion,
        connectionState = ConnectionState.valueOf(this.connectionState),
        lastSyncTime = this.lastSyncTime
    )
}

private fun DeviceInfo.toEntity(): DeviceEntity {
    return DeviceEntity(
        deviceId = this.deviceId,
        deviceName = this.deviceName,
        macAddress = this.macAddress,
        batteryLevel = this.batteryLevel,
        firmwareVersion = this.firmwareVersion,
        connectionState = this.connectionState.name,
        lastSyncTime = this.lastSyncTime
    )
}
