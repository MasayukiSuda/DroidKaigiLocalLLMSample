package com.daasuu.llmsample.data.llm.task

import android.content.Context
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.data.preferences.PreferencesManager
import com.daasuu.llmsample.data.prompts.CommonPrompts
import com.daasuu.llmsample.domain.LLMRepository
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
) : LLMRepository {

    private val TAG = "TaskGenAI"
    private var isInitialized = false

    // MediaPipe GenAI (TextGenerator) via reflection
    private var llmInference: LlmInference? = null

    override suspend fun initialize() {
        if (isInitialized) {
            Timber.d("TaskRepository already initialized, skipping")
            return
        }

        Timber.d("Initializing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // 前回のリソースが残っていれば確実にクリア
                llmInference?.let {
                    Timber.w("Previous generator instance found, clearing...")
                    llmInference?.close()
                    llmInference = null
                }

                val destDir = File(context.filesDir, "models/lite_rt/gemma3")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                    Timber.d("Created destination directory")
                }

                copyAssets("models/lite_rt/gemma3", destDir)

                val allFiles = destDir.listFiles()

                val taskFile = allFiles?.firstOrNull { it.extension.equals("task", true) }
                val taskModelPath = taskFile?.absolutePath
                Timber.d("taskModelPath=$taskModelPath")

                if (taskFile == null) {
                    Timber.w("No .task file found in directory")
                } else {
                    Timber.d(
                        "Found .task file: ${taskFile.name}, size: ${taskFile.length()} bytes"
                    )
                }

                if (taskModelPath != null) {
                    var initSuccess = false
                    try {
                        tryInitMediaPipeGenerator(taskModelPath)
                        if (llmInference != null) {
                            initSuccess = true
                        }
                    } catch (e: Exception) {
                        Timber.w(
                            "Initialization failed, falling back to CPU: ${e.message}"
                        )
                        llmInference = null
                    }

                    isInitialized = initSuccess
                    Timber.d("TaskRepository initialization completed: ${isInitialized}")
                } else {
                    Timber.w("No .task model file found, TaskRepository not initialized")
                    isInitialized = false
                }
            } catch (e: Exception) {
                Timber.e(e, "TaskRepository initialization failed")
                llmInference = null
                isInitialized = false
            }
        }
    }

    override suspend fun release() {
        Timber.d("Releasing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // MediaPipe GenAI インスタンスを明示的にクリア
                llmInference?.let { generator ->
                    try {
                        generator.close()
                        Timber.d("MediaPipe generator closed successfully")
                    } catch (e: Exception) {
                        Timber.w("Failed to close MediaPipe generator: ${e.message}")
                    }
                }

                llmInference = null
                isInitialized = false
                Timber.d("TaskRepository released successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error during TaskRepository release")
                // エラーがあってもisInitializedをfalseにしてリセット
                llmInference = null
                isInitialized = false
            }
        }
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

        Timber.d(
            "generateChatResponse: isInitialized=$isInitialized, mpTextGenerator=$llmInference"
        )

        var hasEmitted = false
        try {
            Timber.d("Starting runMediaPipeGenerate flow collection...")
            runMediaPipeGenerate(finalPrompt).collect { token ->
                send(token)
                hasEmitted = true
                delay(12)
            }
            Timber.d("runMediaPipeGenerate flow collection completed, hasEmitted=$hasEmitted")
        } catch (e: Exception) {
            Timber.w("MediaPipe generate failed: ${e.message}; falling back to mock")
            hasEmitted = false
        }

        if (!hasEmitted) {
            Timber.w("No tokens were emitted, falling back to mock response")
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

        var hasEmitted = false
        try {
            runMediaPipeGenerate(prompt).collect { token ->
                send(token)
                hasEmitted = true
                delay(12)
            }
        } catch (e: Exception) {
            Timber.w("MediaPipe generate failed: ${e.message}")
        }

        if (!hasEmitted) {
            send("- 要約(擬似): " + text.take(40) + "...")
        }
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

        var hasEmitted = false
        try {
            runMediaPipeGenerate(prompt).collect { token ->
                send(token)
                hasEmitted = true
                delay(12)
            }
        } catch (e: Exception) {
            Timber.w("MediaPipe generate failed: ${e.message}")
        }

        if (!hasEmitted) {
            send("{\"corrected_text\":\"$text\",\"corrections\":[]}")
        }
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
            Timber.w("copyAssets failed: ${t.message}")
        }
    }

    private suspend fun tryInitMediaPipeGenerator(modelPath: String?) {
        modelPath ?: return

        Timber.d("Model path: $modelPath")

        try {
            val isGpuEnabled = preferencesManager.isGpuEnabled.first()
            val baseOptionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setPreferredBackend(if (isGpuEnabled) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
                .build()
            llmInference = LlmInference.createFromOptions(context, baseOptionsBuilder)
        } catch (e: Exception) {
            Timber.e("Failed to initialize LlmInference: ${e.message}", e)
            llmInference = null
        }
    }

    private fun runMediaPipeGenerate(prompt: String): Flow<String> = channelFlow {
        Timber.d("runMediaPipeGenerate called with prompt length: ${prompt.length}")

        val llmInference = llmInference
        if (llmInference == null) {
            Timber.w("MediaPipe generator not available - mpTextGenerator is null")
            return@channelFlow
        }

        try {
            Timber.d("Starting MediaPipe generation with prompt length: ${prompt.length}")

            // Create callback for async response
            val callback = ProgressListener<String> { partialResult, done ->
                if (partialResult?.isNotBlank() == true) {
                    trySend(partialResult)
                }
                if (done) {
                    close() // Close the channel when generation is complete
                }
            }

            llmInference.generateResponseAsync(prompt, callback)
            Timber.d("generateResponseAsync called successfully")

            // Wait for the callback to complete or channel to be closed
            awaitClose {
                Timber.d("ChannelFlow is being closed")
            }
        } catch (t: Throwable) {
            // JobCancellationExceptionは正常な終了の場合に発生するため、ログレベルを下げる
            Timber.e(
                t,
                "MediaPipe generation failed with exception: ${t.javaClass.simpleName}: ${t.message}"
            )
            close(t)
        }
    }
}


