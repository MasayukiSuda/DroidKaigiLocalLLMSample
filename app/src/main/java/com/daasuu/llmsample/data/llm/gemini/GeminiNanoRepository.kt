package com.daasuu.llmsample.data.llm.gemini

import android.content.Context
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.domain.LLMRepository
// import com.google.mlkit.genai.GenerativeModel
// import com.google.mlkit.genai.GenerativeModels
// import com.google.mlkit.genai.HarmCategory
// import com.google.mlkit.genai.SafetySetting
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
    @ApplicationContext private val context: Context
) : LLMRepository {
    
    // private var generativeModel: GenerativeModel? = null
    private var isInitialized = false
    private var firstTokenTime: Long = 0L
    
    override suspend fun initialize() {
        if (isInitialized) return
        
        withContext(Dispatchers.IO) {
            try {
                // Mock implementation for demo purposes
                // In production, this would initialize the actual Gemini Nano model
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
            }
        }
    }
    
    override suspend fun release() {
        // generativeModel = null
        isInitialized = false
    }
    
    override suspend fun isAvailable(): Boolean {
        return isInitialized
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
                // Mock implementation for demo
                // In production, this would use the actual Gemini Nano API
                mockGenerate(prompt, onToken, startTime)
            } catch (e: Exception) {
                e.printStackTrace()
                mockGenerate(prompt, onToken, startTime)
            }
        }
    }
    
    private suspend fun mockGenerate(prompt: String, onToken: suspend (String) -> Unit, startTime: Long) {
        val response = "Gemini Nano による回答: $prompt に対する詳細な応答をここに生成します。"
        val tokens = response.split(" ")
        
        tokens.forEachIndexed { index, token ->
            if (firstTokenTime == 0L) {
                firstTokenTime = System.currentTimeMillis() - startTime
            }
            onToken("$token ")
            kotlinx.coroutines.delay(80) // Simulate faster response than other providers
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
        return """
            以下のテキストを校正してください。誤字脱字、文法の誤り、より自然な表現があれば指摘し、修正案を提示してください。
            
            テキスト:
            $text
            
            校正結果:
        """.trimIndent()
    }
}