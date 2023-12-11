package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.polycentric.core.KeyPair
import com.futo.polycentric.core.Process
import com.futo.polycentric.core.ProcessSecret
import com.futo.polycentric.core.SignedEvent
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.base64UrlToByteArray
import com.google.zxing.integration.android.IntentIntegrator
import userpackage.Protocol
import userpackage.Protocol.ExportBundle

class PolycentricImportProfileActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _buttonScanProfile: LinearLayout;
    private lateinit var _buttonImportProfile: LinearLayout;
    private lateinit var _editProfile: EditText;

    private val _qrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        scanResult?.let {
            if (it.contents != null) {
                val scannedUrl = it.contents
                import(scannedUrl)
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_import_profile);
        setNavigationBarColorAndIcons();

        _buttonHelp = findViewById(R.id.button_help);
        _buttonScanProfile = findViewById(R.id.button_scan_profile);
        _buttonImportProfile = findViewById(R.id.button_import_profile);
        _editProfile = findViewById(R.id.edit_profile);
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish();
        };

        _buttonHelp.setOnClickListener {
            startActivity(Intent(this, PolycentricWhyActivity::class.java));
        };

        _buttonScanProfile.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt(getString(R.string.scan_a_qr_code))
            integrator.setOrientationLocked(true);
            integrator.setCameraId(0)
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.setCaptureActivity(QRCaptureActivity::class.java);
            _qrCodeResultLauncher.launch(integrator.createScanIntent())
        };

        _buttonImportProfile.setOnClickListener {
            if (_editProfile.text.isEmpty()) {
                UIDialogs.toast(this, getString(R.string.text_field_does_not_contain_any_data));
                return@setOnClickListener;
            }

            import(_editProfile.text.toString());
        };

        val url = intent.getStringExtra("url");
        if (url != null) {
            import(url);
        }
    }

    private fun import(url: String) {
        if (!url.startsWith("polycentric://")) {
            UIDialogs.toast(this, getString(R.string.not_a_valid_url));
            return;
        }

        try {
            val data = url.substring("polycentric://".length).base64UrlToByteArray();
            val urlInfo = Protocol.URLInfo.parseFrom(data);
            if (urlInfo.urlType != 3L) {
                throw Exception("Expected urlInfo struct of type ExportBundle")
            }

            val exportBundle = ExportBundle.parseFrom(urlInfo.body);
            val keyPair = KeyPair.fromProto(exportBundle.keyPair);

            val existingProcessSecret = Store.instance.getProcessSecret(keyPair.publicKey);
            if (existingProcessSecret != null) {
                UIDialogs.toast(this, getString(R.string.this_profile_is_already_imported));
                return;
            }

            val processSecret = ProcessSecret(keyPair, Process.random());
            Store.instance.addProcessSecret(processSecret);

            val processHandle = processSecret.toProcessHandle();

            for (e in exportBundle.events.eventsList) {
                try {
                    val se = SignedEvent.fromProto(e);
                    Store.instance.putSignedEvent(se);
                } catch (e: Throwable) {
                    Logger.w(TAG, "Ignored invalid event", e);
                }
            }

            StatePolycentric.instance.setProcessHandle(processHandle);
            startActivity(Intent(this@PolycentricImportProfileActivity, PolycentricProfileActivity::class.java));
            finish();
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to import profile", e);
            UIDialogs.toast(this, getString(R.string.failed_to_import_profile) + " '${e.message}'");
        }
    }

    companion object {
        private const val TAG = "PolycentricImportProfileActivity";
    }
}