package com.daasuu.llmsample.data.llm.gemini

import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.data.prompts.CommonPrompts
import com.daasuu.llmsample.domain.LLMRepository
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoRepository @Inject constructor(
    private val modelManager: GeminiNanoModelManager
) : LLMRepository {

    override suspend fun initialize() {
        // No-op: ModelManagerが遅延初期化を担当するため、ここでは何もしない
    }

    override suspend fun release() {
        modelManager.releaseModel()
    }

    override suspend fun isAvailable(): Boolean {
        return modelManager.isModelAvailable()
    }

    override suspend fun generateChatResponse(prompt: String): Flow<String> = flow {
        val model = modelManager.getOrCreateModel()
        if (model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }

        // ベンチマークモードに応じてプロンプトを処理
        val finalPrompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildChatPrompt(prompt)
        } else {
            prompt // 最適化モード: プロンプトをそのまま使用（Gemini Nanoの特性を活かす）
        }

        generateStreamingResponse(model, finalPrompt)
    }.flowOn(Dispatchers.IO)

    override suspend fun summarizeText(text: String): Flow<String> = flow {
        val model = modelManager.getOrCreateModel()
        if (model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildSummarizationPrompt(text)
        } else {
            buildSummarizationPrompt(text) // 最適化モード: Gemini Nano向け最適化プロンプト
        }
        generateStreamingResponse(model, prompt)
    }.flowOn(Dispatchers.IO)

    override suspend fun proofreadText(text: String): Flow<String> = flow {
        val model = modelManager.getOrCreateModel()
        if (model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }

        val prompt = if (BenchmarkMode.isCurrentlyEnabled()) {
            CommonPrompts.buildProofreadingPrompt(text)
        } else {
            buildProofreadingPrompt(text) // 最適化モード: Gemini Nano向け最適化プロンプト
        }
        generateStreamingResponse(model, prompt)
    }.flowOn(Dispatchers.IO)

    /**
     * ストリーミングレスポンスを生成
     * 各リクエストごとに独立したローカル状態を持つ
     */
    private suspend fun FlowCollector<String>.generateStreamingResponse(
        model: GenerativeModel,
        prompt: String
    ) {
        val startTime = System.currentTimeMillis()
        var firstTokenTime = 0L // ← ローカル変数: リクエストごとに独立

        try {
            Timber.d("🚀 Using real Gemini Nano API for: ${prompt.take(50)}...")
            // Use streaming generation for real-time response
            model.generateContentStream(prompt)
                .onCompletion {
                    Timber.d("✅ Gemini Nano stream completed")
                }
                .collect { response ->
                    response.text?.let { text ->
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis() - startTime
                            Timber.d("⚡ First token received in ${firstTokenTime}ms")
                        }
                        emit(text)
                    }
                }
        } catch (e: GenerativeAIException) {
            // Handle specific AI generation errors
            Timber.e("❌ GenerativeAI Error: ${e.message}")
            emit("Error: ${e.message}")
        } catch (e: Exception) {
            Timber.w("⚠️ Falling back to mock response due to: ${e.message}")
            e.printStackTrace()
            // Fallback to mock on other errors
            mockGenerate(prompt, startTime)
        }
    }

    private suspend fun FlowCollector<String>.mockGenerate(prompt: String, startTime: Long) {
        Timber.d("🤖 Using mock response for demonstration")
        var firstTokenTime = 0L // ← モック用のローカル変数

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