package com.futo.platformplayer.debug

import com.futo.platformplayer.logging.Logger


class Stopwatch {
    private var startTime = System.nanoTime()

    val elapsedMs: Double get() {
        val now = System.nanoTime()
        val diff = now - startTime
        return diff / 1000000.0
    }

    fun reset() {
        startTime = System.nanoTime()
    }

    fun logAndNext(tag: String, message: String): Long {
        val now = System.nanoTime()
        val diff = now - startTime
        val diffMs = diff / 1000000.0
        Logger.i(tag, "STOPWATCH $message ${diffMs}ms")
        startTime = now
        return diff
    }
}