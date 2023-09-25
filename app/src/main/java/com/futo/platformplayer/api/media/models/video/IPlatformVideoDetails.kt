package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.structures.IPager

/**
 * A detailed video model with data including video/audio sources
 * TODO:TBD if it should be a derived of IPlatformSearchVideo (to cover identical fields)
 */
interface IPlatformVideoDetails : IPlatformVideo, IPlatformContentDetails {
    val rating : IRating;

    val description : String;

    val video : IVideoSourceDescriptor;
    val preview : IVideoSourceDescriptor?;
    val live : IVideoSource?;
    val dash: IDashManifestSource?;
    val hls: IHLSManifestSource?;

    val subtitles: List<ISubtitleSource>;
}