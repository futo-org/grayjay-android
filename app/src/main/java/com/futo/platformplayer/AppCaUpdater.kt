package com.futo.platformplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppCaUpdater {
    private const val CA_URL = "https://curl.se/ca/cacert.pem"
    private const val CACHE_FILENAME = "curl-ca-bundle.pem"
    private const val MAX_AGE_DAYS = 30

    suspend fun ensureCaBundle(context: Context): File = withContext(Dispatchers.IO) {
        val file = File(context.noBackupFilesDir, CACHE_FILENAME)
        val needsUpdate = !file.exists() || isOlderThanDays(file, MAX_AGE_DAYS)
        if (needsUpdate) {
            downloadToFile(CA_URL, file)
        }
        return@withContext file
    }

    private fun isOlderThanDays(file: File, days: Int): Boolean {
        val ageMs = System.currentTimeMillis() - file.lastModified()
        return ageMs > days * 24L * 60L * 60L * 1000L
    }

    private fun downloadToFile(urlStr: String, dest: File) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { input ->
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
    }
}
