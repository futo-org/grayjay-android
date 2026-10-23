package com.futo.platformplayer.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PairingExpiredException
import com.futo.platformplayer.polycentric.PairingIdentityExistsException
import com.futo.platformplayer.polycentric.PairingPreview
import com.futo.platformplayer.polycentric.parsePairingInfo
import com.futo.platformplayer.polycentric.publicKeyToString
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.others.HarborEmojiFingerprintView
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import polycentric.v2.PairingInfo

/**
 * Claims an identity already present on the issuer device, as a second device for the identity.
 * The issuer will show a pairing link, and this device will join that session.
 */
class PolycentricPairClaimerActivity : AppCompatActivity() {
    private lateinit var _layoutEntry: View
    private lateinit var _editLink: EditText
    private lateinit var _layoutLoading: View
    private lateinit var _textLoading: TextView
    private lateinit var _layoutConfirm: View
    private lateinit var _imageIssuerAvatar: ImageView
    private lateinit var _textIssuerName: TextView
    private lateinit var _textIssuerIdentity: TextView
    private lateinit var _buttonConfirmPair: TextView
    private lateinit var _layoutWaiting: View
    private lateinit var _fingerprint: HarborEmojiFingerprintView
    private lateinit var _textClaimerKey: TextView
    private lateinit var _textWaitingError: TextView
    private lateinit var _textWaitingCountdown: TextView
    private lateinit var _layoutResult: View
    private lateinit var _imageResult: ImageView
    private lateinit var _textResult: TextView
    private lateinit var _textResultDetails: TextView
    private lateinit var _buttonResult: TextView

    /** The running step of the pairing, cancelled when the user leaves it. */
    private var _job: Job? = null
    private var _preview: PairingPreview? = null
    private var _claimed = false

    private val _qrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val contents = IntentIntegrator.parseActivityResult(result.resultCode, result.data)?.contents
            ?: return@registerForActivityResult
        _editLink.text.clear()
        _editLink.text.append(contents)
        startWithLink(contents)
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOpen = true
        setContentView(R.layout.activity_polycentric_pair_claimer)
        setNavigationBarColorAndIcons()

        _layoutEntry = findViewById(R.id.layout_entry)
        _editLink = findViewById(R.id.edit_link)
        _layoutLoading = findViewById(R.id.layout_loading)
        _textLoading = findViewById(R.id.text_loading)
        _layoutConfirm = findViewById(R.id.layout_confirm)
        _imageIssuerAvatar = findViewById(R.id.image_issuer_avatar)
        _textIssuerName = findViewById(R.id.text_issuer_name)
        _textIssuerIdentity = findViewById(R.id.text_issuer_identity)
        _buttonConfirmPair = findViewById(R.id.button_confirm_pair)
        _layoutWaiting = findViewById(R.id.layout_waiting)
        _fingerprint = findViewById(R.id.fingerprint)
        _textClaimerKey = findViewById(R.id.text_claimer_key)
        _textWaitingError = findViewById(R.id.text_waiting_error)
        _textWaitingCountdown = findViewById(R.id.text_waiting_countdown)
        _layoutResult = findViewById(R.id.layout_result)
        _imageResult = findViewById(R.id.image_result)
        _textResult = findViewById(R.id.text_result)
        _textResultDetails = findViewById(R.id.text_result_details)
        _buttonResult = findViewById(R.id.button_result)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.button_scan_qr).setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt(getString(R.string.scan_a_qr_code))
            integrator.setOrientationLocked(true)
            integrator.setCameraId(0)
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.setCaptureActivity(QRCaptureActivity::class.java)
            _qrCodeResultLauncher.launch(integrator.createScanIntent())
        }

        findViewById<TextView>(R.id.button_continue).setOnClickListener {
            startWithLink(_editLink.text.toString())
        }

        findViewById<TextView>(R.id.button_confirm_cancel).setOnClickListener {
            finish()
        }

        _buttonConfirmPair.setOnClickListener {
            val preview = _preview ?: return@setOnClickListener
            join(preview)
        }

        showPanel(_layoutEntry)

        val link = intent.getStringExtra(EXTRA_LINK)
        if (link != null) {
            _editLink.text.append(link)
            startWithLink(link)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isOpen = false
        _job?.cancel()
        if (!_claimed) {
            cancelClaimer()
        }
    }

    private fun showPanel(panel: View) {
        for (view in listOf(_layoutEntry, _layoutLoading, _layoutConfirm, _layoutWaiting, _layoutResult)) {
            view.visibility = if (view === panel) View.VISIBLE else View.GONE
        }
    }

    private fun showLoading(text: Int) {
        _textLoading.setText(text)
        showPanel(_layoutLoading)
    }

    private fun showResult(@DrawableRes icon: Int, summary: String, details: String?, button: Int, onButton: () -> Unit) {
        _imageResult.setImageResource(icon)
        _textResult.text = summary
        _textResultDetails.text = details
        _textResultDetails.visibility = if (details.isNullOrEmpty()) View.GONE else View.VISIBLE
        _buttonResult.setText(button)
        _buttonResult.setOnClickListener { onButton() }
        showPanel(_layoutResult)
    }

    /** Shows an error with a button that undoes the pairing and returns to the entry panel. */
    private fun showError(e: Throwable) {
        val (summary, details) = when (e) {
            is PairingExpiredException -> getString(R.string.pairing_expired) to null
            is PairingIdentityExistsException -> getString(R.string.pairing_identity_exists) to null
            else -> getString(R.string.pairing_failed) to (e.message ?: e.javaClass.simpleName)
        }
        showFailure(summary, details)
    }

    /** Shows a failure with a button that undoes the pairing and returns to the entry panel. */
    private fun showFailure(summary: String, details: String?) {
        showResult(R.drawable.ic_pair_fail, summary, details, R.string.pairing_go_back) {
            cancelClaimer()
            showPanel(_layoutEntry)
        }
    }

    /** Helper that runs pairing steps as [block]s, and replaces/cancels earlier [block]s. */
    private fun launchStep(block: suspend () -> Unit) {
        _job?.cancel()
        _job = lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.w(TAG, "Pairing failed.", e)
                showError(e)
            }
        }
    }

    private fun startWithLink(text: String) {
        val info = parsePairingInfo(text)
        if (info == null) {
            showResult(R.drawable.ic_pair_fail, getString(R.string.pairing_invalid_link), null, R.string.pairing_go_back) {
                showPanel(_layoutEntry)
            }
            return
        }

        showLoading(R.string.pairing_loading_session)
        launchStep {
            val preview = withContext(Dispatchers.IO) { StatePolycentric.instance.previewPairing(info) }
            showConfirm(preview)
        }
    }

    private fun showConfirm(preview: PairingPreview) {
        _preview = preview
        _textIssuerName.text = preview.profile?.name ?: getString(R.string.unknown)
        _textIssuerIdentity.text = preview.issuerIdentity
        Glide.with(_imageIssuerAvatar)
            .load(preview.profile?.avatarUrl)
            .placeholder(R.drawable.placeholder_profile)
            .crossfade()
            .into(_imageIssuerAvatar)
        showPanel(_layoutConfirm)

        // Briefly disable Pair so that a stray tap doesn't confirm.
        _buttonConfirmPair.isEnabled = false
        _buttonConfirmPair.alpha = 0.5f
        launchStep {
            delay(PAIR_ENABLE_DELAY_MS)
            _buttonConfirmPair.isEnabled = true
            _buttonConfirmPair.alpha = 1f
        }
    }

    private fun join(preview: PairingPreview) {
        val info = preview.session.pairingInfo
        showLoading(R.string.pairing_joining_session)
        launchStep {
            val key = withContext(Dispatchers.IO) {
                val key = StatePolycentric.instance.preparePairingClaimer()
                StatePolycentric.instance.joinPairing(info)
                key
            }

            _fingerprint.setPublicKey(key)
            _textClaimerKey.text = publicKeyToString(key)
            _textWaitingError.visibility = View.GONE
            showPanel(_layoutWaiting)

            if (!awaitAuthorization(info, preview.session.expiresAt)) {
                showFailure(getString(R.string.pairing_expired_waiting), getString(R.string.pairing_expired_waiting_details))
                return@launchStep
            }

            showLoading(R.string.pairing_completing_setup)
            withContext(Dispatchers.IO) { StatePolycentric.instance.claimPairedIdentity(preview.session) }
            _claimed = true
            showResult(R.drawable.ic_pair_success, getString(R.string.pairing_claimer_success), null, R.string.pairing_continue) {
                finishWithSuccess()
            }
        }
    }

    /**
     * Polls until the issuer authorizes our key, showing the time left and any failed polls.
     * Returns false if the session expires first. Throws the last error once
     * [MAX_POLL_FAILURES] polls in a row have failed.
     */
    private suspend fun awaitAuthorization(info: PairingInfo, expiresAt: Long): Boolean = coroutineScope {
        val countdown = launch { showCountdown(expiresAt) }
        try {
            var failures = 0
            while (System.currentTimeMillis() < expiresAt) {
                try {
                    if (withContext(Dispatchers.IO) { StatePolycentric.instance.pollPairingAuthorization(info) }) {
                        return@coroutineScope true
                    }
                    failures = 0
                    _textWaitingError.visibility = View.GONE
                } catch (e: PairingExpiredException) {
                    return@coroutineScope false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    failures++
                    Logger.w(TAG, "Failed to poll for pairing authorization ($failures in a row).", e)
                    if (failures >= MAX_POLL_FAILURES) {
                        throw e
                    }
                    _textWaitingError.text = getString(R.string.pairing_poll_failed_retrying) + "\n" + (e.message ?: e.javaClass.simpleName)
                    _textWaitingError.visibility = View.VISIBLE
                }
                delay(POLL_INTERVAL_MS)
            }
            false
        } finally {
            countdown.cancel()
        }
    }

    private suspend fun showCountdown(expiresAt: Long) {
        while (true) {
            val remaining = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            _textWaitingCountdown.text = getString(R.string.pairing_valid_for, "%02d:%02d".format(remaining / 60, remaining % 60))
            delay(1000)
        }
    }

    private fun finishWithSuccess() {
        if (callingActivity != null) {
            setResult(Activity.RESULT_OK)
        } else {
            startActivity(Intent(this, PolycentricProfileActivity::class.java))
        }
        finish()
    }

    /** Restores the identity that was active before pairing, if pairing started. */
    private fun cancelClaimer() {
        _preview = null
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                StatePolycentric.instance.cancelPairingClaimer()
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to cancel pairing.", e)
            }
        }
    }

    companion object {
        private const val TAG = "PolycentricPairClaimerActivity"
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_POLL_FAILURES = 5
        private const val PAIR_ENABLE_DELAY_MS = 1000L

        /** A pairing link or code to start with, skipping the entry panel. */
        const val EXTRA_LINK = "link"

        /** Whether an instance exists, so the launcher icon can bring it back. See [MainActivity]. */
        var isOpen = false
            private set
    }
}
