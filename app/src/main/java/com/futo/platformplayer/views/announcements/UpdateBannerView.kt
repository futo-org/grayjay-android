package com.futo.platformplayer.views.announcements

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UpdateDownloadService
import com.futo.platformplayer.UpdateInstaller
import com.futo.platformplayer.UpdateNotificationManager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateUpdate
import com.futo.platformplayer.states.UpdateUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateBannerView : LinearLayout {
    private val _root: FrameLayout
    private val _iconUpdate: ImageView
    private val _textTitle: TextView
    private val _textBody: TextView
    private val _progressBar: ProgressBar
    private val _buttonAction: FrameLayout
    private val _textAction: TextView
    private val _buttonClose: ImageView

    private val _scope: CoroutineScope?

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_update_banner, this)

        _scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: StateApp.instance.scopeOrNull

        _root = findViewById(R.id.root)
        _iconUpdate = findViewById(R.id.icon_update)
        _textTitle = findViewById(R.id.text_title)
        _textBody = findViewById(R.id.text_body)
        _progressBar = findViewById(R.id.update_banner_progress)
        _buttonAction = findViewById(R.id.button_action)
        _textAction = findViewById(R.id.text_action)
        _buttonClose = findViewById(R.id.button_close)

        _buttonClose.setOnClickListener {
            StateUpdate.instance.dismissUi()
        }

        _buttonAction.setOnClickListener {
            onActionClicked()
        }

        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        StateUpdate.instance.onUiChanged.subscribe(this) {
            _scope?.launch(Dispatchers.Main) {
                refresh()
            }
        }
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        StateUpdate.instance.onUiChanged.remove(this)
    }

    private fun onActionClicked() {
        val st = StateUpdate.instance
        when (st.uiState) {
            UpdateUiState.READY -> {
                val apk = st.uiApkFile ?: return
                UpdateNotificationManager.cancelAll(context)
                UpdateInstaller.startInstall(context, st.uiVersion, apk)
            }
            UpdateUiState.FAILED -> {
                if (st.uiVersion == 0) return
                val intent = Intent(context, UpdateDownloadService::class.java).apply {
                    putExtra(UpdateDownloadService.EXTRA_VERSION, st.uiVersion)
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (t: Throwable) {
                    Logger.w(TAG, "Retry start service failed", t)
                }
            }
            UpdateUiState.DOWNLOADING -> {
                val intent = Intent(context, UpdateDownloadService::class.java).apply {
                    putExtra(UpdateDownloadService.EXTRA_VERSION, st.uiVersion)
                    putExtra(UpdateDownloadService.EXTRA_CANCEL, true)
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (t: Throwable) {
                    Logger.w(TAG, "Cancel start service failed", t)
                }
            }
            UpdateUiState.AVAILABLE -> {
                if (st.uiVersion == 0) return
                val intent = Intent(context, UpdateDownloadService::class.java).apply {
                    putExtra(UpdateDownloadService.EXTRA_VERSION, st.uiVersion)
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (t: Throwable) {
                    Logger.w(TAG, "Download start service failed", t)
                }
            }
            UpdateUiState.NONE -> {}
        }
    }

    private fun refresh() {
        val st = StateUpdate.instance
        val gateOpen = Settings.instance.autoUpdate.shouldBackgroundDownload
        val visible = gateOpen && !st.uiDismissed && st.uiState != UpdateUiState.NONE

        if (!visible) {
            _root.visibility = View.GONE
            return
        }
        _root.visibility = View.VISIBLE

        when (st.uiState) {
            UpdateUiState.AVAILABLE -> {
                _textTitle.text = "Update available (v${st.uiVersion})"
                _textBody.text = "A new Grayjay version is available."
                _textBody.visibility = View.VISIBLE
                _progressBar.visibility = View.GONE
                _textAction.text = "Download"
                _buttonAction.visibility = View.VISIBLE
            }
            UpdateUiState.DOWNLOADING -> {
                _textTitle.text = "Downloading update (v${st.uiVersion})"
                if (st.uiIndeterminate) {
                    _textBody.text = "Starting download…"
                    _progressBar.isIndeterminate = true
                } else {
                    _textBody.text = "${st.uiProgress}% downloaded"
                    _progressBar.isIndeterminate = false
                    _progressBar.progress = st.uiProgress
                }
                _textBody.visibility = View.VISIBLE
                _progressBar.visibility = View.VISIBLE
                _textAction.text = "Cancel"
                _buttonAction.visibility = View.VISIBLE
            }
            UpdateUiState.READY -> {
                _textTitle.text = "Update v${st.uiVersion} ready"
                _textBody.text = "Tap install to apply the update."
                _textBody.visibility = View.VISIBLE
                _progressBar.visibility = View.GONE
                _textAction.text = "Install"
                _buttonAction.visibility = View.VISIBLE
            }
            UpdateUiState.FAILED -> {
                _textTitle.text = "Update failed"
                val err = st.uiError
                _textBody.text = if (err.isNullOrBlank()) "Could not download v${st.uiVersion}." else err
                _textBody.visibility = View.VISIBLE
                _progressBar.visibility = View.GONE
                _textAction.text = "Retry"
                _buttonAction.visibility = View.VISIBLE
            }
            UpdateUiState.NONE -> {
                _root.visibility = View.GONE
            }
        }
    }

    companion object {
        const val TAG = "UpdateBannerView"
    }
}
