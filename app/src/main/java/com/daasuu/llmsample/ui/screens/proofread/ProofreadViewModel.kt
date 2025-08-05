package com.daasuu.llmsample.ui.screens.proofread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val explanation: String = ""
)

@HiltViewModel
class ProofreadViewModel @Inject constructor(
    private val llmManager: LLMManager
) : ViewModel() {
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    private val _corrections = MutableStateFlow<List<ProofreadCorrection>>(emptyList())
    val corrections: StateFlow<List<ProofreadCorrection>> = _corrections.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
        }
    }
    
    fun proofread() {
        proofreadJob?.cancel()
        proofreadJob = viewModelScope.launch {
            proofreadInternal()
        }
    }
    
    private suspend fun proofreadInternal() {
        if (_inputText.value.isBlank()) return
        
        _isLoading.value = true
        
        try {
            val proofreadFlow = llmManager.proofreadText(_inputText.value)
            if (proofreadFlow != null) {
                val resultBuilder = StringBuilder()
                proofreadFlow.collect { token ->
                    resultBuilder.append(token)
                }
                
                // Parse the result to extract corrections
                // For now, create mock corrections
                _corrections.value = listOf(
                    ProofreadCorrection(
                        original = "テキスト",
                        suggested = "文章",
                        type = "表現",
                        explanation = "より自然な表現です"
                    )
                )
            } else {
                _corrections.value = emptyList()
            }
        } catch (e: Exception) {
            _corrections.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }
}