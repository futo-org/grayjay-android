package com.futo.platformplayer.tv.install

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TvInstallStore(context: Context) {
    private val _file = AtomicFile(File(context.noBackupFilesDir, "tv-devices.json"))
    private val _json = Json { ignoreUnknownKeys = true }

    fun load(): List<TvInstallDevice> {
        if (!_file.baseFile.exists())
            return emptyList()
        return _json.decodeFromString<List<TvInstallDevice>>(_file.readFully().decodeToString()).also { devices ->
            devices.forEach { it.endpoint }
        }
    }

    fun save(devices: List<TvInstallDevice>) {
        val stream = _file.startWrite()
        try {
            stream.write(_json.encodeToString(devices).encodeToByteArray())
            _file.finishWrite(stream)
        } catch (error: Exception) {
            _file.failWrite(stream)
            throw error
        }
    }
}
