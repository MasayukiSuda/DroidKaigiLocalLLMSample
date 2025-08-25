package com.daasuu.llmsample.data.benchmark

import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import com.daasuu.llmsample.domain.LLMManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ベンチマーク実行管理リポジトリ
 */
@Singleton
class BenchmarkRepository @Inject constructor(
    private val llmManager: LLMManager,
    private val performanceMonitor: PerformanceMonitor,
    private val interferenceMonitor: InterferenceMonitor
) {
    // ユーザー操作を妨げない低優先度スコープ
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentSession = MutableStateFlow<BenchmarkSession?>(null)
    val currentSession: StateFlow<BenchmarkSession?> = _currentSession.asStateFlow()

    private val _results = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val results: StateFlow<List<BenchmarkResult>> = _results.asStateFlow()

    private val _progress = MutableStateFlow(BenchmarkProgress())
    val progress: StateFlow<BenchmarkProgress> = _progress.asStateFlow()

    private var memoryMonitoringJob: kotlinx.coroutines.Job? = null

    /**
     * ベンチマークセッションを開始
     */
    suspend fun startBenchmarkSession(session: BenchmarkSession) {
        if (_currentSession.value?.status == BenchmarkStatus.RUNNING) {
            throw IllegalStateException("Another benchmark session is already running")
        }

        val startedSession = session.copy(
            status = BenchmarkStatus.RUNNING,
            startTime = System.currentTimeMillis(),
            currentTestIndex = 0,
            currentProviderIndex = 0
        )

        _currentSession.value = startedSession
        _results.value = emptyList()
        _progress.value = BenchmarkProgress()

        // 干渉監視を開始
        interferenceMonitor.startBenchmarkMonitoring()

        backgroundScope.launch {
            try {
                runBenchmarkSession(startedSession)
            } catch (e: Exception) {
                _currentSession.value = startedSession.copy(
                    status = BenchmarkStatus.FAILED,
                    endTime = System.currentTimeMillis()
                )
                throw e
            }
        }
    }

    /**
     * ベンチマークセッションを停止
     */
    fun stopBenchmarkSession() {
        android.util.Log.d("BenchmarkRepository", "stopBenchmarkSession() called")

        // 実行中でない場合は何もしない
        val currentSession = _currentSession.value
        android.util.Log.d(
            "BenchmarkRepository",
            "Current session status: ${currentSession?.status}"
        )

        if (currentSession?.status != BenchmarkStatus.RUNNING) {
            android.util.Log.d("BenchmarkRepository", "No running session to stop")
            return
        }

        android.util.Log.d("BenchmarkRepository", "Stopping benchmark session")

        // 進行中のタスクをキャンセル
        backgroundScope.coroutineContext.cancelChildren()
        memoryMonitoringJob?.cancel()

        // セッション状態を更新
        _currentSession.value?.let { session ->
            _currentSession.value = session.copy(
                status = BenchmarkStatus.CANCELLED,
                endTime = System.currentTimeMillis()
            )
            android.util.Log.d("BenchmarkRepository", "Session status updated to CANCELLED")
        }

        // プログレス状態を即座に停止状態に更新
        val currentProgress = _progress.value
        _progress.value = currentProgress.copy(
            isRunning = false,
            isCompleted = false,
            currentTestCase = "停止済み",
            currentProvider = ""
        )
        android.util.Log.d("BenchmarkRepository", "Progress state updated to stopped")

        // ベンチマーク終了処理
        backgroundScope.launch {
            llmManager.finishBenchmark()
            performanceMonitor.clearMonitoringData()
            interferenceMonitor.clearMonitoring()
            android.util.Log.d("BenchmarkRepository", "Cleanup completed")
        }
    }

    /**
     * ベンチマークセッションの実行
     */
    private suspend fun runBenchmarkSession(session: BenchmarkSession) {
        val totalTests = session.testCases.size * session.providers.size
        var completedTests = 0
        val results = mutableListOf<BenchmarkResult>()

        for ((testIndex, testCase) in session.testCases.withIndex()) {
            for ((providerIndex, provider) in session.providers.withIndex()) {
                // 現在の進行状況を更新
                _currentSession.value = session.copy(
                    currentTestIndex = testIndex,
                    currentProviderIndex = providerIndex
                )

                _progress.value = BenchmarkProgress(
                    totalTests = totalTests,
                    completedTests = completedTests,
                    currentTestCase = testCase.name,
                    currentProvider = provider.displayName,
                    isRunning = true
                )

                try {
                    // 個別テストの実行
                    val result = runSingleBenchmark(testCase, provider)
                    results.add(result)
                    _results.value = results.toList()

                    completedTests++

                } catch (e: Exception) {
                    // エラーが発生した場合もカウントアップ
                    val errorResult = createErrorResult(testCase, provider, e)
                    results.add(errorResult)
                    _results.value = results.toList()
                    completedTests++
                }

                // テスト間の間隔を設ける（ユーザー操作を妨げないよう適度な間隔）
                delay(2000) // 2秒間隔に拡大してユーザー操作の優先度を確保
            }
        }

        // セッション完了
        _currentSession.value = session.copy(
            status = BenchmarkStatus.COMPLETED,
            endTime = System.currentTimeMillis(),
            results = results
        )

        _progress.value = BenchmarkProgress(
            totalTests = totalTests,
            completedTests = completedTests,
            isRunning = false,
            isCompleted = true
        )

        // ベンチマーク終了処理
        llmManager.finishBenchmark()
        performanceMonitor.clearMonitoringData()
        interferenceMonitor.clearMonitoring()
    }

    /**
     * 単一ベンチマークテストの実行
     */
    private suspend fun runSingleBenchmark(
        testCase: BenchmarkTestCase,
        provider: LLMProvider
    ): BenchmarkResult {
        android.util.Log.d(
            "BenchmarkRepository",
            "Starting benchmark for provider: ${provider.displayName}"
        )

        // ベースラインメモリを記録
        val memoryBeforeProvider = performanceMonitor.getCurrentMemoryUsage()
        android.util.Log.d(
            "BenchmarkRepository",
            "Memory before provider init: ${memoryBeforeProvider}MB"
        )

        // プロバイダーの準備（ベンチマーク専用メソッドを使用）
        llmManager.setProviderForBenchmark(provider)

        // プロバイダー初期化後のメモリを記録
        val memoryAfterProvider = performanceMonitor.getCurrentMemoryUsage()
        val providerMemoryUsage = memoryAfterProvider - memoryBeforeProvider
        android.util.Log.d(
            "BenchmarkRepository",
            "Memory after provider init: ${memoryAfterProvider}MB (delta: +${providerMemoryUsage}MB)"
        )

        // パフォーマンス監視開始
        performanceMonitor.startMonitoring()

        // メモリ監視の開始（低優先度で実行）
        memoryMonitoringJob?.cancel()
        memoryMonitoringJob = backgroundScope.launch {
            performanceMonitor.monitorMemoryUsage().collectLatest { /* メモリサンプリング */ }
        }

        val startTime = System.currentTimeMillis()
        var firstTokenTime = 0L
        var tokenCount = 0
        val tokens = mutableListOf<String>()

        try {
            // ベンチマーク専用のLLM実行（ユーザー操作による影響も含めて測定）
            val outputFlow = llmManager.generateForBenchmark(testCase.taskType, testCase.inputText)

            val outputBuilder = StringBuilder()
            outputFlow.collect { token ->
                if (firstTokenTime == 0L) {
                    firstTokenTime = System.currentTimeMillis() - startTime
                }
                tokens.add(token)
                outputBuilder.append(token)
                tokenCount++
            }

            val endTime = System.currentTimeMillis()
            val totalLatency = endTime - startTime

            // 干渉統計を取得
            val interferenceStats = interferenceMonitor.getInterferenceStats()

            // パフォーマンス指標の収集（干渉情報も含む）
            val latencyMetrics = LatencyMetrics(
                firstTokenLatency = firstTokenTime,
                totalLatency = totalLatency,
                tokensPerSecond = if (totalLatency > 0) (tokenCount * 1000.0) / totalLatency else 0.0,
                totalTokens = tokenCount,
                promptTokens = estimateTokenCount(testCase.inputText),
                userInterferenceDetected = interferenceStats.hasSignificantInterference,
                baselineDeviation = calculateBaselineDeviation(totalLatency, testCase.taskType),
                concurrentUserActions = interferenceStats.totalUserActions
            )

            val memoryMetrics = MemoryMetrics(
                modelSizeMB = llmManager.getCurrentModelSize(),
                peakMemoryUsageMB = performanceMonitor.getPeakMemoryUsage(),
                averageMemoryUsageMB = performanceMonitor.getAverageMemoryUsage(),
                memoryIncreaseMB = maxOf(
                    providerMemoryUsage,
                    performanceMonitor.getMemoryIncrease()
                ),
                availableMemoryMB = performanceMonitor.getCurrentMemoryUsage(),
                totalMemoryMB = performanceMonitor.getDeviceInfo().totalRamMB
            )

            val batteryMetrics = performanceMonitor.getBatteryInfo()

            val qualityMetrics = QualityMetrics(
                outputLength = outputBuilder.length,
                outputTokens = tokenCount,
                taskAccomplished = outputBuilder.isNotEmpty()
            )

            val deviceInfo = performanceMonitor.getDeviceInfo()

            val executionInfo = ExecutionInfo(
                startTime = startTime,
                endTime = endTime,
                providerVersion = provider.name,
                configurationParams = mapOf(
                    "provider" to provider.name,
                    "taskType" to testCase.taskType.name
                )
            )

            return BenchmarkResult(
                testCaseId = testCase.id,
                provider = provider,
                modelName = llmManager.getCurrentModelName(),
                latencyMetrics = latencyMetrics,
                memoryMetrics = memoryMetrics,
                batteryMetrics = batteryMetrics,
                qualityMetrics = qualityMetrics,
                deviceInfo = deviceInfo,
                executionInfo = executionInfo,
                generatedText = outputBuilder.toString(),
                isSuccess = true
            )

        } finally {
            memoryMonitoringJob?.cancel()
        }
    }

    /**
     * エラー結果の作成
     */
    private fun createErrorResult(
        testCase: BenchmarkTestCase,
        provider: LLMProvider,
        error: Exception
    ): BenchmarkResult {
        val endTime = System.currentTimeMillis()

        return BenchmarkResult(
            testCaseId = testCase.id,
            provider = provider,
            modelName = llmManager.getCurrentModelName(),
            latencyMetrics = LatencyMetrics(0, 0, 0.0, 0, 0),
            memoryMetrics = MemoryMetrics(0f, 0, 0, 0, 0, 0),
            batteryMetrics = performanceMonitor.getBatteryInfo(),
            qualityMetrics = QualityMetrics(0, 0, taskAccomplished = false),
            deviceInfo = performanceMonitor.getDeviceInfo(),
            executionInfo = ExecutionInfo(
                startTime = endTime,
                endTime = endTime,
                providerVersion = provider.name
            ),
            generatedText = "",
            isSuccess = false,
            errorMessage = error.message
        )
    }

    /**
     * トークン数の推定
     */
    private fun estimateTokenCount(text: String): Int {
        // 簡易的なトークン数推定（実際は言語モデルのトークナイザーに依存）
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }

    /**
     * ベースライン性能からの偏差を計算
     */
    private fun calculateBaselineDeviation(actualLatency: Long, taskType: TaskType): Float {
        // タスクタイプ別のベースライン性能（ms）
        val baselineLatency = when (taskType) {
            TaskType.CHAT -> 2000L // 2秒
            TaskType.SUMMARIZATION -> 3000L // 3秒
            TaskType.PROOFREADING -> 2500L // 2.5秒
        }

        return ((actualLatency - baselineLatency).toFloat() / baselineLatency) * 100f
    }

    /**
     * ベンチマーク統計の計算
     */
    fun calculateBenchmarkStats(results: List<BenchmarkResult>): BenchmarkStats {
        val successfulResults = results.filter { it.isSuccess }

        if (successfulResults.isEmpty()) {
            return BenchmarkStats(
                totalTests = results.size,
                completedTests = 0,
                failedTests = results.size,
                averageLatency = 0.0,
                averageMemoryUsage = 0L,
                averageBatteryDrain = 0f,
                bestPerformingProvider = null,
                worstPerformingProvider = null
            )
        }

        val averageLatency = successfulResults.map { it.latencyMetrics.totalLatency }.average()
        val averageMemoryUsage =
            successfulResults.map { it.memoryMetrics.averageMemoryUsageMB }.average().toLong()
        val averageBatteryDrain =
            successfulResults.map { it.batteryMetrics.batteryDrain }.average().toFloat()

        // プロバイダー別の平均レイテンシを計算
        val providerLatencies = successfulResults.groupBy { it.provider }
            .mapValues { (_, results) -> results.map { it.latencyMetrics.totalLatency }.average() }

        val bestProvider = providerLatencies.minByOrNull { it.value }?.key
        val worstProvider = providerLatencies.maxByOrNull { it.value }?.key

        return BenchmarkStats(
            totalTests = results.size,
            completedTests = successfulResults.size,
            failedTests = results.size - successfulResults.size,
            averageLatency = averageLatency,
            averageMemoryUsage = averageMemoryUsage,
            averageBatteryDrain = averageBatteryDrain,
            bestPerformingProvider = bestProvider,
            worstPerformingProvider = worstProvider
        )
    }

    /**
     * プロバイダー別の結果取得
     */
    fun getResultsByProvider(provider: LLMProvider): Flow<List<BenchmarkResult>> = flow {
        results.collect { allResults ->
            emit(allResults.filter { it.provider == provider })
        }
    }

    /**
     * テストケース別の結果取得
     */
    fun getResultsByTestCase(testCaseId: String): Flow<List<BenchmarkResult>> = flow {
        results.collect { allResults ->
            emit(allResults.filter { it.testCaseId == testCaseId })
        }
    }

    /**
     * 結果のクリア
     */
    fun clearResults() {
        // 実行中の場合は先に停止
        if (_currentSession.value?.status == BenchmarkStatus.RUNNING) {
            stopBenchmarkSession()
        }

        _results.value = emptyList()
        _currentSession.value = null
        _progress.value = BenchmarkProgress()

        // 監視データもクリア
        performanceMonitor.clearMonitoringData()
        interferenceMonitor.clearMonitoring()
    }
}

/**
 * ベンチマーク進行状況
 */
data class BenchmarkProgress(
    val totalTests: Int = 0,
    val completedTests: Int = 0,
    val currentTestCase: String = "",
    val currentProvider: String = "",
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val progressPercentage: Float = if (totalTests > 0) (completedTests.toFloat() / totalTests) * 100f else 0f
)