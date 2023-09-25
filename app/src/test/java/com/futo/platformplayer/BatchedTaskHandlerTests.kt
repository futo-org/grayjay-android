package com.futo.platformplayer

import com.futo.platformplayer.constructs.BatchedTaskHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class BatchedTaskHandlerTest {

    private var cache = HashMap<Int, Int>()

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
        cache.clear()
    }

    @Test
    fun testCacheHit() = runTest {
        cache[5] = 10
        val task = BatchedTaskHandler(this, { param: Int -> param * 2 },
            taskGetCache = { param: Int -> cache[param] },
            taskSetCache = { param: Int, result: Int -> cache[param] = result })

        val result = task.execute(5).await()
        assertEquals(10, result)
    }

    @Test
    fun testCacheMiss() = runTest {
        val task = BatchedTaskHandler(this, { param: Int -> param * 2 },
            taskGetCache = { param: Int -> cache[param] },
            taskSetCache = { param: Int, result: Int -> cache[param] = result })

        val result = task.execute(6).await()
        assertEquals(12, result)
    }

    @Test
    fun testException() = runTest {
        val task = BatchedTaskHandler(this, { param: Int -> throw Exception("Test exception") },
            taskGetCache = { param: Int -> cache[param] },
            taskSetCache = { param: Int, result: Int -> cache[param] = result })
        var exceptionMessage: String? = null

        try {
            task.execute(5).await()
        } catch (e: Exception) {
            exceptionMessage = e.message
        }

        assertEquals("Test exception", exceptionMessage)
    }

    @Test
    fun testCancellation() = runTest {
        val task = BatchedTaskHandler(this, { param: Int -> Thread.sleep(500); param * 2 },
            taskGetCache = { param: Int -> cache[param] },
            taskSetCache = { param: Int, result: Int -> cache[param] = result })

        val deferredResult = task.execute(5)
        deferredResult.cancel()
        delay(1000)

        assertTrue(deferredResult.isCancelled)
    }
}
