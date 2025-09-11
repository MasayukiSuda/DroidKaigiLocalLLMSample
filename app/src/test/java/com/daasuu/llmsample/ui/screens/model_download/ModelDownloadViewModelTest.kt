package com.daasuu.llmsample.ui.screens.model_download

import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.model_manager.ModelDownloader
import com.daasuu.llmsample.data.model_manager.ModelManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class ModelDownloadViewModelTest : TestBase() {

    private lateinit var mockModelManager: ModelManager
    private lateinit var viewModel: ModelDownloadViewModel

    private val testModels = listOf(
        ModelInfo(
            id = "test-model-1",
            name = "Test Model 1",
            downloadUrl = "https://example.com/model1.gguf",
            fileSize = 1000000L,
            description = "Test model 1 description",
            isDownloaded = false,
            localPath = null
        ),
        ModelInfo(
            id = "test-model-2",
            name = "Test Model 2",
            downloadUrl = "https://example.com/model2.gguf",
            fileSize = 2000000L,
            description = "Test model 2 description",
            isDownloaded = true,
            localPath = "/path/to/model2.gguf"
        )
    )

    @Before
    override fun setUp() {
        super.setUp()
        mockModelManager = mockk(relaxed = true)

        // Setup default mock behaviors
        every { mockModelManager.getAvailableModels() } returns testModels

        viewModel = ModelDownloadViewModel(mockModelManager)
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Then
        viewModel.models.test {
            assertEquals(testModels, awaitItem())
        }

        viewModel.downloadingModels.test {
            assertEquals(emptyMap<String, Float>(), awaitItem())
        }

        // Verify loadModels was called during initialization
        verify { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `downloadModel should call modelManager and update progress`() = runTest {
        // Given
        val modelId = "test-model-1"
        val mockProgress = ModelDownloader.DownloadProgress(
            downloadedBytes = 500000L,
            totalBytes = 1000000L,
            progress = 0.5f
        )
        
        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        } coAnswers {
            val onProgress = secondArg<(ModelDownloader.DownloadProgress) -> Unit>()
            onProgress(mockProgress)
            Result.success(Unit)
        }

        // When
        viewModel.downloadModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(100)

        // Then
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        }

        // Verify progress was updated and then cleared after success
        viewModel.downloadingModels.test {
            assertEquals(emptyMap<String, Float>(), awaitItem())
        }
    }

    @Test
    fun `downloadModel should handle progress updates correctly`() = runTest {
        // Given
        val modelId = "test-model-1"
        val progressValues = listOf(0.1f, 0.5f, 0.8f, 1.0f)
        
        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        } coAnswers {
            val onProgress = secondArg<(ModelDownloader.DownloadProgress) -> Unit>()
            
            // Simulate progress updates
            progressValues.forEach { progress ->
                onProgress(ModelDownloader.DownloadProgress(
                    downloadedBytes = (1000000L * progress).toLong(),
                    totalBytes = 1000000L,
                    progress = progress
                ))
                kotlinx.coroutines.delay(10)
            }
            
            Result.success(Unit)
        }

        // When
        viewModel.downloadModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(200)

        // Then
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        }
    }

    @Test
    fun `downloadModel success should reload models and clear downloading status`() = runTest {
        // Given
        val modelId = "test-model-1"
        val updatedModels = testModels.map { 
            if (it.id == modelId) it.copy(isDownloaded = true) else it 
        }
        
        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        } returns Result.success(Unit)

        every { mockModelManager.getAvailableModels() } returnsMany listOf(testModels, updatedModels)

        // When
        viewModel.downloadModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(100)

        // Then
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        }

        // Verify getAvailableModels was called again to reload models
        verify(exactly = 2) { mockModelManager.getAvailableModels() }

        // Verify downloading status was cleared
        viewModel.downloadingModels.test {
            assertEquals(emptyMap<String, Float>(), awaitItem())
        }
    }

    @Test
    fun `downloadModel failure should clear downloading status and handle error`() = runTest {
        // Given
        val modelId = "test-model-1"
        val error = RuntimeException("Download failed")
        
        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        } returns Result.failure(error)

        // When
        viewModel.downloadModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(100)

        // Then
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        }

        // Verify downloading status was cleared
        viewModel.downloadingModels.test {
            assertEquals(emptyMap<String, Float>(), awaitItem())
        }

        // Verify models were not reloaded on failure
        verify(exactly = 1) { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `downloadModel with IllegalStateException should handle manual placement required`() = runTest {
        // Given
        val modelId = "test-model-1"
        val error = IllegalStateException("Manual placement required")
        
        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        } returns Result.failure(error)

        // When
        viewModel.downloadModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(100)

        // Then
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId,
                onProgress = any()
            )
        }

        // Verify downloading status was cleared
        viewModel.downloadingModels.test {
            assertEquals(emptyMap<String, Float>(), awaitItem())
        }
    }

    @Test
    fun `deleteModel success should reload models`() = runTest {
        // Given
        val modelId = "test-model-2"
        val updatedModels = testModels.filter { it.id != modelId }
        
        coEvery { mockModelManager.deleteModel(modelId) } returns Result.success(Unit)
        every { mockModelManager.getAvailableModels() } returnsMany listOf(testModels, updatedModels)

        // When
        viewModel.deleteModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(100)

        // Then
        coVerify { mockModelManager.deleteModel(modelId) }

        // Verify getAvailableModels was called again to reload models
        verify(exactly = 2) { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `deleteModel failure should not reload models`() = runTest {
        // Given
        val modelId = "test-model-2"
        val error = RuntimeException("Delete failed")
        
        coEvery { mockModelManager.deleteModel(modelId) } returns Result.failure(error)

        // When
        viewModel.deleteModel(modelId)

        // Wait for coroutine to complete
        kotlinx.coroutines.delay(100)

        // Then
        coVerify { mockModelManager.deleteModel(modelId) }

        // Verify models were not reloaded on failure
        verify(exactly = 1) { mockModelManager.getAvailableModels() }
    }

    @Test
    fun `multiple download operations should maintain separate progress tracking`() = runTest {
        // Given
        val modelId1 = "test-model-1"
        val modelId2 = "test-model-2"
        
        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId1,
                onProgress = any()
            )
        } coAnswers {
            val onProgress = secondArg<(ModelDownloader.DownloadProgress) -> Unit>()
            onProgress(ModelDownloader.DownloadProgress(
                downloadedBytes = 250000L,
                totalBytes = 1000000L,
                progress = 0.25f
            ))
            Result.success(Unit)
        }

        coEvery { 
            mockModelManager.downloadModel(
                modelId = modelId2,
                onProgress = any()
            )
        } coAnswers {
            val onProgress = secondArg<(ModelDownloader.DownloadProgress) -> Unit>()
            onProgress(ModelDownloader.DownloadProgress(
                downloadedBytes = 1500000L,
                totalBytes = 2000000L,
                progress = 0.75f
            ))
            Result.success(Unit)
        }

        // When
        viewModel.downloadModel(modelId1)
        viewModel.downloadModel(modelId2)

        // Wait for coroutines to complete
        kotlinx.coroutines.delay(200)

        // Then
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId1,
                onProgress = any()
            )
        }
        
        coVerify { 
            mockModelManager.downloadModel(
                modelId = modelId2,
                onProgress = any()
            )
        }

        // Both downloads should eventually complete and clear their progress
        viewModel.downloadingModels.test {
            assertEquals(emptyMap<String, Float>(), awaitItem())
        }
    }
}

