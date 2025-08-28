package com.daasuu.llmsample.data.performance

import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model.TaskType
import java.util.UUID

/**
 * シンプルなパフォーマンス記録
 */
data class PerformanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val provider: LLMProvider,
    val modelName: String?,
    val taskType: TaskType,
    val inputText: String,
    val outputText: String,
    
    // パフォーマンス指標
    val latencyMs: Long, // 総実行時間
    val firstTokenLatencyMs: Long, // 初回トークン時間
    val tokensPerSecond: Double, // スループット
    val totalTokens: Int, // 総トークン数
    val promptTokens: Int, // プロンプトトークン数
    
    // システム情報
    val memoryUsageMB: Int, // メモリ使用量
    val batteryDrain: Float = 0f, // バッテリー消費（推定）
    val deviceInfo: String, // デバイス情報
    
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 記録フィルター
 */
data class PerformanceFilter(
    val provider: LLMProvider? = null,
    val taskType: TaskType? = null,
    val dateRange: DateRange? = null
)

data class DateRange(
    val startTime: Long,
    val endTime: Long
)

/**
 * 統計情報
 */
data class PerformanceStats(
    val totalRecords: Int,
    val averageLatencyMs: Long,
    val averageTokensPerSecond: Double,
    val fastestProvider: LLMProvider?,
    val slowestProvider: LLMProvider?,
    val mostUsedTaskType: TaskType?,
    val successRate: Float
)
