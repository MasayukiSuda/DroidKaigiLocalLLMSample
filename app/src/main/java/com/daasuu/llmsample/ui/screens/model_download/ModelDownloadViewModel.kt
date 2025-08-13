package com.daasuu.llmsample.ui.screens.model_download

import com.daasuu.llmsample.data.model.LLMProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
        // 全プロバイダーのモデルを表示（LiteRTもダウンロード可能に）
        _models.value = modelManager.getAvailableModels()
    }
    
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelManager.downloadModel(
                modelId = modelId,
                onProgress = { progress ->
                    _downloadingModels.value = _downloadingModels.value + (modelId to progress.progress)
                }
            ).fold(
                onSuccess = {
                    _downloadingModels.value = _downloadingModels.value - modelId
                    loadModels()
                },
                onFailure = { error ->
                    _downloadingModels.value = _downloadingModels.value - modelId
                    // エラー処理
                }
            )
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager.deleteModel(modelId).fold(
                onSuccess = {
                    loadModels()
                },
                onFailure = { error ->
                    // エラー処理
                }
            )
        }
    }
}