package com.daasuu.llmsample.data.model_manager

import android.content.Context
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.model.ModelInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.*

class ModelManagerTest : TestBase() {

    private lateinit var mockContext: Context
    private lateinit var mockModelDownloader: ModelDownloader
    private lateinit var modelManager: ModelManager

    @Before
    override fun setUp() {
        super.setUp()
        mockContext = mockk(relaxed = true)
        mockModelDownloader = mockk(relaxed = true)
        
        // Mock the filesDir to return a temporary directory
        val tempDir = File.createTempFile("test", "dir").apply { 
            delete()
            mkdirs() 
        }
        every { mockContext.filesDir } returns tempDir
        
        modelManager = ModelManager(mockContext, mockModelDownloader)
    }

    @Test
    fun `getAvailableModels should return list of downloadable models`() = runTest {
        // When
        val models = modelManager.getAvailableModels()

        // Then
        assertNotNull(models)
        assertTrue(models.all { it is ModelInfo })
        assertTrue(models.isNotEmpty())
    }

    @Test
    fun `downloadableModels should contain expected models`() {
        // When
        val models = modelManager.downloadableModels

        // Then
        assertTrue(models.any { it.id == ModelManager.MODEL_ID_LLAMA_3_8B_COSMOPEDIA_JAPANESE })
        assertTrue(models.any { it.id == ModelManager.MODEL_ID_ELYZA_JAPANESE_LLAMA_2_7B })
    }

    @Test
    fun `downloadModel should delegate to ModelDownloader`() = runTest {
        // Given
        val modelId = ModelManager.MODEL_ID_LLAMA_3_8B_COSMOPEDIA_JAPANESE
        coEvery { 
            mockModelDownloader.downloadModel(any(), any(), any(), any()) 
        } returns Result.success(mockk())

        // When
        val result = modelManager.downloadModel(
            modelId = modelId,
            onProgress = { _ -> }
        )

        // Then
        coVerify { 
            mockModelDownloader.downloadModel(any(), any(), any(), any()) 
        }
        assertTrue(result.isSuccess)
    }
}