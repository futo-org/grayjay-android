package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.polycentric.TWEMOJI_CBDT_METRICS
import com.futo.platformplayer.polycentric.TWEMOJI_COLR_METRICS
import com.futo.platformplayer.polycentric.emojiFingerprint
import polycentric.v2.PublicKey

/**
 * Shows a pairing fingerprint as a row of emojis in Harbor's Twemoji font. EmojiCompat is disabled,
 * so Android won't swap any of its own emojis.
 */
class HarborEmojiFingerprintView(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    init {
        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.HarborEmojiFingerprintView, 0, 0)
        val emojiSize = attrArr.getDimensionPixelSize(R.styleable.HarborEmojiFingerprintView_emojiSize, dp(56f))
        val emojiSpacing = attrArr.getDimensionPixelSize(R.styleable.HarborEmojiFingerprintView_emojiSpacing, dp(12f))
        attrArr.recycle()

        setEmojiCompatEnabled(false)
        typeface = TwemojiFont.typeface(context)
        // Enforce emoji size
        setTextSize(TypedValue.COMPLEX_UNIT_PX, emojiSize.toFloat())
        includeFontPadding = false
        isFallbackLineSpacing = false
        setLineSpacing(emojiSpacing.toFloat(), 1f)
        gravity = Gravity.CENTER

        val metrics = TwemojiFont.metrics
        val fontSpacing = (metrics.advance - metrics.unitsPerEm).toFloat() / metrics.unitsPerEm
        letterSpacing = emojiSpacing.toFloat() / emojiSize - fontSpacing

        // The space before and after each glyph differs, so offset the padding to center the
        // glyphs.
        val offset = (metrics.spaceBefore - metrics.spaceAfter) * emojiSize / metrics.unitsPerEm
        setPadding(
            paddingLeft + maxOf(-offset, 0),
            paddingTop,
            paddingRight + maxOf(offset, 0),
            paddingBottom,
        )
    }

    fun setPublicKey(key: PublicKey) = setEmojis(emojiFingerprint(key))

    fun setEmojis(emojis: List<String>) {
        text = emojis.joinToString("")
        contentDescription = emojis.joinToString(" ")
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

/**
 * Sizes in a Twemoji font, in font units. Each emoji is a square [unitsPerEm] wide. The font adds
 * [spaceBefore] and [spaceAfter] on either side.
 */
internal class TwemojiFontMetrics(
    val unitsPerEm: Int,
    val advance: Int,
    val spaceBefore: Int,
    val spaceAfter: Int,
)

/**
 * Harbor's Twemoji font, so fingerprints look the same as in Harbor. Android 9 doesn't draw the
 * vector font correctly (it ends up blank), so it uses the bitmap font instead.
 */
internal object TwemojiFont {
    private val useColr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val metrics = if (useColr) TWEMOJI_COLR_METRICS else TWEMOJI_CBDT_METRICS
    private var typeface: Typeface? = null

    fun typeface(context: Context): Typeface =
        typeface ?: ResourcesCompat.getFont(
            context.applicationContext,
            if (useColr) R.font.twemoji_fingerprint else R.font.twemoji_fingerprint_cbdt,
        )!!.also { typeface = it }
}
