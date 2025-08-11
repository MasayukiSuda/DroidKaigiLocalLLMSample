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
            println("Mock LlamaCpp initialized: $isInitialized")
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            modelPtr = 0L
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
        
        // Try the absolute simplest format first
        return "Summarize: $cleanText"
    }
    
    private fun buildProofreadingPrompt(text: String): String {
        // Ultra-simple prompt to avoid tokenization issues
        val cleanText = text.trim().take(500) // Very short limit
        if (cleanText.isEmpty()) {
            return "Hello"
        }
        
        // Try the absolute simplest format first
        return "Check: $cleanText"
    }
}