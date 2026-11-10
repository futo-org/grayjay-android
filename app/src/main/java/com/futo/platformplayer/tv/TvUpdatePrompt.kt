package com.futo.platformplayer.tv

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
import java.io.File

internal class TvUpdatePrompt(private val activity: TvActivity) : DefaultLifecycleObserver {
    private var dialog: TvMenuDialog? = null
    private var shownVersion = 0
    private var pendingPermission: Pair<Int, File>? = null

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
    }

    fun showIfReady() {
        if (BuildConfig.IS_PLAYSTORE_BUILD || activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || dialog != null) return
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

    private fun install(version: Int, apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) pendingPermission = version to apk
        else UIDialogs.toast(activity, activity.getString(R.string.installing_update))
        UpdateNotificationManager.cancelAll(activity)
        UpdateInstaller.startInstall(activity, version, apk)
    }
}
