package com.example.newstart.network

import android.content.Context

data class CloudSession(
    val token: String,
    val userId: String,
    val username: String,
    val email: String
)

object CloudSessionStore {

    private const val PREFS_NAME = "cloud_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun save(session: CloudSession) {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun get(): CloudSession? {
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val userId = prefs.getString(KEY_USER_ID, null)?.trim().orEmpty()
        val username = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val email = prefs.getString(KEY_EMAIL, null)?.trim().orEmpty()
        if (token.isEmpty() || userId.isEmpty()) {
            return null
        }
        val finalUsername = if (username.isNotEmpty()) username else email.substringBefore("@", userId)
        return CloudSession(token = token, userId = userId, username = finalUsername, email = email)
    }

    fun clear() {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
