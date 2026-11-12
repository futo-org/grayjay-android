package com.futo.platformplayer.api.media.platforms.local.models

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalVideoFileSource
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class LocalVideoDetails: IPlatformVideoDetails {

    override val contentType: ContentType get() = ContentType.UNKNOWN;

    override val id: PlatformID;
    override val name: String;
    override val author: PlatformAuthorLink;

    override val datetime: OffsetDateTime?;

    override val url: String;
    override val shareUrl: String;
    override val rating: IRating = RatingLikes(0);
    override val description: String = "";

    override val video: IVideoSourceDescriptor;
    override val preview: IVideoSourceDescriptor? = null;
    override val live: IVideoSource? = null;
    override val dash: IDashManifestSource? = null;
    override val hls: IHLSManifestSource? = null;
    override val subtitles: List<ISubtitleSource> = listOf()

    override val thumbnails: Thumbnails;
    override val duration: Long;
    override val viewCount: Long = 0;
    override val isLive: Boolean = false;
    override val isShort: Boolean = false;

    override var playbackTime: Long = -1;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override var playbackDate: OffsetDateTime? = null;

    constructor(file: File) {
        id = PlatformID("Local", file.path, "LOCAL")
        name = file.name;
        author = PlatformAuthorLink.UNKNOWN;

        url = file.canonicalPath;
        shareUrl = "";

        duration = 0;
        thumbnails = Thumbnails(arrayOf());

        datetime = OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(file.lastModified()),
            ZoneId.systemDefault()
        );
        video = LocalVideoMuxedSourceDescriptor(LocalVideoFileSource(file));
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? {
        return null;
    }

    override fun getPlaybackTracker(): IPlaybackTracker? {
        return null;
    }

    override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? {
        return null;
    }
}