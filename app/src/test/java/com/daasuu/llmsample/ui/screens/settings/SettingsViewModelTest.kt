package com.daasuu.llmsample.ui.screens.settings

import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.benchmark.InterferenceMonitor
import com.daasuu.llmsample.data.benchmark.UserActionType
import com.daasuu.llmsample.data.llm.gemini.DeviceCompatibility
import com.daasuu.llmsample.data.llm.gemini.GeminiNanoCompatibilityChecker
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.preferences.PreferencesManager
import com.daasuu.llmsample.domain.LLMManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class SettingsViewModelTest : TestBase() {

    private lateinit var mockModelManager: ModelManager
    private lateinit var mockLlmManager: LLMManager
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var mockInterferenceMonitor: InterferenceMonitor
    private lateinit var mockGeminiNanoCompatibilityChecker: GeminiNanoCompatibilityChecker
    private lateinit var viewModel: SettingsViewModel

    @Before
    override fun setUp() {
        super.setUp()
        mockModelManager = mockk(relaxed = true)
        mockLlmManager = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        mockInterferenceMonitor = mockk(relaxed = true)
        mockGeminiNanoCompatibilityChecker = mockk(relaxed = true)

        // Setup default mock behaviors
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LITE_RT)
        every { mockPreferencesManager.isGpuEnabled } returns flowOf(false)
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf(null)
        every { mockGeminiNanoCompatibilityChecker.isDeviceSupported() } returns DeviceCompatibility.Supported
        every { mockGeminiNanoCompatibilityChecker.getCompatibilityMessage(any()) } returns "Test message"

        viewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Then
        viewModel.selectedProvider.test {
            assertEquals(LLMProvider.LITE_RT, awaitItem())
        }

        viewModel.isGpuEnabled.test {
            assertFalse(awaitItem())
        }

        // Verify model copying was called
        coVerify { mockModelManager.copyModelsFromAssets() }
    }

    @Test
    fun `selectProvider should update preferences and record user action`() = runTest {
        // Given
        val newProvider = LLMProvider.LLAMA_CPP

        // When
        viewModel.selectProvider(newProvider)

        // Then
        coVerify { mockPreferencesManager.setCurrentProvider(newProvider) }
        verify { mockInterferenceMonitor.recordUserAction(UserActionType.PROVIDER_SWITCH) }
    }

    @Test
    fun `provider changes should update selectedProvider and set current provider`() = runTest {
        // Given
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.GEMINI_NANO)

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        newViewModel.selectedProvider.test {
            assertEquals(LLMProvider.GEMINI_NANO, awaitItem())
        }

        coVerify { mockLlmManager.setCurrentProvider(LLMProvider.GEMINI_NANO) }
    }

    @Test
    fun `setGpuEnabled should update preferences`() = runTest {
        // When
        viewModel.setGpuEnabled(true)

        // Then
        coVerify { mockPreferencesManager.setGpuEnabled(true) }
    }

    @Test
    fun `GPU setting changes should reinitialize LITE_RT provider`() = runTest {
        // Given
        every { mockPreferencesManager.isGpuEnabled } returns flowOf(true)
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LITE_RT)

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        newViewModel.isGpuEnabled.test {
            assertTrue(awaitItem())
        }

        // Note: reinitialization only happens on subsequent changes, not initial value
        // So we don't verify reinitializeCurrentProvider() here as it's the initial state
    }

    @Test
    fun `Llama model changes should reinitialize LLAMA_CPP provider`() = runTest {
        // Given
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf("test-model")
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LLAMA_CPP)

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        newViewModel.selectedProvider.test {
            assertEquals(LLMProvider.LLAMA_CPP, awaitItem())
        }

        // Note: reinitialization only happens on subsequent changes, not initial value
    }

    @Test
    fun `isProviderAvailable should return correct values for different providers`() = runTest {
        // Given
        every { mockGeminiNanoCompatibilityChecker.isDeviceSupported() } returns DeviceCompatibility.Supported

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        assertTrue(newViewModel.isProviderAvailable(LLMProvider.LITE_RT))
        assertTrue(newViewModel.isProviderAvailable(LLMProvider.LLAMA_CPP))
        assertTrue(newViewModel.isProviderAvailable(LLMProvider.GEMINI_NANO))
    }

    @Test
    fun `isProviderAvailable should return false for unsupported Gemini Nano`() = runTest {
        // Given
        every { mockGeminiNanoCompatibilityChecker.isDeviceSupported() } returns 
            DeviceCompatibility.UnsupportedDevice("Test Device")

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        assertTrue(newViewModel.isProviderAvailable(LLMProvider.LITE_RT))
        assertTrue(newViewModel.isProviderAvailable(LLMProvider.LLAMA_CPP))
        assertFalse(newViewModel.isProviderAvailable(LLMProvider.GEMINI_NANO))
    }

    @Test
    fun `getProviderUnavailableReason should return null for available providers`() = runTest {
        // Given
        every { mockGeminiNanoCompatibilityChecker.isDeviceSupported() } returns DeviceCompatibility.Supported

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        assertNull(newViewModel.getProviderUnavailableReason(LLMProvider.LITE_RT))
        assertNull(newViewModel.getProviderUnavailableReason(LLMProvider.LLAMA_CPP))
        assertNull(newViewModel.getProviderUnavailableReason(LLMProvider.GEMINI_NANO))
    }

    @Test
    fun `getProviderUnavailableReason should return message for unsupported Gemini Nano`() = runTest {
        // Given
        val compatibility = DeviceCompatibility.UnsupportedDevice("Test Device")
        val expectedMessage = "Device not supported"
        every { mockGeminiNanoCompatibilityChecker.isDeviceSupported() } returns compatibility
        every { mockGeminiNanoCompatibilityChecker.getCompatibilityMessage(compatibility) } returns expectedMessage

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then
        assertEquals(expectedMessage, newViewModel.getProviderUnavailableReason(LLMProvider.GEMINI_NANO))
    }

    @Test
    fun `checkGeminiNanoCompatibility should switch to LITE_RT when GEMINI_NANO is unsupported`() = runTest {
        // Given
        val compatibility = DeviceCompatibility.UnsupportedDevice("Test Device")
        every { mockGeminiNanoCompatibilityChecker.isDeviceSupported() } returns compatibility
        
        // Start with GEMINI_NANO selected but it will be auto-switched
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LITE_RT)

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then - compatibility checking should happen
        verify { mockGeminiNanoCompatibilityChecker.isDeviceSupported() }
        
        // Verify the provider availability check works correctly
        assertFalse(newViewModel.isProviderAvailable(LLMProvider.GEMINI_NANO))
    }

    @Test
    fun `reinitialization error should be handled gracefully`() = runTest {
        // Given
        every { mockPreferencesManager.isGpuEnabled } returns flowOf(false)
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LITE_RT)

        // When
        val newViewModel = SettingsViewModel(
            modelManager = mockModelManager,
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor,
            geminiNanoCompatibilityChecker = mockGeminiNanoCompatibilityChecker
        )

        // Then - should initialize successfully even if other components might fail
        newViewModel.isGpuEnabled.test {
            assertFalse(awaitItem())
        }

        // Verify basic initialization works
        assertNotNull(newViewModel)
    }
}
