package com.daasuu.llmsample.domain

import android.app.ActivityManager
import android.content.Context
import com.daasuu.llmsample.data.benchmark.PerformanceMonitor
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import com.daasuu.llmsample.data.performance.PerformanceLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repositories: Map<LLMProvider, @JvmSuppressWildcards LLMRepository>,
    private val performanceLogger: PerformanceLogger,
    private val performanceMonitor: PerformanceMonitor
) {

    private val _currentProvider = MutableStateFlow(LLMProvider.LITE_RT)

    private val _isInitialized = MutableStateFlow(false)

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
        return wrapWithPerformanceLogging(
            TaskType.CHAT,
            prompt,
            getCurrentRepository()?.generateChatResponse(prompt)
                ?: throw IllegalStateException("No repository available for current provider")
        )
    }

    suspend fun summarizeText(text: String): Flow<String> {
        ensureInitialized()
        return wrapWithPerformanceLogging(
            TaskType.SUMMARIZATION,
            text,
            getCurrentRepository()?.summarizeText(text)
                ?: throw IllegalStateException("No repository available for current provider")
        )
    }

    suspend fun proofreadText(text: String): Flow<String> {
        ensureInitialized()
        return wrapWithPerformanceLogging(
            TaskType.PROOFREADING,
            text,
            getCurrentRepository()?.proofreadText(text)
                ?: throw IllegalStateException("No repository available for current provider")
        )
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

    fun getCurrentModelName(): String? {
        return getCurrentRepository()?.let { repo ->
            "${_currentProvider.value.displayName} Model"
        }
    }

    private fun getCurrentRepository(): LLMRepository? {
        return repositories[_currentProvider.value]
    }

    /**
     * パフォーマンス記録機能付きのFlow包装
     */
    private fun wrapWithPerformanceLogging(
        taskType: TaskType,
        inputText: String,
        originalFlow: Flow<String>
    ): Flow<String> = flow {
        val startTime = System.currentTimeMillis()
        var firstTokenTime = 0L
        var tokenCount = 0
        val outputBuilder = StringBuilder()

        // バッテリー監視開始
        val startBatteryLevel = performanceMonitor.getCurrentBatteryLevel()

        try {
            originalFlow.collect { token ->
                if (firstTokenTime == 0L) {
                    firstTokenTime = System.currentTimeMillis() - startTime
                }
                tokenCount++
                outputBuilder.append(token)
                emit(token)
            }

            val endTime = System.currentTimeMillis()
            val totalLatency = endTime - startTime

            // バッテリー使用量計算
            val endBatteryLevel = performanceMonitor.getCurrentBatteryLevel()
            val batteryDrain =
                calculateBatteryDrain(startBatteryLevel, endBatteryLevel, totalLatency)

            // パフォーマンス記録
            performanceLogger.logPerformance(
                provider = _currentProvider.value,
                modelName = getCurrentModelName(),
                taskType = taskType,
                inputText = inputText,
                outputText = outputBuilder.toString(),
                latencyMs = totalLatency,
                firstTokenLatencyMs = firstTokenTime,
                totalTokens = tokenCount,
                promptTokens = estimateTokenCount(inputText),
                memoryUsageMB = getCurrentMemoryUsage(),
                batteryDrain = batteryDrain,
                isSuccess = true
            )

        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val totalLatency = endTime - startTime

            // エラーの場合もバッテリー使用量を記録
            val endBatteryLevel = performanceMonitor.getCurrentBatteryLevel()
            val batteryDrain =
                calculateBatteryDrain(startBatteryLevel, endBatteryLevel, totalLatency)

            // エラーも記録
            performanceLogger.logPerformance(
                provider = _currentProvider.value,
                modelName = getCurrentModelName(),
                taskType = taskType,
                inputText = inputText,
                outputText = "",
                latencyMs = totalLatency,
                firstTokenLatencyMs = firstTokenTime,
                totalTokens = 0,
                promptTokens = estimateTokenCount(inputText),
                memoryUsageMB = getCurrentMemoryUsage(),
                batteryDrain = batteryDrain,
                isSuccess = false,
                errorMessage = e.message
            )
            throw e
        }
    }


    private fun estimateTokenCount(text: String): Int {
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }

    /**
     * バッテリー使用量を計算（%）
     * @param startLevel 開始時のバッテリーレベル (%)
     * @param endLevel 終了時のバッテリーレベル (%)
     * @param durationMs 実行時間 (ms)
     * @return バッテリー消費量（%）と推定消費率
     */
    private fun calculateBatteryDrain(
        startLevel: Float,
        endLevel: Float,
        durationMs: Long
    ): Float {
        val directDrain = startLevel - endLevel

        // 短時間では正確な測定が困難なので、持続時間に基づいて推定
        return if (durationMs < 30000) { // 30秒未満
            // 短時間の場合は推定消費率を使用
            estimateBatteryDrainForShortTask(durationMs)
        } else {
            // 長時間の場合は直接測定値を使用（ただし負の値は0にクリップ）
            maxOf(0f, directDrain)
        }
    }

    /**
     * 短時間タスクのバッテリー消費量推定
     */
    private fun estimateBatteryDrainForShortTask(durationMs: Long): Float {
        // 基本的なCPU使用に基づく推定
        // 実際のプロファイリングデータに基づいて調整可能
        val baseDrainPerMinute = when (_currentProvider.value) {
            LLMProvider.LLAMA_CPP -> 0.3f // ネイティブ処理、比較的重い
            LLMProvider.LITE_RT -> 0.15f // 最適化済み、軽い
            LLMProvider.GEMINI_NANO -> 0.1f // デバイス最適化
        }

        val durationMinutes = durationMs / 60000f
        return baseDrainPerMinute * durationMinutes
    }

    /**
     * プロセス全体のメモリ使用量を取得（ネイティブライブラリを含む）
     */
    private fun getCurrentMemoryUsage(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pid = android.os.Process.myPid()
        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))

        val processMemoryMB = if (memoryInfo.isNotEmpty()) {
            val info = memoryInfo[0]
            // totalPss（Proportional Set Size）を使用 - プロセス全体のメモリ使用量（ネイティブ含む）
            info.totalPss
        } else {
            // フォールバック：JVMヒープメモリのみ
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            (usedMemory / 1024 / 1024).toInt()
        }

        // 可能であれば、現在のプロバイダーのネイティブメモリ使用量も含める
        val nativeMemoryMB = getNativeMemoryUsage()

        // プロセス全体メモリを基本値とし、必要に応じてネイティブメモリを補正
        return if (nativeMemoryMB > 0) {
            // PSS値は既にネイティブメモリを含んでいるので、追加の詳細情報として記録
            maxOf(processMemoryMB, nativeMemoryMB)
        } else {
            processMemoryMB
        }
    }

    /**
     * 現在のプロバイダーのネイティブメモリ使用量を取得
     */
    private fun getNativeMemoryUsage(): Int {
        return when (_currentProvider.value) {
            LLMProvider.LLAMA_CPP -> {
                try {
                    val repository =
                        getCurrentRepository() as? com.daasuu.llmsample.data.llm.llamacpp.LlamaCppRepository
                    repository?.getModelMemoryUsage()?.let { (it / 1024 / 1024).toInt() } ?: 0
                } catch (e: Exception) {
                    0
                }
            }

            else -> 0 // 他のプロバイダーはネイティブメモリ使用量を提供しない
        }
    }
}