package com.futo.platformplayer

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
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

    @Test
    fun testDecodeString() {
        assertThrows(IOException::class.java) { "\\u02-3".decodeUnicode() }
        assertEquals("", "".decodeUnicode())
        assertEquals("test", "test".decodeUnicode())
        assertEquals("\ntest\b", "\\ntest\\b".decodeUnicode())
        assertEquals("\u123425foo\ntest\b", "\\u123425foo\\ntest\\b".decodeUnicode())
        assertEquals("'\u000coo\teste\r", "\\'\\u000coo\\teste\\r".decodeUnicode())
        assertEquals("\\", "\\".decodeUnicode())
        assertEquals("\uABCDx", "\\uabcdx".decodeUnicode())
        assertEquals("\uABCDx", "\\uABCDx".decodeUnicode())
        assertEquals("\uABCD", "\\uabcd".decodeUnicode())
        assertEquals("\ud83d\udc80\ud83d\udd14", "\\ud83d\\udc80\\ud83d\\udd14".decodeUnicode())
        assertEquals("String with a slash (/) in it", "String with a slash (/) in it".decodeUnicode())
    }
}