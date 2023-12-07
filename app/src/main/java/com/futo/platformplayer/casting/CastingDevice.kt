package com.futo.platformplayer.casting

import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.getNowDiffMiliseconds
import com.futo.platformplayer.models.CastingDeviceInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.InetAddress
import java.time.OffsetDateTime

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
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CastProtocolType", PrimitiveKind.STRING)

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

abstract class CastingDevice {
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
    var timeReceivedAt: OffsetDateTime = OffsetDateTime.now()
        private set;
    var time: Double = 0.0
        set(value) {
            val changed = value != field;
            field = value;
            if (changed) {
                timeReceivedAt = OffsetDateTime.now();
                onTimeChanged.emit(value);
            }
        };
    var volume: Double = 1.0
        set(value) {
            val changed = value != field;
            field = value;
            if (changed) {
                onVolumeChanged.emit(value);
            }
        };
    var speed: Double = 1.0
        set(value) {
            val changed = value != field;
            speed = value;
            if (changed) {
                onSpeedChanged.emit(value);
            }
        };
    val expectedCurrentTime: Double
        get() {
            val diff = timeReceivedAt.getNowDiffMiliseconds().toDouble() / 1000.0;
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
    var onVolumeChanged = Event1<Double>();
    var onSpeedChanged = Event1<Double>();

    abstract fun stopCasting();

    abstract fun seekVideo(timeSeconds: Double);
    abstract fun stopVideo();
    abstract fun pauseVideo();
    abstract fun resumeVideo();
    abstract fun loadVideo(streamType: String, contentType: String, contentId: String, resumePosition: Double, duration: Double, speed: Double?);
    abstract fun loadContent(contentType: String, content: String, resumePosition: Double, duration: Double, speed: Double?);
    open fun changeVolume(volume: Double) { throw NotImplementedError() }
    open fun changeSpeed(speed: Double) { throw NotImplementedError() }

    abstract fun start();
    abstract fun stop();

    abstract fun getDeviceInfo(): CastingDeviceInfo;

    abstract fun getAddresses(): List<InetAddress>;
}