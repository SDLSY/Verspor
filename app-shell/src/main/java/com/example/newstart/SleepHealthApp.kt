package com.example.newstart

import android.app.Application
import com.example.newstart.ai.EdgeLlmOnDeviceModel
import com.example.newstart.data.DataManager
import com.example.newstart.network.ApiClient
import com.example.newstart.xfyun.XfyunCredentialBootstrap

/**
 * 应用程序类
 */
class SleepHealthApp : Application() {
    
    lateinit var dataManager: DataManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化数据管理器
        dataManager = DataManager(this)
        ApiClient.init(this)
        EdgeLlmOnDeviceModel.init(this)
        XfyunCredentialBootstrap.seedIfPresent(this)
        
        // 初始化模拟数据（首次使用时）
        dataManager.initializeMockData()
    }
}
