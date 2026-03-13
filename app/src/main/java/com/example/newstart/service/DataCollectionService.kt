package com.example.newstart.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.newstart.MainActivity
import com.example.newstart.R
import com.example.newstart.bluetooth.BleConnectionManager
import com.example.newstart.data.SensorData
import com.example.newstart.database.AppDatabase
import com.example.newstart.repository.SleepRepository
import com.example.newstart.util.HRVAnalyzer
import com.example.newstart.util.HrvFallbackEstimator
import com.example.newstart.util.TemperatureMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 数据采集后台服务
 * 持续接收智能戒指数据，即使应用在后台
 */
class DataCollectionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "data_collection_channel"
        private const val CHANNEL_NAME = "数据采集服务"
        private const val PERSIST_INTERVAL_MS = 2000L
        private const val PPG_PERSIST_INTERVAL_MS = 500L
        private const val HRV_STALE_TIMEOUT_MS = 15_000L
        private const val MOTION_STALE_TIMEOUT_MS = 20_000L
        
        const val ACTION_START_COLLECTION = "com.example.newstart.START_COLLECTION"
        const val ACTION_STOP_COLLECTION = "com.example.newstart.STOP_COLLECTION"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }

    private lateinit var bleConnectionManager: BleConnectionManager
    private lateinit var sleepRepository: SleepRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var isCollecting = false
    private var currentDeviceAddress: String? = null
    private var dataCollectionCount = 0
    private var lastPersistTimeMs = 0L
    private var lastPpgPersistTimeMs = 0L
    private var lastRawHrvAtMs = 0L
    private var lastMotionSampleAtMs = 0L
    private var lastMappedBodyTemp: Float? = null
    private val hrvFallbackEstimator = HrvFallbackEstimator()
    private var latestSensorData = SensorData(
        timestamp = 0L,
        heartRate = 0,
        bloodOxygen = 0,
        temperature = 0f,
        accelerometer = Triple(0f, 0f, 0f),
        gyroscope = Triple(0f, 0f, 0f),
        ppgValue = 0f,
        hrv = 0,
        steps = 0
    )

    override fun onCreate() {
        super.onCreate()
        
        // 初始化蓝牙管理器
        bleConnectionManager = BleConnectionManager(this)
        
        // 初始化Repository
        val database = AppDatabase.getDatabase(this)
        sleepRepository = SleepRepository(
            database.sleepDataDao(),
            database.healthMetricsDao(),
            database.recoveryScoreDao(),
            database.ppgSampleDao()
        )
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 设置数据接收回调
        bleConnectionManager.setOnDataReceived { sensorData ->
            handleSensorData(sensorData)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COLLECTION -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (deviceAddress.isNullOrBlank()) {
                    updateNotification("未提供设备地址，无法开始采集")
                    android.util.Log.e("DataCollection", "ACTION_START_COLLECTION 缺少设备地址")
                } else {
                    startCollection(deviceAddress)
                }
            }
            ACTION_STOP_COLLECTION -> {
                stopCollection()
                stopSelf()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCollection()
        bleConnectionManager.cleanup()
        serviceScope.cancel()
    }

    /**
     * 启动采集流程：连接指定设备并接收实时数据
     */
    private fun startCollection(deviceAddress: String) {
        if (isCollecting && currentDeviceAddress == deviceAddress && bleConnectionManager.isConnected()) {
            updateNotification("数据采集中...")
            return
        }

        // 切换采集目标时，先停止旧连接
        if (isCollecting) {
            stopCollection()
        }

        currentDeviceAddress = deviceAddress
        updateNotification("正在连接设备...")

        bleConnectionManager.connect(
            deviceAddress = deviceAddress,
            onSuccess = {
                isCollecting = true
                updateNotification("数据采集中...")
                android.util.Log.i("DataCollection", "后台采集连接成功: $deviceAddress")
            },
            onFailed = { error ->
                isCollecting = false
                updateNotification("连接失败: $error")
                android.util.Log.e("DataCollection", "后台采集连接失败: $error")
            }
        )
    }

    /**
     * 停止采集流程
     */
    private fun stopCollection() {
        isCollecting = false
        currentDeviceAddress = null
        bleConnectionManager.disconnect()
        lastRawHrvAtMs = 0L
        lastPpgPersistTimeMs = 0L
        lastMotionSampleAtMs = 0L
    }

    /**
     * 处理传感器数据。
     */
    private fun handleSensorData(data: SensorData) {
        dataCollectionCount++
        val now = System.currentTimeMillis()
        if (data.hrv > 0) {
            lastRawHrvAtMs = now
        }
        val hasMotionSample =
            data.accelerometer != Triple(0f, 0f, 0f) || data.gyroscope != Triple(0f, 0f, 0f)
        if (hasMotionSample) {
            lastMotionSampleAtMs = now
        }

        // 聚合数据：避免字段被0覆盖
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

        val rawHrvFresh = latestSensorData.hrv > 0 && (now - lastRawHrvAtMs) <= HRV_STALE_TIMEOUT_MS
        val hrvEstimatorInput = if (rawHrvFresh) latestSensorData else latestSensorData.copy(hrv = 0)
        val resolvedHrv = hrvFallbackEstimator.resolve(hrvEstimatorInput)
        val isEstimatedHrv = !rawHrvFresh && resolvedHrv > 0

        if (data.ppgValue > 0f && now - lastPpgPersistTimeMs >= PPG_PERSIST_INTERVAL_MS) {
            lastPpgPersistTimeMs = now
            serviceScope.launch {
                sleepRepository.savePpgSample(
                    sleepRecordId = "realtime",
                    timestamp = now,
                    ppgValue = data.ppgValue
                )
            }
        }

        if (now - lastPersistTimeMs >= PERSIST_INTERVAL_MS) {
            lastPersistTimeMs = now
            serviceScope.launch {
                saveHealthMetricsSample(latestSensorData, resolvedHrv, isEstimatedHrv)
            }
        }

        // 更新通知显示
        if (dataCollectionCount % 60 == 0) {  // 每60条数据更新一次通知
            updateNotification("已采集 ${dataCollectionCount} 条数据")
        }

        android.util.Log.d(
            "DataCollection",
            "心率:${data.heartRate} 血氧:${data.bloodOxygen} 体温:${data.temperature}"
        )
    }

    private suspend fun saveHealthMetricsSample(data: SensorData, resolvedHrv: Int, isEstimatedHrv: Boolean) {
        val sleepRecordId = "realtime"

        val hr = data.heartRate
        val spo2 = data.bloodOxygen
        val tempOuter = data.temperature
        val temp = if (tempOuter > 0f) {
            TemperatureMapper.mapOuterToBody(tempOuter, lastMappedBodyTemp).also {
                lastMappedBodyTemp = it
            }
        } else {
            0f
        }
        val hrv = if (resolvedHrv > 0) resolvedHrv else data.hrv
        val motionFresh = (System.currentTimeMillis() - lastMotionSampleAtMs) <= MOTION_STALE_TIMEOUT_MS
        val accMagnitude = if (motionFresh) {
            motionMagnitude(data.accelerometer, data.gyroscope)
        } else {
            0f
        }

        if (hr <= 0 && spo2 <= 0 && temp <= 0f && hrv <= 0 && accMagnitude <= 0f) {
            return
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
            sleepRecordId = sleepRecordId,
            metrics = metrics,
            stepsSample = 0,
            accMagnitudeSample = accMagnitude
        )

        if (isEstimatedHrv) {
            android.util.Log.d("DataCollection", "原始 HRV 缺失，使用估算值: $hrv ms")
        }
    }

    private fun motionMagnitude(
        acc: Triple<Float, Float, Float>,
        gyro: Triple<Float, Float, Float>
    ): Float {
        val (ax, ay, az) = acc
        val (gx, gy, gz) = gyro

        val accMag = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val gyroMag = kotlin.math.sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()

        val dynamicAcc = kotlin.math.abs(accMag - 1f)
        return (dynamicAcc * 2.4f + gyroMag * 0.06f).coerceIn(0f, 20f)
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "智能戒指数据采集服务"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(content: String = "正在采集健康数据..."): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("智能戒指睡眠管家")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification(content))
    }
}


