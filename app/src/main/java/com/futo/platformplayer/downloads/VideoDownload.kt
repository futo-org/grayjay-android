package com.futo.platformplayer.downloads

import com.futo.platformplayer.Settings
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.exceptions.DownloadException
import com.futo.platformplayer.hasAnySource
import com.futo.platformplayer.helpers.FileHelper.Companion.sanitizeFileName
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.isDownloadable
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import com.futo.platformplayer.toHumanBitrate
import com.futo.platformplayer.toHumanBytesSpeed
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.CancellationException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ThreadLocalRandom

@kotlinx.serialization.Serializable
class VideoDownload {
    var state: State = State.QUEUED;

    var video: SerializedPlatformVideo? = null;
    var videoDetails: SerializedPlatformVideoDetails? = null;

    @kotlinx.serialization.Transient
    val videoEither: IPlatformVideo get() = videoDetails ?: video ?: throw IllegalStateException("Missing video?");

    @kotlinx.serialization.Transient
    val id: PlatformID get() = videoEither.id
    @kotlinx.serialization.Transient
    val name: String get() = videoEither.name;
    @kotlinx.serialization.Transient
    val thumbnail: String? get() = videoDetails?.thumbnails?.getHQThumbnail() ?: video?.thumbnails?.getHQThumbnail();

    var targetPixelCount: Long? = null;
    var targetBitrate: Long? = null;
    var videoSource: VideoUrlSource?;
    var audioSource: AudioUrlSource?;
    var subtitleSource: SubtitleRawSource?;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    var prepareTime: OffsetDateTime? = null;

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

    constructor(video: IPlatformVideo, targetPixelCount: Long? = null, targetBitrate: Long? = null) {
        this.video = SerializedPlatformVideo.fromVideo(video);
        this.videoSource = null;
        this.audioSource = null;
        this.subtitleSource = null;
        this.targetPixelCount = targetPixelCount;
        this.targetBitrate = targetBitrate;
    }
    constructor(video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: SubtitleRawSource?) {
        this.video = SerializedPlatformVideo.fromVideo(video);
        this.videoDetails = SerializedPlatformVideoDetails.fromVideo(video, if (subtitleSource != null) listOf(subtitleSource) else listOf());
        this.videoSource = VideoUrlSource.fromUrlSource(videoSource);
        this.audioSource = AudioUrlSource.fromUrlSource(audioSource);
        this.subtitleSource = subtitleSource;
        this.prepareTime = OffsetDateTime.now();
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

    suspend fun prepare() {
        Logger.i(TAG, "VideoDownload Prepare [${name}]");
        if(video == null && videoDetails == null)
            throw IllegalStateException("Missing information for download to complete");
        if(targetPixelCount == null && targetBitrate == null && videoSource == null && audioSource == null)
            throw IllegalStateException("No sources or query values set");

        //Fetch full video object and determine source
        if(video != null && videoDetails == null) {
            val original = StatePlatform.instance.getContentDetails(video!!.url).await();
            if(original !is IPlatformVideoDetails)
                throw IllegalStateException("Original content is not media?");

            if(original.video.hasAnySource() && !original.isDownloadable()) {
                Logger.i(TAG, "Attempted to download unsupported video [${original.name}]:${original.url}");
                throw DownloadException("Unsupported video for downloading", false);
            }

            videoDetails = SerializedPlatformVideoDetails.fromVideo(original, if (subtitleSource != null) listOf(subtitleSource!!) else listOf());
            if(videoSource == null && targetPixelCount != null) {
                val vsource = VideoHelper.selectBestVideoSource(videoDetails!!.video, targetPixelCount!!.toInt(), arrayOf())
                //    ?: throw IllegalStateException("Could not find a valid video source for video");
                if(vsource != null) {
                    if (vsource is IVideoUrlSource)
                        videoSource = VideoUrlSource.fromUrlSource(vsource);
                    else
                        throw DownloadException("Video source is not supported for downloading (yet)", false);
                }
            }

            if(audioSource == null && targetBitrate != null) {
                val asource = VideoHelper.selectBestAudioSource(videoDetails!!.video, arrayOf(), null, targetPixelCount)
                    ?: if(videoSource != null ) null
                    else throw DownloadException("Could not find a valid video or audio source for download")
                if(asource == null)
                    audioSource = null;
                else if(asource is IAudioUrlSource)
                    audioSource = AudioUrlSource.fromUrlSource(asource);
                else
                    throw DownloadException("Audio source is not supported for downloading (yet)", false);
            }

            if(videoSource == null && audioSource == null)
                throw DownloadException("No valid sources found for video/audio");
        }
    }
    suspend fun download(client: ManagedHttpClient, onProgress: ((Double) -> Unit)? = null) = coroutineScope {
        Logger.i(TAG, "VideoDownload Download [${name}]");
        if(videoDetails == null || (videoSource == null && audioSource == null))
            throw IllegalStateException("Missing information for download to complete");
        val downloadDir = StateDownloads.instance.getDownloadsDirectory();

        if(videoDetails!!.id.value == null)
            throw IllegalStateException("Video has no id");

        if(isCancelled) throw CancellationException("Download got cancelled");

        if(videoSource != null) {
            videoFileName = "${videoDetails!!.id.value!!} [${videoSource!!.width}x${videoSource!!.height}].${videoContainerToExtension(videoSource!!.container)}".sanitizeFileName();
            videoFilePath = File(downloadDir, videoFileName!!).absolutePath;
        }
        if(audioSource != null) {
            audioFileName = "${videoDetails!!.id.value!!} [${audioSource!!.bitrate}].${audioContainerToExtension(audioSource!!.container)}".sanitizeFileName();
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

        if(videoSource != null) {
            sourcesToDownload.add(async {
                Logger.i(TAG, "Started downloading video");
                videoFileSize = downloadSource("Video", client, videoSource!!.getVideoUrl(), File(downloadDir, videoFileName!!)) { length, totalRead, speed ->
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
                            onProgress?.invoke(percentage);
                            progress = percentage;
                            onProgressChanged.emit(percentage);
                        }
                    }
                }
            });
        }
        if(audioSource != null) {
            sourcesToDownload.add(async {
                Logger.i(TAG, "Started downloading audio");
                audioFileSize = downloadSource("Audio", client, audioSource!!.getAudioUrl(), File(downloadDir, audioFileName!!)) { length, totalRead, speed ->
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
                            onProgress?.invoke(percentage);
                            progress = percentage;
                            onProgressChanged.emit(percentage);
                        }
                    }
                }
            });
        }
        if (subtitleSource != null) {
            sourcesToDownload.add(async {
                File(downloadDir, subtitleFileName!!).writeText(subtitleSource!!._subtitles)
            });
        }

        try {
            awaitAll(*sourcesToDownload.toTypedArray());
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
    }
    private fun downloadSource(name: String, client: ManagedHttpClient, videoUrl: String, targetFile: File, onProgress: (Long, Long, Long) -> Unit): Long {
        if(targetFile.exists())
            targetFile.delete();

        targetFile.createNewFile();

        var sourceLength: Long? = null;

        val fileStream = FileOutputStream(targetFile);

        try{
            val head = client.tryHead(videoUrl);
            if(Settings.instance.downloads.byteRangeDownload && head?.containsKey("accept-ranges") == true && head.containsKey("content-length"))
            {
                val concurrency = Settings.instance.downloads.getByteRangeThreadCount();
                Logger.i(TAG, "Download ${name} ByteRange Parallel (${concurrency})");
                sourceLength = head["content-length"]!!.toLong();
                onProgress(sourceLength, 0, 0);
                downloadSource_Ranges(name, client, fileStream, videoUrl, sourceLength, 1024*512, concurrency, onProgress);
            }
            else {
                Logger.i(TAG, "Download ${name} Sequential");
                sourceLength = downloadSource_Sequential(client, fileStream, videoUrl, onProgress);
            }

            Logger.i(TAG, "${name} downloadSource Finished");
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
            fileStream?.close();
        }
        return sourceLength!!;
    }
    private fun downloadSource_Sequential(client: ManagedHttpClient, fileStream: FileOutputStream, url: String, onProgress: (Long, Long, Long) -> Unit): Long {
        val progressRate: Int = 4096 * 25;
        var lastProgressCount: Int = 0;
        val speedRate: Int = 4096 * 25;
        var readSinceLastSpeedTest: Long = 0;
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis();

        var lastSpeed: Long = 0;

        val result = client.get(url);
        if (!result.isOk)
            throw IllegalStateException("Failed to download source. Web[${result.code}] Error");
        if (result.body == null)
            throw IllegalStateException("Failed to download source. Web[${result.code}] No response");

        val sourceLength = result.body.contentLength();
        val sourceStream = result.body.byteStream();

        var totalRead: Long = 0;
        var read = 0;

        val buffer = ByteArray(4096);

        do {
            read = sourceStream.read(buffer);
            if (read < 0)
                break;

            fileStream.write(buffer, 0, read);

            totalRead += read;

            readSinceLastSpeedTest += read;
            if (totalRead / progressRate > lastProgressCount) {
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
                throw IllegalStateException("Cancelled");
        } while (read > 0);

        lastSpeed = 0;
        onProgress(sourceLength, totalRead, 0);
        return sourceLength;
    }
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
                throw IllegalStateException("Cancelled");
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
        val toRead = rangeEnd - rangeStart;
        val req = client.get(url, mutableMapOf(Pair("Range", "bytes=${rangeStart}-${rangeEnd}")));
        if(!req.isOk)
            throw IllegalStateException("Range request failed Code [${req.code}] due to: ${req.message}");
        if(req.body == null)
            throw IllegalStateException("Range request failed, No body");
        val read = req.body.contentLength();

        if(read < toRead)
            throw IllegalStateException("Byte-Range request attempted to provide less (${read} < ${toRead})");

        return Triple(req.body.bytes(), rangeStart, rangeEnd);
    }

    fun validate() {
        Logger.i(TAG, "VideoDownload Validate [${name}]");
        if(videoSource != null) {
            if(videoFilePath == null)
                throw IllegalStateException("Missing video file name after download");
            val expectedFile = File(videoFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Video file missing after download");
            if(expectedFile.length() != videoFileSize)
                throw IllegalStateException("Expected size [${videoFileSize}], but found ${expectedFile.length()}");
        }
        if(audioSource != null) {
            if(audioFilePath == null)
                throw IllegalStateException("Missing audio file name after download");
            val expectedFile = File(audioFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Audio file missing after download");
            if(expectedFile.length() != audioFileSize)
                throw IllegalStateException("Expected size [${audioFileSize}], but found ${expectedFile.length()}");
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
        val localVideoSource = videoFilePath?.let { LocalVideoSource.fromSource(videoSource!!, it, videoFileSize ?: 0) };
        val localAudioSource = audioFilePath?.let { LocalAudioSource.fromSource(audioSource!!, it, audioFileSize ?: 0) };
        val localSubtitleSource = subtitleFilePath?.let { LocalSubtitleSource.fromSource(subtitleSource!!, it) };

        if(localVideoSource != null && videoSource != null && videoSource is IStreamMetaDataSource)
            localVideoSource.streamMetaData = (videoSource as IStreamMetaDataSource).streamMetaData;

        if(localAudioSource != null && audioSource != null && audioSource is IStreamMetaDataSource)
            localAudioSource.streamMetaData = (audioSource as IStreamMetaDataSource).streamMetaData;

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
            val newVideo = VideoLocal(videoDetails!!);
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

        fun videoContainerToExtension(container: String): String? {
            if (container.contains("video/mp4"))
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
}