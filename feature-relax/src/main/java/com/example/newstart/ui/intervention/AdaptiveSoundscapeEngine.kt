package com.example.newstart.ui.intervention

import android.content.Context
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.example.newstart.core.common.R
import com.example.newstart.intervention.AdaptiveSoundscapeState
import com.example.newstart.intervention.RelaxRealtimeFeedback
import kotlin.math.roundToInt

class AdaptiveSoundscapeEngine(
    private val context: Context
) {

    companion object {
        const val LAYER_RAIN = "rain"
        const val LAYER_FIRE = "fire"
        const val LAYER_NIGHT = "night"
        const val LAYER_LOW = "low"
    }

    private data class LayerConfig(
        val key: String,
        @RawRes val resId: Int
    )

    private val layers = listOf(
        LayerConfig(LAYER_RAIN, R.raw.sleep_wind_down_audio),
        LayerConfig(LAYER_FIRE, R.raw.pmr_release_audio),
        LayerConfig(LAYER_NIGHT, R.raw.stretch_mobility_audio),
        LayerConfig(LAYER_LOW, R.raw.body_scan_nsdr_audio)
    )

    private val players = linkedMapOf<String, ExoPlayer>()
    private var lastAppliedMix: Map<String, Float> = defaultMix()
    private var manualAdjustCount = 0

    fun prepare() {
        if (players.isNotEmpty()) return
        layers.forEach { layer ->
            val player = ExoPlayer.Builder(context).build().apply {
                val uri = RawResourceDataSource.buildRawResourceUri(layer.resId)
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = lastAppliedMix[layer.key] ?: 0f
                prepare()
            }
            players[layer.key] = player
        }
    }

    fun play() {
        prepare()
        players.values.forEach { player ->
            if (!player.isPlaying) {
                player.play()
            }
        }
    }

    fun pause() {
        players.values.forEach { it.pause() }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }

    fun isPlaying(): Boolean = players.values.any { it.isPlaying }

    fun markManualAdjustment() {
        manualAdjustCount += 1
    }

    fun applyManualMix(mixLevels: Map<String, Float>) {
        lastAppliedMix = normalizeMix(mixLevels)
        players.forEach { (key, player) ->
            player.volume = lastAppliedMix[key] ?: 0f
        }
    }

    fun buildAdaptiveState(
        manualMix: Map<String, Float>,
        adaptiveEnabled: Boolean,
        feedback: RelaxRealtimeFeedback
    ): AdaptiveSoundscapeState {
        val normalizedManual = normalizeMix(manualMix)
        val effectiveMix = if (adaptiveEnabled && feedback.hasRealtimeData) {
            val signal = feedback.relaxSignal.coerceIn(0f, 1f)
            mapOf(
                LAYER_RAIN to (normalizedManual.getValue(LAYER_RAIN) + feedback.motion.coerceIn(0f, 1.6f) * 0.18f).coerceIn(0f, 1f),
                LAYER_FIRE to (normalizedManual.getValue(LAYER_FIRE) + (1f - signal) * 0.10f).coerceIn(0f, 1f),
                LAYER_NIGHT to (normalizedManual.getValue(LAYER_NIGHT) - feedback.motion.coerceIn(0f, 1.2f) * 0.08f).coerceIn(0f, 1f),
                LAYER_LOW to (normalizedManual.getValue(LAYER_LOW) + (1f - signal) * 0.22f).coerceIn(0f, 1f)
            )
        } else {
            normalizedManual
        }
        applyManualMix(effectiveMix)
        return AdaptiveSoundscapeState(
            mixLevels = effectiveMix,
            adaptiveEnabled = adaptiveEnabled,
            manualAdjustCount = manualAdjustCount,
            dominantLayerLabel = dominantLayerLabel(effectiveMix)
        )
    }

    fun currentMix(): Map<String, Float> = lastAppliedMix

    fun currentProgress(): Int {
        val player = players[LAYER_RAIN] ?: return 0
        val duration = player.duration.takeIf { it > 0 } ?: return 0
        val position = player.currentPosition.coerceAtLeast(0L)
        return ((position.toFloat() / duration.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun defaultMix(): Map<String, Float> {
        return linkedMapOf(
            LAYER_RAIN to 0.58f,
            LAYER_FIRE to 0.42f,
            LAYER_NIGHT to 0.32f,
            LAYER_LOW to 0.46f
        )
    }

    private fun normalizeMix(mixLevels: Map<String, Float>): Map<String, Float> {
        return defaultMix().mapValues { (key, defaultValue) ->
            (mixLevels[key] ?: defaultValue).coerceIn(0f, 1f)
        }
    }

    private fun dominantLayerLabel(mix: Map<String, Float>): String {
        return when (mix.maxByOrNull { it.value }?.key) {
            LAYER_RAIN -> "细雨层"
            LAYER_FIRE -> "温暖底噪"
            LAYER_NIGHT -> "夜间点缀"
            else -> "低频环境"
        }
    }
}
