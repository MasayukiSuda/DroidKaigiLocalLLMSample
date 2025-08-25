package com.daasuu.llmsample.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.data.model.ChatMessage
import com.daasuu.llmsample.data.settings.SettingsRepository
import com.daasuu.llmsample.domain.LLMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmManager: LLMManager,
    private val settingsRepository: SettingsRepository,
    private val interferenceMonitor: com.daasuu.llmsample.data.benchmark.InterferenceMonitor
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    init {
        // 永続化された選択に基づいて初期化・切替を行う
        viewModelScope.launch {
            settingsRepository.currentProvider.collect { provider ->
                llmManager.setCurrentProvider(provider)
            }
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank()) return

        // ユーザーアクションを記録
        interferenceMonitor.recordUserAction(
            com.daasuu.llmsample.data.benchmark.UserActionType.CHAT_MESSAGE
        )

        viewModelScope.launch {
            // Add user message
            val userMessage = ChatMessage(
                content = text,
                isUser = true
            )
            _messages.value = _messages.value + userMessage
            _inputText.value = ""
            _isLoading.value = true

            try {
                // Generate response from selected LLM
                val responseFlow = llmManager.generateChatResponse(text)
                val responseBuilder = StringBuilder()
                responseFlow.collect { token ->
                    responseBuilder.append(token)
                    // Update the last message with accumulated response
                    val currentMessages = _messages.value.toMutableList()
                    if (currentMessages.isNotEmpty() && !currentMessages.last().isUser) {
                        // Update existing response message
                        currentMessages[currentMessages.size - 1] = currentMessages.last().copy(
                            content = responseBuilder.toString()
                        )
                    } else {
                        // Add new response message
                        currentMessages.add(
                            ChatMessage(
                                content = responseBuilder.toString(),
                                isUser = false
                            )
                        )
                    }
                    _messages.value = currentMessages
                }
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Error: ${e.message}",
                    isUser = false
                )
                println("errorMessage = ${errorMessage.content}")
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
}