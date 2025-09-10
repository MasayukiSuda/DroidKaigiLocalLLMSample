package com.daasuu.llmsample.data.llm.task

import android.content.Context
import app.cash.turbine.test
import com.daasuu.llmsample.TestBase
import com.daasuu.llmsample.data.preferences.PreferencesManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.*

class TaskRepositoryTest : TestBase() {

    private lateinit var mockContext: Context
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var repository: TaskRepository

    @Before
    override fun setUp() {
        super.setUp()
        mockContext = mockk(relaxed = true)
        mockPreferencesManager = mockk(relaxed = true)
        
        repository = TaskRepository(mockContext, mockPreferencesManager)
    }

    @Test
    fun `initialize should work without errors`() = runTest {
        // When
        repository.initialize()

        // Then - Should not throw exception
        // isAvailable depends on MediaPipe availability, which won't be available in tests
        // So we just verify the call doesn't crash
        assertNotNull(repository)
    }

    @Test
    fun `release should work without errors`() = runTest {
        // Given
        repository.initialize()

        // When
        repository.release()

        // Then - Should not throw exception
        assertNotNull(repository)
    }

    @Test
    fun `isAvailable should return false initially`() = runTest {
        // When & Then
        assertFalse(repository.isAvailable())
    }

    @Test
    fun `generateChatResponse should return error when initialization fails`() = runTest {
        // Given
        repository.initialize()  // This will fail due to missing MediaPipe model files

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then - Should get error response since initialization fails
        flow.test {
            val response = awaitItem()
            assertEquals("Error: .task model not found", response)
            awaitComplete()
        }
    }

    @Test
    fun `generateChatResponse should return error when not initialized`() = runTest {
        // Given - no initialization

        // When
        val flow = repository.generateChatResponse("Hello")

        // Then - Should get error response
        flow.test {
            val response = awaitItem()
            assertEquals("Error: .task model not found", response)
            awaitComplete()
        }
    }

    @Test
    fun `summarizeText should return error when not initialized`() = runTest {
        // Given - no initialization

        // When
        val flow = repository.summarizeText("Long text to summarize")

        // Then - Should get error response
        flow.test {
            val response = awaitItem()
            assertEquals("Error: .task model not found", response)
            awaitComplete()
        }
    }

    @Test
    fun `proofreadText should return empty json when not initialized`() = runTest {
        // Given - no initialization

        // When
        val flow = repository.proofreadText("Text with errors")

        // Then - Should get empty JSON response  
        flow.test {
            val response = awaitItem()
            assertEquals("{}", response)
            awaitComplete()
        }
    }

}