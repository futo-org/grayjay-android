package com.futo.platformplayer.tv

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UpdateInstaller
import com.futo.platformplayer.UpdateNotificationManager
import com.futo.platformplayer.states.StateUpdate
import com.futo.platformplayer.states.UpdateUiState
import com.futo.platformplayer.tv.fragments.TvPlayerFragment
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.text.NumberFormat

internal class TvUpdatePrompt(private val activity: TvActivity) : DefaultLifecycleObserver {
    private var dialog: TvMenuDialog? = null
    private var shownVersion = 0
    private var pendingPermission: Pair<Int, File>? = null
    private var downloadIndicator: View? = null

    init {
        activity.lifecycle.addObserver(this)
        StateUpdate.instance.onUiChanged.subscribe(this) {
            activity.runOnUiThread { showIfReady() }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        val pending = pendingPermission
        pendingPermission = null
        if (pending != null && activity.packageManager.canRequestPackageInstalls()) {
            install(pending.first, pending.second)
        } else {
            if (pending != null) shownVersion = 0
            activity.window.decorView.post { showIfReady() }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        StateUpdate.instance.onUiChanged.remove(this)
        dialog?.dismiss()
        dialog = null
        downloadIndicator?.let { (it.parent as? ViewGroup)?.removeView(it) }
        downloadIndicator = null
    }

    fun showIfReady() {
        if (BuildConfig.IS_PLAYSTORE_BUILD || activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        showDownloadProgress()
        if (dialog != null) return
        if (activity.supportFragmentManager.findFragmentById(R.id.tv_content) is TvPlayerFragment) return
        val state = StateUpdate.instance
        val apk = state.uiApkFile ?: return
        val version = state.uiVersion
        if (state.uiState != UpdateUiState.READY || version <= BuildConfig.VERSION_CODE ||
            version == shownVersion || !apk.isFile) return
        val focused = activity.currentFocus
        shownVersion = version
        dialog = TvMenuDialog(activity).also { window ->
            window.panel.title(activity.getString(R.string.tv_update_ready, version))
            val menu = window.panel.menu()
            menu.addView(TvUi.text(activity, activity.getString(R.string.tv_update_ready_hint), 16f, true).apply {
                setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(24))
            })
            val update = window.panel.option(activity.getString(R.string.tv_update_install)) {
                window.dismiss()
                install(version, apk)
            }.apply { id = R.id.tv_update_install }
            menu.addView(update)
            menu.addView(window.panel.option(activity.getString(R.string.tv_update_later)) { window.dismiss() })
            window.setOnDismissListener {
                dialog = null
                focused?.takeIf { it.isAttachedToWindow }?.requestFocus()
            }
            window.setOnShowListener { update.requestFocus() }
            window.show()
        }
    }

    private fun showDownloadProgress() {
        val state = StateUpdate.instance
        if (state.uiState != UpdateUiState.DOWNLOADING) {
            downloadIndicator?.isVisible = false
            return
        }
        val indicator = downloadIndicator ?: run {
            val root = activity.findViewById<FrameLayout>(android.R.id.content)
            activity.layoutInflater.inflate(R.layout.view_tv_update_progress, root, false).also {
                root.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                downloadIndicator = it
            }
        }
        val progress = state.uiProgress.coerceIn(0, 100)
        indicator.findViewById<TextView>(R.id.tv_update_percentage).apply {
            isVisible = !state.uiIndeterminate
            text = NumberFormat.getPercentInstance().format(progress / 100.0)
        }
        indicator.findViewById<LinearProgressIndicator>(R.id.tv_update_progress_bar).apply {
            if (isIndeterminate != state.uiIndeterminate) {
                hide()
                isIndeterminate = state.uiIndeterminate
            }
            if (!state.uiIndeterminate) setProgressCompat(progress, false)
            show()
        }
        indicator.isVisible = true
    }

    private fun install(version: Int, apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) pendingPermission = version to apk
        else UIDialogs.toast(activity, activity.getString(R.string.installing_update))
        UpdateNotificationManager.cancelAll(activity)
        UpdateInstaller.startInstall(activity, version, apk)
    }
}
