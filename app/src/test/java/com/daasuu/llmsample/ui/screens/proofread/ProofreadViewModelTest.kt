package com.daasuu.llmsample.ui.screens.proofread

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

class ProofreadViewModelTest : TestBase() {

    private lateinit var mockLlmManager: LLMManager
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var mockInterferenceMonitor: InterferenceMonitor
    private lateinit var viewModel: ProofreadViewModel

    @Before
    override fun setUp() {
        super.setUp()
        mockLlmManager = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        mockInterferenceMonitor = mockk(relaxed = true)

        // Setup default mock behaviors
        every { mockPreferencesManager.currentProvider } returns flowOf(LLMProvider.LITE_RT)

        viewModel = ProofreadViewModel(
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

        viewModel.isLoading.test {
            assertFalse(awaitItem())
        }

        viewModel.correctedText.test {
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `init should set current provider from preferences`() = runTest {
        // Given
        val expectedProvider = LLMProvider.GEMINI_NANO
        every { mockPreferencesManager.currentProvider } returns flowOf(expectedProvider)

        // When
        val newViewModel = ProofreadViewModel(
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor
        )

        // Then
        coVerify { mockLlmManager.setCurrentProvider(expectedProvider) }
    }

    @Test
    fun `updateInputText should update input text value`() = runTest {
        // When
        viewModel.updateInputText("Test input")

        // Then
        viewModel.inputText.test {
            assertEquals("Test input", awaitItem())
        }
    }

    @Test
    fun `updateInputText with short text should clear corrections and corrected text`() = runTest {
        // When
        viewModel.updateInputText("short")

        // Then
        viewModel.inputText.test {
            assertEquals("short", awaitItem())
        }

        viewModel.correctedText.test {
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `updateInputText with long text should trigger proofreading after debounce`() = runTest {
        // Given
        val longText = "This is a long text that should trigger proofreading automatically"
        coEvery { mockLlmManager.proofreadText(longText) } returns flowOf("Corrected text")

        // When
        viewModel.updateInputText(longText)

        // Wait for debounce and processing
        kotlinx.coroutines.delay(1000)

        // Then
        coVerify { mockLlmManager.proofreadText(longText) }
    }

    @Test
    fun `proofread should record user action and trigger proofreading`() = runTest {
        // Given
        val inputText = "This text needs proofreading"
        coEvery { mockLlmManager.proofreadText(inputText) } returns flowOf("Corrected text")

        viewModel.updateInputText(inputText)

        // When
        viewModel.proofread()

        // Then
        verify { mockInterferenceMonitor.recordUserAction(UserActionType.PROOFREADING) }
        coVerify { mockLlmManager.proofreadText(inputText) }
    }

    @Test
    fun `proofread should update corrected text when LLM responds`() = runTest {
        // Given
        val inputText = "This text needs proofreading"
        val expectedCorrectedText = "This text needs proofreading."
        coEvery { mockLlmManager.proofreadText(inputText) } returns flowOf("This", " text", " needs", " proofreading", ".")

        viewModel.updateInputText(inputText)

        // When
        viewModel.proofread()

        // Wait for processing
        kotlinx.coroutines.delay(100)

        // Then
        viewModel.correctedText.test {
            assertEquals(expectedCorrectedText, awaitItem())
        }
    }

    @Test
    fun `proofread should handle empty input gracefully`() = runTest {
        // Given
        viewModel.updateInputText("")

        // When
        viewModel.proofread()

        // Then
        verify { mockInterferenceMonitor.recordUserAction(UserActionType.PROOFREADING) }
        // LLM should not be called with empty input
        coVerify(exactly = 0) { mockLlmManager.proofreadText(any()) }
    }

    @Test
    fun `proofread should handle LLM exceptions gracefully`() = runTest {
        // Given
        val inputText = "This text will cause an error"
        coEvery { mockLlmManager.proofreadText(inputText) } throws RuntimeException("LLM Error")

        viewModel.updateInputText(inputText)

        // When
        viewModel.proofread()

        // Wait for processing
        kotlinx.coroutines.delay(100)

        // Then
        viewModel.isLoading.test {
            assertFalse(awaitItem()) // Loading should be false after error
        }

        viewModel.correctedText.test {
            assertEquals("", awaitItem()) // Corrected text should be empty on error
        }
    }

    @Test
    fun `proofread should cancel previous job when called multiple times`() = runTest {
        // Given
        val inputText1 = "First text to proofread"
        val inputText2 = "Second text to proofread"
        coEvery { mockLlmManager.proofreadText(any()) } returns flowOf("Corrected text")

        // When
        viewModel.updateInputText(inputText1)
        viewModel.proofread()
        
        viewModel.updateInputText(inputText2)
        viewModel.proofread()

        // Allow processing to complete
        kotlinx.coroutines.delay(100)

        // Then
        // Should be called for both texts
        coVerify { mockLlmManager.proofreadText(inputText1) }
        coVerify { mockLlmManager.proofreadText(inputText2) }
    }

    @Test
    fun `updateInputText should cancel previous auto-proofread when new text is entered`() = runTest {
        // Given
        val longText1 = "This is the first long text that should trigger automatic proofreading"
        val longText2 = "This is the second long text that should cancel the first proofreading"
        coEvery { mockLlmManager.proofreadText(longText2) } returns flowOf("Corrected text")

        // When
        viewModel.updateInputText(longText1)
        // Immediately update with new text before debounce completes
        viewModel.updateInputText(longText2)

        // Wait for debounce and processing
        kotlinx.coroutines.delay(1000)

        // Then
        // Should only proofread the second text due to cancellation
        coVerify(exactly = 0) { mockLlmManager.proofreadText(longText1) }
        coVerify { mockLlmManager.proofreadText(longText2) }
    }

    @Test
    fun `provider changes should trigger llm manager update`() = runTest {
        // Given
        val newProvider = LLMProvider.LLAMA_CPP
        every { mockPreferencesManager.currentProvider } returns flowOf(newProvider)

        // When
        val newViewModel = ProofreadViewModel(
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor
        )

        // Then
        coVerify { mockLlmManager.setCurrentProvider(newProvider) }
    }
}