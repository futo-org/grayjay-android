package com.futo.platformplayer.logging

import com.futo.platformplayer.constructs.Event1

enum class LogLevel(val value: Int) {
    NONE(0),
    ERROR(1),
    WARNING(2),
    INFORMATION(3),
    VERBOSE(4);

    companion object {
        fun fromInt(value: Int): LogLevel {
            return when (value) {
                0 -> NONE
                1 -> ERROR
                2 -> WARNING
                3 -> INFORMATION
                4 -> VERBOSE
                else -> throw IllegalArgumentException("Invalid LogLevel value: $value")
            }
        }
    }
}

interface ILogConsumer {
    fun willConsume(level: LogLevel, tag: String) : Boolean;
    fun consume(level: LogLevel, tag: String, text: String?, e: Throwable? = null);
}

class Logger {
    companion object {
        private const val TAG = "Logger";

        private var _logConsumers = emptyList<ILogConsumer>();

        val onLogSubmitted = Event1<String?>();

        val hasConsumers: Boolean get() = !_logConsumers.isEmpty();

        fun setLogConsumers(logConsumers: List<ILogConsumer>) {
            _logConsumers = logConsumers;
        }

        fun i(tag: String, e: Throwable? = null, text: () -> String) { log(LogLevel.INFORMATION, tag, e, text); }
        fun e(tag: String, e: Throwable? = null, text: () -> String?) { log(LogLevel.ERROR, tag, e, text); }
        fun w(tag: String, e: Throwable? = null, text: () -> String?) { log(LogLevel.WARNING, tag, e, text); }
        fun v(tag: String, e: Throwable? = null, text: () -> String?) { log(LogLevel.VERBOSE, tag, e, text); }

        fun i(tag: String, text: String, e: Throwable? = null) { log(LogLevel.INFORMATION, tag, text, e); }
        fun e(tag: String, text: String?, e: Throwable? = null) { log(LogLevel.ERROR, tag, text, e); }
        fun w(tag: String, text: String?, e: Throwable? = null) { log(LogLevel.WARNING, tag, text, e); }
        fun v(tag: String, text: String?, e: Throwable? = null) { log(LogLevel.VERBOSE, tag, text, e); }

        fun submitLogs(): Boolean {
            var loggingEnabled = false;
            for (logConsumer in _logConsumers) {
                if (logConsumer is FileLogConsumer) {
                    logConsumer.submitLogs();
                    loggingEnabled = true;
                }
            }
            return loggingEnabled;
        }

        fun submitLogsAsync(): Boolean {
            var loggingEnabled = false;
            for (logConsumer in _logConsumers) {
                if (logConsumer is FileLogConsumer) {
                    logConsumer.submitLogsAsync();
                    loggingEnabled = true;
                }
            }
            return loggingEnabled;
        }

        private fun log(level: LogLevel, tag: String, e: Throwable? = null, textBuilder: () -> String?) {
            if (!_logConsumers.any { c -> c.willConsume(level, tag) }) {
                return;
            }

            log(level, tag, textBuilder(), e);
        }

        private fun log(level: LogLevel, tag: String, text: String?, e: Throwable? = null) {
            _logConsumers.forEach { c -> c.consume(level, tag, text, e) };
        }
    }
}