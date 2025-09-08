package com.daasuu.llmsample.data.llm.task

import android.content.Context
import android.util.Log
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.data.prompts.CommonPrompts
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMRepository
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : LLMRepository {

    private val TAG = "TaskGenAI"
    private var isInitialized = false
    private var taskModelPath: String? = null

    // MediaPipe GenAI (TextGenerator) via reflection
    private var mpTextGenerator: LlmInference? = null

    override suspend fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "TaskRepository already initialized, skipping")
            return
        }

        Log.d(TAG, "Initializing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // 前回のリソースが残っていれば確実にクリア
                mpTextGenerator?.let {
                    Log.w(TAG, "Previous generator instance found, clearing...")
                    mpTextGenerator?.close()
                    mpTextGenerator = null
                }

                val destDir = File(context.filesDir, "models/lite_rt/gemma3")
                if (!destDir.exists()) destDir.mkdirs()
                copyAssets("models/lite_rt/gemma3", destDir)
                val taskFile =
                    destDir.listFiles()?.firstOrNull { it.extension.equals("task", true) }
                taskModelPath = taskFile?.absolutePath
                Log.d(TAG, "taskModelPath=$taskModelPath")

                if (taskModelPath != null) {
                    val isGpuEnabled = settingsRepository.isGpuEnabled.first()

                    // エミュレータ検知
                    val isEmulator = isRunningOnEmulator()
                    if (isEmulator) {
                        Log.w(TAG, "Running on emulator, disabling GPU acceleration")
                    }

                    val actualGpuEnabled = isGpuEnabled && !isEmulator
                    Log.d(
                        TAG,
                        "Starting MediaPipe initialization with GPU=${actualGpuEnabled} (requested: ${isGpuEnabled}, emulator: ${isEmulator})"
                    )

                    // 段階的初期化: GPU有効 → GPU無効 → 初期化スキップ
                    var initSuccess = false

                    if (actualGpuEnabled) {
                        try {
                            Log.d(TAG, "Attempting GPU initialization...")
                            tryInitMediaPipeGenerator(true)
                            if (mpTextGenerator != null) {
                                initSuccess = true
                                Log.d(TAG, "GPU initialization successful")
                            }
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "GPU initialization failed, falling back to CPU: ${e.message}"
                            )
                            mpTextGenerator = null
                        }
                    }

                    if (!initSuccess) {
                        try {
                            Log.d(TAG, "Attempting CPU initialization...")
                            tryInitMediaPipeGenerator(false)
                            if (mpTextGenerator != null) {
                                initSuccess = true
                                Log.d(TAG, "CPU initialization successful")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "CPU initialization also failed: ${e.message}")
                            mpTextGenerator = null
                        }
                    }

                    isInitialized = initSuccess
                    Log.d(TAG, "TaskRepository initialization completed: ${isInitialized}")
                } else {
                    Log.w(TAG, "No .task model file found, TaskRepository not initialized")
                    isInitialized = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "TaskRepository initialization failed", e)
                mpTextGenerator = null
                isInitialized = false
            }
        }
    }

    override suspend fun release() {
        Log.d(TAG, "Releasing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // MediaPipe GenAI インスタンスを明示的にクリア
                mpTextGenerator?.let { generator ->
                    try {
                        generator.close()
                        Log.d(TAG, "MediaPipe generator closed successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close MediaPipe generator: ${e.message}")
                    }
                }

                mpTextGenerator = null
                isInitialized = false
                Log.d(TAG, "TaskRepository released successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TaskRepository release", e)
                // エラーがあってもisInitializedをfalseにしてリセット
                mpTextGenerator = null
                isInitialized = false
            }
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic") ||
                "google_sdk" == android.os.Build.PRODUCT
    }

    override suspend fun isAvailable(): Boolean = isInitialized

    override suspend fun generateChatResponse(prompt: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("Error: .task model not found"); return@channelFlow
        }

        // ベンチマークモードに応じてプロンプトを処理
        val finalPrompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildChatPrompt(prompt)
        } else {
            prompt // 最適化モード: プロンプトをそのまま使用
        }

        val generated = withContext(Dispatchers.IO) { runMediaPipeGenerate(finalPrompt) }
        if (generated != null) {
            generated.split(" ").forEach { token ->
                send("$token ")
                delay(12)
            }
        } else {
            Log.w(TAG, "MediaPipe generate failed; falling back to mock")
            withContext(Dispatchers.IO) {
                ("[task mock] Gemma3: $finalPrompt").split(" ").forEach {
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

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildSummarizationPrompt(text)
        } else {
            "要約してください: \n\n$text" // 最適化モード: MediaPipe GenAI API向けシンプルプロンプト
        }

        val full = withContext(Dispatchers.IO) { runMediaPipeGenerate(prompt) }
        send(full ?: ("- 要約(擬似): " + text.take(40) + "..."))
    }

    override suspend fun proofreadText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) initialize()
        if (!isInitialized) {
            send("{}"); return@channelFlow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildProofreadingPrompt(text)
        } else {
            // 最適化モード: MediaPipe GenAI API向け詳細JSON指示
            val instruction = """
                以下の日本語文を正しく自然な表現に校正してください。出力は校正後の文章だけを返してください。
            """.trimIndent()
            "$instruction\n\n入力: $text"
        }

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

    private fun tryInitMediaPipeGenerator(enableGpu: Boolean = false) {
        val modelPath = taskModelPath ?: return

        Log.d(TAG, "tryInitMediaPipeGenerator called with enableGpu: $enableGpu")
        Log.d(TAG, "Model path: $modelPath")

        try {
            val baseOptionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)

            mpTextGenerator = LlmInference.createFromOptions(context, baseOptionsBuilder.build())

            if (mpTextGenerator != null) {
                Log.d(TAG, "Initialized via createFromOptions with GPU=$enableGpu")
            } else {
                Log.w(TAG, "createFromOptions returned null")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LlmInference: ${e.message}", e)
            mpTextGenerator = null
        }
    }

    private fun runMediaPipeGenerate(prompt: String): String? {
        val gen = mpTextGenerator ?: return null
        return try {
            Log.d(TAG, "Starting MediaPipe generation with prompt length: ${prompt.length}")
            // 直接generateResponseメソッドを呼び出し
            val result = gen.generateResponse(prompt)
            Log.d(TAG, "MediaPipe generation completed")

            result
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "MediaPipe generation failed with exception: ${t.javaClass.simpleName}: ${t.message}",
                t
            )
            null
        }
    }
}


