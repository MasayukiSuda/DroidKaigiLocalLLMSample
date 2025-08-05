package com.daasuu.llmsample.data.model

data class BenchmarkResult(
    val provider: LLMProvider,
    val taskType: TaskType,
    val firstTokenLatencyMs: Long,
    val totalLatencyMs: Long,
    val memoryUsageMb: Float,
    val batteryUsagePercent: Float? = null,
    val modelSizeMb: Float,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TaskType {
    CHAT,
    SUMMARIZATION,
    PROOFREADING
}