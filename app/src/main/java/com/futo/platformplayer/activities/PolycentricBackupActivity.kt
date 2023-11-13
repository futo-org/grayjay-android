package com.futo.platformplayer.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.polycentric.core.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import userpackage.Protocol
import userpackage.Protocol.ExportBundle
import userpackage.Protocol.URLInfo

class PolycentricBackupActivity : AppCompatActivity() {
    private lateinit var _buttonShare: BigButton;
    private lateinit var _buttonCopy: BigButton;
    private lateinit var _imageQR: ImageView;
    private lateinit var _exportBundle: String;
    private lateinit var _textQR: TextView;

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_backup);
        setNavigationBarColorAndIcons();

        _buttonShare = findViewById(R.id.button_share);
        _buttonCopy = findViewById(R.id.button_copy);
        _imageQR = findViewById(R.id.image_qr);
        _textQR = findViewById(R.id.text_qr);
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish();
        };

        _exportBundle = createExportBundle();

        try {
            val dimension = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, resources.displayMetrics).toInt();
            val qrCodeBitmap = generateQRCode(_exportBundle, dimension, dimension);
            _imageQR.setImageBitmap(qrCodeBitmap);
        } catch (e: Exception) {
            Logger.e(TAG, getString(R.string.failed_to_generate_qr_code), e);
            _imageQR.visibility = View.INVISIBLE;
            _textQR.visibility = View.INVISIBLE;
        }

        _buttonShare.onClick.subscribe {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain";
                putExtra(Intent.EXTRA_TEXT, _exportBundle);
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_text)));
        };

        _buttonCopy.onClick.subscribe {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;
            val clip = ClipData.newPlainText(getString(R.string.copied_text), _exportBundle);
            clipboard.setPrimaryClip(clip);
        };
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

    private fun createExportBundle(): String {
        val processHandle = StatePolycentric.instance.processHandle!!;

        val relevantContentTypes = listOf(ContentType.SERVER.value, ContentType.AVATAR.value, ContentType.USERNAME.value);
        val crdtSetItems = arrayListOf<Pair<SignedEvent, StorageTypeCRDTSetItem>>();
        val crdtItems = arrayListOf<Pair<SignedEvent, StorageTypeCRDTItem>>();

        Store.instance.enumerateSignedEvents(processHandle.system) { signedEvent ->
            if (!relevantContentTypes.contains(signedEvent.event.contentType)) {
                return@enumerateSignedEvents;
            }

            val event = signedEvent.event;
            event.lwwElementSet?.let { lwwElementSet ->
                val foundIndex = crdtSetItems.indexOfFirst { pair ->
                    pair.second.contentType == event.contentType && pair.second.value.contentEquals(lwwElementSet.value)
                }

                var found = false
                if (foundIndex != -1) {
                    val foundPair = crdtSetItems[foundIndex]
                    if (foundPair.second.unixMilliseconds < lwwElementSet.unixMilliseconds) {
                        foundPair.second.operation = lwwElementSet.operation
                        foundPair.second.unixMilliseconds = lwwElementSet.unixMilliseconds
                        found = true
                    }
                }

                if (!found) {
                    crdtSetItems.add(Pair(signedEvent, StorageTypeCRDTSetItem(event.contentType, lwwElementSet.value, lwwElementSet.unixMilliseconds, lwwElementSet.operation)))
                }
            }

            event.lwwElement?.let { lwwElement ->
                val foundIndex = crdtItems.indexOfFirst { pair ->
                    pair.second.contentType == event.contentType
                }

                var found = false
                if (foundIndex != -1) {
                    val foundPair = crdtItems[foundIndex]
                    if (foundPair.second.unixMilliseconds < lwwElement.unixMilliseconds) {
                        foundPair.second.value = lwwElement.value
                        foundPair.second.unixMilliseconds = lwwElement.unixMilliseconds
                        found = true
                    }
                }

                if (!found) {
                    crdtItems.add(Pair(signedEvent, StorageTypeCRDTItem(event.contentType, lwwElement.value, lwwElement.unixMilliseconds)))
                }
            }
        };

        val relevantEvents = arrayListOf<SignedEvent>();
        for (pair in crdtSetItems) {
            relevantEvents.add(pair.first);
        }

        for (pair in crdtItems) {
            relevantEvents.add(pair.first);
        }

        val exportBundle = ExportBundle.newBuilder()
            .setKeyPair(processHandle.processSecret.system.toProto())
            .setEvents(Protocol.Events.newBuilder()
                .addAllEvents(relevantEvents.map { it.toProto() })
                .build())
            .build();

        val urlInfo = URLInfo.newBuilder()
            .setUrlType(3)
            .setBody(exportBundle.toByteString())
            .build();

        return "polycentric://" + urlInfo.toByteArray().toBase64Url()
    }

    companion object {
        private const val TAG = "PolycentricBackupActivity";
    }
}