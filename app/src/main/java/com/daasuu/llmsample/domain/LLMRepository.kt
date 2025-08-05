package com.daasuu.llmsample.domain

import com.daasuu.llmsample.data.model.BenchmarkResult
import com.daasuu.llmsample.data.model.TaskType
import kotlinx.coroutines.flow.Flow

interface LLMRepository {
    suspend fun generateChatResponse(prompt: String): Flow<String>
    suspend fun summarizeText(text: String): Flow<String>
    suspend fun proofreadText(text: String): Flow<String>
    suspend fun getBenchmarkResult(taskType: TaskType, input: String): BenchmarkResult
    suspend fun isAvailable(): Boolean
    suspend fun initialize()
    suspend fun release()
}