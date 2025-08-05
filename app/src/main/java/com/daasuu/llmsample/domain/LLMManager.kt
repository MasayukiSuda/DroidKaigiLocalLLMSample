package com.daasuu.llmsample.domain

import com.daasuu.llmsample.data.model.BenchmarkResult
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
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
    
    suspend fun generateChatResponse(prompt: String): Flow<String>? {
        return getCurrentRepository()?.generateChatResponse(prompt)
    }
    
    suspend fun summarizeText(text: String): Flow<String>? {
        return getCurrentRepository()?.summarizeText(text)
    }
    
    suspend fun proofreadText(text: String): Flow<String>? {
        return getCurrentRepository()?.proofreadText(text)
    }
    
    suspend fun getBenchmarkResult(taskType: TaskType, input: String): BenchmarkResult? {
        return getCurrentRepository()?.getBenchmarkResult(taskType, input)
    }
    
    suspend fun getAllBenchmarkResults(taskType: TaskType, input: String): List<BenchmarkResult> {
        val results = mutableListOf<BenchmarkResult>()
        
        repositories.forEach { (provider, repo) ->
            try {
                if (!repo.isAvailable()) {
                    repo.initialize()
                }
                if (repo.isAvailable()) {
                    val result = repo.getBenchmarkResult(taskType, input)
                    results.add(result)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return results
    }
    
    fun getAvailableProviders(): List<LLMProvider> {
        return repositories.keys.toList()
    }
    
    private fun getCurrentRepository(): LLMRepository? {
        return repositories[_currentProvider.value]
    }
}