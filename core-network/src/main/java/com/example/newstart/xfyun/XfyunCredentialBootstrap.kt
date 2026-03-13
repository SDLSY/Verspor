package com.example.newstart.xfyun

import android.content.Context
import android.content.SharedPreferences

object XfyunCredentialBootstrap {

    private const val PREF_NAME = "settings"

    fun seedIfPresent(context: Context) {
        val credentials = XfyunConfig.aiuiCredentials
        if (!credentials.isReady) return
        prefs(context).edit()
            .putString("pref_appid", credentials.appId)
            .putString("pref_key", credentials.apiKey)
            .putString("pref_api_secret", credentials.apiSecret)
            .putString("pref_scene", XfyunConfig.aiuiScene)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}
