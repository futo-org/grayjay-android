package com.futo.platformplayer.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.LegacyExportParseException
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.states.StatePolycentric.LegacyImportResult
import com.futo.platformplayer.views.overlays.LoaderOverlay
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Imports a Polycentric v1 profile export (pasted, scanned, or from a file) and migrates it to the
 * Harbor identity created for it by the Grayjay migrator. Finishes with [Activity.RESULT_OK] once
 * signed in to that identity.
 */
class PolycentricImportProfileActivity : AppCompatActivity() {
    private lateinit var _editProfile: EditText
    private lateinit var _loaderOverlay: LoaderOverlay
    private var _importing = false

    private val _qrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        IntentIntegrator.parseActivityResult(result.resultCode, result.data)?.contents?.let { import(it) }
    }

    private val _filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val content = try {
            val size = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0
            if (size > MAX_FILE_SIZE) {
                UIDialogs.toast(this, getString(R.string.failed_to_import_profile))
                return@registerForActivityResult
            }
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read profile export file", e)
            null
        }
        if (content.isNullOrBlank()) {
            UIDialogs.toast(this, getString(R.string.failed_to_import_profile))
            return@registerForActivityResult
        }
        import(content)
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polycentric_import_profile)
        setNavigationBarColorAndIcons()

        _editProfile = findViewById(R.id.edit_profile)
        _loaderOverlay = findViewById(R.id.loader_overlay)
        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.button_help).setOnClickListener {
            startActivity(Intent(this, PolycentricWhyActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.button_scan_profile).setOnClickListener {
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

        findViewById<LinearLayout>(R.id.button_import_file).setOnClickListener {
            _filePickerLauncher.launch("text/plain")
        }

        findViewById<LinearLayout>(R.id.button_import_profile).setOnClickListener {
            if (_editProfile.text.isBlank()) {
                UIDialogs.toast(this, getString(R.string.text_field_does_not_contain_any_data))
                return@setOnClickListener
            }
            import(_editProfile.text.toString())
        }
    }

    private fun import(text: String) {
        // Ignore further imports until this one resolves, such that a double tap can't run two.
        if (_importing) {
            return
        }
        _importing = true
        _loaderOverlay.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                StatePolycentric.instance.importLegacyExport(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to import legacy profile", e)
                withContext(Dispatchers.Main) {
                    _loaderOverlay.hide()
                    _importing = false
                    if (e is LegacyExportParseException) {
                        UIDialogs.toast(this@PolycentricImportProfileActivity, getString(R.string.failed_to_import_profile) + " '${e.message}'")
                    } else {
                        UIDialogs.showGeneralRetryErrorDialog(this@PolycentricImportProfileActivity,
                            getString(R.string.polycentric_migration_failed), e, { import(text) })
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _loaderOverlay.hide()
                _importing = false
                val context = this@PolycentricImportProfileActivity
                when (result) {
                    is LegacyImportResult.Migrated ->
                        UIDialogs.showDialogOk(context, R.drawable.ic_check, getString(R.string.polycentric_migration_complete)) { openProfile() }
                    is LegacyImportResult.AlreadyImported -> {
                        UIDialogs.toast(context, getString(R.string.this_profile_is_already_imported))
                        openProfile()
                    }
                    LegacyImportResult.NotFound ->
                        UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, getString(R.string.polycentric_migration_not_found))
                    LegacyImportResult.AlreadyClaimed ->
                        UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, getString(R.string.polycentric_migration_already_claimed))
                }
            }
        }
    }

    /** Signals the caller that the user is now signed in, such that it opens the profile screen. */
    private fun openProfile() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        private const val TAG = "PolycentricImportProfileActivity"
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024
    }
}
