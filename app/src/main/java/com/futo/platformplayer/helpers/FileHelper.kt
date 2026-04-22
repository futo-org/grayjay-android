package com.futo.platformplayer.helpers

import java.text.Normalizer

class FileHelper {
    companion object {

        fun String.sanitizeFileName(allowSpace: Boolean = false): String {
            val normalized = Normalizer.normalize(this, Normalizer.Form.NFC)

            val cleaned = buildString(normalized.length) {
                for (ch in normalized) {
                    when {
                        ch == '\u0000' -> {}
                        Character.isISOControl(ch) -> {}
                        ch == '/' || ch == '\\' || ch == ':' || ch == '*' ||
                                ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|' -> append('_')
                        ch == ' ' && !allowSpace -> append('_')
                        else -> append(ch)
                    }
                }
            }

            val collapsed = if (allowSpace) {
                cleaned.replace(Regex("""\s+"""), " ")
            } else {
                cleaned.replace(Regex("""\s+"""), "_")
            }

            return collapsed
                .trim()
                .trimEnd('.')
        }
    }
}