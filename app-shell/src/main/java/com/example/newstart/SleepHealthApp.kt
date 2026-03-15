package com.example.newstart

import android.app.Application
import android.util.Log
import com.example.newstart.ai.EdgeLlmOnDeviceModel
import com.example.newstart.data.DataManager
import com.example.newstart.network.ApiClient
import com.example.newstart.repository.DemoBootstrapCoordinator
import com.example.newstart.xfyun.XfyunCredentialBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用程序入口。
 */
class SleepHealthApp : Application() {

    companion object {
        private const val TAG = "SleepHealthApp"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var dataManager: DataManager
        private set

    override fun onCreate() {
        super.onCreate()

        dataManager = DataManager(this)
        ApiClient.init(this)
        EdgeLlmOnDeviceModel.init(this)
        XfyunCredentialBootstrap.seedIfPresent(this)

        // 保留现有首启 mock 初始化逻辑，演示账号会在后续导入时覆盖本地库。
        dataManager.initializeMockData()
        syncDemoBootstrapIfNeeded()
    }

    private fun syncDemoBootstrapIfNeeded() {
        applicationScope.launch {
            val result = DemoBootstrapCoordinator(this@SleepHealthApp).bootstrapForCurrentSession()
            result.onSuccess {
                if (it.isDemoAccount && it.applied) {
                    Log.i(TAG, "demo bootstrap applied on startup: ${it.scenario}/${it.version}")
                }
            }.onFailure {
                Log.w(TAG, "demo bootstrap sync skipped: ${it.message}", it)
            }
        }
    }
}
