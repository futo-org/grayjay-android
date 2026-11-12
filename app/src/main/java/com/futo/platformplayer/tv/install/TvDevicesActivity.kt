package com.futo.platformplayer.tv.install

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.SyncPairActivity
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.tv.install.TvInstallViewModel.Page
import com.futo.platformplayer.tv.install.TvInstallViewModel.TvType
import com.futo.platformplayer.tv.install.TvInstallViewModel.DebuggingMethod
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

class TvDevicesActivity : AppCompatActivity() {
    private lateinit var _credentials: TvCredentialDialog
    private lateinit var _model: TvInstallViewModel
    private lateinit var _address: EditText
    private lateinit var _code: EditText
    private var _renderedPage: Page? = null
    private var _bindingAddress = false
    private var _bindingDebugOptions = false
    private val _pairExisting = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        _model.existingPaired(it.data?.getStringExtra("pairedTvKey"))
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
        _credentials = TvCredentialDialog(this)
        _address = findViewById(R.id.tv_install_address)
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
        findViewById<View>(R.id.tv_install_google_tv).setOnClickListener { _model.chooseType(TvType.GOOGLE_TV) }
        findViewById<View>(R.id.tv_install_android_tv).setOnClickListener { _model.chooseType(TvType.ANDROID_TV) }
        findViewById<View>(R.id.tv_install_shield).setOnClickListener { _model.chooseType(TvType.SHIELD) }
        findViewById<View>(R.id.tv_install_secondary).setOnClickListener {
            when {
                _model.state.value.busy -> confirmStop()
                _model.state.value.page == Page.INSTALLED -> _model.alreadyInstalled()
                _model.state.value.page == Page.EXISTING -> _model.useAdb()
                _model.state.value.page == Page.INSTALL -> _model.skipInstall()
                _model.state.value.page == Page.SYNC -> _model.skipSync()
                _model.state.value.page == Page.CREDENTIALS -> _model.credentialsFinished()
                _model.state.value.page == Page.DETAILS -> _model.state.value.selected?.let { _model.open(it) }
                _model.state.value.page == Page.SECURITY || _model.state.value.page == Page.DISABLE -> _model.finishSetup()
                _model.state.value.page == Page.CONNECT -> _model.pairAgain()
                _model.state.value.page == Page.DEVELOPER -> _model.developerOptionsEnabled()
            }
        }
        _address.doAfterTextChanged {
            if (_bindingAddress) return@doAfterTextChanged
            if (_model.state.value.page == Page.PAIR)
                _model.pairingAddress = it.toString()
            else if (_model.state.value.pairedHost != null)
                _model.connectionPort = it.toString()
            else
                _model.connectionAddress = it.toString()
        }
        findViewById<RadioGroup>(R.id.tv_install_debug_options).setOnCheckedChangeListener { _, checkedId ->
            if (!_bindingDebugOptions && checkedId != -1)
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
            Page.CONNECT -> _model.connect()
            Page.INSTALL -> _model.install()
            Page.SYNC -> StateSync.instance.confirmStarted(this, { _model.pairSync() }, {})
            Page.EXISTING -> StateSync.instance.confirmStarted(this, {
                _pairExisting.launch(Intent(this, SyncPairActivity::class.java).putExtra("pairTv", true))
            }, {})
            Page.SECURITY -> _model.debuggingSettings()
            Page.DISABLE -> _model.finishSetup()
            Page.CREDENTIALS, Page.DETAILS -> {
                val state = _model.state.value
                val key = state.credentialKey ?: return
                StateSync.instance.confirmStarted(this, {
                    _credentials.show(key, state.credentialName) { _model.credentialsFinished() }
                }, {})
            }
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
        findViewById<TextView>(R.id.tv_install_toolbar_title).setText(if (isList || state.page == Page.DETAILS) R.string.tv_install_menu else R.string.tv_install_setup)
        findViewById<TextView>(R.id.tv_install_error).apply {
            isVisible = state.error != null
            text = state.error?.let {
                when (it) {
                    R.string.tv_install_abi_mismatch, R.string.tv_install_download_unavailable ->
                        getString(it, BuildConfig.VERSION_NAME, architectureName(state.apk?.abi))
                    else -> getString(it)
                }
            }.orEmpty()
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
                setOnClickListener { _model.details(device) }
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
                _model.details(device)
            }
            row.findViewById<View>(R.id.tv_install_device_more).apply {
                contentDescription = getString(R.string.tv_install_more, device.name)
                setOnClickListener {
                    StateSync.instance.confirmStarted(this@TvDevicesActivity, {
                        UIDialogs.showDialog(this@TvDevicesActivity, R.drawable.ic_error,
                            getString(R.string.tv_sync_remove), getString(R.string.tv_sync_remove_hint, device.name), null, 0,
                            UIDialogs.Action(getString(R.string.cancel), {}, UIDialogs.ActionStyle.ACCENT),
                            UIDialogs.Action(getString(R.string.tv_sync_remove), { _model.forgetSync(device.key) }, UIDialogs.ActionStyle.DANGEROUS))
                    }, {})
                }
            }
            container.addView(row)
        }
    }

    private fun renderSetup(state: TvInstallViewModel.State, changedPage: Boolean) {
        val content = content(state.page)
        val complete = state.page == Page.SECURITY || state.page == Page.DETAILS || state.page == Page.TYPE
        val shield = state.tvType == TvType.SHIELD
        findViewById<View>(R.id.tv_install_type_options).isVisible = state.page == Page.TYPE
        findViewById<TextView>(R.id.tv_install_step).apply {
            text = if (content.guideStep != null) getString(R.string.tv_install_developer_step, content.guideStep)
                else getString(R.string.tv_install_step, content.step)
            isVisible = !complete
        }
        findViewById<LinearProgressIndicator>(R.id.tv_install_steps).apply {
            progress = content.step
            isVisible = !complete
        }
        findViewById<TvSetupScreenshotView>(R.id.tv_install_screenshot).apply {
            showFullImage = shield
            isVisible = content.screenshot != null
            if (content.screenshot != null)
                setImageResource(content.screenshot)
        }
        val download = state.page == Page.INSTALL && state.apk?.downloadRequired == true
        val portOnly = state.page == Page.CONNECT && state.pairedHost != null
        findViewById<TextView>(R.id.tv_install_title).text = if (state.page == Page.DETAILS) state.credentialName
            else getString(if (download) R.string.tv_install_download_title else content.title)
        findViewById<TextView>(R.id.tv_install_description).text = when (state.page) {
            Page.INSTALL -> if (download) getString(if (BuildConfig.IS_PLAYSTORE_BUILD) R.string.tv_install_playstore_download_description else R.string.tv_install_download_description,
                state.selected?.name.orEmpty(), BuildConfig.VERSION_NAME, architectureName(state.apk?.abi))
                else getString(content.description, state.selected?.name.orEmpty())
            Page.SYNC -> getString(content.description, state.selected?.name.orEmpty())
            Page.CREDENTIALS -> getString(content.description, state.credentialName)
            Page.DETAILS -> if (state.credentialKey == null) getString(R.string.tv_credentials_pair_first)
                else getString(content.description)
            Page.CONNECT -> getString(when {
                portOnly -> R.string.tv_install_connect_port_description
                _model.usesPairing -> content.description
                else -> R.string.tv_install_connect_network_description
            })
            else -> getString(content.description)
        }
        findViewById<TextView>(R.id.tv_install_instructions).apply {
            isVisible = content.instructions != null
            text = content.instructions?.let { getString(it) }.orEmpty()
        }
        findViewById<TextView>(R.id.tv_install_note).apply {
            isVisible = state.page != Page.TYPE
            setText(if (download) R.string.tv_install_download_note
                else if (state.page == Page.INSTALL && state.appInstalled) R.string.tv_install_update_note else content.note)
        }
        findViewById<RadioGroup>(R.id.tv_install_debug_options).apply {
            isVisible = state.page == Page.DEBUGGING
            _bindingDebugOptions = true
            try {
                check(when (state.debuggingMethod) {
                    DebuggingMethod.WIRELESS -> R.id.tv_install_wireless
                    DebuggingMethod.NETWORK -> R.id.tv_install_network
                    null -> -1
                })
            } finally {
                _bindingDebugOptions = false
            }
        }
        findViewById<View>(R.id.tv_install_wireless).isVisible = !shield
        findViewById<View>(R.id.tv_install_wireless_description).isVisible = !shield
        val hasFields = state.page == Page.PAIR || state.page == Page.CONNECT
        findViewById<View>(R.id.tv_install_fields).isVisible = hasFields
        findViewById<View>(R.id.tv_install_code_fields).isVisible = state.page == Page.PAIR
        if (hasFields) {
            val address = when {
                state.page == Page.PAIR -> _model.pairingAddress
                portOnly -> _model.connectionPort
                else -> _model.connectionAddress
            }
            val hint = when {
                portOnly -> R.string.tv_install_connection_port_hint
                _model.usesPairing -> R.string.tv_install_address_hint
                else -> R.string.tv_install_network_address_hint
            }
            findViewById<TextView>(R.id.tv_install_address_label).setText(
                if (portOnly) R.string.tv_install_connection_port else R.string.tv_install_address)
            _address.setHint(hint)
            findViewById<View>(R.id.tv_install_address_example).isVisible = state.page != Page.PAIR
            findViewById<TextView>(R.id.tv_install_address_example).setText(
                if (portOnly) R.string.tv_install_connection_port_example else hint)
            _bindingAddress = true
            try {
                val inputType = if (portOnly) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                if (_address.inputType != inputType) _address.inputType = inputType
                if (changedPage || _address.text.toString() != address)
                    _address.setText(address)
            } finally {
                _bindingAddress = false
            }
        }
        _address.isEnabled = !state.busy
        _code.isEnabled = !state.busy
        findViewById<View>(R.id.tv_install_apk).isVisible = state.page == Page.INSTALL
        if (state.page == Page.INSTALL) {
            val size = (listOf(applicationInfo.sourceDir) + applicationInfo.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
            findViewById<TextView>(R.id.tv_install_apk_name).text = getString(R.string.tv_install_package, BuildConfig.VERSION_NAME)
            findViewById<TextView>(R.id.tv_install_apk_size).text = if (download)
                getString(R.string.tv_install_download_details, architectureName(state.apk?.abi),
                    getString(if (BuildConfig.IS_UNSTABLE_BUILD) R.string.tv_install_release_unstable else R.string.tv_install_release_stable))
                else getString(R.string.tv_install_package_details, Formatter.formatFileSize(this, size))
        }
    }

    private fun architectureName(abi: String?): String {
        return when (abi) {
            "armeabi-v7a" -> getString(R.string.tv_install_arm32)
            "arm64-v8a" -> getString(R.string.tv_install_arm64)
            "x86" -> getString(R.string.tv_install_x86)
            "x86_64" -> getString(R.string.tv_install_x86_64)
            else -> abi.orEmpty()
        }
    }

    private fun renderActions(state: TvInstallViewModel.State) {
        findViewById<MaterialButton>(R.id.tv_install_primary).apply {
            isVisible = state.page != Page.TYPE
            setText(when (state.page) {
                Page.DEVICES -> R.string.tv_install_add
                Page.INSTALLED -> R.string.tv_install_not_installed
                Page.EXISTING -> R.string.tv_install_scan_sync
                Page.INTRO -> R.string.tv_install_begin
                Page.PAIR -> R.string.tv_install_pair
                Page.CONNECT -> R.string.tv_install_connect
                Page.INSTALL -> if (state.apk?.downloadRequired == true) R.string.tv_install_download_button
                    else if (state.appInstalled) R.string.tv_install_update else R.string.tv_install_button
                Page.SYNC -> R.string.tv_install_sync_pair
                Page.CREDENTIALS -> R.string.tv_credentials_select
                Page.DETAILS -> R.string.tv_credentials_send
                Page.SECURITY -> R.string.tv_install_disable_open
                Page.DISABLE -> R.string.tv_install_done
                else -> R.string.tv_install_continue
            })
            isEnabled = !state.busy && (state.page != Page.DETAILS || state.credentialKey != null) &&
                (state.page != Page.DEBUGGING || state.debuggingMethod != null)
            alpha = if (!isEnabled) 0.5f else 1f
        }
        val download = state.downloadProgress
        findViewById<LinearProgressIndicator>(R.id.tv_install_busy).apply {
            val indeterminate = download == null || download.total <= 0
            if (isIndeterminate != indeterminate) {
                hide()
                isIndeterminate = indeterminate
            }
            isVisible = state.busy
            if (!indeterminate && download != null)
                progress = (download.received * 100 / download.total).toInt().coerceIn(0, 100)
        }
        findViewById<TextView>(R.id.tv_install_status).apply {
            isVisible = state.busy
            text = if (download != null) {
                val received = Formatter.formatFileSize(this@TvDevicesActivity, download.received)
                if (download.total > 0) getString(R.string.tv_install_download_progress, received,
                    Formatter.formatFileSize(this@TvDevicesActivity, download.total))
                else getString(R.string.tv_install_download_received, received)
            } else getString(state.status ?: when (state.page) {
                Page.PAIR -> R.string.tv_install_pairing
                Page.CONNECT -> R.string.tv_install_connecting
                Page.INSTALL -> R.string.tv_install_installing
                else -> R.string.tv_install_working
            })
        }
        findViewById<MaterialButton>(R.id.tv_install_secondary).apply {
            isVisible = state.busy || state.page in listOf(Page.INSTALLED, Page.EXISTING, Page.INSTALL, Page.SYNC,
                Page.SECURITY, Page.DEVELOPER, Page.CREDENTIALS) || (state.page == Page.DETAILS && state.selected != null) || (state.page == Page.CONNECT && _model.usesPairing)
            setText(when {
                state.busy -> R.string.cancel
                state.page == Page.INSTALLED -> R.string.tv_install_is_installed
                state.page == Page.EXISTING -> R.string.tv_install_use_adb
                state.page == Page.INSTALL -> if (state.appInstalled) R.string.tv_install_skip_update else R.string.tv_install_not_now
                state.page == Page.SYNC || state.page == Page.CREDENTIALS -> R.string.tv_install_not_now
                state.page == Page.DETAILS -> R.string.tv_device_manage_install
                state.page == Page.SECURITY -> R.string.tv_install_keep_debugging
                state.page == Page.DEVELOPER -> R.string.tv_install_already_enabled
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
        UIDialogs.showDialog(this, R.drawable.ic_error,
            getString(R.string.tv_install_cancel_title), getString(R.string.tv_install_cancel_description), null, 0,
            UIDialogs.Action(getString(R.string.cancel), {}, UIDialogs.ActionStyle.ACCENT),
            UIDialogs.Action(getString(R.string.tv_install_stop), { _model.cancel() }, UIDialogs.ActionStyle.DANGEROUS))
    }

    private fun confirmForget(device: TvInstallDevice) {
        UIDialogs.showDialog(this, R.drawable.ic_error,
            getString(R.string.tv_install_forget), getString(R.string.tv_install_forget_description, device.name), null, 0,
            UIDialogs.Action(getString(R.string.cancel), {}, UIDialogs.ActionStyle.ACCENT),
            UIDialogs.Action(getString(R.string.tv_install_forget), { _model.forget(device) }, UIDialogs.ActionStyle.DANGEROUS))
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(_address.windowToken, 0)
        _address.clearFocus()
        _code.clearFocus()
    }

    private fun content(page: Page): PageContent {
        val content = when (page) {
            Page.TYPE -> PageContent(1, R.string.tv_install_type_title, R.string.tv_install_type_description,
                R.string.tv_install_installed_note)
            Page.INSTALLED -> PageContent(1, R.string.tv_install_installed_title, R.string.tv_install_installed_description,
                R.string.tv_install_installed_note)
            Page.EXISTING -> PageContent(1, R.string.tv_install_existing_title, R.string.tv_install_existing_description,
                R.string.tv_install_existing_note)
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
            Page.CREDENTIALS -> PageContent(7, R.string.tv_credentials_title, R.string.tv_credentials_description, R.string.tv_credentials_warning)
            Page.DETAILS -> PageContent(1, R.string.tv_install_menu, R.string.tv_device_details_hint, R.string.tv_device_details_note)
            Page.SECURITY -> PageContent(8, R.string.tv_install_security_title, R.string.tv_install_security_description, R.string.tv_install_security_note)
            Page.DISABLE -> PageContent(8, R.string.tv_install_disable_title, R.string.tv_install_disable_description, R.string.tv_install_disable_note)
            Page.DEVICES -> error("The device list has no setup content")
        }
        if (_model.state.value.tvType != TvType.SHIELD) return content
        return when (page) {
            Page.DEVELOPER -> content.copy(title = R.string.tv_install_shield_preferences_title,
                description = R.string.tv_install_shield_preferences_description,
                note = R.string.tv_install_shield_preferences_note, screenshot = R.drawable.tv_setup_shield_settings)
            Page.ABOUT -> content.copy(description = R.string.tv_install_shield_about_description,
                note = R.string.tv_install_shield_about_note, screenshot = R.drawable.tv_setup_shield_about)
            Page.BUILD -> content.copy(description = R.string.tv_install_shield_build_description,
                screenshot = R.drawable.tv_setup_shield_build)
            Page.DEVELOPER_OPTIONS -> content.copy(description = R.string.tv_install_shield_options_description,
                note = R.string.tv_install_shield_options_note, screenshot = R.drawable.tv_setup_shield_developer)
            Page.DEBUGGING -> content.copy(title = R.string.tv_install_shield_debug_title,
                description = R.string.tv_install_shield_debug_description,
                note = R.string.tv_install_shield_debug_note, screenshot = R.drawable.tv_setup_shield_network)
            else -> content
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
