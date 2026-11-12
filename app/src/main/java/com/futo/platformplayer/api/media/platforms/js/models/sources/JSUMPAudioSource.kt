package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.sabr.SabrFormat

class JSUMPAudioSource : JSSource, IAudioSource {
    val parent: JSUMPSource
    val format: SabrFormat

    override val name: String
    override val bitrate: Int
    override val container: String
    override val codec: String
    override val language: String
    override val duration: Long?
    override val priority: Boolean = false
    override val original: Boolean

    constructor(parent: JSUMPSource, format: SabrFormat)
        : super(TYPE_UMP, parent.getUnderlyingPlugin()!!, parent.getUnderlyingObject()!!) {
        this.parent = parent
        this.format = format
        name = format.audioLabel
        bitrate = format.bitrate
        container = format.containerMimeType
        codec = format.codecs
        language = format.language ?: "Unknown"
        duration = parent.duration
        original = format.isOriginalAudio
    }
}
