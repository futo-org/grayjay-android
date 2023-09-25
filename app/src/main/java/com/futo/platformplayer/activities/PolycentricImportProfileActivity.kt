package com.futo.platformplayer.activities

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StatePolycentric
import com.futo.polycentric.core.*
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import userpackage.Protocol
import userpackage.Protocol.ExportBundle

class PolycentricImportProfileActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _buttonScanProfile: LinearLayout;
    private lateinit var _buttonImportProfile: LinearLayout;
    private lateinit var _editProfile: EditText;

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
            val integrator = IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan a QR code");
            integrator.initiateScan();
        };

        _buttonImportProfile.setOnClickListener {
            if (_editProfile.text.isEmpty()) {
                UIDialogs.toast(this, "Text field does not contain any data");
                return@setOnClickListener;
            }

            import(_editProfile.text.toString());
        };

        val url = intent.getStringExtra("url");
        if (url != null) {
            import(url);
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val scannedUrl = result.contents;
                import(scannedUrl);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun import(url: String) {
        if (!url.startsWith("polycentric://")) {
            UIDialogs.toast(this, "Not a valid URL");
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
                UIDialogs.toast(this, "This profile is already imported");
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
            UIDialogs.toast(this, "Failed to import profile: '${e.message}'");
        }
    }

    companion object {
        private const val TAG = "PolycentricImportProfileActivity";
    }
}