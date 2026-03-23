package com.example.newstart.xfyun

import com.example.newstart.core.network.BuildConfig

data class XfyunCredentials(
    val appId: String,
    val apiKey: String,
    val apiSecret: String = ""
) {
    val isReady: Boolean
        get() = appId.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()
}

data class XfyunSignaCredentials(
    val appId: String,
    val apiKey: String
) {
    val isReady: Boolean
        get() = appId.isNotBlank() && apiKey.isNotBlank()
}

data class XfyunSparkEndpoint(
    val url: String,
    val domain: String,
    val credentials: XfyunCredentials
) {
    val isReady: Boolean
        get() = credentials.isReady
}

object XfyunConfig {

    private const val DEFAULT_AIUI_DOCTOR_PROMPT =
        "你是长庚环的数字人医生助手，服务于睡眠、恢复、压力、疲劳、呼吸和轻度症状初筛场景。" +
            "你的目标是做连续追问、结构化病史收集、风险分层和下一步建议，而不是做确定性诊断。" +
            "你可以进行实时追问、梳理病史、总结症状与指标、给出保守建议，并在必要时建议线下就医。" +
            "你不能做明确诊断，不能开药，不能承诺治疗结果，不能替代真实医生面诊。" +
            "优先依据当前绑定的长庚环医生知识库回答产品导航、干预模块、睡眠恢复、医检解释与风险提示问题。" +
            "如果知识库没有覆盖，再给出通用且保守的健康建议，并明确这是一般性信息。" +
            "始终使用简体中文。句子简短、专业、克制。每轮只问一个高价值问题，不要连续抛出多个问题，不要像表单机器人。" +
            "若出现胸痛、呼吸困难、昏厥、意识异常、持续高热、明显低血氧、急性神经系统症状、黑便便血或快速恶化等高风险线索，" +
            "立即停止闲聊式追问，并直接建议尽快线下就医。"

    private fun fallback(primary: String, secondary: String): String {
        return primary.ifBlank { secondary }
    }

    private fun normalizeSparkXDomain(raw: String): String {
        return when (raw.trim().lowercase()) {
            "", "x1", "x1.5", "sparkx" -> "spark-x"
            else -> raw
        }
    }

    val iatCredentials: XfyunCredentials = XfyunCredentials(
        appId = fallback(BuildConfig.XFYUN_IAT_APP_ID, BuildConfig.XFYUN_APP_ID),
        apiKey = fallback(BuildConfig.XFYUN_IAT_API_KEY, BuildConfig.XFYUN_API_KEY),
        apiSecret = fallback(BuildConfig.XFYUN_IAT_API_SECRET, BuildConfig.XFYUN_API_SECRET)
    )

    val ttsCredentials: XfyunCredentials = XfyunCredentials(
        appId = fallback(BuildConfig.XFYUN_TTS_APP_ID, BuildConfig.XFYUN_APP_ID),
        apiKey = fallback(BuildConfig.XFYUN_TTS_API_KEY, BuildConfig.XFYUN_API_KEY),
        apiSecret = fallback(BuildConfig.XFYUN_TTS_API_SECRET, BuildConfig.XFYUN_API_SECRET)
    )

    val ocrCredentials: XfyunCredentials = XfyunCredentials(
        appId = fallback(BuildConfig.XFYUN_OCR_APP_ID, BuildConfig.XFYUN_APP_ID),
        apiKey = fallback(BuildConfig.XFYUN_OCR_API_KEY, BuildConfig.XFYUN_API_KEY),
        apiSecret = fallback(BuildConfig.XFYUN_OCR_API_SECRET, BuildConfig.XFYUN_API_SECRET)
    )

    val sparkLiteEndpoint: XfyunSparkEndpoint = XfyunSparkEndpoint(
        url = "wss://spark-api.xf-yun.com/v1.1/chat",
        domain = BuildConfig.XFYUN_SPARK_LITE_DOMAIN.ifBlank { "lite" },
        credentials = XfyunCredentials(
            appId = fallback(BuildConfig.XFYUN_SPARK_LITE_APP_ID, BuildConfig.XFYUN_APP_ID),
            apiKey = fallback(BuildConfig.XFYUN_SPARK_LITE_API_KEY, BuildConfig.XFYUN_API_KEY),
            apiSecret = fallback(BuildConfig.XFYUN_SPARK_LITE_API_SECRET, BuildConfig.XFYUN_API_SECRET)
        )
    )

    val sparkXEndpoint: XfyunSparkEndpoint = XfyunSparkEndpoint(
        url = "wss://spark-api.xf-yun.com/v1/x1",
        domain = normalizeSparkXDomain(BuildConfig.XFYUN_SPARK_X_DOMAIN),
        credentials = XfyunCredentials(
            appId = fallback(BuildConfig.XFYUN_SPARK_X_APP_ID, BuildConfig.XFYUN_APP_ID),
            apiKey = fallback(BuildConfig.XFYUN_SPARK_X_API_KEY, BuildConfig.XFYUN_API_KEY),
            apiSecret = fallback(BuildConfig.XFYUN_SPARK_X_API_SECRET, BuildConfig.XFYUN_API_SECRET)
        )
    )

    val rtasrCredentials: XfyunSignaCredentials = XfyunSignaCredentials(
        appId = fallback(BuildConfig.XFYUN_RTASR_APP_ID, BuildConfig.XFYUN_APP_ID),
        apiKey = fallback(BuildConfig.XFYUN_RTASR_API_KEY, BuildConfig.XFYUN_API_KEY)
    )

    val raasrCredentials: XfyunSignaCredentials = XfyunSignaCredentials(
        appId = fallback(BuildConfig.XFYUN_RAASR_APP_ID, BuildConfig.XFYUN_APP_ID),
        apiKey = fallback(BuildConfig.XFYUN_RAASR_API_KEY, BuildConfig.XFYUN_API_KEY)
    )

    val aiuiCredentials: XfyunCredentials = XfyunCredentials(
        appId = fallback(BuildConfig.XFYUN_AIUI_APP_ID, BuildConfig.XFYUN_APP_ID),
        apiKey = fallback(BuildConfig.XFYUN_AIUI_API_KEY, BuildConfig.XFYUN_API_KEY),
        apiSecret = fallback(BuildConfig.XFYUN_AIUI_API_SECRET, BuildConfig.XFYUN_API_SECRET)
    )

    val aiuiScene: String
        get() = BuildConfig.XFYUN_AIUI_SCENE.ifBlank { "sos_app" }

    val aiuiDoctorPrompt: String
        get() = BuildConfig.XFYUN_AIUI_DOCTOR_PROMPT.ifBlank { DEFAULT_AIUI_DOCTOR_PROMPT }

    val defaultVoiceName: String
        get() = BuildConfig.XFYUN_TTS_VOICE_NAME.ifBlank {
            BuildConfig.XFYUN_VH_VOICE_NAME.ifBlank { "x4_yezi" }
        }

    val defaultAvatarId: String
        get() = BuildConfig.XFYUN_VH_AVATAR_ID
}

