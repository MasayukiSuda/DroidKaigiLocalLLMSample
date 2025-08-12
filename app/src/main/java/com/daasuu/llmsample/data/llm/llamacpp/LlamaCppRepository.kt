package com.daasuu.llmsample.data.llm.llamacpp

import android.content.Context
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.domain.LLMRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class LlamaCppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) : LLMRepository {
    
    private var modelPtr: Long = 0L
    private var isInitialized = false
    private var firstTokenTime: Long = 0L
    private var isMock: Boolean = false
    
    override suspend fun initialize() {
        if (isInitialized) return
        
        withContext(Dispatchers.IO) {
            try {
                // Try to find downloaded models for llama.cpp
                val downloadedModels = modelManager.getModelsByProvider(LLMProvider.LLAMA_CPP)
                    .filter { it.isDownloaded }
                
                if (downloadedModels.isNotEmpty()) {
                    for (modelInfo in downloadedModels) {
                        val modelPath = modelInfo.localPath!!
                        
                        // Check if model file actually exists and is readable
                        val modelFile = java.io.File(modelPath)
                        if (!modelFile.exists()) {
                            println("Model file does not exist: $modelPath")
                            continue
                        }
                        if (!modelFile.canRead()) {
                            println("Cannot read model file: $modelPath")
                            continue
                        }
                        if (modelFile.length() == 0L) {
                            println("Model file is empty: $modelPath")
                            continue
                        }
                        
                        println("Attempting to initialize LlamaCpp with model: $modelPath (${modelFile.length()} bytes)")
                        
                        modelPtr = LlamaCppJNI.loadModel(
                            modelPath = modelPath,
                            contextSize = 2048,
                            nGpuLayers = 0 // CPU only for now
                        )
                        
                        if (modelPtr != 0L) {
                            isInitialized = true
                            isMock = false
                            println("LlamaCpp model loaded successfully with pointer: $modelPtr")
                            return@withContext
                        } else {
                            println("Failed to load model: $modelPath")
                        }
                    }
                    
                    // If we get here, no models worked
                    println("All downloaded models failed to load, falling back to mock")
                    fallbackToMock()
                } else {
                    println("No downloaded models found, using mock implementation")
                    fallbackToMock()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Exception during LlamaCpp initialization: ${e.message}")
                fallbackToMock()
            }
        }
    }
    
    private fun fallbackToMock() {
        try {
            modelPtr = LlamaCppJNI.loadModel(
                modelPath = "mock-model",
                contextSize = 2048,
                nGpuLayers = 0
            )
            isInitialized = modelPtr != 0L
            isMock = true
            println("Mock LlamaCpp initialized: $isInitialized")
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            modelPtr = 0L
            isMock = false
        }
    }
    
    override suspend fun release() {
        if (modelPtr != 0L) {
            withContext(Dispatchers.IO) {
                LlamaCppJNI.unloadModel(modelPtr)
                modelPtr = 0L
                isInitialized = false
            }
        }
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
        
        withContext(Dispatchers.IO) {
            LlamaCppJNI.generate(
                modelPtr = modelPtr,
                prompt = fullPrompt,
                maxTokens = 512,
                temperature = 0.7f,
                topP = 0.9f,
                callback = object : LlamaCppJNI.GenerationCallback {
                    override fun onToken(token: String) {
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis() - startTime
                        }
                        trySend(token)
                    }
                    
                    override fun onComplete() {
                        // Generation complete
                    }
                    
                    override fun onError(error: String) {
                        trySend(" Error: $error")
                    }
                }
            )
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
            LlamaCppJNI.generate(
                modelPtr = modelPtr,
                prompt = prompt,
                maxTokens = 128,
                temperature = 0.2f,
                topP = 0.9f,
                callback = object : LlamaCppJNI.GenerationCallback {
                    override fun onToken(token: String) {
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis() - startTime
                        }
                        trySend(token)
                    }
                    
                    override fun onComplete() {}
                    override fun onError(error: String) {
                        trySend(" Error: $error")
                    }
                }
            )
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
            LlamaCppJNI.generate(
                modelPtr = modelPtr,
                prompt = prompt,
                maxTokens = 256,
                temperature = 0.2f,
                topP = 0.95f,
                callback = object : LlamaCppJNI.GenerationCallback {
                    override fun onToken(token: String) {
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis() - startTime
                        }
                        trySend(token)
                    }
                    
                    override fun onComplete() {}
                    override fun onError(error: String) {
                        trySend(" Error: $error")
                    }
                }
            )
        }
    }
    

    
    private suspend fun generateResponse(prompt: String, onToken: suspend (String) -> Unit) {
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()
        
        withContext(Dispatchers.IO) {
            LlamaCppJNI.generate(
                modelPtr = modelPtr,
                prompt = prompt,
                maxTokens = 512,
                temperature = 0.7f,
                topP = 0.9f,
                callback = object : LlamaCppJNI.GenerationCallback {
                    override fun onToken(token: String) {
                        if (firstTokenTime == 0L) {
                            firstTokenTime = System.currentTimeMillis() - startTime
                        }
                        kotlinx.coroutines.runBlocking {
                            onToken(token)
                        }
                    }
                    
                    override fun onComplete() {}
                    override fun onError(error: String) {
                        kotlinx.coroutines.runBlocking {
                            onToken("\nError: $error")
                        }
                    }
                }
            )
        }
    }
    
    private fun buildChatPrompt(userMessage: String): String {
        // Ultra-simple prompt to avoid tokenization issues
        val cleanMessage = userMessage.trim().take(500) // Very short limit
        if (cleanMessage.isEmpty()) {
            return "Hello"
        }
        
        // Try the absolute simplest format first
        return cleanMessage
    }
    
    private fun buildSummarizationPrompt(text: String): String {
        // Ultra-simple prompt to avoid tokenization issues
        val cleanText = text.trim().take(500) // Very short limit
        if (cleanText.isEmpty()) {
            return "Hello"
        }

        val isJapanese = containsJapanese(cleanText)
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
        // Keep instruction extremely compact to accommodate tiny models
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