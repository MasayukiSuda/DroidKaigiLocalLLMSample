package com.daasuu.llmsample.data.llm.task

import android.content.Context
import android.util.Log
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
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
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LLMRepository {

    private val TAG = "TaskGenAI"
    private var isInitialized = false

    // MediaPipe GenAI (TextGenerator) via reflection
    private var llmInference: LlmInference? = null

    override suspend fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "TaskRepository already initialized, skipping")
            return
        }

        Log.d(TAG, "Initializing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // 前回のリソースが残っていれば確実にクリア
                llmInference?.let {
                    Log.w(TAG, "Previous generator instance found, clearing...")
                    llmInference?.close()
                    llmInference = null
                }

                val destDir = File(context.filesDir, "models/lite_rt/gemma3")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                    Log.d(TAG, "Created destination directory")
                }

                copyAssets("models/lite_rt/gemma3", destDir)

                val allFiles = destDir.listFiles()

                val taskFile = allFiles?.firstOrNull { it.extension.equals("task", true) }
                val taskModelPath = taskFile?.absolutePath
                Log.d(TAG, "taskModelPath=$taskModelPath")

                if (taskFile == null) {
                    Log.w(TAG, "No .task file found in directory")
                } else {
                    Log.d(
                        TAG,
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
                        Log.w(
                            TAG,
                            "Initialization failed, falling back to CPU: ${e.message}"
                        )
                        llmInference = null
                    }

                    isInitialized = initSuccess
                    Log.d(TAG, "TaskRepository initialization completed: ${isInitialized}")
                } else {
                    Log.w(TAG, "No .task model file found, TaskRepository not initialized")
                    isInitialized = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "TaskRepository initialization failed", e)
                llmInference = null
                isInitialized = false
            }
        }
    }

    override suspend fun release() {
        Log.d(TAG, "Releasing TaskRepository...")
        withContext(Dispatchers.IO) {
            try {
                // MediaPipe GenAI インスタンスを明示的にクリア
                llmInference?.let { generator ->
                    try {
                        generator.close()
                        Log.d(TAG, "MediaPipe generator closed successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close MediaPipe generator: ${e.message}")
                    }
                }

                llmInference = null
                isInitialized = false
                Log.d(TAG, "TaskRepository released successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TaskRepository release", e)
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

        Log.d(
            TAG,
            "generateChatResponse: isInitialized=$isInitialized, mpTextGenerator=$llmInference"
        )

        var hasEmitted = false
        try {
            Log.d(TAG, "Starting runMediaPipeGenerate flow collection...")
            runMediaPipeGenerate(finalPrompt).collect { token ->
                send(token)
                hasEmitted = true
                delay(12)
            }
            Log.d(TAG, "runMediaPipeGenerate flow collection completed, hasEmitted=$hasEmitted")
        } catch (e: Exception) {
            Log.w(TAG, "MediaPipe generate failed: ${e.message}; falling back to mock")
            hasEmitted = false
        }

        if (!hasEmitted) {
            Log.w(TAG, "No tokens were emitted, falling back to mock response")
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
            Log.w(TAG, "MediaPipe generate failed: ${e.message}")
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
            Log.w(TAG, "MediaPipe generate failed: ${e.message}")
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
            Log.w(TAG, "copyAssets failed: ${t.message}")
        }
    }

    private fun tryInitMediaPipeGenerator(modelPath: String?) {
        modelPath ?: return

        Log.d(TAG, "Model path: $modelPath")

        try {
            val baseOptionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .build()
            llmInference = LlmInference.createFromOptions(context, baseOptionsBuilder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LlmInference: ${e.message}", e)
            llmInference = null
        }
    }

    private fun runMediaPipeGenerate(prompt: String): Flow<String> = channelFlow {
        Log.d(TAG, "runMediaPipeGenerate called with prompt length: ${prompt.length}")

        val llmInference = llmInference
        if (llmInference == null) {
            Log.w(TAG, "MediaPipe generator not available - mpTextGenerator is null")
            return@channelFlow
        }

        try {
            Log.d(TAG, "Starting MediaPipe generation with prompt length: ${prompt.length}")

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
            Log.d(TAG, "generateResponseAsync called successfully")

            // Wait for the callback to complete or channel to be closed
            awaitClose {
                Log.d(TAG, "ChannelFlow is being closed")
            }
        } catch (t: Throwable) {
            // JobCancellationExceptionは正常な終了の場合に発生するため、ログレベルを下げる
            Log.e(
                TAG,
                "MediaPipe generation failed with exception: ${t.javaClass.simpleName}: ${t.message}",
                t
            )
            close(t)
        }
    }
}


