package com.example.newstart.demo

import android.content.Context
import android.content.SharedPreferences

/**
 * 演示模式配置管理。
 * 归档副本，保持与当前模块化实现一致。
 */
object DemoConfig {

    private const val PREFS_NAME = "demo_mode_prefs"
    private const val KEY_DEMO_MODE = "is_demo_mode"
    private const val DEFAULT_DEMO_MODE = false

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var isDemoMode: Boolean
        get() = prefs?.getBoolean(KEY_DEMO_MODE, DEFAULT_DEMO_MODE) ?: DEFAULT_DEMO_MODE
        set(value) {
            prefs?.edit()?.putBoolean(KEY_DEMO_MODE, value)?.apply()
        }

    const val DEMO_DEVICE_NAME = "长庚环 智能戒指"
    const val DEMO_DEVICE_MAC = "DE:MO:AA:BB:CC:DD"
    const val DEMO_FIRMWARE_VERSION = "v2.1.0-demo"
    const val DEMO_BATTERY_LEVEL = 85

    const val DATA_UPDATE_INTERVAL_MS = 2000L
    const val AUTO_CONNECT_DELAY_MS = 1500L
    const val SCAN_DELAY_MS = 1000L

    val HEART_RATE_RANGE = 55..75
    val SPO2_RANGE = 95..99
    val TEMP_RANGE = 36.0f..37.0f
    val HRV_RANGE = 40..80
    val STEPS_RANGE = 3000..12000

    const val DEMO_MODE_LABEL = "演示模式"
    const val DEMO_MODE_DESCRIPTION = "当前为演示模式，数据为模拟生成。\n实际使用时请连接真实设备。"
    const val DEMO_BADGE_COLOR = "#FF6B35"
    const val PRELOAD_DAYS = 7
}
