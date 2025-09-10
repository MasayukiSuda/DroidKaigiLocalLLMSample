package com.daasuu.llmsample.data.llm.gemini

import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.GenerateContentResponse
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class GeminiNanoRepositoryTest : TestBase() {

    private lateinit var mockModelManager: GeminiNanoModelManager
    private lateinit var mockGenerativeModel: GenerativeModel
    private lateinit var repository: GeminiNanoRepository

    @Before
    override fun setUp() {
        super.setUp()
        mockModelManager = mockk(relaxed = true)
        mockGenerativeModel = mockk(relaxed = true)
        repository = GeminiNanoRepository(mockModelManager)
    }

    @Test
    fun `initialize should be no-op as documented`() = runTest {
        // When
        repository.initialize()

        // Then - initialize() is documented as no-op, so no verification needed
        // The actual initialization happens lazily in getOrCreateModel()
    }

    @Test
    fun `isAvailable should delegate to modelManager`() = runTest {
        // Given
        coEvery { mockModelManager.isModelAvailable() } returns true

        // When
        val result = repository.isAvailable()

        // Then
        assertTrue(result)
        coVerify { mockModelManager.isModelAvailable() }
    }

    @Test
    fun `isAvailable should return false when model manager returns false`() = runTest {
        // Given
        coEvery { mockModelManager.isModelAvailable() } returns false

        // When
        val result = repository.isAvailable()

        // Then
        assertFalse(result)
        coVerify { mockModelManager.isModelAvailable() }
    }

    @Test
    fun `release should call releaseModel on modelManager`() = runTest {
        // When
        repository.release()

        // Then
        coVerify { mockModelManager.releaseModel() }
    }

    @Test
    fun `generateChatResponse should return streaming response when model available`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns mockGenerativeModel
        
        val mockResponse1 = mockk<GenerateContentResponse>()
        val mockResponse2 = mockk<GenerateContentResponse>()
        every { mockResponse1.text } returns "Hello"
        every { mockResponse2.text } returns " World!"
        
        every { mockGenerativeModel.generateContentStream(any<String>()) } returns 
            flowOf<GenerateContentResponse>(mockResponse1, mockResponse2)

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then
        flow.test {
            assertEquals("Hello", awaitItem())
            assertEquals(" World!", awaitItem())
            awaitComplete()
        }
        
        coVerify { mockModelManager.getOrCreateModel() }
        verify { mockGenerativeModel.generateContentStream(any<String>()) }
    }

    @Test
    fun `generateChatResponse should return error when model not available`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns null

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then
        flow.test {
            assertEquals("Error: Gemini Nano not initialized", awaitItem())
            awaitComplete()
        }
        
        coVerify { mockModelManager.getOrCreateModel() }
    }

    @Test
    fun `generateChatResponse should handle exceptions gracefully with mock fallback`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns mockGenerativeModel
        every { mockGenerativeModel.generateContentStream(any<String>()) } throws RuntimeException("Network error")

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then - Should fallback to mock response
        flow.test {
            // Mock response is split by "。" so we get multiple items
            val response1 = awaitItem() // Should contain "【モック】"
            assertTrue(response1.contains("【モック】") && response1.contains("チャットレスポンス"), 
                      "First response should contain mock chat response, got: $response1")
            
            val response2 = awaitItem() // Should contain the remaining part
            assertTrue(response2.contains("実際のGemini Nanoの使用"), 
                      "Second response should contain continuation, got: $response2")
            
            val response3 = awaitItem() // Should contain the prompt part
            assertTrue(response3.contains("試したいプロンプト"), 
                      "Third response should contain prompt part, got: $response3")
            
            awaitComplete()
        }
    }

    @Test
    fun `summarizeText should return error when model not available`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns null

        // When
        val flow = repository.summarizeText("Long text to summarize")

        // Then
        flow.test {
            assertEquals("Error: Gemini Nano not initialized", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `summarizeText should generate summary with correct prompt`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns mockGenerativeModel
        
        val mockResponse1 = mockk<GenerateContentResponse>()
        val mockResponse2 = mockk<GenerateContentResponse>()
        every { mockResponse1.text } returns "This"
        every { mockResponse2.text } returns " is summary."
        
        every { mockGenerativeModel.generateContentStream(any<String>()) } returns 
            flowOf<GenerateContentResponse>(mockResponse1, mockResponse2)

        // When
        val flow = repository.summarizeText("Long text to summarize")

        // Then
        flow.test {
            assertEquals("This", awaitItem())
            assertEquals(" is summary.", awaitItem())
            awaitComplete()
        }
        
        verify { 
            mockGenerativeModel.generateContentStream(match<String> { prompt ->
                prompt.contains("要約") || prompt.lowercase().contains("summarize")
            })
        }
    }

    @Test
    fun `proofreadText should return error when model not available`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns null

        // When
        val flow = repository.proofreadText("Text with errors")

        // Then
        flow.test {
            assertEquals("Error: Gemini Nano not initialized", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `proofreadText should generate corrections with correct prompt`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns mockGenerativeModel
        
        val mockResponse1 = mockk<GenerateContentResponse>()
        val mockResponse2 = mockk<GenerateContentResponse>()
        every { mockResponse1.text } returns "hello"
        every { mockResponse2.text } returns " world!"
        
        every { mockGenerativeModel.generateContentStream(any<String>()) } returns 
            flowOf<GenerateContentResponse>(mockResponse1, mockResponse2)

        // When
        val flow = repository.proofreadText("helo wrold!")

        // Then
        flow.test {
            assertEquals("hello", awaitItem())
            assertEquals(" world!", awaitItem())
            awaitComplete()
        }
        
        verify { 
            mockGenerativeModel.generateContentStream(match<String> { prompt ->
                prompt.contains("校正")
            })
        }
    }

    @Test
    fun `summarizeText should fallback to mock on exception`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns mockGenerativeModel
        every { mockGenerativeModel.generateContentStream(any<String>()) } throws RuntimeException("Model overloaded")
        
        // When
        val flow = repository.summarizeText("Long text")

        // Then - Should fallback to mock response
        flow.test {
            // Mock response is split by "。" so we get multiple items
            val response1 = awaitItem() // Should contain "【モック】"
            assertTrue(response1.contains("【モック】") && response1.contains("要約"), 
                      "First response should contain mock summarization, got: $response1")
            
            val response2 = awaitItem() // Should contain the remaining part
            assertTrue(response2.contains("実際のGemini Nanoの使用"), 
                      "Second response should contain continuation, got: $response2")
            
            awaitComplete()
        }
    }

    @Test
    fun `proofreadText should fallback to mock on exception`() = runTest {
        // Given
        coEvery { mockModelManager.getOrCreateModel() } returns mockGenerativeModel
        every { mockGenerativeModel.generateContentStream(any<String>()) } throws RuntimeException("Service unavailable")

        // When
        val flow = repository.proofreadText("Text with errors")

        // Then - Should fallback to mock response
        flow.test {
            // Mock response is split by "。" so we get multiple items
            val response1 = awaitItem() // Should contain "【モック】"
            assertTrue(response1.contains("【モック】") && response1.contains("校正"), 
                      "First response should contain mock proofreading, got: $response1")
            
            val response2 = awaitItem() // Should contain the remaining part
            assertTrue(response2.contains("実際のGemini Nanoの使用"), 
                      "Second response should contain continuation, got: $response2")
            
            awaitComplete()
        }
    }
}