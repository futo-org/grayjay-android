package com.futo.platformplayer.activities

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SyncPairActivity : AppCompatActivity() {
    private lateinit var _editCode: EditText

    private lateinit var _layoutPairing: LinearLayout
    private lateinit var _textPairingStatus: TextView

    private lateinit var _layoutPairingSuccess: LinearLayout

    private lateinit var _layoutPairingError: LinearLayout
    private lateinit var _textError: TextView

    private val _qrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        scanResult?.let {
            if (it.contents != null) {
                _editCode.text.clear()
                _editCode.text.append(it.contents)
                pair(it.contents)
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_pair)
        setNavigationBarColorAndIcons()

        _editCode = findViewById(R.id.edit_code)
        _layoutPairing = findViewById(R.id.layout_pairing)
        _textPairingStatus = findViewById(R.id.text_pairing_status)
        _layoutPairingSuccess = findViewById(R.id.layout_pairing_success)
        _layoutPairingError = findViewById(R.id.layout_pairing_error)
        _textError = findViewById(R.id.text_error)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.button_scan_qr).setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt(getString(R.string.scan_a_qr_code))
            integrator.setOrientationLocked(true);
            integrator.setCameraId(0)
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.setCaptureActivity(QRCaptureActivity::class.java);
            _qrCodeResultLauncher.launch(integrator.createScanIntent())
        }

        findViewById<LinearLayout>(R.id.button_link_new_device).setOnClickListener {
            pair(_editCode.text.toString())
        }

        _layoutPairingSuccess.setOnClickListener {
            _layoutPairingSuccess.visibility = View.GONE
        }
        _layoutPairingError.setOnClickListener {
            _layoutPairingError.visibility = View.GONE
        }
        _layoutPairingSuccess.visibility = View.GONE
        _layoutPairingError.visibility = View.GONE
    }

    fun pair(url: String) {
        try {
            _layoutPairing.visibility = View.VISIBLE
            _textPairingStatus.text = "Parsing text..."

            if (!url.startsWith("grayjay://sync/")) {
                throw Exception("Not a valid URL: $url")
            }

            val deviceInfo: SyncDeviceInfo = Json.decodeFromString<SyncDeviceInfo>(Base64.decode(url.substring("grayjay://sync/".length), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).decodeToString())
            if (StateSync.instance.isAuthorized(deviceInfo.publicKey)) {
                throw Exception("This device is already paired")
            }

            _textPairingStatus.text = "Connecting..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    StateSync.instance.connect(deviceInfo) { session, complete, message ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (complete) {
                                _layoutPairingSuccess.visibility = View.VISIBLE
                                _layoutPairing.visibility = View.GONE
                            } else {
                                _textPairingStatus.text = message
                            }
                        }
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _layoutPairingError.visibility = View.VISIBLE
                        if(e.message == "Failed to connect") {
                            _textError.text = "Failed to connect.\n\nThis may be due to not being on the same network, due to firewall, or vpn.\nSync currently operates only over local direct connections."
                        }
                        else
                            _textError.text = e.message
                        _layoutPairing.visibility = View.GONE
                        Logger.e(TAG, "Failed to pair", e)
                    }
                }
            }
        } catch(e: Throwable) {
            _layoutPairingError.visibility = View.VISIBLE
            _textError.text = e.message
            _layoutPairing.visibility = View.GONE
            Logger.e(TAG, "Failed to pair", e)
        } finally {
            _layoutPairing.visibility = View.GONE
        }
    }

    companion object {
        private const val TAG = "SyncPairActivity"
    }
}