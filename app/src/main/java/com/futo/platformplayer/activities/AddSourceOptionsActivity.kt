package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.*
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput
import com.google.zxing.integration.android.IntentIntegrator

class AddSourceOptionsActivity : AppCompatActivity() {
    lateinit var _buttonBack: ImageButton;

    lateinit var _overlayContainer: FrameLayout;
    lateinit var _buttonQR: BigButton;
    lateinit var _buttonBrowse: BigButton;
    lateinit var _buttonURL: BigButton;
    lateinit var _buttonPlugins: BigButton;

    private val _qrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        scanResult?.let {
            val content = it.contents
            if (content == null) {
                UIDialogs.toast(this, getString(R.string.failed_to_scan_qr_code))
                return@let
            }

            val url = if (content.startsWith("https://")) {
                content
            } else if (content.startsWith("grayjay://plugin/")) {
                content.substring("grayjay://plugin/".length)
            } else {
                UIDialogs.toast(this, getString(R.string.not_a_plugin_url))
                return@let;
            }

            val intent = Intent(this, AddSourceActivity::class.java).apply {
                data = Uri.parse(url);
            };
            startActivity(intent);
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_source_options);
        setNavigationBarColorAndIcons();

        _overlayContainer = findViewById(R.id.overlay_container);
        _buttonBack = findViewById(R.id.button_back);

        _buttonQR = findViewById(R.id.option_qr);
        _buttonBrowse = findViewById(R.id.option_browse);
        _buttonURL = findViewById(R.id.option_url);
        _buttonPlugins = findViewById(R.id.option_plugins);

        _buttonBack.setOnClickListener {
            finish();
        };

        _buttonQR.onClick.subscribe {
            val integrator = IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt(getString(R.string.scan_a_qr_code))
            integrator.setOrientationLocked(true);
            integrator.setCameraId(0)
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.setCaptureActivity(QRCaptureActivity::class.java);
            _qrCodeResultLauncher.launch(integrator.createScanIntent())
        }
        _buttonBrowse.onClick.subscribe {
            startActivity(MainActivity.getTabIntent(this, "BROWSE_PLUGINS"));
        }

        _buttonURL.onClick.subscribe {
            val nameInput = SlideUpMenuTextInput(this, "ex. https://yourplugin.com/config.json");
            UISlideOverlays.showOverlay(_overlayContainer, "Enter your url", "Install", {

                val content = nameInput.text;

                val url = if (content.startsWith("https://")) {
                    content
                } else if (content.startsWith("grayjay://plugin/")) {
                    content.substring("grayjay://plugin/".length)
                } else {
                    UIDialogs.toast(this, getString(R.string.not_a_plugin_url))
                    return@showOverlay;
                }

                val intent = Intent(this, AddSourceActivity::class.java).apply {
                    data = Uri.parse(url);
                };
                startActivity(intent);
            }, nameInput)
        }
    }
}