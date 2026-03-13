package com.example.newstart.demo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.newstart.data.ConnectionState
import com.example.newstart.data.DeviceInfo
import com.example.newstart.data.SensorData
import kotlinx.coroutines.*

/**
 * 虚拟蓝牙管理器
 * 模拟真实的蓝牙设备扫描、连接和数据传输
 */
class MockBleManager(private val context: Context) {
    
    private val TAG = "MockBleManager"
    
    private var isScanning = false
    private var isConnected = false
    private var dataUpdateJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 回调
    private var onDeviceFoundCallback: ((DeviceInfo) -> Unit)? = null
    private var onConnectionStateChangeCallback: ((Boolean) -> Unit)? = null
    private var onDataReceivedCallback: ((SensorData) -> Unit)? = null
    
    /**
     * 开始扫描设备
     */
    fun startScan(callback: (DeviceInfo) -> Unit) {
        if (!DemoConfig.isDemoMode) {
            Log.w(TAG, "Demo mode is disabled, cannot start mock scan")
            return
        }
        
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }
        
        Log.d(TAG, "开始扫描虚拟设备...")
        isScanning = true
        onDeviceFoundCallback = callback
        
        // 延迟一段时间后返回虚拟设备（模拟真实扫描过程）
        mainHandler.postDelayed({
            val demoDevice = createDemoDevice()
            Log.d(TAG, "发现虚拟设备: ${demoDevice.deviceName}")
            callback(demoDevice)
            isScanning = false
        }, DemoConfig.SCAN_DELAY_MS)
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        isScanning = false
        Log.d(TAG, "停止扫描")
    }
    
    /**
     * 连接设备
     */
    fun connect(deviceId: String, callback: (Boolean) -> Unit) {
        if (!DemoConfig.isDemoMode) {
            Log.w(TAG, "Demo mode is disabled, cannot connect")
            callback(false)
            return
        }
        
        if (isConnected) {
            Log.w(TAG, "Already connected")
            callback(true)
            return
        }
        
        Log.d(TAG, "正在连接虚拟设备: $deviceId")
        onConnectionStateChangeCallback = callback
        
        // 模拟连接过程
        mainHandler.postDelayed({
            isConnected = true
            Log.d(TAG, "虚拟设备连接成功")
            callback(true)
            
            // 连接成功后开始推送数据
            startDataUpdates()
        }, DemoConfig.AUTO_CONNECT_DELAY_MS)
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        if (!isConnected) {
            Log.w(TAG, "Already disconnected")
            return
        }
        
        Log.d(TAG, "断开虚拟设备连接")
        isConnected = false
        stopDataUpdates()
        onConnectionStateChangeCallback?.invoke(false)
    }
    
    /**
     * 设置数据接收回调
     */
    fun setOnDataReceived(callback: (SensorData) -> Unit) {
        onDataReceivedCallback = callback
    }
    
    /**
     * 开始数据更新（模拟实时数据传输）
     */
    private fun startDataUpdates() {
        stopDataUpdates()  // 先停止之前的任务
        
        Log.d(TAG, "开始推送虚拟传感器数据，间隔: ${DemoConfig.DATA_UPDATE_INTERVAL_MS}ms")
        
        dataUpdateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isConnected) {
                try {
                    // 生成模拟数据
                    val sensorData = DemoDataGenerator.generateSensorData()
                    
                    // 在主线程回调
                    withContext(Dispatchers.Main) {
                        onDataReceivedCallback?.invoke(sensorData)
                    }
                    
                    Log.v(TAG, "推送数据: HR=${sensorData.heartRate}, SpO2=${sensorData.bloodOxygen}, Temp=${sensorData.temperature}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "数据生成错误", e)
                }
                
                delay(DemoConfig.DATA_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * 停止数据更新
     */
    private fun stopDataUpdates() {
        dataUpdateJob?.cancel()
        dataUpdateJob = null
        Log.d(TAG, "停止推送数据")
    }
    
    /**
     * 创建虚拟设备信息
     */
    private fun createDemoDevice(): DeviceInfo {
        return DeviceInfo(
            deviceId = DemoConfig.DEMO_DEVICE_MAC,
            deviceName = DemoConfig.DEMO_DEVICE_NAME,
            macAddress = DemoConfig.DEMO_DEVICE_MAC,
            batteryLevel = DemoConfig.DEMO_BATTERY_LEVEL,
            firmwareVersion = DemoConfig.DEMO_FIRMWARE_VERSION,
            connectionState = ConnectionState.DISCONNECTED
        )
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopScan()
        disconnect()
        onDeviceFoundCallback = null
        onConnectionStateChangeCallback = null
        onDataReceivedCallback = null
    }
}
