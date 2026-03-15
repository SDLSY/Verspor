package com.example.newstart.ui.profile

import android.content.Context

data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val reportAlertsEnabled: Boolean = true,
    val interventionRemindersEnabled: Boolean = true,
    val avatarSpeechEnabled: Boolean = true,
    val hapticsEnabled: Boolean = false,
    val preferredHapticMode: String = "BREATH",
    val adaptiveSoundscapeEnabled: Boolean = true
)

object ProfileSettingsStore {

    private const val PREFS_NAME = "profile_settings"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_REPORT_ALERTS_ENABLED = "report_alerts_enabled"
    private const val KEY_INTERVENTION_REMINDERS_ENABLED = "intervention_reminders_enabled"
    private const val KEY_AVATAR_SPEECH_ENABLED = "avatar_speech_enabled"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_PREFERRED_HAPTIC_MODE = "preferred_haptic_mode"
    private const val KEY_ADAPTIVE_SOUNDSCAPE_ENABLED = "adaptive_soundscape_enabled"

    fun getNotificationSettings(context: Context): NotificationSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotificationSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            reportAlertsEnabled = prefs.getBoolean(KEY_REPORT_ALERTS_ENABLED, true),
            interventionRemindersEnabled = prefs.getBoolean(KEY_INTERVENTION_REMINDERS_ENABLED, true),
            avatarSpeechEnabled = prefs.getBoolean(KEY_AVATAR_SPEECH_ENABLED, true),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, false),
            preferredHapticMode = prefs.getString(KEY_PREFERRED_HAPTIC_MODE, "BREATH") ?: "BREATH",
            adaptiveSoundscapeEnabled = prefs.getBoolean(KEY_ADAPTIVE_SOUNDSCAPE_ENABLED, true)
        )
    }

    fun saveNotificationSettings(context: Context, settings: NotificationSettings) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
            .putBoolean(KEY_REPORT_ALERTS_ENABLED, settings.reportAlertsEnabled)
            .putBoolean(KEY_INTERVENTION_REMINDERS_ENABLED, settings.interventionRemindersEnabled)
            .putBoolean(KEY_AVATAR_SPEECH_ENABLED, settings.avatarSpeechEnabled)
            .putBoolean(KEY_HAPTICS_ENABLED, settings.hapticsEnabled)
            .putString(KEY_PREFERRED_HAPTIC_MODE, settings.preferredHapticMode)
            .putBoolean(KEY_ADAPTIVE_SOUNDSCAPE_ENABLED, settings.adaptiveSoundscapeEnabled)
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
