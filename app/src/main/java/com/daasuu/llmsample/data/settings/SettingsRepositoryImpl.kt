package com.daasuu.llmsample.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.daasuu.llmsample.data.model.LLMProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val CURRENT_PROVIDER = intPreferencesKey("current_provider")
    }

    override val currentProvider: Flow<LLMProvider> =
        context.dataStore.data.map { prefs ->
            val ordinal = prefs[Keys.CURRENT_PROVIDER] ?: LLMProvider.LLAMA_CPP.ordinal
            LLMProvider.entries.getOrNull(ordinal) ?: LLMProvider.LLAMA_CPP
        }

    override suspend fun setCurrentProvider(provider: LLMProvider) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_PROVIDER] = provider.ordinal
        }
    }
}


