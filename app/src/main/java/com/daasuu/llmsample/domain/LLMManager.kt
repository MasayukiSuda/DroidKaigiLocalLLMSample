package com.daasuu.llmsample.domain

import android.content.Context
import com.daasuu.llmsample.data.benchmark.PerformanceMonitor
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import com.daasuu.llmsample.data.performance.PerformanceLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
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
     * 現在のプロバイダーを強制的に再初期化する
     * GPU設定変更など、プロバイダー固有の設定が変更された時に使用
     */
    suspend fun reinitializeCurrentProvider() {
        providerSwitchMutex.withLock {
            // 現在のプロバイダーをリリース
            getCurrentRepository()?.release()

            // ネイティブリソースの解放完了を待つ（短時間）
            kotlinx.coroutines.delay(100)

            // 再初期化フラグを設定
            _isInitialized.value = false

            // 次回使用時に再初期化される
            // （必要に応じて即座に初期化も可能）
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

        // 実行開始前のベースラインメモリを記録
        val baselineMemory = performanceMonitor.getCurrentMemoryUsage()
        Timber.d("実行開始前ベースラインメモリ: ${baselineMemory}MB")

        // シンプルなメモリー監視用リスト
        val memoryReadings = mutableListOf<Int>()
        memoryReadings.add(baselineMemory)

        // メモリー監視セッション開始（100ms間隔で監視）
        val memorySession =
            PerformanceMonitor.MemoryMonitoringSession.start(performanceMonitor, 100)

        try {
            originalFlow.collect { token ->
                if (firstTokenTime == 0L) {
                    firstTokenTime = System.currentTimeMillis() - startTime
                    // 最初のトークン生成時のメモリ使用量を明示的に記録
                    val firstTokenMemory = performanceMonitor.getCurrentMemoryUsage()
                    memoryReadings.add(firstTokenMemory)
                    Timber.d("初回トークン生成時メモリ: ${firstTokenMemory}MB")
                }

                // 定期的にメモリーを追加測定（10トークンごと）
                if (tokenCount % 10 == 0) {
                    val currentMemory = performanceMonitor.getCurrentMemoryUsage()
                    memoryReadings.add(currentMemory)
                    Timber.d("トークン ${tokenCount} 時メモリ: ${currentMemory}MB")
                }

                tokenCount++
                outputBuilder.append(token)
                emit(token)
            }

            val endTime = System.currentTimeMillis()
            val totalLatency = endTime - startTime

            // 実行完了直前のメモリを記録
            val preStopMemory = performanceMonitor.getCurrentMemoryUsage()
            memoryReadings.add(preStopMemory)
            Timber.d("実行完了直前メモリ: ${preStopMemory}MB, 実行時間: ${totalLatency}ms")

            // バッテリー使用量計算
            val endBatteryLevel = performanceMonitor.getCurrentBatteryLevel()
            val batteryDrain =
                calculateBatteryDrain(startBatteryLevel, endBatteryLevel, totalLatency)

            // メモリー監視セッション停止
            memorySession.stop()

            // 直接メモリー統計を計算
            val currentMemoryMB = memoryReadings.lastOrNull() ?: 0
            val maxMemorySpikeMB = memoryReadings.maxOrNull() ?: 0
            val averageMemoryUsageMB = if (memoryReadings.isNotEmpty()) {
                memoryReadings.average().toInt()
            } else 0

            // デバッグ情報をログ出力
            Timber.d("メモリー統計 (直接計算): 測定数=${memoryReadings.size}, " +
                        "現在=${currentMemoryMB}MB, " +
                        "最大=${maxMemorySpikeMB}MB, " +
                        "平均=${averageMemoryUsageMB}MB, " +
                        "実行時間=${totalLatency}ms"
            )
            Timber.d("全メモリー測定値: $memoryReadings")

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
                memoryUsageMB = currentMemoryMB,
                maxMemorySpikeMB = maxMemorySpikeMB,
                averageMemoryUsageMB = averageMemoryUsageMB,
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

            // メモリー監視セッション停止
            memorySession.stop()

            // エラー時のメモリー統計を計算
            val errorMemory = performanceMonitor.getCurrentMemoryUsage()
            memoryReadings.add(errorMemory)

            val currentMemoryMB = memoryReadings.lastOrNull() ?: 0
            val maxMemorySpikeMB = memoryReadings.maxOrNull() ?: 0
            val averageMemoryUsageMB = if (memoryReadings.isNotEmpty()) {
                memoryReadings.average().toInt()
            } else 0

            Timber.d("エラー時メモリー統計: 測定数=${memoryReadings.size}, " +
                        "現在=${currentMemoryMB}MB, 最大=${maxMemorySpikeMB}MB, 平均=${averageMemoryUsageMB}MB"
            )

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
                memoryUsageMB = currentMemoryMB,
                maxMemorySpikeMB = maxMemorySpikeMB,
                averageMemoryUsageMB = averageMemoryUsageMB,
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
}