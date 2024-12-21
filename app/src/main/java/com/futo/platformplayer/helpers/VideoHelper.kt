package com.futo.platformplayer.helpers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IWidevineSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSAudioUrlRangeSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlRangeSource
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.Language
import getHttpDataSourceFactory
import kotlin.math.abs

class VideoHelper {
    companion object {

        fun isDownloadable(detail: IPlatformVideoDetails): Boolean {
            if (detail.video.videoSources.any { isDownloadable(it) }) {
                return true
            }

            val descriptor = detail.video
            if (descriptor is VideoUnMuxedSourceDescriptor) {
                if (descriptor.audioSources.any { isDownloadable(it) }) {
                    return true
                }
            }

            return false
        }

        fun isDownloadable(source: IVideoSource) = (source is IVideoUrlSource || source is IHLSManifestSource || source is JSDashManifestRawSource) && source !is IWidevineSource
        fun isDownloadable(source: IAudioSource) = (source is IAudioUrlSource || source is IHLSManifestAudioSource || source is JSDashManifestRawAudioSource) && source !is IWidevineSource

        fun selectBestVideoSource(desc: IVideoSourceDescriptor, desiredPixelCount : Int, prefContainers : Array<String>) : IVideoSource? = selectBestVideoSource(desc.videoSources.toList(), desiredPixelCount, prefContainers);
        fun selectBestVideoSource(sources: Iterable<IVideoSource>, desiredPixelCount : Int, prefContainers : Array<String>) : IVideoSource? {
            val targetVideo = if(desiredPixelCount > 0) {
                sources.toList().minByOrNull { x -> abs(x.height * x.width - desiredPixelCount) };
            } else {
                sources.toList().lastOrNull();
            }

            val hasPriority = sources.any { it.priority };

            val targetPixelCount = if(targetVideo != null) targetVideo.width * targetVideo.height else desiredPixelCount;
            val altSources = if(hasPriority) {
                sources.filter { it.priority }.sortedBy { x -> abs(x.height * x.width - targetPixelCount) };
            } else {
                sources.filter { it.height == (targetVideo?.height ?: 0) };
            }

            var bestSource = altSources.firstOrNull();
            for (prefContainer in prefContainers) {
                val betterSource = altSources.firstOrNull { it.container == prefContainer };
                if(betterSource != null) {
                    bestSource = betterSource;
                    break;
                }
            }

            return bestSource;
        }


        fun selectBestAudioSource(desc: IVideoSourceDescriptor, prefContainers : Array<String>, prefLanguage: String? = null, targetBitrate: Long? = null) : IAudioSource? {
            if(!desc.isUnMuxed)
                return null;

            return selectBestAudioSource((desc as VideoUnMuxedSourceDescriptor).audioSources.toList(), prefContainers, prefLanguage, targetBitrate);
        }
        fun selectBestAudioSource(altSources : Iterable<IAudioSource>, prefContainers : Array<String>, preferredLanguage: String? = null, targetBitrate: Long? = null) : IAudioSource? {
            val languageToFilter = if(preferredLanguage != null && altSources.any { it.language == preferredLanguage }) {
                preferredLanguage
            } else {
                if(altSources.any { it.language == Language.ENGLISH })
                    Language.ENGLISH
                else
                    Language.UNKNOWN;
            }

            var usableSources = if(altSources.any { it.language == languageToFilter }) {
                altSources.filter { it.language == languageToFilter }.sortedBy { it.bitrate }.toList();
            } else {
                altSources.sortedBy { it.bitrate }
            }

            if(usableSources.any { it.priority }) {
                usableSources = usableSources.filter { it.priority };
            }


            var bestSource = if(targetBitrate != null) {
                usableSources.minByOrNull { abs(it.bitrate - targetBitrate) };
            } else {
                usableSources.lastOrNull();
            }

            for (prefContainer in prefContainers) {
                val betterSources = usableSources.filter { it.container == prefContainer };
                val betterSource = if(targetBitrate != null) {
                    betterSources.minByOrNull { abs(it.bitrate - targetBitrate) };
                } else {
                    betterSources.lastOrNull();
                }

                if(betterSource != null) {
                    bestSource = betterSource;
                    break;
                }
            }
            return bestSource;
        }

        @OptIn(UnstableApi::class)
        fun convertItagSourceToChunkedDashSource(videoSource: JSVideoUrlRangeSource) : Pair<MediaSource, String> {
            val urlToUse = videoSource.getVideoUrl();
            val manifestConfig = ProgressiveDashManifestCreator.fromVideoProgressiveStreamingUrl(urlToUse,
                videoSource.duration * 1000,
                videoSource.container,
                videoSource.itagId ?: 1,
                videoSource.codec,
                videoSource.bitrate,
                videoSource.width,
                videoSource.height,
                -1,
                videoSource.indexStart ?: 0,
                videoSource.indexEnd ?: 0,
                videoSource.initStart ?: 0,
                videoSource.initEnd ?: 0
            );

            val manifest = DashManifestParser().parse(Uri.parse(""), manifestConfig.byteInputStream());
            return Pair(DashMediaSource.Factory(ResolvingDataSource.Factory(videoSource.getHttpDataSourceFactory(), ResolvingDataSource.Resolver { dataSpec ->
                Logger.v("PLAYBACK", "Video REQ Range [" + dataSpec.position + "-" + (dataSpec.position + dataSpec.length) + "](" + dataSpec.length + ")", null);
                return@Resolver dataSpec;
            })).createMediaSource(manifest, MediaItem.Builder().setUri(Uri.parse(videoSource.getVideoUrl())).build()), manifestConfig);
        }

        fun getMediaMetadata(media: IPlatformVideoDetails): MediaMetadata {
            val builder = MediaMetadata.Builder()
                .setArtist(media.author.name)
                .setTitle(media.name)

            media.thumbnails.getHQThumbnail()?.let {
                builder.setArtworkUri(Uri.parse(it))
            }

            return builder.build()
        }

        @OptIn(UnstableApi::class)
        fun convertItagSourceToChunkedDashSource(audioSource: JSAudioUrlRangeSource) : MediaSource {
            val manifestConfig = ProgressiveDashManifestCreator.fromAudioProgressiveStreamingUrl(audioSource.getAudioUrl(),
                audioSource.duration?.times(1000) ?: 0,
                audioSource.container,
                audioSource.audioChannels,
                audioSource.itagId ?: 1,
                audioSource.codec,
                audioSource.bitrate,
                -1,
                audioSource.indexStart ?: 0,
                audioSource.indexEnd ?: 0,
                audioSource.initStart ?: 0,
                audioSource.initEnd ?: 0
            );

            val manifest = DashManifestParser().parse(Uri.parse(""), manifestConfig.byteInputStream());

            return DashMediaSource.Factory(ResolvingDataSource.Factory(audioSource.getHttpDataSourceFactory(), ResolvingDataSource.Resolver { dataSpec ->
                Logger.v("PLAYBACK", "Audio REQ Range [" + dataSpec.position + "-" + (dataSpec.position + dataSpec.length) + "](" + dataSpec.length + ")", null);
                return@Resolver dataSpec;
            })).createMediaSource(manifest, MediaItem.Builder().setUri(Uri.parse(audioSource.getAudioUrl())).build())
        }


        fun estimateSourceSize(source: IVideoSource?): Long {
            if(source == null) return 0;
            if(source is IVideoSource) {
                if(source.bitrate ?: 0 <= 0 || source.duration.toInt() == 0)
                    return 0;
                return (source.duration / 8) * source.bitrate!!;
            }
            else return 0;
        }
        fun estimateSourceSize(source: IAudioSource?): Long {
            if(source == null) return 0;
            if(source is IAudioSource) {
                if(source.bitrate <= 0 || source.duration?.toInt() ?: 0 == 0)
                    return 0;
                return (source.duration!! / 8) * source.bitrate;
            }
            else return 0;
        }
    }
}
