package com.futo.platformplayer

import com.futo.platformplayer.logging.ILogConsumer
import com.futo.platformplayer.logging.LogLevel
import com.futo.platformplayer.logging.Logger
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class LoggerTests {
    private class TestLogConsumer : ILogConsumer {
        var willConsume = false
        var lastLevel: LogLevel? = null
        var lastTag: String? = null
        var lastText: String? = null
        var lastThrowable: Throwable? = null

        override fun willConsume(level: LogLevel, tag: String): Boolean {
            return willConsume
        }

        override fun consume(level: LogLevel, tag: String, text: String?, e: Throwable?) {
            lastLevel = level
            lastTag = tag
            lastText = text
            lastThrowable = e
        }
    }
    private lateinit var testLogConsumer: TestLogConsumer

    @Before
    fun setup() {
        testLogConsumer = TestLogConsumer()
        Logger.setLogConsumers(listOf(testLogConsumer))
    }

    @Test
    fun `logs information level`() {
        val testMessage = "Test message"
        testLogConsumer.willConsume = true
        Logger.i("TAG", testMessage)
        assertEquals(LogLevel.INFORMATION, testLogConsumer.lastLevel)
        assertEquals("TAG", testLogConsumer.lastTag)
        assertEquals(testMessage, testLogConsumer.lastText)
    }

    @Test
    fun `logs error level`() {
        val testMessage = "Test error message"
        val testException = RuntimeException("Test exception")
        testLogConsumer.willConsume = true
        Logger.e("TAG", testMessage, testException)
        assertEquals(LogLevel.ERROR, testLogConsumer.lastLevel)
        assertEquals("TAG", testLogConsumer.lastTag)
        assertEquals(testMessage, testLogConsumer.lastText)
        assertEquals(testException, testLogConsumer.lastThrowable)
    }

    @Test
    fun `logs warning level`() {
        val testMessage = "Test warning message"
        testLogConsumer.willConsume = true
        Logger.w("TAG", testMessage)
        assertEquals(LogLevel.WARNING, testLogConsumer.lastLevel)
        assertEquals("TAG", testLogConsumer.lastTag)
        assertEquals(testMessage, testLogConsumer.lastText)
    }

    @Test
    fun `logs verbose level`() {
        val testMessage = "Test verbose message"
        testLogConsumer.willConsume = true
        Logger.v("TAG", testMessage)
        assertEquals(LogLevel.VERBOSE, testLogConsumer.lastLevel)
        assertEquals("TAG", testLogConsumer.lastTag)
        assertEquals(testMessage, testLogConsumer.lastText)
    }
}