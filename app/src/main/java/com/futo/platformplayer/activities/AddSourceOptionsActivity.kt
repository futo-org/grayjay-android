package com.futo.platformplayer.activities

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.*
import com.futo.platformplayer.views.buttons.BigButton
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.CaptureActivity

class AddSourceOptionsActivity : AppCompatActivity() {
    lateinit var _buttonBack: ImageButton;

    lateinit var _buttonQR: BigButton;
    lateinit var _buttonURL: BigButton;

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
            integrator.initiateScan()
        }
        _buttonURL.onClick.subscribe {
            UIDialogs.toast(this, "Not implemented yet..");
        }
    }


    class QRCaptureActivity: CaptureActivity() {

    }
}