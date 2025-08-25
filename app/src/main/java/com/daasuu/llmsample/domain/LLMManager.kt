package com.daasuu.llmsample.domain

import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMManager @Inject constructor(
    private val repositories: Map<LLMProvider, @JvmSuppressWildcards LLMRepository>
) {

    private val _currentProvider = MutableStateFlow(LLMProvider.LITE_RT)

    private val _isInitialized = MutableStateFlow(false)

    // ベンチマーク実行中フラグ
    private val _isBenchmarkRunning = MutableStateFlow(false)

    // プロバイダー切り替えの排他制御
    private val providerSwitchMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun initialize(provider: LLMProvider) {
        repositories[provider]?.let { repo ->
            repo.initialize()
            _currentProvider.value = provider
            _isInitialized.value = repo.isAvailable()
        }
    }

    suspend fun generateChatResponse(prompt: String): Flow<String> {
        ensureInitialized()
        return getCurrentRepository()?.generateChatResponse(prompt)
            ?: throw IllegalStateException("No repository available for current provider")
    }

    suspend fun summarizeText(text: String): Flow<String> {
        ensureInitialized()
        return getCurrentRepository()?.summarizeText(text)
            ?: throw IllegalStateException("No repository available for current provider")
    }

    suspend fun proofreadText(text: String): Flow<String> {
        ensureInitialized()
        return getCurrentRepository()?.proofreadText(text)
            ?: throw IllegalStateException("No repository available for current provider")
    }

    suspend fun setCurrentProvider(provider: LLMProvider) {
        providerSwitchMutex.withLock {
            if (_currentProvider.value != provider) {
                // Release current provider
                getCurrentRepository()?.release()

                // プロバイダーを変更（初期化は遅延実行）
                _currentProvider.value = provider
                _isInitialized.value = false
            }
            // 必要に応じて初期化（実際の使用時に遅延実行）
            // initialize(provider) は必要時に実行
        }
    }

    /**
     * 必要時に初期化を実行
     */
    private suspend fun ensureInitialized() {
        if (!_isInitialized.value) {
            initialize(_currentProvider.value)
        }
    }

    /**
     * ベンチマーク専用のプロバイダー設定（低優先度で実行）
     */
    suspend fun setProviderForBenchmark(provider: LLMProvider) {
        // ベンチマーク実行フラグを設定
        _isBenchmarkRunning.value = true

        // ユーザー操作が進行中の場合は少し待機
        providerSwitchMutex.withLock {
            if (_currentProvider.value != provider) {
                // 現在のリポジトリを保持（ユーザー操作用）
                val userRepository = getCurrentRepository()

                // ベンチマーク用のプロバイダーを初期化
                initialize(provider)
            }
        }
    }

    /**
     * ベンチマーク終了時の処理
     */
    fun finishBenchmark() {
        _isBenchmarkRunning.value = false

        // 元のプロバイダーに戻す処理は必要に応じて実装
        // 現在は最後に使用されたプロバイダーのままにしておく
    }

    /**
     * ユーザー操作による影響を考慮したベンチマーク用タスク実行
     */
    suspend fun generateForBenchmark(
        taskType: TaskType,
        input: String
    ): Flow<String> {
        // ベンチマーク実行時は確実に初期化
        ensureInitialized()
        return when (taskType) {
            TaskType.CHAT -> getCurrentRepository()?.generateChatResponse(input)
            TaskType.SUMMARIZATION -> getCurrentRepository()?.summarizeText(input)
            TaskType.PROOFREADING -> getCurrentRepository()?.proofreadText(input)
        } ?: throw IllegalStateException("No repository available for current provider")
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