package com.example.newstart.ai

import android.content.Context
import android.os.Looper
import android.util.Log
import com.example.newstart.core.ml.BuildConfig
import com.example.newstart.data.HealthMetrics
import com.example.newstart.data.RecoveryScore
import com.example.newstart.data.SleepData
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

object EdgeLlmOnDeviceModel {

    private const val TAG = "EdgeLlmOnDeviceModel"
    // Some device/ABI combinations crash in native llama grammar parsing.
    // Keep grammar-based JSON generation disabled by default and rely on text JSON extraction.
    private const val ENABLE_JSON_GRAMMAR = false
    private const val MODEL_FILE_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private val MODEL_ASSET_PATH_CANDIDATES = listOf(
        "llm/$MODEL_FILE_NAME",
        "ml/$MODEL_FILE_NAME"
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var modelPath: String? = null

    private val llamaCallLock = Any()

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) {
            return
        }

        val localPath = ensureModelFile(appContext!!) ?: return
        val ready = runLlamaCall("initGenerateModel") {
            LlamaBridgeProvider.initGenerateModel(localPath)
        } ?: false
        if (ready) {
            modelPath = localPath
            initialized = true
            Log.i(TAG, "Qwen model initialized: $localPath")
        } else {
            Log.w(TAG, "Qwen model init failed: $localPath")
        }
    }

    fun hasModel(): Boolean {
        return initialized
    }

    fun predictTopicWeights(
        recoveryScore: RecoveryScore,
        sleepData: SleepData,
        metrics: HealthMetrics,
    ): FloatArray? {
        val context = appContext ?: return null
        if (isMainThread()) {
            Log.w(TAG, "predictTopicWeights called on main thread, fallback to heuristic weights")
            return heuristicWeights(recoveryScore, sleepData, metrics)
        }
        if (!initialized) {
            init(context)
        }
        if (!initialized) {
            return null
        }

        val systemPrompt =
            "You are a sleep coach. Return compact JSON with keys exercise,sleep,stress,spo2,wake and numeric values between 0 and 1."
        val contextBlock =
            "recovery=${recoveryScore.score}; sleep_minutes=${sleepData.totalSleepMinutes}; efficiency=${sleepData.sleepEfficiency}; awake_count=${sleepData.awakeCount}; hr=${metrics.heartRate.current}; spo2_min=${metrics.bloodOxygen.min}; hrv=${metrics.hrv.current}; temp=${metrics.temperature.current}"
        val userPrompt = "Output JSON only. Sum should approximately equal 1."

        val json = generateJsonWithContextSafely(
            systemPrompt = systemPrompt,
            contextBlock = contextBlock,
            userPrompt = userPrompt,
            jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "exercise": {"type": "number"},
                    "sleep": {"type": "number"},
                    "stress": {"type": "number"},
                    "spo2": {"type": "number"},
                    "wake": {"type": "number"}
                  },
                  "required": ["exercise","sleep","stress","spo2","wake"]
                }
            """.trimIndent()
        )
        parseWeights(json.orEmpty())?.let { return it }

        // Fallback path without grammar constraints: ask model for JSON and extract first JSON object.
        val plain = runLlamaCall("predictTopicWeights.generateWithContext") {
            LlamaBridgeProvider.generateWithContext(
                systemPrompt,
                contextBlock,
                "$userPrompt Return compact JSON object only."
            )
        }
        val extracted = plain?.let { extractFirstJsonObject(it) ?: it }
        return parseWeights(extracted.orEmpty()) ?: heuristicWeights(recoveryScore, sleepData, metrics)
    }

    fun generateDoctorReply(
        question: String,
        contextBlock: String,
        ragContext: String,
    ): String? {
        val context = appContext ?: return null
        if (isMainThread()) {
            Log.w(TAG, "generateDoctorReply called on main thread, skip edge generation")
            return null
        }
        if (!initialized) {
            init(context)
        }
        if (!initialized) {
            return null
        }

        val systemPrompt =
            "You are a private sleep and recovery doctor assistant. Reply in concise Simplified Chinese. Provide practical lifestyle suggestions and avoid diagnosis."
        val userPrompt =
            "User question: $question\nPlease provide practical and safe advice based on context."
        val mergedContext = "health_context=$contextBlock\nrag_context=$ragContext"

        val rawJson = generateJsonWithContextSafely(
            systemPrompt = systemPrompt,
            contextBlock = mergedContext,
            userPrompt = userPrompt,
            jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "answer": {"type": "string"}
                  },
                  "required": ["answer"]
                }
            """.trimIndent()
        )

        if (!rawJson.isNullOrBlank()) {
            extractDoctorReplyText(rawJson)?.let { return it }
            Log.w(TAG, "Qwen JSON reply unresolved, fallback to plain generation")
        }

        val rawText = runLlamaCall("generateDoctorReply.generateWithContext") {
            LlamaBridgeProvider.generateWithContext(systemPrompt, mergedContext, userPrompt)
        }
        if (!rawText.isNullOrBlank()) {
            extractDoctorReplyText(rawText)?.let { return it }
        }

        val compactPrompt = buildString {
            append(systemPrompt)
            append('\n')
            append(mergedContext)
            append('\n')
            append(userPrompt)
        }
        val rawSimple = runLlamaCall("generateDoctorReply.generate") {
            LlamaBridgeProvider.generate(compactPrompt)
        }
        if (!rawSimple.isNullOrBlank()) {
            extractDoctorReplyText(rawSimple)?.let { return it }
        }

        if (rawJson.isNullOrBlank() && rawText.isNullOrBlank()) {
            Log.w(TAG, "Qwen doctor reply empty: json/plain/simple all blank")
        } else {
            Log.w(
                TAG,
                "Qwen doctor reply unresolved after parsing: jsonLen=${rawJson?.length ?: -1}, plainLen=${rawText?.length ?: -1}, simpleLen=${rawSimple?.length ?: -1}"
            )
        }
        return null
    }

    fun generateInterventionPlan(input: InterventionPlanInput): InterventionPlanResult {
        val context = appContext
        if (isMainThread()) {
            Log.w(TAG, "generateInterventionPlan called on main thread, fallback used")
            return buildFallbackInterventionPlan(input, true)
        }
        if (context != null && !initialized) {
            init(context)
        }

        if (!initialized) {
            return buildFallbackInterventionPlan(input, true)
        }

        val systemPrompt =
            "You are a health intervention coach. Return strict JSON only. Do not provide diagnosis."
        val contextBlock =
            "zone=${input.bodyZone}; zone_detail=${input.zoneDetail}; pick_source=${input.pickSource}; trigger=${input.triggerReason}; stress=${input.stressIndex}; recovery=${input.recoveryScore}; hr=${input.heartRate}; hrv=${input.hrv}; spo2=${input.spo2}"
        val userPrompt = "Generate one practical intervention plan. Keep concise and actionable."

        val raw = generateJsonWithContextSafely(
            systemPrompt = systemPrompt,
            contextBlock = contextBlock,
            userPrompt = userPrompt,
            jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "title": {"type": "string"},
                    "rationale": {"type": "string"},
                    "protocolType": {"type": "string"},
                    "caution": {"type": "string"},
                    "completionRule": {"type": "string"},
                    "actions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "detail": {"type": "string"},
                          "durationSec": {"type": "integer"}
                        },
                        "required": ["name", "detail", "durationSec"]
                      }
                    }
                  },
                  "required": ["title", "rationale", "protocolType", "actions", "caution", "completionRule"]
                }
            """.trimIndent()
        )

        val parsed = raw?.let { parseInterventionPlanJson(it) } ?: runCatching {
            val plain = runLlamaCall("generateInterventionPlan.generateWithContext") {
                LlamaBridgeProvider.generateWithContext(
                systemPrompt,
                contextBlock,
                "$userPrompt Return JSON object only."
                )
            } ?: return@runCatching null
            val jsonOnly = extractFirstJsonObject(plain) ?: plain
            parseInterventionPlanJson(jsonOnly)
        }.getOrNull()
        return parsed ?: buildFallbackInterventionPlan(input, true)
    }

    private fun generateJsonWithContextSafely(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String
    ): String? {
        if (!ENABLE_JSON_GRAMMAR) {
            return null
        }
        return runLlamaCall("generateJsonWithContext") {
            LlamaBridgeProvider.generateJsonWithContext(
                systemPrompt = systemPrompt,
                contextBlock = contextBlock,
                userPrompt = userPrompt,
                jsonSchema = jsonSchema
            )
        }
    }

    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    private fun <T> runLlamaCall(callName: String, block: () -> T): T? {
        return runCatching {
            synchronized(llamaCallLock) {
                block()
            }
        }.onFailure { error ->
            Log.e(TAG, "Llama call failed: $callName", error)
        }.getOrNull()
    }

    private fun extractDoctorReplyText(raw: String): String? {
        parseJsonAnswer(raw)?.let { return it }

        val cleaned = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        parseJsonAnswer(cleaned)?.let { return it }

        extractFirstJsonObject(cleaned)?.let { jsonOnly ->
            parseJsonAnswer(jsonOnly)?.let { return it }
        }

        val regexAnswer = Regex("\"answer\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.trim()
        if (!regexAnswer.isNullOrBlank()) {
            return regexAnswer
        }

        return cleaned.ifBlank { null }
    }

    private fun parseJsonAnswer(raw: String): String? {
        val parsed = runCatching {
            val element = JsonParser.parseString(raw)
            extractAnswerFromJson(element)
        }.getOrNull().orEmpty()
        return parsed.ifBlank { null }
    }

    private fun extractAnswerFromJson(element: JsonElement): String? {
        if (!element.isJsonObject) {
            return null
        }
        val obj = element.asJsonObject
        obj.get("answer")?.let { direct ->
            if (direct.isJsonPrimitive) {
                val text = direct.asString.trim()
                if (text.isNotBlank()) return text
            }
        }

        val nestedKeys = listOf("data", "result", "output", "response")
        for (key in nestedKeys) {
            val nested = obj.get(key)
            if (nested != null && nested.isJsonObject) {
                val nestedAnswer = extractAnswerFromJson(nested)
                if (!nestedAnswer.isNullOrBlank()) {
                    return nestedAnswer
                }
            }
        }
        return null
    }

    private fun extractFirstJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val ch = raw[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return raw.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseWeights(raw: String): FloatArray? {
        return runCatching {
            val obj = JsonParser.parseString(raw).asJsonObject
            val arr = floatArrayOf(
                obj.get("exercise")?.asFloat ?: 0f,
                obj.get("sleep")?.asFloat ?: 0f,
                obj.get("stress")?.asFloat ?: 0f,
                obj.get("spo2")?.asFloat ?: 0f,
                obj.get("wake")?.asFloat ?: 0f,
            )
            normalize(arr)
        }.getOrNull()
    }

    private fun parseInterventionPlanJson(raw: String): InterventionPlanResult? {
        return runCatching {
            val root = JsonParser.parseString(raw).asJsonObject
            val title = root.get("title")?.asString?.trim().orEmpty()
            val rationale = root.get("rationale")?.asString?.trim().orEmpty()
            val protocol = root.get("protocolType")?.asString?.trim().orEmpty()
            val caution = root.get("caution")?.asString?.trim().orEmpty()
            val completionRule = root.get("completionRule")?.asString?.trim().orEmpty()
            val actionsArray = root.getAsJsonArray("actions")

            val actions = actionsArray?.mapNotNull { item ->
                runCatching {
                    val obj = item.asJsonObject
                    val name = obj.get("name")?.asString?.trim().orEmpty()
                    val detail = obj.get("detail")?.asString?.trim().orEmpty()
                    val durationSec = obj.get("durationSec")?.asInt ?: 0
                    if (name.isBlank() || detail.isBlank()) {
                        null
                    } else {
                        InterventionAction(
                            name = name,
                            detail = detail,
                            durationSec = durationSec.coerceAtLeast(30)
                        )
                    }
                }.getOrNull()
            }.orEmpty()

            if (title.isBlank() || rationale.isBlank() || actions.isEmpty()) {
                null
            } else {
                InterventionPlanResult(
                    title = title,
                    rationale = rationale,
                    actions = actions,
                    caution = caution.ifBlank { "If symptoms continue, seek in-person medical care." },
                    completionRule = completionRule.ifBlank { "Complete all actions and record pre/post feelings." },
                    protocolType = protocol.ifBlank { "BREATH_4_6" },
                    fallbackUsed = false
                )
            }
        }.getOrNull()
    }

    private fun buildFallbackInterventionPlan(
        input: InterventionPlanInput,
        fallbackUsed: Boolean
    ): InterventionPlanResult {
        val zone = input.bodyZone.uppercase()
        val zoneGroup = when (zone) {
            "HEAD", "NECK" -> "HEAD"
            "CHEST", "UPPER_BACK" -> "CHEST"
            "ABDOMEN", "LOWER_BACK" -> "ABDOMEN"
            else -> "LIMB"
        }
        val isHighStress = input.stressIndex >= 70 || input.recoveryScore < 55
        return when (zoneGroup) {
            "HEAD" -> {
                val duration = if (isHighStress) 300 else 180
                InterventionPlanResult(
                    title = "Pre-sleep de-stimulation",
                    rationale = "Head-zone stress signal is high. Reduce cognitive load and support sleep onset.",
                    actions = listOf(
                        InterventionAction("Reduce screen stimulation", "Dim display and stop intense information input.", 600),
                        InterventionAction("Rhythmic breathing", "Practice 4-6 breathing with stable rhythm.", duration),
                    ),
                    caution = "If persistent headache or chest discomfort appears, seek medical help.",
                    completionRule = "Complete both actions and keep breathing practice for at least ${duration / 60} minutes.",
                    protocolType = "BREATH_4_6",
                    fallbackUsed = fallbackUsed
                )
            }
            "CHEST" -> {
                val duration = if (isHighStress) 300 else 180
                InterventionPlanResult(
                    title = "Respiratory rhythm intervention",
                    rationale = "Chest-zone trigger indicates sympathetic activation. Prioritize paced breathing.",
                    actions = listOf(
                        InterventionAction("4-7-8 breathing", "Inhale 4s, hold 7s, exhale 8s with gentle intensity.", duration),
                        InterventionAction("Low-intensity walk", "Take a relaxed walk after breathing practice.", 600),
                    ),
                    caution = "If obvious chest pain or breathing difficulty appears, seek immediate care.",
                    completionRule = "Finish breathing practice and record stress before/after.",
                    protocolType = "BREATH_4_7_8",
                    fallbackUsed = fallbackUsed
                )
            }
            "ABDOMEN" -> {
                InterventionPlanResult(
                    title = "Parasympathetic activation session",
                    rationale = "Abdomen-zone trigger suggests elevated tension. Use paced breathing and body relaxation.",
                    actions = listOf(
                        InterventionAction("Box breathing", "Inhale 4s, hold 4s, exhale 4s, hold 4s.", 240),
                        InterventionAction("Supine abdominal relaxation", "Lie down and focus on diaphragmatic breathing.", 180),
                    ),
                    caution = "If abdominal pain or nausea continues, seek offline evaluation.",
                    completionRule = "Complete both actions with no more than one interruption.",
                    protocolType = "BOX",
                    fallbackUsed = fallbackUsed
                )
            }
            else -> {
                InterventionPlanResult(
                    title = "Low-load recovery plan",
                    rationale = "Limb-zone trigger suggests insufficient recovery. Prioritize gentle movement and walking.",
                    actions = listOf(
                        InterventionAction("Dynamic stretching", "Complete a gentle upper and lower limb stretch sequence.", 300),
                        InterventionAction("Light walking", "Walk at an intensity where normal conversation is still easy.", 600),
                    ),
                    caution = "Stop immediately if dizziness or chest discomfort appears.",
                    completionRule = "After stretching and walking, record the change in fatigue level.",
                    protocolType = "LOW_ACTIVITY",
                    fallbackUsed = fallbackUsed
                )
            }
        }
    }

    private fun heuristicWeights(
        recoveryScore: RecoveryScore,
        sleepData: SleepData,
        metrics: HealthMetrics,
    ): FloatArray {
        val recovery = recoveryScore.score / 100f
        val sleepDebt = (420 - sleepData.totalSleepMinutes).coerceAtLeast(0) / 180f
        val stress = (metrics.hrv.baseline - metrics.hrv.current).coerceAtLeast(0) / 60f
        val spo2Risk = (95 - metrics.bloodOxygen.min).coerceAtLeast(0) / 10f
        val wakeRisk = sleepData.awakeCount.coerceAtMost(10) / 10f
        return normalize(
            floatArrayOf(
                max(0.05f, recovery),
                max(0.05f, sleepDebt),
                max(0.05f, stress),
                max(0.05f, spo2Risk),
                max(0.05f, wakeRisk),
            )
        )
    }

    private fun normalize(values: FloatArray): FloatArray {
        val clipped = values.map { max(0f, it) }
        val sum = clipped.sum()
        if (sum <= 1e-6f) {
            return floatArrayOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
        }
        return FloatArray(values.size) { idx -> clipped[idx] / sum }
    }

    private fun ensureModelFile(context: Context): String? {
        val modelDir = File(context.filesDir, "llm")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        findLocalModelFile(modelDir)?.let { found ->
            Log.i(TAG, "Using local Qwen model: ${found.absolutePath}")
            return found.absolutePath
        }

        copyModelFromAssets(context, modelDir)?.let { copied ->
            Log.i(TAG, "Copied Qwen model from assets: ${copied.absolutePath}")
            return copied.absolutePath
        }

        val modelFile = File(modelDir, MODEL_FILE_NAME)
        val downloadUrl = BuildConfig.QWEN_MODEL_URL.trim()
        if (downloadUrl.isNotEmpty() && downloadToFile(downloadUrl, modelFile)) {
            return modelFile.absolutePath
        }

        Log.w(
            TAG,
            "Qwen model file missing: assets=${MODEL_ASSET_PATH_CANDIDATES.joinToString()}, url=${if (downloadUrl.isNotEmpty()) "set" else "empty"}"
        )
        return null
    }

    private fun findLocalModelFile(modelDir: File): File? {
        val preferred = File(modelDir, MODEL_FILE_NAME)
        if (preferred.exists() && preferred.length() > 0L) {
            return preferred
        }

        val candidates = modelDir.listFiles().orEmpty()
        return candidates.firstOrNull { file ->
            file.isFile && file.length() > 0L && file.name.endsWith(".gguf", ignoreCase = true)
        }
    }

    private fun copyModelFromAssets(context: Context, modelDir: File): File? {
        val assetPath = resolveAssetModelPath(context) ?: return null
        val target = File(modelDir, assetPath.substringAfterLast('/'))
        return if (copyFromAssets(context, assetPath, target)) target else null
    }

    private fun resolveAssetModelPath(context: Context): String? {
        MODEL_ASSET_PATH_CANDIDATES.firstOrNull { assetExists(context, it) }?.let { return it }

        val assetDirs = listOf("llm", "ml")
        for (dir in assetDirs) {
            val files = runCatching { context.assets.list(dir) }.getOrNull().orEmpty()
            val gguf = files.firstOrNull { it.endsWith(".gguf", ignoreCase = true) }
            if (gguf != null) {
                return "$dir/$gguf"
            }
        }
        return null
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).use { }
            true
        }.getOrDefault(false)
    }

    private fun copyFromAssets(context: Context, assetPath: String, target: File): Boolean {
        return runCatching {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun downloadToFile(url: String, target: File): Boolean {
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.connect()
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return false
            }
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            true
        }.getOrElse {
            false
        }
    }
}


