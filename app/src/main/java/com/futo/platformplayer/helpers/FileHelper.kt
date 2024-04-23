package com.futo.platformplayer.helpers

class FileHelper {
    companion object {
        fun String.sanitizeFileName(allowSpace: Boolean = false): String {
            return this.filter {
                (it in '0' .. '9') ||
                    (it in 'a'..'z') ||
                    (it in 'A'..'Z') ||
                    (it == '-' || it == '.' || it == '_' || (it == ' ' && allowSpace)) ||
                    (it in '丁'..'龤') || //Chinese/Kanji
                    (it in '\u3040'..'\u309f') || //Hiragana
                    (it in '\u30A0'..'\u30ff') || //Katakana
                    (it in '\u0600'..'\u06FF') //Arabic
            }; //Chinese
        }
    }
}