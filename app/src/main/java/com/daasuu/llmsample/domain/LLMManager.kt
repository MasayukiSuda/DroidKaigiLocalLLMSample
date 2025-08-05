package com.daasuu.llmsample.domain

import com.daasuu.llmsample.data.model.LLMProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMManager @Inject constructor(
    private val repositories: Map<LLMProvider, @JvmSuppressWildcards LLMRepository>
) {
    
    private val _currentProvider = MutableStateFlow(LLMProvider.LLAMA_CPP)
    val currentProvider: StateFlow<LLMProvider> = _currentProvider.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    suspend fun initialize(provider: LLMProvider) {
        repositories[provider]?.let { repo ->
            repo.initialize()
            _currentProvider.value = provider
            _isInitialized.value = repo.isAvailable()
        }
    }
    
    suspend fun switchProvider(provider: LLMProvider) {
        // Release current provider
        getCurrentRepository()?.release()
        
        // Initialize new provider
        initialize(provider)
    }
    
    suspend fun generateChatResponse(prompt: String): Flow<String> {
        return getCurrentRepository()?.generateChatResponse(prompt) 
            ?: throw IllegalStateException("No repository available for current provider")
    }
    
    suspend fun summarizeText(text: String): Flow<String> {
        return getCurrentRepository()?.summarizeText(text)
            ?: throw IllegalStateException("No repository available for current provider")
    }
    
    suspend fun proofreadText(text: String): Flow<String> {
        return getCurrentRepository()?.proofreadText(text)
            ?: throw IllegalStateException("No repository available for current provider")
    }
    
    
    fun getAvailableProviders(): List<LLMProvider> {
        return repositories.keys.toList()
    }
    
    suspend fun setCurrentProvider(provider: LLMProvider) {
        if (_currentProvider.value != provider) {
            switchProvider(provider)
        }
    }
    
    fun getCurrentModelName(): String? {
        return getCurrentRepository()?.let { repo ->
            "${_currentProvider.value.displayName} Model"
        }
    }
    
    fun getCurrentModelSize(): Float {
        return getCurrentRepository()?.let { repo ->
            // モデルサイズを取得（実装は各リポジトリに依存）
            when (_currentProvider.value) {
                LLMProvider.LLAMA_CPP -> 640f // TinyLlama approximate size
                LLMProvider.LITE_RT -> 512f // TensorFlow Lite model size
                LLMProvider.GEMINI_NANO -> 0f // On-device, no separate download
            }
        } ?: 0f
    }
    
    private fun getCurrentRepository(): LLMRepository? {
        return repositories[_currentProvider.value]
    }
}