package com.example.newstart.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.newstart.data.ConnectionState
import com.example.newstart.data.SensorData
import java.util.*

/**
 * BLE manager.
 * Responsibilities:
 * 1) scan devices
 * 2) establish GATT connection
 * 3) receive sensor data (HR/SpO2/temperature/PPG/motion)
 * 4) manage connection state
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        
        // Hi90B / Clevering custom UUIDs (0xFFFA / 0xFFFC / 0xFFFB)
        private val SERVICE_UUID = convertShortUuid(0xFFFA)
        private val NOTIFY_CHARACTERISTIC_UUID = convertShortUuid(0xFFFC)
        private val WRITE_CHARACTERISTIC_UUID = convertShortUuid(0xFFFB)
        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        private const val SCAN_PERIOD: Long = 10000  // scan for 10s
        private const val WRITE_NO_RESPONSE_INTERVAL_MS = 120L
        private const val WRITE_RETRY_INTERVAL_MS = 120L
        private const val WRITE_COMMAND_GAP_MS = 80L
        private const val WRITE_MAX_RETRIES = 6
        private const val PPG_WATCHDOG_CHECK_MS = 30_000L
        private const val PPG_WATCHDOG_TIMEOUT_MS = 90_000L
        private const val VITAL_SIGNS_ITEM = 0x03
        private const val VITAL_SIGNS_CHANNEL = 0x0003
        private const val VITAL_SIGNS_RATE = 50
        private const val VITAL_SIGNS_DURATION_MS = 25_000
        private const val PPG_ITEM = 0x01
        private const val PPG_CHANNEL = 0x0003
        private const val PPG_RATE = 100
        private const val PPG_DURATION_MS = 12_000
        private const val VITAL_PHASE_DELAY_AFTER_START_MS = 500L
        private const val PPG_PHASE_DELAY_AFTER_START_MS = 4_000L
        private const val MOTION_BURST_ITEM = 0x80
        private const val MOTION_BURST_CHANNEL = 0x0030
        private const val MOTION_BURST_RATE = 0x01F4 // 500Hz
        private const val MOTION_BURST_DURATION_MS = 10_000
        private const val MOTION_BURST_DELAY_AFTER_START_MS = 9_000L
        /**
         * 转换短UUID为完整UUID
         */
        private fun convertShortUuid(shortUuid: Int): UUID {
            val msb = ((shortUuid.toLong() and 0xFFFFL) shl 32) or 0x00001000L
            val lsb = -0x7fffff7fa064cb05L  // 0x800000805F9B34FB的有符号表示
            return UUID(msb, lsb)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = ConnectionState.DISCONNECTED
    
    private val handler = Handler(Looper.getMainLooper())
    
    // 回调接口
    private var scanCallback: BleManagerCallback? = null
    private var connectionCallback: ConnectionCallback? = null
    private var dataCallback: DataCallback? = null
    private var lastPpgUploadAtMs: Long = 0L
    private var ppgFallbackApplied = false

    private val vitalSignsRunnable = Runnable {
        if (connectionState != ConnectionState.CONNECTED || bluetoothGatt == null) return@Runnable
        sendVitalSignsInstant()
    }
    private val ppgPriorityRunnable = Runnable {
        if (connectionState != ConnectionState.CONNECTED || bluetoothGatt == null) return@Runnable
        sendPpgInstant()
    }
    private val motionBurstRunnable = Runnable {
        if (connectionState != ConnectionState.CONNECTED || bluetoothGatt == null) return@Runnable
        sendMotionBurstInstant()
    }
    private val ppgWatchdogRunnable = object : Runnable {
        override fun run() {
            val connected = connectionState == ConnectionState.CONNECTED && bluetoothGatt != null
            if (!connected) return

            val now = System.currentTimeMillis()
            val idleMs = now - lastPpgUploadAtMs
            if (idleMs >= PPG_WATCHDOG_TIMEOUT_MS) {
                if (!ppgFallbackApplied) {
                    ppgFallbackApplied = true
                    Log.w(TAG, "[PPG] No PPG upload for ${idleMs}ms, apply fallback profile")
                    applyPpgFallbackProfile()
                } else {
                    Log.w(TAG, "[PPG] Still idle ${idleMs}ms after fallback, restart transfer")
                    sendCommand(Hi90BCommandBuilder.controlDataTransmission(start = false))
                    sendCommand(Hi90BCommandBuilder.controlDataTransmission(start = true))
                    sendCommand(Hi90BCommandBuilder.queryHistorySize())
                    lastPpgUploadAtMs = now
                }
            }
            handler.postDelayed(this, PPG_WATCHDOG_CHECK_MS)
        }
    }
    
    /**
     * 开始扫描设备。
     */
    fun startScan(callback: BleManagerCallback) {
        this.scanCallback = callback
        
        if (bluetoothLeScanner == null) {
            callback.onScanFailed("Bluetooth unavailable")
            return
        }
        
        callback.onScanStarted()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bluetoothLeScanner.startScan(null, scanSettings, leScanCallback)
        
        // 定时停止扫描
        handler.postDelayed({
            stopScan()
        }, SCAN_PERIOD)
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        bluetoothLeScanner?.stopScan(leScanCallback)
        scanCallback?.onScanStopped()
    }
    
    /**
     * Connect to device.
     */
    fun connect(deviceAddress: String, callback: ConnectionCallback) {
        this.connectionCallback = callback
        
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            callback.onConnectionFailed("Device not found")
            return
        }
        
        connectionState = ConnectionState.CONNECTING
        callback.onConnectionStateChanged(ConnectionState.CONNECTING)
        
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        handler.removeCallbacks(ppgWatchdogRunnable)
        handler.removeCallbacks(vitalSignsRunnable)
        handler.removeCallbacks(ppgPriorityRunnable)
        handler.removeCallbacks(motionBurstRunnable)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        commandQueue.clear()
        pendingCommand = null
        pendingRetryCount = 0
        isWriting = false
        lastPpgUploadAtMs = 0L
        ppgFallbackApplied = false
        
        connectionState = ConnectionState.DISCONNECTED
        connectionCallback?.onConnectionStateChanged(ConnectionState.DISCONNECTED)
    }
    
    /**
     * Get current connection state.
     */
    fun getConnectionState(): ConnectionState = connectionState
    
    /**
     * Whether connected.
     */
    fun isConnected(): Boolean = connectionState == ConnectionState.CONNECTED
    
    /**
     * 设置数据回调
     */
    fun setDataCallback(callback: DataCallback) {
        this.dataCallback = callback
    }
    
    /**
     * 扫描回调
     */
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "未知设备"
            val deviceAddress = device.address
            val rssi = result.rssi
            
            Log.d(TAG, "Found device: $deviceName ($deviceAddress), RSSI: $rssi")
            
            // 过滤：显示Clevering或Hi90B设备
            if (deviceName.contains("Clevering", ignoreCase = true) || 
                deviceName.contains("Hi90B", ignoreCase = true)) {
                scanCallback?.onDeviceFound(device, rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            scanCallback?.onScanFailed("扫描失败，错误码: $errorCode")
        }
    }
    
    /**
     * GATT回调
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    connectionState = ConnectionState.CONNECTED
                    connectionCallback?.onConnectionStateChanged(ConnectionState.CONNECTED)
                    
                    // 发现服务
                    handler.postDelayed({
                        bluetoothGatt?.discoverServices()
                    }, 600)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    connectionState = ConnectionState.DISCONNECTED
                    handler.removeCallbacks(vitalSignsRunnable)
                    handler.removeCallbacks(ppgPriorityRunnable)
                    handler.removeCallbacks(motionBurstRunnable)
                    handler.removeCallbacks(ppgWatchdogRunnable)
                    connectionCallback?.onConnectionStateChanged(ConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: $status")
                return
            }

            Log.i(TAG, "Services discovered")

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Clevering service not found")
                connectionCallback?.onConnectionFailed("Service not found")
                return
            }

            val notifyChar = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
            if (notifyChar == null) {
                Log.e(TAG, "Notify characteristic not found")
                connectionCallback?.onConnectionFailed("Notify characteristic not found")
                return
            }

            if ((notifyChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                Log.e(TAG, "Characteristic doesn't support notifications")
                connectionCallback?.onConnectionFailed("Characteristic doesn't support notifications")
                return
            }

            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(CCC_DESCRIPTOR_UUID)
            if (descriptor == null) {
                Log.e(TAG, "CCC descriptor not found")
                connectionCallback?.onConnectionFailed("CCC descriptor not found")
                return
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Log.i(TAG, "Notification enabled for Clevering device")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != NOTIFY_CHARACTERISTIC_UUID) return
            handleNotifyBytes(value)
        }

        @Deprecated("Use onCharacteristicChanged(gatt, characteristic, value) instead")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // This is the old callback. It's forwarded to the new one on newer Android versions.
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            if (characteristic.uuid != NOTIFY_CHARACTERISTIC_UUID) return
            val value = characteristic.value ?: return
            handleNotifyBytes(value)
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor written successfully - Notifications enabled")
                // Notifications enabled; now send initialization commands.
                initAfterNotifyEnabled()
            } else {
                Log.e(TAG, "Failed to write descriptor: $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!isWriting) {
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Wrote: ${characteristic.value.toHex()}")
                finishWrite(success = true, reason = "callback_success")
            } else {
                Log.e(TAG, "Write failed status=$status value=${characteristic.value?.toHex()}")
                finishWrite(success = false, reason = "callback_status_$status")
            }
        }
    }
    
    /**
     * 描述符写入成功后的回调后续会推动命令队列继续发送。
     */
    private val commandQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
    private var isWriting = false
    private var pendingCommand: ByteArray? = null
    private var pendingRetryCount = 0
    private var lastWriteFinishAtMs = 0L

    fun sendCommand(command: ByteArray) {
        commandQueue.add(command)
        processCommandQueue()
    }

    private fun finishWrite(success: Boolean, reason: String) {
        val current = pendingCommand
        isWriting = false

        if (success) {
            pendingCommand = null
            pendingRetryCount = 0
        } else {
            pendingRetryCount += 1
            if (pendingRetryCount > WRITE_MAX_RETRIES) {
                Log.e(TAG, "Drop command after retries, reason=$reason, cmd=${current?.toHex()}")
                pendingCommand = null
                pendingRetryCount = 0
            } else {
                Log.w(TAG, "Retry command #$pendingRetryCount, reason=$reason, cmd=${current?.toHex()}")
            }
        }

        lastWriteFinishAtMs = System.currentTimeMillis()
        val nextDelay = if (success) WRITE_COMMAND_GAP_MS else WRITE_RETRY_INTERVAL_MS
        handler.postDelayed({ processCommandQueue() }, nextDelay)
    }

    private fun processCommandQueue() {
        if (isWriting) return

        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(SERVICE_UUID) ?: return
        val writeChar = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID) ?: return

        val now = System.currentTimeMillis()
        val elapsedSinceLastWrite = now - lastWriteFinishAtMs
        if (elapsedSinceLastWrite in 0 until WRITE_COMMAND_GAP_MS) {
            handler.postDelayed(
                { processCommandQueue() },
                WRITE_COMMAND_GAP_MS - elapsedSinceLastWrite
            )
            return
        }

        if (pendingCommand == null) {
            pendingCommand = commandQueue.poll() ?: return
            pendingRetryCount = 0
        }

        val command = pendingCommand ?: return
        isWriting = true
        writeChar.value = command

        // Device prefers WRITE_TYPE_NO_RESPONSE (default write may fail with status=3).
        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        if (gatt.writeCharacteristic(writeChar)) {
            // Some devices do not callback onCharacteristicWrite for NO_RESPONSE.
            handler.postDelayed({
                if (isWriting && pendingCommand === command) {
                    finishWrite(success = true, reason = "no_response_tick")
                }
            }, WRITE_NO_RESPONSE_INTERVAL_MS)
        } else {
            finishWrite(success = false, reason = "writeCharacteristic_false")
        }
    }

    private fun initAfterNotifyEnabled() {
        handler.removeCallbacks(ppgWatchdogRunnable)
        handler.removeCallbacks(vitalSignsRunnable)
        handler.removeCallbacks(ppgPriorityRunnable)
        handler.removeCallbacks(motionBurstRunnable)
        lastPpgUploadAtMs = System.currentTimeMillis()
        ppgFallbackApplied = false

        // Clear possibly running instant tasks from previous sessions before applying new profile.
        sendCommand(Hi90BCommandBuilder.controlDataTransmission(start = false))
        clearInstantTasksForItems(listOf(1, 3, 4, 5, 6, MOTION_BURST_ITEM))

        sendCommand(Hi90BCommandBuilder.setClock())
        sendCommand(Hi90BCommandBuilder.queryBatteryLevel())

        val motionAllAxis = 0x0007

        // Priority chain: vital signs -> PPG -> motion.
        val periodicParams = listOf(
            Hi90BWorkParam(
                item = VITAL_SIGNS_ITEM, // HR + SpO2
                sampleChannel = VITAL_SIGNS_CHANNEL,
                sampleRate = VITAL_SIGNS_RATE,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 30_000L,
                durationMs = 12_000L
            ),
            Hi90BWorkParam(
                item = 5, // temperature
                sampleChannel = 0x0000,
                sampleRate = 0,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 120_000L,
                durationMs = 0L
            ),
            Hi90BWorkParam(
                item = 4, // HRV
                sampleChannel = 0x0000,
                sampleRate = 0,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 60_000L,
                durationMs = 0L
            ),
            Hi90BWorkParam(
                item = PPG_ITEM, // PPG
                sampleChannel = PPG_CHANNEL,
                sampleRate = PPG_RATE,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 90_000L,
                durationMs = 10_000L
            ),
            Hi90BWorkParam(
                item = 6, // motion
                sampleChannel = motionAllAxis,
                sampleRate = 25,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 120_000L,
                durationMs = 12_000L
            )
        )
        sendCommand(Hi90BCommandBuilder.setWorkParams(periodicParams))
        sendCommand(Hi90BCommandBuilder.readWorkParams())
        sendCommand(Hi90BCommandBuilder.queryHistorySize())
        sendCommand(Hi90BCommandBuilder.controlDataTransmission(start = true))
        handler.postDelayed(vitalSignsRunnable, VITAL_PHASE_DELAY_AFTER_START_MS)
        handler.postDelayed(ppgPriorityRunnable, PPG_PHASE_DELAY_AFTER_START_MS)
        handler.postDelayed(motionBurstRunnable, MOTION_BURST_DELAY_AFTER_START_MS)
        handler.postDelayed(ppgWatchdogRunnable, PPG_WATCHDOG_CHECK_MS)

        Log.i(TAG, "已下发采集参数(生命体征优先->PPG->运动)+读取参数(0x31/0x33)+启动传输(0x34)，等待0x50/0x52上报...")
    }

    private fun applyPpgFallbackProfile() {
        handler.removeCallbacks(vitalSignsRunnable)
        handler.removeCallbacks(ppgPriorityRunnable)
        handler.removeCallbacks(motionBurstRunnable)
        val motionAllAxis = 0x0007

        val fallbackParams = listOf(
            Hi90BWorkParam(
                item = VITAL_SIGNS_ITEM,
                sampleChannel = VITAL_SIGNS_CHANNEL,
                sampleRate = VITAL_SIGNS_RATE,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 60_000L,
                durationMs = 30_000L
            ),
            Hi90BWorkParam(
                item = 5,
                sampleChannel = 0x0000,
                sampleRate = 0,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 600_000L,
                durationMs = 0L
            ),
            Hi90BWorkParam(
                item = 4,
                sampleChannel = 0x0000,
                sampleRate = 0,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 60_000L,
                durationMs = 0L
            ),
            Hi90BWorkParam(
                item = PPG_ITEM,
                sampleChannel = PPG_CHANNEL,
                sampleRate = PPG_RATE,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 45_000L,
                durationMs = 10_000L
            ),
            Hi90BWorkParam(
                item = 6,
                sampleChannel = motionAllAxis,
                sampleRate = 25,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24L * 60L,
                periodMs = 120_000L,
                durationMs = 15_000L
            )
        )

        sendCommand(Hi90BCommandBuilder.controlDataTransmission(start = false))
        clearInstantTasksForItems(listOf(1, 3, 4, 5, 6, MOTION_BURST_ITEM))
        sendCommand(Hi90BCommandBuilder.setWorkParams(fallbackParams))
        sendCommand(Hi90BCommandBuilder.readWorkParams())
        sendCommand(Hi90BCommandBuilder.queryHistorySize())
        sendCommand(Hi90BCommandBuilder.controlDataTransmission(start = true))
        handler.postDelayed(vitalSignsRunnable, VITAL_PHASE_DELAY_AFTER_START_MS)
        handler.postDelayed(ppgPriorityRunnable, PPG_PHASE_DELAY_AFTER_START_MS)
        handler.postDelayed(motionBurstRunnable, MOTION_BURST_DELAY_AFTER_START_MS)
        handler.postDelayed(ppgWatchdogRunnable, PPG_WATCHDOG_CHECK_MS)
    }

    private fun clearInstantTasksForItems(items: List<Int>) {
        items.forEach { item ->
            sendCommand(
                Hi90BCommandBuilder.setInstantCollectionMode(
                    item = item,
                    sampleChannel = 0,
                    sampleRate = 0,
                    durationMs = 0
                )
            )
        }
    }

    private fun sendMotionBurstInstant(durationMs: Int = MOTION_BURST_DURATION_MS) {
        sendCommand(
            Hi90BCommandBuilder.setInstantCollectionMode(
                item = MOTION_BURST_ITEM,
                sampleChannel = MOTION_BURST_CHANNEL,
                sampleRate = MOTION_BURST_RATE,
                durationMs = durationMs
            )
        )
        Log.i(
            TAG,
            "下发运动传感器Burst(0x80): channel=0x${MOTION_BURST_CHANNEL.toString(16)}, rate=$MOTION_BURST_RATE, durationMs=$durationMs"
        )
    }

    private fun sendVitalSignsInstant(durationMs: Int = VITAL_SIGNS_DURATION_MS) {
        sendCommand(
            Hi90BCommandBuilder.setInstantCollectionMode(
                item = VITAL_SIGNS_ITEM,
                sampleChannel = VITAL_SIGNS_CHANNEL,
                sampleRate = VITAL_SIGNS_RATE,
                durationMs = durationMs
            )
        )
        Log.i(
            TAG,
            "下发生命体征即时任务(item=0x03): channel=0x${VITAL_SIGNS_CHANNEL.toString(16)}, rate=$VITAL_SIGNS_RATE, durationMs=$durationMs"
        )
    }

    private fun sendPpgInstant(durationMs: Int = PPG_DURATION_MS) {
        sendCommand(
            Hi90BCommandBuilder.setInstantCollectionMode(
                item = PPG_ITEM,
                sampleChannel = PPG_CHANNEL,
                sampleRate = PPG_RATE,
                durationMs = durationMs
            )
        )
        Log.i(
            TAG,
            "下发PPG即时任务(item=0x01): channel=0x${PPG_CHANNEL.toString(16)}, rate=$PPG_RATE, durationMs=$durationMs"
        )
    }

    private val frameParser = Hi90BFrameParser()
    
    interface ProtocolCallback {
        fun onBatteryStatus(chargingStatus: Int, batteryLevel: Int) {}
        fun onFirmwareVersion(version: Int) {}
        fun onWorkParamsRaw(raw: ByteArray) {}
        fun onDataTransmissionStarted() {}
        fun onDataTransmissionStopped() {}
        fun onFrameReceived(cmd: Int, data: ByteArray) {}
    }

    private var protocolCallback: ProtocolCallback? = null

    fun setProtocolCallback(callback: ProtocolCallback) {
        protocolCallback = callback
    }

    private fun handleNotifyBytes(bytes: ByteArray) {
        val frames = frameParser.feed(bytes)
        for (f in frames) {
            protocolCallback?.onFrameReceived(f.cmd, f.data)
            when (f.cmd) {
                0x20 -> { // 固件版本
                    if (f.data.size >= 2) {
                        val version = ((f.data[0].toInt() and 0xFF) shl 8) or (f.data[1].toInt() and 0xFF)
                        protocolCallback?.onFirmwareVersion(version)
                    }
                }
                0x22 -> { // 电量
                    if (f.data.size >= 2) {
                        val charging = f.data[0].toInt() and 0xFF
                        val level = f.data[1].toInt() and 0xFF
                        protocolCallback?.onBatteryStatus(charging, level)
                    }
                }
                0x31 -> {
                    val params = Hi90BWorkParamsCodec.decode(f.data)
                    Log.i(TAG, "0x31 工作参数(解析):\n${Hi90BWorkParamsCodec.debugString(params)}")
                    protocolCallback?.onWorkParamsRaw(f.data)
                }
                0x33 -> {
                    if (f.data.size >= 4) {
                        val historySize = readUInt32BE(f.data, 0)
                        Log.i(TAG, "0x33 历史数据量: $historySize")
                    } else {
                        Log.i(TAG, "0x33 响应长度异常: ${f.data.size}")
                    }
                }
                0x34 -> {
                    val started = f.data.firstOrNull()?.toInt() == 0x01
                    if (started) {
                        protocolCallback?.onDataTransmissionStarted()
                    } else {
                        protocolCallback?.onDataTransmissionStopped()
                    }
                    Log.i(TAG, "0x34 传输状态响应: ${f.data.toHex()}")
                }
                0x50, 0x52 -> {
                    parseUploadData(f.cmd, f.data)
                }
            }
        }
    }

    private fun parseUploadData(cmd: Int, data: ByteArray) {
        // data: type(1) + taskSeq(4) + packetNo(4) + ts(6) + payload(m)
        if (data.size < 15) { // 头部至少15字节
            Log.w(TAG, "[RECV] Invalid upload data size: ${data.size}")
            return
        }

        val type = data[0].toInt() and 0xFF
        val taskSeq = readUInt32BE(data, 1)
        val packetNo = readUInt32BE(data, 5)
        val isEnd = packetNo == 0xFFFFFFFFL

        val payload = if (data.size > 15) data.copyOfRange(15, data.size) else byteArrayOf()

        Log.d(TAG, "[RECV] cmd=0x${String.format("%02X", cmd)}, type=$type, task=$taskSeq, packetNo=$packetNo, payloadLen=${payload.size}")

        if (isEnd) {
            Log.i(TAG, "[RECV] End packet. Skipping.")
            return
        }

        when (type) {
            0x01 -> {
                // Format A: channel(1) + sampleRate(2) + N * (IR(3) + RED(3))
                // Format B (compat): N * sample(3), no channel/sampleRate header
                if (payload.size >= 9 && ((payload.size - 3) % 6 == 0)) {
                    val channel = payload[0].toInt() and 0xFF
                    val sampleRate = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
                    val sampleBytes = payload.size - 3
                    val sampleCount = sampleBytes / 6

                    var latestPpg = 0f
                    for (i in 0 until sampleCount) {
                        val offset = 3 + i * 6
                        val ir = readUInt24BE(payload, offset)
                        val red = readUInt24BE(payload, offset + 3)
                        latestPpg = when (channel) {
                            0x01 -> ir.toFloat()
                            0x02 -> red.toFloat()
                            else -> (ir + red) / 2f
                        }
                    }

                    dataCallback?.onDataReceived(createSensorData(ppgValue = latestPpg))
                    lastPpgUploadAtMs = System.currentTimeMillis()
                    ppgFallbackApplied = false
                    Log.d(TAG, "[RECV] PPG(A) channel=$channel, sampleRate=$sampleRate, samples=$sampleCount, latest=$latestPpg")
                } else if (payload.size >= 3 && payload.size % 3 == 0) {
                    val sampleCount = payload.size / 3
                    var latestPpg = 0f
                    for (i in 0 until sampleCount) {
                        val offset = i * 3
                        latestPpg = readUInt24BE(payload, offset).toFloat()
                    }
                    dataCallback?.onDataReceived(createSensorData(ppgValue = latestPpg))
                    lastPpgUploadAtMs = System.currentTimeMillis()
                    ppgFallbackApplied = false
                    Log.d(TAG, "[RECV] PPG(B) samples=$sampleCount, latest=$latestPpg")
                } else {
                    Log.w(TAG, "[RECV] PPG payload unrecognized, len=${payload.size}")
                }
            }
            0x03, 0x83 -> { // 血氧+心率，兼容高位类型
                val decoded = decodeHeartRateSpO2(payload)
                if (decoded != null) {
                    dataCallback?.onDataReceived(
                        createSensorData(
                            heartRate = decoded.heartRate,
                            bloodOxygen = decoded.bloodOxygen
                        )
                    )
                } else {
                    Log.w(TAG, "[RECV] HR+SpO2 payload invalid, type=0x${String.format("%02X", type)}, len=${payload.size}, hex=${payload.toHex()}")
                }
            }
            0x02, 0x82 -> { // 心率，兼容高位类型
                if (payload.size >= 2) {
                    val hr = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    dataCallback?.onDataReceived(createSensorData(heartRate = hr))
                }
            }
            0x05, 0x85 -> { // 体温，兼容高位类型
                if (payload.size >= 2) {
                    val raw = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    val signed = raw.toShort().toInt()
                    val temp = signed * 0.1f
                    dataCallback?.onDataReceived(createSensorData(temperature = temp))
                }
            }
            0x04, 0x84 -> { // HRV，兼容高位类型
                if (payload.size >= 2) {
                    val hrv = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    dataCallback?.onDataReceived(createSensorData(hrv = hrv))
                }
            }
            0x06, 0x86 -> { // 加速度计，兼容高位类型
                if (payload.size >= 6) {
                    val x = bytesToShort(payload[0], payload[1]) / 1000f
                    val y = bytesToShort(payload[2], payload[3]) / 1000f
                    val z = bytesToShort(payload[4], payload[5]) / 1000f
                    dataCallback?.onDataReceived(
                        createSensorData(accelerometer = Triple(x, y, z))
                    )
                }
            }
            0x07, 0x87 -> { // 陀螺仪，兼容高位类型
                if (payload.size >= 6) {
                    val x = bytesToShort(payload[0], payload[1]) / 100f
                    val y = bytesToShort(payload[2], payload[3]) / 100f
                    val z = bytesToShort(payload[4], payload[5]) / 100f
                    dataCallback?.onDataReceived(
                        createSensorData(gyroscope = Triple(x, y, z))
                    )
                }
            }
            0x81 -> { // 部分固件把心率+血氧上报为 0x81
                val decoded = decodeHeartRateSpO2(payload)
                if (decoded != null) {
                    dataCallback?.onDataReceived(
                        createSensorData(
                            heartRate = decoded.heartRate,
                            bloodOxygen = decoded.bloodOxygen
                        )
                    )
                    Log.d(TAG, "[RECV] Compat HR+SpO2 from type=0x81 hr=${decoded.heartRate}, spo2=${decoded.bloodOxygen}")
                } else {
                    Log.d(TAG, "[RECV] Ignore type=0x81 payload, len=${payload.size}, hex=${payload.toHex()}")
                }
            }
            0x08 -> { // 步数类型：当前策略不采集，仅记录日志
                Log.d(TAG, "[RECV] Ignore steps payload, type=0x${String.format("%02X", type)}, len=${payload.size}")
            }
            0x80 -> { // motion payload, prefer ACC(6)+GYRO(6) decoding
                when {
                    payload.size >= 15 && ((payload.size - 3) % 12 == 0) -> {
                        // Some firmwares prepend channel(1)+rate(2) before motion frames.
                        val base = 3
                        val groupSize = 12
                        val groupCount = (payload.size - base) / groupSize
                        val offset = base + (groupCount - 1) * groupSize

                        val ax = bytesToShort(payload[offset], payload[offset + 1]) / 1000f
                        val ay = bytesToShort(payload[offset + 2], payload[offset + 3]) / 1000f
                        val az = bytesToShort(payload[offset + 4], payload[offset + 5]) / 1000f
                        val gx = bytesToShort(payload[offset + 6], payload[offset + 7]) / 100f
                        val gy = bytesToShort(payload[offset + 8], payload[offset + 9]) / 100f
                        val gz = bytesToShort(payload[offset + 10], payload[offset + 11]) / 100f

                        dataCallback?.onDataReceived(
                            createSensorData(
                                accelerometer = Triple(ax, ay, az),
                                gyroscope = Triple(gx, gy, gz)
                            )
                        )
                    }
                    payload.size >= 12 -> {
                        val groupSize = 12
                        val groupCount = payload.size / groupSize
                        val offset = (groupCount - 1) * groupSize

                        val ax = bytesToShort(payload[offset], payload[offset + 1]) / 1000f
                        val ay = bytesToShort(payload[offset + 2], payload[offset + 3]) / 1000f
                        val az = bytesToShort(payload[offset + 4], payload[offset + 5]) / 1000f
                        val gx = bytesToShort(payload[offset + 6], payload[offset + 7]) / 100f
                        val gy = bytesToShort(payload[offset + 8], payload[offset + 9]) / 100f
                        val gz = bytesToShort(payload[offset + 10], payload[offset + 11]) / 100f

                        dataCallback?.onDataReceived(
                            createSensorData(
                                accelerometer = Triple(ax, ay, az),
                                gyroscope = Triple(gx, gy, gz)
                            )
                        )

                        val remainder = payload.size % groupSize
                        if (remainder != 0) {
                            Log.w(TAG, "[RECV] Motion payload has trailing bytes: $remainder")
                        }
                    }
                    payload.size >= 6 -> {
                        val x = bytesToShort(payload[0], payload[1]) / 1000f
                        val y = bytesToShort(payload[2], payload[3]) / 1000f
                        val z = bytesToShort(payload[4], payload[5]) / 1000f
                        dataCallback?.onDataReceived(createSensorData(accelerometer = Triple(x, y, z)))

                        if (payload.size > 6) {
                            Log.w(TAG, "[RECV] Motion payload len=${payload.size}, parsed as accelerometer only")
                        }
                    }
                    else -> {
                        Log.w(TAG, "[RECV] Motion payload too short, len=${payload.size}")
                    }
                }
            }
            else -> {
                // TODO: support more payload types if protocol expands
                Log.d(TAG, "[RECV] 未处理的数据类型: 0x${String.format("%02X", type)}")
            }
        }
    }

    private data class HrSpo2Decoded(
        val heartRate: Int,
        val bloodOxygen: Int
    )

    private fun decodeHeartRateSpO2(payload: ByteArray): HrSpo2Decoded? {
        if (payload.size < 4) return null

        fun u16be(offset: Int): Int {
            return ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
        }

        fun u16le(offset: Int): Int {
            return ((payload[offset + 1].toInt() and 0xFF) shl 8) or (payload[offset].toInt() and 0xFF)
        }

        fun inRange(hr: Int, spo2: Int): Boolean {
            return hr in 25..240 && spo2 in 70..100
        }

        val candidates = listOf(
            HrSpo2Decoded(u16be(0), u16be(2)),
            HrSpo2Decoded(u16le(0), u16le(2)),
            HrSpo2Decoded(u16be(0) / 10, u16be(2) / 10),
            HrSpo2Decoded(u16le(0) / 10, u16le(2) / 10)
        )

        return candidates.firstOrNull { inRange(it.heartRate, it.bloodOxygen) }
    }

    private fun readUInt32BE(b: ByteArray, offset: Int): Long {
        return ((b[offset].toLong() and 0xFF) shl 24) or
            ((b[offset + 1].toLong() and 0xFF) shl 16) or
            ((b[offset + 2].toLong() and 0xFF) shl 8) or
            (b[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt32LE(b: ByteArray, offset: Int): Long {
        return ((b[offset + 3].toLong() and 0xFF) shl 24) or
            ((b[offset + 2].toLong() and 0xFF) shl 16) or
            ((b[offset + 1].toLong() and 0xFF) shl 8) or
            (b[offset].toLong() and 0xFF)
    }

    private fun readUInt24BE(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 16) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            (b[offset + 2].toInt() and 0xFF)
    }
    
    /**
     * 创建传感器数据对象
     */
    private fun bytesToShort(low: Byte, high: Byte): Short {
        return (((high.toInt() and 0xFF) shl 8) or (low.toInt() and 0xFF)).toShort()
    }
    
    /**
     * 创建传感器数据对象
     */
    private fun createSensorData(
        heartRate: Int = 0,
        bloodOxygen: Int = 0,
        temperature: Float = 0f,
        accelerometer: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
        gyroscope: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
        ppgValue: Float = 0f,
        hrv: Int = 0,
        steps: Int = 0
    ): SensorData {
        return SensorData(
            timestamp = System.currentTimeMillis(),
            heartRate = heartRate,
            bloodOxygen = bloodOxygen,
            temperature = temperature,
            accelerometer = accelerometer,
            gyroscope = gyroscope,
            ppgValue = ppgValue,
            hrv = hrv,
            steps = steps
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopScan()
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }
    
    // ========== 回调接口 ==========
    
    interface BleManagerCallback {
        fun onScanStarted()
        fun onDeviceFound(device: BluetoothDevice, rssi: Int)
        fun onScanStopped()
        fun onScanFailed(error: String)
    }
    
    interface ConnectionCallback {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onConnectionFailed(error: String)
    }
    
    interface DataCallback {
        fun onDataReceived(data: SensorData)
    }
}


