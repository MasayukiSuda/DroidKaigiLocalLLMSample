package com.daasuu.llmsample.data.settings

import com.daasuu.llmsample.data.model.LLMProvider
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val currentProvider: Flow<LLMProvider>
    val isGpuEnabled: Flow<Boolean>
    suspend fun setCurrentProvider(provider: LLMProvider)
    suspend fun setGpuEnabled(enabled: Boolean)
}


