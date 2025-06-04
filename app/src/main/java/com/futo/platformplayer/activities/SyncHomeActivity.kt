package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.LinkType
import com.futo.platformplayer.sync.internal.SyncSession
import com.futo.platformplayer.views.sync.SyncDeviceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncHomeActivity : AppCompatActivity() {
    private lateinit var _layoutDevices: LinearLayout
    private lateinit var _layoutEmpty: LinearLayout
    private val _viewMap: MutableMap<String, SyncDeviceView> = mutableMapOf()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (StateApp.instance.contextOrNull == null) {
            Logger.w(TAG, "No main activity, restarting main.")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_sync_home)
        setNavigationBarColorAndIcons()

        _layoutDevices = findViewById(R.id.layout_devices)
        _layoutEmpty = findViewById(R.id.layout_empty)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.button_link_new_device).setOnClickListener {
            startActivity(Intent(this@SyncHomeActivity, SyncPairActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.button_show_pairing_code).setOnClickListener {
            startActivity(Intent(this@SyncHomeActivity, SyncShowPairingCodeActivity::class.java))
        }

        initializeDevices()

        StateSync.instance.deviceUpdatedOrAdded.subscribe(this) { publicKey, session ->
            lifecycleScope.launch(Dispatchers.Main) {
                val view = _viewMap[publicKey]
                if (!session.isAuthorized) {
                    if (view != null) {
                        _viewMap.remove(publicKey)
                    }
                    return@launch
                }

                if (view == null) {
                    val syncDeviceView = SyncDeviceView(this@SyncHomeActivity)
                    syncDeviceView.onRemove.subscribe {
                        StateApp.instance.scopeOrNull?.launch {
                            StateSync.instance.delete(publicKey)
                        }
                    }
                    val v = updateDeviceView(syncDeviceView, publicKey, session)
                    _layoutDevices.addView(v, 0)
                    _viewMap[publicKey] = v
                } else {
                    updateDeviceView(view, publicKey, session)
                }

                updateEmptyVisibility()
            }
        }

        StateSync.instance.deviceRemoved.subscribe(this) {
            lifecycleScope.launch(Dispatchers.Main) {
                val view = _viewMap[it]
                if (view != null) {
                    _layoutDevices.removeView(view)
                    _viewMap.remove(it)
                }

                updateEmptyVisibility()
            }
        }

        StateSync.instance.confirmStarted(this, onStarted = {
            if (StateSync.instance.syncService?.serverSocketFailedToStart == true) {
                UIDialogs.toast(this, "Server socket failed to start, is the port in use?", true)
            }
            if (StateSync.instance.syncService?.relayConnected == false) {
                UIDialogs.toast(this, "Not connected to relay, remote connections will work.", false)
            }
            if (StateSync.instance.syncService?.serverSocketStarted == false) {
                UIDialogs.toast(this, "Listener not started, local connections will not work.", false)
            }
        }, onNotStarted = {
            finish()
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        StateSync.instance.deviceUpdatedOrAdded.remove(this)
        StateSync.instance.deviceRemoved.remove(this)
    }

    private fun updateDeviceView(syncDeviceView: SyncDeviceView, publicKey: String, session: SyncSession?): SyncDeviceView {
        val connected = session?.connected ?: false
        val authorized = session?.isAuthorized ?: false

        syncDeviceView.setLinkType(session?.linkType ?: LinkType.None)
            .setName(session?.displayName ?: StateSync.instance.getCachedName(publicKey) ?: publicKey)
            //TODO: also display public key?
            .setStatus(if (connected && authorized) "Connected" else "Disconnected or unauthorized")
        return syncDeviceView
    }

    private fun updateEmptyVisibility() {
        if (_viewMap.isNotEmpty()) {
            _layoutEmpty.visibility = View.GONE
        } else {
            _layoutEmpty.visibility = View.VISIBLE
        }
    }

    private fun initializeDevices() {
        _layoutDevices.removeAllViews()

        for (publicKey in StateSync.instance.getAll()) {
            val syncDeviceView = SyncDeviceView(this)
            syncDeviceView.onRemove.subscribe {
                StateApp.instance.scopeOrNull?.launch {
                    StateSync.instance.delete(publicKey)
                }
            }
            val view = updateDeviceView(syncDeviceView, publicKey, StateSync.instance.getSession(publicKey))
            _layoutDevices.addView(view)
            _viewMap[publicKey] = view
        }

        updateEmptyVisibility()
    }

    companion object {
        private const val TAG = "SyncHomeActivity"
    }
}