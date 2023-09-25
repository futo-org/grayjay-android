package com.futo.platformplayer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.*

@ExperimentalCoroutinesApi
class ExtensionsCoroutineTests {
    @Test
    fun testAwaitFirstEmptyList() = runTest {
        val deferredList = emptyList<CompletableDeferred<String>>()
        val result = deferredList.awaitFirst()
        assertNull(result)
    }

    @Test
    fun testAwaitFirstDeferredEmptyList() = runTest {
        val deferredList = emptyList<CompletableDeferred<String>>()

        assertFailsWith<IllegalArgumentException> {
            deferredList.awaitFirstDeferred()
        }
    }

    @Test
    fun testAwaitFirstNotNullDeferredEmptyList() = runTest {
        val deferredList = emptyList<CompletableDeferred<String?>>()

        val result = deferredList.awaitFirstNotNullDeferred()
        assertNull(result)
    }

    @Test
    fun testAwaitFirstNotNullDeferredAllNull() = runTest {
        val firstDeferred = completedDeferred<String?>(null)
        val secondDeferred = completedDeferred<String?>(null)
        val thirdDeferred = completedDeferred<String?>(null)
        val deferredList = listOf(firstDeferred, secondDeferred, thirdDeferred)

        val result = deferredList.awaitFirstNotNullDeferred()
        assertNull(result)
    }

    @Test
    fun testAwaitFirstExceptionInCoroutine() = runTest {
        val deferredList = listOf(
            CompletableDeferred<String>().apply { completeExceptionally(IllegalArgumentException("Test exception")) },
            CompletableDeferred("Second"),
            CompletableDeferred("Third")
        )

        assertFailsWith<IllegalArgumentException> {
            deferredList.awaitFirst()
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `awaitFirstNotNullDeferred should return first Deferred not null`() = runTest {
        val deferreds: MutableList<Deferred<Int?>> = mutableListOf(
            async { delay(300); null },
            async { delay(100); null },
            async { delay(200); 3 },
            async { delay(50); null }
        )
        val result = deferreds.awaitFirstNotNullDeferred()
        assertNotNull(result)
        assertEquals(3, result!!.second)
    }

    @Test
    fun `awaitFirst should return first completed Deferred`() = runTest {
        val deferreds: MutableList<Deferred<Int>> = mutableListOf(
            async { delay(300); 1 },
            async { delay(100); 2 },
            async { delay(200); 3 },
            async { delay(50); 4 }
        )
        val result = deferreds.awaitFirst()
        assertNotNull(result)
        assertEquals(4, result)
    }

    private fun <T> completedDeferred(v: T): CompletableDeferred<T> {
        return CompletableDeferred<T>(null).apply { complete(v) };
    }
}
