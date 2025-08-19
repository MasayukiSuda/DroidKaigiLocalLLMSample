package com.daasuu.llmsample.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMManager
import com.daasuu.llmsample.data.llm.gemini.GeminiNanoCompatibilityChecker
import com.daasuu.llmsample.data.llm.gemini.DeviceCompatibility
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
    private val interferenceMonitor: com.daasuu.llmsample.data.benchmark.InterferenceMonitor,
    private val geminiNanoCompatibilityChecker: GeminiNanoCompatibilityChecker
) : ViewModel() {
    
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()
    
    private val _selectedProvider = MutableStateFlow(LLMProvider.LITE_RT)
    val selectedProvider: StateFlow<LLMProvider> = _selectedProvider.asStateFlow()
    
    private val _geminiNanoCompatibility = MutableStateFlow<DeviceCompatibility?>(null)
    val geminiNanoCompatibility: StateFlow<DeviceCompatibility?> = _geminiNanoCompatibility.asStateFlow()
    
    private val _availableProviders = MutableStateFlow<List<LLMProvider>>(LLMProvider.entries)
    val availableProviders: StateFlow<List<LLMProvider>> = _availableProviders.asStateFlow()
    
    init {
        // 起動時にassetsからモデルをコピー
        viewModelScope.launch {
            modelManager.copyModelsFromAssets()
            refreshModels()
        }

        refreshModels()
        
        // Gemini Nanoの対応状況を確認
        viewModelScope.launch {
            checkGeminiNanoCompatibility()
        }

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
    
    private suspend fun checkGeminiNanoCompatibility() {
        val compatibility = geminiNanoCompatibilityChecker.isDeviceSupported()
        _geminiNanoCompatibility.value = compatibility
        
        // 非対応の場合は利用可能なプロバイダーリストからGemini Nanoを除外
        val availableProviders = if (compatibility is DeviceCompatibility.Supported) {
            LLMProvider.entries
        } else {
            LLMProvider.entries.filter { it != LLMProvider.GEMINI_NANO }
        }
        _availableProviders.value = availableProviders
        
        // 現在選択されているプロバイダーがGemini Nanoで、かつ非対応端末の場合は別のプロバイダーに切り替え
        if (_selectedProvider.value == LLMProvider.GEMINI_NANO && compatibility !is DeviceCompatibility.Supported) {
            selectProvider(LLMProvider.LITE_RT) // デフォルトに切り替え
        }
    }
    
    fun isProviderAvailable(provider: LLMProvider): Boolean {
        return when (provider) {
            LLMProvider.GEMINI_NANO -> _geminiNanoCompatibility.value is DeviceCompatibility.Supported
            else -> true
        }
    }
    
    fun getProviderUnavailableReason(provider: LLMProvider): String? {
        return when (provider) {
            LLMProvider.GEMINI_NANO -> {
                val compatibility = _geminiNanoCompatibility.value
                if (compatibility !is DeviceCompatibility.Supported) {
                    geminiNanoCompatibilityChecker.getCompatibilityMessage(compatibility ?: DeviceCompatibility.Unknown)
                } else null
            }
            else -> null
        }
    }
}