package com.futo.platformplayer.helpers

object VttHelper {
    private val tagRegex = Regex("<(/?)([^\\s>./]*)[^>]*>")
    private val supportedTags = setOf("b", "i", "u", "c", "v", "lang", "ruby", "rt")

    fun isVtt(contentType: String?, text: String): Boolean =
        contentType?.contains("vtt", ignoreCase = true) == true || text.trimStart('﻿').startsWith("WEBVTT")

    fun stripUnsupportedTags(text: String): String {
        if (!text.contains('<')) return text
        return text.split('\n').joinToString("\n") { line ->
            if (!line.contains('<') || line.contains("-->")) line
            else tagRegex.replace(line) { m ->
                if (m.groupValues[1].isEmpty() && m.value.length > 1 && m.value[1].isDigit()) m.value
                else if (supportedTags.contains(m.groupValues[2].lowercase())) m.value
                else ""
            }
        }
    }

    fun clean(contentType: String?, text: String): String =
        if (isVtt(contentType, text)) stripUnsupportedTags(text) else text

    fun clean(contentType: String?, bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.UTF_8)
        if (!isVtt(contentType, text)) return bytes
        val cleaned = stripUnsupportedTags(text)
        return if (cleaned === text || cleaned == text) bytes else cleaned.toByteArray(Charsets.UTF_8)
    }
}
