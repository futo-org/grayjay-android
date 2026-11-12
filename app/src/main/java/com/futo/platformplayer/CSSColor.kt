import kotlin.math.*

class CSSColor(r: Float, g: Float, b: Float, a: Float = 1f) {
    init {
        require(r in 0f..1f && g in 0f..1f && b in 0f..1f && a in 0f..1f) {
            "RGBA channels must be in [0,1]"
        }
    }

    // -- RGB(A) channels stored 0–1 --
    var r: Float = r.coerceIn(0f, 1f)
        set(v) { field = v.coerceIn(0f, 1f); _hslDirty = true }
    var g: Float = g.coerceIn(0f, 1f)
        set(v) { field = v.coerceIn(0f, 1f); _hslDirty = true }
    var b: Float = b.coerceIn(0f, 1f)
        set(v) { field = v.coerceIn(0f, 1f); _hslDirty = true }
    var a: Float = a.coerceIn(0f, 1f)
        set(v) { field = v.coerceIn(0f, 1f) }

    // -- Int views of RGBA 0–255 --
    var red: Int
        get() = (r * 255).roundToInt()
        set(v) { r = (v.coerceIn(0, 255) / 255f) }
    var green: Int
        get() = (g * 255).roundToInt()
        set(v) { g = (v.coerceIn(0, 255) / 255f) }
    var blue: Int
        get() = (b * 255).roundToInt()
        set(v) { b = (v.coerceIn(0, 255) / 255f) }
    var alpha: Int
        get() = (a * 255).roundToInt()
        set(v) { a = (v.coerceIn(0, 255) / 255f) }

    // -- HSLA storage & lazy recompute flags --
    private var _h: Float = 0f
    private var _s: Float = 0f
    private var _l: Float = 0f
    private var _hslDirty = true

    /** Hue [0...360) */
    var hue: Float
        get() { computeHslIfNeeded(); return _h }
        set(v) { setHsl(v, saturation, lightness) }

    /** Saturation [0...1] */
    var saturation: Float
        get() { computeHslIfNeeded(); return _s }
        set(v) { setHsl(hue, v, lightness) }

    /** Lightness [0...1] */
    var lightness: Float
        get() { computeHslIfNeeded(); return _l }
        set(v) { setHsl(hue, saturation, v) }

    private fun computeHslIfNeeded() {
        if (!_hslDirty) return
        val max = max(max(r, g), b)
        val min = min(min(r, g), b)
        val d   = max - min
        _l      = (max + min) / 2f
        _s      = if (d == 0f) 0f else d / (1f - abs(2f * _l - 1f))
        _h      = when {
            d == 0f    -> 0f
            max == r   -> ((g - b) / d % 6f) * 60f
            max == g   -> (((b - r) / d) + 2f) * 60f
            else       -> (((r - g) / d) + 4f) * 60f
        }.let { if (it < 0f) it + 360f else it }
        _hslDirty = false
    }

    /**
     * Set all three HSL channels at once.
     * Hue in degrees [0...360), s/l [0...1].
     */
    fun setHsl(h: Float, s: Float, l: Float) {
        val hh = ((h % 360f) + 360f) % 360f
        val cc = (1f - abs(2f * l - 1f)) * s
        val x  = cc * (1f - abs((hh / 60f) % 2f - 1f))
        val m  = l - cc / 2f

        val (rp, gp, bp) = when {
            hh < 60f -> Triple(cc, x, 0f)
            hh < 120f -> Triple(x, cc, 0f)
            hh < 180f -> Triple(0f, cc, x)
            hh < 240f -> Triple(0f, x, cc)
            hh < 300f -> Triple(x, 0f, cc)
            else -> Triple(cc, 0f, x)
        }

        r = rp + m; g = gp + m; b = bp + m
        _h = hh; _s = s; _l = l; _hslDirty = false
    }

    /** Return 0xRRGGBBAA int */
    fun toRgbaInt(): Int {
        val ai = (a * 255).roundToInt() and 0xFF
        val ri = (r * 255).roundToInt() and 0xFF
        val gi = (g * 255).roundToInt() and 0xFF
        val bi = (b * 255).roundToInt() and 0xFF
        return (ri shl 24) or (gi shl 16) or (bi shl 8) or ai
    }

    /** Return 0xAARRGGBB int */
    fun toArgbInt(): Int {
        val ai = (a * 255).roundToInt() and 0xFF
        val ri = (r * 255).roundToInt() and 0xFF
        val gi = (g * 255).roundToInt() and 0xFF
        val bi = (b * 255).roundToInt() and 0xFF
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    // — Convenience modifiers (chainable) — 

    /** Lighten by fraction [0...1] */
    fun lighten(fraction: Float): CSSColor = apply {
        lightness = (lightness + fraction).coerceIn(0f, 1f)
    }

    /** Darken by fraction [0...1] */
    fun darken(fraction: Float): CSSColor = apply {
        lightness = (lightness - fraction).coerceIn(0f, 1f)
    }

    /** Increase saturation by fraction [0...1] */
    fun saturate(fraction: Float): CSSColor = apply {
        saturation = (saturation + fraction).coerceIn(0f, 1f)
    }

    /** Decrease saturation by fraction [0...1] */
    fun desaturate(fraction: Float): CSSColor = apply {
        saturation = (saturation - fraction).coerceIn(0f, 1f)
    }

    /** Rotate hue by degrees (can be negative) */
    fun rotateHue(degrees: Float): CSSColor = apply {
        hue = (hue + degrees) % 360f
    }

    companion object {
        /** Create from Android 0xAARRGGBB */
        @JvmStatic fun fromArgb(color: Int): CSSColor {
            val a = ((color ushr 24) and 0xFF) / 255f
            val r = ((color ushr 16) and 0xFF) / 255f
            val g = ((color ushr 8)  and 0xFF) / 255f
            val b = ( color and 0xFF) / 255f
            return CSSColor(r, g, b, a)
        }

        /** Create from Android 0xRRGGBBAA */
        @JvmStatic fun fromRgba(color: Int): CSSColor {
            val r = ((color ushr 24) and 0xFF) / 255f
            val g = ((color ushr 16) and 0xFF) / 255f
            val b = ((color ushr 8)  and 0xFF) / 255f
            val a = ( color and 0xFF) / 255f
            return CSSColor(r, g, b, a)
        }

        @JvmStatic fun fromAndroidColor(color: Int): CSSColor {
            return fromArgb(color)
        }

        private val NAMED_HEX = mapOf(
            "aliceblue" to "F0F8FF", "antiquewhite" to "FAEBD7", "aqua" to "00FFFF",
            "aquamarine" to "7FFFD4", "azure" to "F0FFFF", "beige" to "F5F5DC",
            "bisque" to "FFE4C4", "black" to "000000", "blanchedalmond" to "FFEBCD",
            "blue" to "0000FF", "blueviolet" to "8A2BE2", "brown" to "A52A2A",
            "burlywood" to "DEB887", "cadetblue" to "5F9EA0", "chartreuse" to "7FFF00",
            "chocolate" to "D2691E", "coral" to "FF7F50", "cornflowerblue" to "6495ED",
            "cornsilk" to "FFF8DC", "crimson" to "DC143C", "cyan" to "00FFFF",
            "darkblue" to "00008B", "darkcyan" to "008B8B", "darkgoldenrod" to "B8860B",
            "darkgray" to "A9A9A9", "darkgreen" to "006400", "darkgrey" to "A9A9A9",
            "darkkhaki" to "BDB76B", "darkmagenta" to "8B008B", "darkolivegreen" to "556B2F",
            "darkorange" to "FF8C00", "darkorchid" to "9932CC", "darkred" to "8B0000",
            "darksalmon" to "E9967A", "darkseagreen" to "8FBC8F", "darkslateblue" to "483D8B",
            "darkslategray" to "2F4F4F", "darkslategrey" to "2F4F4F", "darkturquoise" to "00CED1",
            "darkviolet" to "9400D3", "deeppink" to "FF1493", "deepskyblue" to "00BFFF",
            "dimgray" to "696969", "dimgrey" to "696969", "dodgerblue" to "1E90FF",
            "firebrick" to "B22222", "floralwhite" to "FFFAF0", "forestgreen" to "228B22",
            "fuchsia" to "FF00FF", "gainsboro" to "DCDCDC", "ghostwhite" to "F8F8FF",
            "gold" to "FFD700", "goldenrod" to "DAA520", "gray" to "808080",
            "green" to "008000", "greenyellow" to "ADFF2F", "grey" to "808080",
            "honeydew" to "F0FFF0", "hotpink" to "FF69B4", "indianred" to "CD5C5C",
            "indigo" to "4B0082", "ivory" to "FFFFF0", "khaki" to "F0E68C",
            "lavender" to "E6E6FA", "lavenderblush" to "FFF0F5", "lawngreen" to "7CFC00",
            "lemonchiffon" to "FFFACD", "lightblue" to "ADD8E6", "lightcoral" to "F08080",
            "lightcyan" to "E0FFFF", "lightgoldenrodyellow" to "FAFAD2", "lightgray" to "D3D3D3",
            "lightgreen" to "90EE90", "lightgrey" to "D3D3D3", "lightpink" to "FFB6C1",
            "lightsalmon" to "FFA07A", "lightseagreen" to "20B2AA", "lightskyblue" to "87CEFA",
            "lightslategray" to "778899", "lightslategrey" to "778899", "lightsteelblue" to "B0C4DE",
            "lightyellow" to "FFFFE0", "lime" to "00FF00", "limegreen" to "32CD32",
            "linen" to "FAF0E6", "magenta" to "FF00FF", "maroon" to "800000",
            "mediumaquamarine" to "66CDAA", "mediumblue" to "0000CD", "mediumorchid" to "BA55D3",
            "mediumpurple" to "9370DB", "mediumseagreen" to "3CB371", "mediumslateblue" to "7B68EE",
            "mediumspringgreen" to "00FA9A", "mediumturquoise" to "48D1CC", "mediumvioletred" to "C71585",
            "midnightblue" to "191970", "mintcream" to "F5FFFA", "mistyrose" to "FFE4E1",
            "moccasin" to "FFE4B5", "navajowhite" to "FFDEAD", "navy" to "000080",
            "oldlace" to "FDF5E6", "olive" to "808000", "olivedrab" to "6B8E23",
            "orange" to "FFA500", "orangered" to "FF4500", "orchid" to "DA70D6",
            "palegoldenrod" to "EEE8AA", "palegreen" to "98FB98", "paleturquoise" to "AFEEEE",
            "palevioletred" to "DB7093", "papayawhip" to "FFEFD5", "peachpuff" to "FFDAB9",
            "peru" to "CD853F", "pink" to "FFC0CB", "plum" to "DDA0DD",
            "powderblue" to "B0E0E6", "purple" to "800080", "rebeccapurple" to "663399",
            "red" to "FF0000", "rosybrown" to "BC8F8F", "royalblue" to "4169E1",
            "saddlebrown" to "8B4513", "salmon" to "FA8072", "sandybrown" to "F4A460",
            "seagreen" to "2E8B57", "seashell" to "FFF5EE", "sienna" to "A0522D",
            "silver" to "C0C0C0", "skyblue" to "87CEEB", "slateblue" to "6A5ACD",
            "slategray" to "708090", "slategrey" to "708090", "snow" to "FFFAFA",
            "springgreen" to "00FF7F", "steelblue" to "4682B4", "tan" to "D2B48C",
            "teal" to "008080", "thistle" to "D8BFD8", "tomato" to "FF6347",
            "turquoise" to "40E0D0", "violet" to "EE82EE", "wheat" to "F5DEB3",
            "white" to "FFFFFF", "whitesmoke" to "F5F5F5", "yellow" to "FFFF00",
            "yellowgreen" to "9ACD32"
        )
        private val NAMED: Map<String, Int> = NAMED_HEX
            .mapValues { (_, hexRgb) ->
                // parse hexRgb ("RRGGBB") to Int, then OR in 0xFF000000 for full opacity
                val rgb = hexRgb.toInt(16)
                (rgb shl 8) or 0xFF
            } + ("transparent" to 0x00000000)

        private val HEX_REGEX = Regex("^#([0-9a-fA-F]{3,8})$", RegexOption.IGNORE_CASE)
        private val RGB_REGEX = Regex("^rgba?\\(([^)]+)\\)\$", RegexOption.IGNORE_CASE)
        private val HSL_REGEX = Regex("^hsla?\\(([^)]+)\\)\$", RegexOption.IGNORE_CASE)

        @JvmStatic
        fun parseColor(s: String): CSSColor {
            val str = s.trim()
            // named
            NAMED[str.lowercase()]?.let { return it.RGBAtoCSSColor() }

            // hex
            HEX_REGEX.matchEntire(str)?.groupValues?.get(1)?.let { part ->
                return parseHexPart(part)
            }

            // rgb/rgba
            RGB_REGEX.matchEntire(str)?.groupValues?.get(1)?.let {
                return parseRgbParts(it.split(',').map(String::trim))
            }

            // hsl/hsla
            HSL_REGEX.matchEntire(str)?.groupValues?.get(1)?.let {
                return parseHslParts(it.split(',').map(String::trim))
            }

            error("Cannot parse color: \"$s\"")
        }

        private fun parseHexPart(p: String): CSSColor {
            // expand shorthand like "RGB" or "RGBA" to full 8-chars "RRGGBBAA"
            val hex = when (p.length) {
                3 -> p.map { "$it$it" }.joinToString("") + "FF"
                4 -> p.map { "$it$it" }.joinToString("")
                6 -> p + "FF"
                8 -> p
                else -> error("Invalid hex color: #$p")
            }

            val parsed = hex.toLong(16).toInt()
            val alpha = (parsed and 0xFF) shl 24
            val rgbOnly = (parsed ushr 8) and 0x00FFFFFF
            val argb = alpha or rgbOnly
            return fromArgb(argb)
        }

        private fun parseRgbParts(parts: List<String>): CSSColor {
            require(parts.size == 3 || parts.size == 4) { "rgb/rgba needs 3 or 4 parts" }

            // r/g/b: "128" → 128/255, "50%" → 0.5
            fun channel(ch: String): Float =
                if (ch.endsWith("%")) ch.removeSuffix("%").toFloat() / 100f
                else ch.toFloat().coerceIn(0f, 255f) / 255f

            // alpha: "0.5" → 0.5, "50%" → 0.5
            fun alpha(a: String): Float =
                if (a.endsWith("%")) a.removeSuffix("%").toFloat() / 100f
                else a.toFloat().coerceIn(0f, 1f)

            val r = channel(parts[0])
            val g = channel(parts[1])
            val b = channel(parts[2])
            val a = if (parts.size == 4) alpha(parts[3]) else 1f

            return CSSColor(r, g, b, a)
        }

        private fun parseHslParts(parts: List<String>): CSSColor {
            require(parts.size == 3 || parts.size == 4) { "hsl/hsla needs 3 or 4 parts" }

            fun hueOf(h: String): Float = when {
                h.endsWith("deg")  -> h.removeSuffix("deg").toFloat()
                h.endsWith("grad") -> h.removeSuffix("grad").toFloat() * 0.9f
                h.endsWith("rad")  -> h.removeSuffix("rad").toFloat() * (180f / PI.toFloat())
                h.endsWith("turn") -> h.removeSuffix("turn").toFloat() * 360f
                else               -> h.toFloat()
            }

            // for s and l you only ever see percentages
            fun pct(p: String): Float =
                p.removeSuffix("%").toFloat().coerceIn(0f, 100f) / 100f

            // alpha: "0.5" → 0.5, "50%" → 0.5
            fun alpha(a: String): Float =
                if (a.endsWith("%")) pct(a)
                else a.toFloat().coerceIn(0f, 1f)

            val h = hueOf(parts[0])
            val s = pct(parts[1])
            val l = pct(parts[2])
            val a = if (parts.size == 4) alpha(parts[3]) else 1f

            return CSSColor(0f, 0f, 0f, a).apply { setHsl(h, s, l) }
        }
    }
}

fun Int.RGBAtoCSSColor(): CSSColor = CSSColor.fromRgba(this)
fun Int.ARGBtoCSSColor(): CSSColor = CSSColor.fromArgb(this)
fun CSSColor.toAndroidColor(): Int = toArgbInt()
