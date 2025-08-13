package com.daasuu.llmsample.data.llm.litert

import android.content.Context
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.domain.LLMRepository
// GPU delegate factory is provided by the GPU artifact; guard import by reflection at use site if unresolved
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRTRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) : LLMRepository {
    
    private var interpreter: InterpreterApi? = null
    private var tokenizer: Gpt2Tokenizer? = null
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
                    // Use the first available model (assumed GPT-2 .tflite in future; currently MobileNet for pipe check)
                    val modelPath = downloadedModels.first().localPath!!
                    val modelFile = File(modelPath)

                    val model = loadModelFile(modelFile)
                    modelSize = modelFile.length() / (1024f * 1024f)

                    // Create interpreter via Play services LiteRT API
                    val options = Options()
                        .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                        .setNumThreads(4)
                    try {
                        val clazz = Class.forName("com.google.android.gms.tflite.gpu.GpuDelegateFactory")
                        val factory = clazz.getDeclaredConstructor().newInstance()
                        val addMethod = Options::class.java.getMethod("addDelegateFactory", Class.forName("com.google.android.gms.tflite.java.DelegateFactory"))
                        addMethod.invoke(options, factory)
                    } catch (_: Throwable) {
                        // GPU factory not available; continue with CPU
                    }
                    interpreter = InterpreterApi.create(model, options)

                    // Try to locate tokenizer files next to model
                    val tokDir = modelFile.parentFile ?: context.filesDir
                    tokenizer = Gpt2Tokenizer(tokDir)

                    isInitialized = true
                } else {
                    // No models downloaded, initialize with mock for demo
                    isInitialized = true
                    modelSize = 512f // Mock 512MB model
                    initializeVocabulary() // legacy mock
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // フォールバック: 失敗時もモックで利用可能にする
                isInitialized = true
                modelSize = 512f
                initializeVocabulary()
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
            initialize()
        }
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
            initialize()
        }
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
            initialize()
        }
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
            val interp = interpreter
            val tok = tokenizer
            if (interp == null || tok == null) {
                // fallback to mock if tokenizer/interpreter is not ready
                val tokens = mockTokenize(prompt)
                val generatedTokens = mockInference(tokens)
                generatedTokens.forEach { token ->
                    if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis() - startTime
                    onToken(token)
                    delay(50)
                }
                return@withContext
            }

            // Minimal greedy decode loop stub (model-specific I/O must be adjusted for real GPT-2 TFLite)
            val maxNewTokens = 64
            val inputIds = tok.encode(prompt, maxTokens = 128)

            // Allocate simple I/O buffers (placeholders; real tensor shapes depend on converted model)
            // Example assumes [1, seq] int32 input and returns next token logits [1, vocab]
            var lastToken = inputIds.lastOrNull() ?: 0
            val sb = StringBuilder()
            for (step in 0 until maxNewTokens) {
                // This is a placeholder; actual invocation requires proper inputs & outputs per model signature
                try {
                    // Example shape: feed last token only (next-token prediction)
                    val input = intArrayOf(1, 1)
                    val inputBuffer = java.nio.ByteBuffer.allocateDirect(4 * 1).order(java.nio.ByteOrder.nativeOrder())
                    inputBuffer.putInt(lastToken)

                    val outputs: MutableMap<Int, Any> = HashMap()
                    val logits = java.nio.ByteBuffer.allocateDirect(4 * 50257).order(java.nio.ByteOrder.nativeOrder())
                    outputs[0] = logits

                    interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

                    // Greedy: argmax over vocab
                    logits.rewind()
                    var bestId = 0
                    var bestVal = Float.NEGATIVE_INFINITY
                    for (i in 0 until 50257) {
                        val v = logits.float
                        if (v > bestVal) {
                            bestVal = v
                            bestId = i
                        }
                    }
                    lastToken = bestId
                    val piece = tok.decodeSingle(bestId)
                    if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis() - startTime
                    onToken(piece)
                    sb.append(piece)
                } catch (e: Exception) {
                    // On any mismatch, fallback to mock continuation but keep streamed text
                    val tokens = mockTokenize(prompt)
                    val cont = mockInference(tokens)
                    for (t in cont) {
                        if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis() - startTime
                        onToken(t)
                        delay(40)
                    }
                    break
                }
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