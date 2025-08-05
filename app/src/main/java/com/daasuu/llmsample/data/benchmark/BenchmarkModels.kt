package com.daasuu.llmsample.data.benchmark

import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import java.util.UUID

/**
 * ベンチマークテストケース
 */
data class BenchmarkTestCase(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val taskType: TaskType,
    val inputText: String,
    val expectedOutputLength: Int? = null, // 期待される出力トークン数（推定）
    val category: BenchmarkCategory = BenchmarkCategory.GENERAL
)

/**
 * ベンチマークカテゴリ
 */
enum class BenchmarkCategory(val displayName: String) {
    GENERAL("一般"),
    CODING("コーディング"),
    CONVERSATION("会話"),
    SUMMARIZATION("要約"),
    PROOFREADING("校正"),
    CREATIVE("創作"),
    TECHNICAL("技術文書")
}

/**
 * ベンチマーク結果
 */
data class BenchmarkResult(
    val id: String = UUID.randomUUID().toString(),
    val testCaseId: String,
    val provider: LLMProvider,
    val modelName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    
    // パフォーマンス指標
    val latencyMetrics: LatencyMetrics,
    val memoryMetrics: MemoryMetrics,
    val batteryMetrics: BatteryMetrics,
    val qualityMetrics: QualityMetrics,
    
    // 実行情報
    val deviceInfo: DeviceInfo,
    val executionInfo: ExecutionInfo,
    
    // 出力結果
    val generatedText: String,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

/**
 * レイテンシ指標
 */
data class LatencyMetrics(
    val firstTokenLatency: Long, // 最初のトークンまでの時間 (ms)
    val totalLatency: Long, // 全体の実行時間 (ms)
    val tokensPerSecond: Double, // トークン生成速度 (tokens/sec)
    val totalTokens: Int, // 生成されたトークン数
    val promptTokens: Int, // プロンプトのトークン数
    val averageTokenLatency: Double = if (totalTokens > 0) totalLatency.toDouble() / totalTokens else 0.0
)

/**
 * メモリ指標
 */
data class MemoryMetrics(
    val modelSizeMB: Float, // モデルファイルサイズ (MB)
    val peakMemoryUsageMB: Long, // ピークメモリ使用量 (MB)
    val averageMemoryUsageMB: Long, // 平均メモリ使用量 (MB)
    val memoryIncreaseMB: Long, // 実行前後のメモリ増加量 (MB)
    val availableMemoryMB: Long, // 利用可能メモリ (MB)
    val totalMemoryMB: Long // 総メモリ容量 (MB)
)

/**
 * バッテリー指標
 */
data class BatteryMetrics(
    val batteryLevelBefore: Float, // 実行前のバッテリーレベル (%)
    val batteryLevelAfter: Float, // 実行後のバッテリーレベル (%)
    val batteryDrain: Float, // バッテリー消費量 (%)
    val estimatedBatteryDrainPerHour: Float, // 推定時間当たりバッテリー消費 (%/hour)
    val powerConsumptionMW: Float? = null, // 消費電力 (mW) - 測定可能な場合
    val isCharging: Boolean, // 充電中かどうか
    val batteryTemperature: Float? = null // バッテリー温度 (°C)
)

/**
 * 品質指標
 */
data class QualityMetrics(
    val outputLength: Int, // 出力文字数
    val outputTokens: Int, // 出力トークン数
    val relevanceScore: Float? = null, // 関連性スコア (0.0-1.0)
    val coherenceScore: Float? = null, // 一貫性スコア (0.0-1.0)
    val fluencyScore: Float? = null, // 流暢さスコア (0.0-1.0)
    val completeness: Float? = null, // 完成度 (0.0-1.0)
    val taskAccomplished: Boolean = true // タスクが完了したか
)

/**
 * デバイス情報
 */
data class DeviceInfo(
    val manufacturer: String, // デバイスメーカー
    val model: String, // デバイスモデル
    val androidVersion: String, // Androidバージョン
    val apiLevel: Int, // APIレベル
    val cpuArchitecture: String, // CPUアーキテクチャ
    val totalRamMB: Long, // 総RAM容量 (MB)
    val availableStorageGB: Long, // 利用可能ストレージ (GB)
    val screenDensity: Float, // 画面密度
    val thermalState: String? = null // サーマル状態
)

/**
 * 実行情報
 */
data class ExecutionInfo(
    val startTime: Long, // 実行開始時刻
    val endTime: Long, // 実行終了時刻
    val providerVersion: String? = null, // プロバイダーバージョン
    val modelVersion: String? = null, // モデルバージョン
    val configurationParams: Map<String, Any> = emptyMap(), // 設定パラメータ
    val environmentInfo: Map<String, String> = emptyMap() // 環境情報
)

/**
 * ベンチマーク実行状態
 */
enum class BenchmarkStatus {
    IDLE,      // 待機中
    RUNNING,   // 実行中
    COMPLETED, // 完了
    FAILED,    // 失敗
    CANCELLED  // キャンセル
}

/**
 * ベンチマークセッション
 */
data class BenchmarkSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val testCases: List<BenchmarkTestCase>,
    val providers: List<LLMProvider>,
    val status: BenchmarkStatus = BenchmarkStatus.IDLE,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val results: List<BenchmarkResult> = emptyList(),
    val currentTestIndex: Int = 0,
    val currentProviderIndex: Int = 0
) {
    val isCompleted: Boolean
        get() = status == BenchmarkStatus.COMPLETED
    
    val progress: Float
        get() {
            val totalTests = testCases.size * providers.size
            return if (totalTests > 0) {
                results.size.toFloat() / totalTests
            } else 0f
        }
}

/**
 * ベンチマーク統計
 */
data class BenchmarkStats(
    val totalTests: Int,
    val completedTests: Int,
    val failedTests: Int,
    val averageLatency: Double,
    val averageMemoryUsage: Long,
    val averageBatteryDrain: Float,
    val bestPerformingProvider: LLMProvider?,
    val worstPerformingProvider: LLMProvider?
)