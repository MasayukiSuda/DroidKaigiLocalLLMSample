package com.daasuu.llmsample.data.model_manager

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
            id = "llama-3.2-3b-instruct-q4_k_m",
            name = "Llama 3.2 3B Instruct Q4_K_M",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileSize = 2300000000L, // ~2.3GB (approx)
            description = "Meta Llama 3.2 3B Instruct quantized to 4-bit (Q4_K_M)"
        ),
        ModelInfo(
            id = "llama-3.2-1b-instruct-q4_k_m",
            name = "Llama 3.2 1B Instruct Q4_K_M",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileSize = 934281216L, // ~890MB
            description = "Meta Llama 3.2 1B Instruct - smaller version of Llama 3.2"
        ),
        ModelInfo(
            id = "llama-3.1-8b-instruct-q4_k_m",
            name = "Llama 3.1 8B Instruct Q4_K_M",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            fileSize = 4830000000L, // ~4.5GB (approx)
            description = "Meta Llama 3.1 8B Instruct - larger, more capable model"
        ),
        ModelInfo(
            id = "qwen2.5-3b-instruct-q4_k_m",
            name = "Qwen2.5 3B Instruct Q4_K_M",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-3B-Instruct-GGUF/resolve/main/Qwen2.5-3B-Instruct-Q4_K_M.gguf",
            fileSize = 2100000000L, // ~2GB (approx)
            description = "Alibaba Qwen2.5 3B Instruct - excellent multilingual support"
        ),
        ModelInfo(
            id = "tinyllama-1.1b-q4",
            name = "TinyLlama 1.1B Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_0.gguf",
            fileSize = 669769472L, // ~640MB
            description = "Lightweight TinyLlama model for quick testing"
        ),
        ModelInfo(
            id = "phi-2-q4",
            name = "Phi-2 Q4",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
            fileSize = 1610612736L, // ~1.5GB
            description = "Microsoft Phi-2 model quantized to 4-bit"
        ),
        ModelInfo(
            id = "phi-3.5-mini-instruct-q4_k_m",
            name = "Phi-3.5 Mini Instruct Q4_K_M",
            provider = LLMProvider.LLAMA_CPP,
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            fileSize = 2300000000L, // ~2.2GB (approx)
            description = "Microsoft Phi-3.5 Mini Instruct - improved version of Phi series"
        ),
        // Gemma3 Models (LiteRT/Task format)
        ModelInfo(
            id = "gemma3-2b-task",
            name = "Gemma3 2B (.task)",
            provider = LLMProvider.LITE_RT,
            downloadUrl = "", // Manual placement required
            fileSize = 1500000000L, // ~1.5GB (approx)
            description = "Google Gemma3 2B model in TensorFlow Lite Task format - place manually in assets"
        ),
        ModelInfo(
            id = "gemma3-9b-task",
            name = "Gemma3 9B (.task)",
            provider = LLMProvider.LITE_RT,
            downloadUrl = "", // Manual placement required
            fileSize = 5000000000L, // ~5GB (approx)
            description = "Google Gemma3 9B model in TensorFlow Lite Task format - place manually in assets"
        ),
        ModelInfo(
            id = "gemma3-27b-task",
            name = "Gemma3 27B (.task)",
            provider = LLMProvider.LITE_RT,
            downloadUrl = "", // Manual placement required
            fileSize = 15000000000L, // ~15GB (approx)
            description = "Google Gemma3 27B model in TensorFlow Lite Task format - place manually in assets"
        ),
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
            copyModelsFromAssetsForProvider(assetManager, "models/llama_cpp")

            // lite_rtモデルをコピー
            copyModelsFromAssetsForProvider(assetManager, "models/lite_rt")

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun copyModelsFromAssetsForProvider(
        assetManager: AssetManager,
        assetPath: String,
    ) = withContext(Dispatchers.IO) {
        try {
            val files = assetManager.list(assetPath) ?: return@withContext

            for (fileName in files) {
                val fullAssetPath = "$assetPath/$fileName"

                // モデルIDを特定
                val modelId = when {
                    fileName.contains(
                        "llama-3.2-3b",
                        ignoreCase = true
                    ) -> "llama-3.2-3b-instruct-q4_k_m"

                    fileName.contains(
                        "llama-3.2-1b",
                        ignoreCase = true
                    ) -> "llama-3.2-1b-instruct-q4_k_m"

                    fileName.contains(
                        "llama-3.1-8b",
                        ignoreCase = true
                    ) -> "llama-3.1-8b-instruct-q4_k_m"

                    fileName.contains(
                        "qwen2.5-3b",
                        ignoreCase = true
                    ) -> "qwen2.5-3b-instruct-q4_k_m"

                    fileName.contains("tinyllama", ignoreCase = true) -> "tinyllama-1.1b-q4"
                    fileName.contains("phi-2", ignoreCase = true) -> "phi-2-q4"
                    fileName.contains(
                        "phi-3.5",
                        ignoreCase = true
                    ) -> "phi-3.5-mini-instruct-q4_k_m"


                    fileName.contains("gemma3-2b", ignoreCase = true) -> "gemma3-2b-task"
                    fileName.contains("gemma3-9b", ignoreCase = true) -> "gemma3-9b-task"
                    fileName.contains("gemma3-27b", ignoreCase = true) -> "gemma3-27b-task"
                    fileName.contains(
                        "gemma3",
                        ignoreCase = true
                    ) -> "gemma3-2b-task" // Default to 2B
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