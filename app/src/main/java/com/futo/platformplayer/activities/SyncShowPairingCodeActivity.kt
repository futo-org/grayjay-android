package com.futo.platformplayer.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.NetworkInterface

class SyncShowPairingCodeActivity : AppCompatActivity() {
    private lateinit var _textCode: TextView
    private lateinit var _imageQR: ImageView
    private lateinit var _textQR: TextView
    private var _code: String? = null

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onDestroy() {
        super.onDestroy()
        activity = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this

        setContentView(R.layout.activity_sync_show_pairing_code)
        setNavigationBarColorAndIcons()

        _textCode = findViewById(R.id.text_code)
        _imageQR = findViewById(R.id.image_qr)
        _textQR = findViewById(R.id.text_scan_qr)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.button_copy).setOnClickListener {
            val code = _code ?: return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;
            val clip = ClipData.newPlainText(getString(R.string.copied_text), code);
            clipboard.setPrimaryClip(clip);
            UIDialogs.toast(this, "Copied to clipboard")
        }

        val ips = getIPs()
        val selfDeviceInfo = SyncDeviceInfo(StateSync.instance.publicKey!!, ips.toTypedArray(), StateSync.PORT, StateSync.instance.pairingCode)
        val json = Json.encodeToString(selfDeviceInfo)
        val base64 = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val url = "grayjay://sync/${base64}"
        setCode(url)
    }

    fun setCode(code: String?) {
        _code = code

        _textCode.text = code

        if (code == null) {
            _imageQR.visibility = View.INVISIBLE
            _textQR.visibility = View.INVISIBLE
            return
        }

        try {
            val dimension = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, resources.displayMetrics).toInt()
            val qrCodeBitmap = generateQRCode(code, dimension, dimension)
            _imageQR.setImageBitmap(qrCodeBitmap)
            _imageQR.visibility = View.VISIBLE
            _textQR.visibility = View.VISIBLE
        } catch (e: Exception) {
            Logger.e(TAG, getString(R.string.failed_to_generate_qr_code), e)
            _imageQR.visibility = View.INVISIBLE
            _textQR.visibility = View.INVISIBLE
        }
    }

    private fun generateQRCode(content: String, width: Int, height: Int): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height);
        return bitMatrixToBitmap(bitMatrix);
    }

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width;
        val height = matrix.height;
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE);
            }
        }
        return bmp;
    }

    private fun getIPs(): List<String> {
        val ips = arrayListOf<String>()
        for (intf in NetworkInterface.getNetworkInterfaces()) {
            for (addr in intf.inetAddresses) {
                if (addr.isLoopbackAddress) {
                    continue
                }

                if (addr.address.size != 4) {
                    continue
                }

                addr.hostAddress?.let { ips.add(it) }
            }
        }
        return ips
    }

    companion object {
        private const val TAG = "SyncShowPairingCodeActivity"
        var activity: SyncShowPairingCodeActivity? = null
            private set
    }
}