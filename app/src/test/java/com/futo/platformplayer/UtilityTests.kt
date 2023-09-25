package com.futo.platformplayer

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class UtilityTests {
    private val _allowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ";

    @Test
    fun `getRandomString generates string of correct length and allowed characters`() {

        val length = 10
        val result = getRandomString(length)
        assertEquals(length, result.length)
        assertTrue(result.all { it in _allowedCharacters })
    }

    @Test
    fun `getRandomStringRandomLength generates string of correct length and allowed characters`() {
        val minLength = 5
        val maxLength = 10
        val result = getRandomStringRandomLength(minLength, maxLength)
        assertTrue(result.length in minLength..maxLength)
        assertTrue(result.all { it in _allowedCharacters })
    }

    @Test
    fun `findNonRuntimeException returns non-runtime exception`() {
        val innerException = SocketTimeoutException("Timeout")
        val runtimeException = RuntimeException("Runtime Exception", innerException)
        val result = findNonRuntimeException(runtimeException)
        assertEquals(innerException, result)
    }
}