package com.daasuu.llmsample.data.llm.litert

import android.content.Context
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model_manager.ModelManager
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
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
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
                // Try to find downloaded models for LiteRT
                val downloadedModels = modelManager.getModelsByProvider(LLMProvider.LITE_RT)
                    .filter { it.isDownloaded }
                
                if (downloadedModels.isNotEmpty()) {
                    // Use the first available model
                    val modelPath = downloadedModels.first().localPath!!
                    val modelFile = File(modelPath)
                    
                    val model = loadModelFile(modelFile)
                    modelSize = modelFile.length() / (1024f * 1024f)
                    
                    // Create interpreter with GPU delegate if available
                    val options = Interpreter.Options()
                    
                    // Try GPU delegate first
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        val gpuDelegate = GpuDelegate()
                        options.addDelegate(gpuDelegate)
                    } else {
                        // Fallback to NNAPI
                        try {
                            val nnApiDelegate = NnApiDelegate()
                            options.addDelegate(nnApiDelegate)
                        } catch (e: Exception) {
                            // NNAPI not available, use CPU
                        }
                    }
                    
                    options.setNumThreads(4)
                    interpreter = Interpreter(model, options)
                    
                    // Initialize vocabulary
                    initializeVocabulary()
                    
                    isInitialized = true
                } else {
                    // No models downloaded, initialize with mock for demo
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

        val prompt = inputTokens.joinToString(separator = " ") { idx -> reverseVocabulary[idx] ?: "" }
        if (prompt.contains("JSON only") && prompt.contains("Text:")) {
            // Return single JSON string as token list chunks
            val originalStart = prompt.indexOf("Text:")
            val original = if (originalStart >= 0) prompt.substring(originalStart + 5).trim() else ""
            val corrected = original.replace("テキスト", "文章")
            val startIdx = original.indexOf("テキスト").coerceAtLeast(0)
            val endIdx = (if (startIdx >= 0) startIdx + 3 else 0)
            val json = """
            {"corrected_text":"${corrected}","corrections":[{"original":"テキスト","suggested":"文章","type":"表現","explanation":"より自然な表現です","start":${startIdx},"end":${endIdx}}]}
            """.trimIndent()
            return json.chunked(24)
        }

        val response = "LiteRT による回答: ${reverseVocabulary[inputTokens.lastOrNull() ?: 0]} に関する詳細な説明です。"
        return response.split(" ")
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
        val cleanText = text.trim().take(800)
        if (cleanText.isEmpty()) return "{}"
        return (
            "JSON only. No prose. Japanese proofreading: minimal edits only, preserve meaning/order, no additions. " +
            "Format {\"corrected_text\":string,\"corrections\":[{\"original\":string,\"suggested\":string,\"type\":string,\"explanation\":string,\"start\":number,\"end\":number}]}. " +
            "Text: " + cleanText
        )
    }
}