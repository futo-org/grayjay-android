package com.futo.platformplayer.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

class IdenticonView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var hashString: String = "default"
        set(value) {
            field = value
            hash = md5(value)
            iconGenerator = null
            invalidate()
        }

    private var hash = ByteArray(16)
    private var iconGenerator: IconGenerator? = null
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = (width.coerceAtMost(height) / 2).toFloat()
        val clipPath = path.apply {
            reset()
            addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
        }

        canvas.clipPath(clipPath)

        if (iconGenerator == null) {
            iconGenerator = IconGenerator(min(height, width).toFloat(), hash)
        }

        iconGenerator?.render(canvas)
    }

    private fun md5(input: String): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8))
    }

    interface Shape {
        fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint)
    }

    class CutCorner : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val k = size * 0.42f
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size, 0f)
                lineTo(size, size - k * 2)
                lineTo(size - k, size)
                lineTo(0f, size)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class SideTriangle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val w = size / 2
            val h = size * 0.8f
            val path = Path().apply {
                moveTo(size - w, 0f)
                lineTo(size, h)
                lineTo(size - w, h)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class MiddleSquare : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val s = size / 3
            canvas.drawRect(s, s, size - s, size - s, paint)
        }
    }

    class CornerSquare : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val inner = size * 0.1f
            val outer = max(1f, size * 0.25f)
            canvas.drawRect(outer, outer, size - inner - outer, size - inner - outer, paint)
        }
    }

    class OffCenterCircle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val m = size * 0.15f
            val s = size * 0.5f
            canvas.drawCircle(size - s - m, size - s - m, s / 2, paint)
        }
    }

    class NegativeTriangle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val inner = size * 0.1f
            val outer = inner * 4
            val path = Path().apply {
                addRect(0f, 0f, size, size, Path.Direction.CW)
                moveTo(outer, outer)
                lineTo(size - inner, outer)
                lineTo(outer + (size - outer - inner) / 2, size - inner)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class CutSquare : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size, 0f)
                lineTo(size, size * 0.7f)
                lineTo(size * 0.4f, size * 0.4f)
                lineTo(size * 0.7f, size)
                lineTo(0f, size)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class CornerPlusTriangle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val halfSize = size / 2
            canvas.drawRect(0f, 0f, size, halfSize, paint)
            canvas.drawRect(0f, halfSize, halfSize, size, paint)
            val path = Path().apply {
                moveTo(halfSize, halfSize)
                lineTo(size, halfSize)
                lineTo(halfSize, size)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class NegativeSquare : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val inner = size * 0.14f
            val outer = size * 0.35f
            val path = Path().apply {
                addRect(0f, 0f, size, size, Path.Direction.CW)
                addRect(outer, outer, size - outer - inner, size - outer - inner, Path.Direction.CCW)
            }
            canvas.drawPath(path, paint)
        }
    }

    class NegativeCircle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val inner = size * 0.12f
            val outer = inner * 3
            val path = Path().apply {
                addRect(0f, 0f, size, size, Path.Direction.CW)
                addCircle(outer, outer, (size - inner - outer) / 2, Path.Direction.CCW)
            }
            canvas.drawPath(path, paint)
        }
    }

    class NegativeRhombus : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val m = size * 0.25f
            val path = Path().apply {
                addRect(0f, 0f, size, size, Path.Direction.CW)
                moveTo(m, size / 2)
                lineTo(size / 2, m)
                lineTo(size - m, size / 2)
                lineTo(size / 2, size - m)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class ConditionalCircle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            if (index == 0) {
                val m = size * 0.4f
                val s = size * 1.2f
                canvas.drawCircle(m, m, s / 2, paint)
            }
        }
    }

    class HalfTriangle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val path = Path().apply {
                moveTo(size / 2, size / 2)
                lineTo(size, size / 2)
                lineTo(size / 2, size)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class Triangle(val corner: Int = 0) : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val path = Path().apply {
                when (corner) {
                    0 -> {
                        moveTo(0f, 0f)
                        lineTo(size, 0f)
                        lineTo(0f, size)
                    }
                    1 -> {
                        moveTo(size, 0f)
                        lineTo(size, size)
                        lineTo(0f, size)
                    }
                    2 -> {
                        moveTo(0f, 0f)
                        lineTo(size, 0f)
                        lineTo(size, size)
                    }
                    3 -> {
                        moveTo(0f, 0f)
                        lineTo(0f, size)
                        lineTo(size, size)
                    }
                }
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class BottomHalfTriangle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val path = Path().apply {
                moveTo(0f, size / 2)
                lineTo(size, size / 2)
                lineTo(size / 2, size)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class Rhombus : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val path = Path().apply {
                moveTo(size / 2, 0f)
                lineTo(size, size / 2)
                lineTo(size / 2, size)
                lineTo(0f, size / 2)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    class Circle : Shape {
        override fun draw(canvas: Canvas, size: Float, index: Int, paint: Paint) {
            val m = size / 6
            canvas.drawCircle(m, m, size / 2 - m, paint)
        }
    }

    class IconGenerator(private val size: Float, private val hash: ByteArray) {
        private val digits: ByteArray
        private var selectedColors = arrayOf<Paint>()

        init {
            digits = ByteArray(max(12, hash.size * 2))
            var index = 0
            for (byte in hash) {
                if (index >= digits.size) {
                    break
                }
                digits[index] = ((byte.toInt() shr 4) and 0x0f).toByte()
                digits[index + 1] = (byte.toInt() and 0x0f).toByte()
                index += 2
            }
            selectColors()
        }

        private fun selectColors() {
            val value = hash.copyOfRange(hash.size - 4, hash.size).fold(0) { acc, byte ->
                (acc shl 8) or (byte.toInt() and 0xFF)
            } and 0x0FFFFFFF
            val colorTheme = ColorTheme(hue = value.toFloat() / 0x0FFFFFFF)

            val selectedColorIndices = mutableListOf<Int>()
            for (i in 0 until 3) {
                val index = (digits[8 + i].toInt() % colorTheme.colors.size)
                selectedColorIndices.add(colorTheme.validateIndex(index, selectedColorIndices))
            }

            selectedColors = selectedColorIndices.map { index ->
                Paint().apply {
                    color = colorTheme.colors[index]
                    style = Paint.Style.FILL
                }
            }.toTypedArray()
        }

        fun renderBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            render(canvas)
            return bitmap
        }

        fun render(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)

            renderShape(canvas, 0, outerShapes, 2, 3, arrayOf(
                PointF(1f, 0f),
                PointF(2f, 0f),
                PointF(2f, 3f),
                PointF(1f, 3f),
                PointF(0f, 1f),
                PointF(3f, 1f),
                PointF(3f, 2f),
                PointF(0f, 2f),
            ))
            renderShape(canvas, 1, outerShapes, 4, 5, arrayOf(
                PointF(0f, 0f),
                PointF(3f, 0f),
                PointF(3f, 3f),
                PointF(0f, 3f),
            ))
            renderShape(canvas, 2, centerShapes, 1, null, arrayOf(
                PointF(1f, 1f),
                PointF(2f, 1f),
                PointF(2f, 2f),
                PointF(1f, 2f),
            ))
        }

        private fun renderShape(
            canvas: Canvas,
            colorIndex: Int,
            shapes: Array<Shape>,
            index: Int,
            rotationIndex: Int?,
            positions: Array<PointF>
        ) {
            val cellSize = size / 4
            var r = rotationIndex?.let { digits[it].toInt() } ?: 0
            val shape = shapes[digits[index].toInt() % shapes.size]

            val paint = Paint().apply {
                color = selectedColors[colorIndex % selectedColors.size].color
                style = Paint.Style.FILL
            }

            for ((idx, position) in positions.withIndex()) {
                canvas.save()
                canvas.translate(position.x * cellSize, position.y * cellSize)
                canvas.translate(cellSize / 2, cellSize / 2)
                canvas.rotate((r % 4) * 90f)
                canvas.translate(-cellSize / 2, -cellSize / 2)

                shape.draw(canvas, cellSize, idx, paint)
                canvas.restore()
                r++
            }
        }

        class ColorTheme(val hue: Float, val saturation: Float = 0.5f) {
            val colors: List<Int>

            init {
                colors = listOf(
                    // Dark gray
                    grayscaleColor(0f),
                    // Mid color
                    hslColor(hue, saturation, colorLightness(0.5f)),
                    // Light gray
                    grayscaleColor(1f),
                    // Light color
                    hslColor(hue, saturation, colorLightness(1f)),
                    // Dark color
                    hslColor(hue, saturation, colorLightness(0f))
                )
            }

            fun validateIndex(index: Int, selected: List<Int>): Int {
                return if (isDuplicate(index, listOf(0, 4), selected) || isDuplicate(index, listOf(2, 3), selected)) {
                    1
                } else {
                    index
                }
            }

            private fun isDuplicate(index: Int, values: List<Int>, selected: List<Int>): Boolean {
                if (!values.contains(index)) return false
                return values.any { selected.contains(it) }
            }

            private fun colorLightness(value: Float): Float = lightness(value, 0.4f, 0.8f)

            private fun grayscaleLightness(value: Float): Float = lightness(value, 0.3f, 0.9f)

            private fun lightness(value: Float, min: Float, max: Float): Float {
                val lightness = min + value * (max - min)
                return minOf(1f, maxOf(0f, lightness))
            }

            private fun grayscaleColor(lightness: Float): Int {
                return Color.HSVToColor(floatArrayOf(0f, 0f, lightness))
            }

            private fun hslColor(hue: Float, saturation: Float, lightness: Float): Int {
                return Color.HSVToColor(floatArrayOf(hue, saturation, lightness))
            }
        }
    }

    companion object {
        val centerShapes = arrayOf(
            CutCorner(),
            SideTriangle(),
            MiddleSquare(),
            CornerSquare(),
            OffCenterCircle(),
            NegativeTriangle(),
            CutSquare(),
            HalfTriangle(),
            CornerPlusTriangle(),
            CutSquare(),
            NegativeCircle(),
            HalfTriangle(),
            NegativeRhombus(),
            ConditionalCircle()
        )

        val outerShapes = arrayOf(
            Triangle(),
            BottomHalfTriangle(),
            Rhombus(),
            Circle(),
        )

        private const val TAG = "IdenticonView"
    }
}