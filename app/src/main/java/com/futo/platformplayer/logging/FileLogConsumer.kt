package com.futo.platformplayer.logging

import android.util.Log
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event1
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentLinkedQueue

class FileLogConsumer : ILogConsumer, Closeable {
    private var _logThread: Thread? = null;
    private var _shouldSubmitLogs = false;
    private val _linesToWrite = ConcurrentLinkedQueue<String>();
    private var _writer: BufferedWriter? = null;
    private var _running: Boolean = false;
    private var _file: File;
    private val _level: LogLevel;

    constructor(file: File, level: LogLevel, append: Boolean) {
        _file = file;
        _level = level;

        if (level.value < LogLevel.ERROR.value) {
            throw Exception("Do not use a file logger with log level NONE.");
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        _writer = BufferedWriter(FileWriter(file, append))
        val t = Thread {
            Log.i(TAG, "Started log writer.");

            while (_running) {
                Thread.sleep(1000);

                try {
                    if (_shouldSubmitLogs) {
                        submitLogs();
                    }

                    while (_linesToWrite.isNotEmpty()) {
                        val todo = _linesToWrite.remove()
                        _writer?.appendLine(todo);
                    }

                    _writer?.flush();
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to process logs.", e);
                }
            }

            Log.i(TAG, "Stopped log writer.");
        }
        t.start();

        _logThread = t;
        _running = true;
    }


    override fun willConsume(level: LogLevel, tag: String): Boolean {
        return level.value <= _level.value;
    }

    override fun consume(level: LogLevel, tag: String, text: String?, e: Throwable?) {
        _linesToWrite.add(Logging.buildLogString(level, tag, text, e));
    }

    fun submitLogs() {
        val id = Logging.submitLog(_file);
        _shouldSubmitLogs = false;
        Logger.onLogSubmitted.emit(id)
    }

    fun submitLogsAsync() {
        _shouldSubmitLogs = true;
    }

    override fun close() {
        Log.i(TAG, "Requesting log writer exit.");

        _running = false;
        _writer?.close();
        _writer = null;
        //_logThread?.join();
        _logThread = null;
    }

    companion object {
        private const val TAG = "FileLogConsumer"
    }
}