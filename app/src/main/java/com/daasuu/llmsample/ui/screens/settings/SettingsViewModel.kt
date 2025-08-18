package com.daasuu.llmsample.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val llmManager: LLMManager,
    private val settingsRepository: SettingsRepository,
    private val interferenceMonitor: com.daasuu.llmsample.data.benchmark.InterferenceMonitor
) : ViewModel() {
    
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()
    
    private val _selectedProvider = MutableStateFlow(LLMProvider.LLAMA_CPP)
    val selectedProvider: StateFlow<LLMProvider> = _selectedProvider.asStateFlow()
    
    init {
        // 起動時にassetsからモデルをコピー
        viewModelScope.launch {
            modelManager.copyModelsFromAssets()
            refreshModels()
        }

        refreshModels()

        // 永続化されたプロバイダーを監視し、UIおよび LLM を同期
        viewModelScope.launch {
            settingsRepository.currentProvider.collect { provider ->
                _selectedProvider.value = provider
                llmManager.setCurrentProvider(provider)
            }
        }
    }
    
    fun refreshModels() {
        _models.value = modelManager.getAvailableModels()
    }
    
    fun selectProvider(provider: LLMProvider) {
        // ユーザーアクションを記録
        interferenceMonitor.recordUserAction(
            com.daasuu.llmsample.data.benchmark.UserActionType.PROVIDER_SWITCH
        )
        
        viewModelScope.launch {
            // 永続化のみを行い、反映はフロー監視で一元化
            settingsRepository.setCurrentProvider(provider)
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val result = modelManager.deleteModel(modelId)
            result.onSuccess {
                refreshModels()
            }.onFailure { exception ->
                // Handle deletion failure
                exception.printStackTrace()
            }
        }
    }
}