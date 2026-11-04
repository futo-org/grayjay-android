package com.futo.platformplayer.tv.install

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.tv.sync.TvSyncIdentityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvInstallViewModel(application: Application) : AndroidViewModel(application) {
    private val _installer = TvInstaller(application)
    private val _store = TvInstallStore(application)
    private val _sync = TvSetupSync(application, _installer)
    private val _state = MutableStateFlow(State())
    private var _operation: Job? = null
    private var _loaded = false
    private var _previousDevice: TvInstallDevice? = null

    val state = _state.asStateFlow()
    var host = ""
    var pairingPort = ""
    var connectionPort = ""
    var usesPairing = true

    init {
        StateSync.instance.deviceUpdatedOrAdded.subscribe(this) { _, _ -> refreshSync() }
        StateSync.instance.deviceRemoved.subscribe(this) { refreshSync() }
        execute(R.string.tv_install_saved_failed) {
            val devices = withContext(Dispatchers.IO) { _store.load() }
            _loaded = true
            _state.update { it.copy(devices = devices) }
            refreshConnections()
        }
    }

    override fun onCleared() {
        StateSync.instance.deviceUpdatedOrAdded.remove(this)
        StateSync.instance.deviceRemoved.remove(this)
        _operation?.cancel()
        _installer.close()
        super.onCleared()
    }

    fun add() {
        if (_state.value.busy || !_loaded)
            return
        host = ""
        pairingPort = ""
        connectionPort = ""
        usesPairing = true
        _previousDevice = null
        _state.update { it.copy(page = Page.INSTALLED, selected = null, error = null, appInstalled = false) }
    }

    fun open(device: TvInstallDevice) {
        if (_state.value.busy)
            return
        host = device.host
        connectionPort = device.port.toString()
        usesPairing = device.usesPairing
        _previousDevice = device
        _state.update {
            it.copy(
                page = if (device.endpoint.serial in it.connected) Page.INSTALL else Page.CONNECT,
                selected = device,
                appInstalled = device.installedVersion != null,
                error = null)
        }
    }

    fun next() {
        if (_state.value.busy)
            return
        val page = when (_state.value.page) {
            Page.INSTALLED -> Page.INTRO
            Page.INTRO -> Page.DEVELOPER
            Page.DEVELOPER -> Page.ABOUT
            Page.ABOUT -> Page.BUILD
            Page.BUILD -> Page.DEVELOPER_OPTIONS
            Page.DEVELOPER_OPTIONS -> Page.DEBUGGING
            Page.DEBUGGING -> if (usesPairing) Page.PAIR else Page.ADDRESS
            else -> return
        }
        _state.update { it.copy(page = page, error = null) }
    }

    fun pair(code: String) {
        val endpoint = endpoint(pairingPort) ?: return
        if (!code.matches(Regex("[0-9]{6}"))) {
            _state.update { it.copy(error = R.string.tv_install_code_invalid) }
            return
        }
        execute(R.string.tv_install_pair_failed) {
            _installer.pair(endpoint, code)
            _state.update { it.copy(page = Page.CONNECT) }
            val port = try {
                TvAdbDiscovery.connectionPort(getApplication(), host)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (port != null) {
                connectionPort = port.toString()
                connectDevice(AdbEndpoint.parse(host, connectionPort))
            }
        }
    }

    fun developerOptionsEnabled() {
        if (!_state.value.busy && _state.value.page == Page.DEVELOPER)
            _state.update { it.copy(page = Page.DEVELOPER_OPTIONS, error = null) }
    }

    fun manualConnection() {
        if (!_state.value.busy) {
            if (connectionPort.isEmpty()) connectionPort = "5555"
            _state.update { it.copy(page = Page.CONNECT, error = null) }
        }
    }

    fun connect() {
        val endpoint = endpoint(if (_state.value.page == Page.ADDRESS) "5555" else connectionPort) ?: return
        connectionPort = endpoint.port.toString()
        _state.update { it.copy(page = Page.CONNECT) }
        execute(R.string.tv_install_connect_failed) { connectDevice(endpoint) }
    }

    fun install() {
        val device = _state.value.selected ?: return
        execute(R.string.tv_install_failed) {
            _installer.install(device)
            val installed = device.copy(installedVersion = BuildConfig.VERSION_NAME)
            save(installed)
            _state.update { it.copy(page = Page.SYNC, selected = installed, appInstalled = true) }
        }
    }

    fun launch() {
        val device = _state.value.selected ?: return
        execute(R.string.tv_install_launch_failed) {
            _installer.launch(device)
        }
    }

    fun refresh() {
        if (!_loaded || _state.value.page != Page.DEVICES)
            return
        execute(R.string.tv_install_connect_failed) { refreshConnections() }
    }

    fun forget(device: TvInstallDevice) {
        execute(R.string.tv_install_saved_failed) {
            if (device.endpoint.serial in _state.value.connected)
                _installer.disconnect(device.endpoint)
            val devices = _state.value.devices.filter { it.endpoint != device.endpoint }
            withContext(Dispatchers.IO) { _store.save(devices) }
            _state.update { it.copy(devices = devices, connected = it.connected - device.endpoint.serial) }
        }
    }

    fun back(): Boolean {
        if (_state.value.busy)
            return false
        val previous = when (_state.value.page) {
            Page.DEVICES -> return false
            Page.INSTALLED, Page.SECURITY -> Page.DEVICES
            Page.INTRO, Page.EXISTING -> Page.INSTALLED
            Page.DEVELOPER -> Page.INTRO
            Page.ABOUT -> Page.DEVELOPER
            Page.BUILD -> Page.ABOUT
            Page.DEVELOPER_OPTIONS -> Page.BUILD
            Page.DEBUGGING -> Page.DEVELOPER_OPTIONS
            Page.PAIR -> Page.DEBUGGING
            Page.ADDRESS -> Page.DEBUGGING
            Page.CONNECT -> if (_previousDevice != null) Page.DEVICES else Page.DEBUGGING
            Page.INSTALL -> Page.DEVICES
            Page.SYNC -> Page.INSTALL
            Page.DISABLE -> Page.SECURITY
        }
        _state.update { it.copy(page = previous, error = null) }
        return true
    }

    fun finishSetup() {
        execute(R.string.tv_install_saved_failed) {
            withContext(Dispatchers.IO) { _installer.close() }
            _state.update { it.copy(page = Page.DEVICES, selected = null, error = null, connected = emptySet(), syncDevices = _sync.devices()) }
        }
    }

    fun cancel() {
        _operation?.cancel()
    }

    fun pairAgain() {
        if (_state.value.busy)
            return
        usesPairing = true
        pairingPort = ""
        _state.update { it.copy(page = Page.PAIR, error = null) }
    }

    fun alreadyInstalled() {
        if (!_state.value.busy)
            _state.update { it.copy(page = Page.EXISTING, error = null) }
    }

    fun useAdb() {
        if (!_state.value.busy)
            _state.update { it.copy(page = Page.INTRO, error = null) }
    }

    fun skipInstall() {
        if (!_state.value.busy)
            _state.update { it.copy(page = if (it.appInstalled) Page.SYNC else Page.SECURITY, error = null) }
    }

    fun pairSync() {
        val device = _state.value.selected ?: return
        execute(R.string.tv_install_sync_failed) {
            val key = _sync.pair(device) { message -> _state.update { it.copy(status = message) } }
            val paired = device.copy(syncPublicKey = key)
            save(paired)
            _state.update { it.copy(page = Page.SECURITY, selected = paired, syncDevices = _sync.devices()) }
        }
    }

    fun skipSync() {
        if (!_state.value.busy)
            _state.update { it.copy(page = Page.SECURITY, error = null) }
    }

    fun debuggingSettings() {
        val device = _state.value.selected ?: return
        _state.update { it.copy(page = Page.DISABLE, error = null) }
        execute(R.string.tv_install_settings_failed) {
            _installer.connect(device.endpoint)
            _installer.openDebuggingSettings(device)
        }
    }

    fun forgetSync(key: String) {
        execute(R.string.tv_install_saved_failed) {
            StateSync.instance.delete(key)
            if (key !in StateSync.StoreBasedSyncDatabaseProvider().getAllAuthorizedDevices())
                TvSyncIdentityStore.forget(getApplication(), key)
            _state.update { it.copy(syncDevices = _sync.devices()) }
        }
    }

    private fun refreshSync() {
        viewModelScope.launch {
            val devices = withContext(Dispatchers.IO) { _sync.devices() }
            _state.update { it.copy(syncDevices = devices) }
        }
    }

    private fun endpoint(port: String): AdbEndpoint? {
        return try {
            AdbEndpoint.parse(host, port)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(error = R.string.tv_install_address_invalid) }
            null
        }
    }

    private suspend fun save(device: TvInstallDevice) {
        check(_loaded)
        val devices = _state.value.devices.filter {
            it.endpoint != device.endpoint && it.endpoint != _previousDevice?.endpoint
        } + device
        withContext(Dispatchers.IO) { _store.save(devices) }
        _previousDevice = device
        _state.update { it.copy(devices = devices) }
    }

    private suspend fun refreshConnections() {
        val devices = withContext(Dispatchers.IO) { _sync.devices() }
        _state.update { it.copy(syncDevices = devices) }
        if (_installer.available) {
            val connected = _installer.connected()
            _state.update { it.copy(connected = connected) }
        }
    }

    private suspend fun connectDevice(endpoint: AdbEndpoint) {
        val device = _installer.connect(endpoint).copy(usesPairing = usesPairing,
            installedVersion = _previousDevice?.installedVersion, syncPublicKey = _previousDevice?.syncPublicKey)
        val installed = _installer.isInstalled(device)
        save(device)
        _state.update { it.copy(page = Page.INSTALL, selected = device, appInstalled = installed,
            connected = it.connected + endpoint.serial) }
    }

    private fun execute(errorMessage: Int, action: suspend () -> Unit) {
        if (_state.value.busy)
            return
        _state.update { it.copy(busy = true, error = null, status = null) }
        _operation = viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { _installer.close() }
                _state.update { it.copy(connected = emptySet()) }
                throw error
            } catch (error: TvInstaller.Failure) {
                withContext(Dispatchers.IO) { _installer.close() }
                _state.update { it.copy(error = error.messageId, connected = emptySet()) }
            } catch (error: Exception) {
                withContext(Dispatchers.IO) { _installer.close() }
                _state.update { it.copy(error = errorMessage, connected = emptySet()) }
            } finally {
                _state.update { it.copy(busy = false, status = null) }
            }
        }
    }

    data class State(
        val page: Page = Page.DEVICES,
        val devices: List<TvInstallDevice> = emptyList(),
        val syncDevices: List<TvSetupSync.Device> = emptyList(),
        val appInstalled: Boolean = false,
        val status: Int? = null,
        val connected: Set<String> = emptySet(),
        val selected: TvInstallDevice? = null,
        val busy: Boolean = false,
        val error: Int? = null
    )

    enum class Page {
        DEVICES, INSTALLED, EXISTING, INTRO, DEVELOPER, ABOUT, BUILD, DEVELOPER_OPTIONS, DEBUGGING, PAIR, ADDRESS, CONNECT, INSTALL, SYNC, SECURITY, DISABLE
    }
}
