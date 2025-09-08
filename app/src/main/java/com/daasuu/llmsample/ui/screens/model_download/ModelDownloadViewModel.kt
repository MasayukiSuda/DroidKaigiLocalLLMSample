package com.daasuu.llmsample.ui.screens.model_download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _downloadingModels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingModels: StateFlow<Map<String, Float>> = _downloadingModels.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        // llama.cpp など既存は維持。
        _models.value =
            modelManager.getAvailableModels()
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            Timber.d("Starting download for model: $modelId")
            modelManager.downloadModel(
                modelId = modelId,
                onProgress = { progress ->
                    _downloadingModels.value =
                        _downloadingModels.value + (modelId to progress.progress)
                }
            ).fold(
                onSuccess = {
                    Timber.d("Download completed for model: $modelId")
                    _downloadingModels.value = _downloadingModels.value - modelId
                    loadModels()
                },
                onFailure = { error ->
                    Timber.e(error, "Download failed for model: $modelId")
                    _downloadingModels.value = _downloadingModels.value - modelId
                    when (error) {
                        is IllegalStateException -> {
                            // 手動配置が必要なモデルの場合
                            Timber.w("Manual placement required for model: $modelId")
                        }

                        else -> {
                            Timber.e("Unexpected error during download: ${error.message}")
                        }
                    }
                }
            )
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            Timber.d("Deleting model: $modelId")
            modelManager.deleteModel(modelId).fold(
                onSuccess = {
                    Timber.d("Model deleted successfully: $modelId")
                    loadModels()
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to delete model: $modelId")
                }
            )
        }
    }
}