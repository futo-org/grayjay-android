package com.futo.platformplayer.api.media.models.video

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
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
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.LocalVideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.AudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.api.media.platforms.local.models.LocalVideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalAudioContentSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalVideoContentSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalVideoFileSource
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.others.Language
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.states.StateApp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class LocalVideoDetails(
    override val id: PlatformID,
    override val name: String,
    override val thumbnails: Thumbnails,
    override val author: PlatformAuthorLink,
    override val url: String,
    override val duration: Long,

    val mimeType: String? = null,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val datetime: OffsetDateTime?
) : IPlatformVideo, IPlatformVideoDetails {
    final override val contentType: ContentType get() = ContentType.MEDIA;

    override var playbackTime: Long = -1;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override var playbackDate: OffsetDateTime? = null;

    override val isLive: Boolean get() = false;

    override val dash: IDashManifestSource? get() = null;
    override val hls: IHLSManifestSource? get() = null;
    override val live: IVideoSource? get() = null;


    override val shareUrl: String = ""
    override val viewCount: Long = -1
    override val rating: IRating = RatingLikes(0)
    override val description: String = "";
    override val video: IVideoSourceDescriptor = (if(mimeType?.startsWith("audio/") ?: false)
        (LocalVideoUnMuxedSourceDescriptor(
            arrayOf(),
            arrayOf(LocalAudioContentSource(url, mimeType ?: "", name, duration))
        ))
        else (LocalVideoMuxedSourceDescriptor(
            LocalVideoContentSource(url, mimeType ?: "", name, duration)
        ))
    );
    override val preview: ISerializedVideoSourceDescriptor? = null;

    override val subtitles: List<SubtitleRawSource> = listOf()
    override val isShort: Boolean = false

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
        fun fromFile(name: String, filePath: String, mimeType: String? = null) : LocalVideoDetails {
            if(filePath.startsWith("content://"))
                return fromContent(filePath, mimeType);

            return LocalVideoDetails(PlatformID("FILE", filePath, null, 0, -1),
                name, Thumbnails(), PlatformAuthorLink.UNKNOWN, filePath, -1, mimeType, null);
        }
        fun fromContent(contentUrl: String, mimeType: String? = null) : LocalVideoDetails {
            var nameToUse = getFileNameFromContentUrl(contentUrl) ?: "File";

            return LocalVideoDetails(PlatformID("FILE", contentUrl, null, 0, -1),
                nameToUse, Thumbnails(), PlatformAuthorLink.UNKNOWN, contentUrl, -1, mimeType, null);
        }

        @SuppressLint("Range")
        private fun getFileNameFromContentUrl(url: String): String? {
            val cursor = StateApp.instance.context.contentResolver.query(url.toUri(), null, null, null, null);
            cursor?.moveToFirst();
            val fileName = cursor?.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            cursor?.close();
            return fileName;
        }
    }
}