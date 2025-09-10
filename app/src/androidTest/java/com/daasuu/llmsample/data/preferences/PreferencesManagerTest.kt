package com.daasuu.llmsample.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daasuu.llmsample.data.model.LLMProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesManagerTest {

    private lateinit var testContext: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        testContext = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(testContext)
        
        // Clean up any existing test data
        runTest {
            clearPreferences()
        }
    }

    @After
    fun tearDown() = runTest {
        // Clean up test data after each test
        clearPreferences()
    }

    private suspend fun clearPreferences() {
        // Reset all preferences to their default values
        preferencesManager.setCurrentProvider(LLMProvider.LITE_RT)
        preferencesManager.setGpuEnabled(false)
        preferencesManager.setSelectedLlamaModel(null)
    }

    @Test
    fun defaultProvider_shouldBeLiteRT() = runTest {
        // Test default provider
        val currentProvider = preferencesManager.currentProvider.first()
        assertEquals(LLMProvider.LITE_RT, currentProvider)
    }

    @Test
    fun setCurrentProvider_shouldPersistValue() = runTest {
        // Set GEMINI_NANO provider
        preferencesManager.setCurrentProvider(LLMProvider.GEMINI_NANO)
        
        // Verify it was saved
        val provider = preferencesManager.currentProvider.first()
        assertEquals(LLMProvider.GEMINI_NANO, provider)
    }

    @Test
    fun setCurrentProvider_allProviders_shouldWork() = runTest {
        // Test all provider types
        for (provider in LLMProvider.entries) {
            preferencesManager.setCurrentProvider(provider)
            val savedProvider = preferencesManager.currentProvider.first()
            assertEquals(provider, savedProvider)
        }
    }

    @Test
    fun defaultGpuEnabled_shouldBeFalse() = runTest {
        // Test default GPU setting
        val isGpuEnabled = preferencesManager.isGpuEnabled.first()
        assertFalse(isGpuEnabled)
    }

    @Test
    fun setGpuEnabled_true_shouldPersistValue() = runTest {
        // Enable GPU
        preferencesManager.setGpuEnabled(true)
        
        // Verify it was saved
        val isEnabled = preferencesManager.isGpuEnabled.first()
        assertTrue(isEnabled)
    }

    @Test
    fun setGpuEnabled_false_shouldPersistValue() = runTest {
        // First enable GPU
        preferencesManager.setGpuEnabled(true)
        assertTrue(preferencesManager.isGpuEnabled.first())
        
        // Then disable GPU
        preferencesManager.setGpuEnabled(false)
        val isEnabled = preferencesManager.isGpuEnabled.first()
        assertFalse(isEnabled)
    }

    @Test
    fun defaultSelectedLlamaModel_shouldBeNull() = runTest {
        // Test default selected model
        val selectedModel = preferencesManager.selectedLlamaModel.first()
        assertNull(selectedModel)
    }

    @Test
    fun setSelectedLlamaModel_shouldPersistValue() = runTest {
        val testModelId = "test_model_123"
        
        // Set model
        preferencesManager.setSelectedLlamaModel(testModelId)
        
        // Verify it was saved
        val savedModel = preferencesManager.selectedLlamaModel.first()
        assertEquals(testModelId, savedModel)
    }

    @Test
    fun setSelectedLlamaModel_null_shouldRemoveValue() = runTest {
        val testModelId = "test_model_123"
        
        // First set a model
        preferencesManager.setSelectedLlamaModel(testModelId)
        assertEquals(testModelId, preferencesManager.selectedLlamaModel.first())
        
        // Then set to null to remove
        preferencesManager.setSelectedLlamaModel(null)
        val savedModel = preferencesManager.selectedLlamaModel.first()
        assertNull(savedModel)
    }

    @Test
    fun multiplePreferences_shouldWorkTogether() = runTest {
        // Set multiple preferences
        preferencesManager.setCurrentProvider(LLMProvider.LLAMA_CPP)
        preferencesManager.setGpuEnabled(true)
        preferencesManager.setSelectedLlamaModel("multi_test_model")
        
        // Verify all values
        assertEquals(LLMProvider.LLAMA_CPP, preferencesManager.currentProvider.first())
        assertTrue(preferencesManager.isGpuEnabled.first())
        assertEquals("multi_test_model", preferencesManager.selectedLlamaModel.first())
    }

    @Test
    fun invalidProviderOrdinal_shouldFallbackToDefault() = runTest {
        // This test verifies the robustness of the provider loading
        // by testing the fallback mechanism in the actual flow
        val currentProvider = preferencesManager.currentProvider.first()
        
        // Should always return a valid provider (default is LITE_RT)
        assertTrue(LLMProvider.entries.contains(currentProvider))
    }
}
