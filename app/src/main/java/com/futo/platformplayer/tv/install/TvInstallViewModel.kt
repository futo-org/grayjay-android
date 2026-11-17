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
    private val _download = TvApkDownload(application)
    private val _store = TvInstallStore(application)
    private val _sync = TvSetupSync(application, _installer)
    private val _state = MutableStateFlow(State())
    private var _operation: Job? = null
    private var _loaded = false
    private var _previousDevice: TvInstallDevice? = null

    val state = _state.asStateFlow()
    var pairingAddress = ""
    var connectionAddress = ""
    var usesPairing: Boolean
        get() = _state.value.debuggingMethod == DebuggingMethod.WIRELESS
        set(value) {
            _state.update { it.copy(debuggingMethod = if (value) DebuggingMethod.WIRELESS else DebuggingMethod.NETWORK) }
        }

    var connectionPort: String
        get() = connectionAddress.substringAfterLast(':', "")
        set(value) {
            val host = _state.value.pairedHost ?: return
            connectionAddress = if (host.contains(':')) "[$host]:$value" else "$host:$value"
        }

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
        pairingAddress = ""
        connectionAddress = ""
        _previousDevice = null
        _state.update { it.copy(page = Page.TYPE, tvType = TvType.GOOGLE_TV, debuggingMethod = DebuggingMethod.WIRELESS,
            selected = null, error = null, appInstalled = false, apk = null, pairedHost = null) }
    }

    fun chooseType(type: TvType) {
        if (_state.value.busy || _state.value.page != Page.TYPE) return
        _state.update { it.copy(page = Page.INSTALLED, tvType = type, debuggingMethod = when (type) {
            TvType.GOOGLE_TV -> DebuggingMethod.WIRELESS
            TvType.ANDROID_TV -> null
            TvType.SHIELD -> DebuggingMethod.NETWORK
        }) }
    }

    fun details(device: TvInstallDevice) {
        if (!_state.value.busy)
            _state.update { it.copy(page = Page.DETAILS, selected = device, credentialKey = device.syncPublicKey,
                credentialName = device.name, error = null) }
    }

    fun details(device: TvSetupSync.Device) {
        if (!_state.value.busy)
            _state.update { it.copy(page = Page.DETAILS, selected = null, credentialKey = device.key,
                credentialName = device.name, error = null) }
    }

    fun existingPaired(key: String?) {
        if (key == null) {
            finishSetup()
            return
        }
        val device = _sync.devices().firstOrNull { it.key == key } ?: return
        _state.update { it.copy(page = Page.CREDENTIALS, selected = null, credentialKey = key,
            credentialName = device.name, error = null) }
    }

    fun credentialsFinished() {
        if (_state.value.page != Page.CREDENTIALS) return
        if (_state.value.selected == null) finishSetup()
        else _state.update { it.copy(page = Page.SECURITY, error = null) }
    }

    fun open(device: TvInstallDevice) {
        if (_state.value.busy)
            return
        connectionAddress = device.endpoint.serial
        usesPairing = device.usesPairing
        _previousDevice = device
        val connected = device.endpoint.serial in _state.value.connected
        _state.update {
            it.copy(
                page = Page.CONNECT,
                tvType = TvType.ANDROID_TV,
                selected = device,
                apk = null,
                pairedHost = null,
                appInstalled = device.installedVersion != null,
                error = null)
        }
        if (connected)
            execute(R.string.tv_install_connect_failed) { connectDevice(device.endpoint) }
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
            Page.DEBUGGING -> when (_state.value.debuggingMethod) {
                DebuggingMethod.WIRELESS -> Page.PAIR
                DebuggingMethod.NETWORK -> Page.CONNECT
                null -> return
            }
            else -> return
        }
        _state.update { it.copy(page = page, error = null,
            pairedHost = if (it.page == Page.DEBUGGING) null else it.pairedHost) }
    }

    fun pair(code: String) {
        val endpoint = endpoint(pairingAddress) ?: return
        if (!code.matches(Regex("[0-9]{6}"))) {
            _state.update { it.copy(error = R.string.tv_install_code_invalid) }
            return
        }
        execute(R.string.tv_install_pair_failed) {
            _installer.pair(endpoint, code)
            connectionAddress = endpoint.serial.substringBeforeLast(':') + ":"
            _state.update { it.copy(page = Page.CONNECT, pairedHost = endpoint.host) }
            val port = try {
                TvAdbDiscovery.connectionPort(getApplication(), endpoint.host)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (port != null) {
                val connection = AdbEndpoint(endpoint.host, port)
                connectionAddress = connection.serial
                connectDevice(connection)
            }
        }
    }

    fun developerOptionsEnabled() {
        if (!_state.value.busy && _state.value.page == Page.DEVELOPER)
            _state.update { it.copy(page = Page.DEVELOPER_OPTIONS, error = null) }
    }

    fun connect() {
        val endpoint = endpoint(connectionAddress, if (usesPairing) null else 5555) ?: return
        connectionAddress = endpoint.serial
        execute(R.string.tv_install_connect_failed) { connectDevice(endpoint) }
    }

    fun install() {
        val device = _state.value.selected ?: return
        val approved = _state.value.apk
        execute(if (approved?.downloadRequired == true) R.string.tv_install_download_failed else R.string.tv_install_failed) {
            _state.update { it.copy(status = R.string.tv_install_checking) }
            _installer.connect(device.endpoint)
            val apk = _installer.packageFor(device)
            _state.update { it.copy(apk = apk) }
            if (apk.downloadRequired) {
                if (apk != approved)
                    return@execute
                _state.update { it.copy(status = R.string.tv_install_downloading) }
                _download.withApk(apk.abi, { progress -> _state.update { it.copy(downloadProgress = progress) } }) { files ->
                    _state.update { it.copy(status = R.string.tv_install_installing, downloadProgress = null) }
                    _installer.connect(device.endpoint)
                    _installer.install(device, files)
                }
            } else {
                _state.update { it.copy(status = R.string.tv_install_installing) }
                _installer.install(device)
            }
            val installed = device.copy(installedVersion = BuildConfig.VERSION_NAME)
            save(installed)
            _state.update { it.copy(page = Page.SYNC, selected = installed, appInstalled = true,
                connected = it.connected + device.endpoint.serial) }
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
            Page.TYPE, Page.SECURITY, Page.DETAILS -> Page.DEVICES
            Page.INSTALLED -> Page.TYPE
            Page.CREDENTIALS -> { credentialsFinished(); return true }
            Page.INTRO, Page.EXISTING -> Page.INSTALLED
            Page.DEVELOPER -> Page.INTRO
            Page.ABOUT -> Page.DEVELOPER
            Page.BUILD -> Page.ABOUT
            Page.DEVELOPER_OPTIONS -> Page.BUILD
            Page.DEBUGGING -> Page.DEVELOPER_OPTIONS
            Page.PAIR -> Page.DEBUGGING
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
        pairingAddress = connectionAddress.substringBeforeLast(':', connectionAddress).let {
            if (it.isEmpty()) "" else "$it:"
        }
        _state.update { it.copy(page = Page.PAIR, error = null, pairedHost = null) }
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
            _state.update { it.copy(page = Page.CREDENTIALS, selected = paired, syncDevices = _sync.devices(),
                credentialKey = key, credentialName = paired.name) }
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

    private fun endpoint(address: String, defaultPort: Int? = null): AdbEndpoint? {
        return try {
            AdbEndpoint.parse(address, defaultPort)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(error = if (it.page == Page.CONNECT && it.pairedHost != null)
                R.string.tv_install_connection_port_invalid else R.string.tv_install_address_invalid) }
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
        val apk = _installer.packageFor(device)
        save(device)
        _state.update { it.copy(page = Page.INSTALL, selected = device, appInstalled = installed, apk = apk,
            connected = it.connected + endpoint.serial) }
    }

    private fun execute(errorMessage: Int, action: suspend () -> Unit) {
        if (_state.value.busy)
            return
        _state.update { it.copy(busy = true, error = null, status = null, downloadProgress = null) }
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
                _state.update { it.copy(busy = false, status = null, downloadProgress = null) }
            }
        }
    }

    data class State(
        val page: Page = Page.DEVICES,
        val tvType: TvType = TvType.GOOGLE_TV,
        val debuggingMethod: DebuggingMethod? = DebuggingMethod.WIRELESS,
        val devices: List<TvInstallDevice> = emptyList(),
        val syncDevices: List<TvSetupSync.Device> = emptyList(),
        val appInstalled: Boolean = false,
        val pairedHost: String? = null,
        val apk: TvInstaller.Package? = null,
        val downloadProgress: TvApkDownload.Progress? = null,
        val status: Int? = null,
        val connected: Set<String> = emptySet(),
        val selected: TvInstallDevice? = null,
        val credentialKey: String? = null,
        val credentialName: String = "",
        val busy: Boolean = false,
        val error: Int? = null
    )

    enum class Page {
        DEVICES, DETAILS, TYPE, INSTALLED, EXISTING, INTRO, DEVELOPER, ABOUT, BUILD, DEVELOPER_OPTIONS, DEBUGGING, PAIR, CONNECT, INSTALL, SYNC, CREDENTIALS, SECURITY, DISABLE
    }

    enum class TvType {
        GOOGLE_TV, ANDROID_TV, SHIELD
    }

    enum class DebuggingMethod {
        WIRELESS, NETWORK
    }
}
