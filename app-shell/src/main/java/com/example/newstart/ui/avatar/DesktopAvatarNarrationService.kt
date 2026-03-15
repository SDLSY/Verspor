package com.example.newstart.ui.avatar

import android.content.Context
import android.util.Base64
import com.example.newstart.network.ApiClient
import com.example.newstart.network.models.AvatarNarrationRequest
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.service.ai.SpeechService
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.spark.XfyunSparkLiteWsClient
import com.example.newstart.xfyun.spark.XfyunSparkXWsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

class DesktopAvatarNarrationService(
    context: Context,
    private val sparkLiteClient: XfyunSparkLiteWsClient = XfyunSparkLiteWsClient(XfyunConfig.sparkLiteEndpoint),
    private val sparkXClient: XfyunSparkXWsClient = XfyunSparkXWsClient(XfyunConfig.sparkXEndpoint),
    private val speechService: SpeechService = SpeechService(NetworkRepository())
) {

    private val appContext = context.applicationContext
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localVoiceCacheDir: File by lazy(LazyThreadSafetyMode.NONE) {
        File(appContext.cacheDir, "avatar-local-voice-doubao-v1").apply { mkdirs() }
    }

    suspend fun generate(context: PageNarrationContext): AvatarNarration {
        return if (context.trigger == "enter") {
            generateEntryNarration(context)
        } else {
            generateInteractiveNarration(context)
        }
    }

    fun prewarmLocalNarration(context: PageNarrationContext) {
        if (context.trigger != "enter") return
        if (AvatarEntryAudioRegistry.hasBundledAudio(context.pageKey)) return

        val narration = buildEntryNarration(context)
        val cacheFile = localVoiceCacheFile(context.pageKey, narration.text)
        if (cacheFile.exists()) return

        backgroundScope.launch {
            runCatching {
                val audioDataUrl = synthesizeAudio(narration.text, context.pageKey)
                persistAudioDataUrl(cacheFile, audioDataUrl)
            }
        }
    }

    private suspend fun generateEntryNarration(context: PageNarrationContext): AvatarNarration {
        val narration = buildEntryNarration(context)
        AvatarEntryAudioRegistry.audioSource(appContext, context.pageKey)?.let { bundledSource ->
            return narration.copy(
                audioDataUrl = bundledSource,
                source = "bundled_entry_audio",
                modelLabel = "Bundled entry audio"
            )
        }

        val cacheFile = localVoiceCacheFile(context.pageKey, narration.text)
        if (cacheFile.exists()) {
            return narration.copy(
                audioDataUrl = cacheFile.toPlaybackSource(),
                source = "local_cache",
                modelLabel = "Local cached voice"
            )
        }

        val audioDataUrl = synthesizeAudio(narration.text, context.pageKey)
        if (audioDataUrl.isBlank()) {
            prewarmLocalNarration(context)
            return narration.copy(
                audioDataUrl = "",
                source = "local_script",
                modelLabel = "Local script"
            )
        }

        persistAudioDataUrl(cacheFile, audioDataUrl)
        return narration.copy(
            audioDataUrl = cacheFile.toPlaybackSource(),
            source = "cloud_tts_cache",
            modelLabel = "Cloud TTS cached voice"
        )
    }

    private suspend fun generateInteractiveNarration(context: PageNarrationContext): AvatarNarration {
        val fallbackNarration = buildInteractiveFallbackNarration(context)

        val cloudNarration = runCatching { generateFromCloud(context) }.getOrNull()
        val localNarration = cloudNarration ?: runCatching {
            generateFromLocalSpark(context, fallbackNarration)
        }.getOrElse {
            fallbackNarration
        }

        val finalText = localNarration.text
            .sanitizeNarration()
            .ifBlank { fallbackNarration.text }
        val finalAction = localNarration.semanticAction.ifBlank { fallbackNarration.semanticAction }
        val audioDataUrl = synthesizeAudio(finalText, context.pageKey)

        return localNarration.copy(
            text = finalText,
            audioDataUrl = audioDataUrl,
            semanticAction = finalAction
        )
    }

    private fun buildEntryNarration(context: PageNarrationContext): AvatarNarration {
        val script = AvatarGuideScriptProvider.scriptFor(context.destinationId)
        val text = script.enterMessage
            .sanitizeNarration()
            .ifBlank {
                when {
                    context.pageSubtitle.isNotBlank() -> "这里是${context.pageTitle}。${context.pageSubtitle}"
                    else -> "这里是${context.pageTitle}，我会先帮你概括当前页面重点。"
                }
            }
            .take(140)

        return AvatarNarration(
            text = text,
            audioDataUrl = "",
            source = "local_script",
            modelLabel = "Local script",
            semanticAction = inferSemanticAction(context, text)
        )
    }

    private fun buildInteractiveFallbackNarration(context: PageNarrationContext): AvatarNarration {
        val script = AvatarGuideScriptProvider.scriptFor(context.destinationId)
        val summary = context.userStateSummary
            .split("；")
            .map { it.trim() }
            .firstOrNull { it.length in 2..24 }
            .orEmpty()
        val risk = context.riskSummary.takeIf { it.isNotBlank() }?.sanitizeNarration().orEmpty()
        val text = when {
            hasActionableRisk(risk) -> "这里先关注$risk。建议你先处理当前页面最关键的一步。"
            summary.isNotBlank() -> "这里是${context.pageTitle}，当前重点是$summary。建议先按页面第一步继续。"
            context.trigger in setOf("tap", "button", "replay") ->
                script.tapReplies.firstOrNull().orEmpty()
            context.pageSubtitle.isNotBlank() -> "这里是${context.pageTitle}。${context.pageSubtitle}"
            else -> "我会根据当前页面内容，帮你总结重点并提醒下一步。"
        }.sanitizeNarration()

        return AvatarNarration(
            text = text,
            audioDataUrl = "",
            source = "fallback",
            modelLabel = "Local fallback",
            semanticAction = inferSemanticAction(context, text)
        )
    }

    private suspend fun generateFromCloud(context: PageNarrationContext): AvatarNarration? {
        val response = ApiClient.apiService.generateAvatarNarration(
            AvatarNarrationRequest(
                pageKey = context.pageKey,
                pageTitle = context.pageTitle,
                pageSubtitle = context.pageSubtitle,
                visibleHighlights = context.visibleHighlights.take(8),
                userStateSummary = context.userStateSummary,
                riskSummary = context.riskSummary,
                actionHint = context.actionHint,
                trigger = context.trigger
            )
        )
        val payload = response.body()?.data ?: return null
        if (!response.isSuccessful || payload.text.isBlank()) {
            return null
        }
        return AvatarNarration(
            text = payload.text,
            audioDataUrl = "",
            source = payload.source.ifBlank { "cloud" },
            modelLabel = payload.modelLabel.ifBlank { "Cloud AI" },
            semanticAction = payload.semanticAction
        )
    }

    private suspend fun generateFromLocalSpark(
        context: PageNarrationContext,
        fallbackNarration: AvatarNarration
    ): AvatarNarration {
        val useSparkX = DesktopAvatarNarrationPolicy.shouldUseSparkX(context)
        val generatedText = runCatching {
            val messages = DesktopAvatarPromptBuilder.buildMessages(context)
            when {
                useSparkX && XfyunConfig.sparkXEndpoint.isReady -> sparkXClient.chat(messages)
                XfyunConfig.sparkLiteEndpoint.isReady -> sparkLiteClient.chat(messages)
                else -> fallbackNarration.text
            }
        }.getOrElse { fallbackNarration.text }
            .sanitizeNarration()
            .ifBlank { fallbackNarration.text }

        return AvatarNarration(
            text = generatedText,
            audioDataUrl = "",
            source = if (generatedText == fallbackNarration.text) "fallback" else "xfyun",
            modelLabel = if (useSparkX) "Spark X" else "Spark Lite",
            semanticAction = inferSemanticAction(context, generatedText)
        )
    }

    private fun inferSemanticAction(context: PageNarrationContext, text: String): String {
        val lowered = text.lowercase()
        return when {
            hasActionableRisk(context.riskSummary) || text.contains("风险") || text.contains("预警") || lowered.contains("alert") -> "alert"
            context.trigger in setOf("tap", "button", "replay") -> "listen"
            text.contains("点击") || text.contains("进入") || text.contains("打开") || lowered.contains("open") -> "point"
            text.contains("继续") || text.contains("建议") || text.contains("先") -> "encourage"
            else -> "wave"
        }
    }

    private suspend fun synthesizeAudio(text: String, pageKey: String): String {
        if (text.isBlank()) {
            return ""
        }
        return runCatching {
            speechService.synthesize(
                text = text,
                voice = XfyunConfig.defaultVoiceName,
                profile = resolveSpeechProfile(pageKey)
            )?.audioUrl.orEmpty()
        }.getOrDefault("")
    }

    private fun resolveSpeechProfile(pageKey: String): String {
        return when (pageKey) {
            "doctor" -> "doctor_summary"
            "breathing_coach",
            "intervention_center",
            "relax_center",
            "symptom_guide",
            "intervention_session" -> "sleep_coach"
            else -> "calm_assistant"
        }
    }

    private fun persistAudioDataUrl(targetFile: File, audioDataUrl: String) {
        val base64 = audioDataUrl.substringAfter("base64,", "")
        if (base64.isBlank()) return
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        targetFile.outputStream().use { it.write(bytes) }
    }

    private fun localVoiceCacheFile(pageKey: String, text: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$pageKey::$text".toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(localVoiceCacheDir, "${pageKey}_$digest.mp3")
    }

    private fun File.toPlaybackSource(): String = "file://$absolutePath"

    private fun String.sanitizeNarration(): String {
        return replace("```", "")
            .replace("*", "")
            .replace("#", "")
            .replace("\r", "")
            .lines()
            .map { it.trim().trim('"', '\'', '“', '”', '‘', '’') }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(140)
            .trim()
    }

    private fun hasActionableRisk(value: String): Boolean {
        val normalized = value.sanitizeNarration()
        if (normalized.isBlank()) return false
        val nonRiskMarkers = listOf("暂无", "未见", "无明显", "未提示", "稳定", "正常")
        if (nonRiskMarkers.any { normalized.contains(it) }) {
            return false
        }
        return listOf("风险", "预警", "异常", "红旗", "高危", "警示").any { normalized.contains(it) }
    }
}
