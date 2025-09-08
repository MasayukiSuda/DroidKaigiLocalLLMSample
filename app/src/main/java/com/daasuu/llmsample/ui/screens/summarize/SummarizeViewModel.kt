package com.daasuu.llmsample.ui.screens.summarize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.benchmark.InterferenceMonitor
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import timber.log.Timber
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummarizeViewModel @Inject constructor(
    private val llmManager: LLMManager,
    private val settingsRepository: SettingsRepository,
    private val interferenceMonitor: InterferenceMonitor
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

    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var summarizeJob: Job? = null

    fun updateInputText(text: String) {
        _inputText.value = text

        // Cancel previous job and start new one for real-time summarization
        summarizeJob?.cancel()
        if (text.length > 50) { // Only summarize if text is long enough
            summarizeJob = viewModelScope.launch {
                delay(500) // Debounce
                summarizeInternal()
            }
        } else {
            _summaryText.value = ""
        }
    }

    fun summarize() {
        // ユーザーアクションを記録
        interferenceMonitor.recordUserAction(
            com.daasuu.llmsample.data.benchmark.UserActionType.SUMMARIZATION
        )

        summarizeJob?.cancel()
        summarizeJob = viewModelScope.launch {
            summarizeInternal()
        }
    }

    private suspend fun summarizeInternal() {
        if (_inputText.value.isBlank()) return

        _isLoading.value = true

        try {
            val summaryFlow = llmManager.summarizeText(_inputText.value)
            val summaryBuilder = StringBuilder()
            summaryFlow.collect { token ->
                summaryBuilder.append(token)
                _summaryText.value = summaryBuilder.toString()
            }
            Timber.d("[SUMMARIZE] LLM Final Summary: ${summaryBuilder.toString()}")
        } catch (e: Exception) {
            _summaryText.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun clearAll() {
        _inputText.value = ""
        _summaryText.value = ""
        summarizeJob?.cancel()
    }
}