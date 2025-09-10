package com.daasuu.llmsample.domain

import android.content.Context
import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.benchmark.PerformanceMonitor
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.performance.PerformanceLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class LLMManagerTest : TestBase() {

    private lateinit var mockContext: Context
    private lateinit var mockLiteRtRepository: LLMRepository
    private lateinit var mockLlamaCppRepository: LLMRepository
    private lateinit var mockGeminiNanoRepository: LLMRepository
    private lateinit var mockPerformanceLogger: PerformanceLogger
    private lateinit var mockPerformanceMonitor: PerformanceMonitor
    private lateinit var llmManager: LLMManager

    @Before
    override fun setUp() {
        super.setUp()
        mockContext = mockk(relaxed = true)
        mockLiteRtRepository = mockk(relaxed = true)
        mockLlamaCppRepository = mockk(relaxed = true)
        mockGeminiNanoRepository = mockk(relaxed = true)
        mockPerformanceLogger = mockk(relaxed = true)
        mockPerformanceMonitor = mockk(relaxed = true)

        val repositories = mapOf(
            LLMProvider.LITE_RT to mockLiteRtRepository,
            LLMProvider.LLAMA_CPP to mockLlamaCppRepository,
            LLMProvider.GEMINI_NANO to mockGeminiNanoRepository
        )

        llmManager = LLMManager(
            context = mockContext,
            repositories = repositories,
            performanceLogger = mockPerformanceLogger,
            performanceMonitor = mockPerformanceMonitor
        )
    }

    @Test
    fun `initialize should call repository initialize`() = runTest {
        // Given
        coEvery { mockLiteRtRepository.initialize() } just runs
        coEvery { mockLiteRtRepository.isAvailable() } returns true

        // When
        llmManager.initialize(LLMProvider.LITE_RT)

        // Then
        coVerify { mockLiteRtRepository.initialize() }
    }

    @Test
    fun `generateChatResponse should return flow from repository`() = runTest {
        // Given
        val prompt = "Hello, how are you?"
        val expectedResponse = "I'm doing well, thank you!"

        coEvery { mockLiteRtRepository.isAvailable() } returns true
        coEvery { mockLiteRtRepository.initialize() } just runs
        coEvery { mockLiteRtRepository.generateChatResponse(prompt) } returns flowOf(
            expectedResponse
        )

        llmManager.initialize(LLMProvider.LITE_RT)

        // When & Then
        llmManager.generateChatResponse(prompt).test {
            assertEquals(expectedResponse, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `summarizeText should return flow from repository`() = runTest {
        // Given
        val text = "This is a long text that needs to be summarized."
        val expectedSummary = "Short summary"

        coEvery { mockLiteRtRepository.isAvailable() } returns true
        coEvery { mockLiteRtRepository.initialize() } just runs
        coEvery { mockLiteRtRepository.summarizeText(text) } returns flowOf(expectedSummary)

        llmManager.initialize(LLMProvider.LITE_RT)

        // When & Then
        llmManager.summarizeText(text).test {
            assertEquals(expectedSummary, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `proofreadText should return flow from repository`() = runTest {
        // Given
        val text = "This text has some errors."
        val expectedCorrected = "This text has some errors."

        coEvery { mockGeminiNanoRepository.isAvailable() } returns true
        coEvery { mockGeminiNanoRepository.initialize() } just runs
        coEvery { mockGeminiNanoRepository.proofreadText(text) } returns flowOf(expectedCorrected)

        llmManager.initialize(LLMProvider.GEMINI_NANO)

        // When & Then
        llmManager.proofreadText(text).test {
            assertEquals(expectedCorrected, awaitItem())
            awaitComplete()
        }
    }
}