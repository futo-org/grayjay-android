package com.futo.platformplayer.logging

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class Logging {

    companion object {
        val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }


        //TODO: Why not just stackTraceToString()
        private fun throwableToString(t: Throwable): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            return "${t.message}\n${sw}"
        }

        fun buildLogString(logLevel: LogLevel, tag: String, text: String?, e: Throwable? = null): String {
            val currentDate = Date(System.currentTimeMillis());
            val timestamp = logDateFormat.format(currentDate);

            val levelString = when (logLevel) {
                LogLevel.ERROR -> "e"
                LogLevel.WARNING -> "w"
                LogLevel.INFORMATION -> "i"
                LogLevel.VERBOSE -> "v"
                else -> throw Exception("Invalid log level $logLevel")
            }

            if (e != null) {
                return "($levelString, $tag, ${timestamp}): ${text ?: ""}\n${throwableToString(e)}";
            } else {
                return "($levelString, $tag, ${timestamp}): $text";
            }
        }

        fun submitLog(file: File): String? {
            if (!file.exists()) {
                return null;
            }

            val requestBody: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .build();

            val request: Request = Request.Builder()
                .url("https://logs.grayjay.app/logs")
                //.url("http://192.168.1.231:5413/logs")
                .post(requestBody)
                .build()

            val client = OkHttpClient()
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string();
                return if (body != null) Json.decodeFromString<String>(body) else null;
            } else {
                Logger.e("Failed to submit log.") { "Failed to submit logs (${response.code}): ${response.body?.string()}" };
                return null;
            }
        }
    }

}