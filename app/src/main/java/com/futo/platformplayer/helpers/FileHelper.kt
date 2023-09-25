package com.futo.platformplayer.helpers

class FileHelper {
    companion object {
        val allowedCharacters = HashSet("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz-.".toCharArray().toList());


        fun String.sanitizeFileName(): String {
            return this.filter { allowedCharacters.contains(it) };
        }
    }
}