package com.futo.platformplayer.tv.install

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class TvSetupScreenshotView(context: Context, attrs: AttributeSet) : AppCompatImageView(context, attrs) {
    var showFullImage = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Show the menu pane of the original 1920 × 1080 TV captures at a readable size.
        val imageWidth = if (showFullImage) drawable?.intrinsicWidth?.toFloat() ?: 1008f else 1008f
        val imageHeight = if (showFullImage) drawable?.intrinsicHeight ?: 1080 else 1080
        val scale = width / imageWidth
        setMeasuredDimension(width, resolveSize((imageHeight * scale).toInt(), heightMeasureSpec))
        imageMatrix = Matrix().apply { setScale(scale, scale) }
    }
}
