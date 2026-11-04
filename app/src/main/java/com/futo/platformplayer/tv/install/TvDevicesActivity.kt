package com.futo.platformplayer.tv.install

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.activities.SyncHomeActivity
import com.futo.platformplayer.activities.SyncPairActivity
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.tv.install.TvInstallViewModel.Page
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

class TvDevicesActivity : AppCompatActivity() {
    private lateinit var _model: TvInstallViewModel
    private lateinit var _host: EditText
    private lateinit var _port: EditText
    private lateinit var _code: EditText
    private var _renderedPage: Page? = null
    private val _pairExisting = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        _model.finishSetup()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FragmentedStorage.isInitialized) FragmentedStorage.initialize(filesDir)
        setContentView(R.layout.activity_tv_devices)
        setNavigationBarColorAndIcons()
        _model = ViewModelProvider(this)[TvInstallViewModel::class.java]
        _host = findViewById(R.id.tv_install_host)
        _port = findViewById(R.id.tv_install_port)
        _code = findViewById(R.id.tv_install_code)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tv_install_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = max(bars.bottom, keyboard.bottom))
            insets
        }
        findViewById<View>(R.id.tv_install_back).setOnClickListener { goBack() }
        findViewById<View>(R.id.tv_install_refresh).setOnClickListener { _model.refresh() }
        findViewById<View>(R.id.tv_install_primary).setOnClickListener { primaryAction() }
        findViewById<View>(R.id.tv_install_secondary).setOnClickListener {
            when {
                _model.state.value.busy -> confirmStop()
                _model.state.value.page == Page.INSTALLED -> _model.alreadyInstalled()
                _model.state.value.page == Page.EXISTING -> _model.useAdb()
                _model.state.value.page == Page.INSTALL -> _model.skipInstall()
                _model.state.value.page == Page.SYNC -> _model.skipSync()
                _model.state.value.page == Page.SECURITY || _model.state.value.page == Page.DISABLE -> _model.finishSetup()
                _model.state.value.page == Page.CONNECT -> _model.pairAgain()
                _model.state.value.page == Page.ADDRESS -> _model.manualConnection()
                _model.state.value.page == Page.DEVELOPER -> _model.developerOptionsEnabled()
            }
        }
        _host.doAfterTextChanged { _model.host = it.toString() }
        _port.doAfterTextChanged {
            if (_model.state.value.page == Page.PAIR)
                _model.pairingPort = it.toString()
            else
                _model.connectionPort = it.toString()
        }
        findViewById<RadioGroup>(R.id.tv_install_debug_options).setOnCheckedChangeListener { _, checkedId ->
            _model.usesPairing = checkedId == R.id.tv_install_wireless
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                _model.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::_model.isInitialized) _model.refresh()
    }

    private fun primaryAction() {
        hideKeyboard()
        when (_model.state.value.page) {
            Page.DEVICES -> _model.add()
            Page.PAIR -> {
                val code = _code.text.toString()
                _code.text.clear()
                _model.pair(code)
            }
            Page.ADDRESS, Page.CONNECT -> _model.connect()
            Page.INSTALL -> _model.install()
            Page.SYNC -> StateSync.instance.confirmStarted(this, { _model.pairSync() }, {})
            Page.EXISTING -> StateSync.instance.confirmStarted(this, {
                _pairExisting.launch(Intent(this, SyncPairActivity::class.java).putExtra("pairTv", true))
            }, {})
            Page.SECURITY -> _model.debuggingSettings()
            Page.DISABLE -> _model.finishSetup()
            else -> _model.next()
        }
    }

    private fun render(state: TvInstallViewModel.State) {
        val isList = state.page == Page.DEVICES
        val changedPage = _renderedPage != state.page
        _renderedPage = state.page
        findViewById<View>(R.id.tv_install_devices_page).isVisible = isList
        findViewById<View>(R.id.tv_install_setup_page).isVisible = !isList
        findViewById<View>(R.id.tv_install_refresh).apply {
            isVisible = isList && (state.devices.isNotEmpty() || state.syncDevices.isNotEmpty())
            isEnabled = !state.busy
        }
        findViewById<TextView>(R.id.tv_install_toolbar_title).setText(if (isList) R.string.tv_install_menu else R.string.tv_install_setup)
        findViewById<TextView>(R.id.tv_install_error).apply {
            isVisible = state.error != null
            text = state.error?.let { getString(it) }.orEmpty()
        }
        if (isList)
            renderDevices(state)
        else
            renderSetup(state, changedPage)
        renderActions(state)
        if (changedPage) {
            hideKeyboard()
            _code.text.clear()
            findViewById<NestedScrollView>(R.id.tv_install_scroll).scrollTo(0, 0)
        }
    }

    private fun renderDevices(state: TvInstallViewModel.State) {
        findViewById<View>(R.id.tv_install_empty).isVisible = state.devices.isEmpty() && state.syncDevices.isEmpty()
        val container = findViewById<LinearLayout>(R.id.tv_install_devices)
        container.removeAllViews()
        for (device in state.devices) {
            val row = layoutInflater.inflate(R.layout.view_tv_install_device, container, false)
            row.findViewById<TextView>(R.id.tv_install_device_name).text = device.name
            row.findViewById<TextView>(R.id.tv_install_device_address).text = device.endpoint.serial
            row.findViewById<TextView>(R.id.tv_install_device_status).apply {
                val synced = state.syncDevices.firstOrNull { it.key == device.syncPublicKey }
                val connected = synced?.connected == true || device.endpoint.serial in state.connected
                setText(when {
                    synced?.connected == true -> R.string.tv_install_sync_connected
                    device.endpoint.serial in state.connected -> R.string.tv_install_connected
                    synced != null -> R.string.tv_install_sync_saved
                    else -> R.string.tv_install_saved
                })
                setTextColor(if (connected) Color.rgb(100, 205, 166) else Color.rgb(172, 172, 172))
            }
            row.findViewById<View>(R.id.tv_install_device_open).apply {
                isEnabled = !state.busy
                setOnClickListener { _model.open(device) }
            }
            row.findViewById<View>(R.id.tv_install_device_more).apply {
                isEnabled = !state.busy
                contentDescription = getString(R.string.tv_install_more, device.name)
                setOnClickListener { confirmForget(device) }
            }
            container.addView(row)
        }
        for (device in state.syncDevices.filter { sync -> state.devices.none { it.syncPublicKey == sync.key } }) {
            val row = layoutInflater.inflate(R.layout.view_tv_install_device, container, false)
            row.findViewById<TextView>(R.id.tv_install_device_name).text = device.name
            row.findViewById<TextView>(R.id.tv_install_device_address).setText(R.string.tv_install_sync_device)
            row.findViewById<TextView>(R.id.tv_install_device_status).apply {
                setText(if (device.connected) R.string.tv_install_sync_connected else R.string.tv_install_sync_saved)
                setTextColor(if (device.connected) Color.rgb(100, 205, 166) else Color.rgb(172, 172, 172))
            }
            row.findViewById<View>(R.id.tv_install_device_open).setOnClickListener {
                startActivity(Intent(this, SyncHomeActivity::class.java))
            }
            row.findViewById<View>(R.id.tv_install_device_more).apply {
                contentDescription = getString(R.string.tv_install_more, device.name)
                setOnClickListener {
                    StateSync.instance.confirmStarted(this@TvDevicesActivity, {
                        AlertDialog.Builder(this@TvDevicesActivity)
                            .setTitle(R.string.tv_sync_remove)
                            .setMessage(getString(R.string.tv_sync_remove_hint, device.name))
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.tv_sync_remove) { _, _ -> _model.forgetSync(device.key) }
                            .show()
                    }, {})
                }
            }
            container.addView(row)
        }
    }

    private fun renderSetup(state: TvInstallViewModel.State, changedPage: Boolean) {
        val content = content(state.page)
        val complete = state.page == Page.SECURITY
        findViewById<TextView>(R.id.tv_install_step).apply {
            text = if (content.guideStep != null) getString(R.string.tv_install_developer_step, content.guideStep)
                else getString(R.string.tv_install_step, content.step)
            isVisible = !complete
        }
        findViewById<LinearProgressIndicator>(R.id.tv_install_steps).apply {
            progress = content.step
            isVisible = !complete
        }
        findViewById<ImageView>(R.id.tv_install_step_icon).apply {
            isVisible = content.screenshot == null
            setImageResource(if (complete) R.drawable.ic_check else R.drawable.ic_live_tv)
        }
        findViewById<ImageView>(R.id.tv_install_screenshot).apply {
            isVisible = content.screenshot != null
            if (content.screenshot != null)
                setImageResource(content.screenshot)
        }
        findViewById<TextView>(R.id.tv_install_title).setText(content.title)
        findViewById<TextView>(R.id.tv_install_description).text = when (state.page) {
            Page.INSTALL, Page.SYNC -> getString(content.description, state.selected?.name.orEmpty())
            Page.CONNECT -> getString(if (_model.usesPairing) content.description else R.string.tv_install_connect_network_description)
            else -> getString(content.description)
        }
        findViewById<TextView>(R.id.tv_install_instructions).apply {
            isVisible = content.instructions != null
            text = content.instructions?.let { getString(it) }.orEmpty()
        }
        findViewById<TextView>(R.id.tv_install_note).setText(
            if (state.page == Page.INSTALL && state.appInstalled) R.string.tv_install_update_note else content.note)
        findViewById<RadioGroup>(R.id.tv_install_debug_options).apply {
            isVisible = state.page == Page.DEBUGGING
            check(if (_model.usesPairing) R.id.tv_install_wireless else R.id.tv_install_network)
        }
        val hasFields = state.page == Page.PAIR || state.page == Page.CONNECT || state.page == Page.ADDRESS
        findViewById<View>(R.id.tv_install_fields).isVisible = hasFields
        findViewById<View>(R.id.tv_install_code_fields).isVisible = state.page == Page.PAIR
        _port.isVisible = state.page != Page.ADDRESS
        findViewById<View>(R.id.tv_install_port_label).isVisible = state.page != Page.ADDRESS
        if (hasFields) {
            findViewById<TextView>(R.id.tv_install_port_label).setText(
                if (state.page == Page.PAIR) R.string.tv_install_pair_port else R.string.tv_install_connection_port)
            _port.setHint(if (state.page == Page.PAIR) R.string.tv_install_pair_port_hint else R.string.tv_install_connection_port_hint)
            if (changedPage) {
                _host.setText(_model.host)
                _port.setText(if (state.page == Page.PAIR) _model.pairingPort else _model.connectionPort)
            }
        }
        _host.isEnabled = !state.busy
        _port.isEnabled = !state.busy
        _code.isEnabled = !state.busy
        findViewById<View>(R.id.tv_install_apk).isVisible = state.page == Page.INSTALL
        if (state.page == Page.INSTALL) {
            val size = (listOf(applicationInfo.sourceDir) + applicationInfo.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
            findViewById<TextView>(R.id.tv_install_apk_name).text = getString(R.string.tv_install_package, BuildConfig.VERSION_NAME)
            findViewById<TextView>(R.id.tv_install_apk_size).text = getString(R.string.tv_install_package_details, Formatter.formatFileSize(this, size))
        }
    }

    private fun renderActions(state: TvInstallViewModel.State) {
        findViewById<MaterialButton>(R.id.tv_install_primary).apply {
            setText(when (state.page) {
                Page.DEVICES -> R.string.tv_install_add
                Page.INSTALLED -> R.string.tv_install_not_installed
                Page.EXISTING -> R.string.tv_install_scan_sync
                Page.INTRO -> R.string.tv_install_begin
                Page.PAIR -> R.string.tv_install_pair
                Page.ADDRESS, Page.CONNECT -> R.string.tv_install_connect
                Page.INSTALL -> if (state.appInstalled) R.string.tv_install_update else R.string.tv_install_button
                Page.SYNC -> R.string.tv_install_sync_pair
                Page.SECURITY -> R.string.tv_install_disable_open
                Page.DISABLE -> R.string.tv_install_done
                else -> R.string.tv_install_continue
            })
            isEnabled = !state.busy
            alpha = if (state.busy) 0.5f else 1f
        }
        findViewById<View>(R.id.tv_install_busy).isVisible = state.busy
        findViewById<TextView>(R.id.tv_install_status).apply {
            isVisible = state.busy
            setText(state.status ?: when (state.page) {
                Page.PAIR -> R.string.tv_install_pairing
                Page.ADDRESS, Page.CONNECT -> R.string.tv_install_connecting
                Page.INSTALL -> R.string.tv_install_installing
                else -> R.string.tv_install_working
            })
        }
        findViewById<MaterialButton>(R.id.tv_install_secondary).apply {
            isVisible = state.busy || state.page in listOf(Page.INSTALLED, Page.EXISTING, Page.INSTALL, Page.SYNC,
                Page.SECURITY, Page.CONNECT, Page.ADDRESS, Page.DEVELOPER)
            setText(when {
                state.busy -> R.string.cancel
                state.page == Page.INSTALLED -> R.string.tv_install_is_installed
                state.page == Page.EXISTING -> R.string.tv_install_use_adb
                state.page == Page.INSTALL -> if (state.appInstalled) R.string.tv_install_skip_update else R.string.tv_install_not_now
                state.page == Page.SYNC -> R.string.tv_install_not_now
                state.page == Page.SECURITY -> R.string.tv_install_keep_debugging
                state.page == Page.DEVELOPER -> R.string.tv_install_already_enabled
                state.page == Page.ADDRESS -> R.string.tv_install_manual_port
                else -> R.string.tv_install_pair_again
            })
        }
    }

    private fun goBack() {
        if (_model.state.value.busy)
            confirmStop()
        else if (!_model.back())
            finish()
    }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle(R.string.tv_install_cancel_title)
            .setMessage(R.string.tv_install_cancel_description)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.tv_install_stop) { _, _ -> _model.cancel() }
            .show()
    }

    private fun confirmForget(device: TvInstallDevice) {
        AlertDialog.Builder(this)
            .setTitle(R.string.tv_install_forget)
            .setMessage(getString(R.string.tv_install_forget_description, device.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.tv_install_forget) { _, _ -> _model.forget(device) }
            .show()
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(_host.windowToken, 0)
        _host.clearFocus()
        _port.clearFocus()
        _code.clearFocus()
    }

    private fun content(page: Page): PageContent {
        return when (page) {
            Page.INSTALLED -> PageContent(1, R.string.tv_install_installed_title, R.string.tv_install_installed_description,
                R.string.tv_install_installed_note)
            Page.EXISTING -> PageContent(1, R.string.tv_install_existing_title, R.string.tv_install_existing_description,
                R.string.tv_install_existing_note)
            Page.ADDRESS -> PageContent(4, R.string.tv_install_connect_title, R.string.tv_install_address_description,
                R.string.tv_install_address_note)
            Page.INTRO -> PageContent(1, R.string.tv_install_intro_title, R.string.tv_install_intro_description,
                R.string.tv_install_intro_note, R.string.tv_install_intro_instructions)
            Page.DEVELOPER -> PageContent(2, R.string.tv_install_developer_title, R.string.tv_install_developer_description,
                R.string.tv_install_developer_note, screenshot = R.drawable.tv_setup_system, guideStep = 1)
            Page.ABOUT -> PageContent(2, R.string.tv_install_about_title, R.string.tv_install_about_description,
                R.string.tv_install_about_note, screenshot = R.drawable.tv_setup_about, guideStep = 2)
            Page.BUILD -> PageContent(2, R.string.tv_install_build_title, R.string.tv_install_build_description,
                R.string.tv_install_build_note, screenshot = R.drawable.tv_setup_build, guideStep = 3)
            Page.DEVELOPER_OPTIONS -> PageContent(2, R.string.tv_install_options_title, R.string.tv_install_options_description,
                R.string.tv_install_options_note, screenshot = R.drawable.tv_setup_developer, guideStep = 4)
            Page.DEBUGGING -> PageContent(3, R.string.tv_install_debug_title, R.string.tv_install_debug_description, R.string.tv_install_debug_note)
            Page.PAIR -> PageContent(4, R.string.tv_install_pair_title, R.string.tv_install_pair_description, R.string.tv_install_pair_note)
            Page.CONNECT -> PageContent(4, R.string.tv_install_connect_title, R.string.tv_install_connect_description, R.string.tv_install_connect_note)
            Page.INSTALL -> PageContent(5, R.string.tv_install_ready_title, R.string.tv_install_ready_description, R.string.tv_install_ready_note)
            Page.SYNC -> PageContent(6, R.string.tv_install_sync_title, R.string.tv_install_sync_description, R.string.tv_install_sync_note)
            Page.SECURITY -> PageContent(7, R.string.tv_install_security_title, R.string.tv_install_security_description, R.string.tv_install_security_note)
            Page.DISABLE -> PageContent(7, R.string.tv_install_disable_title, R.string.tv_install_disable_description, R.string.tv_install_disable_note)
            Page.DEVICES -> error("The device list has no setup content")
        }
    }

    private data class PageContent(
        val step: Int,
        val title: Int,
        val description: Int,
        val note: Int,
        val instructions: Int? = null,
        val screenshot: Int? = null,
        val guideStep: Int? = null
    )
}
