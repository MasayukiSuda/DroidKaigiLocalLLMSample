package com.daasuu.llmsample.data.llm.llamacpp

import android.llama.cpp.LLamaAndroid
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import timber.log.Timber
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.preferences.PreferencesManager
import com.daasuu.llmsample.data.prompts.CommonPrompts
import com.daasuu.llmsample.domain.LLMRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaCppRepository @Inject constructor(
    private val modelManager: ModelManager,
    private val llamaAndroid: LLamaAndroid,
    private val preferencesManager: PreferencesManager,
) : LLMRepository {

    private var isInitialized = false
    private var firstTokenTime: Long = 0L
    private var isMock: Boolean = false

    override suspend fun initialize() {
        if (isInitialized) return

        withContext(Dispatchers.IO) {
            try {
                // Get user-selected model preference
                val selectedModelId = preferencesManager.selectedLlamaModel.first()

                // Get all downloaded models
                val downloadedModels = modelManager.getAvailableModels()
                    .filter { it.isDownloaded }

                if (downloadedModels.isNotEmpty()) {
                    // If a specific model is selected, try it first
                    selectedModelId?.let { modelId ->
                        val selectedModel = downloadedModels.find { it.id == modelId }
                        selectedModel?.let { model ->
                            if (tryLoadModel(model)) {
                                return@withContext
                            }
                        }
                    }

                    // If no specific model selected or selected model failed, try all downloaded models
                    for (modelInfo in downloadedModels) {
                        if (tryLoadModel(modelInfo)) {
                            return@withContext
                        }
                    }
                    // If we get here, no models worked
                    Timber.d("All downloaded models failed to load, falling back to mock")
                } else {
                    Timber.d("No downloaded models found, using mock implementation")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.e(e, "Exception during LlamaCpp initialization: ${e.message}")
            }
        }
    }

    private suspend fun tryLoadModel(modelInfo: com.daasuu.llmsample.data.model.ModelInfo): Boolean {
        val modelPath = modelInfo.localPath ?: return false

        // Check if model file actually exists and is readable
        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            Timber.w("Model file does not exist: $modelPath")
            return false
        }
        if (!modelFile.canRead()) {
            Timber.w("Cannot read model file: $modelPath")
            return false
        }
        if (modelFile.length() == 0L) {
            Timber.w("Model file is empty: $modelPath")
            return false
        }

        return try {
            Timber.d("Attempting to initialize LlamaCpp with model: ${modelInfo.name} at $modelPath (${modelFile.length()} bytes)")
            llamaAndroid.load(modelPath)
            isInitialized = true
            isMock = false
            Timber.d("Successfully loaded model: ${modelInfo.name}")
            true
        } catch (e: Exception) {
            Timber.w("Failed to load model ${modelInfo.name}: ${e.message}")
            false
        }
    }

    override suspend fun release() {
        llamaAndroid.unload()
    }

    override suspend fun isAvailable(): Boolean = isInitialized

    override suspend fun generateChatResponse(prompt: String): Flow<String> = channelFlow {
        if (!isInitialized) {
            send("Error: Llama.cpp model not initialized")
            return@channelFlow
        }

        val fullPrompt = buildChatPrompt(prompt)
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()
        val responseBuilder = StringBuilder()

        withContext(Dispatchers.IO) {
            llamaAndroid.send(fullPrompt, maxLength = 1024)
                .catch { error ->
                    Timber.e(error, "send() failed")
                    trySend(" Error: $error")
                }
                .collect {
                    if (firstTokenTime == 0L) {
                        firstTokenTime = System.currentTimeMillis() - startTime
                    }

                    responseBuilder.append(it)
                    trySend(it)
                }
        }
    }

    override suspend fun summarizeText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) {
            send("Error: Llama.cpp model not initialized")
            return@channelFlow
        }

        val prompt = buildSummarizationPrompt(text)
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            llamaAndroid.send(message = prompt, maxLength = 512)
                .catch { error ->
                    Timber.e(error, "send() failed")
                    trySend(" Error: $error")
                }
                .collect {
                    if (firstTokenTime == 0L) {
                        firstTokenTime = System.currentTimeMillis() - startTime
                    }
                    trySend(it)
                }
        }
    }

    override suspend fun proofreadText(text: String): Flow<String> = channelFlow {
        if (!isInitialized) {
            send("Error: Llama.cpp model not initialized")
            return@channelFlow
        }

        val prompt = buildProofreadingPrompt(text)
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()

        // If running in mock mode and prompt requests JSON, synthesize JSON stream here
        if (isMock && prompt.contains("JSON only") && prompt.contains("Text:")) {
            val original = prompt.substringAfter("Text:").trim()
            val corrected = original.replace("テキスト", "文章")
            val startIdx = original.indexOf("テキスト").coerceAtLeast(0)
            val endIdx = (if (startIdx >= 0) startIdx + 3 else 0)
            val json = """
            {"corrected_text":"${corrected}","corrections":[{"original":"テキスト","suggested":"文章","type":"表現","explanation":"より自然な表現です","start":${startIdx},"end":${endIdx}}]}
            """.trimIndent()
            val chunks = json.chunked(24)
            withContext(Dispatchers.IO) {
                chunks.forEach { chunk ->
                    if (firstTokenTime == 0L) {
                        firstTokenTime = System.currentTimeMillis() - startTime
                    }
                    trySend(chunk)
                    kotlinx.coroutines.delay(30)
                }
            }
            return@channelFlow
        }

        withContext(Dispatchers.IO) {
            llamaAndroid.send(prompt, maxLength = 512)
                .catch { error ->
                    Timber.e(error, "send() failed")
                    trySend(" Error: $error")
                }
                .collect {
                    if (firstTokenTime == 0L) {
                        firstTokenTime = System.currentTimeMillis() - startTime
                    }
                    trySend(it)
                }
        }
    }

    private fun buildChatPrompt(userMessage: String): String {
        // ベンチマークモードが有効な場合は統一プロンプトを使用
        if (BenchmarkMode.isCurrentlyEnabled()) {
            return CommonPrompts.buildChatPrompt(userMessage)
        }

        // 最適化モード: Llama.cpp向けの詳細なシステムプロンプト
        val cleanMessage = userMessage.trim().take(500)
        if (cleanMessage.isEmpty()) return "Hello"

        // Detect language rough heuristic to localize system prompt
        val isJapanese = CommonPrompts.containsJapanese(cleanMessage)
        return if (isJapanese) {
            // 単発・簡潔回答 + ロールプレイ禁止を明示
            (
                    "指示: 次の入力に日本語で簡潔に返答してください。2文以内。ロールプレイや複数人物の会話(例: Mom:, Son:, 母:, 父:, 息子:, 娘:)は禁止。あなた(Assistant)以外の発話は書かない。\n" +
                            "入力: " + cleanMessage + "\n" +
                            "返答:"
                    )
        } else {
            (
                    "Instruction: Respond concisely in at most 2 sentences. Do NOT roleplay or write multi-speaker dialogues (e.g., Mom:, Dad:, Son:, Daughter:). Write only your answer as the assistant.\n" +
                            "Input: " + cleanMessage + "\n" +
                            "Answer:"
                    )
        }
    }

    private fun buildSummarizationPrompt(text: String): String {
        // ベンチマークモードが有効な場合は統一プロンプトを使用
        if (BenchmarkMode.isCurrentlyEnabled()) {
            return CommonPrompts.buildSummarizationPrompt(text)
        }

        // 最適化モード: Llama.cpp向けの詳細な指示
        val cleanText = text.trim().take(500) // Very short limit
        if (cleanText.isEmpty()) {
            return "Hello"
        }

        val isJapanese = CommonPrompts.containsJapanese(cleanText)
        if (isJapanese) {
            return """
以下のテキストを日本語で簡潔に要約してください。出力は箇条書きで2〜3点。前置きや締めの文は不要で、要点のみを示してください。翻訳はせず、日本語で出力してください。

本文:
---
$cleanText
---

要約:
-
""".trimIndent()
        }

        // Non-Japanese: keep English instruction but emphasize not to translate
        return """
Summarize the following text concisely in the same language as the input. Output 2-3 bullet points. Do not translate. No preface or closing, only the summary.

Text:
---
$cleanText
---

Summary:
-
""".trimIndent()
    }


    private fun buildProofreadingPrompt(text: String): String {
        // ベンチマークモードが有効な場合は統一プロンプトを使用
        if (BenchmarkMode.isCurrentlyEnabled()) {
            return CommonPrompts.buildProofreadingPrompt(text)
        }

        // 最適化モード: Llama.cpp向けのJSON形式指示
        val cleanText = text.trim().take(500)
        if (cleanText.isEmpty()) {
            return "{}"
        }

        // Ask for strict JSON only to allow deterministic parsing on UI side
        // Keep everything single-line and short
        return (
                "JSON only. No prose. Japanese proofreading: minimal edits only, preserve meaning/order, do not add info. " +
                        "Format {\"corrected_text\":string,\"corrections\":[{\"original\":string,\"suggested\":string,\"type\":string,\"explanation\":string,\"start\":number,\"end\":number}]}. " +
                        "Text: " + cleanText
                )
    }
}