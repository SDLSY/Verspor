package com.example.newstart.util

import kotlin.math.abs

/**
 * 体外温度(指环/皮肤/环境耦合) -> 体温(更接近人体正常范围) 映射。
 *
 * 说明：
 * - 指环测到的温度往往偏低且受环境影响；这里提供一个“可解释、可调参”的分段线性映射。
 * - 该映射不是医学级体温测量，仅用于App展示与趋势分析。
 */
object TemperatureMapper {

    data class Params(
        // 基础偏移（整体上移），通常为正数
        val baseOffset: Float = 5.5f,
        // 体外温度越低，越容易受环境影响，给更大的补偿
        val coldBoostSlope: Float = 0.35f,
        // 冷端阈值（低于这个认为更“冷”）
        val coldThreshold: Float = 28.0f,
        // 热端阈值（高于这个认为更“贴近体温”）
        val warmThreshold: Float = 33.0f,
        // 输出范围裁剪（避免异常值污染趋势）
        val minBodyTemp: Float = 34.0f,
        val maxBodyTemp: Float = 41.5f,
        // 平滑：上一时刻权重（0-1，越大越平滑）
        val smoothingAlpha: Float = 0.85f
    )

    /**
     * 将体外温度映射到“更像体温”的范围。
     * @param outerTemp 体外温度（°C）
     * @param lastMapped 上一次映射后的温度（用于平滑），可为null
     */
    fun mapOuterToBody(outerTemp: Float, lastMapped: Float? = null, params: Params = Params()): Float {
        if (!outerTemp.isFinite() || outerTemp <= 0f) return 0f

        // 1) 基础上移
        var mapped = outerTemp + params.baseOffset

        // 2) 分段补偿：越冷补偿越多（把冷端往正常范围拉）
        if (outerTemp < params.coldThreshold) {
            val delta = params.coldThreshold - outerTemp
            mapped += delta * params.coldBoostSlope
        }

        // 3) 暖端（更接近体温）减少补偿：让高温段不要被抬太多
        if (outerTemp > params.warmThreshold) {
            // 让 warmThreshold 以上逐渐回到“接近1:1 + baseOffset”的感觉
            val delta = outerTemp - params.warmThreshold
            mapped -= delta * 0.15f
        }

        // 4) 裁剪到合理体温范围
        mapped = mapped.coerceIn(params.minBodyTemp, params.maxBodyTemp)

        // 5) 平滑（防止跳变）
        val prev = lastMapped
        if (prev != null && prev > 0f && prev.isFinite()) {
            val alpha = params.smoothingAlpha.coerceIn(0f, 0.98f)
            mapped = prev * alpha + mapped * (1f - alpha)
        }

        return mapped
    }
}
