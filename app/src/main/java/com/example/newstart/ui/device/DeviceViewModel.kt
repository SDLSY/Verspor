package com.example.newstart.ui.device

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.lifecycle.*
import androidx.core.content.ContextCompat
import com.example.newstart.R
import com.example.newstart.bluetooth.BleConnectionManager
import com.example.newstart.bluetooth.BleManager
import com.example.newstart.data.ConnectionState
import com.example.newstart.data.DeviceInfo
import android.util.Log
import com.example.newstart.bluetooth.Hi90BWorkParamsCodec
import com.example.newstart.data.SensorData
import com.example.newstart.database.AppDatabase
import com.example.newstart.repository.DeviceRepository
import com.example.newstart.demo.DemoConfig
import com.example.newstart.demo.MockBleManager
import com.example.newstart.service.DataCollectionService
import com.example.newstart.util.HRVAnalyzer
import com.example.newstart.util.HrvFallbackEstimator
import com.example.newstart.util.PpgPeakDetector
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * 设备ViewModel（集成真实蓝牙功能）
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val METRICS_PERSIST_INTERVAL_MS = 2000L
        private const val PPG_PERSIST_INTERVAL_MS = 500L
        private const val HRV_STALE_TIMEOUT_MS = 15_000L
        private const val MOTION_STALE_TIMEOUT_MS = 20_000L
        private const val PPG_WAVE_WINDOW_MS = 20_000L
        private const val PPG_UI_UPDATE_INTERVAL_MS = 400L
        private const val PPG_MAX_POINTS = 120
    }

    private val repository: DeviceRepository
    private val sleepRepository: com.example.newstart.repository.SleepRepository
    private val bleConnectionManager: BleConnectionManager
    
    // 虚拟蓝牙管理器（演示模式用）
    private val mockBleManager: MockBleManager
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = DeviceRepository(database.deviceDao())
        sleepRepository = com.example.newstart.repository.SleepRepository(
            database.sleepDataDao(),
            database.healthMetricsDao(),
            database.recoveryScoreDao(),
            database.ppgSampleDao()
        )
        bleConnectionManager = BleConnectionManager(application)
        
        // 初始化虚拟蓝牙管理器
        mockBleManager = MockBleManager(application)
    }

    private val _currentDevice = MutableLiveData<DeviceInfo?>()
    val currentDevice: LiveData<DeviceInfo?> = _currentDevice

    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning

    private val _scannedDevices = MutableLiveData<List<ScannedDevice>>()
    val scannedDevices: LiveData<List<ScannedDevice>> = _scannedDevices

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _isBackgroundCollecting = MutableLiveData(false)
    val isBackgroundCollecting: LiveData<Boolean> = _isBackgroundCollecting

    private val _ppgWaveState = MutableLiveData(PpgWaveUiState())
    val ppgWaveState: LiveData<PpgWaveUiState> = _ppgWaveState

    private val defaultDeviceName: String by lazy {
        getApplication<Application>().getString(R.string.device_name)
    }

    private fun normalizeDeviceName(rawName: String?): String {
        val name = rawName?.trim().orEmpty()
        if (name.isBlank()) return defaultDeviceName
        if (name.equals("Unknown Device", ignoreCase = true) || name == "未知设备") return defaultDeviceName
        if (name.contains("智能戒指")) return defaultDeviceName
        return name
    }

    /**
     * 加载当前设备信息
     */
    fun loadCurrentDevice() {
        viewModelScope.launch {
            repository.getPrimaryDevice().collect { device ->
                // 如果当前已经有连接的设备，不覆盖
                val current = _currentDevice.value
                if (current != null && current.connectionState == ConnectionState.CONNECTED) {
                    android.util.Log.d("DeviceViewModel", "当前设备已连接，跳过数据库加载")
                    return@collect
                }
                
                if (device != null) {
                    // 从数据库加载（仅在未连接时）
                    val normalizedDevice = device.copy(
                        deviceName = normalizeDeviceName(device.deviceName)
                    )
                    _currentDevice.value = normalizedDevice
                    if (normalizedDevice.deviceName != device.deviceName) {
                        saveDeviceToDb(normalizedDevice)
                    }
                } else {
                    // 无设备，使用默认模拟数据
                    val mockDevice = DeviceInfo(
                        deviceId = "device_001",
                        deviceName = defaultDeviceName,
                        macAddress = "00:11:22:33:44:55",
                        batteryLevel = 85,
                        firmwareVersion = "v1.0.2",
                        connectionState = ConnectionState.DISCONNECTED,
                        lastSyncTime = System.currentTimeMillis() - 2 * 60 * 1000
                    )
                    _currentDevice.value = mockDevice
                }
            }
        }
    }
    
    /**
     * 保存设备信息
     */
    private fun saveDeviceToDb(device: DeviceInfo) {
        viewModelScope.launch {
            repository.saveDevice(device)
        }
    }

    /**
     * Connect device (supports both real BLE and demo mode).
     */
    fun connect() {
        val device = _currentDevice.value ?: return
        
        android.util.Log.d("DeviceViewModel", "开始连接设备: ${device.deviceName}, MAC: ${device.macAddress}")
        
        _statusMessage.value = "正在连接..."
        _currentDevice.value = device.copy(connectionState = ConnectionState.CONNECTING)
        
        if (DemoConfig.isDemoMode) {
            // ========== 演示模式：使用虚拟蓝牙管理器 ==========
            connectDemoDevice(device)
        } else {
            // ========== 真实模式：使用真实蓝牙管理器 ==========
            connectRealDevice(device)
        }
    }
    
    /**
     * 连接虚拟设备（演示模式）
     */
    private fun connectDemoDevice(device: DeviceInfo) {
        mockBleManager.connect(device.deviceId) { success ->
            if (success) {
                val connectedDevice = device.copy(
                    connectionState = ConnectionState.CONNECTED,
                    lastSyncTime = System.currentTimeMillis()
                )
                
                android.util.Log.d("DeviceViewModel", "虚拟设备连接成功")
                
                _currentDevice.value = connectedDevice
                _statusMessage.value = "演示设备已连接"
                saveDeviceToDb(connectedDevice)
                
                // 设置虚拟数据回调
                mockBleManager.setOnDataReceived { sensorData ->
                    // Update UI or record incoming demo data.
                    android.util.Log.d("DeviceViewModel", "收到虚拟数据: HR=${sensorData.heartRate}, SpO2=${sensorData.bloodOxygen}")
                }
            } else {
                val failedDevice = device.copy(connectionState = ConnectionState.DISCONNECTED)
                _currentDevice.value = failedDevice
                _statusMessage.value = "连接失败"
            }
        }
    }
    
    /**
     * 连接真实设备
     */
    private fun connectRealDevice(device: DeviceInfo) {
        bleConnectionManager.connect(
            deviceAddress = device.macAddress,
            onSuccess = {
                // 连接成功 - 切换到主线程更新UI
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val connectedDevice = device.copy(
                        connectionState = ConnectionState.CONNECTED,
                        lastSyncTime = System.currentTimeMillis()
                    )
                    
                    android.util.Log.d("DeviceViewModel", "连接成功，更新 UI: ${connectedDevice.deviceName}")
                    
                    _currentDevice.value = connectedDevice
                    _statusMessage.value = "连接成功"
                    
                    // 保存到数据库
                    saveDeviceToDb(connectedDevice)
                    
                    // Start data collection.
                    startDataCollection()
                }
            },
            onFailed = { error ->
                // 连接失败 - 切换到主线程更新UI
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _currentDevice.value = device.copy(connectionState = ConnectionState.DISCONNECTED)
                    _statusMessage.value = "连接失败：$error"
                }
            }
        )
    }

    /**
     * Disconnect device (supports both real BLE and demo mode).
     */
    fun disconnect() {
        if (DemoConfig.isDemoMode) {
            mockBleManager.disconnect()
        } else {
            bleConnectionManager.disconnect()
        }
        
        _currentDevice.value = _currentDevice.value?.copy(
            connectionState = ConnectionState.DISCONNECTED
        )
        _statusMessage.value = "已断开连接"
        
        // 停止数据采集
        stopDataCollection()
        stopBackgroundCollection()
        resetPpgWaveState()
        lastMotionSampleAtMs = 0L
    }

    /**
     * Start BLE scanning.
     */
    fun startScan() {
        _isScanning.value = true
        _statusMessage.value = "正在扫描设备..."
        _scannedDevices.value = emptyList()  // Clear previous scan list.
        
        val deviceList = mutableListOf<ScannedDevice>()
        
        bleConnectionManager.startScan(object : BleManager.BleManagerCallback {
            override fun onScanStarted() {
                _statusMessage.value = "正在扫描附近的智能戒指..."
            }

            override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
                // 找到设备，添加到列表
                val scannedDevice = ScannedDevice(
                    name = device.name ?: "未知设备",
                    address = device.address,
                    rssi = rssi
                )
                
                // 避免重复
                if (deviceList.none { it.address == scannedDevice.address }) {
                    deviceList.add(scannedDevice)
                    _scannedDevices.value = deviceList.toList()
                }
            }

            override fun onScanStopped() {
                _isScanning.value = false
                _statusMessage.value = "扫描完成，找到 ${deviceList.size} 个设备"
            }

            override fun onScanFailed(error: String) {
                _isScanning.value = false
                _statusMessage.value = "扫描失败：$error"
            }
        })
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        bleConnectionManager.stopScan()
        _isScanning.value = false
    }

    /**
     * 连接到指定设备（从扫描列表）
     */
    fun connectToDevice(device: ScannedDevice) {
        _statusMessage.value = "正在连接到 ${device.name}..."
        
        // 停止扫描
        stopScan()
        
        // 创建设备信息
        val deviceInfo = DeviceInfo(
            deviceId = "device_${device.address.replace(":", "")}",
            deviceName = normalizeDeviceName(device.name),
            macAddress = device.address,
            batteryLevel = 85,  // 初始值，连接后会更新
            firmwareVersion = "v1.0.2",  // Will refresh after connection.
            connectionState = ConnectionState.CONNECTING,
            lastSyncTime = 0L
        )
        
        _currentDevice.value = deviceInfo
        
        // 真实连接
        bleConnectionManager.connect(
            deviceAddress = device.address,
            onSuccess = {
                // 连接成功 - 切换到主线程更新UI
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val connectedDevice = deviceInfo.copy(
                        connectionState = ConnectionState.CONNECTED,
                        lastSyncTime = System.currentTimeMillis()
                    )
                    _currentDevice.value = connectedDevice
                    _statusMessage.value = "连接成功"
                    _scannedDevices.value = emptyList()  // 清空扫描列表
                    
                    // 保存到数据库
                    saveDeviceToDb(connectedDevice)
                    
                    // Start data collection.
                    startDataCollection()
                }
            },
            onFailed = { error ->
                // 连接失败 - 切换到主线程更新UI
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _currentDevice.value = deviceInfo.copy(connectionState = ConnectionState.DISCONNECTED)
                    _statusMessage.value = "连接失败：$error"
                }
            }
        )
    }
    
    /**
     * Start sensor data collection.
     */
    private fun startDataCollection() {
        // 1. Set sensor data callback.
        bleConnectionManager.setOnDataReceived { sensorData ->
            handleSensorData(sensorData)
        }

        // 2. 设置协议回调，用于处理命令应答和非传感器数据
        bleConnectionManager.setProtocolCallback(object : BleManager.ProtocolCallback {
            override fun onBatteryStatus(chargingStatus: Int, batteryLevel: Int) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _currentDevice.value = _currentDevice.value?.copy(batteryLevel = batteryLevel)
                    _statusMessage.value = "电量更新: $batteryLevel%"
                }
            }

            override fun onFirmwareVersion(version: Int) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val versionString = "v${version shr 8}.${version and 0xFF}"
                    _currentDevice.value = _currentDevice.value?.copy(firmwareVersion = versionString)
                    _statusMessage.value = "固件版本: $versionString"
                }
            }

            override fun onWorkParamsRaw(raw: ByteArray) {
                val params = Hi90BWorkParamsCodec.decode(raw)
                val text = Hi90BWorkParamsCodec.debugString(params)
                Log.i("DeviceViewModel", "读取到工作参数:\n$text")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _statusMessage.value = "工作参数:\n$text"
                }
            }
        })
    }
    
    /**
     * 停止数据采集
     */
    private fun stopDataCollection() {
        // 清空数据回调
        bleConnectionManager.setOnDataReceived { }
    }

    /**
     * 启动后台采集服务（显式触发，避免与前台连接抢占）
     */
    fun startBackgroundCollection() {
        if (DemoConfig.isDemoMode) {
            _statusMessage.value = "演示模式不需要后台采集服务"
            return
        }

        val device = _currentDevice.value
        if (device == null || device.macAddress.isBlank()) {
            _statusMessage.value = "请先选择设备"
            return
        }

        if (bleConnectionManager.isConnected()) {
            _statusMessage.value = "请先断开前台连接，再启动后台采集"
            return
        }

        val intent = Intent(getApplication(), DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_START_COLLECTION
            putExtra(DataCollectionService.EXTRA_DEVICE_ADDRESS, device.macAddress)
            putExtra(DataCollectionService.EXTRA_DEVICE_NAME, device.deviceName)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
        _isBackgroundCollecting.value = true
        _statusMessage.value = "后台采集已启动"
    }

    /**
     * 停止后台采集服务
     */
    fun stopBackgroundCollection() {
        val intent = Intent(getApplication(), DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_STOP_COLLECTION
        }
        getApplication<Application>().startService(intent)
        _isBackgroundCollecting.value = false
    }
    
    /**
     * Process sensor data.
     */
    private var latestSensorData = SensorData(0L, 0, 0, 0f, Triple(0f, 0f, 0f), Triple(0f, 0f, 0f), 0f)
    private var lastMappedBodyTemp: Float? = null
    private var lastMetricsPersistTimeMs: Long = 0L
    private var lastRawHrvAtMs: Long = 0L
    private var lastMotionSampleAtMs: Long = 0L
    private var lastMotionDynamicAcc: Float? = null
    private var lastMotionComputedAtMs: Long = 0L
    private var smoothedMotionIntensity: Float = 0f
    private val hrvFallbackEstimator = HrvFallbackEstimator()
    private val ppgWaveBuffer = ArrayDeque<Pair<Long, Float>>()
    private var lastPpgUiUpdateAtMs = 0L
    private var lastPpgPersistTimeMs = 0L

    private fun handleSensorData(data: SensorData) {
        val now = System.currentTimeMillis()
        if (data.hrv > 0) {
            lastRawHrvAtMs = now
        }
        val hasMotionSample =
            data.accelerometer != Triple(0f, 0f, 0f) || data.gyroscope != Triple(0f, 0f, 0f)
        if (hasMotionSample) {
            lastMotionSampleAtMs = now
        }

        // Merge fresh data into the latest snapshot to avoid overriding valid fields with stale zeros.
        latestSensorData = latestSensorData.copy(
            timestamp = now,
            heartRate = if (data.heartRate > 0) data.heartRate else latestSensorData.heartRate,
            bloodOxygen = if (data.bloodOxygen > 0) data.bloodOxygen else latestSensorData.bloodOxygen,
            temperature = if (data.temperature > 0f) data.temperature else latestSensorData.temperature,
            hrv = if (data.hrv > 0) data.hrv else latestSensorData.hrv,
            steps = 0,
            accelerometer = if (data.accelerometer != Triple(0f, 0f, 0f)) data.accelerometer else latestSensorData.accelerometer,
            gyroscope = if (data.gyroscope != Triple(0f, 0f, 0f)) data.gyroscope else latestSensorData.gyroscope,
            ppgValue = if (data.ppgValue > 0f) data.ppgValue else latestSensorData.ppgValue
        )

        // 使用聚合后的完整数据进行后续操作
        val aggregatedData = latestSensorData
        val rawHrvFresh = aggregatedData.hrv > 0 && (now - lastRawHrvAtMs) <= HRV_STALE_TIMEOUT_MS
        val hrvEstimatorInput = if (rawHrvFresh) aggregatedData else aggregatedData.copy(hrv = 0)
        val resolvedHrv = hrvFallbackEstimator.resolve(hrvEstimatorInput)
        val isEstimatedHrv = !rawHrvFresh && resolvedHrv > 0
        val motionFresh = (now - lastMotionSampleAtMs) <= MOTION_STALE_TIMEOUT_MS
        val motionIntensity = if (motionFresh) {
            estimateMotionIntensity(
                acc = aggregatedData.accelerometer,
                gyro = aggregatedData.gyroscope,
                now = now
            )
        } else {
            resetMotionIntensity()
            0f
        }

        if (data.ppgValue > 0f) {
            ppgWaveBuffer.addLast(now to data.ppgValue)
            trimPpgWaveBuffer(now)
            if (now - lastPpgUiUpdateAtMs >= PPG_UI_UPDATE_INTERVAL_MS) {
                lastPpgUiUpdateAtMs = now
                publishPpgWaveState()
            }
        }

        if (data.ppgValue > 0f && now - lastPpgPersistTimeMs >= PPG_PERSIST_INTERVAL_MS) {
            lastPpgPersistTimeMs = now
            viewModelScope.launch {
                sleepRepository.savePpgSample(
                    sleepRecordId = "realtime",
                    timestamp = now,
                    ppgValue = data.ppgValue
                )
            }
        }

        if (now - lastMetricsPersistTimeMs < METRICS_PERSIST_INTERVAL_MS) {
            return
        }

        if (
            aggregatedData.heartRate <= 0 &&
            aggregatedData.bloodOxygen <= 0 &&
            aggregatedData.temperature <= 0f &&
            resolvedHrv <= 0 &&
            motionIntensity <= 0f
        ) {
            return
        }
        lastMetricsPersistTimeMs = now

        // 调试阶段：直接把采集到的样本写入 Room，用于趋势页实时展示
        viewModelScope.launch {
            val hr = aggregatedData.heartRate
            val spo2 = aggregatedData.bloodOxygen
            val tempOuter = aggregatedData.temperature
            val hrv = resolvedHrv
            val temp = com.example.newstart.util.TemperatureMapper.mapOuterToBody(
                outerTemp = tempOuter,
                lastMapped = lastMappedBodyTemp
            )
            if (temp > 0f) {
                lastMappedBodyTemp = temp
            }
            val hrvBaseline = 50
            val hrvRecoveryRate = if (hrv > 0) {
                (hrv - hrvBaseline).toFloat() / hrvBaseline * 100f
            } else {
                0f
            }
            val stressLevel = if (hrv > 0) {
                HRVAnalyzer.calculateStressLevel(hrv, hrvBaseline)
            } else {
                com.example.newstart.data.StressLevel.MODERATE
            }

            val oxygenStability = when {
                spo2 <= 0 -> "未知"
                spo2 >= 95 -> "稳定"
                spo2 >= 90 -> "轻微波动"
                else -> "偏低"
            }

            val temperatureStatus = when {
                temp <= 0f -> "未知"
                temp < 36.0f -> "偏低"
                temp <= 37.5f -> "正常"
                temp <= 38.0f -> "偏高"
                else -> "发热"
            }

            val metrics = com.example.newstart.data.HealthMetrics(
                heartRate = com.example.newstart.data.HeartRateData(
                    current = hr,
                    avg = hr,
                    min = hr,
                    max = hr,
                    trend = com.example.newstart.data.Trend.STABLE,
                    isAbnormal = false
                ),
                bloodOxygen = com.example.newstart.data.BloodOxygenData(
                    current = spo2,
                    avg = spo2,
                    min = spo2,
                    stability = oxygenStability,
                    isAbnormal = spo2 in 1..89
                ),
                temperature = com.example.newstart.data.TemperatureData(
                    current = temp,
                    avg = temp,
                    status = temperatureStatus,
                    isAbnormal = temp > 37.5f
                ),
                hrv = com.example.newstart.data.HRVData(
                    current = hrv,
                    baseline = hrvBaseline,
                    recoveryRate = hrvRecoveryRate,
                    trend = com.example.newstart.data.Trend.STABLE,
                    stressLevel = stressLevel
                )
            )

            sleepRepository.saveHealthMetrics(
                sleepRecordId = "realtime",
                metrics = metrics,
                stepsSample = 0,
                accMagnitudeSample = motionIntensity
            )
            if (isEstimatedHrv) {
                Log.d("DeviceViewModel", "原始HRV缺失，使用估算值: $hrv ms")
            }
            Log.d("DeviceViewModel", "已写入 health_metrics 样本: hr=$hr spo2=$spo2 temp=$temp hrv=$hrv motion=$motionIntensity")
        }

        android.util.Log.d(
            "DeviceViewModel",
            "聚合后数据: 心率=${aggregatedData.heartRate}, 血氧=${aggregatedData.bloodOxygen}, 体温=${aggregatedData.temperature}, HRV=$resolvedHrv${if (isEstimatedHrv) "(估算)" else ""}"
        )
    }
    
    private fun estimateMotionIntensity(
        acc: Triple<Float, Float, Float>,
        gyro: Triple<Float, Float, Float>,
        now: Long
    ): Float {
        val (ax, ay, az) = acc
        val (gx, gy, gz) = gyro

        val accMag = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val gyroMag = kotlin.math.sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        val dynamicAcc = kotlin.math.abs(accMag - 1f)

        val dtSeconds = if (lastMotionComputedAtMs > 0L) {
            ((now - lastMotionComputedAtMs).coerceIn(50L, 2000L) / 1000f)
        } else {
            0f
        }
        val jerk = if (lastMotionDynamicAcc != null && dtSeconds > 0f) {
            kotlin.math.abs(dynamicAcc - lastMotionDynamicAcc!!) / dtSeconds
        } else {
            0f
        }

        // 融合算法:
        // 1) dynamicAcc: 去重力后线性加速度，反映位移强度
        // 2) gyroMag: 角速度幅值，反映转动/姿态变化
        // 3) jerk: 动态加速度变化率，抑制“静态抖动”
        val accScore = ((dynamicAcc - 0.015f) / 0.55f).coerceIn(0f, 1f)
        val gyroScore = ((gyroMag - 2f) / 140f).coerceIn(0f, 1f)
        val jerkScore = (jerk / 8f).coerceIn(0f, 1f)
        val rawScore = (accScore * 0.55f + gyroScore * 0.30f + jerkScore * 0.15f).coerceIn(0f, 1f)

        smoothedMotionIntensity = (smoothedMotionIntensity * 0.70f + rawScore * 0.30f).coerceIn(0f, 1f)
        lastMotionDynamicAcc = dynamicAcc
        lastMotionComputedAtMs = now
        return (smoothedMotionIntensity * 20f).coerceIn(0f, 20f)
    }

    private fun resetMotionIntensity() {
        smoothedMotionIntensity = 0f
        lastMotionDynamicAcc = null
        lastMotionComputedAtMs = 0L
    }

    private fun trimPpgWaveBuffer(now: Long) {
        while (ppgWaveBuffer.isNotEmpty() && now - ppgWaveBuffer.first().first > PPG_WAVE_WINDOW_MS) {
            ppgWaveBuffer.removeFirst()
        }
    }

    private fun publishPpgWaveState() {
        if (ppgWaveBuffer.size < 8) {
            _ppgWaveState.postValue(PpgWaveUiState())
            return
        }

        val sorted = ppgWaveBuffer.toList().sortedBy { it.first }
        val downsampled = downsamplePpg(sorted, PPG_MAX_POINTS)
        val normalizedWave = normalizeWaveForChart(downsampled.map { it.second })
        val peakMetrics = PpgPeakDetector.analyze(sorted)

        _ppgWaveState.postValue(
            PpgWaveUiState(
                values = normalizedWave,
                sampleCount = sorted.size,
                peakCount = peakMetrics?.peakTimestamps?.size ?: 0,
                estimatedBpm = peakMetrics?.estimatedHeartRateBpm?.toInt(),
                signalQuality = peakMetrics?.signalQuality ?: 0f
            )
        )
    }

    private fun downsamplePpg(samples: List<Pair<Long, Float>>, maxPoints: Int): List<Pair<Long, Float>> {
        if (samples.size <= maxPoints) return samples

        val step = samples.size.toFloat() / maxPoints.toFloat()
        var cursor = 0f
        val result = ArrayList<Pair<Long, Float>>(maxPoints)
        repeat(maxPoints) {
            val index = cursor.toInt().coerceIn(0, samples.lastIndex)
            result.add(samples[index])
            cursor += step
        }
        return result
    }

    private fun normalizeWaveForChart(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: min
        val range = max - min
        if (range <= 1e-3f) {
            return List(values.size) { 50f }
        }
        return values.map { ((it - min) / range) * 100f }
    }

    private fun resetPpgWaveState() {
        ppgWaveBuffer.clear()
        lastPpgUiUpdateAtMs = 0L
        lastPpgPersistTimeMs = 0L
        _ppgWaveState.postValue(PpgWaveUiState())
    }

    fun readWorkParams() {
        bleConnectionManager.sendCommand(com.example.newstart.bluetooth.Hi90BCommandBuilder.readWorkParams())
        _statusMessage.value = "正在读取工作参数..."
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        resetPpgWaveState()
        resetMotionIntensity()
        bleConnectionManager.cleanup()
    }
}

/**
 * 扫描到的设备
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int  // 信号强度
)

data class PpgWaveUiState(
    val values: List<Float> = emptyList(),
    val sampleCount: Int = 0,
    val peakCount: Int = 0,
    val estimatedBpm: Int? = null,
    val signalQuality: Float = 0f
)
