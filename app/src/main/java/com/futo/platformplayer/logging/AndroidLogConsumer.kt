package com.futo.platformplayer.logging

import android.util.Log
import com.futo.platformplayer.logging.ILogConsumer
import com.futo.platformplayer.logging.LogLevel

class AndroidLogConsumer : ILogConsumer {
    override fun willConsume(level: LogLevel, tag: String): Boolean {
        return Log.isLoggable(tag, when (level) {
            LogLevel.VERBOSE -> Log.VERBOSE
            LogLevel.INFORMATION -> Log.INFO
            LogLevel.WARNING -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
            else -> throw Exception("Unknown log level")
        });
    }

    override fun consume(level: LogLevel, tag: String, text: String?, e: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> Log.v("INTERNAL;$tag", text, e)
            LogLevel.INFORMATION -> Log.i("INTERNAL;$tag", text, e)
            LogLevel.WARNING -> Log.w("INTERNAL;$tag", text, e)
            LogLevel.ERROR -> Log.e("INTERNAL;$tag", text, e)
            else -> throw Exception("Unknown log level")
        }
    }
}