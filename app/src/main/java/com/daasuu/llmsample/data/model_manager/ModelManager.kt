package com.daasuu.llmsample.data.model_manager

import android.content.Context
import com.daasuu.llmsample.data.model.DownloadProgress
import com.daasuu.llmsample.data.model.DownloadStatus
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { 
            level = HttpLoggingInterceptor.Level.BASIC 
        })
        .build()
    
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()
    
    // 利用可能なモデルの定義
    private val availableModels = listOf(
        ModelInfo(
            id = "llama2-7b-chat-q4",
            name = "Llama 2 7B Chat Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGML/resolve/main/llama-2-7b-chat.q4_0.bin",
            fileSize = 3825373472L, // ~3.8GB
            description = "Llama 2 7B model quantized to 4-bit for mobile devices"
        ),
        ModelInfo(
            id = "tinyllama-1.1b-q4",
            name = "TinyLlama 1.1B Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGML/resolve/main/tinyllama-1.1b-chat-v1.0.q4_0.bin",
            fileSize = 669769472L, // ~640MB
            description = "Lightweight TinyLlama model for quick testing"
        ),
        ModelInfo(
            id = "phi-2-q4",
            name = "Phi-2 Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/microsoft/phi-2-ggml/resolve/main/phi-2-q4_0.bin",
            fileSize = 1610612736L, // ~1.5GB
            description = "Microsoft Phi-2 model quantized to 4-bit"
        ),
        ModelInfo(
            id = "mobilevlm-q4",
            name = "MobileVLM Q4",
            provider = LLMProvider.LITE_RT,
            downloadUrl = "https://github.com/Meituan-AutoML/MobileVLM/releases/download/v1.0/mobilevlm_q4.tflite",
            fileSize = 536870912L, // ~512MB
            description = "Mobile-optimized Vision-Language model for TensorFlow Lite"
        )
    )
    
    fun getAvailableModels(): List<ModelInfo> {
        return availableModels.map { model ->
            val localFile = getModelFile(model)
            model.copy(
                isDownloaded = localFile.exists(),
                localPath = if (localFile.exists()) localFile.absolutePath else null
            )
        }
    }
    
    fun getModelsByProvider(provider: LLMProvider): List<ModelInfo> {
        return getAvailableModels().filter { it.provider == provider }
    }
    
    fun getDownloadedModels(): List<ModelInfo> {
        return getAvailableModels().filter { it.isDownloaded }
    }
    
    suspend fun downloadModel(modelId: String): Result<String> = withContext(Dispatchers.IO) {
        val model = availableModels.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Model not found: $modelId"))
        
        val localFile = getModelFile(model)
        if (localFile.exists()) {
            return@withContext Result.success(localFile.absolutePath)
        }
        
        updateDownloadProgress(modelId, DownloadStatus.DOWNLOADING, 0f, 0L, model.fileSize)
        
        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    updateDownloadProgress(modelId, DownloadStatus.FAILED, error = "HTTP ${response.code}")
                    return@withContext Result.failure(IOException("Failed to download: HTTP ${response.code}"))
                }
                
                val body = response.body ?: run {
                    updateDownloadProgress(modelId, DownloadStatus.FAILED, error = "Empty response body")
                    return@withContext Result.failure(IOException("Empty response body"))
                }
                
                val contentLength = body.contentLength()
                localFile.parentFile?.mkdirs()
                
                FileOutputStream(localFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var totalBytesRead = 0L
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            val progress = if (contentLength > 0) {
                                totalBytesRead.toFloat() / contentLength.toFloat()
                            } else {
                                0f
                            }
                            
                            updateDownloadProgress(
                                modelId, 
                                DownloadStatus.DOWNLOADING, 
                                progress, 
                                totalBytesRead, 
                                contentLength
                            )
                        }
                    }
                }
                
                updateDownloadProgress(modelId, DownloadStatus.COMPLETED, 1f, contentLength, contentLength)
                Result.success(localFile.absolutePath)
            }
        } catch (e: Exception) {
            updateDownloadProgress(modelId, DownloadStatus.FAILED, error = e.message)
            if (localFile.exists()) {
                localFile.delete()
            }
            Result.failure(e)
        }
    }
    
    suspend fun deleteModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val model = availableModels.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Model not found: $modelId"))
        
        val localFile = getModelFile(model)
        if (localFile.exists()) {
            if (localFile.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Failed to delete model file"))
            }
        } else {
            Result.success(Unit)
        }
    }
    
    fun getModelPath(modelId: String): String? {
        val model = availableModels.find { it.id == modelId } ?: return null
        val localFile = getModelFile(model)
        return if (localFile.exists()) localFile.absolutePath else null
    }
    
    private fun getModelFile(model: ModelInfo): File {
        val modelsDir = File(context.filesDir, "models")
        val providerDir = File(modelsDir, model.provider.name.lowercase())
        val fileName = when (model.provider) {
            LLMProvider.LLAMA_CPP -> "${model.id}.bin"
            LLMProvider.LITE_RT -> "${model.id}.tflite"
            LLMProvider.GEMINI_NANO -> "${model.id}.bin" // Placeholder
        }
        return File(providerDir, fileName)
    }
    
    private fun updateDownloadProgress(
        modelId: String,
        status: DownloadStatus,
        progress: Float = 0f,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        error: String? = null
    ) {
        val currentProgress = _downloadProgress.value.toMutableMap()
        currentProgress[modelId] = DownloadProgress(
            modelId = modelId,
            status = status,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            error = error
        )
        _downloadProgress.value = currentProgress
    }
    
    fun getModelSize(modelId: String): Long {
        val modelPath = getModelPath(modelId) ?: return 0L
        return File(modelPath).length()
    }
}