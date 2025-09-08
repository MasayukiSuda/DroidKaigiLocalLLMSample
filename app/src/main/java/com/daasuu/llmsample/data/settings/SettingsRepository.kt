package com.daasuu.llmsample.data.settings

import com.daasuu.llmsample.data.model.LLMProvider
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val currentProvider: Flow<LLMProvider>
    suspend fun setCurrentProvider(provider: LLMProvider)
}


