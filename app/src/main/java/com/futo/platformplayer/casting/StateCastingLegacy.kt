package com.futo.platformplayer.casting

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import android.util.Log
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress
import kotlinx.coroutines.delay

class StateCastingLegacy : StateCasting() {
    private var _nsdManager: NsdManager? = null

    private val _discoveryListeners = mapOf(
        "_googlecast._tcp" to createDiscoveryListener(::addOrUpdateChromeCastDevice),
        "_airplay._tcp" to createDiscoveryListener(::addOrUpdateAirPlayDevice),
        "_fastcast._tcp" to createDiscoveryListener(::addOrUpdateFastCastDevice),
        "_fcast._tcp" to createDiscoveryListener(::addOrUpdateFastCastDevice)
    )

    override fun handleUrl(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "fcast") {
            throw Exception("Expected scheme to be FCast")
        }

        val type = uri.host
        if (type != "r") {
            throw Exception("Expected type r")
        }

        val connectionInfo = uri.pathSegments[0]
        val json =
            Base64.decode(connectionInfo, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                .toString(Charsets.UTF_8)
        val networkConfig = Json.decodeFromString<FCastNetworkConfig>(json)
        val tcpService = networkConfig.services.first { v -> v.type == 0 }

        val foundInfo = addRememberedDevice(
            CastingDeviceInfo(
                name = networkConfig.name,
                type = CastProtocolType.FCAST,
                addresses = networkConfig.addresses.toTypedArray(),
                port = tcpService.port
            )
        )

        if (foundInfo != null) {
            connectDevice(deviceFromInfo(foundInfo))
        }
    }

    override fun onStop() {
        val ad = activeDevice ?: return;
        _resumeCastingDevice = ad.getDeviceInfo()
        Log.i(TAG, "_resumeCastingDevice set to '${ad.name}'")
        Logger.i(TAG, "Stopping active device because of onStop.");
        ad.disconnect();
    }

    @Synchronized
    override fun start(context: Context) {
        if (_started)
            return;
        _started = true;

        Log.i(TAG, "_resumeCastingDevice set null start")
        _resumeCastingDevice = null;

        Logger.i(TAG, "CastingService starting...");

        _castServer.start();
        enableDeveloper(true);

        Logger.i(TAG, "CastingService started.");

        _nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        startDiscovering()
    }

    @Synchronized
    private fun startDiscovering() {
        _nsdManager?.apply {
            _discoveryListeners.forEach {
                discoverServices(it.key, NsdManager.PROTOCOL_DNS_SD, it.value)
            }
        }
    }

    @Synchronized
    private fun stopDiscovering() {
        _nsdManager?.apply {
            _discoveryListeners.forEach {
                try {
                    stopServiceDiscovery(it.value)
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to stop service discovery", e)
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!_started)
            return;

        _started = false;

        Logger.i(TAG, "CastingService stopping.")

        stopDiscovering()
        _scopeIO.cancel();
        _scopeMain.cancel();

        Logger.i(TAG, "Stopping active device because StateCasting is being stopped.")
        val d = activeDevice;
        activeDevice = null;
        d?.disconnect();

        _castServer.stop();
        _castServer.removeAllHandlers();

        Logger.i(TAG, "CastingService stopped.")

        _nsdManager = null
    }

    private fun createDiscoveryListener(addOrUpdate: (String, Array<InetAddress>, Int) -> Unit): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started for $regType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "service lost: $service")
                // TODO: Handle service lost, e.g., remove device
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed for $serviceType: Error code:$errorCode")
                try {
                    _nsdManager?.stopServiceDiscovery(this)
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to stop service discovery", e)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed for $serviceType: Error code:$errorCode")
                try {
                    _nsdManager?.stopServiceDiscovery(this)
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to stop service discovery", e)
                }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.v(TAG, "Service discovery success for ${service.serviceType}: $service")
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    service.hostAddresses.toTypedArray()
                } else {
                    arrayOf(service.host)
                }
                addOrUpdate(service.serviceName, addresses, service.port)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    _nsdManager?.registerServiceInfoCallback(
                        service,
                        { it.run() },
                        object : NsdManager.ServiceInfoCallback {
                            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                Log.v(TAG, "onServiceUpdated: $serviceInfo")
                                addOrUpdate(
                                    serviceInfo.serviceName,
                                    serviceInfo.hostAddresses.toTypedArray(),
                                    serviceInfo.port
                                )
                            }

                            override fun onServiceLost() {
                                Log.v(TAG, "onServiceLost: $service")
                                // TODO: Handle service lost
                            }

                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                Log.v(TAG, "onServiceInfoCallbackRegistrationFailed: $errorCode")
                            }

                            override fun onServiceInfoCallbackUnregistered() {
                                Log.v(TAG, "onServiceInfoCallbackUnregistered")
                            }
                        })
                } else {
                    _nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.v(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.v(TAG, "Resolve Succeeded: $serviceInfo")
                            addOrUpdate(
                                serviceInfo.serviceName,
                                arrayOf(serviceInfo.host),
                                serviceInfo.port
                            )
                        }
                    })
                }
            }
        }
    }

    override fun startUpdateTimeJob(
        onTimeJobTimeChanged_s: Event1<Long>,
        setTime: (Long) -> Unit
    ): Job? {
        val d = activeDevice;
        if (d is CastingDeviceLegacyWrapper && (d.inner is AirPlayCastingDevice || d.inner is ChromecastCastingDevice)) {
            return _scopeMain.launch {
                while (true) {
                    val device = instance.activeDevice
                    if (device == null || !device.isPlaying) {
                        break
                    }

                    delay(1000)
                    val time_ms = (device.expectedCurrentTime * 1000.0).toLong()
                    setTime(time_ms)
                    onTimeJobTimeChanged_s.emit(device.expectedCurrentTime.toLong())
                }
            }
        }
        return null
    }

    override fun deviceFromInfo(deviceInfo: CastingDeviceInfo): CastingDevice {
        return CastingDeviceLegacyWrapper(
            when (deviceInfo.type) {
                CastProtocolType.CHROMECAST -> {
                    ChromecastCastingDevice(deviceInfo);
                }

                CastProtocolType.AIRPLAY -> {
                    AirPlayCastingDevice(deviceInfo);
                }

                CastProtocolType.FCAST -> {
                    FCastCastingDevice(deviceInfo);
                }
            }
        )
    }

    private fun addOrUpdateChromeCastDevice(
        name: String,
        addresses: Array<InetAddress>,
        port: Int
    ) {
        return addOrUpdateCastDevice(
            name,
            deviceFactory = {
                CastingDeviceLegacyWrapper(
                    ChromecastCastingDevice(
                        name,
                        addresses,
                        port
                    )
                )
            },
            deviceUpdater = { d ->
                if (d.isReady || d !is CastingDeviceLegacyWrapper || d.inner !is ChromecastCastingDevice) {
                    return@addOrUpdateCastDevice false;
                }

                val changed =
                    addresses.contentEquals(d.inner.addresses) || d.name != name || d.inner.port != port;
                if (changed) {
                    d.inner.name = name;
                    d.inner.addresses = addresses;
                    d.inner.port = port;
                }

                return@addOrUpdateCastDevice changed;
            }
        );
    }

    private fun addOrUpdateAirPlayDevice(name: String, addresses: Array<InetAddress>, port: Int) {
        return addOrUpdateCastDevice(
            name,
            deviceFactory = {
                CastingDeviceLegacyWrapper(
                    AirPlayCastingDevice(
                        name,
                        addresses,
                        port
                    )
                )
            },
            deviceUpdater = { d ->
                if (d.isReady || d !is CastingDeviceLegacyWrapper || d.inner !is AirPlayCastingDevice) {
                    return@addOrUpdateCastDevice false;
                }

                val changed =
                    addresses.contentEquals(addresses) || d.name != name || d.inner.port != port;
                if (changed) {
                    d.inner.name = name;
                    d.inner.port = port;
                    d.inner.addresses = addresses;
                }

                return@addOrUpdateCastDevice changed;
            }
        );
    }

    private fun addOrUpdateFastCastDevice(name: String, addresses: Array<InetAddress>, port: Int) {
        return addOrUpdateCastDevice(
            name,
            deviceFactory = { CastingDeviceLegacyWrapper(FCastCastingDevice(name, addresses, port)) },
            deviceUpdater = { d ->
                if (d.isReady || d !is CastingDeviceLegacyWrapper || d.inner !is FCastCastingDevice) {
                    return@addOrUpdateCastDevice false;
                }

                val changed =
                    addresses.contentEquals(addresses) || d.name != name || d.inner.port != port;
                if (changed) {
                    d.inner.name = name;
                    d.inner.port = port;
                    d.inner.addresses = addresses;
                }

                return@addOrUpdateCastDevice changed;
            }
        );
    }

    private inline fun addOrUpdateCastDevice(
        name: String,
        deviceFactory: () -> CastingDevice,
        deviceUpdater: (device: CastingDevice) -> Boolean
    ) {
        var invokeEvents: (() -> Unit)? = null;

        synchronized(devices) {
            val device = devices[name];
            if (device != null) {
                val changed = deviceUpdater(device);
                if (changed) {
                    invokeEvents = {
                        onDeviceChanged.emit(device);
                    }
                }
            } else {
                val newDevice = deviceFactory();
                this.devices[name] = newDevice

                invokeEvents = {
                    onDeviceAdded.emit(newDevice);
                };
            }
        }

        invokeEvents?.let { _scopeMain.launch { it(); }; };
    }

    @Serializable
    private data class FCastNetworkConfig(
        val name: String,
        val addresses: List<String>,
        val services: List<FCastService>
    )

    @Serializable
    private data class FCastService(
        val port: Int,
        val type: Int
    )

    companion object {
        private val TAG = "StateCastingLegacy"
    }
}
