package com.futo.platformplayer.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.*
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.toHumanBitrate
import kotlinx.coroutines.*
import java.io.*
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException

@kotlinx.serialization.Serializable
class VideoExport {
    var state: State = State.QUEUED;

    var videoLocal: VideoLocal;
    var videoSource: LocalVideoSource?;
    var audioSource: LocalAudioSource?;
    var subtitleSource: LocalSubtitleSource?;

    var progress: Double = 0.0;
    var isCancelled = false;

    var error: String? = null;

    @kotlinx.serialization.Transient
    val onStateChanged = Event1<State>();
    @kotlinx.serialization.Transient
    val onProgressChanged = Event1<Double>();

    fun changeState(newState: State) {
        state = newState;
        onStateChanged.emit(newState);
    }

    constructor(videoLocal: VideoLocal, videoSource: LocalVideoSource?, audioSource: LocalAudioSource?, subtitleSource: LocalSubtitleSource?) {
        this.videoLocal = videoLocal;
        this.videoSource = videoSource;
        this.audioSource = audioSource;
        this.subtitleSource = subtitleSource;
    }

    suspend fun export(context: Context, onProgress: ((Double) -> Unit)? = null): DocumentFile = coroutineScope {
        if(isCancelled) throw CancellationException("Export got cancelled");

        val v = videoSource;
        val a = audioSource;
        val s = subtitleSource;

        var sourceCount = 0;
        if (v != null) sourceCount++;
        if (a != null) sourceCount++;
        if (s != null) sourceCount++;

        val outputFile: DocumentFile?;
        val downloadRoot = StateApp.instance.getExternalDownloadDirectory(context) ?: throw Exception("External download directory is not set");
        if (sourceCount > 1) {
            val outputFileName = toSafeFileName(videoLocal.name) + ".mp4"// + VideoDownload.videoContainerToExtension(v.container);
            val f = downloadRoot.createFile("video/mp4", outputFileName)
                ?: throw Exception("Failed to create file in external directory.");

            Logger.i(TAG, "Combining video and audio through FFMPEG.");
            val tempFile = File(context.cacheDir, "${UUID.randomUUID()}.mp4");
            try {
                combine(a?.filePath, v?.filePath, s?.filePath, tempFile.absolutePath, videoLocal.duration.toDouble()) { progress -> onProgress?.invoke(progress) };
                context.contentResolver.openOutputStream(f.uri)?.use { outputStream ->
                    copy(tempFile.absolutePath, outputStream) { progress -> onProgress?.invoke(progress) };
                }
            } finally {
                tempFile.delete();
            }
            outputFile = f;
        } else if (v != null) {
            val outputFileName = toSafeFileName(videoLocal.name) + "." + VideoDownload.videoContainerToExtension(v.container);
            val f = downloadRoot.createFile(v.container, outputFileName)
                ?: throw Exception("Failed to create file in external directory.");

            Logger.i(TAG, "Copying video.");

            context.contentResolver.openOutputStream(f.uri)?.use { outputStream ->
                copy(v.filePath, outputStream) { progress -> onProgress?.invoke(progress) };
            }

            outputFile = f;
        } else if (a != null) {
            val outputFileName = toSafeFileName(videoLocal.name) + "." + VideoDownload.audioContainerToExtension(a.container);
            val f = downloadRoot.createFile(a.container, outputFileName)
                ?: throw Exception("Failed to create file in external directory.");

            Logger.i(TAG, "Copying audio.");

            context.contentResolver.openOutputStream(f.uri)?.use { outputStream ->
                copy(a.filePath, outputStream) { progress -> onProgress?.invoke(progress) };
            }

            outputFile = f;
        } else {
            throw Exception("Cannot export when no audio or video source is set.");
        }

        onProgressChanged.emit(100.0);
        return@coroutineScope outputFile;
    }

    private fun toSafeFileName(input: String): String {
        val safeCharacters = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_')
        return input.map { if (it in safeCharacters) it else '_' }.joinToString(separator = "")
    }

    private suspend fun combine(inputPathAudio: String?, inputPathVideo: String?, inputPathSubtitles: String?, outputPath: String, duration: Double, onProgress: ((Double) -> Unit)? = null) = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            //ffmpeg -i a.mp4 -i b.m4a -scodec mov_text -i c.vtt -map 0:v -map 1:a -map 2 -c:v copy -c:a copy -c:s mov_text output.mp4

            val cmdBuilder = StringBuilder("-y")
            var counter = 0

            if (inputPathVideo != null) {
                cmdBuilder.append(" -i $inputPathVideo")
            }
            if (inputPathAudio != null) {
                cmdBuilder.append(" -i $inputPathAudio")
            }
            if (inputPathSubtitles != null) {
                val subtitleExtension = File(inputPathSubtitles).extension

                val codec = when (subtitleExtension.lowercase()) {
                    "srt" -> "mov_text"
                    "vtt" -> "webvtt"
                    else -> throw Exception("Unsupported subtitle format: $subtitleExtension")
                }

                cmdBuilder.append(" -scodec $codec -i $inputPathSubtitles")
            }

            if (inputPathVideo != null) {
                cmdBuilder.append(" -map ${counter++}:v")
            }
            if (inputPathAudio != null) {
                cmdBuilder.append(" -map ${counter++}:a")
            }

            if (inputPathSubtitles != null) {
                cmdBuilder.append(" -map ${counter++}")
            }

            if (inputPathVideo != null) {
                cmdBuilder.append(" -c:v copy")
            }
            if (inputPathAudio != null) {
                cmdBuilder.append(" -c:a copy")
            }
            if (inputPathAudio != null) {
                cmdBuilder.append(" -c:s mov_text")
            }

            cmdBuilder.append(" $outputPath")

            val cmd = cmdBuilder.toString()
            Logger.i(TAG, "Used command: $cmd");

            val statisticsCallback = StatisticsCallback { statistics ->
                val time = statistics.time.toDouble() / 1000.0
                val progressPercentage = (time / duration)
                onProgress?.invoke(progressPercentage)
            }

            val executorService = Executors.newSingleThreadExecutor()
            val session = FFmpegKit.executeAsync(cmd,
                { session ->
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        continuation.resumeWith(Result.success(Unit))
                    } else {
                        val errorMessage = if (ReturnCode.isCancel(session.returnCode)) {
                            "Command cancelled"
                        } else {
                            "Command failed with state '${session.state}' and return code ${session.returnCode}, stack trace ${session.failStackTrace}"
                        }
                        continuation.resumeWithException(RuntimeException(errorMessage))
                    }
                },
                LogCallback { Logger.v(TAG, it.message) },
                statisticsCallback,
                executorService
            )

            continuation.invokeOnCancellation {
                session.cancel()
            }
        }
    }

    private suspend fun copy(fromPath: String, outputStream: OutputStream, bufferSize: Int = 8192, onProgress: ((Double) -> Unit)? = null) {
        withContext(Dispatchers.IO) {
            var inputStream: FileInputStream? = null

            try {
                val srcFile = File(fromPath)
                if (!srcFile.exists()) {
                    throw IOException("Source file not found.")
                }

                inputStream = FileInputStream(srcFile)

                val buffer = ByteArray(bufferSize)
                val totalBytes = srcFile.length()
                var bytesCopied: Long = 0

                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead.toLong()

                    onProgress?.let {
                        withContext(Dispatchers.Main) {
                            it(bytesCopied / totalBytes.toDouble())
                        }
                    }
                }
            } catch (e: Exception) {
                throw IOException("Error occurred while copying file: ${e.message}", e)
            } finally {
                inputStream?.close()
            }
        }
    }

    fun getExportInfo() : String {
        val tokens = ArrayList<String>();
        val v = videoSource;
        if (v != null) {
            tokens.add("${v.width}x${v.height} (${v.container})");
        }

        val a = audioSource;
        if (a != null) {
            tokens.add(a.bitrate.toHumanBitrate());
        }

        return tokens.joinToString(" • ");
    }

    enum class State {
        QUEUED,
        EXPORTING,
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
        private const val TAG = "VideoExport"
    }
}