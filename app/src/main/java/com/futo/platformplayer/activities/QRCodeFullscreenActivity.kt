package com.futo.platformplayer.activities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class QRCodeFullscreenActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_QR_TEXT = "qr_text"
        
        fun createIntent(context: Context, qrText: String): android.content.Intent {
            return android.content.Intent(context, QRCodeFullscreenActivity::class.java).apply {
                putExtra(EXTRA_QR_TEXT, qrText)
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_fullscreen)
        setNavigationBarColorAndIcons()

        val qrText = intent.getStringExtra(EXTRA_QR_TEXT)

        val imageQR = findViewById<ImageView>(R.id.image_qr_fullscreen)
        val buttonBack = findViewById<ImageButton>(R.id.button_back_fullscreen)
        val buttonClose = findViewById<ImageButton>(R.id.button_close_fullscreen)

        // Generate QR code bitmap from text
        qrText?.let { text ->
            try {
                if (!isContentSuitableForQRCode(text)) {
                    throw Exception("Data too big for QR code generation")
                }
                
                val dimension = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 300f, resources.displayMetrics
                ).toInt()
                val qrBitmap = generateQRCode(text, dimension, dimension)
                imageQR.setImageBitmap(qrBitmap)
            } catch (e: Exception) {
                // If QR generation fails, show error or fallback
                imageQR.setImageResource(R.drawable.ic_qr)
            }
        }

        buttonBack.setOnClickListener {
            finish()
        }

        buttonClose.setOnClickListener {
            finish()
        }

        imageQR.setOnClickListener {
            finish()
        }
    }

    private fun isContentSuitableForQRCode(content: String): Boolean {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return bytes.size <= 2300  // QR Code Version 40 with Error Correction Level M can hold ~2331 bytes
    }

    private fun generateQRCode(content: String, width: Int, height: Int): Bitmap {
        if (!isContentSuitableForQRCode(content)) {
            throw Exception("Data too big for QR code generation")
        }
        
        val hints = java.util.EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = 1
        
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
        return bitMatrixToBitmap(bitMatrix)
    }

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}

