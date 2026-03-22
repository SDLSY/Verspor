package com.example.newstart.demo

import android.content.Context
import android.content.SharedPreferences

/**
 * 演示模式配置管理。
 * 用于在没有真实硬件的情况下驱动演示链路。
 */
object DemoConfig {

    private const val PREFS_NAME = "demo_mode_prefs"
    private const val KEY_DEMO_MODE = "is_demo_mode"
    private const val DEFAULT_DEMO_MODE = false

    private var prefs: SharedPreferences? = null

    /**
     * 在 Application 或 MainActivity 中初始化。
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 演示模式开关。
     */
    var isDemoMode: Boolean
        get() = prefs?.getBoolean(KEY_DEMO_MODE, DEFAULT_DEMO_MODE) ?: DEFAULT_DEMO_MODE
        set(value) {
            prefs?.edit()?.putBoolean(KEY_DEMO_MODE, value)?.apply()
        }

    // ========== 虚拟设备信息 ==========

    const val DEMO_DEVICE_NAME = "长庚环 智能戒指"
    const val DEMO_DEVICE_MAC = "DE:MO:AA:BB:CC:DD"
    const val DEMO_FIRMWARE_VERSION = "v2.1.0-demo"
    const val DEMO_BATTERY_LEVEL = 85

    // ========== 数据生成配置 ==========

    /** 数据更新间隔，单位毫秒。 */
    const val DATA_UPDATE_INTERVAL_MS = 2000L

    /** 自动连接延迟，单位毫秒。 */
    const val AUTO_CONNECT_DELAY_MS = 1500L

    /** 扫描延迟，单位毫秒。 */
    const val SCAN_DELAY_MS = 1000L

    // ========== 数据范围 ==========

    val HEART_RATE_RANGE = 55..75
    val SPO2_RANGE = 95..99
    val TEMP_RANGE = 36.0f..37.0f
    val HRV_RANGE = 40..80
    val STEPS_RANGE = 3000..12000

    // ========== UI 配置 ==========

    const val DEMO_MODE_LABEL = "演示模式"
    const val DEMO_MODE_DESCRIPTION = "当前为演示模式，数据为模拟生成。\n实际使用时请连接真实设备。"
    const val DEMO_BADGE_COLOR = "#FF6B35"

    // ========== 历史数据配置 ==========

    const val PRELOAD_DAYS = 7
}
