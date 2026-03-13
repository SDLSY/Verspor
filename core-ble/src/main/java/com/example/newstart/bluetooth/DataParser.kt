package com.example.newstart.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import com.example.newstart.data.SensorData

/**
 * 蓝牙数据解析器
 * 根据Hi90B智能指环通讯协议解析数据
 */
object DataParser {
    
    /**
     * 解析心率数据
     * 格式：字节0-3=时间戳，字节4=类型，字节5-6=心率值，字节7=校验和
     */
    fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int? {
        val data = characteristic.value ?: return null
        if (data.size < 7) return null
        
        // 检查校验和
        if (!validateChecksum(data)) {
            return null
        }
        
        // 提取心率值（字节5-6，小端序）
        val heartRate = ((data[6].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        
        // 心率合理性检查（40-200 bpm）
        return if (heartRate in 40..200) heartRate else null
    }
    
    /**
     * 解析血氧数据
     */
    fun parseBloodOxygen(characteristic: BluetoothGattCharacteristic): Int? {
        val data = characteristic.value ?: return null
        if (data.isEmpty()) return null
        
        val spo2 = data[0].toInt() and 0xFF
        
        // 血氧合理性检查（80-100%）
        return if (spo2 in 80..100) spo2 else null
    }
    
    /**
     * 解析体温数据
     */
    fun parseTemperature(characteristic: BluetoothGattCharacteristic): Float? {
        val data = characteristic.value ?: return null
        if (data.size < 2) return null
        
        // 体温值（需要除以100）
        val tempRaw = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        val temperature = tempRaw / 100.0f
        
        // 体温合理性检查（32-42°C）
        return if (temperature in 32f..42f) temperature else null
    }
    
    /**
     * 解析PPG原始波形数据
     */
    fun parsePPG(characteristic: BluetoothGattCharacteristic): FloatArray? {
        val data = characteristic.value ?: return null
        if (data.size < 2) return null
        
        // PPG是50Hz采样，每次可能收到多个采样点
        val samples = FloatArray(data.size / 2)
        for (i in samples.indices) {
            val index = i * 2
            if (index + 1 < data.size) {
                val value = ((data[index + 1].toInt() and 0xFF) shl 8) or (data[index].toInt() and 0xFF)
                samples[i] = value.toFloat()
            }
        }
        
        return samples
    }
    
    /**
     * 解析加速度数据
     */
    fun parseAccelerometer(characteristic: BluetoothGattCharacteristic): Triple<Float, Float, Float>? {
        val data = characteristic.value ?: return null
        if (data.size < 6) return null
        
        // 三轴加速度（每轴2字节）
        val x = bytesToShort(data[0], data[1]) / 1000.0f
        val y = bytesToShort(data[2], data[3]) / 1000.0f
        val z = bytesToShort(data[4], data[5]) / 1000.0f
        
        return Triple(x, y, z)
    }
    
    /**
     * 解析陀螺仪数据
     */
    fun parseGyroscope(characteristic: BluetoothGattCharacteristic): Triple<Float, Float, Float>? {
        val data = characteristic.value ?: return null
        if (data.size < 6) return null
        
        // 三轴陀螺仪（每轴2字节）
        val x = bytesToShort(data[0], data[1]) / 100.0f
        val y = bytesToShort(data[2], data[3]) / 100.0f
        val z = bytesToShort(data[4], data[5]) / 100.0f
        
        return Triple(x, y, z)
    }
    
    /**
     * 解析HRV数据
     */
    fun parseHRV(characteristic: BluetoothGattCharacteristic): Int? {
        val data = characteristic.value ?: return null
        if (data.size < 2) return null
        
        val hrv = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        
        // HRV合理性检查（10-200ms）
        return if (hrv in 10..200) hrv else null
    }
    
    /**
     * 解析电池电量
     */
    fun parseBatteryLevel(characteristic: BluetoothGattCharacteristic): Int? {
        val data = characteristic.value ?: return null
        if (data.isEmpty()) return null
        
        val battery = data[0].toInt() and 0xFF
        
        // 电量检查（0-100%）
        return if (battery in 0..100) battery else null
    }
    
    // ========== 辅助函数 ==========
    
    /**
     * 将两个字节转换为Short
     */
    private fun bytesToShort(low: Byte, high: Byte): Short {
        return (((high.toInt() and 0xFF) shl 8) or (low.toInt() and 0xFF)).toShort()
    }
    
    /**
     * 校验和验证
     */
    private fun validateChecksum(data: ByteArray): Boolean {
        if (data.size < 8) return false
        
        // 简单校验：所有字节求和 mod 256
        var sum = 0
        for (i in 0 until data.size - 1) {
            sum += data[i].toInt() and 0xFF
        }
        val checksum = sum and 0xFF
        val receivedChecksum = data[data.size - 1].toInt() and 0xFF
        
        return checksum == receivedChecksum
    }
    
    /**
     * 创建传感器数据对象
     */
    fun createSensorData(
        timestamp: Long = System.currentTimeMillis(),
        heartRate: Int = 0,
        bloodOxygen: Int = 0,
        temperature: Float = 0f,
        accelerometer: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
        gyroscope: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
        ppgValue: Float = 0f
    ): SensorData {
        return SensorData(
            timestamp = timestamp,
            heartRate = heartRate,
            bloodOxygen = bloodOxygen,
            temperature = temperature,
            accelerometer = accelerometer,
            gyroscope = gyroscope,
            ppgValue = ppgValue
        )
    }
}
