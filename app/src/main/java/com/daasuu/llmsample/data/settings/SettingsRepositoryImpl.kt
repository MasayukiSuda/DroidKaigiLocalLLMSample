package com.daasuu.llmsample.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val GPU_ENABLED = booleanPreferencesKey("gpu_enabled")
        val SELECTED_LLAMA_MODEL = stringPreferencesKey("selected_llama_model")
    }

    override val currentProvider: Flow<LLMProvider> =
        context.dataStore.data.map { prefs ->
            val ordinal = prefs[Keys.CURRENT_PROVIDER] ?: LLMProvider.LITE_RT.ordinal
            LLMProvider.entries.getOrNull(ordinal) ?: LLMProvider.LITE_RT
        }

    override val isGpuEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.GPU_ENABLED] ?: false
        }

    override val selectedLlamaModel: Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.SELECTED_LLAMA_MODEL]
        }

    override suspend fun setCurrentProvider(provider: LLMProvider) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_PROVIDER] = provider.ordinal
        }
    }

    override suspend fun setGpuEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GPU_ENABLED] = enabled
        }
    }

    override suspend fun setSelectedLlamaModel(modelId: String?) {
        context.dataStore.edit { prefs ->
            if (modelId != null) {
                prefs[Keys.SELECTED_LLAMA_MODEL] = modelId
            } else {
                prefs.remove(Keys.SELECTED_LLAMA_MODEL)
            }
        }
    }
}


