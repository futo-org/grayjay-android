package com.futo.platformplayer.casting

import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.CastingDeviceInfo
import org.fcast.sender_sdk.Metadata
import java.net.InetAddress

abstract class CastingDevice {
    abstract val isReady: Boolean
    abstract val usedRemoteAddress: InetAddress?
    abstract val localAddress: InetAddress?
    abstract val name: String?
    abstract val onConnectionStateChanged: Event1<CastConnectionState>
    abstract val onPlayChanged: Event1<Boolean>
    abstract val onTimeChanged: Event1<Double>
    abstract val onDurationChanged: Event1<Double>
    abstract val onVolumeChanged: Event1<Double>
    abstract val onSpeedChanged: Event1<Double>
    abstract val onMediaItemEnd: Event0
    abstract var connectionState: CastConnectionState
    abstract val protocolType: CastProtocolType
    abstract var isPlaying: Boolean
    abstract val expectedCurrentTime: Double
    abstract var speed: Double
    abstract var time: Double
    abstract var duration: Double
    abstract var volume: Double
    abstract fun canSetVolume(): Boolean
    abstract fun canSetSpeed(): Boolean

    @Throws
    abstract fun resumePlayback()

    @Throws
    abstract fun pausePlayback()

    @Throws
    abstract fun stopPlayback()

    @Throws
    abstract fun seekTo(timeSeconds: Double)

    @Throws
    abstract fun changeVolume(timeSeconds: Double)

    @Throws
    abstract fun changeSpeed(speed: Double)

    @Throws
    abstract fun connect()

    @Throws
    abstract fun disconnect()
    abstract fun getDeviceInfo(): CastingDeviceInfo
    abstract fun getAddresses(): List<InetAddress>

    @Throws
    abstract fun loadVideo(
        streamType: String,
        contentType: String,
        contentId: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?,
        metadata: Metadata?
    )

    @Throws
    abstract fun loadContent(
        contentType: String,
        content: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?,
        metadata: Metadata?
    )

    abstract fun ensureThreadStarted()
}

