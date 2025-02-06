package com.futo.platformplayer.downloads

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.PlatformID
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
import com.futo.platformplayer.parsers.HLS
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.toHumanBitrate
import com.futo.platformplayer.toHumanBytesSpeed
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Thread.sleep
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ThreadLocalRandom
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

    var progress: Double = 0.0;
    var isCancelled = false;

    var downloadSpeedVideo: Long = 0;
    var downloadSpeedAudio: Long = 0;
    val downloadSpeed: Long get() = downloadSpeedVideo + downloadSpeedAudio;

    var error: String? = null;

    var videoFilePath: String? = null;
    var videoFileName: String? = null;
    var videoFileSize: Long? = null;

    var audioFilePath: String? = null;
    var audioFileName: String? = null;
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
        this.requiresLiveVideoSource = this.hasVideoRequestExecutor || (videoSource is JSDashManifestRawSource && videoSource.hasGenerate);
        this.requiresLiveAudioSource = this.hasAudioRequestExecutor || (audioSource is JSDashManifestRawAudioSource && audioSource.hasGenerate);
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
        }
        if(requiresLiveAudioSource && !isLiveAudioSourceValid) {
            videoDetails = null;
            audioSource = null;
            videoSourceLive = null;
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
                                val playlistContent = playlistResponse.body?.string()
                                if (playlistContent != null) {
                                    videoSources.addAll(HLS.parseAndGetVideoSources(source, playlistContent, source.url))
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
                                    val playlistContent = playlistResponse.body?.string()
                                    if (playlistContent != null) {
                                        audioSources.addAll(HLS.parseAndGetAudioSources(source, playlistContent, source.url))
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
                    this.hasVideoRequestExecutor = this.hasVideoRequestExecutor || asource.hasRequestExecutor;
                    this.requiresLiveVideoSource = this.hasVideoRequestExecutor || (asource is JSDashManifestRawSource && asource.hasGenerate);
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
            videoFileName = "${videoDetails!!.id.value!!} [${actualVideoSource!!.width}x${actualVideoSource!!.height}].${videoContainerToExtension(actualVideoSource!!.container)}".sanitizeFileName();
            videoFilePath = File(downloadDir, videoFileName!!).absolutePath;
        }
        if(actualAudioSource != null) {
            audioFileName = "${videoDetails!!.id.value!!} [${actualAudioSource!!.language}-${actualAudioSource!!.bitrate}].${audioContainerToExtension(actualAudioSource!!.container)}".sanitizeFileName();
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
                        "application/vnd.apple.mpegurl" -> downloadHlsSource(context, "Video", client, videoSource!!.getVideoUrl(), File(downloadDir, videoFileName!!), progressCallback)
                        else -> downloadFileSource("Video", client, videoSource!!.getVideoUrl(), File(downloadDir, videoFileName!!), progressCallback)
                    }
                else if(actualVideoSource is JSDashManifestRawSource) {
                    videoFileSize = downloadDashFileSource("Video", client, actualVideoSource, File(downloadDir, videoFileName!!), progressCallback);
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
                        "application/vnd.apple.mpegurl" -> downloadHlsSource(context, "Audio", client, audioSource!!.getAudioUrl(), File(downloadDir, audioFileName!!), progressCallback)
                        else -> downloadFileSource("Audio", client, audioSource!!.getAudioUrl(), File(downloadDir, audioFileName!!), progressCallback)
                    }
                else if(actualAudioSource is JSDashManifestRawAudioSource) {
                    audioFileSize = downloadDashFileSource("Audio", client, actualAudioSource, File(downloadDir, audioFileName!!), progressCallback);
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

    private suspend fun downloadHlsSource(context: Context, name: String, client: ManagedHttpClient, hlsUrl: String, targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();

        var downloadedTotalLength = 0L

        val segmentFiles = arrayListOf<File>()
        try {
            val response = client.get(hlsUrl)
            check(response.isOk) { "Failed to get variant playlist: ${response.code}" }

            val vpContent = response.body?.string()
                ?: throw Exception("Variant playlist content is empty")

            val variantPlaylist = HLS.parseVariantPlaylist(vpContent, hlsUrl)
            variantPlaylist.segments.forEachIndexed { index, segment ->
                if (segment !is HLS.MediaSegment) {
                    return@forEachIndexed
                }

                Logger.i(TAG, "Download '$name' segment $index Sequential");
                val segmentFile = File(context.cacheDir, "segment-${UUID.randomUUID()}")
                val outputStream = segmentFile.outputStream()
                try {
                    segmentFiles.add(segmentFile)

                    val segmentLength = downloadSource_Sequential(client, outputStream, segment.uri) { segmentLength, totalRead, lastSpeed ->
                        val averageSegmentLength = if (index == 0) segmentLength else downloadedTotalLength / index
                        val expectedTotalLength = averageSegmentLength * (variantPlaylist.segments.size - 1) + segmentLength
                        onProgress(expectedTotalLength, downloadedTotalLength + totalRead, lastSpeed)
                    }

                    downloadedTotalLength += segmentLength
                } finally {
                    outputStream.close()
                }
            }

            Logger.i(TAG, "Combining segments into $targetFile");
            combineSegments(context, segmentFiles, targetFile)

            Logger.i(TAG, "${name} downloadSource Finished");
        }
        catch(ioex: IOException) {
            if(targetFile.exists())
                targetFile.delete();
            if(ioex.message?.contains("ENOSPC") ?: false)
                throw Exception("Not enough space on device", ioex);
            else
                throw ioex;
        }
        catch(ex: Throwable) {
            if(targetFile.exists())
                targetFile.delete();
            throw ex;
        }
        finally {
            for (segmentFile in segmentFiles) {
                segmentFile.delete()
            }
        }
        return downloadedTotalLength;
    }

    private suspend fun combineSegments(context: Context, segmentFiles: List<File>, targetFile: File) = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val fileList = File(context.cacheDir, "fileList-${UUID.randomUUID()}.txt")
            fileList.writeText(segmentFiles.joinToString("\n") { "file '${it.absolutePath}'" })

            val cmd = "-f concat -safe 0 -i \"${fileList.absolutePath}\" -c copy \"${targetFile.absolutePath}\""

            val statisticsCallback = StatisticsCallback { _ ->
                //TODO: Show progress?
            }

            val executorService = Executors.newSingleThreadExecutor()
            val session = FFmpegKit.executeAsync(cmd,
                { session ->
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        fileList.delete()
                        continuation.resumeWith(Result.success(Unit))
                    } else {
                        val errorMessage = if (ReturnCode.isCancel(session.returnCode)) {
                            "Command cancelled"
                        } else {
                            "Command failed with state '${session.state}' and return code ${session.returnCode}, stack trace ${session.failStackTrace}"
                        }
                        fileList.delete()
                        continuation.resumeWithException(RuntimeException(errorMessage))
                    }
                },
                { Logger.v(TAG, it.message) },
                statisticsCallback,
                executorService
            )

            continuation.invokeOnCancellation {
                session.cancel()
            }
        }
    }

    private fun downloadDashFileSource(name: String, client: ManagedHttpClient, source: IJSDashManifestRawSource, targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();

        targetFile.createNewFile();

        val sourceLength: Long?;
        val fileStream = FileOutputStream(targetFile);

        try{
            var manifest = source.manifest;
            if(source.hasGenerate)
                manifest = source.generate();
            if(manifest == null)
                throw IllegalStateException("No manifest after generation");

            //TODO: Temporary naive assume single-sourced dash
            val foundTemplate = REGEX_DASH_TEMPLATE.find(manifest);
            if(foundTemplate == null || foundTemplate.groupValues.size != 3)
                throw IllegalStateException("No SegmentTemplate found in manifest (unsupported dash?)");
            val foundTemplateUrl = foundTemplate.groupValues[1];
            val foundCues = REGEX_DASH_CUE.findAll(foundTemplate.groupValues[2]);
            if(foundCues.count() <= 0)
                throw IllegalStateException("No Cues found in manifest (unsupported dash?)");

            val executor = if(source is JSSource && source.hasRequestExecutor)
                source.getRequestExecutor();
            else
                null;
            val speedTracker = SpeedTracker(1000);

            Logger.i(TAG, "Download $name Dash, CueCount: " + foundCues.count().toString());

            var written = 0;
            var indexCounter = 0;
            onProgress(foundCues.count().toLong(), 0, 0);
            for(cue in foundCues) {
                val t = cue.groupValues[1];
                val d = cue.groupValues[2];

                val url = foundTemplateUrl.replace("\$Number\$", indexCounter.toString());

                val data = if(executor != null)
                    executor.executeRequest("GET", url, null, mapOf());
                else {
                    val resp = client.get(url, mutableMapOf());
                    if(!resp.isOk)
                        throw IllegalStateException("Dash request failed for index " + indexCounter.toString() + ", with code: " + resp.code.toString());
                    resp.body!!.bytes()
                }
                fileStream.write(data, 0, data.size);
                speedTracker.addWork(data.size.toLong());
                written += data.size;

                onProgress(foundCues.count().toLong(), indexCounter.toLong(), speedTracker.lastSpeed);

                indexCounter++;
            }
            sourceLength = written.toLong();

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
    private fun downloadFileSource(name: String, client: ManagedHttpClient, videoUrl: String, targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();

        targetFile.createNewFile();

        val sourceLength: Long?;
        val fileStream = FileOutputStream(targetFile);

        try{
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
                downloadSource_Ranges(name, client, fileStream, videoUrl, sourceLength, 1024*512, concurrency, onProgress);
            }
            else {
                Logger.i(TAG, "Download $name Sequential");
                try {
                    sourceLength = downloadSource_Sequential(client, fileStream, videoUrl, onProgress);
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
    private fun downloadSource_Sequential(client: ManagedHttpClient, fileStream: FileOutputStream, url: String, onProgress: (Long, Long, Long) -> Unit): Long {
        val progressRate: Int = 4096 * 5;
        var lastProgressCount: Int = 0;
        val speedRate: Int = 4096 * 5;
        var readSinceLastSpeedTest: Long = 0;
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis();

        var lastSpeed: Long = 0;

        val result = client.get(url);
        if (!result.isOk) {
            result.body?.close()
            throw IllegalStateException("Failed to download source. Web[${result.code}] Error");
        }
        if (result.body == null)
            throw IllegalStateException("Failed to download source. Web[${result.code}] No response");

        val sourceLength = result.body.contentLength();
        val sourceStream = result.body.byteStream();

        var totalRead: Long = 0;
        try {
            var read: Int;
            val buffer = ByteArray(4096);

            do {
                read = sourceStream.read(buffer);
                if (read < 0)
                    break;

                fileStream.write(buffer, 0, read);

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
    private fun downloadSource_Ranges(name: String, client: ManagedHttpClient, fileStream: FileOutputStream, url: String, sourceLength: Long, rangeSize: Int, concurrency: Int = 1, onProgress: (Long, Long, Long) -> Unit) {
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

            val byteRangeResults = requestByteRangeParallel(client, pool, url, sourceLength, concurrency, totalRead,
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

    private fun requestByteRangeParallel(client: ManagedHttpClient, pool: ForkJoinPool, url: String, totalLength: Long, concurrency: Int, rangePosition: Long, rangeSize: Int, rangeVariance: Int = -1): List<Triple<ByteArray, Long, Long>> {
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
                return@submit requestByteRange(client, url, rangeStart, rangeEnd);
            });
            readPosition = rangeEnd + 1;
        }

        return tasks.map { it.get() };
    }
    private fun requestByteRange(client: ManagedHttpClient, url: String, rangeStart: Long, rangeEnd: Long): Triple<ByteArray, Long, Long> {
        var retryCount = 0
        var lastException: Throwable? = null

        while (retryCount <= 3) {
            try {
                val toRead = rangeEnd - rangeStart;
                val req = client.get(url, mutableMapOf(Pair("Range", "bytes=${rangeStart}-${rangeEnd}")));
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
        if(audioSourceToUse != null) {
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
        val localVideoSource = videoFilePath?.let { LocalVideoSource.fromSource(videoSourceToUse!!, it, videoFileSize ?: 0) };
        val localAudioSource = audioFilePath?.let { LocalAudioSource.fromSource(audioSourceToUse!!, it, audioFileSize ?: 0) };
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
                return "video";
        }

        fun audioContainerToExtension(container: String): String {
            if (container.contains("audio/mp4"))
                return "mp4a";
            else if (container.contains("audio/mpeg"))
                return "mpga";
            else if (container.contains("audio/mp3"))
                return "mp3";
            else if (container.contains("audio/webm"))
                return "webma";
            else if (container == "application/vnd.apple.mpegurl")
                return "mp4";
            else
                return "audio";
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