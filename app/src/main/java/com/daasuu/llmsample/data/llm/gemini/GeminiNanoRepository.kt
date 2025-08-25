package com.daasuu.llmsample.data.llm.gemini

import android.content.Context
import com.daasuu.llmsample.data.model.LLMProvider
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
import kotlin.system.measureTimeMillis

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
        
        val fullPrompt = buildChatPrompt(prompt)
        generateStreamingResponse(fullPrompt)
    }.flowOn(Dispatchers.IO)
    
    override suspend fun summarizeText(text: String): Flow<String> = flow {
        if (!isInitialized || model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }
        
        val prompt = buildSummarizationPrompt(text)
        generateStreamingResponse(prompt)
    }.flowOn(Dispatchers.IO)
    
    override suspend fun proofreadText(text: String): Flow<String> = flow {
        if (!isInitialized || model == null) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }
        
        val prompt = buildProofreadingPrompt(text)
        generateStreamingResponse(prompt)
    }.flowOn(Dispatchers.IO)
    

    
    private suspend fun FlowCollector<String>.generateStreamingResponse(prompt: String) {
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()
        
        try {
            if (model != null) {
                // Use streaming generation for real-time response
                model!!.generateContentStream(prompt)
                    .onCompletion { /* Stream completed */ }
                    .collect { response ->
                        response.text?.let { text ->
                            if (firstTokenTime == 0L) {
                                firstTokenTime = System.currentTimeMillis() - startTime
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
            println("GenerativeAI Error: ${e.message}")
            emit("Error: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to mock on other errors
            mockGenerate(prompt, startTime)
        }
    }
    
    private suspend fun FlowCollector<String>.mockGenerate(prompt: String, startTime: Long) {
        // If proofreading prompt requests JSON, return deterministic JSON for demo
        if (prompt.contains("JSON only") && prompt.contains("Text:")) {
            val original = prompt.substringAfter("Text:").trim()
            val corrected = original.replace("テキスト", "文章")
            val startIdx = original.indexOf("テキスト").coerceAtLeast(0)
            val endIdx = (if (startIdx >= 0) startIdx + 3 else 0)
            val json = """
            {"corrected_text":"${corrected}","corrections":[{"original":"テキスト","suggested":"文章","type":"表現","explanation":"より自然な表現です","start":${startIdx},"end":${endIdx}}]}
            """.trimIndent()
            val chunks = json.chunked(24)
            chunks.forEach { chunk ->
                if (firstTokenTime == 0L) {
                    firstTokenTime = System.currentTimeMillis() - startTime
                }
                emit(chunk)
                kotlinx.coroutines.delay(40)
            }
            return
        }

        val response = "Gemini Nano（オンデバイス）による回答: $prompt に対する詳細な応答をここに生成します。"
        val tokens = response.split(" ")
        
        tokens.forEach { token ->
            if (firstTokenTime == 0L) {
                firstTokenTime = System.currentTimeMillis() - startTime
            }
            emit("$token ")
            kotlinx.coroutines.delay(60) // Slightly faster
        }
    }
    
    private fun buildChatPrompt(userMessage: String): String {
        return "User: $userMessage\nAssistant: "
    }
    
    private fun buildSummarizationPrompt(text: String): String {
        val isJapanese = containsJapanese(text)
        if (isJapanese) {
            return """
以下のテキストを日本語で簡潔に要約してください。出力は箇条書きで2〜3点。前置きや締めの文は不要で、要点のみを示してください。翻訳はせず、日本語で出力してください。

本文:
---
$text
---

要約:
-
""".trimIndent()
        }

        return """
Summarize the following text concisely in the same language as the input. Output 2-3 bullet points. Do not translate. No preface or closing, only the summary.

Text:
---
$text
---

Summary:
-
""".trimIndent()
    }

    private fun containsJapanese(text: String): Boolean {
        for (ch in text) {
            val block = java.lang.Character.UnicodeBlock.of(ch)
            if (block == java.lang.Character.UnicodeBlock.HIRAGANA ||
                block == java.lang.Character.UnicodeBlock.KATAKANA ||
                block == java.lang.Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
            ) {
                return true
            }
        }
        return false
    }
    
    private fun buildProofreadingPrompt(text: String): String {
        val cleanText = text.trim().take(1200)
        if (cleanText.isEmpty()) return "{}"
        return (
            "JSON only. No prose. Japanese proofreading: minimal edits only, preserve meaning/order, no additions. " +
            "Format {\"corrected_text\":string,\"corrections\":[{\"original\":string,\"suggested\":string,\"type\":string,\"explanation\":string,\"start\":number,\"end\":number}]}. " +
            "Text: " + cleanText
        )
    }
}