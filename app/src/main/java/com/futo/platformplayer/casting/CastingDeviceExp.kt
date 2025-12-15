package com.futo.platformplayer.casting

import android.os.Build
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.polycentric.core.Event
import org.fcast.sender_sdk.ApplicationInfo
import org.fcast.sender_sdk.KeyEvent
import org.fcast.sender_sdk.MediaEvent
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.Source
import java.net.InetAddress
import org.fcast.sender_sdk.CastingDevice as RsCastingDevice;
import org.fcast.sender_sdk.DeviceEventHandler as RsDeviceEventHandler;
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceFeature
import org.fcast.sender_sdk.EventSubscription
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaItemEventType
import org.fcast.sender_sdk.Metadata
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.urlFormatIpAddr
import java.net.Inet4Address
import java.net.Inet6Address

private fun ipAddrToInetAddress(addr: IpAddr): InetAddress = when (addr) {
    is IpAddr.V4 -> Inet4Address.getByAddress(
        byteArrayOf(
            addr.o1.toByte(),
            addr.o2.toByte(),
            addr.o3.toByte(),
            addr.o4.toByte()
        )
    )

    is IpAddr.V6 -> Inet6Address.getByAddress(
        byteArrayOf(
            addr.o1.toByte(),
            addr.o2.toByte(),
            addr.o3.toByte(),
            addr.o4.toByte(),
            addr.o5.toByte(),
            addr.o6.toByte(),
            addr.o7.toByte(),
            addr.o8.toByte(),
            addr.o9.toByte(),
            addr.o10.toByte(),
            addr.o11.toByte(),
            addr.o12.toByte(),
            addr.o13.toByte(),
            addr.o14.toByte(),
            addr.o15.toByte(),
            addr.o16.toByte()
        )
    )
}

class CastingDeviceExp(val device: RsCastingDevice) : CastingDevice() {
    class EventHandler : RsDeviceEventHandler {
        var onConnectionStateChanged = Event1<DeviceConnectionState>();
        var onPlayChanged = Event1<Boolean>()
        var onTimeChanged = Event1<Double>()
        var onDurationChanged = Event1<Double>()
        var onVolumeChanged = Event1<Double>()
        var onSpeedChanged = Event1<Double>()
        var onMediaItemEnd = Event0()

        override fun connectionStateChanged(state: DeviceConnectionState) {
            onConnectionStateChanged.emit(state)
        }

        override fun volumeChanged(volume: Double) {
            onVolumeChanged.emit(volume)
        }

        override fun timeChanged(time: Double) {
            onTimeChanged.emit(time)
        }

        override fun playbackStateChanged(state: PlaybackState) {
            onPlayChanged.emit(state == PlaybackState.PLAYING)
        }

        override fun durationChanged(duration: Double) {
            onDurationChanged.emit(duration)
        }

        override fun speedChanged(speed: Double) {
            onSpeedChanged.emit(speed)
        }

        override fun sourceChanged(source: Source) {
            // TODO
        }

        override fun keyEvent(event: KeyEvent) {
            // Unreachable
        }

        override fun mediaEvent(event: MediaEvent) {
            if (event.type == MediaItemEventType.END) {
                onMediaItemEnd.emit()
            }
        }

        override fun playbackError(message: String) {
            Logger.e(TAG, "Playback error: $message")
        }
    }

    val eventHandler = EventHandler()
    override val isReady: Boolean
        get() = device.isReady()
    override val name: String
        get() = device.name()
    override var usedRemoteAddress: InetAddress? = null
    override var localAddress: InetAddress? = null
    override fun canSetVolume(): Boolean = device.supportsFeature(DeviceFeature.SET_VOLUME)
    override fun canSetSpeed(): Boolean = device.supportsFeature(DeviceFeature.SET_SPEED)

    override val onConnectionStateChanged =
        Event1<CastConnectionState>()
    override val onPlayChanged: Event1<Boolean>
        get() = eventHandler.onPlayChanged
    override val onTimeChanged: Event1<Double>
        get() = eventHandler.onTimeChanged
    override val onDurationChanged: Event1<Double>
        get() = eventHandler.onDurationChanged
    override val onVolumeChanged: Event1<Double>
        get() = eventHandler.onVolumeChanged
    override val onSpeedChanged: Event1<Double>
        get() = eventHandler.onSpeedChanged
    override val onMediaItemEnd: Event0
        get() = eventHandler.onMediaItemEnd

    override fun resumePlayback() = device.resumePlayback()
    override fun pausePlayback() = device.pausePlayback()
    override fun stopPlayback() = device.stopPlayback()
    override fun seekTo(timeSeconds: Double) = device.seek(timeSeconds)
    override fun changeVolume(newVolume: Double) {
        device.changeVolume(newVolume)
        volume = newVolume
    }
    override fun changeSpeed(speed: Double) = device.changeSpeed(speed)
    override fun connect() = device.connect(
        ApplicationInfo(
            "Grayjay Android",
            "${BuildConfig.VERSION_NAME}-${BuildConfig.FLAVOR}",
            "${Build.MANUFACTURER} ${Build.MODEL}"
        ),
        eventHandler,
        1000.toULong()
    )

    override fun disconnect() = device.disconnect()

    override fun getDeviceInfo(): CastingDeviceInfo {
        val info = device.getDeviceInfo()
        return CastingDeviceInfo(
            info.name,
            when (info.protocol) {
                ProtocolType.CHROMECAST -> CastProtocolType.CHROMECAST
                ProtocolType.F_CAST -> CastProtocolType.FCAST
            },
            addresses = info.addresses.map { urlFormatIpAddr(it) }.toTypedArray(),
            port = info.port.toInt(),
        )
    }

    override fun getAddresses(): List<InetAddress> = device.getAddresses().map {
        ipAddrToInetAddress(it)
    }

    override fun loadVideo(
        streamType: String,
        contentType: String,
        contentId: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?,
        metadata: Metadata?
    ) = device.load(
        LoadRequest.Video(
            contentType = contentType,
            url = contentId,
            resumePosition = resumePosition,
            speed = speed,
            volume = volume,
            metadata = metadata,
            requestHeaders = null,
        )
    )

    override fun loadContent(
        contentType: String,
        content: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?,
        metadata: Metadata?
    ) = device.load(
        LoadRequest.Content(
            contentType = contentType,
            content = content,
            resumePosition = resumePosition,
            speed = speed,
            volume = volume,
            metadata = metadata,
            requestHeaders = null,
        )
    )

    override var connectionState = CastConnectionState.DISCONNECTED
    override val protocolType: CastProtocolType
        get() = when (device.castingProtocol()) {
            ProtocolType.CHROMECAST -> CastProtocolType.CHROMECAST
            ProtocolType.F_CAST -> CastProtocolType.FCAST
        }
    override var volume: Double = 1.0
    override var duration: Double = 0.0
    private var lastTimeChangeTime_ms: Long = 0
    override var time: Double = 0.0
    override var speed: Double = 0.0
    override var isPlaying: Boolean = false

    override val expectedCurrentTime: Double
        get() {
            val diff =
                if (isPlaying) ((System.currentTimeMillis() - lastTimeChangeTime_ms).toDouble() / 1000.0) else 0.0;
            return time + diff
        }

    init {
        eventHandler.onConnectionStateChanged.subscribe { newState ->
            when (newState) {
                is DeviceConnectionState.Connected -> {
                    if (device.supportsFeature(DeviceFeature.MEDIA_EVENT_SUBSCRIPTION)) {
                        try {
                            device.subscribeEvent(EventSubscription.MediaItemEnd)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to subscribe to MediaItemEnd events: $e")
                        }
                    }
                    usedRemoteAddress = ipAddrToInetAddress(newState.usedRemoteAddr)
                    localAddress = ipAddrToInetAddress(newState.localAddr)
                    connectionState = CastConnectionState.CONNECTED
                    onConnectionStateChanged.emit(CastConnectionState.CONNECTED)
                }

                DeviceConnectionState.Connecting, DeviceConnectionState.Reconnecting ->  {
                    connectionState = CastConnectionState.CONNECTING
                    onConnectionStateChanged.emit(CastConnectionState.CONNECTING)
                }

                DeviceConnectionState.Disconnected -> {
                    connectionState = CastConnectionState.DISCONNECTED
                    onConnectionStateChanged.emit(CastConnectionState.DISCONNECTED)
                }
            }

            if (newState == DeviceConnectionState.Disconnected) {
                try {
                    Logger.i(TAG, "Stopping device")
                    device.disconnect()
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to stop device: $e")
                }
            }
        }
        eventHandler.onPlayChanged.subscribe { isPlaying = it }
        eventHandler.onTimeChanged.subscribe {
            lastTimeChangeTime_ms = System.currentTimeMillis()
            time = it
        }
        eventHandler.onDurationChanged.subscribe { duration = it }
        eventHandler.onVolumeChanged.subscribe { volume = it }
        eventHandler.onSpeedChanged.subscribe { speed = it }
    }

    override fun ensureThreadStarted() {}

    companion object {
        private val TAG = "CastingDeviceExp"
    }
}
