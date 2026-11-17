package com.futo.platformplayer.tv.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID

class AdbProcess(private val executable: File, private val directory: File) : Closeable {
    private val _socket = File(directory, "${UUID.randomUUID().toString().take(8)}.sock")
    private val _lock = Any()
    private var _generation = 0
    @Volatile
    private var _server: Process? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        if (_server?.isAlive == true)
            return@withContext
        check(executable.isFile && executable.canExecute()) { "ADB executable is missing" }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create ADB directory" }
        require(_socket.absolutePath.toByteArray().size < 104) { "ADB socket path is too long" }
        _socket.delete()
        val generation = synchronized(_lock) { _generation }
        val server = builder(listOf("server", "nodaemon"))
            .redirectOutput(File("/dev/null"))
            .start()
        synchronized(_lock) {
            if (generation != _generation) {
                server.destroyForcibly()
                throw IOException("ADB server start was cancelled")
            }
            _server = server
        }
        try {
            withTimeout(10_000) {
                while (!_socket.exists()) {
                    if (_server?.isAlive != true)
                        throw IOException("ADB server could not start")
                    delay(50)
                }
            }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    suspend fun run(arguments: List<String>, timeoutSeconds: Long = 20, input: String? = null): Result {
        return withContext(Dispatchers.IO) {
            check(_server?.isAlive == true) { "ADB server is not running" }
            coroutineScope {
                val process = builder(arguments).start()
                val output = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { reader ->
                        val text = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0)
                                break
                            text.append(buffer, 0, count)
                            if (text.length > 65_536)
                                text.delete(0, text.length - 65_536)
                        }
                        text.toString()
                    }
                }
                try {
                    process.outputStream.bufferedWriter().use { writer ->
                        if (input != null) {
                            writer.write(input)
                            writer.newLine()
                        }
                    }
                    val finished = runInterruptible { process.waitFor(timeoutSeconds, TimeUnit.SECONDS) }
                    if (!finished)
                        throw IOException("ADB command timed out")
                    Result(process.exitValue(), output.await())
                } finally {
                    process.destroy()
                    if (process.isAlive)
                        process.destroyForcibly()
                }
            }
        }
    }

    override fun close() {
        synchronized(_lock) {
            _generation++
            _server?.destroy()
            _server?.let {
                if (it.isAlive)
                    it.destroyForcibly()
            }
            _server = null
            _socket.delete()
        }
    }

    private fun builder(arguments: List<String>): ProcessBuilder {
        return ProcessBuilder(listOf(executable.absolutePath, "-L", "localfilesystem:${_socket.absolutePath}") + arguments)
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                environment().apply {
                    // ADB stores its identity under HOME/.android; only this child process uses it.
                    put("HOME", directory.absolutePath)
                    put("TMPDIR", directory.absolutePath)
                    put("ADB_MDNS_AUTO_CONNECT", "0")
                    put("ADB_LOCAL_TRANSPORT_MAX_PORT", "0")
                    put("ADB_LIBUSB", "0")
                    remove("ADB_TRACE")
                    remove("ADB_VENDOR_KEYS")
                    remove("ANDROID_SERIAL")
                }
            }
    }

    data class Result(val exitCode: Int, val output: String) {
        val succeeded: Boolean
            get() = exitCode == 0
    }
}
