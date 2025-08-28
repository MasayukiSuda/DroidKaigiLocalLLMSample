package com.daasuu.llmsample.ui.screens.proofread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProofreadCorrection(
    val original: String,
    val suggested: String,
    val type: String, // e.g., "誤字", "文法", "表現"
    val explanation: String = "",
    val start: Int? = null,
    val end: Int? = null
)

@HiltViewModel
class ProofreadViewModel @Inject constructor(
    private val llmManager: LLMManager,
    private val settingsRepository: SettingsRepository,
    private val interferenceMonitor: com.daasuu.llmsample.data.benchmark.InterferenceMonitor
) : ViewModel() {
    init {
        // 永続化された選択に基づいて初期化・切替を行う
        viewModelScope.launch {
            settingsRepository.currentProvider.collect { provider ->
                llmManager.setCurrentProvider(provider)
            }
        }
    }

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _corrections = MutableStateFlow<List<ProofreadCorrection>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _correctedText = MutableStateFlow("")
    val correctedText: StateFlow<String> = _correctedText.asStateFlow()

    private val _rawOutput = MutableStateFlow("")

    private var proofreadJob: Job? = null

    fun updateInputText(text: String) {
        _inputText.value = text

        // Cancel previous job and start new one for real-time proofreading
        proofreadJob?.cancel()
        if (text.length > 10) { // Only proofread if text is long enough
            proofreadJob = viewModelScope.launch {
                delay(800) // Debounce
                proofreadInternal()
            }
        } else {
            _corrections.value = emptyList()
            _correctedText.value = ""
            _rawOutput.value = ""
        }
    }

    fun proofread() {
        // ユーザーアクションを記録
        interferenceMonitor.recordUserAction(
            com.daasuu.llmsample.data.benchmark.UserActionType.PROOFREADING
        )

        proofreadJob?.cancel()
        proofreadJob = viewModelScope.launch {
            proofreadInternal()
        }
    }

    private suspend fun proofreadInternal() {
        if (_inputText.value.isBlank()) return

        _isLoading.value = true
        _rawOutput.value = ""

        try {
            val proofreadFlow = llmManager.proofreadText(_inputText.value)
            val resultBuilder = StringBuilder()
            proofreadFlow.collect { token ->
                resultBuilder.append(token)
            }

            val response = resultBuilder.toString().trim()
            println("[PROOFREAD] LLM Final Response: $response")
            _rawOutput.value = response
            if (response.isNotEmpty()) {
                _correctedText.value = response
                _corrections.value = emptyList()
            } else {
                _correctedText.value = ""
                _corrections.value = emptyList()
            }
        } catch (e: Exception) {
            _corrections.value = emptyList()
            _correctedText.value = ""
            _rawOutput.value = ""
        } finally {
            _isLoading.value = false
        }
    }

}