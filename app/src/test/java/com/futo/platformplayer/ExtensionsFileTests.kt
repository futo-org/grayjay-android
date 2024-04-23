package com.futo.platformplayer

import com.futo.platformplayer.helpers.FileHelper.Companion.sanitizeFileName
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExtensionsFileTests {
    @Test
    fun test_sanitizeFileName1() {
        assertEquals("Helloworld", "Hello world".sanitizeFileName());
        assertEquals("Hello world", "Hello world".sanitizeFileName(true));
        assertEquals("漫漫听-点唱-公主冠", "漫漫听-点唱- 公主冠".sanitizeFileName());
        assertEquals("食べる", "食べ る".sanitizeFileName()); //Hiragana
        assertEquals("テレビ", "テレ ビ".sanitizeFileName()); //Katakana
        assertEquals("يخبر", "ي خبر".sanitizeFileName()); //Arabic
        assertEquals("..testing", "../testing".sanitizeFileName()); //Escaping
    }
}
