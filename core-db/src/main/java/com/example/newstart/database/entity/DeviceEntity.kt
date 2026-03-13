package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 设备信息实体类
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val deviceId: String,
    
    val deviceName: String,
    val macAddress: String,
    val batteryLevel: Int,
    val firmwareVersion: String,
    val connectionState: String,        // "CONNECTED", "DISCONNECTED", etc.
    val lastSyncTime: Long,
    val isPrimary: Boolean = true,      // 是否为主设备
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
