package com.futo.platformplayer.casting

import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.CastingDeviceInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.fcast.sender_sdk.Metadata
import java.net.InetAddress

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

abstract class CastingDeviceLegacy {
    abstract val protocol: CastProtocolType;
    abstract val isReady: Boolean;
    abstract var usedRemoteAddress: InetAddress?;
    abstract var localAddress: InetAddress?;
    abstract val canSetVolume: Boolean;
    abstract val canSetSpeed: Boolean;

    var name: String? = null;
    var isPlaying: Boolean = false
        set(value) {
            val changed = value != field;
            field = value;
            if (changed) {
                onPlayChanged.emit(value);
            }
        };

    private var lastTimeChangeTime_ms: Long = 0
    var time: Double = 0.0
        private set

    protected fun setTime(value: Double, changeTime_ms: Long = System.currentTimeMillis()) {
        if (changeTime_ms > lastTimeChangeTime_ms && value != time) {
            time = value
            lastTimeChangeTime_ms = changeTime_ms
            onTimeChanged.emit(value)
        }
    }

    private var lastDurationChangeTime_ms: Long = 0
    var duration: Double = 0.0
        private set

    protected fun setDuration(value: Double, changeTime_ms: Long = System.currentTimeMillis()) {
        if (changeTime_ms > lastDurationChangeTime_ms && value != duration) {
            duration = value
            lastDurationChangeTime_ms = changeTime_ms
            onDurationChanged.emit(value)
        }
    }

    private var lastVolumeChangeTime_ms: Long = 0
    var volume: Double = 1.0
        private set

    protected fun setVolume(value: Double, changeTime_ms: Long = System.currentTimeMillis()) {
        if (changeTime_ms > lastVolumeChangeTime_ms && value != volume) {
            volume = value
            lastVolumeChangeTime_ms = changeTime_ms
            onVolumeChanged.emit(value)
        }
    }

    private var lastSpeedChangeTime_ms: Long = 0
    var speed: Double = 1.0
        private set

    protected fun setSpeed(value: Double, changeTime_ms: Long = System.currentTimeMillis()) {
        if (changeTime_ms > lastSpeedChangeTime_ms && value != speed) {
            speed = value
            lastSpeedChangeTime_ms = changeTime_ms
            onSpeedChanged.emit(value)
        }
    }

    val expectedCurrentTime: Double
        get() {
            val diff =
                if (isPlaying) ((System.currentTimeMillis() - lastTimeChangeTime_ms).toDouble() / 1000.0) else 0.0;
            return time + diff;
        };
    var connectionState: CastConnectionState = CastConnectionState.DISCONNECTED
        set(value) {
            val changed = value != field;
            field = value;

            if (changed) {
                onConnectionStateChanged.emit(value);
            }
        };

    var onConnectionStateChanged = Event1<CastConnectionState>();
    var onPlayChanged = Event1<Boolean>();
    var onTimeChanged = Event1<Double>();
    var onDurationChanged = Event1<Double>();
    var onVolumeChanged = Event1<Double>();
    var onSpeedChanged = Event1<Double>();

    abstract fun stopCasting();

    abstract fun seekVideo(timeSeconds: Double);
    abstract fun stopVideo();
    abstract fun pauseVideo();
    abstract fun resumeVideo();
    abstract fun loadVideo(
        streamType: String,
        contentType: String,
        contentId: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?
    );

    abstract fun loadContent(
        contentType: String,
        content: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?
    );

    open fun changeVolume(volume: Double) {
        throw NotImplementedError()
    }

    open fun changeSpeed(speed: Double) {
        throw NotImplementedError()
    }

    abstract fun start();
    abstract fun stop();

    abstract fun getDeviceInfo(): CastingDeviceInfo;

    abstract fun getAddresses(): List<InetAddress>;
}

class CastingDeviceLegacyWrapper(val inner: CastingDeviceLegacy) : CastingDevice() {
    override val isReady: Boolean get() = inner.isReady
    override val usedRemoteAddress: InetAddress? get() = inner.usedRemoteAddress
    override val localAddress: InetAddress? get() = inner.localAddress
    override val name: String? get() = inner.name
    override val onConnectionStateChanged: Event1<CastConnectionState> get() = inner.onConnectionStateChanged
    override val onPlayChanged: Event1<Boolean> get() = inner.onPlayChanged
    override val onTimeChanged: Event1<Double> get() = inner.onTimeChanged
    override val onDurationChanged: Event1<Double> get() = inner.onDurationChanged
    override val onVolumeChanged: Event1<Double> get() = inner.onVolumeChanged
    override val onSpeedChanged: Event1<Double> get() = inner.onSpeedChanged
    override val onMediaItemEnd: Event0 = Event0()
    override var connectionState: CastConnectionState
        get() = inner.connectionState
        set(_) = Unit
    override val protocolType: CastProtocolType get() = inner.protocol
    override var isPlaying: Boolean
        get() = inner.isPlaying
        set(_) = Unit
    override val expectedCurrentTime: Double
        get() = inner.expectedCurrentTime
    override var speed: Double
        get() = inner.speed
        set(_) = Unit
    override var time: Double
        get() = inner.time
        set(_) = Unit
    override var duration: Double
        get() = inner.duration
        set(_) = Unit
    override var volume: Double
        get() = inner.volume
        set(_) = Unit

    override fun canSetVolume(): Boolean = inner.canSetVolume
    override fun canSetSpeed(): Boolean = inner.canSetSpeed
    override fun resumePlayback() = inner.resumeVideo()
    override fun pausePlayback() = inner.pauseVideo()
    override fun stopPlayback() = inner.stopVideo()
    override fun seekTo(timeSeconds: Double) = inner.seekVideo(timeSeconds)
    override fun changeVolume(timeSeconds: Double) = inner.changeVolume(timeSeconds)
    override fun changeSpeed(speed: Double) = inner.changeSpeed(speed)
    override fun connect() = inner.start()
    override fun disconnect() = inner.stop()
    override fun getDeviceInfo(): CastingDeviceInfo = inner.getDeviceInfo()
    override fun getAddresses(): List<InetAddress> = inner.getAddresses()
    override fun loadVideo(
        streamType: String,
        contentType: String,
        contentId: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?,
        metadata: Metadata?
    ) = inner.loadVideo(streamType, contentType, contentId, resumePosition, duration, speed)

    override fun loadContent(
        contentType: String,
        content: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?,
        metadata: Metadata?
    ) = inner.loadContent(contentType, content, resumePosition, duration, speed)

    override fun ensureThreadStarted() = when (inner) {
        is FCastCastingDevice -> inner.ensureThreadStarted()
        is ChromecastCastingDevice -> inner.ensureThreadsStarted()
        else -> {}
    }
}