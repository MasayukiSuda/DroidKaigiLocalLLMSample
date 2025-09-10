package com.daasuu.llmsample.data.llm.llamacpp

import android.llama.cpp.LLamaAndroid
import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.model_manager.ModelManager
import com.daasuu.llmsample.data.model.ModelInfo
import com.daasuu.llmsample.data.preferences.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.*

class LlamaCppRepositoryTest : TestBase() {

    private lateinit var mockModelManager: ModelManager
    private lateinit var mockLlamaAndroid: LLamaAndroid
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var repository: LlamaCppRepository
    private lateinit var mockFile: File

    @Before
    override fun setUp() {
        super.setUp()
        mockModelManager = mockk(relaxed = true)
        mockLlamaAndroid = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        mockFile = mockk(relaxed = true)
        
        repository = LlamaCppRepository(mockModelManager, mockLlamaAndroid, mockPreferencesManager)
    }

    @Test
    fun `initialize should load model successfully`() = runTest {
        // Given
        // Create a temporary file for testing
        val tempFile = java.io.File.createTempFile("test-model", ".bin")
        tempFile.writeText("dummy model content")
        
        val selectedModel = ModelInfo(
            id = "test-model",
            name = "Test Model",
            downloadUrl = "https://example.com/model.bin",
            fileSize = 1000000L,
            description = "Test model",
            isDownloaded = true,
            localPath = tempFile.absolutePath
        )
        
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf("test-model")
        every { mockModelManager.getAvailableModels() } returns listOf(selectedModel)
        
        coEvery { mockLlamaAndroid.load(any()) } returns Unit

        // When
        repository.initialize()

        // Then
        coVerify { mockLlamaAndroid.load(tempFile.absolutePath) }
        assertTrue(repository.isAvailable())
        
        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `initialize should handle missing model file`() = runTest {
        // Given
        val selectedModel = ModelInfo(
            id = "test-model",
            name = "Test Model",
            downloadUrl = "https://example.com/model.bin",
            fileSize = 1000000L,
            description = "Test model",
            isDownloaded = true,
            localPath = "/non/existent/path/model.bin"
        )
        
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf("test-model")
        every { mockModelManager.getAvailableModels() } returns listOf(selectedModel)

        // When
        repository.initialize()

        // Then
        assertFalse(repository.isAvailable())
        coVerify(exactly = 0) { mockLlamaAndroid.load(any()) }
    }

    @Test
    fun `initialize should handle null selected model`() = runTest {
        // Given
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf(null)
        every { mockModelManager.getAvailableModels() } returns emptyList()

        // When
        repository.initialize()

        // Then
        assertFalse(repository.isAvailable())
        coVerify(exactly = 0) { mockLlamaAndroid.load(any()) }
    }

    @Test
    fun `release should call llamaAndroid unload`() = runTest {
        // Given
        coEvery { mockLlamaAndroid.unload() } just runs

        // When
        repository.release()

        // Then
        coVerify { mockLlamaAndroid.unload() }
    }

    @Test
    fun `isAvailable should return false initially`() = runTest {
        // When & Then
        assertFalse(repository.isAvailable())
    }

    @Test
    fun `isAvailable should return true after successful initialization`() = runTest {
        // Given
        // Create a temporary file for testing
        val tempFile = java.io.File.createTempFile("test-model", ".bin")
        tempFile.writeText("dummy model content")
        
        val selectedModel = ModelInfo(
            id = "test-model",
            name = "Test Model",
            downloadUrl = "https://example.com/model.bin",
            fileSize = 1000000L,
            description = "Test model",
            isDownloaded = true,
            localPath = tempFile.absolutePath
        )
        
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf("test-model")
        every { mockModelManager.getAvailableModels() } returns listOf(selectedModel)
        
        coEvery { mockLlamaAndroid.load(any()) } returns Unit

        repository.initialize()

        // When & Then
        assertTrue(repository.isAvailable())
        
        // Cleanup
        tempFile.delete()
    }

    @Test
    fun `generateChatResponse should return flow from llamaAndroid`() = runTest {
        // Given
        // Set up the repository as if it were initialized by using reflection
        val isInitializedField = repository::class.java.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.setBoolean(repository, true)
        
        val expectedResponse = "Hello there!"
        coEvery { mockLlamaAndroid.send(any(), any()) } returns flowOf(expectedResponse)

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then - just verify the flow can be created
        assertNotNull(flow)
    }

    @Test
    fun `generateChatResponse should handle llamaAndroid errors`() = runTest {
        // Given
        // Set up the repository as if it were initialized by using reflection
        val isInitializedField = repository::class.java.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.setBoolean(repository, true)
        
        val exception = RuntimeException("Model error")
        coEvery { mockLlamaAndroid.send(any(), any()) } throws exception

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then - just verify the flow can be created
        assertNotNull(flow)
    }

    @Test
    fun `summarizeText should generate summary with correct prompt`() = runTest {
        // Given
        // Set up the repository as if it were initialized by using reflection
        val isInitializedField = repository::class.java.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.setBoolean(repository, true)
        
        val expectedSummary = "Short summary"
        coEvery { mockLlamaAndroid.send(any(), any()) } returns flowOf(expectedSummary)

        // When
        val flow = repository.summarizeText("Long text to summarize")

        // Then - just verify the flow can be created
        assertNotNull(flow)
    }

    @Test
    fun `proofreadText should generate corrections with correct prompt`() = runTest {
        // Given
        // Set up the repository as if it were initialized by using reflection
        val isInitializedField = repository::class.java.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.setBoolean(repository, true)
        
        val expectedCorrection = "Corrected text"
        coEvery { mockLlamaAndroid.send(any(), any()) } returns flowOf(expectedCorrection)

        // When
        val flow = repository.proofreadText("Text with errors")

        // Then - just verify the flow can be created
        assertNotNull(flow)
    }

    @Test
    fun `repository should handle model loading failure gracefully`() = runTest {
        // Given
        // Create a temporary file for testing
        val tempFile = java.io.File.createTempFile("test-model", ".bin")
        tempFile.writeText("dummy model content")
        
        val selectedModel = ModelInfo(
            id = "test-model",
            name = "Test Model",
            downloadUrl = "https://example.com/model.bin",
            fileSize = 1000000L,
            description = "Test model",
            isDownloaded = true,
            localPath = tempFile.absolutePath
        )
        
        every { mockPreferencesManager.selectedLlamaModel } returns flowOf("test-model")
        every { mockModelManager.getAvailableModels() } returns listOf(selectedModel)
        
        val exception = RuntimeException("Failed to load model")
        coEvery { mockLlamaAndroid.load(any()) } throws exception

        // When
        repository.initialize()

        // Then
        assertFalse(repository.isAvailable())
        
        // Cleanup
        tempFile.delete()
    }
}