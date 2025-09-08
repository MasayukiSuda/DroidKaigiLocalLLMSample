package com.daasuu.llmsample.ui.screens.model_selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            // 設定から現在選択されているモデルを読み込み
            settingsRepository.selectedLlamaModel.collect { modelId ->
                _selectedModel.value = modelId
            }
        }

        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val models = modelManager.getAvailableModels()
                _availableModels.value = models
            } catch (e: Exception) {
                // エラーハンドリング
                _availableModels.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectModel(modelId: String?) {
        viewModelScope.launch {
            settingsRepository.setSelectedLlamaModel(modelId)
        }
    }

    fun refreshModels() {
        loadModels()
    }
}
