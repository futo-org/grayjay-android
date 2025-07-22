package com.futo.platformplayer

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import toAndroidColor

class CSSColorTests {
    @Test
    fun test1() {
        val androidHex = "#80336699"
        val androidColorInt = Color.parseColor(androidHex)

        val cssHex = "#33669980"
        val cssColor = CSSColor.parseColor(cssHex)

        assertEquals(
            "CSSColor($cssHex).toAndroidColor() should equal Color.parseColor($androidHex)",
            androidColorInt,
            cssColor.toAndroidColor(),
        )
    }

    @Test
    fun test2() {
        val androidHex = "#123ABC"
        val androidColorInt = Color.parseColor(androidHex)

        val cssHex = "#123ABCFF"
        val cssColor = CSSColor.parseColor(cssHex)

        assertEquals(
            "CSSColor($cssHex).toAndroidColor() should equal Color.parseColor($androidHex)",
            androidColorInt,
            cssColor.toAndroidColor()
        )
    }
}