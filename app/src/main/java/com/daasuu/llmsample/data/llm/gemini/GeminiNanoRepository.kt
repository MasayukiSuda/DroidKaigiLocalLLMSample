package com.daasuu.llmsample.data.llm.gemini

import android.content.Context
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.data.prompts.CommonPrompts
import com.daasuu.llmsample.domain.LLMRepository
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compatibilityChecker: GeminiNanoCompatibilityChecker
) : LLMRepository {

    private var model: GenerativeModel? = null
    private var isInitialized = false
    private var firstTokenTime: Long = 0L

    override suspend fun initialize() {
        if (isInitialized) return

        withContext(Dispatchers.IO) {
            try {
                // Check device compatibility first
                val compatibility = compatibilityChecker.isDeviceSupported()
                if (compatibility !is DeviceCompatibility.Supported) {
                    val message = compatibilityChecker.getCompatibilityMessage(compatibility)
                    println("Gemini Nano initialization failed: $message")
                    isInitialized = false
                    return@withContext
                }

                // Initialize GenerativeModel
                initGenerativeModel()
                isInitialized = model != null

                if (isInitialized) {
                    println("Gemini Nano initialized successfully")
                } else {
                    println("Failed to initialize Gemini Nano GenerativeModel")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
            }
        }
    }

    override suspend fun release() {
        model?.close()
        model = null
        isInitialized = false
    }

    override suspend fun isAvailable(): Boolean {
        return isInitialized && model != null
    }

    private fun initGenerativeModel() {
        try {
            model = GenerativeModel(
                generationConfig {
                    context = this@GeminiNanoRepository.context
                    temperature = 0.7f
                    topK = 40
                    maxOutputTokens = 1000
                }
            )
        } catch (e: Exception) {
            println("Failed to create GenerativeModel: ${e.message}")
            model = null
        }
    }

    override suspend fun generateChatResponse(prompt: String): Flow<String> = flow {
        if (!isInitialized || model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }

        // ベンチマークモードに応じてプロンプトを処理
        val finalPrompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildChatPrompt(prompt)
        } else {
            prompt // 最適化モード: プロンプトをそのまま使用（Gemini Nanoの特性を活かす）
        }

        generateStreamingResponse(finalPrompt)
    }.flowOn(Dispatchers.IO)

    override suspend fun summarizeText(text: String): Flow<String> = flow {
        if (!isInitialized || model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildSummarizationPrompt(text)
        } else {
            buildSummarizationPrompt(text) // 最適化モード: Gemini Nano向け最適化プロンプト
        }
        generateStreamingResponse(prompt)
    }.flowOn(Dispatchers.IO)

    override suspend fun proofreadText(text: String): Flow<String> = flow {
        if (!isInitialized || model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildProofreadingPrompt(text)
        } else {
            buildProofreadingPrompt(text) // 最適化モード: Gemini Nano向け最適化プロンプト
        }
        generateStreamingResponse(prompt)
    }.flowOn(Dispatchers.IO)


    private suspend fun FlowCollector<String>.generateStreamingResponse(prompt: String) {
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()

        try {
            if (model != null) {
                println("🚀 Using real Gemini Nano API for: ${prompt.take(50)}...")
                // Use streaming generation for real-time response
                model!!.generateContentStream(prompt)
                    .onCompletion {
                        println("✅ Gemini Nano stream completed")
                    }
                    .collect { response ->
                        response.text?.let { text ->
                            if (firstTokenTime == 0L) {
                                firstTokenTime = System.currentTimeMillis() - startTime
                                println("⚡ First token received in ${firstTokenTime}ms")
                            }
                            emit(text)
                        }
                    }
            } else {
                // Model not available, use mock response
                emit("Error: Gemini Nano model not initialized. Please check device compatibility.")
            }
        } catch (e: GenerativeAIException) {
            // Handle specific AI generation errors
            println("❌ GenerativeAI Error: ${e.message}")
            emit("Error: ${e.message}")
        } catch (e: Exception) {
            println("⚠️ Falling back to mock response due to: ${e.message}")
            e.printStackTrace()
            // Fallback to mock on other errors
            mockGenerate(prompt, startTime)
        }
    }

    private suspend fun FlowCollector<String>.mockGenerate(prompt: String, startTime: Long) {
        println("🤖 Using mock response for demonstration")

        val response = when {
            prompt.contains("要約") || prompt.lowercase()
                .contains("summarize") -> "【モック】 こちらはデモ用の要約レスポンスです。実際のGemini Nanoの使用には対応端末が必要です。"

            prompt.contains("校正") || prompt.lowercase()
                .contains("proofread") -> "【モック】 こちらはデモ用の校正レスポンスです。実際のGemini Nanoの使用には対応端末が必要です。"

            else -> "【モック】 こちらはデモ用のチャットレスポンスです。実際のGemini Nanoの使用には対応端末が必要です。試したいプロンプト: '${
                prompt.take(
                    30
                )
            }...'"
        }

        val tokens = response.split("。")

        tokens.forEach { token ->
            if (firstTokenTime == 0L) {
                firstTokenTime = System.currentTimeMillis() - startTime
            }
            if (token.isNotEmpty()) {
                emit("$token。")
                kotlinx.coroutines.delay(80) // Simulate on-device processing delay
            }
        }
    }


    private fun buildSummarizationPrompt(text: String): String {
        val isJapanese = CommonPrompts.containsJapanese(text)
        if (isJapanese) {
            return "以下のテキストを日本語で簡潔に要約してください:\n\n$text"
        }
        return "Please summarize the following text concisely:\n\n$text"
    }


    private fun buildProofreadingPrompt(text: String): String {
        val cleanText = text.trim().take(1200)
        if (cleanText.isEmpty()) return "テキストが空です。"
        return "以下の日本語文章を校正してください。間違いがあれば修正し、より自然な表現に改善してください:\n\n$cleanText"
    }
}