package com.daasuu.llmsample.data.llm.gemini

import android.content.Context
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.domain.LLMRepository
// Note: Official Gemini Nano API is not yet publicly available
// This implementation uses experimental AICore service access
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class GeminiNanoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compatibilityChecker: GeminiNanoCompatibilityChecker,
    private val aicoreServiceHelper: AICoreServiceHelper
) : LLMRepository {
    
    private val aicoreHelper: AICoreServiceHelper by lazy {
        AICoreServiceHelper(context)
    }
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
                
                // Check if AICore service is available
                if (!aicoreServiceHelper.isAICoreAvailable()) {
                    println("AICore service not available on this device")
                    isInitialized = false
                    return@withContext
                }
                
                // Attempt to connect to AICore service
                val connected = aicoreServiceHelper.connectToAICore()
                if (connected) {
                    isInitialized = true
                    println("Gemini Nano initialized successfully via AICore")
                } else {
                    isInitialized = false
                    println("Failed to connect to AICore service")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
            }
        }
    }
    
    override suspend fun release() {
        aicoreServiceHelper.disconnect()
        isInitialized = false
    }
    
    override suspend fun isAvailable(): Boolean {
        return isInitialized && aicoreServiceHelper.isAICoreAvailable()
    }
    
    override suspend fun generateChatResponse(prompt: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }
        
        val fullPrompt = buildChatPrompt(prompt)
        generateResponse(fullPrompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun summarizeText(text: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }
        
        val prompt = buildSummarizationPrompt(text)
        generateResponse(prompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun proofreadText(text: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: Gemini Nano not initialized")
            return@flow
        }
        
        val prompt = buildProofreadingPrompt(text)
        generateResponse(prompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    

    
    private suspend fun generateResponse(prompt: String, onToken: suspend (String) -> Unit) {
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()
        
        withContext(Dispatchers.IO) {
            try {
                if (isInitialized && aicoreServiceHelper.isAICoreAvailable()) {
                    // Attempt to use AICore service for Gemini Nano
                    val response = aicoreServiceHelper.generateText(prompt)
                    
                    if (response != null) {
                        // Simulate streaming by chunking the response
                        val chunks = response.chunked(20)
                        chunks.forEach { chunk ->
                            if (firstTokenTime == 0L) {
                                firstTokenTime = System.currentTimeMillis() - startTime
                            }
                            onToken(chunk)
                            kotlinx.coroutines.delay(80) // Slightly slower for on-device processing
                        }
                    } else {
                        // AICore service failed, use mock response
                        mockGenerate(prompt, onToken, startTime)
                    }
                } else {
                    // Device not supported or service not available
                    onToken("Error: Gemini Nano not available on this device. Please use a supported Pixel or Galaxy device.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to mock on error
                mockGenerate(prompt, onToken, startTime)
            }
        }
    }
    
    private suspend fun mockGenerate(prompt: String, onToken: suspend (String) -> Unit, startTime: Long) {
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
                onToken(chunk)
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
            onToken("$token ")
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