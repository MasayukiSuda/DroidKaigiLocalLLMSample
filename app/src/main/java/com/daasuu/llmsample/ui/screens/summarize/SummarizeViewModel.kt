package com.daasuu.llmsample.ui.screens.summarize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummarizeViewModel @Inject constructor() : ViewModel() {
    
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
        summarizeJob?.cancel()
        summarizeJob = viewModelScope.launch {
            summarizeInternal()
        }
    }
    
    private suspend fun summarizeInternal() {
        if (_inputText.value.isBlank()) return
        
        _isLoading.value = true
        
        // TODO: Call selected LLM for summarization
        // For now, just return a simple summary
        delay(1000) // Simulate processing
        _summaryText.value = "要約: ${_inputText.value.take(100)}..."
        
        _isLoading.value = false
    }
    
    fun clearAll() {
        _inputText.value = ""
        _summaryText.value = ""
        summarizeJob?.cancel()
    }
}