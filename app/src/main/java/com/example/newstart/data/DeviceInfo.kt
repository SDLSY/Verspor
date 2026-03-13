package com.example.newstart.data

/**
 * 设备信息模型
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val macAddress: String,
    val batteryLevel: Int,          // 电量 (0-100)
    val firmwareVersion: String,
    val connectionState: ConnectionState,
    val lastSyncTime: Long = 0L     // 最后同步时间戳
) {
    fun getBatteryStatus(): String {
        return when {
            batteryLevel > 80 -> "电量充足"
            batteryLevel > 50 -> "电量良好"
            batteryLevel > 20 -> "电量偏低"
            else -> "电量不足，请充电"
        }
    }
    
    fun getBatteryIcon(): String {
        return when {
            batteryLevel > 80 -> "🔋"
            batteryLevel > 50 -> "🔋"
            batteryLevel > 20 -> "🪫"
            else -> "🪫"
        }
    }
}

/**
 * 连接状态枚举
 */
enum class ConnectionState {
    CONNECTED,      // 已连接
    CONNECTING,     // 连接中
    DISCONNECTED,   // 已断开
    SCANNING;       // 扫描中
    
    fun getDisplayName(): String {
        return when (this) {
            CONNECTED -> "已连接"
            CONNECTING -> "连接中"
            DISCONNECTED -> "未连接"
            SCANNING -> "扫描中"
        }
    }
}

/**
 * 传感器数据（实时数据）
 */
data class SensorData(
    val timestamp: Long,
    val heartRate: Int,             // 心率
    val bloodOxygen: Int,           // 血氧
    val temperature: Float,         // 体温
    val accelerometer: Triple<Float, Float, Float>,  // 加速度 (x, y, z)
    val gyroscope: Triple<Float, Float, Float>,      // 陀螺仪 (x, y, z)
    val ppgValue: Float,            // PPGԭʼֵ
    val hrv: Int = 0,               // 心率变异性 (ms)
    val steps: Int = 0              // 步数
)
