package com.daasuu.llmsample.data.llm.llamacpp

import android.content.Context
import com.daasuu.llmsample.data.model.BenchmarkResult
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import com.daasuu.llmsample.domain.LLMRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class LlamaCppRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LLMRepository {
    
    private var modelPtr: Long = 0L
    private var isInitialized = false
    private var firstTokenTime: Long = 0L
    
    override suspend fun initialize() {
        if (isInitialized) return
        
        withContext(Dispatchers.IO) {
            try {
                // For demo purposes, always initialize successfully
                // In production, check for actual model file
                val modelFile = File(context.filesDir, "llama-model.gguf")
                
                // Load model (will use mock if JNI not available)
                modelPtr = LlamaCppJNI.loadModel(
                    modelPath = if (modelFile.exists()) modelFile.absolutePath else "mock-model",
                    contextSize = 2048,
                    nGpuLayers = 0 // CPU only for now
                )
                isInitialized = modelPtr != 0L
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
            }
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
    
    override suspend fun generateChatResponse(prompt: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: Llama.cpp model not initialized")
            return@flow
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
                        // Emit token in the flow
                        kotlinx.coroutines.runBlocking {
                            emit(token)
                        }
                    }
                    
                    override fun onComplete() {
                        // Generation complete
                    }
                    
                    override fun onError(error: String) {
                        kotlinx.coroutines.runBlocking {
                            emit("\nError: $error")
                        }
                    }
                }
            )
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun summarizeText(text: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: Llama.cpp model not initialized")
            return@flow
        }
        
        val prompt = buildSummarizationPrompt(text)
        generateResponse(prompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun proofreadText(text: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: Llama.cpp model not initialized")
            return@flow
        }
        
        val prompt = buildProofreadingPrompt(text)
        generateResponse(prompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun getBenchmarkResult(taskType: TaskType, input: String): BenchmarkResult {
        val memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val totalTime = measureTimeMillis {
            when (taskType) {
                TaskType.CHAT -> generateChatResponse(input).collect {}
                TaskType.SUMMARIZATION -> summarizeText(input).collect {}
                TaskType.PROOFREADING -> proofreadText(input).collect {}
            }
        }
        
        val memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryUsedMb = (memoryAfter - memoryBefore) / (1024f * 1024f)
        
        return BenchmarkResult(
            provider = LLMProvider.LLAMA_CPP,
            taskType = taskType,
            firstTokenLatencyMs = firstTokenTime,
            totalLatencyMs = totalTime,
            memoryUsageMb = memoryUsedMb,
            modelSizeMb = if (modelPtr != 0L) LlamaCppJNI.getModelSizeMB(modelPtr) else 0f
        )
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
        return """
            |### Human: $userMessage
            |### Assistant: 
        """.trimMargin()
    }
    
    private fun buildSummarizationPrompt(text: String): String {
        return """
            |以下のテキストを簡潔に要約してください:
            |
            |$text
            |
            |要約:
        """.trimMargin()
    }
    
    private fun buildProofreadingPrompt(text: String): String {
        return """
            |以下のテキストの誤字脱字や文法の誤りを指摘し、修正案を提示してください:
            |
            |$text
            |
            |校正結果:
        """.trimMargin()
    }
}