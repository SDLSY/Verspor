package com.example.newstart.ai

import android.util.Log
import java.lang.reflect.Method

internal object LlamaBridgeProvider {

    private const val TAG = "LlamaBridgeProvider"
    private const val BRIDGE_CLASS_NAME = "com.llamatik.library.platform.LlamaBridge"

    private val bridgeClass: Class<*>? by lazy {
        runCatching { Class.forName(BRIDGE_CLASS_NAME) }
            .onFailure { error ->
                Log.w(TAG, "Llama bridge unavailable on runtime classpath", error)
            }
            .getOrNull()
    }

    private val initGenerateModelMethod: Method? by lazy {
        bridgeClass?.getMethod("initGenerateModel", String::class.java)
    }

    private val generateMethod: Method? by lazy {
        bridgeClass?.getMethod("generate", String::class.java)
    }

    private val generateWithContextMethod: Method? by lazy {
        bridgeClass?.getMethod(
            "generateWithContext",
            String::class.java,
            String::class.java,
            String::class.java
        )
    }

    private val generateJsonWithContextMethod: Method? by lazy {
        bridgeClass?.getMethod(
            "generateJsonWithContext",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
    }

    fun initGenerateModel(localPath: String): Boolean? {
        return invoke(initGenerateModelMethod, localPath) as? Boolean
    }

    fun generate(prompt: String): String? {
        return invoke(generateMethod, prompt) as? String
    }

    fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String? {
        return invoke(generateWithContextMethod, systemPrompt, contextBlock, userPrompt) as? String
    }

    fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String
    ): String? {
        return invoke(
            generateJsonWithContextMethod,
            systemPrompt,
            contextBlock,
            userPrompt,
            jsonSchema
        ) as? String
    }

    private fun invoke(method: Method?, vararg args: Any): Any? {
        if (method == null) {
            return null
        }
        return runCatching {
            method.invoke(null, *args)
        }.onFailure { error ->
            Log.e(TAG, "Llama bridge invocation failed: ${method.name}", error)
        }.getOrNull()
    }
}
