package com.futo.platformplayer.downloads

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.LocalVideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.LocalVideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.stores.v2.IStoreItem
import java.io.File
import java.time.OffsetDateTime

//TODO: Better name
@kotlinx.serialization.Serializable
class VideoLocal: IPlatformVideoDetails, IStoreItem {
    var videoSerialized: SerializedPlatformVideoDetails;

    var groupType: String? = null;
    var groupID: String? = null;

    var videoSource: ArrayList<LocalVideoSource> = arrayListOf();
    var audioSource: ArrayList<LocalAudioSource> = arrayListOf();
    var subtitlesSources: ArrayList<LocalSubtitleSource> = arrayListOf();

    override val contentType: ContentType get() = ContentType.MEDIA;
    override val id: PlatformID get() = videoSerialized.id;
    override val name: String get() = videoSerialized.name;
    override val description: String get() = videoSerialized.description;

    override val thumbnails: Thumbnails get() = videoSerialized.thumbnails;
    override val author: PlatformAuthorLink get() = videoSerialized.author;

    override val datetime: OffsetDateTime? get() = videoSerialized.datetime;

    override val url: String get() = videoSerialized.url;
    override val shareUrl: String get() = videoSerialized.shareUrl;

    @kotlinx.serialization.Transient
    override val video: IVideoSourceDescriptor get() = if(!audioSource.isEmpty())
        LocalVideoUnMuxedSourceDescriptor(this)
    else
        LocalVideoMuxedSourceDescriptor(this);
    override val preview: IVideoSourceDescriptor? get() = videoSerialized.preview;

    override val live: IVideoSource? get() = videoSerialized.live;
    override val dash: IDashManifestSource? get() = videoSerialized.dash;
    override val hls: IHLSManifestSource? get() = videoSerialized.hls;

    override val duration: Long get() = videoSerialized.duration;
    override val viewCount: Long get() = videoSerialized.viewCount;

    override val rating: IRating get() = videoSerialized.rating;

    override val isLive: Boolean get() = videoSerialized.isLive;

    //TODO: Offline subtitles
    override val subtitles: List<ISubtitleSource> = listOf();

    constructor(video: SerializedPlatformVideoDetails) {
        this.videoSerialized = video;
    }
    constructor(video: IPlatformVideoDetails, subtitleSources: List<SubtitleRawSource>) {
        this.videoSerialized = SerializedPlatformVideoDetails.fromVideo(video, subtitleSources);
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? = null;
    override fun getPlaybackTracker(): IPlaybackTracker? = null;

    fun toPlatformVideo() : IPlatformVideoDetails {
        throw NotImplementedError();
    }

    fun getSimilarVideo(targetPixelCount: Int): LocalVideoSource? {
        return videoSource.filter {
            val px = it.height * it.width;
            val diff = Math.abs(px - targetPixelCount);
            val max = Math.max(targetPixelCount, px);
            return@filter (diff.toFloat() / max) < 0.15f;
        }.firstOrNull();
    }
    fun getSimilarAudio(targetBitrate: Int): LocalAudioSource? {
        return audioSource.filter {
            val diff = Math.abs(it.bitrate - targetBitrate);
            val max = Math.max(it.bitrate, targetBitrate);
            return@filter (diff.toFloat() / max) < 0.15f;
        }.firstOrNull();
    }

    override fun onDelete() {
        for(srcFile in videoSource) {
            val file = File(srcFile.filePath);
            if (file.exists())
                file.delete();
        }
        for(srcFile in audioSource) {
            val file = File(srcFile.filePath);
            if (file.exists())
                file.delete();
        }
        for(srcFile in subtitlesSources) {
            val file = File(srcFile.filePath);
            if (file.exists())
                file.delete();
        }
    }
}