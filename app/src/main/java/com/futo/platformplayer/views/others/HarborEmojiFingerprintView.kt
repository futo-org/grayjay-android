package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.polycentric.TWEMOJI_CBDT_METRICS
import com.futo.platformplayer.polycentric.TWEMOJI_COLR_METRICS
import com.futo.platformplayer.polycentric.emojiFingerprint
import polycentric.v2.PublicKey

/**
 * Shows a pairing fingerprint as a row of emojis in Harbor's Twemoji font. Draws the font directly
 * (using a canvas rather than a TextView), so Android can't swap in its own emojis or add padding.
 */
class HarborEmojiFingerprintView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val emojiSize: Int
    private val emojiSpacing: Int
    private var emojis: List<String> = emptyList()
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        typeface = TwemojiRenderer.typeface(context)
    }

    init {
        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.HarborEmojiFingerprintView, 0, 0)
        emojiSize = attrArr.getDimensionPixelSize(R.styleable.HarborEmojiFingerprintView_emojiSize, dp(56f))
        emojiSpacing = attrArr.getDimensionPixelSize(R.styleable.HarborEmojiFingerprintView_emojiSpacing, dp(12f))
        attrArr.recycle()
    }

    fun setPublicKey(key: PublicKey) = setEmojis(emojiFingerprint(key))

    fun setEmojis(emojis: List<String>) {
        val sizeChanged = emojis.size != this.emojis.size
        this.emojis = emojis
        contentDescription = emojis.joinToString(" ")
        if (sizeChanged) requestLayout()
        invalidate()
    }

    private fun contentWidth(): Int =
        if (emojis.isEmpty()) 0 else emojis.size * emojiSize + (emojis.size - 1) * emojiSpacing

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(contentWidth() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(emojiSize + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Center the emojis in the view.
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        var left = paddingLeft + (innerWidth - contentWidth()) / 2f
        val top = paddingTop + (innerHeight - emojiSize) / 2f
        for (emoji in emojis) {
            TwemojiRenderer.drawEmoji(canvas, emoji, left, top, emojiSize.toFloat(), paint)
            left += emojiSize + emojiSpacing
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

/**
 * Sizes in a Twemoji font, in font units. Each emoji is a square [unitsPerEm] wide, with its top at
 * [ascent]. The font adds [spaceBefore] and [spaceAfter] on either side.
 */
internal class TwemojiFontMetrics(
    val unitsPerEm: Int,
    val ascent: Int,
    val descent: Int,
    val advance: Int,
    val spaceBefore: Int,
    val spaceAfter: Int,
)

/**
 * Draws emojis with Harbor's Twemoji font, so fingerprints look the same as in Harbor. Android 9
 * doesn't draw the vector font correctly (it ends up blank), so it uses the bitmap font instead.
 */
internal object TwemojiRenderer {
    private val useColr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val metrics = if (useColr) TWEMOJI_COLR_METRICS else TWEMOJI_CBDT_METRICS
    private var typeface: Typeface? = null

    fun typeface(context: Context): Typeface =
        typeface ?: ResourcesCompat.getFont(
            context.applicationContext,
            if (useColr) R.font.twemoji_fingerprint else R.font.twemoji_fingerprint_cbdt,
        )!!.also { typeface = it }

    /**
     * Draws [emoji] as a [size] square with its top left corner at ([left], [top]). [paint] is
     * expected to be a [TextPaint] object using [typeface].
     */
    fun drawEmoji(canvas: Canvas, emoji: String, left: Float, top: Float, size: Float, paint: Paint) {
        val unit = size / metrics.unitsPerEm
        paint.textSize = size
        canvas.drawText(emoji, left - metrics.spaceBefore * unit, top + metrics.ascent * unit, paint)
    }
}
