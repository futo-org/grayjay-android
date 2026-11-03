package com.futo.platformplayer.casting.dlna

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.handlers.HttpHandler
import com.futo.platformplayer.logging.Logger
import java.io.ByteArrayOutputStream

/**
 * Renders the still "Preparing media" screen shown on DLNA renderers while a stream is being prepared. Without it
 * the renderer keeps requesting the previous, already removed stream and shows a network error.
 */
object DlnaStatusImage {
    private const val TAG = "DlnaStatusImage"
    //Rendered at 4K, renderers scale it down if needed. Layout values below are in 720p units.
    private const val WIDTH = 3840
    private const val HEIGHT = 2160
    private const val SCALE = WIDTH / 1280f

    fun render(context: Context?, title: String?, thumbnailUrl: String?, status: String): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(12, 12, 12))

        loadThumbnail(thumbnailUrl)?.let { thumbnail ->
            //Center-crop the thumbnail as a dimmed backdrop.
            val scale = maxOf(WIDTH.toFloat() / thumbnail.width, HEIGHT.toFloat() / thumbnail.height)
            val w = thumbnail.width * scale
            val h = thumbnail.height * scale
            canvas.drawBitmap(thumbnail, null, RectF((WIDTH - w) / 2, (HEIGHT - h) / 2, (WIDTH + w) / 2, (HEIGHT + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(Color.argb(200, 0, 0, 0))
            thumbnail.recycle()
        }

        val centerX = WIDTH / 2f
        context?.let { drawAppIcon(it, canvas, centerX, 210f * SCALE) }

        //Static ring with a highlighted arc, reads as a spinner.
        val ringRect = RectF(centerX - 34 * SCALE, 330f * SCALE, centerX + 34 * SCALE, 398f * SCALE)
        canvas.drawArc(ringRect, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7f * SCALE; color = Color.argb(70, 255, 255, 255)
        })
        canvas.drawArc(ringRect, -90f, 100f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7f * SCALE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE
        })

        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 40f * SCALE; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(status, centerX, 470f * SCALE, statusPaint)

        if (!title.isNullOrBlank()) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255); textSize = 30f * SCALE; textAlign = Paint.Align.CENTER
            }
            val ellipsized = TextUtils.ellipsize(title, titlePaint, WIDTH * 0.8f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(ellipsized, centerX, 530f * SCALE, titlePaint)
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255); textSize = 24f * SCALE; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Grayjay", centerX, HEIGHT - 40f * SCALE, footerPaint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun drawAppIcon(context: Context, canvas: Canvas, centerX: Float, centerY: Float) {
        try {
            val icon = context.packageManager.getApplicationIcon(context.packageName)
            val size = (120 * SCALE).toInt()
            icon.bounds = Rect((centerX - size / 2).toInt(), (centerY - size / 2).toInt(), (centerX + size / 2).toInt(), (centerY + size / 2).toInt())
            icon.draw(canvas)
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to draw app icon: ${e.message}")
        }
    }

    private fun loadThumbnail(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            DlnaSoap.createClient().get(url).use { response ->
                if (!response.isOk) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to load thumbnail: ${e.message}")
            null
        }
    }

    class Handler(path: String, private val jpeg: ByteArray) : HttpHandler("GET", path) {
        override fun handle(httpContext: HttpContext) {
            val headers = this.headers.clone()
            headers["Content-Type"] = "image/jpeg"
            headers["contentFeatures.dlna.org"] = "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=00900000000000000000000000000000"
            headers["transferMode.dlna.org"] = "Interactive"
            httpContext.respondBytes(200, headers, jpeg)
        }
    }
}
