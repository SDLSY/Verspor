package com.example.newstart.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.newstart.data.ConnectionState
import com.example.newstart.data.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 蓝牙连接管理器（增强版）
 * 新增功能：
 * - 断线自动重连
 * - 数据缓存队列
 * - 连接状态监控
 * - 错误处理
 */
@SuppressLint("MissingPermission")
class BleConnectionManager(context: Context) {
    
    companion object {
        private const val TAG = "BleConnectionManager"
        private const val MAX_RETRY_TIMES = 3
        private const val RECONNECT_DELAY = 2000L
    }
    
    private val bleManager = BleManager(context)

    fun sendCommand(command: ByteArray) {
        bleManager.sendCommand(command)
    }
    
    // 连接状态Flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // 数据缓存队列
    private val dataQueue = ConcurrentLinkedQueue<SensorData>()
    
    // 重连计数
    private var retryCount = 0
    private var currentDeviceAddress: String? = null
    
    // 数据回调
    private var onDataReceived: ((SensorData) -> Unit)? = null
    
    /**
     * 连接设备（带自动重连）
     */
    fun connect(deviceAddress: String, onSuccess: () -> Unit, onFailed: (String) -> Unit) {
        currentDeviceAddress = deviceAddress
        retryCount = 0
        
        bleManager.connect(deviceAddress, object : BleManager.ConnectionCallback {
            override fun onConnectionStateChanged(state: ConnectionState) {
                _connectionState.value = state
                
                when (state) {
                    ConnectionState.CONNECTED -> {
                        Log.i(TAG, "Connected successfully")
                        retryCount = 0
                        onSuccess()
                    }
                    ConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "Disconnected")
                        // 尝试重连
                        if (retryCount < MAX_RETRY_TIMES) {
                            retryCount++
                            Log.i(TAG, "Reconnecting... Attempt $retryCount")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                currentDeviceAddress?.let { connect(it, onSuccess, onFailed) }
                            }, RECONNECT_DELAY)
                        }
                    }
                    else -> {}
                }
            }

            override fun onConnectionFailed(error: String) {
                Log.e(TAG, "Connection failed: $error")
                _connectionState.value = ConnectionState.DISCONNECTED
                onFailed(error)
            }
        })
        
        // 设置数据回调
        bleManager.setDataCallback(object : BleManager.DataCallback {
            override fun onDataReceived(data: SensorData) {
                // 缓存数据
                dataQueue.offer(data)
                
                // 通知回调
                onDataReceived?.invoke(data)
                
                // 限制队列大小
                while (dataQueue.size > 1000) {
                    dataQueue.poll()
                }
            }
        })
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        currentDeviceAddress = null
        retryCount = 0
        bleManager.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    /**
     * 扫描设备
     */
    fun startScan(callback: BleManager.BleManagerCallback) {
        bleManager.startScan(callback)
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        bleManager.stopScan()
    }
    
    /**
     * 设置数据接收回调
     */
    fun setOnDataReceived(callback: (SensorData) -> Unit) {
        this.onDataReceived = callback
    }

    fun setProtocolCallback(callback: BleManager.ProtocolCallback) {
        bleManager.setProtocolCallback(callback)
    }
    
    /**
     * 获取缓存的数据
     */
    fun getCachedData(): List<SensorData> {
        return dataQueue.toList()
    }
    
    /**
     * 清空数据缓存
     */
    fun clearCache() {
        dataQueue.clear()
    }
    
    /**
     * 是否已连接
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnect()
        bleManager.cleanup()
        dataQueue.clear()
    }
}
