package com.futo.platformplayer.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.*
import com.futo.platformplayer.views.buttons.BigButton
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.CaptureActivity

class AddSourceOptionsActivity : AppCompatActivity() {
    lateinit var _buttonBack: ImageButton;

    lateinit var _buttonQR: BigButton;
    lateinit var _buttonURL: BigButton;

    private val _qrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        scanResult?.let {
            val content = it.contents
            if (content == null) {
                UIDialogs.toast(this, "Failed to scan QR code")
                return@let
            }

            val url = if (content.startsWith("https://")) {
                content
            } else if (content.startsWith("grayjay://plugin/")) {
                content.substring("grayjay://plugin/".length)
            } else {
                UIDialogs.toast(this, "Not a plugin URL")
                return@let;
            }

            val intent = Intent(this, AddSourceActivity::class.java).apply {
                data = Uri.parse(url);
            };
            startActivity(intent);
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_source_options);
        setNavigationBarColorAndIcons();

        _buttonBack = findViewById(R.id.button_back);

        _buttonQR = findViewById(R.id.option_qr);
        _buttonURL = findViewById(R.id.option_url);

        _buttonBack.setOnClickListener {
            finish();
        };

        _buttonQR.onClick.subscribe {
            val integrator = IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt("Scan a QR Code")
            integrator.setOrientationLocked(true);
            integrator.setCameraId(0)
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.setCaptureActivity(QRCaptureActivity::class.java);
            _qrCodeResultLauncher.launch(integrator.createScanIntent())
        }

        _buttonURL.onClick.subscribe {
            UIDialogs.toast(this, "Not implemented yet..");
        }
    }


    class QRCaptureActivity: CaptureActivity() {

    }
}