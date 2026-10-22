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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PairingExpiredException
import com.futo.platformplayer.polycentric.encodePairingLink
import com.futo.platformplayer.polycentric.publicKeyToString
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.others.Checkbox
import com.futo.platformplayer.views.others.HarborEmojiFingerprintView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.polycentric.core.PairingSessionManager
import polycentric.v2.PublicKey

/**
 * Issues a pairing link and authorizes another device (the claimer) as a second device for the
 * current identity.
 */
class PolycentricPairIssuerActivity : AppCompatActivity() {
    private lateinit var _layoutLoading: View
    private lateinit var _layoutSession: View
    private lateinit var _imageQR: ImageView
    private lateinit var _textCountdown: TextView
    private lateinit var _textPollError: TextView
    private lateinit var _layoutApproval: View
    private lateinit var _fingerprint: HarborEmojiFingerprintView
    private lateinit var _textClaimerKey: TextView
    private lateinit var _layoutRotationKey: View
    private lateinit var _checkboxRotationKey: Checkbox
    private lateinit var _layoutApprovalButtons: View
    private lateinit var _loaderApproving: View
    private lateinit var _layoutResult: View
    private lateinit var _imageResult: ImageView
    private lateinit var _textResult: TextView
    private lateinit var _textResultDetails: TextView

    private var _session: PairingSessionManager.PairingSession? = null
    private var _link: String? = null
    private var _pollJob: Job? = null
    private var _countdownJob: Job? = null
    private var _approving = false
    private var _finished = false

    /**
     * Every claimer key in the pairing session. Append-only, so that the server can't change a key
     * the user is already looking at.
     */
    private val _claimers = LinkedHashMap<String, PublicKey>()
    /** Index of [_claimers] for the next key to show. Denied keys are skipped. */
    private var _claimerCursor = 0

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOpen = true
        setContentView(R.layout.activity_polycentric_pair_issuer)
        setNavigationBarColorAndIcons()

        _layoutLoading = findViewById(R.id.layout_loading)
        _layoutSession = findViewById(R.id.layout_session)
        _imageQR = findViewById(R.id.image_qr)
        _textCountdown = findViewById(R.id.text_countdown)
        _textPollError = findViewById(R.id.text_poll_error)
        _layoutApproval = findViewById(R.id.layout_approval)
        _fingerprint = findViewById(R.id.fingerprint)
        _textClaimerKey = findViewById(R.id.text_claimer_key)
        _layoutRotationKey = findViewById(R.id.layout_rotation_key)
        _checkboxRotationKey = findViewById(R.id.checkbox_rotation_key)
        _layoutApprovalButtons = findViewById(R.id.layout_approval_buttons)
        _loaderApproving = findViewById(R.id.loader_approving)
        _layoutResult = findViewById(R.id.layout_result)
        _imageResult = findViewById(R.id.image_result)
        _textResult = findViewById(R.id.text_result)
        _textResultDetails = findViewById(R.id.text_result_details)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.button_done).setOnClickListener {
            finish()
        }

        _imageQR.setOnClickListener {
            val link = _link ?: return@setOnClickListener
            startActivity(QRCodeFullscreenActivity.createIntent(this, link))
        }

        findViewById<LinearLayout>(R.id.button_copy).setOnClickListener {
            val link = _link ?: return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.pairing_copy_link), link))
            UIDialogs.toast(this, getString(R.string.pairing_link_copied))
        }

        findViewById<LinearLayout>(R.id.button_share).setOnClickListener {
            val link = _link ?: return@setOnClickListener
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
            }
            startActivity(Intent.createChooser(share, getString(R.string.share)))
        }

        // By default, authorize the claimer's key as a rotation key.
        _checkboxRotationKey.value = true
        _layoutRotationKey.setOnClickListener {
            if (!_approving) {
                _checkboxRotationKey.value = !_checkboxRotationKey.value
            }
        }

        findViewById<TextView>(R.id.button_deny).setOnClickListener {
            if (_approving) {
                return@setOnClickListener
            }
            // Denying only skips the key here; the claimer keeps waiting until the session expires.
            _claimerCursor++
            showNextClaimer()
        }

        findViewById<TextView>(R.id.button_approve).setOnClickListener {
            approveCurrentClaimer()
        }

        showPanel(_layoutLoading)
        createSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        isOpen = false
    }

    private fun showPanel(panel: View) {
        for (view in listOf(_layoutLoading, _layoutSession, _layoutApproval, _layoutResult)) {
            view.visibility = if (view === panel) View.VISIBLE else View.GONE
        }
    }

    /** Shows a final state and stops the session. */
    private fun showResult(@DrawableRes icon: Int, summary: String, details: String?) {
        _finished = true
        _pollJob?.cancel()
        _countdownJob?.cancel()
        _imageResult.setImageResource(icon)
        _textResult.text = summary
        _textResultDetails.text = details
        _textResultDetails.visibility = if (details.isNullOrEmpty()) View.GONE else View.VISIBLE
        showPanel(_layoutResult)
    }

    private fun showError(e: Throwable) {
        if (e is PairingExpiredException) {
            showResult(R.drawable.ic_pair_fail, getString(R.string.pairing_expired), null)
        } else {
            showResult(R.drawable.ic_pair_fail, getString(R.string.pairing_failed), e.message ?: e.javaClass.simpleName)
        }
    }

    private fun createSession() {
        lifecycleScope.launch {
            val session = try {
                withContext(Dispatchers.IO) { StatePolycentric.instance.startIssuerPairing() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to create pairing session.", e)
                showError(e)
                return@launch
            }

            val link = encodePairingLink(session.pairingInfo)
            _session = session
            _link = link

            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, resources.displayMetrics).toInt()
            try {
                _imageQR.setImageBitmap(withContext(Dispatchers.Default) { generateQRCode(link, size) })
            } catch (e: Throwable) {
                Logger.e(TAG, getString(R.string.failed_to_generate_qr_code), e)
            }

            showPanel(_layoutSession)
            startCountdown(session.expiresAt)
            startPolling(session)
        }
    }

    private fun startCountdown(expiresAt: Long) {
        _countdownJob = lifecycleScope.launch {
            while (isActive) {
                val remaining = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                _textCountdown.text = getString(R.string.pairing_valid_for, "%02d:%02d".format(remaining / 60, remaining % 60))
                if (remaining <= 0 && !_approving) {
                    showResult(R.drawable.ic_pair_fail, getString(R.string.pairing_expired), null)
                    return@launch
                }
                delay(1000)
            }
        }
    }

    /**
     * Polls for claimers joining the session, showing any failed polls. Ends the session with the
     * last error once [MAX_POLL_FAILURES] polls in a row have failed.
     */
    private fun startPolling(session: PairingSessionManager.PairingSession) {
        val info = session.pairingInfo
        _pollJob = lifecycleScope.launch {
            var failures = 0
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_approving) {
                    continue
                }

                val keys = try {
                    withContext(Dispatchers.IO) { StatePolycentric.instance.pollPairingClaimers(info) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PairingExpiredException) {
                    showError(e)
                    return@launch
                } catch (e: Throwable) {
                    failures++
                    Logger.w(TAG, "Failed to poll for pairing claimers ($failures in a row).", e)
                    if (failures >= MAX_POLL_FAILURES) {
                        if (!_finished && !_approving) {
                            showError(e)
                        }
                        return@launch
                    }
                    _textPollError.text = getString(R.string.pairing_poll_claimers_failed_retrying) + "\n" + (e.message ?: e.javaClass.simpleName)
                    _textPollError.visibility = View.VISIBLE
                    continue
                }
                failures = 0
                _textPollError.visibility = View.GONE

                if (_finished || _approving) {
                    continue
                }

                var added = false
                for (key in keys) {
                    added = _claimers.putIfAbsent(publicKeyToString(key), key) == null || added
                }

                if (added && _layoutSession.visibility == View.VISIBLE) {
                    showNextClaimer()
                }
            }
        }
    }

    /**
     * Shows the next claimer in the pairing session awaiting approval, or the pairing link if
     * there is none.
     */
    private fun showNextClaimer() {
        if (_finished) {
            return
        }
        val key = _claimers.values.elementAtOrNull(_claimerCursor)
        if (key == null) {
            showPanel(_layoutSession)
            return
        }

        _fingerprint.setPublicKey(key)
        _textClaimerKey.text = publicKeyToString(key)
        setApproving(false)
        showPanel(_layoutApproval)
    }

    private fun setApproving(approving: Boolean) {
        _approving = approving
        _layoutApprovalButtons.visibility = if (approving) View.GONE else View.VISIBLE
        _loaderApproving.visibility = if (approving) View.VISIBLE else View.GONE
        _layoutRotationKey.isEnabled = !approving
        _checkboxRotationKey.isEnabled = !approving
    }

    private fun approveCurrentClaimer() {
        val session = _session ?: return
        val key = _claimers.values.elementAtOrNull(_claimerCursor) ?: return
        if (_approving || _finished) {
            return
        }
        val asRotationKey = _checkboxRotationKey.value
        setApproving(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    StatePolycentric.instance.approvePairingClaimer(session, key, asRotationKey)
                }
                showResult(R.drawable.ic_pair_success, getString(R.string.pairing_issuer_success), getString(R.string.pairing_issuer_success_details))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to approve pairing claimer.", e)
                showError(e)
            } finally {
                _approving = false
            }
        }
    }

    private fun generateQRCode(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
    }

    companion object {
        private const val TAG = "PolycentricPairIssuerActivity"
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_POLL_FAILURES = 5

        /** Whether an instance exists, so the launcher icon can bring it back. See [MainActivity]. */
        var isOpen = false
            private set
    }
}
