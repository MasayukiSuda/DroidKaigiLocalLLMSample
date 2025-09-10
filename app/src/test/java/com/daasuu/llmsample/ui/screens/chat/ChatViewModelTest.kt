package com.daasuu.llmsample.ui.screens.chat

import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.benchmark.InterferenceMonitor
import com.daasuu.llmsample.data.model.ChatMessage
import com.daasuu.llmsample.data.preferences.PreferencesManager
import com.daasuu.llmsample.domain.LLMManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class ChatViewModelTest : TestBase() {

    private lateinit var mockLlmManager: LLMManager
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var mockInterferenceMonitor: InterferenceMonitor
    private lateinit var viewModel: ChatViewModel

    @Before
    override fun setUp() {
        super.setUp()
        mockLlmManager = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        mockInterferenceMonitor = mockk(relaxed = true)
        
        viewModel = ChatViewModel(
            llmManager = mockLlmManager,
            preferencesManager = mockPreferencesManager,
            interferenceMonitor = mockInterferenceMonitor
        )
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Then
        viewModel.messages.test {
            val initialMessages = awaitItem()
            assertTrue(initialMessages.isEmpty())
        }
    }

    @Test
    fun `sendMessage should add user and AI messages`() = runTest {
        // Given
        val userInput = "Hello!"
        val aiResponse = "Hi there!"
        
        viewModel.updateInputText(userInput)
        coEvery { mockLlmManager.generateChatResponse(userInput) } returns flowOf(aiResponse)

        // When
        viewModel.sendMessage()

        // Then
        viewModel.messages.test {
            val messages = awaitItem()
            assertEquals(2, messages.size)
            
            // Check user message
            assertEquals(userInput, messages[0].content)
            assertTrue(messages[0].isUser)
            
            // Check AI response
            assertEquals(aiResponse, messages[1].content)
            assertFalse(messages[1].isUser)
        }
    }

    @Test
    fun `updateInputText should update input text`() = runTest {
        // Given
        val newText = "Hello World"

        // When
        viewModel.updateInputText(newText)

        // Then
        viewModel.inputText.test {
            assertEquals(newText, awaitItem())
        }
    }

    @Test
    fun `sendMessage with empty input should not call LLMManager`() = runTest {
        // Given
        viewModel.updateInputText("")

        // When
        viewModel.sendMessage()

        // Then
        coVerify(exactly = 0) { mockLlmManager.generateChatResponse(any()) }
        
        viewModel.messages.test {
            val messages = awaitItem()
            assertTrue(messages.isEmpty())
        }
    }
}