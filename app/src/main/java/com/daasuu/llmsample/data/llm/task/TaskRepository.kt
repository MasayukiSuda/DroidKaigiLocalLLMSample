package com.daasuu.llmsample.data.llm.task

import android.content.Context
import android.util.Log
import com.daasuu.llmsample.domain.LLMRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LLMRepository {

    private val TAG = "TaskGenAI"
    private var isInitialized = false
    private var taskModelPath: String? = null

    // MediaPipe GenAI (TextGenerator) via reflection
    private var mpTextGenerator: Any? = null

    override suspend fun initialize() {
        if (isInitialized) return
        withContext(Dispatchers.IO) {
            val destDir = File(context.filesDir, "models/lite_rt/gemma3")
            if (!destDir.exists()) destDir.mkdirs()
            copyAssets("models/lite_rt/gemma3", destDir)
            val taskFile = destDir.listFiles()?.firstOrNull { it.extension.equals("task", true) }
            taskModelPath = taskFile?.absolutePath
            Log.d(TAG, "taskModelPath=$taskModelPath")
            isInitialized = taskModelPath != null
            if (isInitialized) {
                tryInitMediaPipeGenerator()
                Log.d(TAG, "mpTextGenerator inited=${mpTextGenerator != null}")
            }
        }
    }

    override suspend fun release() {
        isInitialized = false
    }

    override suspend fun isAvailable(): Boolean = isInitialized

    override suspend fun generateChatResponse(prompt: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("Error: .task model not found"); return@channelFlow
        }
        val generated = withContext(Dispatchers.IO) { runMediaPipeGenerate(prompt) }
        if (generated != null) {
            generated.split(" ").forEach { token ->
                send(token + " ")
                delay(12)
            }
        } else {
            Log.w(TAG, "MediaPipe generate failed; falling back to mock")
            withContext(Dispatchers.IO) {
                ("[task mock] Gemma3: " + prompt).split(" ").forEach {
                    send(it + " ")
                    delay(20)
                }
            }
        }
    }

    override suspend fun summarizeText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("Error: .task model not found"); return@channelFlow
        }
        val full =
            withContext(Dispatchers.IO) { runMediaPipeGenerate("要約してください: \n\n$text") }
        send(full ?: ("- 要約(擬似): " + text.take(40) + "..."))
    }

    override suspend fun proofreadText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("{}"); return@channelFlow
        }
        val instruction = """
            次の入力文を日本語で最小限に校正してください。出力は次のJSONオブジェクトのみとし、余計な説明やマークダウンは一切出力しないでください。

            {
              "corrected_text": "校正後の全文",
              "corrections": [
                {
                  "original": "修正前の語/句",
                }
              ]
            }

            必須ルール:
            - フィールド名・キーは上記と完全一致させる（スネークケース）。
            - 値はJSONとして有効な文字列のみを使用（改行・引用符はエスケープ）。
            - 入力と同一ならcorrectionsは空配列とし、corrected_textは入力をそのまま返す。
            - JSON以外のテキストは出力しない。
        """.trimIndent()
        val prompt = "$instruction\n\n入力: $text"
        val full = withContext(Dispatchers.IO) { runMediaPipeGenerate(prompt) }
        send(full ?: "{\"corrected_text\":\"$text\",\"corrections\":[]}")
    }

    private fun copyAssets(assetPath: String, destDir: File) {
        try {
            val am = context.assets
            val entries = am.list(assetPath) ?: return
            for (name in entries) {
                val child = if (assetPath.isEmpty()) name else "$assetPath/$name"
                val children = am.list(child)
                if (children != null && children.isNotEmpty()) {
                    val dir = File(destDir, name)
                    if (!dir.exists()) dir.mkdirs()
                    copyAssets(child, dir)
                } else {
                    val out = File(destDir, name)
                    if (!out.exists()) {
                        am.open(child).use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "copyAssets failed: ${t.message}")
        }
    }

    private fun tryInitMediaPipeGenerator() {
        val modelPath = taskModelPath ?: return
        val assetRelative = "models/lite_rt/gemma3/" + File(modelPath).name
        val candidates = listOf(
            Triple(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference",
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions",
                null
            )
        )
        for ((genCls, optCls, baseCls) in candidates) {
            try {
                Log.d(TAG, "Trying $genCls ...")
                val genClass = Class.forName(genCls)
                var instance: Any? = null

                // 1) Prefer simple path: createFromFile(Context, String)
                run {
                    val byFile =
                        genClass.methods.firstOrNull { it.name == "createFromFile" && it.parameterTypes.size == 2 }
                    if (byFile != null) {
                        try {
                            instance = byFile.invoke(null, context, modelPath)
                            if (instance == null) instance =
                                byFile.invoke(null, context, assetRelative)
                        } catch (t: Throwable) {
                            Log.w(TAG, "invoke createFromFile failed: ${t.message}")
                        }
                    }
                }
                if (instance != null) {
                    mpTextGenerator = instance
                    Log.d(TAG, "Initialized via createFromFile on $genCls")
                    return
                }

                // 2) Fallback: createFromOptions(Context, Options) if BaseOptions is available
                val baseOptionsClass = if (baseCls != null) try {
                    Class.forName(baseCls)
                } catch (_: Throwable) {
                    null
                } else null
                if (baseOptionsClass == null) {
                    Log.w(TAG, "BaseOptions class not found for $genCls; trying inner Options only")
                    // Try builder on LlmInference$LlmInferenceOptions directly
                    try {
                        val optionsClass = Class.forName(optCls)
                        val optBuilder = optionsClass.getMethod("builder").invoke(null)
                        // setModelPath or setModelAssetPath on Options builder (some builds expose this directly)
                        val setModelPath2 = optBuilder.javaClass.methods.firstOrNull {
                            it.name == "setModelPath" && it.parameterTypes.contentEquals(
                                arrayOf(
                                    String::class.java
                                )
                            )
                        }
                        val setModelAssetPath2 = optBuilder.javaClass.methods.firstOrNull {
                            it.name == "setModelAssetPath" && it.parameterTypes.contentEquals(
                                arrayOf(String::class.java)
                            )
                        }
                        setModelPath2?.invoke(optBuilder, modelPath)
                        setModelAssetPath2?.invoke(
                            optBuilder,
                            assetRelative
                        )
                        val optionsBuilt =
                            optBuilder.javaClass.getMethod("build").invoke(optBuilder)
                        val methods = genClass.methods.filter { it.name == "createFromOptions" }
                        for (m in methods) {
                            try {
                                val params = m.parameterTypes
                                instance = when (params.size) {
                                    2 -> m.invoke(null, context, optionsBuilt)
                                    3 -> m.invoke(null, context, optionsBuilt, null)
                                    else -> null
                                }
                                if (instance != null) break
                            } catch (ie: Throwable) {
                                Log.w(TAG, "invoke createFromOptions failed: ${ie.message}")
                            }
                        }
                        if (instance != null) {
                            mpTextGenerator = instance
                            Log.d(TAG, "Initialized with $genCls via inner Options")
                            return
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "inner Options builder path failed: ${t.message}")
                    }
                    continue
                }
                val baseBuilder = baseOptionsClass.getMethod("builder").invoke(null)
                val setModelPath = baseBuilder.javaClass.methods.firstOrNull {
                    it.name == "setModelPath" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                val setModelAssetPath = baseBuilder.javaClass.methods.firstOrNull {
                    it.name == "setModelAssetPath" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                setModelPath?.invoke(baseBuilder, modelPath)
                setModelAssetPath?.invoke(baseBuilder, assetRelative)
                val baseBuilt = baseBuilder.javaClass.getMethod("build").invoke(baseBuilder)

                val optionsClass = Class.forName(optCls)
                val optBuilder = optionsClass.getMethod("builder").invoke(null)
                val setBaseOptions =
                    optBuilder.javaClass.methods.firstOrNull { it.name == "setBaseOptions" && it.parameterTypes.size == 1 }
                setBaseOptions?.invoke(optBuilder, baseBuilt)
                val optionsBuilt = optBuilder.javaClass.getMethod("build").invoke(optBuilder)

                val methods = genClass.methods.filter { it.name == "createFromOptions" }
                for (m in methods) {
                    try {
                        val params = m.parameterTypes
                        instance = when (params.size) {
                            2 -> m.invoke(null, context, optionsBuilt)
                            3 -> m.invoke(null, context, optionsBuilt, null)
                            else -> null
                        }
                        if (instance != null) break
                    } catch (ie: Throwable) {
                        Log.w(TAG, "invoke createFromOptions failed: ${ie.message}")
                    }
                }
                if (instance != null) {
                    mpTextGenerator = instance
                    Log.d(TAG, "Initialized with $genCls via options")
                    return
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Class not available or init failed for $genCls: ${e.message}")
            }
        }
    }

    private fun runMediaPipeGenerate(prompt: String): String? {
        val gen = mpTextGenerator ?: return null
        return try {
            val m =
                gen.javaClass.methods.firstOrNull { (it.name == "generate" || it.name == "generateResponse") && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
            val result = m?.invoke(gen, prompt)
            when (result) {
                is String -> result
                else -> {
                    val getText =
                        result?.javaClass?.methods?.firstOrNull { it.name == "getText" && it.parameterTypes.isEmpty() }
                    val getOut =
                        result?.javaClass?.methods?.firstOrNull { it.name == "getOutputText" && it.parameterTypes.isEmpty() }
                    (getText?.invoke(result) as? String) ?: (getOut?.invoke(result) as? String)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "generate failed: ${t.message}")
            null
        }
    }
}


