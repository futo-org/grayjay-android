package com.futo.platformplayer.casting

import android.os.Build
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.fcast.sender_sdk.ApplicationInfo
import org.fcast.sender_sdk.CastingDevice as RsCastingDevice
import org.fcast.sender_sdk.MediaTrack
import org.fcast.sender_sdk.MediaTrackType
import java.net.InetAddress
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.Source
import org.fcast.sender_sdk.urlFormatIpAddr
import java.net.Inet4Address
import java.net.Inet6Address
import org.fcast.sender_sdk.DeviceEventHandler as RsDeviceEventHandler;
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceFeature
import org.fcast.sender_sdk.ReceiverCapabilities
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.Metadata
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.QueueState
import org.fcast.sender_sdk.ReceiverError
import org.fcast.sender_sdk.SubtitleContent
import org.fcast.sender_sdk.SubtitleSource
import org.fcast.sender_sdk.TrackList

enum class CastConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

@Serializable(with = CastProtocolType.CastProtocolTypeSerializer::class)
enum class CastProtocolType {
    CHROMECAST,
    AIRPLAY,
    FCAST;

    object CastProtocolTypeSerializer : KSerializer<CastProtocolType> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("CastProtocolType", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: CastProtocolType) {
            encoder.encodeString(value.name)
        }

        override fun deserialize(decoder: Decoder): CastProtocolType {
            val name = decoder.decodeString()
            return when (name) {
                "FASTCAST" -> FCAST // Handle the renamed case
                else -> CastProtocolType.valueOf(name)
            }
        }
    }
}

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

class CastingDevice(val device: RsCastingDevice) {
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
            if (state == PlaybackState.ENDED) {
                onMediaItemEnd.emit()
            } else {
                onPlayChanged.emit(state == PlaybackState.PLAYING)
            }
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

        override fun playbackError(message: String) {
            Logger.e(TAG, "Playback error: $message")
        }

        override fun tracksAvailable(tracks: List<MediaTrack>) {}

        override fun trackSelected(id: UInt?, typ: MediaTrackType) {}

        override fun playbackStopped() {}

        override fun tracksChanged(tracks: TrackList) {}

        override fun queueChanged(queue: QueueState) {}

        override fun commandError(error: ReceiverError) {}
    }

    val eventHandler = EventHandler()
    val isReady: Boolean
        get() = device.isReady()
    val name: String
        get() = device.name()
    var usedRemoteAddress: InetAddress? = null
    var localAddress: InetAddress? = null
    // FCast V4 only, null for v2/v3/gcast
    var receiverCapabilities: ReceiverCapabilities? = null
        private set

    fun isSabrSupported(): Boolean =
        receiverCapabilities?.media?.protocols?.any { it == "sabr" } == true
    // FCast V4 only
    fun supportsExternalSubtitles(): Boolean =
        receiverCapabilities?.media?.externalSubtitles == true
    fun canSetVolume(): Boolean = device.supportsFeature(DeviceFeature.SET_VOLUME)
    fun canSetSpeed(): Boolean = device.supportsFeature(DeviceFeature.SET_SPEED)

    fun addSubtitleSource(content: SubtitleContent, select: Boolean, name: String?) =
        device.addSubtitleSource(SubtitleSource(content, select, name))
    fun disableSubtitles() = device.changeTrack(null, MediaTrackType.SUBTITLE)

    val onConnectionStateChanged =
        Event1<CastConnectionState>()
    val onPlayChanged = Event1<Boolean>()
    val onTimeChanged = Event1<Double>()
    val onDurationChanged = Event1<Double>()
    val onVolumeChanged = Event1<Double>()
    val onSpeedChanged = Event1<Double>()
    val onMediaItemEnd = Event0()

    fun resumePlayback() = device.resumePlayback()
    fun pausePlayback() = device.pausePlayback()
    fun stopPlayback() = device.stopPlayback()
    fun seekTo(timeSeconds: Double) = device.seek(timeSeconds)
    fun changeVolume(newVolume: Double) {
        device.changeVolume(newVolume)
        volume = newVolume
    }
    fun changeSpeed(speed: Double) = device.changeSpeed(speed)
    fun connect() = device.connect(
        ApplicationInfo(
            "Grayjay Android",
            "${BuildConfig.VERSION_NAME}-${BuildConfig.FLAVOR}",
            "${Build.MANUFACTURER} ${Build.MODEL}"
        ),
        eventHandler,
        1000.toULong()
    )

    fun disconnect() = device.disconnect()

    fun getDeviceInfo(): CastingDeviceInfo {
        val info = device.getDeviceInfo()
        return CastingDeviceInfo(
            info.name,
            when (info.protocol) {
                ProtocolType.CHROMECAST -> CastProtocolType.CHROMECAST
                ProtocolType.F_CAST -> CastProtocolType.FCAST
            },
            addresses = info.addresses.map { urlFormatIpAddr(it) }.toTypedArray(),
            port = info.port.toInt(),
            txtRecords = info.txtRecords,
        )
    }

    fun getAddresses(): List<InetAddress> = device.getAddresses().map {
        ipAddrToInetAddress(it)
    }

    fun loadVideo(
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
            volume = null,
            metadata = metadata,
            requestHeaders = null,
        ),
        500.toULong()
    )

    fun loadContent(
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
            volume = null,
            metadata = metadata,
            requestHeaders = null,
        ),
        500.toULong()
    )

    var connectionState = CastConnectionState.DISCONNECTED
    val protocolType: CastProtocolType
        get() = when (device.castingProtocol()) {
            ProtocolType.CHROMECAST -> CastProtocolType.CHROMECAST
            ProtocolType.F_CAST -> CastProtocolType.FCAST
        }
    var volume: Double = 1.0
    var duration: Double = 0.0
    private var lastTimeChangeTime_ms: Long = 0
    var time: Double = 0.0
    var speed: Double = 0.0
    var isPlaying: Boolean = false

    val hasReportedTime: Boolean get() = lastTimeChangeTime_ms > 0

    val expectedCurrentTime: Double
        get() {
            val diff =
                if (isPlaying && lastTimeChangeTime_ms > 0) ((System.currentTimeMillis() - lastTimeChangeTime_ms).toDouble() / 1000.0) else 0.0;
            return time + diff
        }

    init {
        eventHandler.onConnectionStateChanged.subscribe { newState ->
            when (newState) {
                is DeviceConnectionState.Connected -> {
                    usedRemoteAddress = ipAddrToInetAddress(newState.usedRemoteAddr)
                    localAddress = ipAddrToInetAddress(newState.localAddr)
                    receiverCapabilities = newState.capabilities
                    Logger.i(TAG, "Receiver capabilities: protocols=${newState.capabilities?.media?.protocols}, isSabrSupported=${isSabrSupported()}")
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
        eventHandler.onPlayChanged.subscribe {
            if (isPlaying != it && lastTimeChangeTime_ms > 0) lastTimeChangeTime_ms = System.currentTimeMillis()
            isPlaying = it
            onPlayChanged.emit(it)
        }
        eventHandler.onTimeChanged.subscribe {
            lastTimeChangeTime_ms = System.currentTimeMillis()
            time = it
            onTimeChanged.emit(it)
        }
        eventHandler.onDurationChanged.subscribe {
            duration = it
            onDurationChanged.emit(it)
        }
        eventHandler.onVolumeChanged.subscribe {
            volume = it
            onVolumeChanged.emit(it)
        }
        eventHandler.onSpeedChanged.subscribe {
            speed = it
            onSpeedChanged.emit(it)
        }
        eventHandler.onMediaItemEnd.subscribe { onMediaItemEnd.emit() }
    }

    fun ensureThreadStarted() {}

    companion object {
        private val TAG = "CastingDeviceExp"
    }
}
