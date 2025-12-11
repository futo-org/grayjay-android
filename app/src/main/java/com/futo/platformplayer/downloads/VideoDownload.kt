package com.futo.platformplayer.downloads

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.AudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.api.media.platforms.js.models.JSVideo
import com.futo.platformplayer.api.media.platforms.js.models.sources.IJSDashManifestRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.exceptions.DownloadException
import com.futo.platformplayer.helpers.FileHelper.Companion.sanitizeFileName
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.Language
import com.futo.platformplayer.parsers.HLS
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.toHumanBitrate
import com.futo.platformplayer.toHumanBytesSpeed
import com.futo.polycentric.core.hexStringToByteArray
import hasAnySource
import isDownloadable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ThreadLocalRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resumeWithException
import kotlin.time.times

@kotlinx.serialization.Serializable
class VideoDownload {
    var state: State = State.QUEUED;

    var video: SerializedPlatformVideo? = null;
    var videoDetails: SerializedPlatformVideoDetails? = null;

    val videoEither: IPlatformVideo get() = videoDetails ?: video ?: throw IllegalStateException("Missing video?");
    val id: PlatformID get() = videoEither.id
    val name: String get() = videoEither.name;
    val thumbnail: String? get() = videoDetails?.thumbnails?.getHQThumbnail();

    var targetPixelCount: Long? = null;
    var targetBitrate: Long? = null;
    var targetVideoName: String? = null;
    var targetAudioName: String? = null;

    var videoSource: VideoUrlSource?;
    var audioSource: AudioUrlSource?;
    var overrideResultAudioSource: IAudioSource? = null;
    @Contextual
    @Transient
    val videoSourceToUse: IVideoSource? get () = if(requiresLiveVideoSource) videoSourceLive as IVideoSource? else videoSource as IVideoSource?;
    @Contextual
    @Transient
    val audioSourceToUse: IAudioSource? get () = if(requiresLiveAudioSource) audioSourceLive as IAudioSource? else audioSource as IAudioSource?;

    var requireVideoSource: Boolean = false;
    var requireAudioSource: Boolean = false;
    var requiredCheck: Boolean = false;

    @Contextual
    @Transient
    val isVideoDownloadReady: Boolean get() = !requireVideoSource ||
            ((requiresLiveVideoSource && isLiveVideoSourceValid) || (!requiresLiveVideoSource && videoSource != null));
    @Contextual
    @Transient
    val isAudioDownloadReady: Boolean get() = !requireAudioSource ||
            ((requiresLiveAudioSource && isLiveAudioSourceValid) || (!requiresLiveAudioSource && audioSource != null));


    var subtitleSource: SubtitleRawSource?;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    var prepareTime: OffsetDateTime? = null;

    var requiresLiveVideoSource: Boolean = false;
    @Contextual
    @kotlinx.serialization.Transient
    var videoSourceLive: JSSource? = null;
    val isLiveVideoSourceValid get() = videoSourceLive?.getUnderlyingObject()?.isClosed?.let { !it } ?: false;

    var requiresLiveAudioSource: Boolean = false;
    @Contextual
    @kotlinx.serialization.Transient
    var audioSourceLive: JSSource? = null;
    val isLiveAudioSourceValid get() = audioSourceLive?.getUnderlyingObject()?.isClosed?.let { !it } ?: false;

    var hasVideoRequestExecutor: Boolean = false;
    var hasAudioRequestExecutor: Boolean = false;
    var hasVideoRequestModifier: Boolean = false;
    var hasAudioRequestModifier: Boolean = false;

    var progress: Double = 0.0;
    var isCancelled = false;

    var downloadSpeedVideo: Long = 0;
    var downloadSpeedAudio: Long = 0;
    val downloadSpeed: Long get() = downloadSpeedVideo + downloadSpeedAudio;

    var error: String? = null;

    var videoFilePath: String? = null;
    var videoFileNameBase: String? = null;
    var videoFileNameExt: String? = null;
    val videoFileName: String? get() = if(videoFileNameBase.isNullOrEmpty()) null else videoFileNameBase + (if(!videoFileNameExt.isNullOrEmpty()) "." + videoFileNameExt else "");
    var videoOverrideContainer: String? = null;
    var videoFileSize: Long? = null;

    var audioFilePath: String? = null;
    var audioFileNameBase: String? = null;
    var audioFileNameExt: String? = null;
    val audioFileName: String? get() = if(audioFileNameBase.isNullOrEmpty()) null else audioFileNameBase + (if(!audioFileNameExt.isNullOrEmpty()) "." + audioFileNameExt else "");
    var audioOverrideContainer: String? = null;
    var audioFileSize: Long? = null;

    var subtitleFilePath: String? = null;
    var subtitleFileName: String? = null;

    var groupType: String? = null;
    var groupID: String? = null;

    @kotlinx.serialization.Transient
    val onStateChanged = Event1<State>();
    @kotlinx.serialization.Transient
    val onProgressChanged = Event1<Double>();


    fun changeState(newState: State) {
        state = newState;
        onStateChanged.emit(newState);
    }

    constructor(video: IPlatformVideo, targetPixelCount: Long? = null, targetBitrate: Long? = null, optionalSources: Boolean = false) {
        this.video = SerializedPlatformVideo.fromVideo(video);
        this.videoSource = null;
        this.audioSource = null;
        this.subtitleSource = null;
        this.targetPixelCount = targetPixelCount;
        this.targetBitrate = targetBitrate;
        this.hasVideoRequestExecutor = video is JSSource && video.hasRequestExecutor;
        this.requiresLiveVideoSource = false;
        this.requiresLiveAudioSource = false;
        this.targetVideoName = videoSource?.name;
        this.requireVideoSource = targetPixelCount != null;
        this.requireAudioSource = targetBitrate != null; //TODO: May not be a valid check.. can only be determined after live fetch?
        this.requiredCheck = optionalSources;
    }
    constructor(video: IPlatformVideoDetails, videoSource: IVideoSource?, audioSource: IAudioSource?, subtitleSource: SubtitleRawSource?) {
        this.video = SerializedPlatformVideo.fromVideo(video);
        this.videoDetails = SerializedPlatformVideoDetails.fromVideo(video, if (subtitleSource != null) listOf(subtitleSource) else listOf());
        this.videoSource = if(videoSource is IVideoUrlSource) VideoUrlSource.fromUrlSource(videoSource) else null;
        this.audioSource = if(audioSource is IAudioUrlSource) AudioUrlSource.fromUrlSource(audioSource) else null;
        this.videoSourceLive = if(videoSource is JSSource) videoSource else null;
        this.audioSourceLive = if(audioSource is JSSource) audioSource else null;
        this.subtitleSource = subtitleSource;
        this.prepareTime = OffsetDateTime.now();
        this.hasVideoRequestExecutor = videoSource is JSSource && videoSource.hasRequestExecutor;
        this.hasAudioRequestExecutor = audioSource is JSSource && audioSource.hasRequestExecutor;
        this.hasVideoRequestModifier = videoSource is JSSource && videoSource.hasRequestModifier;
        this.hasAudioRequestModifier = audioSource is JSSource && audioSource.hasRequestModifier;
        this.requiresLiveVideoSource = this.hasVideoRequestModifier || this.hasVideoRequestExecutor || (videoSource is JSDashManifestRawSource && videoSource.hasGenerate);
        this.requiresLiveAudioSource = this.hasAudioRequestModifier || this.hasAudioRequestExecutor || (audioSource is JSDashManifestRawAudioSource && audioSource.hasGenerate);
        this.targetVideoName = videoSource?.name;
        this.targetAudioName = audioSource?.name;
        this.targetPixelCount = if(videoSource != null) (videoSource.width * videoSource.height).toLong() else null;
        this.targetBitrate = if(audioSource != null) audioSource.bitrate.toLong() else null;
        this.requireVideoSource = videoSource != null;
        this.requireAudioSource = audioSource != null;
    }

    fun withGroup(groupType: String, groupID: String): VideoDownload {
        this.groupType = groupType;
        this.groupID = groupID;
        return this;
    }

    fun getDownloadInfo() : String {
        val videoInfo = if(videoSource != null)
            "${videoSource!!.width}x${videoSource!!.height} (${videoSource!!.container})"
        else if(targetPixelCount != null && targetPixelCount!! > 0) {
            val guessWidth = ((4 * Math.sqrt(targetPixelCount!!.toDouble())) / 3).toInt();
            val guessHeight = ((3 * Math.sqrt(targetPixelCount!!.toDouble())) / 4).toInt();
            "${guessWidth}x${guessHeight}"
        }
        else null;
        val audioInfo = if(audioSource != null)
            audioSource!!.bitrate.toHumanBitrate();
        else if(targetBitrate != null && targetBitrate!! > 0)
            targetBitrate!!.toHumanBitrate();
        else null;

        val items = arrayOf(videoInfo, audioInfo).filter { it != null };

        return items.joinToString(" • ");
    }

    suspend fun prepare(client: ManagedHttpClient) {
        Logger.i(TAG, "VideoDownload Prepare [${name}]");

        //If live sources are required, ensure a live object is present
        if(requiresLiveVideoSource && !isLiveVideoSourceValid) {
            videoDetails = null;
            videoSource = null;
            videoSourceLive = null;
            videoOverrideContainer = null;
        }
        if(requiresLiveAudioSource && !isLiveAudioSourceValid) {
            videoDetails = null;
            audioSource = null;
            videoSourceLive = null;
            audioOverrideContainer = null;
        }
        if(video == null && videoDetails == null)
            throw IllegalStateException("Missing information for download to complete");
        if(targetPixelCount == null && targetBitrate == null && videoSource == null && audioSource == null && targetVideoName == null && targetAudioName == null)
            throw IllegalStateException("No sources or query values set");

        //Fetch full video object and determine source
        if(video != null && videoDetails == null) {
            val original = StatePlatform.instance.getContentDetails(video!!.url).await();
            if(original !is IPlatformVideoDetails)
                throw IllegalStateException("Original content is not media?");

            if(requiredCheck) {
                if(original.video is VideoUnMuxedSourceDescriptor) {
                    if(requireVideoSource) {
                        if((original.video as VideoUnMuxedSourceDescriptor).audioSources.any() && !original.video.videoSources.any()) {
                            requireVideoSource = false;
                            targetPixelCount = null;
                        }
                    }
                    if(requireAudioSource) {
                        if(!(original.video as VideoUnMuxedSourceDescriptor).audioSources.any() && original.video.videoSources.any()) {
                            requireAudioSource = false;
                            targetBitrate = null;
                        }
                    }
                }
                else {
                    if(requireAudioSource) {
                        requireAudioSource = false;
                        targetBitrate = null;
                    }
                }
                requiredCheck = false;
            }

            if(original.video.hasAnySource() && !original.isDownloadable()) {
                Logger.i(TAG, "Attempted to download unsupported video [${original.name}]:${original.url}");
                throw DownloadException("Unsupported video for downloading", false);
            }

            videoDetails = SerializedPlatformVideoDetails.fromVideo(original, if (subtitleSource != null) listOf(subtitleSource!!) else listOf());
            if(videoSource == null && targetPixelCount != null) {
                val videoSources = arrayListOf<IVideoSource>()
                for (source in original.video.videoSources) {
                    if (source is IHLSManifestSource) {
                        try {
                            val playlistResponse = client.get(source.url)
                            if (playlistResponse.isOk) {
                                val resolvedPlaylistUrl = playlistResponse.url
                                val playlistContent = playlistResponse.body?.string()
                                if (playlistContent != null) {
                                    videoSources.addAll(HLS.parseAndGetVideoSources(source, playlistContent, resolvedPlaylistUrl))
                                }
                            }
                        } catch (e: Throwable) {
                            Log.i(TAG, "Failed to get HLS video sources", e)
                        }
                    } else {
                        videoSources.add(source)
                    }
                }
                var vsource: IVideoSource? = null;

                if(targetVideoName != null)
                    vsource = videoSources.find { x -> x.isDownloadable() && x.name == targetVideoName };
                if(vsource == null && targetPixelCount == null)
                    throw IllegalStateException("Could not find comparable downloadable video stream (No target pixel count)");
                if(vsource == null)
                    vsource = VideoHelper.selectBestVideoSource(videoSources, targetPixelCount!!.toInt(), arrayOf())
                //    ?: throw IllegalStateException("Could not find a valid video source for video");
                if(vsource is JSSource) {
                    this.hasVideoRequestExecutor = this.hasVideoRequestExecutor || vsource.hasRequestExecutor;
                    this.requiresLiveVideoSource = this.hasVideoRequestExecutor || (vsource is JSDashManifestRawSource && vsource.hasGenerate);
                }

                if(vsource == null) {
                    videoSource = null;
                    if(original.video.videoSources.size == 0)
                        requireVideoSource = false;
                }
                else if(vsource is IVideoUrlSource)
                    videoSource = VideoUrlSource.fromUrlSource(vsource)
                else if(vsource is JSSource && requiresLiveVideoSource)
                    videoSourceLive = vsource;
                else
                    throw DownloadException("Video source is not supported for downloading (yet) [" + vsource?.javaClass?.name + "]", false);
            }

            if(audioSource == null && targetBitrate != null) {
                var audioSources = mutableListOf<IAudioSource>()
                val video = original.video
                if (video is VideoUnMuxedSourceDescriptor) {
                    for (source in video.audioSources) {
                        if (source is IHLSManifestAudioSource) {
                            try {
                                val playlistResponse = client.get(source.url)
                                if (playlistResponse.isOk) {
                                    val resolvedPlaylistUrl = playlistResponse.url
                                    val playlistContent = playlistResponse.body?.string()
                                    if (playlistContent != null) {
                                        audioSources.addAll(HLS.parseAndGetAudioSources(source, playlistContent, resolvedPlaylistUrl))
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.i(TAG, "Failed to get HLS audio sources", e)
                            }
                        } else {
                            audioSources.add(source)
                        }
                    }
                }

                var asource: IAudioSource? = null;
                if(targetAudioName != null) {
                    val filteredAudioSources = audioSources.filter { x -> x.isDownloadable() && x.name == targetAudioName }.toTypedArray();
                    if(filteredAudioSources.size == 1)
                        asource = filteredAudioSources.first();
                    else if(filteredAudioSources.size > 1)
                        audioSources = filteredAudioSources.toMutableList();
                }
                if(asource == null && targetBitrate == null)
                    throw IllegalStateException("Could not find comparable downloadable video stream (No target bitrate)");
                if(asource == null)
                    asource = VideoHelper.selectBestAudioSource(audioSources, arrayOf(), null, targetBitrate)
                        ?: if(videoSource != null ) null
                        else throw DownloadException("Could not find a valid video or audio source for download")

                if(asource is JSSource) {
                    this.hasAudioRequestExecutor = this.hasAudioRequestExecutor || asource.hasRequestExecutor;
                    this.requiresLiveAudioSource = this.hasAudioRequestExecutor || (asource is JSDashManifestRawSource && asource.hasGenerate);
                }

                if(asource == null) {
                    audioSource = null;
                    if(!original.video.isUnMuxed || original.video.videoSources.size == 0)
                        requireVideoSource = false;
                }
                else if(asource is IAudioUrlSource)
                    audioSource = AudioUrlSource.fromUrlSource(asource)
                else if(asource is JSSource && requiresLiveAudioSource)
                    audioSourceLive = asource;
                else
                    throw DownloadException("Audio source is not supported for downloading (yet) [" + asource?.javaClass?.name + "]", false);
            }

            if(!isVideoDownloadReady)
                throw DownloadException("No valid sources found for video");
            if(!isAudioDownloadReady)
                throw DownloadException("No valid sources found for audio");
        }
    }

    suspend fun download(context: Context, client: ManagedHttpClient, onProgress: ((Double) -> Unit)? = null) = coroutineScope {
        Logger.i(TAG, "VideoDownload Download [${name}]");
        if(videoDetails == null || (videoSourceToUse == null && audioSourceToUse == null))
            throw IllegalStateException("Missing information for download to complete");
        val downloadDir = StateDownloads.instance.getDownloadsDirectory();

        if(videoDetails!!.id.value == null)
            throw IllegalStateException("Video has no id");

        if(isCancelled) throw CancellationException("Download got cancelled");

        val actualVideoSource = if(requiresLiveVideoSource && videoSourceLive is IVideoSource)
            videoSourceLive as IVideoSource?;
        else videoSource;
        val actualAudioSource = if(requiresLiveAudioSource && audioSourceLive is IAudioSource)
            audioSourceLive as IAudioSource?;
        else audioSource;

        if(actualVideoSource != null) {
            videoFileNameBase = "${videoDetails!!.id.value!!} [${actualVideoSource!!.width}x${actualVideoSource!!.height}]".sanitizeFileName();
            videoFileNameExt = videoContainerToExtension(actualVideoSource!!.container);
            videoFilePath = File(downloadDir, videoFileName!!).absolutePath;
            if(actualVideoSource is JSDashManifestRawSource && actualAudioSource == null) {
                audioFileNameBase = "${videoDetails!!.id.value!!}-[unknown]".sanitizeFileName();
                audioFileNameExt = videoAudioContainerToExtension(actualVideoSource!!.container);
                audioFilePath = File(downloadDir, audioFileName!!).absolutePath;
            }
        }
        if(actualAudioSource != null) {
            audioFileNameBase = "${videoDetails!!.id.value!!} [${actualAudioSource!!.language}-${actualAudioSource!!.bitrate}]".sanitizeFileName();
            audioFileNameExt = audioContainerToExtension(actualAudioSource!!.container);
            audioFilePath = File(downloadDir, audioFileName!!).absolutePath;
        }
        if(subtitleSource != null) {
            subtitleFileName = "${videoDetails!!.id.value!!} [${subtitleSource!!.name}].${subtitleContainerToExtension(subtitleSource!!.format)}".sanitizeFileName();
            subtitleFilePath = File(downloadDir, subtitleFileName!!).absolutePath;
        }
        val progressLock = Object();
        val sourcesToDownload = mutableListOf<Deferred<Unit>>();

        var lastVideoLength: Long = 0;
        var lastVideoRead: Long = 0;
        var lastAudioLength: Long = 0;
        var lastAudioRead: Long = 0;

        if(actualVideoSource != null) {
            sourcesToDownload.add(async {
                Logger.i(TAG, "Started downloading video");

                var lastEmit = 0L;
                val progressCallback = { length: Long, totalRead: Long, speed: Long ->
                    synchronized(progressLock) {
                        lastVideoLength = length;
                        lastVideoRead = totalRead;
                        downloadSpeedVideo = speed;
                        if(videoFileSize == null)
                            videoFileSize = lastVideoLength;

                        val totalLength = lastVideoLength + lastAudioLength;
                        val total = lastVideoRead + lastAudioRead;
                        if(totalLength > 0) {
                            val percentage = (total / totalLength.toDouble());
                            progress = percentage;

                            val now = System.currentTimeMillis();
                            if(now - lastEmit > 200) {
                                lastEmit = System.currentTimeMillis();
                                onProgress?.invoke(percentage);
                                onProgressChanged.emit(percentage);
                            }
                        }
                    }
                }

                if(actualVideoSource is IVideoUrlSource)
                    videoFileSize = when (videoSource!!.container) {
                        "application/vnd.apple.mpegurl" -> downloadHlsSource(context, "Video", client, if (actualVideoSource is JSSource) actualVideoSource else null, videoSource!!.getVideoUrl(), File(downloadDir, videoFileName!!), progressCallback)
                        else -> downloadFileSource("Video", client, if (actualVideoSource is JSSource) actualVideoSource else null, videoSource!!.getVideoUrl(), File(downloadDir, videoFileName!!), progressCallback)
                    }
                else if(actualVideoSource is JSDashManifestRawSource) {
                    if(actualAudioSource == null)
                        videoFileSize = downloadDashFileSource("Video", client, actualVideoSource, File(downloadDir, videoFileName!!), progressCallback, 3,
                            File(downloadDir, audioFileName!!));
                    else
                        videoFileSize = downloadDashFileSource("Video", client, actualVideoSource, File(downloadDir, videoFileName!!), progressCallback, 1);
                }
                else throw NotImplementedError("NotImplemented video download: " + actualVideoSource.javaClass.name);
            });
        }
        if(actualAudioSource != null) {
            sourcesToDownload.add(async {
                Logger.i(TAG, "Started downloading audio");

                var lastEmit = 0L;
                val progressCallback = { length: Long, totalRead: Long, speed: Long ->
                    synchronized(progressLock) {
                        lastAudioLength = length;
                        lastAudioRead = totalRead;
                        downloadSpeedAudio = speed;
                        if(audioFileSize == null)
                            audioFileSize = lastAudioLength;

                        val totalLength = lastVideoLength + lastAudioLength;
                        val total = lastVideoRead + lastAudioRead;
                        if(totalLength > 0) {
                            val percentage = (total / totalLength.toDouble());
                            progress = percentage;

                            val now = System.currentTimeMillis();
                            if(now - lastEmit > 200) {
                                lastEmit = System.currentTimeMillis();
                                onProgress?.invoke(percentage);
                                onProgressChanged.emit(percentage);
                            }
                        }
                    }
                }

                if(actualAudioSource is IAudioUrlSource)
                    audioFileSize = when (audioSource!!.container) {
                        "application/vnd.apple.mpegurl" -> downloadHlsSource(context, "Audio", client, if (actualAudioSource is JSSource) actualAudioSource else null, audioSource!!.getAudioUrl(), File(downloadDir, audioFileName!!), progressCallback)
                        else -> downloadFileSource("Audio", client, if (actualAudioSource is JSSource) actualAudioSource else null, audioSource!!.getAudioUrl(), File(downloadDir, audioFileName!!), progressCallback)
                    }
                else if(actualAudioSource is JSDashManifestRawAudioSource) {
                    audioFileSize = downloadDashFileSource("Audio", client, actualAudioSource, File(downloadDir, audioFileName!!), progressCallback, 2);
                }
                else throw NotImplementedError("NotImplemented audio download: " + actualAudioSource.javaClass.name);
            });
        }
        if (subtitleSource != null) {
            sourcesToDownload.add(async {
                File(downloadDir, subtitleFileName!!).writeText(subtitleSource!!._subtitles)
            });
        }

        var wasSuccesful = false;
        try {
            awaitAll(*sourcesToDownload.toTypedArray());
            wasSuccesful = true;
        }
        catch(runtimeEx: RuntimeException) {
            if(runtimeEx.cause != null)
                throw runtimeEx.cause!!;
            else
                throw runtimeEx;
        }
        catch(ex: Throwable) {
            throw ex;
        }
        finally {
            if(!wasSuccesful) {
                try {
                    if(videoFilePath != null) {
                        val remainingVideo = File(videoFilePath!!);
                        if (remainingVideo.exists()) {
                            Logger.i(TAG, "Deleting remaining video file");
                            remainingVideo.delete();
                        }
                    }
                    if(audioFilePath != null) {
                        val remainingAudio = File(audioFilePath!!);
                        if (remainingAudio.exists()) {
                            Logger.i(TAG, "Deleting remaining audio file");
                            remainingAudio.delete();
                        }
                    }
                }
                catch(iex: Throwable) {
                    Logger.e(TAG, "Failed to delete files after failure:\n${iex.message}", iex);
                }
            }
        }
    }

    private fun decryptSegment(encryptedSegment: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(encryptedSegment)
    }

    private suspend fun combineSegments(context: Context, segmentFiles: List<File>, targetFile: File) = withContext(Dispatchers.IO) {
        require(segmentFiles.isNotEmpty()) { "segmentFiles must not be empty" }

        suspendCancellableCoroutine { continuation ->
            val concatInput = buildString {
                append("concat:")
                append(
                    segmentFiles.joinToString("|") { file ->
                        file.absolutePath
                    }
                )
            }

            val cmd = "-i \"$concatInput\" -c copy \"${targetFile.absolutePath}\""

            val statisticsCallback = StatisticsCallback { _ ->
                //No callback
            }

            val executorService = Executors.newSingleThreadExecutor()

            val session = FFmpegKit.executeAsync(
                cmd,
                { completedSession ->
                    executorService.shutdown()

                    if (ReturnCode.isSuccess(completedSession.returnCode)) {
                        continuation.resumeWith(Result.success(Unit))
                    } else {
                        val errorMessage = if (ReturnCode.isCancel(completedSession.returnCode)) {
                            "Command cancelled"
                        } else {
                            "Command failed with state '${completedSession.state}' " +
                                    "and return code ${completedSession.returnCode}, " +
                                    "stack trace ${completedSession.failStackTrace}"
                        }
                        continuation.resumeWithException(RuntimeException(errorMessage))
                    }
                },
                { log ->
                    Logger.v(TAG, log.message)
                },
                statisticsCallback,
                executorService
            )

            continuation.invokeOnCancellation {
                session.cancel()
                executorService.shutdownNow()
            }
        }
    }

    private suspend fun downloadHlsSource(context: Context, name: String, client: ManagedHttpClient, source: JSSource?, hlsUrl: String, targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if (targetFile.exists())
            targetFile.delete()

        var downloadedTotalLength = 0L
        val modifier = if (source is JSSource && source.hasRequestModifier)
            source.getRequestModifier()
        else
            null

        fun downloadBytes(url: String, rangeStart: Long? = null, rangeLength: Long? = null): ByteArray {
            val headers = mutableMapOf<String, String>()

            if (rangeStart != null) {
                if (rangeLength != null && rangeLength > 0) {
                    val end = rangeStart + rangeLength - 1
                    headers["Range"] = "bytes=$rangeStart-$end"
                } else {
                    headers["Range"] = "bytes=$rangeStart-"
                }
            }

            val modified = modifier?.modifyRequest(url, headers)
            val finalUrl = modified?.url ?: url
            val finalHeaders = modified?.headers?.toMutableMap() ?: headers

            val resp = client.get(finalUrl, finalHeaders)
            if (!resp.isOk) {
                resp.body?.close()
                throw IllegalStateException("Failed to download HLS resource ($finalUrl): HTTP ${resp.code}")
            }

            val body = resp.body ?: throw IllegalStateException("Failed to download HLS resource ($finalUrl): Empty body")
            val bytes = body.bytes()
            body.close()
            return bytes
        }

        fun buildSequenceIv(sequenceNumber: Long): ByteArray {
            return ByteBuffer.allocate(16)
                .putLong(0L)
                .putLong(sequenceNumber)
                .array()
        }

        val segmentFiles = arrayListOf<File>()
        try {
            val playlistHeaders = mutableMapOf<String, String>()
            val modifiedPlaylistReq = modifier?.modifyRequest(hlsUrl, playlistHeaders)
            val playlistResp = client.get(
                modifiedPlaylistReq?.url ?: hlsUrl,
                modifiedPlaylistReq?.headers?.toMutableMap() ?: playlistHeaders
            )

            check(playlistResp.isOk) { "Failed to get variant playlist: ${playlistResp.code}" }

            val vpContent = playlistResp.body?.string()
                ?: throw IllegalStateException("Variant playlist content is empty")

            val variantPlaylist = HLS.parseVariantPlaylist(vpContent, hlsUrl)
            val hlsDec = variantPlaylist.decryptionInfo
            val useDecryption = hlsDec != null && !hlsDec.method.equals("NONE", ignoreCase = true)
            var keyBytes: ByteArray? = null
            var staticIvBytes: ByteArray? = null

            if (useDecryption) {
                if (!hlsDec.method.equals("AES-128", ignoreCase = true)) {
                    throw UnsupportedOperationException("HLS decryption method '${hlsDec.method}' is not supported.")
                }

                val keyUrl = hlsDec.keyUrl ?: throw IllegalStateException("Encrypted HLS playlist without key URI is not supported.")

                keyBytes = downloadBytes(keyUrl)
                if (!hlsDec.iv.isNullOrEmpty()) {
                    staticIvBytes = hlsDec.iv.hexStringToByteArray()
                }
            }

            val mediaSequence = variantPlaylist.mediaSequence ?: 0L
            val rangeOffsets = mutableMapOf<String, Long>()

            if (!variantPlaylist.mapUrl.isNullOrEmpty()) {
                if (isCancelled) throw CancellationException("Cancelled")

                Logger.i(TAG, "Downloading HLS initialization map")

                var mapRangeStart: Long? = null
                var mapRangeLength: Long? = null

                if (variantPlaylist.mapBytesLength > 0) {
                    mapRangeLength = variantPlaylist.mapBytesLength

                    val mapUrl = variantPlaylist.mapUrl
                    if (variantPlaylist.mapBytesStart >= 0) {
                        mapRangeStart = variantPlaylist.mapBytesStart
                        rangeOffsets[mapUrl] =
                            variantPlaylist.mapBytesStart + variantPlaylist.mapBytesLength
                    } else {
                        val offset = rangeOffsets[mapUrl] ?: 0L
                        mapRangeStart = offset
                        rangeOffsets[mapUrl] = offset + variantPlaylist.mapBytesLength
                    }
                }

                var mapBytes = downloadBytes(variantPlaylist.mapUrl!!, mapRangeStart, mapRangeLength)

                if (useDecryption) {
                    val kb = keyBytes ?: throw IllegalStateException("Decryption key bytes are missing.")
                    val iv = staticIvBytes
                        ?: throw UnsupportedOperationException("Encrypted EXT-X-MAP without explicit IV is not supported.")
                    mapBytes = decryptSegment(mapBytes, kb, iv)
                }

                if (mapBytes.size.toLong() > Int.MAX_VALUE) {
                    throw IllegalStateException("HLS MAP segment too large to handle.")
                }

                val segmentFile = File(context.cacheDir, "segment-${UUID.randomUUID()}")
                val outStr = segmentFile.outputStream()
                try {
                    segmentFiles.add(segmentFile)
                    outStr.write(mapBytes)
                    outStr.flush()
                } finally {
                    outStr.close()
                }
                downloadedTotalLength += mapBytes.size
            }

            val totalSegments = variantPlaylist.segments.size
            var mediaSegmentIndex = 0

            var bytesSinceLastSpeedUpdate = 0L
            var lastSpeedUpdateTime = System.currentTimeMillis()
            var lastSpeed = 0L

            variantPlaylist.segments.forEachIndexed { index, segment ->
                if (segment !is HLS.MediaSegment) return@forEachIndexed
                if (isCancelled) throw CancellationException("Cancelled")

                Logger.i(TAG, "Download '$name' segment $index sequential")

                var rangeStart: Long? = null
                var rangeLength: Long? = null

                if (segment.bytesLength > 0) {
                    rangeLength = segment.bytesLength

                    val urlKey = segment.uri
                    if (segment.bytesStart >= 0) {
                        rangeStart = segment.bytesStart
                        rangeOffsets[urlKey] = segment.bytesStart + segment.bytesLength
                    } else {
                        val offset = rangeOffsets[urlKey] ?: 0L
                        rangeStart = offset
                        rangeOffsets[urlKey] = offset + segment.bytesLength
                    }
                }

                var segmentBytes = downloadBytes(segment.uri, rangeStart, rangeLength)

                if (useDecryption) {
                    val kb = keyBytes ?: throw IllegalStateException("Decryption key bytes are missing.")
                    val ivBytes = if (staticIvBytes != null) {
                        staticIvBytes
                    } else {
                        val sequenceNumber = mediaSequence + mediaSegmentIndex
                        buildSequenceIv(sequenceNumber)
                    }

                    segmentBytes = decryptSegment(segmentBytes, kb, ivBytes)
                }

                val segmentLength = segmentBytes.size.toLong()
                if (segmentLength > Int.MAX_VALUE) {
                    throw IllegalStateException("HLS media segment too large to handle.")
                }

                val avgLen = if (index == 0) {
                    segmentLength
                } else {
                    if (index > 0) downloadedTotalLength / index else segmentLength
                }
                val expectedTotal = avgLen * (totalSegments - 1) + segmentLength

                val segmentFile = File(context.cacheDir, "segment-${UUID.randomUUID()}")
                val outStr = segmentFile.outputStream()
                try {
                    segmentFiles.add(segmentFile)
                    outStr.write(segmentBytes)
                } finally {
                    outStr.close()
                }
                downloadedTotalLength += segmentLength

                bytesSinceLastSpeedUpdate += segmentLength
                val now = System.currentTimeMillis()
                val elapsed = now - lastSpeedUpdateTime
                if (elapsed >= 500 && bytesSinceLastSpeedUpdate > 0) {
                    lastSpeed = (bytesSinceLastSpeedUpdate * 1000L / elapsed)
                    bytesSinceLastSpeedUpdate = 0
                    lastSpeedUpdateTime = now
                }

                onProgress(expectedTotal, downloadedTotalLength, lastSpeed)
                mediaSegmentIndex++
            }

            combineSegments(context, segmentFiles, targetFile)
            Logger.i(TAG, "Finished HLS Source for $name")
        } catch (ioex: IOException) {
            if (targetFile.exists())
                targetFile.delete()
            if (ioex.message?.contains("ENOSPC") == true)
                throw Exception("Not enough space on device", ioex)
            else
                throw ioex
        } catch (ex: Throwable) {
            if (targetFile.exists())
                targetFile.delete()
            throw ex
        }
        finally {
            for (segmentFile in segmentFiles) {
                segmentFile.delete()
            }
        }

        return downloadedTotalLength
    }

    private fun downloadDashFileSource(name: String, client: ManagedHttpClient, source: IJSDashManifestRawSource, targetFile: File, onProgress: (Long, Long, Long) -> Unit, downloadType: Int = 0, targetFileAudio: File? = null): Long {
        if(targetFile.exists())
            targetFile.delete();
        if(targetFileAudio?.exists() ?: false)
            targetFileAudio.delete();

        targetFile.createNewFile();
        targetFileAudio?.createNewFile();

        val sourceLength: Long?;
        val sourceLengthAudio: Long?;
        val fileStream = FileOutputStream(targetFile);
        val fileStream2 = if(targetFileAudio != null) FileOutputStream(targetFileAudio) else null;

        var executor: JSRequestExecutor? = null;
        try{
            var manifest = source.manifest;
            if(source.hasGenerate)
                manifest = source.generate();
            if(manifest == null)
                throw IllegalStateException("No manifest after generation");

            //TODO: Temporary naive assume single-sourced dash
            val foundTemplates = REGEX_DASH_TEMPLATE_WITH_MIME.findAll(manifest);
            val foundTemplate = when(downloadType) {
                1 -> foundTemplates.find({ it.groupValues[1].contains("video/") });
                2 -> foundTemplates.find({ it.groupValues[1].contains("audio/") });
                else -> foundTemplates.find({ it.groupValues[1].contains("video/") });
            }
            if(foundTemplate == null || foundTemplate.groupValues.size != 4)
                throw IllegalStateException("No SegmentTemplate found in manifest (unsupported dash?)");
            val foundTemplateUrl = foundTemplate.groupValues[2];
            val foundCues = REGEX_DASH_CUE.findAll(foundTemplate.groupValues[3]).toList();
            if(foundCues.count() <= 0)
                throw IllegalStateException("No Cues found in manifest (unsupported dash?)");

            val foundTemplate2 = if(downloadType == 3) foundTemplates.find({ it.groupValues[1].contains("audio/") }); else null;
            val foundTemplateUrl2 = if(foundTemplate2 != null) foundTemplate2.groupValues[2] else null;
            val foundCues2 = if(foundTemplate2 != null) REGEX_DASH_CUE.findAll(foundTemplate2.groupValues[3]).toList() else null;
            val foundCues2Downloaded = hashSetOf<MatchResult>();

            if(foundTemplate2 != null)
                overrideResultAudioSource = LocalAudioSource((videoSource?.name)?.let { it + " [audio]" } ?: "audio", "", 0, 0, foundTemplate2.groupValues[1], REGEX_CODECS.find(foundTemplate2.groupValues[0])?.groupValues?.get(1) ?: "", Language.UNKNOWN);

            executor = if(source is JSSource && source.hasRequestExecutor)
                source.getRequestExecutor();
            else
                null;

            val modifier = if (source is JSSource && source.hasRequestModifier)
                source.getRequestModifier();
            else
                null;
            val speedTracker = SpeedTracker(1000);

            Logger.i(TAG, "Download $name Dash, CueCount: " + foundCues.count().toString());

            var written: Long = 0;
            var written2: Long = 0;
            var indexCounter = 0;
            var indexCounter2 = 0;
            onProgress(foundCues.count().toLong(), 0, 0);
            val totalCues = foundCues.count().toLong() + (foundCues2?.count()?.toLong() ?: 0)
            val lastCue = foundCues.lastOrNull();
            for(cue in foundCues) {
                val t = cue.groupValues[1];
                val d = cue.groupValues[2];

                Logger.i(TAG, "Downloading cue ${indexCounter}")
                val url = foundTemplateUrl.replace("\$Number\$", (indexCounter).toString());
                val modified = modifier?.modifyRequest(url, mapOf());

                val data = if(executor != null)
                    executor.executeRequest("GET", modified?.url ?: url, null, modified?.headers ?: mapOf());
                else {
                    val resp = client.get(modified?.url ?: url, modified?.headers?.toMutableMap() ?: mutableMapOf());
                    if(!resp.isOk)
                        throw IllegalStateException("Dash request failed for index " + indexCounter.toString() + ", with code: " + resp.code.toString());
                    resp.body!!.bytes()
                }
                fileStream.write(data, 0, data.size);
                speedTracker.addWork(data.size.toLong());
                written += data.size;

                onProgress(totalCues, indexCounter.toLong() + indexCounter2.toLong(), speedTracker.lastSpeed);


                indexCounter++;

                if(foundCues2 != null && foundTemplateUrl2 != null && fileStream2 != null) {
                    val toDownload = if(lastCue != null && cue == lastCue)
                        foundCues2.filter { !foundCues2Downloaded.contains(it) }.toList() else
                        foundCues2.filter { !foundCues2Downloaded.contains(it) && (it.groupValues[1].toLong()) < t.toLong() }.toList();
                    Logger.i(TAG, "Downloading audio cues (${toDownload.size})")
                    for(cue2 in toDownload) {
                        val index2 = foundCues2.indexOf(cue2);
                        val t2 = cue2.groupValues[1];
                        val d2 = cue2.groupValues[2];
                        val url2 = foundTemplateUrl2!!.replace("\$Number\$", (index2).toString());
                        val modified2 = modifier?.modifyRequest(url, mapOf());

                        val data = if(executor != null)
                            executor.executeRequest("GET", modified2?.url ?: url2, null, modified2?.headers ?: mapOf());
                        else {
                            val resp = client.get(modified2?.url ?: url, modified2?.headers?.toMutableMap() ?: mutableMapOf());
                            if(!resp.isOk)
                                throw IllegalStateException("Dash request2 failed for index " + indexCounter.toString() + ", with code: " + resp.code.toString());
                            resp.body!!.bytes()
                        }
                        fileStream2.write(data, 0, data.size);
                        speedTracker.addWork(data.size.toLong());
                        written2 += data.size;
                        indexCounter2++;

                        foundCues2Downloaded.add(cue2);
                        onProgress(totalCues, indexCounter.toLong() + indexCounter2.toLong(), speedTracker.lastSpeed);
                    }
                }
            }
            sourceLength = written;
            sourceLengthAudio = written2;

            Logger.i(TAG, "$name downloadSource Finished");
        }
        catch(ioex: IOException) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(targetFileAudio?.exists() ?: false)
                targetFileAudio.delete();
            if(ioex.message?.contains("ENOSPC") ?: false)
                throw Exception("Not enough space on device", ioex);
            else
                throw ioex;
        }
        catch(ex: Throwable) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(targetFileAudio?.exists() ?: false)
                targetFileAudio.delete();
            throw ex;
        }
        finally {
            fileStream.close();
            fileStream2?.close();
            executor?.closeAsync()
        }
        if(sourceLengthAudio != null && sourceLengthAudio > 0)
            audioFileSize = sourceLengthAudio
        return sourceLength!!;
    }
    private fun downloadFileSource(name: String, client: ManagedHttpClient, source: JSSource?, videoUrl: String, targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();

        targetFile.createNewFile();

        val sourceLength: Long?;
        val fileStream = FileOutputStream(targetFile);

        val modifier = if (source is JSSource && source.hasRequestModifier)
            source.getRequestModifier();
        else
            null;

        try {
            val head = client.tryHead(videoUrl);
            val relatedPlugin = (video?.url ?: videoDetails?.url)?.let { StatePlatform.instance.getContentClient(it) }?.let { if(it is JSClient) it else null };
            if(Settings.instance.downloads.byteRangeDownload && head?.containsKey("accept-ranges") == true && head.containsKey("content-length"))
            {
                val maxParallel = if(relatedPlugin != null && relatedPlugin.config.maxDownloadParallelism > 0)
                    relatedPlugin.config.maxDownloadParallelism else 99;
                val concurrency = Math.min(maxParallel, Settings.instance.downloads.getByteRangeThreadCount());
                Logger.i(TAG, "Download $name ByteRange Parallel (${concurrency}): " + videoUrl);
                sourceLength = head["content-length"]!!.toLong();
                onProgress(sourceLength, 0, 0);
                downloadSource_Ranges(name, client, modifier, fileStream, videoUrl, sourceLength, 1024*512, concurrency, onProgress);
            }
            else {
                Logger.i(TAG, "Download $name Sequential");
                try {
                    sourceLength = downloadSource_Sequential(client, modifier, fileStream, videoUrl, null, 0, onProgress);
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to download sequentially (url = $videoUrl)")
                    throw e
                }
            }

            Logger.i(TAG, "$name downloadSource Finished");
        }
        catch(ioex: IOException) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            if(ioex.message?.contains("ENOSPC") ?: false)
                throw Exception("Not enough space on device", ioex);
            else
                throw ioex;
        }
        catch(ex: Throwable) {
            if(targetFile.exists() ?: false)
                targetFile.delete();
            throw ex;
        }
        finally {
            fileStream.close();
        }
        return sourceLength!!;
    }

    data class DecryptionInfo(
        val key: ByteArray,
        val iv: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DecryptionInfo

            if (!key.contentEquals(other.key)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    private fun downloadSource_Sequential(client: ManagedHttpClient, modifier: IRequestModifier? = null, fileStream: FileOutputStream, url: String, decryptionInfo: DecryptionInfo?, index: Int, onProgress: (Long, Long, Long) -> Unit): Long {
        val progressRate: Int = 4096 * 5;
        var lastProgressCount: Int = 0;
        val speedRate: Int = 4096 * 5;
        var readSinceLastSpeedTest: Long = 0;
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis();

        var lastSpeed: Long = 0;

        val result = if (modifier != null) {
            val modified = modifier.modifyRequest(url, mapOf())
            client.get(modified.url!!, modified.headers.toMutableMap())
        } else {
            client.get(url)
        }
        if (!result.isOk) {
            result.body?.close()
            throw IllegalStateException("Failed to download source. Web[${result.code}] Error");
        }
        if (result.body == null)
            throw IllegalStateException("Failed to download source. Web[${result.code}] No response");

        val sourceLength = result.body.contentLength();
        val sourceStream = result.body.byteStream();

        val segmentBuffer = ByteArrayOutputStream()

        var totalRead: Long = 0;
        try {
            var read: Int;
            val buffer = ByteArray(4096);

            do {
                read = sourceStream.read(buffer);
                if (read < 0)
                    break;

                segmentBuffer.write(buffer, 0, read);

                totalRead += read;

                readSinceLastSpeedTest += read;
                if (totalRead.toDouble() / progressRate > lastProgressCount) {
                    onProgress(sourceLength, totalRead, lastSpeed);
                    lastProgressCount++;
                }
                if (readSinceLastSpeedTest > speedRate) {
                    val lastSpeedTime = timeSinceLastSpeedTest;
                    timeSinceLastSpeedTest = System.currentTimeMillis();
                    val timeSince = timeSinceLastSpeedTest - lastSpeedTime;
                    if (timeSince > 0)
                        lastSpeed = (readSinceLastSpeedTest / (timeSince / 1000.0)).toLong();
                    readSinceLastSpeedTest = 0;
                }

                if (isCancelled)
                    throw CancellationException("Cancelled");
            } while (read > 0);
        } finally {
            sourceStream.close()
            result.body.close()
        }

        if (decryptionInfo != null) {
            var iv = decryptionInfo.iv
            if (iv == null) {
                iv = ByteBuffer.allocate(16)
                    .putLong(0L)
                    .putLong(index.toLong())
                    .array()
            }

            val decryptedData = decryptSegment(segmentBuffer.toByteArray(), decryptionInfo.key, iv!!)
            fileStream.write(decryptedData)
        } else {
            fileStream.write(segmentBuffer.toByteArray())
        }

        onProgress(sourceLength, totalRead, 0);
        return sourceLength;
    }
    /*private fun downloadSource_Sequential(client: ManagedHttpClient, fileStream: FileOutputStream, url: String, onProgress: (Long, Long, Long) -> Unit): Long {
        val progressRate: Int = 4096 * 25
        var lastProgressCount: Int = 0
        val speedRate: Int = 4096 * 25
        var readSinceLastSpeedTest: Long = 0
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis()

        var lastSpeed: Long = 0

        var totalRead: Long = 0
        var sourceLength: Long
        val buffer = ByteArray(4096)

        var isPartialDownload = false
        var result: ManagedHttpClient.Response? = null
        do {
            result = client.get(url, if (isPartialDownload) hashMapOf("Range" to "bytes=$totalRead-") else hashMapOf())
            if (isPartialDownload) {
                if (result.code != 206)
                    throw IllegalStateException("Failed to download source, byte range fallback failed. Web[${result.code}] Error")
            } else {
                if (!result.isOk)
                    throw IllegalStateException("Failed to download source. Web[${result.code}] Error")
            }
            if (result.body == null)
                throw IllegalStateException("Failed to download source. Web[${result.code}] No response")

            isPartialDownload = true
            sourceLength = result.body!!.contentLength()
            val sourceStream = result.body!!.byteStream()

            try {
                while (true) {
                    val read = sourceStream.read(buffer)
                    if (read <= 0) {
                        break
                    }

                    fileStream.write(buffer, 0, read)

                    totalRead += read
                    readSinceLastSpeedTest += read

                    if (totalRead / progressRate > lastProgressCount) {
                        onProgress(sourceLength, totalRead, lastSpeed)
                        lastProgressCount++
                    }
                    if (readSinceLastSpeedTest > speedRate) {
                        val lastSpeedTime = timeSinceLastSpeedTest
                        timeSinceLastSpeedTest = System.currentTimeMillis()
                        val timeSince = timeSinceLastSpeedTest - lastSpeedTime
                        if (timeSince > 0)
                            lastSpeed = (readSinceLastSpeedTest / (timeSince / 1000.0)).toLong()
                        readSinceLastSpeedTest = 0
                    }

                    if (isCancelled)
                        throw CancellationException("Cancelled")
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Sequential download was interrupted, trying to fallback to byte ranges", e)
            } finally {
                sourceStream.close()
                result.body?.close()
            }
        } while (totalRead < sourceLength)

        onProgress(sourceLength, totalRead, 0)
        return sourceLength
    }*/
    private fun downloadSource_Ranges(name: String, client: ManagedHttpClient, modifier: IRequestModifier?, fileStream: FileOutputStream, url: String, sourceLength: Long, rangeSize: Int, concurrency: Int = 1, onProgress: (Long, Long, Long) -> Unit) {
        val progressRate: Int = 4096 * 5;
        var lastProgressCount: Int = 0;
        val speedRate: Int = 4096 * 5;
        var readSinceLastSpeedTest: Long = 0;
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis();

        var lastSpeed: Long = 0;

        var reqCount = -1;
        var totalRead: Long = 0;

        val pool = ForkJoinPool(concurrency);

        while(totalRead < sourceLength) {
            reqCount++;

            Logger.i(TAG, "Download ${name} Batch #${reqCount} [${concurrency}] (${lastSpeed.toHumanBytesSpeed()})");

            val byteRangeResults = requestByteRangeParallel(client, pool, modifier, url, sourceLength, concurrency, totalRead,
                rangeSize, 1024 * 64);

            for(byteRange in byteRangeResults) {
                val read = ((byteRange.third - byteRange.second) + 1).toInt();

                fileStream.write(byteRange.first, 0, read);

                totalRead += read;
                readSinceLastSpeedTest += read;
            }

            if(readSinceLastSpeedTest > speedRate) {
                val lastSpeedTime = timeSinceLastSpeedTest;
                timeSinceLastSpeedTest = System.currentTimeMillis();
                val timeSince = timeSinceLastSpeedTest - lastSpeedTime;
                if(timeSince > 0)
                    lastSpeed = (readSinceLastSpeedTest / (timeSince / 1000.0)).toLong();
                readSinceLastSpeedTest = 0;
            }
            if(totalRead / progressRate > lastProgressCount) {
                onProgress(sourceLength, totalRead, lastSpeed);
                lastProgressCount++;
            }

            if(isCancelled)
                throw CancellationException("Cancelled", null);
        }
        onProgress(sourceLength, totalRead, 0);
    }

    private fun requestByteRangeParallel(client: ManagedHttpClient, pool: ForkJoinPool, modifier: IRequestModifier?, url: String, totalLength: Long, concurrency: Int, rangePosition: Long, rangeSize: Int, rangeVariance: Int = -1): List<Triple<ByteArray, Long, Long>> {
        val tasks = mutableListOf<ForkJoinTask<Triple<ByteArray, Long, Long>>>();
        var readPosition = rangePosition;
        for(i in 0 until concurrency) {
            if(readPosition >= totalLength - 1)
                continue;

            val toRead = rangeSize + (if(rangeVariance >= 1) ThreadLocalRandom.current().nextInt(rangeVariance * -1, rangeVariance) else 0);
            val rangeStart = readPosition;
            val rangeEnd = if(rangeStart + toRead > totalLength)
                totalLength - 1;
            else readPosition + toRead;

            tasks.add(pool.submit<Triple<ByteArray, Long, Long>> {
                return@submit requestByteRange(client, modifier, url, rangeStart, rangeEnd);
            });
            readPosition = rangeEnd + 1;
        }

        return tasks.map { it.get() };
    }
    private fun requestByteRange(client: ManagedHttpClient, modifier: IRequestModifier?, url: String, rangeStart: Long, rangeEnd: Long): Triple<ByteArray, Long, Long> {
        var retryCount = 0
        var lastException: Throwable? = null;

        val headers = mutableMapOf(Pair("Range", "bytes=${rangeStart}-${rangeEnd}"));
        val modified = modifier?.modifyRequest(url, headers);

        while (retryCount <= 3) {
            try {
                val toRead = rangeEnd - rangeStart;

                val req = client.get(modified?.url ?: url, modified?.headers?.toMutableMap() ?: headers);
                if (!req.isOk) {
                    val bodyString = req.body?.string()
                    req.body?.close()
                    throw IllegalStateException("Range request failed Code [${req.code}] due to: ${req.message}");
                }
                if (req.body == null)
                    throw IllegalStateException("Range request failed, No body");
                val read = req.body.contentLength();

                if (read < toRead)
                    throw IllegalStateException("Byte-Range request attempted to provide less (${read} < ${toRead})");

                return Triple(req.body.bytes(), rangeStart, rangeEnd);
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to download range (url=${url} bytes=${rangeStart}-${rangeEnd})", e)

                retryCount++
                lastException = e

                sleep(when (retryCount) {
                    1 -> 1000 + ((Math.random() * 300.0).toLong() - 150)
                    2 -> 2000 + ((Math.random() * 300.0).toLong() - 150)
                    3 -> 4000 + ((Math.random() * 300.0).toLong() - 150)
                    else -> 1000 + ((Math.random() * 300.0).toLong() - 150)
                })
            }
        }

        throw lastException!!
    }

    fun validate() {
        Logger.i(TAG, "VideoDownload Validate [${name}]");
        if(videoSourceToUse != null) {
            if(videoFilePath == null)
                throw IllegalStateException("Missing video file name after download");
            val expectedFile = File(videoFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Video file missing after download");
            if (videoSource?.container != "application/vnd.apple.mpegurl") {
                if (expectedFile.length() != videoFileSize)
                    throw IllegalStateException("Expected size [${videoFileSize}], but found ${expectedFile.length()}");
            }
        }
        if(audioSourceToUse != null || (videoSourceToUse is IJSDashManifestRawSource)) {
            if(audioFilePath == null)
                throw IllegalStateException("Missing audio file name after download");
            val expectedFile = File(audioFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Audio file missing after download");
            if (audioSource?.container != "application/vnd.apple.mpegurl") {
                if (expectedFile.length() != audioFileSize)
                    throw IllegalStateException("Expected size [${audioFileSize}], but found ${expectedFile.length()}");
            }
        }
        if(subtitleSource != null) {
            if(subtitleFilePath == null)
                throw IllegalStateException("Missing subtitle file name after download");
            val expectedFile = File(subtitleFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Subtitle file missing after download");
        }
    }
    fun complete() {
        Logger.i(TAG, "VideoDownload Complete [${name}]");
        val existing = StateDownloads.instance.getCachedVideo(id);
        val localVideoSource = videoFilePath?.let { LocalVideoSource.fromSource(videoSourceToUse!!, it, videoFileSize ?: 0, videoOverrideContainer) };
        val localAudioSource = audioFilePath?.let { LocalAudioSource.fromSource(overrideResultAudioSource ?: audioSourceToUse!!, it, audioFileSize ?: 0, audioOverrideContainer) };
        val localSubtitleSource = subtitleFilePath?.let { LocalSubtitleSource.fromSource(subtitleSource!!, it) };

        if(localVideoSource != null && videoSourceToUse != null && videoSourceToUse is IStreamMetaDataSource)
            localVideoSource.streamMetaData = (videoSourceToUse as IStreamMetaDataSource).streamMetaData;

        if(localAudioSource != null && audioSourceToUse != null && audioSourceToUse is IStreamMetaDataSource)
            localAudioSource.streamMetaData = (audioSourceToUse as IStreamMetaDataSource).streamMetaData;

        if(existing != null) {
            existing.videoSerialized = videoDetails!!;
            if(localVideoSource != null) {
                val newVideos = ArrayList(existing.videoSource);
                newVideos.add(localVideoSource);
                existing.videoSource = newVideos;
            }
            if(localAudioSource != null) {
                val newAudios = ArrayList(existing.audioSource);
                newAudios.add(localAudioSource);
                existing.audioSource = newAudios;
            }
            if (localSubtitleSource != null) {
                val newSubtitles = ArrayList(existing.subtitlesSources);
                newSubtitles.add(localSubtitleSource);
                existing.subtitlesSources = newSubtitles;
            }
            StateDownloads.instance.updateCachedVideo(existing);
        }
        else {
            val newVideo = VideoLocal(videoDetails!!, OffsetDateTime.now());
            if(localVideoSource != null)
                newVideo.videoSource.add(localVideoSource);
            if(localAudioSource != null)
                newVideo.audioSource.add(localAudioSource);
            if (localSubtitleSource != null)
                newVideo.subtitlesSources.add(localSubtitleSource);
            newVideo.groupID = groupID;
            newVideo.groupType = groupType;
            StateDownloads.instance.updateCachedVideo(newVideo);
        }
    }

    enum class State {
        QUEUED,
        PREPARING,
        DOWNLOADING,
        VALIDATING,
        FINALIZING,
        COMPLETED,
        ERROR;

        override fun toString(): String {
            val lowercase = super.toString().lowercase();
            if(lowercase.length == 0)
                return lowercase;
            return lowercase[0].uppercase() + lowercase.substring(1);
        }
    }

    companion object {
        const val TAG = "VideoDownload";
        const val GROUP_PLAYLIST = "Playlist";
        const val GROUP_WATCHLATER= "WatchLater";

        val REGEX_DASH_TEMPLATE = Regex("<SegmentTemplate .*?media=\"(.*?)\".*?>(.*?)<\\/SegmentTemplate>", RegexOption.DOT_MATCHES_ALL);
        val REGEX_DASH_TEMPLATE_WITH_MIME = Regex("<Representation.*?mimeType=\\\"(.*?)\\\".*?>.*?<SegmentTemplate .*?media=\\\"(.*?)\\\".*?>(.*?)<\\/SegmentTemplate>", RegexOption.DOT_MATCHES_ALL);
        val REGEX_CODECS = Regex("codecs=\\\"(.*?)\\\"")
        val REGEX_DASH_CUE = Regex("<S .*?t=\"([0-9]*?)\".*?d=\"([0-9]*?)\".*?\\/>", RegexOption.DOT_MATCHES_ALL);

        fun videoContainerToExtension(container: String): String? {
            if (container.contains("video/mp4") || container == "application/vnd.apple.mpegurl")
                return "mp4";
            else if (container.contains("application/x-mpegURL"))
                return "m3u8";
            else if (container.contains("video/3gpp"))
                return "3gp";
            else if (container.contains("video/quicktime"))
                return "mov";
            else if (container.contains("video/webm"))
                return "webm";
            else if (container.contains("video/x-matroska"))
                return "mkv";
            else
                return "video";//throw IllegalStateException("Unknown container: " + container)
        }

        //TODO: Change usages of this to an accurate container instead of infering it.
        fun videoAudioContainerToExtension(container: String): String? {
            if (container.contains("video/mp4") || container == "application/vnd.apple.mpegurl")
                return "mp4a";
            else if (container.contains("video/webm"))
                return "webm";
            else
                return "mp4a";//throw IllegalStateException("Unknown container: " + container)
        }

        fun audioContainerToExtension(container: String): String {
            if (container.contains("audio/mp4"))
                return "mp4a";
            else if (container.contains("video/mp4"))
                return "mp4";
            else if (container.contains("audio/mpeg"))
                return "mpga";
            else if (container.contains("audio/mp3"))
                return "mp3";
            else if (container.contains("audio/webm"))
                return "webm";
            else if (container == "application/vnd.apple.mpegurl")
                return "m4a";
            else
                return "audio";// throw IllegalStateException("Unknown container: " + container)
        }

        fun subtitleContainerToExtension(container: String?): String {
            if (container == null)
                return "subtitle";

            if (container.contains("text/vtt"))
                return "vtt";
            else if (container.contains("text/plain"))
                return "srt";
            else if (container.contains("application/x-subrip"))
                return "srt";
            else
                return "subtitle";
        }
    }

    class SpeedTracker {
        private val segmentStart: Long;
        private val intervalMs: Long;
        private var workDone: Long;
        var lastSpeed: Long;
        constructor(intervalMs: Long) {
            segmentStart = System.currentTimeMillis();
            this.intervalMs = intervalMs;
            this.workDone = 0;
            this.lastSpeed = 0;
        }
        fun addWork(work: Long) {
            val now = System.currentTimeMillis();
            if((now - segmentStart) > intervalMs)
            {
                lastSpeed = workDone;
                workDone = 0;
            }
            workDone += work;
        }

    }
}