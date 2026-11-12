package com.futo.platformplayer.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.overlays.LoaderOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.polycentric.core.ServerAlreadyAddedException

/**
 * Lists the active Harbor/Polycentric identity's servers, and lets user add and remove them if the
 * device's key has permissions to.
 */
class PolycentricServersActivity : AppCompatActivity() {
    private lateinit var _layoutServers: LinearLayout
    private lateinit var _textServersEmpty: TextView
    private lateinit var _textLastServerHint: TextView
    private lateinit var _layoutAdd: LinearLayout
    private lateinit var _editServerUrl: EditText
    private lateinit var _buttonAddServer: View
    private lateinit var _layoutAddError: LinearLayout
    private lateinit var _textToggleLogs: TextView
    private lateinit var _textAddErrorLogs: TextView
    private lateinit var _loaderOverlay: LoaderOverlay

    /** Whether an add or remove is running; further edits are ignored until it finishes. */
    private var _busy = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polycentric_servers)
        setNavigationBarColorAndIcons()

        _layoutServers = findViewById(R.id.layout_servers)
        _textServersEmpty = findViewById(R.id.text_servers_empty)
        _textLastServerHint = findViewById(R.id.text_last_server_hint)
        _layoutAdd = findViewById(R.id.layout_add)
        _editServerUrl = findViewById(R.id.edit_server_url)
        _buttonAddServer = findViewById(R.id.button_add_server)
        _layoutAddError = findViewById(R.id.layout_add_error)
        _textToggleLogs = findViewById(R.id.text_toggle_logs)
        _textAddErrorLogs = findViewById(R.id.text_add_error_logs)
        _loaderOverlay = findViewById(R.id.loader_overlay)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish()
        }

        _buttonAddServer.setOnClickListener {
            addServer(_editServerUrl.text.toString())
        }
        _editServerUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return@setOnEditorActionListener false
            }
            addServer(_editServerUrl.text.toString())
            true
        }

        _textToggleLogs.setOnClickListener {
            val show = _textAddErrorLogs.visibility != View.VISIBLE
            _textAddErrorLogs.visibility = if (show) View.VISIBLE else View.GONE
            _textToggleLogs.text = getString(if (show) R.string.hide_logs else R.string.show_logs)
        }
    }

    override fun onResume() {
        super.onResume()
        StatePolycentric.instance.onServersChanged.subscribe(this) { loadServers() }
        loadServers()
    }

    override fun onPause() {
        super.onPause()
        StatePolycentric.instance.onServersChanged.remove(this)
    }

    private fun loadServers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val loaded = try {
                Pair(StatePolycentric.instance.getServers(), StatePolycentric.instance.canEditServers())
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to load servers.", e)
                null
            }

            withContext(Dispatchers.Main) {
                if (loaded == null) {
                    UIDialogs.toast(this@PolycentricServersActivity, getString(R.string.harbor_servers_load_failed))
                    finish()
                    return@withContext
                }
                bindServers(loaded.first, loaded.second)
            }
        }
    }

    private fun bindServers(servers: List<String>, canEdit: Boolean) {
        _layoutServers.removeAllViews()
        _textServersEmpty.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        // The last server shouldn't be removed
        val canRemove = canEdit && servers.size > 1
        for (server in servers) {
            _layoutServers.addView(createRow(server, canRemove))
        }
        _textLastServerHint.visibility = if (canEdit && servers.size == 1) View.VISIBLE else View.GONE
        _layoutAdd.visibility = if (canEdit) View.VISIBLE else View.GONE
    }

    private fun createRow(server: String, removable: Boolean): View {
        val row = LayoutInflater.from(this).inflate(R.layout.view_harbor_server_row, _layoutServers, false)
        row.findViewById<TextView>(R.id.text_server_url).text = server
        val button = row.findViewById<ImageButton>(R.id.button_remove_server)
        if (removable) {
            button.setColorFilter(ContextCompat.getColor(this, R.color.pastel_red))
            button.setOnClickListener { confirmRemoveServer(server) }
        } else {
            button.visibility = View.GONE
        }
        return row
    }

    private fun addServer(url: String) {
        if (url.isBlank() || _busy) {
            return
        }
        setBusy(true)
        hideAddError()

        lifecycleScope.launch(Dispatchers.IO) {
            val error = try {
                StatePolycentric.instance.addServer(url)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e
            }

            withContext(Dispatchers.Main) {
                setBusy(false)
                when (error) {
                    null -> {
                        UIDialogs.toast(this@PolycentricServersActivity, getString(R.string.harbor_server_added))
                        _editServerUrl.text.clear()
                    }
                    is ServerAlreadyAddedException -> {
                        UIDialogs.toast(this@PolycentricServersActivity, getString(R.string.harbor_server_already_added))
                    }
                    else -> {
                        Logger.e(TAG, "Failed to add server.", error)
                        UIDialogs.toast(this@PolycentricServersActivity, getString(R.string.harbor_server_add_failed))
                        showAddError(error)
                    }
                }
            }
        }
    }

    private fun confirmRemoveServer(server: String) {
        if (_busy) {
            return
        }
        UIDialogs.showConfirmationDialog(this, getString(R.string.harbor_server_remove_message, server), {
            removeServer(server)
        })
    }

    private fun removeServer(server: String) {
        if (_busy) {
            return
        }
        setBusy(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val error = try {
                StatePolycentric.instance.removeServer(server)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to remove server.", e)
                e
            }

            withContext(Dispatchers.Main) {
                setBusy(false)
                val message = if (error == null) getString(R.string.harbor_server_removed)
                    else error.message ?: getString(R.string.harbor_server_remove_failed)
                UIDialogs.toast(this@PolycentricServersActivity, message)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        _busy = busy
        _editServerUrl.isEnabled = !busy
        _buttonAddServer.isEnabled = !busy
        if (busy) {
            _loaderOverlay.show()
        } else {
            _loaderOverlay.hide()
        }
    }

    private fun showAddError(error: Throwable) {
        _textAddErrorLogs.text = error.toString()
        _textAddErrorLogs.visibility = View.GONE
        _textToggleLogs.text = getString(R.string.show_logs)
        _layoutAddError.visibility = View.VISIBLE
    }

    private fun hideAddError() {
        _layoutAddError.visibility = View.GONE
    }

    companion object {
        private const val TAG = "PolycentricServersActivity"
    }
}
