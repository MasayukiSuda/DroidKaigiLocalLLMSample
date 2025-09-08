package com.daasuu.llmsample.data.performance

import android.content.Context
import android.os.Build
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _records = MutableStateFlow<List<PerformanceRecord>>(emptyList())
    val records: StateFlow<List<PerformanceRecord>> = _records.asStateFlow()

    /**
     * パフォーマンス記録を追加
     */
    fun logPerformance(
        provider: LLMProvider,
        modelName: String?,
        taskType: TaskType,
        inputText: String,
        outputText: String,
        latencyMs: Long,
        firstTokenLatencyMs: Long,
        totalTokens: Int,
        promptTokens: Int,
        memoryUsageMB: Int,
        maxMemorySpikeMB: Int,
        averageMemoryUsageMB: Int,
        batteryDrain: Float = 0f,
        isSuccess: Boolean = true,
        errorMessage: String? = null
    ) {
        // 追加デバッグ：受け取った値を確認
        Timber.d("記録する値: memoryUsage=${memoryUsageMB}MB, " +
            "maxSpike=${maxMemorySpikeMB}MB, average=${averageMemoryUsageMB}MB")

        val record = PerformanceRecord(
            provider = provider,
            modelName = modelName,
            taskType = taskType,
            inputText = inputText, // 入力テキスト全文を記録
            outputText = outputText, // 出力テキスト全文を記録

            latencyMs = latencyMs,
            firstTokenLatencyMs = firstTokenLatencyMs,
            tokensPerSecond = if (latencyMs > 0) (totalTokens * 1000.0) / latencyMs else 0.0,
            totalTokens = totalTokens,
            promptTokens = promptTokens,
            memoryUsageMB = memoryUsageMB,
            maxMemorySpikeMB = maxMemorySpikeMB,
            averageMemoryUsageMB = averageMemoryUsageMB,
            batteryDrain = batteryDrain,
            deviceInfo = getDeviceInfo(),
            isSuccess = isSuccess,
            errorMessage = errorMessage
        )

        val currentRecords = _records.value.toMutableList()
        currentRecords.add(0, record) // 新しい記録を先頭に追加

        // 最大1000件まで保持
        if (currentRecords.size > 1000) {
            currentRecords.removeAt(currentRecords.size - 1)
        }

        _records.value = currentRecords
    }

    /**
     * フィルターされた記録を取得
     */
    fun getFilteredRecords(filter: PerformanceFilter): List<PerformanceRecord> {
        return _records.value.filter { record ->
            (filter.provider == null || record.provider == filter.provider) &&
            (filter.taskType == null || record.taskType == filter.taskType) &&
            (filter.dateRange == null || (record.timestamp >= filter.dateRange.startTime && record.timestamp <= filter.dateRange.endTime))
        }
    }

    /**
     * 統計情報を取得
     */
    fun getStats(filter: PerformanceFilter = PerformanceFilter()): PerformanceStats {
        val filteredRecords = getFilteredRecords(filter)
        
        if (filteredRecords.isEmpty()) {
            return PerformanceStats(
                totalRecords = 0,
                averageLatencyMs = 0,
                averageTokensPerSecond = 0.0,
                fastestProvider = null,
                slowestProvider = null,
                mostUsedTaskType = null,
                successRate = 0f
            )
        }

        val successfulRecords = filteredRecords.filter { it.isSuccess }
        val averageLatency = successfulRecords.map { it.latencyMs }.average().toLong()
        val averageTokensPerSecond = successfulRecords.map { it.tokensPerSecond }.average()

        // プロバイダー別平均レイテンシ
        val providerLatencies = successfulRecords
            .groupBy { it.provider }
            .mapValues { (_, records) -> records.map { it.latencyMs }.average() }

        val fastestProvider = providerLatencies.minByOrNull { it.value }?.key
        val slowestProvider = providerLatencies.maxByOrNull { it.value }?.key

        // 最も使用されたタスクタイプ
        val mostUsedTaskType = filteredRecords
            .groupBy { it.taskType }
            .maxByOrNull { it.value.size }?.key

        val successRate = if (filteredRecords.isNotEmpty()) {
            successfulRecords.size.toFloat() / filteredRecords.size
        } else 0f

        return PerformanceStats(
            totalRecords = filteredRecords.size,
            averageLatencyMs = averageLatency,
            averageTokensPerSecond = averageTokensPerSecond,
            fastestProvider = fastestProvider,
            slowestProvider = slowestProvider,
            mostUsedTaskType = mostUsedTaskType,
            successRate = successRate
        )
    }

    /**
     * 記録をクリア
     */
    fun clearRecords() {
        _records.value = emptyList()
    }

    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}
