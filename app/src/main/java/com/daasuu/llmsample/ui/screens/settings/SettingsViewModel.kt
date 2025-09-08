package com.daasuu.llmsample.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.llm.gemini.DeviceCompatibility
import com.daasuu.llmsample.data.llm.gemini.GeminiNanoCompatibilityChecker
import com.daasuu.llmsample.data.model.LLMProvider
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
    private val interferenceMonitor: com.daasuu.llmsample.data.benchmark.InterferenceMonitor,
    private val geminiNanoCompatibilityChecker: GeminiNanoCompatibilityChecker
) : ViewModel() {

    private val _selectedProvider = MutableStateFlow(LLMProvider.LITE_RT)
    val selectedProvider: StateFlow<LLMProvider> = _selectedProvider.asStateFlow()

    private val _geminiNanoCompatibility = MutableStateFlow<DeviceCompatibility?>(null)

    private val _availableProviders = MutableStateFlow<List<LLMProvider>>(LLMProvider.entries)

    private val _isGpuEnabled = MutableStateFlow(false)
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled.asStateFlow()

    init {
        // 起動時にassetsからモデルをコピー
        viewModelScope.launch {
            modelManager.copyModelsFromAssets()
        }

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

        // GPU設定を監視し、変更時にLITE_RTプロバイダーを再初期化
        viewModelScope.launch {
            var isFirst = true
            settingsRepository.isGpuEnabled.collect { enabled ->
                val previousValue = _isGpuEnabled.value
                _isGpuEnabled.value = enabled

                // 初回以外で、かつGPU設定が実際に変更され、現在LITE_RTが選択されている場合に再初期化
                if (!isFirst && previousValue != enabled && _selectedProvider.value == LLMProvider.LITE_RT) {
                    android.util.Log.d(
                        "SettingsViewModel",
                        "GPU setting changed from $previousValue to $enabled, reinitializing TaskRepository..."
                    )
                    try {
                        llmManager.reinitializeCurrentProvider()
                        android.util.Log.d(
                            "SettingsViewModel",
                            "TaskRepository successfully reinitialized with GPU setting: $enabled"
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "SettingsViewModel",
                            "Critical error during TaskRepository reinitialization",
                            e
                        )
                        // ユーザーに問題を通知することも検討
                        // 必要に応じて設定をロールバックする処理も追加可能
                    }
                }
                isFirst = false
            }
        }

        // Llamaモデル選択を監視し、変更時にLLAMA_CPPプロバイダーを再初期化
        viewModelScope.launch {
            var isFirst = true
            settingsRepository.selectedLlamaModel.collect { selectedModelId ->
                // 初回以外で、現在LLAMA_CPPが選択されている場合に再初期化
                if (!isFirst && _selectedProvider.value == LLMProvider.LLAMA_CPP) {
                    android.util.Log.d(
                        "SettingsViewModel",
                        "Llama model selection changed to: $selectedModelId, reinitializing LlamaCppRepository..."
                    )
                    try {
                        llmManager.reinitializeCurrentProvider()
                        android.util.Log.d(
                            "SettingsViewModel",
                            "LlamaCppRepository successfully reinitialized with selected model: $selectedModelId"
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "SettingsViewModel",
                            "Critical error during LlamaCppRepository reinitialization",
                            e
                        )
                        // ユーザーに問題を通知することも検討
                    }
                }
                isFirst = false
            }
        }
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

    private fun checkGeminiNanoCompatibility() {
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
                    geminiNanoCompatibilityChecker.getCompatibilityMessage(
                        compatibility ?: DeviceCompatibility.Unknown
                    )
                } else null
            }

            else -> null
        }
    }

    fun setGpuEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGpuEnabled(enabled)
        }
    }
}