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
import org.json.JSONObject
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
    val corrections: StateFlow<List<ProofreadCorrection>> = _corrections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _correctedText = MutableStateFlow("")
    val correctedText: StateFlow<String> = _correctedText.asStateFlow()

    private val _rawOutput = MutableStateFlow("")
    val rawOutput: StateFlow<String> = _rawOutput.asStateFlow()

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
            if (proofreadFlow != null) {
                val resultBuilder = StringBuilder()
                proofreadFlow.collect { token ->
                    resultBuilder.append(token)
                }

                val response = resultBuilder.toString().trim()
                println("Proofread response: $response")
                _rawOutput.value = response
                val parsed = parseProofreadResponse(response, _inputText.value)
                if (parsed != null) {
                    _correctedText.value = parsed.first
                    _corrections.value = emptyList()
                } else {
                    _correctedText.value = ""
                    _corrections.value = emptyList()
                }
            } else {
                _corrections.value = emptyList()
                _correctedText.value = ""
                _rawOutput.value = ""
            }
        } catch (e: Exception) {
            _corrections.value = emptyList()
            _correctedText.value = ""
            _rawOutput.value = ""
        } finally {
            _isLoading.value = false
        }
    }

    private fun parseProofreadResponse(
        response: String,
        original: String
    ): Pair<String, List<ProofreadCorrection>>? {
        // Try to locate a JSON object in the response
        val startIdx = response.indexOf('{')
        val endIdx = response.lastIndexOf('}')
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return null
        val jsonString = response.substring(startIdx, endIdx + 1)
        return try {
            val root = JSONObject(jsonString)

            // Accept both corrected_text and correctedText
            val correctedFromJson = when {
                root.has("corrected_text") -> root.optString("corrected_text", "")
                root.has("correctedText") -> root.optString("correctedText", "")
                else -> ""
            }.trim()

            if (correctedFromJson.isNotEmpty()) {
                // correctionsは表示不要なので常に空にする
                correctedFromJson to emptyList()
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
}