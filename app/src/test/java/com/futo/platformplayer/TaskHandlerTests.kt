package com.futo.platformplayer

import com.futo.platformplayer.constructs.TaskHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TaskHandlerTest {
    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSuccess() = runTest {
        val taskHandler = TaskHandler({ this }, { param: Int -> param * 2 }, Dispatchers.Main.immediate)

        var result: Int? = null
        taskHandler.success { result = it }
        taskHandler.run(5)
        advanceTimeBy(1000)

        // Ensure the onSuccess callback is called with the correct value
        assertEquals(10, result)
    }

    @Test
    fun testNonLastCancellation() = runTest {
        val taskHandler = TaskHandler({ this }, { param: Int -> delay(500); param * 2 }, Dispatchers.Main.immediate)

        var result: Int? = null
        taskHandler.success {
            if (result != null) {
                throw Exception("Should only be set once")
            }

            result = it
        }
        taskHandler.run(5)
        taskHandler.run(6)
        advanceTimeBy(1000)

        // Ensure the onSuccess callback is not called
        assertEquals(12, result)
    }

    @Test
    fun testException() = runTest {
        val taskHandler = TaskHandler({ this }, { param: Int -> throw Exception("Test exception") }, Dispatchers.Main.immediate)
        var exceptionMessage: String? = null

        taskHandler.exception<Exception> { exceptionMessage = it.message }
        taskHandler.run(5)
        advanceTimeBy(1000)

        // Ensure the exception callback is called with the correct value
        assertEquals("Test exception", exceptionMessage)
    }

    @Test
    fun testCancellation() = runTest {
        val taskHandler = TaskHandler({ this }, { param: Int -> delay(500); param * 2 }, Dispatchers.Main.immediate)

        var result: Int? = null
        taskHandler.success { result = it }
        taskHandler.run(5)
        taskHandler.cancel()
        advanceTimeBy(1000)

        // Ensure the onSuccess callback is not called
        assertNull(result)
    }
}