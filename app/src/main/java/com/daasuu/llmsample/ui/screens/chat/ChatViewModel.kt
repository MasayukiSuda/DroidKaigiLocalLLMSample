package com.daasuu.llmsample.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    fun updateInputText(text: String) {
        _inputText.value = text
    }
    
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank()) return
        
        viewModelScope.launch {
            // Add user message
            val userMessage = ChatMessage(
                content = text,
                isUser = true
            )
            _messages.value = _messages.value + userMessage
            _inputText.value = ""
            _isLoading.value = true
            
            // TODO: Generate response from selected LLM
            // For now, just echo back
            val responseMessage = ChatMessage(
                content = "Echo: $text",
                isUser = false
            )
            _messages.value = _messages.value + responseMessage
            _isLoading.value = false
        }
    }
}