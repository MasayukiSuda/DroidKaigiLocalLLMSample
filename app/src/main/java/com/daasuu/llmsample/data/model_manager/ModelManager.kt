package com.daasuu.llmsample.data.model_manager

import kotlinx.coroutines.flow.Flow
import android.content.Context
import android.content.res.AssetManager
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader
) {
    
    // 利用可能なモデルの定義
    private val availableModels = listOf(
        ModelInfo(
            id = "tinyllama-1.1b-q4",
            name = "TinyLlama 1.1B Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_0.gguf",
            fileSize = 669769472L, // ~640MB
            description = "Lightweight TinyLlama model for quick testing"
        ),
        ModelInfo(
            id = "llama2-7b-chat-q4",
            name = "Llama 2 7B Chat Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/Llama-2-7b-Chat-GGUF/resolve/main/llama-2-7b-chat.Q4_0.gguf",
            fileSize = 3825373472L, // ~3.8GB
            description = "Llama 2 7B model quantized to 4-bit for mobile devices"
        ),
        ModelInfo(
            id = "phi-2-q4",
            name = "Phi-2 Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
            fileSize = 1610612736L, // ~1.5GB
            description = "Microsoft Phi-2 model quantized to 4-bit"
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
    
    suspend fun downloadModel(
        modelId: String,
        onProgress: (ModelDownloader.DownloadProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val model = availableModels.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Model not found: $modelId"))
        
        val localFile = getModelFile(model)
        
        // すでに存在する場合はスキップ
        if (localFile.exists()) {
            return@withContext Result.success(Unit)
        }
        
        modelDownloader.downloadModel(
            url = model.downloadUrl,
            destinationFile = localFile,
            onProgress = onProgress
        ).map { Unit }
    }
    
    private fun getModelFile(model: ModelInfo): File {
        val modelsDir = File(context.filesDir, "models")
        val providerDir = File(modelsDir, model.provider.name.lowercase())
        val fileName = when (model.provider) {
            LLMProvider.LLAMA_CPP -> "${model.id}.gguf"
            LLMProvider.LITE_RT -> "${model.id}.tflite"
            LLMProvider.GEMINI_NANO -> "${model.id}.bin" // Placeholder
        }
        return File(providerDir, fileName)
    }

    // Assetsからモデルをコピーする機能
    suspend fun copyModelsFromAssets() = withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            
            // llama_cppモデルをコピー
            copyModelsFromAssetsForProvider(assetManager, "models/llama_cpp", LLMProvider.LLAMA_CPP)
            
            // lite_rtモデルをコピー
            copyModelsFromAssetsForProvider(assetManager, "models/lite_rt", LLMProvider.LITE_RT)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun copyModelsFromAssetsForProvider(
        assetManager: AssetManager,
        assetPath: String,
        provider: LLMProvider
    ) = withContext(Dispatchers.IO) {
        try {
            val files = assetManager.list(assetPath) ?: return@withContext
            
            for (fileName in files) {
                val fullAssetPath = "$assetPath/$fileName"
                
                // モデルIDを特定
                val modelId = when {
                    fileName.contains("tinyllama", ignoreCase = true) -> "tinyllama-1.1b-q4"
                    fileName.contains("llama2-7b", ignoreCase = true) -> "llama2-7b-chat-q4"
                    fileName.contains("phi-2", ignoreCase = true) -> "phi-2-q4"
                    fileName.contains("mobilevlm", ignoreCase = true) -> "mobilevlm-q4"
                    else -> continue
                }
                
                val model = availableModels.find { it.id == modelId } ?: continue
                val targetFile = getModelFile(model)
                
                // ファイルが既に存在する場合はスキップ
                if (targetFile.exists()) continue
                
                // ディレクトリを作成
                targetFile.parentFile?.mkdirs()
                
                // ファイルをコピー
                assetManager.open(fullAssetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            // Assetsにモデルがない場合は無視
        }
    }
}