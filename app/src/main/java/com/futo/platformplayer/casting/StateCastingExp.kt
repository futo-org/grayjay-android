package com.futo.platformplayer.casting

import android.content.Context
import android.util.Log
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.fcast.sender_sdk.DeviceInfo as RsDeviceInfo
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.NsdDeviceDiscoverer

class StateCastingExp : StateCasting() {
    private val _context = CastContext()
    var _deviceDiscoverer: NsdDeviceDiscoverer? = null

    class DiscoveryEventHandler(
        private val onDeviceAdded: (RsDeviceInfo) -> Unit,
        private val onDeviceRemoved: (String) -> Unit,
        private val onDeviceUpdated: (RsDeviceInfo) -> Unit,
    ) : org.fcast.sender_sdk.DeviceDiscovererEventHandler {
        override fun deviceAvailable(deviceInfo: RsDeviceInfo) {
            onDeviceAdded(deviceInfo)
        }

        override fun deviceChanged(deviceInfo: RsDeviceInfo) {
            onDeviceUpdated(deviceInfo)
        }

        override fun deviceRemoved(deviceName: String) {
            onDeviceRemoved(deviceName)
        }
    }

    init {
        if (BuildConfig.DEBUG) {
            org.fcast.sender_sdk.initLogger(org.fcast.sender_sdk.LogLevelFilter.DEBUG)
        }
    }

    override fun handleUrl(url: String) {
        try {
            val foundDeviceInfo = org.fcast.sender_sdk.deviceInfoFromUrl(url)!!
            val foundDevice = _context.createDeviceFromInfo(foundDeviceInfo)
            connectDevice(CastingDeviceExp(foundDevice))
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to handle URL: $e")
        }
    }

    override fun onStop() {
        val ad = activeDevice ?: return
        _resumeCastingDevice = ad.getDeviceInfo()
        Log.i(TAG, "_resumeCastingDevice set to '${ad.name}'")
        Logger.i(TAG, "Stopping active device because of onStop.")
        try {
            ad.disconnect()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to disconnect from device: $e")
        }
    }

    @Synchronized
    override fun start(context: Context) {
        if (_started)
            return
        _started = true

        Log.i(TAG, "_resumeCastingDevice set null start")
        _resumeCastingDevice = null

        Logger.i(TAG, "CastingService starting...")

        _castServer.start()
        enableDeveloper(true)

        Logger.i(TAG, "CastingService started.")

        _deviceDiscoverer = NsdDeviceDiscoverer(
            context,
            DiscoveryEventHandler(
                { deviceInfo -> // Added
                    Logger.i(TAG, "Device added: ${deviceInfo.name}")
                    val device = _context.createDeviceFromInfo(deviceInfo)
                    val deviceHandle = CastingDeviceExp(device)
                    devices[deviceHandle.device.name()] = deviceHandle
                    invokeInMainScopeIfRequired {
                        onDeviceAdded.emit(deviceHandle)
                    }
                },
                { deviceName -> // Removed
                    invokeInMainScopeIfRequired {
                        if (devices.containsKey(deviceName)) {
                            val device = devices.remove(deviceName)
                            if (device != null) {
                                onDeviceRemoved.emit(device)
                            }
                        }
                    }
                },
                { deviceInfo -> // Updated
                    Logger.i(TAG, "Device updated: $deviceInfo")
                    val handle = devices[deviceInfo.name]
                    if (handle != null && handle is CastingDeviceExp) {
                        handle.device.setPort(deviceInfo.port)
                        handle.device.setAddresses(deviceInfo.addresses)
                        invokeInMainScopeIfRequired {
                            onDeviceChanged.emit(handle)
                        }
                    }
                },
            )
        )
    }

    @Synchronized
    override fun stop() {
        if (!_started) {
            return
        }

        _started = false

        Logger.i(TAG, "CastingService stopping.")

        _scopeIO.cancel()
        _scopeMain.cancel()

        Logger.i(TAG, "Stopping active device because StateCasting is being stopped.")
        val d = activeDevice
        activeDevice = null
        try {
            d?.disconnect()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to disconnect device: $e")
        }

        _castServer.stop()
        _castServer.removeAllHandlers()

        Logger.i(TAG, "CastingService stopped.")

        _deviceDiscoverer = null
    }

    override fun startUpdateTimeJob(
        onTimeJobTimeChanged_s: Event1<Long>,
        setTime: (Long) -> Unit
    ): Job? = null

    override fun deviceFromInfo(deviceInfo: CastingDeviceInfo): CastingDeviceExp {
        val rsAddrs =
            deviceInfo.addresses.map { org.fcast.sender_sdk.tryIpAddrFromStr(it) } // Throws!
        val rsDeviceInfo = RsDeviceInfo(
            name = deviceInfo.name,
            protocol = when (deviceInfo.type) {
                com.futo.platformplayer.casting.CastProtocolType.CHROMECAST -> ProtocolType.CHROMECAST
                com.futo.platformplayer.casting.CastProtocolType.FCAST -> ProtocolType.F_CAST
                else -> throw IllegalArgumentException()
            },
            addresses = rsAddrs,
            port = deviceInfo.port.toUShort(),
        )

        return CastingDeviceExp(_context.createDeviceFromInfo(rsDeviceInfo))
    }

    companion object {
        private val TAG = "StateCastingExp"
    }
}
