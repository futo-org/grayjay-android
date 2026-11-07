package com.futo.platformplayer.tv.install

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class TvSetupScreenshotView(context: Context, attrs: AttributeSet) : AppCompatImageView(context, attrs) {
    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Show the menu pane of the original 1920 × 1080 TV captures at a readable size.
        val scale = width / 1008f
        setMeasuredDimension(width, resolveSize((1080 * scale).toInt(), heightMeasureSpec))
        imageMatrix = Matrix().apply { setScale(scale, scale) }
    }
}
