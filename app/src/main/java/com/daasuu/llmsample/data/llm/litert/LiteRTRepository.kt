package com.daasuu.llmsample.data.llm.litert

import android.content.Context
import com.daasuu.llmsample.data.model.BenchmarkResult
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import com.daasuu.llmsample.domain.LLMRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class LiteRTRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LLMRepository {
    
    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var modelSize: Float = 0f
    private var firstTokenTime: Long = 0L
    
    // Tokenizer and vocabulary (simplified for demo)
    private val vocabulary = mutableMapOf<String, Int>()
    private val reverseVocabulary = mutableMapOf<Int, String>()
    
    override suspend fun initialize() {
        if (isInitialized) return
        
        withContext(Dispatchers.IO) {
            try {
                // For demo purposes, create a mock model
                // In production, you would load an actual TFLite model
                val modelFile = File(context.filesDir, "llm_model.tflite")
                
                if (modelFile.exists()) {
                    val model = loadModelFile(modelFile)
                    modelSize = modelFile.length() / (1024f * 1024f)
                    
                    // Create interpreter with GPU delegate if available
                    val options = Interpreter.Options()
                    
                    // Try GPU delegate first
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val delegateOptions = compatList.bestOptionsForThisDevice
                        val gpuDelegate = GpuDelegate(delegateOptions)
                        options.addDelegate(gpuDelegate)
                    } else {
                        // Fallback to NNAPI
                        val nnApiDelegate = NnApiDelegate()
                        options.addDelegate(nnApiDelegate)
                    }
                    
                    options.setNumThreads(4)
                    interpreter = Interpreter(model, options)
                    
                    // Initialize vocabulary (mock)
                    initializeVocabulary()
                    
                    isInitialized = true
                } else {
                    // Mock initialization for demo
                    isInitialized = true
                    modelSize = 512f // Mock 512MB model
                    initializeVocabulary()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
            }
        }
    }
    
    override suspend fun release() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
    
    override suspend fun isAvailable(): Boolean = isInitialized
    
    override suspend fun generateChatResponse(prompt: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: LiteRT model not initialized")
            return@flow
        }
        
        val fullPrompt = buildChatPrompt(prompt)
        generateResponse(fullPrompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun summarizeText(text: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: LiteRT model not initialized")
            return@flow
        }
        
        val prompt = buildSummarizationPrompt(text)
        generateResponse(prompt) { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun proofreadText(text: String): Flow<String> = flow {
        if (!isInitialized) {
            emit("Error: LiteRT model not initialized")
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
            provider = LLMProvider.LITE_RT,
            taskType = taskType,
            firstTokenLatencyMs = firstTokenTime,
            totalLatencyMs = totalTime,
            memoryUsageMb = memoryUsedMb,
            modelSizeMb = modelSize
        )
    }
    
    private suspend fun generateResponse(prompt: String, onToken: suspend (String) -> Unit) {
        firstTokenTime = 0L
        val startTime = System.currentTimeMillis()
        
        withContext(Dispatchers.IO) {
            // Mock generation using TensorFlow Lite
            // In production, tokenize input, run inference, and decode output
            
            val tokens = mockTokenize(prompt)
            val generatedTokens = mockInference(tokens)
            
            generatedTokens.forEachIndexed { index, token ->
                if (firstTokenTime == 0L) {
                    firstTokenTime = System.currentTimeMillis() - startTime
                }
                onToken(token)
                delay(50) // Simulate processing time
            }
        }
    }
    
    private fun loadModelFile(modelFile: File): MappedByteBuffer {
        val inputStream = FileInputStream(modelFile)
        val fileChannel = inputStream.channel
        val startOffset = 0L
        val declaredLength = fileChannel.size()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    private fun initializeVocabulary() {
        // Mock vocabulary initialization
        val commonWords = listOf(
            "<pad>", "<unk>", "<s>", "</s>", "the", "a", "an", "and", "or", "but",
            "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were",
            "I", "you", "he", "she", "it", "we", "they", "this", "that", "these", "those",
            "hello", "world", "good", "bad", "yes", "no", "please", "thank", "you", "sorry"
        )
        
        commonWords.forEachIndexed { index, word ->
            vocabulary[word] = index
            reverseVocabulary[index] = word
        }
    }
    
    private fun mockTokenize(text: String): IntArray {
        // Simple whitespace tokenization
        val words = text.lowercase().split("\\s+".toRegex())
        return words.map { word ->
            vocabulary[word] ?: vocabulary["<unk>"] ?: 1
        }.toIntArray()
    }
    
    private suspend fun mockInference(inputTokens: IntArray): List<String> {
        // Mock inference - in production, this would use the actual TFLite interpreter
        delay(100) // Simulate model inference time
        
        val response = "LiteRT による回答: ${reverseVocabulary[inputTokens.lastOrNull() ?: 0]} に関する詳細な説明です。"
        return response.split(" ")
    }
    
    private fun buildChatPrompt(userMessage: String): String {
        return "User: $userMessage\nAssistant: "
    }
    
    private fun buildSummarizationPrompt(text: String): String {
        return "以下のテキストを要約してください:\n\n$text\n\n要約:"
    }
    
    private fun buildProofreadingPrompt(text: String): String {
        return "以下のテキストを校正してください:\n\n$text\n\n校正結果:"
    }
}