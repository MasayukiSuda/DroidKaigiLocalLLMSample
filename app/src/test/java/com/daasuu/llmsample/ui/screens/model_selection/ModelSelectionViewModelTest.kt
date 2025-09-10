package com.daasuu.llmsample.ui.screens.model_selection

import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.preferences.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class ModelSelectionViewModelTest : TestBase() {

    private lateinit var mockModelManager: ModelManager
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var viewModel: ModelSelectionViewModel

    private val testModels = listOf(
        ModelInfo(
            id = "test-model-1",
            name = "Test Model 1",
            downloadUrl = "https://example.com/model1.gguf",
            fileSize = 1000000L,
            description = "Test model 1 description",
            isDownloaded = true,
            localPath = "/path/to/model1.gguf"
        ),
        ModelInfo(
            id = "test-model-2", 
            name = "Test Model 2",
            downloadUrl = "https://example.com/model2.gguf",
            fileSize = 2000000L,
            description = "Test model 2 description",
            isDownloaded = false,
            localPath = null
        )
    )

    @Before
    override fun setUp() {
        super.setUp()
        mockModelManager = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)

        // Setup default mock behaviors
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf(null)
        every { mockModelManager.getAvailableModels() } returns testModels
        coEvery { mockPreferencesManager.setSelectedLlamaModel(any()) } just Runs

        viewModel = ModelSelectionViewModel(
            modelManager = mockModelManager,
            preferencesManager = mockPreferencesManager
        )
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Then
        viewModel.selectedModel.test {
            assertNull(awaitItem())
        }

        viewModel.availableModels.test {
            assertEquals(testModels, awaitItem())
        }

        viewModel.isLoading.test {
            assertFalse(awaitItem()) // Loading should complete
        }

        // Verify that getAvailableModels was called during initialization
        verify { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `should collect selected model from preferences`() = runTest {
        // Given
        val selectedModelId = "test-model-1"
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf(selectedModelId)

        // When
        val newViewModel = ModelSelectionViewModel(
            modelManager = mockModelManager,
            preferencesManager = mockPreferencesManager
        )

        // Then
        newViewModel.selectedModel.test {
            assertEquals(selectedModelId, awaitItem())
        }
    }

    @Test
    fun `loadModels should update availableModels and loading state`() = runTest {
        // Given - models are already loaded in setup

        // Then
        viewModel.availableModels.test {
            assertEquals(testModels, awaitItem())
        }

        viewModel.isLoading.test {
            assertFalse(awaitItem()) // Should be false after loading completes
        }

        verify { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `loadModels should handle errors gracefully`() = runTest {
        // Given
        every { mockModelManager.getAvailableModels() } throws RuntimeException("Network error")

        // When
        val newViewModel = ModelSelectionViewModel(
            modelManager = mockModelManager,
            preferencesManager = mockPreferencesManager
        )

        // Then
        newViewModel.availableModels.test {
            assertEquals(emptyList(), awaitItem())
        }

        newViewModel.isLoading.test {
            assertFalse(awaitItem()) // Loading should complete even with error
        }

        verify { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `selectModel should call preferencesManager setSelectedLlamaModel`() = runTest {
        // Given
        val modelId = "test-model-1"

        // When
        viewModel.selectModel(modelId)

        // Then
        coVerify { mockPreferencesManager.setSelectedLlamaModel(modelId) }
    }

    @Test
    fun `selectModel with null should call preferencesManager setSelectedLlamaModel with null`() = runTest {
        // When
        viewModel.selectModel(null)

        // Then
        coVerify { mockPreferencesManager.setSelectedLlamaModel(null) }
    }

    @Test
    fun `refreshModels should call loadModels again`() = runTest {
        // Given - clear previous invocations
        clearMocks(mockModelManager)
        every { mockModelManager.getAvailableModels() } returns testModels

        // When
        viewModel.refreshModels()

        // Then
        verify { mockModelManager.getAvailableModels() }
        
        viewModel.availableModels.test {
            assertEquals(testModels, awaitItem())
        }
    }

    @Test
    fun `refreshModels should update loading state during execution`() = runTest {
        // Given
        var callCount = 0
        every { mockModelManager.getAvailableModels() } answers {
            callCount++
            if (callCount == 1) {
                // First call during init
                testModels
            } else {
                // Second call during refresh
                Thread.sleep(100) // Simulate some loading time
                testModels
            }
        }

        // When
        viewModel.refreshModels()

        // Then - loading state should be managed properly
        viewModel.isLoading.test {
            assertFalse(awaitItem()) // Should be false after refresh completes
        }
    }

    @Test
    fun `preference changes should update selectedModel`() = runTest {
        // Given
        val modelId = "test-model-1"
        
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf(modelId)

        // When
        val newViewModel = ModelSelectionViewModel(
            modelManager = mockModelManager,
            preferencesManager = mockPreferencesManager
        )

        // Then
        newViewModel.selectedModel.test {
            assertEquals(modelId, awaitItem())
        }
    }

    @Test
    fun `loadModels error should not affect selectedModel`() = runTest {
        // Given
        val selectedModelId = "test-model-1"
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf(selectedModelId)
        every { mockModelManager.getAvailableModels() } throws RuntimeException("Error")

        // When
        val newViewModel = ModelSelectionViewModel(
            modelManager = mockModelManager,
            preferencesManager = mockPreferencesManager
        )

        // Then
        newViewModel.selectedModel.test {
            assertEquals(selectedModelId, awaitItem())
        }

        newViewModel.availableModels.test {
            assertEquals(emptyList(), awaitItem())
        }
    }
}
