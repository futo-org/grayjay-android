package com.futo.platformplayer.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.security.MessageDigest

class IdenticonView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var hashString: String = "default"
        set(value) {
            field = value
            hash = md5(value)
            invalidate()
        }

    private var hash = ByteArray(16)

    private val path = Path()
    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    init {
        hashString = "default"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = (width.coerceAtMost(height) / 2).toFloat()
        val clipPath = path.apply {
            reset()
            addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
        }

        canvas.clipPath(clipPath)

        val size = width.coerceAtMost(height) / 5
        val colors = generateColorsFromHash(hash)

        for (x in 0 until 5) {
            for (y in 0 until 5) {
                val shapeIndex = getShapeIndex(x, y, hash)
                paint.color = colors[shapeIndex % colors.size]
                drawShape(canvas, x, y, size, shapeIndex)
            }
        }
    }

    private fun md5(input: String): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8))
    }

    private fun generateColorsFromHash(hash: ByteArray): List<Int> {
        val hue = hash[0].toFloat() / 255f
        return listOf(
            adjustColor(hue, 0.5f, 0.4f),
            adjustColor(hue, 0.5f, 0.8f),
            adjustColor(hue, 0.5f, 0.3f, 0.9f),
            adjustColor(hue, 0.5f, 0.4f, 0.7f)
        )
    }

    private fun getShapeIndex(x: Int, y: Int, hash: ByteArray): Int {
        val index = if (x < 3) y else 4 - y
        return hash[index].toInt() shr x * 2 and 0x03
    }

    private fun drawShape(canvas: Canvas, x: Int, y: Int, size: Int, shapeIndex: Int) {
        val left = x * size.toFloat()
        val top = y * size.toFloat()
        val path = Path()

        when (shapeIndex) {
            0 -> {
                // Square
                path.addRect(left, top, left + size, top + size, Path.Direction.CW)
            }
            1 -> {
                // Circle
                val radius = size / 2f
                path.addCircle(left + radius, top + radius, radius, Path.Direction.CW)
            }
            2 -> {
                // Diamond
                val halfSize = size / 2f
                path.moveTo(left + halfSize, top)
                path.lineTo(left + size, top + halfSize)
                path.lineTo(left + halfSize, top + size)
                path.lineTo(left, top + halfSize)
                path.close()
            }
            3 -> {
                // Triangle
                path.moveTo(left + size / 2f, top)
                path.lineTo(left + size, top + size)
                path.lineTo(left, top + size)
                path.close()
            }
        }

        canvas.drawPath(path, paint)
    }

    private fun adjustColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1.0f): Int {
        val color = Color.HSVToColor(floatArrayOf(hue * 360, saturation, lightness))
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb((alpha * 255).toInt(), red, green, blue)
    }
}
