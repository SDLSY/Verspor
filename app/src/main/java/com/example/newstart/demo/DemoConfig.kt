package com.example.newstart.demo

import android.content.Context
import android.content.SharedPreferences

/**
 * 演示模式配置管理
 * 用于在没有硬件设备的情况下演示应用功能
 */
object DemoConfig {
    
    private const val PREFS_NAME = "demo_mode_prefs"
    private const val KEY_DEMO_MODE = "is_demo_mode"
    private const val DEFAULT_DEMO_MODE = false
    
    private var prefs: SharedPreferences? = null
    
    /**
     * 初始化配置（在 Application 或 MainActivity 中调用）
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    /**
     * 演示模式开关（默认开启）
     */
    var isDemoMode: Boolean
        get() = prefs?.getBoolean(KEY_DEMO_MODE, DEFAULT_DEMO_MODE) ?: DEFAULT_DEMO_MODE
        set(value) {
            prefs?.edit()?.putBoolean(KEY_DEMO_MODE, value)?.apply()
        }
    
    // ========== 虚拟设备信息 ==========
    
    const val DEMO_DEVICE_NAME = "长庚环 Demo"
    const val DEMO_DEVICE_MAC = "DE:MO:AA:BB:CC:DD"
    const val DEMO_FIRMWARE_VERSION = "v2.1.0-demo"
    const val DEMO_BATTERY_LEVEL = 85
    
    // ========== 数据生成配置 ==========
    
    /** 数据更新间隔（毫秒） */
    const val DATA_UPDATE_INTERVAL_MS = 2000L  // 2秒更新一次
    
    /** 自动连接延迟（毫秒） */
    const val AUTO_CONNECT_DELAY_MS = 1500L    // 模拟连接过程
    
    /** 扫描延迟（毫秒） */
    const val SCAN_DELAY_MS = 1000L
    
    // ========== 数据范围配置 ==========
    
    /** 心率范围 (bpm) */
    val HEART_RATE_RANGE = 55..75
    
    /** 血氧范围 (%) */
    val SPO2_RANGE = 95..99
    
    /** 体温范围 (°C) */
    val TEMP_RANGE = 36.0f..37.0f
    
    /** HRV 范围 (ms) */
    val HRV_RANGE = 40..80
    
    /** 步数范围（每天） */
    val STEPS_RANGE = 3000..12000
    
    // ========== UI 配置 ==========
    
    /** 演示模式提示文字 */
    const val DEMO_MODE_LABEL = "演示模式"
    
    /** 演示模式说明 */
    const val DEMO_MODE_DESCRIPTION = "当前为演示模式，数据为模拟生成\n实际使用时请连接真实设备"
    
    /** 演示模式徽章颜色 */
    const val DEMO_BADGE_COLOR = "#FF6B35"  // 橙色
    
    // ========== 历史数据配置 ==========
    
    /** 预加载的历史天数 */
    const val PRELOAD_DAYS = 7
}
