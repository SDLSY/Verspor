package com.example.newstart.ui.profile

import android.content.Context

data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val reportAlertsEnabled: Boolean = true,
    val interventionRemindersEnabled: Boolean = true,
    val avatarSpeechEnabled: Boolean = true
)

object ProfileSettingsStore {

    private const val PREFS_NAME = "profile_settings"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_REPORT_ALERTS_ENABLED = "report_alerts_enabled"
    private const val KEY_INTERVENTION_REMINDERS_ENABLED = "intervention_reminders_enabled"
    private const val KEY_AVATAR_SPEECH_ENABLED = "avatar_speech_enabled"

    fun getNotificationSettings(context: Context): NotificationSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotificationSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            reportAlertsEnabled = prefs.getBoolean(KEY_REPORT_ALERTS_ENABLED, true),
            interventionRemindersEnabled = prefs.getBoolean(KEY_INTERVENTION_REMINDERS_ENABLED, true),
            avatarSpeechEnabled = prefs.getBoolean(KEY_AVATAR_SPEECH_ENABLED, true)
        )
    }

    fun saveNotificationSettings(context: Context, settings: NotificationSettings) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
            .putBoolean(KEY_REPORT_ALERTS_ENABLED, settings.reportAlertsEnabled)
            .putBoolean(KEY_INTERVENTION_REMINDERS_ENABLED, settings.interventionRemindersEnabled)
            .putBoolean(KEY_AVATAR_SPEECH_ENABLED, settings.avatarSpeechEnabled)
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
