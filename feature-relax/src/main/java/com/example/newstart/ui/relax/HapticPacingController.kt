package com.example.newstart.ui.relax

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.newstart.intervention.HapticPatternMode

class HapticPacingController(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun playPhase(phase: BreathingPhase, mode: HapticPatternMode) {
        val safeVibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = when (mode) {
            HapticPatternMode.BREATH -> effectForBreathPhase(phase)
            HapticPatternMode.CALM_HEARTBEAT -> calmHeartbeatEffect()
        }
        safelyVibrate(safeVibrator, effect, cancelFirst = true)
    }

    fun pulseSessionAccent(mode: HapticPatternMode) {
        val safeVibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        safelyVibrate(
            vibrator = safeVibrator,
            effect = if (mode == HapticPatternMode.CALM_HEARTBEAT) calmHeartbeatEffect() else effectForBreathPhase(BreathingPhase.INHALE),
            cancelFirst = false
        )
    }

    fun stop() {
        runCatching { vibrator?.cancel() }
            .onFailure { Log.w(TAG, "Unable to stop haptic pacing; falling back silently", it) }
    }

    private fun safelyVibrate(vibrator: Vibrator, effect: VibrationEffect, cancelFirst: Boolean) {
        runCatching {
            if (cancelFirst) vibrator.cancel()
            vibrator.vibrate(effect)
        }.onFailure {
            Log.w(TAG, "Unable to play haptic pacing; disabling this pulse", it)
        }
    }

    private fun effectForBreathPhase(phase: BreathingPhase): VibrationEffect {
        val timings = when (phase) {
            BreathingPhase.INHALE -> longArrayOf(0, 18, 120, 24, 180)
            BreathingPhase.HOLD -> longArrayOf(0, 14)
            BreathingPhase.EXHALE -> longArrayOf(0, 24, 180, 30, 260)
            BreathingPhase.PREPARE -> longArrayOf(0, 12, 100)
        }
        return VibrationEffect.createWaveform(timings, -1)
    }

    private fun calmHeartbeatEffect(): VibrationEffect {
        return VibrationEffect.createWaveform(longArrayOf(0, 18, 90, 10, 18, 360), -1)
    }

    companion object {
        private const val TAG = "HapticPacing"
    }
}
