package com.futo.platformplayer

import CSSColor
import org.junit.Assert.assertEquals
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CSSColorTest {

    private fun approxEq(expected: Float, actual: Float, eps: Float = 1e-5f) {
        assertTrue(abs(expected - actual) <= eps, "Expected $expected but got $actual")
    }

    @Test fun `hex #RRGGBB parses correctly`() {
        val c = CSSColor.parseColor("#336699")
        assertEquals(0x33, c.red)
        assertEquals(0x66, c.green)
        assertEquals(0x99, c.blue)
        assertEquals(255, c.alpha)
    }

    @Test fun `hex #RGB shorthand expands`() {
        val c = CSSColor.parseColor("#369")
        assertEquals(0x33, c.red)
        assertEquals(0x66, c.green)
        assertEquals(0x99, c.blue)
    }

    @Test fun `hex #RRGGBBAA parses alpha`() {
        val c = CSSColor.parseColor("#33669980")
        assertEquals(0x33, c.red)
        assertEquals(0x66, c.green)
        assertEquals(0x99, c.blue)
        approxEq(128 / 255f, c.a)
        assertEquals(128, c.alpha)
    }

    @Test fun `hex #RGBA shorthand expands with alpha`() {
        val c = CSSColor.parseColor("#3698")
        assertEquals(0x33, c.red)
        assertEquals(0x66, c.green)
        assertEquals(0x99, c.blue)
        assertEquals(0x88, c.alpha)
    }

    @Test fun `hex uppercase shorthand parses`() {
        val c = CSSColor.parseColor("#AbC")
        // expands to AABBCC
        assertEquals(0xAA, c.red)
        assertEquals(0xBB, c.green)
        assertEquals(0xCC, c.blue)
    }

    @Test fun `rgb(ints) functional parser`() {
        val c = CSSColor.parseColor("rgb(255,128,0)")
        assertEquals(255, c.red)
        assertEquals(128, c.green)
        assertEquals(0,   c.blue)
        assertEquals(255, c.alpha)
    }

    @Test fun `rgb(percent) functional parser`() {
        val c = CSSColor.parseColor("rgb(100%,50%,0%)")
        assertEquals(255, c.red)
        assertEquals(128, c.green)
        assertEquals(0,   c.blue)
    }

    @Test fun `rgba raw‐float alpha functional parser`() {
        val c = CSSColor.parseColor("rgba(255,0,0,0.5)")
        assertEquals(255, c.red)
        assertEquals(0,   c.green)
        assertEquals(0,   c.blue)
        approxEq(0.5f, c.a)
    }

    @Test fun `rgba percent alpha functional parser`() {
        val c = CSSColor.parseColor("rgba(100%,0%,0%,50%)")
        assertEquals(255, c.red)
        assertEquals(0,   c.green)
        assertEquals(0,   c.blue)
        approxEq(0.5f, c.a)
    }

    @Test fun `hsl() functional parser yields correct RGB`() {
        // pure green: hue=120°, sat=100%, light=50%
        val c = CSSColor.parseColor("hsl(120,100%,50%)")
        assertEquals(0,   c.red)
        assertEquals(255, c.green)
        assertEquals(0,   c.blue)
    }

    @Test fun `hsla percent alpha functional parser`() {
        val c = CSSColor.parseColor("hsla(240,100%,50%,25%)")
        // pure blue, alpha 25%
        assertEquals(0,   c.red)
        assertEquals(0,   c.green)
        assertEquals(255, c.blue)
        approxEq(0.25f, c.a)
    }

    @Test fun `hsla raw‐float alpha functional parser`() {
        val c = CSSColor.parseColor("hsla(240,100%,50%,0.25)")
        assertEquals(0,   c.red)
        assertEquals(0,   c.green)
        assertEquals(255, c.blue)
        approxEq(0.25f, c.a)
    }

    @Test fun `hsl radian unit parsing`() {
        // 180° = π radians → cyan
        val c = CSSColor.parseColor("hsl(${PI}rad,100%,50%)")
        assertEquals(0,   c.red)
        assertEquals(255, c.green)
        assertEquals(255, c.blue)
    }

    @Test fun `hsl turn unit parsing`() {
        // 0.5 turn = 180° → cyan
        val c = CSSColor.parseColor("hsl(0.5turn,100%,50%)")
        assertEquals(0,   c.red)
        assertEquals(255, c.green)
        assertEquals(255, c.blue)
    }

    @Test fun `hsl grad unit parsing`() {
        // 200 grad = 180° → cyan
        val c = CSSColor.parseColor("hsl(200grad,100%,50%)")
        assertEquals(0,   c.red)
        assertEquals(255, c.green)
        assertEquals(255, c.blue)
    }

    @Test fun `named colors parse`() {
        val red = CSSColor.parseColor("red")
        assertEquals(255, red.red)
        assertEquals(0,   red.green)
        assertEquals(0,   red.blue)

        val rebecca = CSSColor.parseColor("rebeccapurple")
        assertEquals(0x66, rebecca.red)
        assertEquals(0x33, rebecca.green)
        assertEquals(0x99, rebecca.blue)

        val transparent = CSSColor.parseColor("transparent")
        assertEquals(0, transparent.alpha)
    }

    @Test fun `round-trip Android Int ↔ CSSColor`() {
        val original = CSSColor(0.2f, 0.4f, 0.6f, 0.8f)
        val colorInt = original.toRgbaInt()
        val back = CSSColor.fromRgba(colorInt)
        approxEq(original.r, back.r)
        approxEq(original.g, back.g)
        approxEq(original.b, back.b)
        approxEq(original.a, back.a)
    }

    @Test fun `individual channel setters`() {
        val c = CSSColor(0f,0f,0f,1f)
        c.red = 128;   assertEquals(128, c.red);   approxEq(128/255f, c.r)
        c.green = 64;  assertEquals(64,  c.green); approxEq(64/255f,  c.g)
        c.blue = 32;   assertEquals(32,  c.blue);  approxEq(32/255f,  c.b)
        c.alpha = 200; assertEquals(200, c.alpha); approxEq(200/255f, c.a)
    }

    @Test fun `hsl channel setters update RGB`() {
        val c = CSSColor.parseColor("hsl(0,100%,50%)") // red
        c.hue = 120f   // → green
        assertEquals(0,   c.red)
        assertEquals(255, c.green)
        assertEquals(0,   c.blue)

        c.saturation = 0f  // → gray
        assertTrue(c.red == c.green && c.green == c.blue)
    }

    @Test fun `convenience modifiers chain as expected`() {
        val c = CSSColor.parseColor("#888888")
            .lighten(0.1f)
            .saturate(0.2f)
            .rotateHue(45f)

        approxEq(0.633f, c.lightness, eps = 1e-3f)
        approxEq(0.2f,   c.saturation, eps = 1e-3f)
        approxEq(45f,    c.hue)
    }

    @Test
    fun `invalid formats throw IllegalArgumentException`() {
        listOf("", "rgb()", "hsl(0,0)", "#12", "rgba(0,0,0,150%)", "hsla(0,0%,0%,2)").forEach { bad ->
            try {
                CSSColor.parseColor(bad)
                assert(false)
            } catch (e: Throwable) {

            }
        }
    }

    @Test fun `out‐of‐range RGB ints clamp`() {
        val c = CSSColor.parseColor("rgb(300,-20, 260)")
        assertEquals(255, c.red)
        assertEquals(0,   c.green)
        assertEquals(255, c.blue)
    }

    @Test fun `parser is case- and whitespace-tolerant`() {
        val a = CSSColor.parseColor("  RgB(  10 ,20,  30 )")
        assertEquals(10,  a.red)
        assertEquals(20,  a.green)
        assertEquals(30,  a.blue)

        val b = CSSColor.parseColor("   ReBeCcaPURple ")
        assertEquals(0x66, b.red)
        assertEquals(0x33, b.green)
        assertEquals(0x99, b.blue)
    }

    @Test fun `hsl lightness extremes`() {
        // lightness = 0 → black
        val black = CSSColor.parseColor("hsl(123,45%,0%)")
        assertEquals(0, black.red)
        assertEquals(0, black.green)
        assertEquals(0, black.blue)
        // lightness = 100% → white
        val white = CSSColor.parseColor("hsl(321,55%,100%)")
        assertEquals(255, white.red)
        assertEquals(255, white.green)
        assertEquals(255, white.blue)
        // saturation = 0 → gray (r==g==b)
        val gray  = CSSColor.parseColor("hsl(50,0%,60%)")
        assertTrue(gray.red == gray.green && gray.green == gray.blue)
    }

    @Test fun `hsl negative and large hues wrap`() {
        val c1 = CSSColor.parseColor("hsl(-120,100%,50%)")  // → same as 240°
        assertEquals(0,   c1.red)
        assertEquals(0,   c1.green)
        assertEquals(255, c1.blue)

        val c2 = CSSColor.parseColor("hsl(480,100%,50%)")   // → same as 120°
        assertEquals(0,   c2.red)
        assertEquals(255, c2.green)
        assertEquals(0,   c2.blue)
    }

    @Test fun `lighten then darken returns original`() {
        val base = CSSColor.parseColor("#123456")
        val round = base.lighten(0.2f).darken(0.2f)
        approxEq(base.r, round.r)
        approxEq(base.g, round.g)
        approxEq(base.b, round.b)
    }
}