package com.daasuu.llmsample.domain

import kotlinx.coroutines.flow.Flow

interface LLMRepository {
    suspend fun generateChatResponse(prompt: String): Flow<String>
    suspend fun summarizeText(text: String): Flow<String>
    suspend fun proofreadText(text: String): Flow<String>
    suspend fun isAvailable(): Boolean
    suspend fun initialize()
    suspend fun release()
}