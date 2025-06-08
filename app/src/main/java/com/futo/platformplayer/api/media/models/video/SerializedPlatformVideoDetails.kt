package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class SerializedPlatformVideoDetails(
    override val id: PlatformID,
    override val name: String,
    override val thumbnails: Thumbnails,
    override val author: PlatformAuthorLink,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val datetime: OffsetDateTime?,
    override val url: String,
    override val shareUrl: String,

    override val duration: Long,
    override val viewCount: Long,

    override val rating: IRating,
    override val description: String,

    override val video: ISerializedVideoSourceDescriptor,
    override val preview: ISerializedVideoSourceDescriptor?,

    override val subtitles: List<SubtitleRawSource> = listOf(),
    override val isShort: Boolean = false
) : IPlatformVideo, IPlatformVideoDetails {
    final override val contentType: ContentType get() = ContentType.MEDIA;

    override val isLive: Boolean get() = false;

    override val dash: IDashManifestSource? get() = null;
    override val hls: IHLSManifestSource? get() = null;
    override val live: IVideoSource? get() = null;

    fun toJson() : String {
        return Json.encodeToString(this);
    }
    fun fromJson(str : String) : SerializedPlatformVideoDetails {
        return Serializer.json.decodeFromString<SerializedPlatformVideoDetails>(str);
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? = null;
    override fun getPlaybackTracker(): IPlaybackTracker? = null;
    override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? = null;

    companion object {
        fun fromVideo(video : IPlatformVideoDetails, subtitleSources: List<SubtitleRawSource>) : SerializedPlatformVideoDetails {
            val descriptor = ISerializedVideoSourceDescriptor.fromDescriptor(video.video);
            return SerializedPlatformVideoDetails(
                video.id,
                video.name,
                video.thumbnails,
                video.author,
                video.datetime,
                video.url,
                video.shareUrl,
                video.duration,
                video.viewCount,
                video.rating,
                video.description,
                descriptor,
                video.preview?.let { descriptor },
                subtitleSources
            );
        }
    }
}