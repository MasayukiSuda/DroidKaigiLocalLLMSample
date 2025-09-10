package com.daasuu.llmsample.ui.screens.summarize

import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.benchmark.InterferenceMonitor
import com.daasuu.llmsample.data.benchmark.UserActionType
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.data.preferences.PreferencesManager
import com.daasuu.llmsample.domain.LLMManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class SummarizeViewModelTest : TestBase() {

    private lateinit var mockLlmManager: LLMManager
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var mockInterferenceMonitor: InterferenceMonitor
    private lateinit var viewModel: SummarizeViewModel

    @Before
    override fun setUp() {
        super.setUp()
        mockLlmManager = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        mockInterferenceMonitor = mockk(relaxed = true)
        
        // Mock preferences manager to return a provider flow
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LITE_RT)
        
        viewModel = SummarizeViewModel(
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor
        )
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Then
        viewModel.inputText.test {
            assertEquals("", awaitItem())
        }
        
        viewModel.summaryText.test {
            assertEquals("", awaitItem())
        }
        
        viewModel.isLoading.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `init should set current provider from preferences`() = runTest {
        // Given - already set up in @Before
        
        // Then - verify LLMManager.setCurrentProvider was called
        coVerify { mockLlmManager.setCurrentProvider(LLMProvider.LITE_RT) }
    }

    @Test
    fun `updateInputText should update input text`() = runTest {
        // When
        viewModel.updateInputText("Test input")
        
        // Then
        viewModel.inputText.test {
            assertEquals("Test input", awaitItem())
        }
    }

    @Test
    fun `updateInputText with short text should clear summary`() = runTest {
        // Given - set some initial summary
        viewModel.updateInputText("Long text that triggers summarization automatically")
        
        // When - update with short text
        viewModel.updateInputText("Short")
        
        // Then
        viewModel.summaryText.test {
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `updateInputText with long text should trigger automatic summarization`() = runTest {
        // Given
        val longText = "This is a very long text that should trigger automatic summarization because it has more than 50 characters"
        coEvery { mockLlmManager.summarizeText(longText) } returns flowOf("Automatic summary")
        
        // When
        viewModel.updateInputText(longText)
        
        // Wait for debounce and processing
        kotlinx.coroutines.delay(600)
        
        // Then
        viewModel.summaryText.test {
            assertEquals("Automatic summary", awaitItem())
        }
        
        coVerify { mockLlmManager.summarizeText(longText) }
    }

    @Test
    fun `summarize should record user action and trigger summarization`() = runTest {
        // Given
        val inputText = "Test text to summarize"
        viewModel.updateInputText(inputText)
        coEvery { mockLlmManager.summarizeText(inputText) } returns flowOf("Test", " summary")
        
        // When
        viewModel.summarize()
        
        // Then
        verify { mockInterferenceMonitor.recordUserAction(UserActionType.SUMMARIZATION) }
        
        // Wait for processing
        kotlinx.coroutines.delay(100)
        
        viewModel.summaryText.test {
            assertEquals("Test summary", awaitItem())
        }
        
        coVerify { mockLlmManager.summarizeText(inputText) }
    }

    @Test
    fun `summarize should handle streaming response correctly`() = runTest {
        // Given
        val inputText = "Test input"
        viewModel.updateInputText(inputText)
        coEvery { mockLlmManager.summarizeText(inputText) } returns flowOf("First", " part", " of summary")
        
        // When
        viewModel.summarize()
        
        // Wait for processing
        kotlinx.coroutines.delay(100)
        
        // Then
        viewModel.summaryText.test {
            assertEquals("First part of summary", awaitItem())
        }
    }

    @Test
    fun `summarize should handle errors gracefully`() = runTest {
        // Given
        val inputText = "Test input"
        viewModel.updateInputText(inputText)
        coEvery { mockLlmManager.summarizeText(inputText) } throws RuntimeException("Test error")
        
        // When
        viewModel.summarize()
        
        // Wait for processing
        kotlinx.coroutines.delay(100)
        
        // Then
        viewModel.summaryText.test {
            val result = awaitItem()
            assertTrue(result.startsWith("Error:"))
            assertTrue(result.contains("Test error"))
        }
    }

    @Test
    fun `summarize should not process when input is blank`() = runTest {
        // Given
        viewModel.updateInputText("")
        
        // When
        viewModel.summarize()
        
        // Then
        coVerify(exactly = 0) { mockLlmManager.summarizeText(any()) }
        
        viewModel.summaryText.test {
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `isLoading should be true during summarization`() = runTest {
        // Given
        val inputText = "Test input"
        viewModel.updateInputText(inputText)
        
        // Create a flow that takes some time to complete
        coEvery { mockLlmManager.summarizeText(inputText) } returns kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(50)
            emit("Result")
        }
        
        // When
        viewModel.summarize()
        
        // Wait a moment for the loading state to change
        kotlinx.coroutines.delay(10)
        
        // Then - check loading state changes
        viewModel.isLoading.test {
            assertEquals(true, awaitItem())  // During processing
            assertEquals(false, awaitItem()) // After completion
        }
    }

    @Test
    fun `clearAll should reset all values and cancel job`() = runTest {
        // Given
        val inputText = "Test input"
        viewModel.updateInputText(inputText)
        coEvery { mockLlmManager.summarizeText(inputText) } returns flowOf("Test summary")
        viewModel.summarize()
        
        // Wait for some processing
        kotlinx.coroutines.delay(50)
        
        // When
        viewModel.clearAll()
        
        // Then
        viewModel.inputText.test {
            assertEquals("", awaitItem())
        }
        
        viewModel.summaryText.test {
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `updateInputText should cancel previous summarization job`() = runTest {
        // Given
        val longText1 = "This is the first long text that should trigger automatic summarization"
        val longText2 = "This is the second long text that should trigger automatic summarization"
        
        coEvery { mockLlmManager.summarizeText(longText1) } returns flowOf("First summary")
        coEvery { mockLlmManager.summarizeText(longText2) } returns flowOf("Second summary")
        
        // When
        viewModel.updateInputText(longText1)
        kotlinx.coroutines.delay(100) // Wait a bit
        viewModel.updateInputText(longText2) // This should cancel the first job
        
        // Wait for debounce and processing
        kotlinx.coroutines.delay(600)
        
        // Then - only the second summarization should be called
        coVerify(exactly = 0) { mockLlmManager.summarizeText(longText1) }
        coVerify { mockLlmManager.summarizeText(longText2) }
        
        viewModel.summaryText.test {
            assertEquals("Second summary", awaitItem())
        }
    }

    @Test
    fun `provider changes should update LLMManager`() = runTest {
        // Given
        val newProviderFlow = flowOf(LLMProvider.GEMINI_NANO)
        every { mockPreferencesManager.currentProvider } returns newProviderFlow
        
        // Create new viewModel to trigger init block
        viewModel = SummarizeViewModel(
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor
        )
        
        // Wait for coroutine to process
        kotlinx.coroutines.delay(100)
        
        // Then
        coVerify { mockLlmManager.setCurrentProvider(LLMProvider.GEMINI_NANO) }
    }
}
