package com.futo.platformplayer.states

import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.helpers.FileHelper.Companion.sanitizeFileName
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.Language
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.internal.LinkType
import com.futo.platformplayer.sync.internal.Opcode
import com.futo.platformplayer.sync.internal.SyncSession
import com.futo.platformplayer.sync.models.DownloadSyncWire
import com.futo.platformplayer.sync.models.SyncDownloadFile
import com.futo.platformplayer.sync.models.SyncDownloadIndexPackage
import com.futo.platformplayer.sync.models.SyncDownloadIndexRequest
import com.futo.platformplayer.sync.models.SyncDownloadItem
import com.futo.platformplayer.sync.models.SyncFileChunkHeader
import com.futo.platformplayer.sync.models.SyncFileChunkRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads sync (separate from the main Sync): see the download lists of paired devices,
 * and copy items from them on the same Wi-Fi. Pull only; delete is local.
 * Wire format: sync/models/DownloadSyncModels.kt.
 */
class StateDownloadSync {
    class RemoteIndex(val publicKey: String, val index: SyncDownloadIndexPackage, val receivedAt: Long);

    private val _remote = ConcurrentHashMap<String, RemoteIndex>();
    private val _pending = ConcurrentHashMap<Long, CompletableDeferred<Pair<SyncFileChunkHeader, ByteArray>>>();
    private val _nextReqId = AtomicLong(System.currentTimeMillis());
    private var _broadcastScheduled = false;
    private var _started = false;

    /** Emits the public key of a device whose list changed. */
    val onRemoteIndexChanged = Event1<String>();

    private val storeDir: File? get() = StateApp.instance.contextOrNull?.filesDir?.let { File(it, "download_sync_indexes").apply { mkdirs() } };

    /** Loads the last known lists and sends this device's list when downloads change. */
    fun start() {
        if (_started)
            return;
        _started = true;
        loadStoredIndexes();
        StateDownloads.instance.onDownloadedChanged.subscribe(this) { scheduleBroadcast() };
    }

    // ---------- Index ----------

    fun itemIdOf(item: VideoLocal): String = item.id.value?.let { "${item.id.platform}:$it" } ?: item.url;

    fun buildIndex(): SyncDownloadIndexPackage {
        val downloads = StateDownloads.instance;
        val items = downloads.getDownloadedVideos().map { item ->
            val files = arrayListOf<SyncDownloadFile>();
            item.videoSource.forEachIndexed { i, s ->
                files.add(SyncDownloadFile(SyncDownloadItem.KIND_VIDEO, i, File(s.filePath).name, s.fileSize, s.name, s.container, s.codec, s.width, s.height, s.bitrate));
            };
            item.audioSource.forEachIndexed { i, s ->
                files.add(SyncDownloadFile(SyncDownloadItem.KIND_AUDIO, i, File(s.filePath).name, s.fileSize, s.name, s.container, s.codec, 0, 0, s.bitrate, s.language));
            };
            item.subtitlesSources.forEachIndexed { i, s ->
                val file = downloads.resolveFile(s.filePath);
                files.add(SyncDownloadFile(SyncDownloadItem.KIND_SUBTITLE, i, file.name, if (file.isFile) file.length() else 0, s.name, language = s.language, format = s.format));
            };
            SyncDownloadItem(
                itemId = itemIdOf(item),
                platform = item.id.platform,
                videoId = item.id.value,
                pluginId = item.id.pluginId,
                url = item.url,
                name = item.name,
                author = item.author.name,
                thumbnail = item.thumbnails.getHQThumbnail(),
                duration = item.duration,
                downloadDate = item.downloadDate?.toInstant()?.toEpochMilli(),
                available = !downloads.isMissing(item),
                files = files,
                details = Serializer.json.encodeToJsonElement(item.videoSerialized)
            );
        };
        return SyncDownloadIndexPackage(1, "${android.os.Build.MANUFACTURER}-${android.os.Build.MODEL}", System.currentTimeMillis(), items);
    }

    fun sendIndex(session: SyncSession) {
        session.sendData(GJSyncOpcodes.downloadIndex, Serializer.json.encodeToString(buildIndex()));
    }

    fun requestIndex(publicKey: String) {
        val session = StateSync.instance.getSession(publicKey) ?: return;
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                session.sendData(GJSyncOpcodes.downloadIndexRequest, Serializer.json.encodeToString(SyncDownloadIndexRequest()));
            } catch (ex: Throwable) {
                Logger.w(TAG, "Failed to request download list from $publicKey", ex);
            }
        };
    }

    private fun scheduleBroadcast() {
        synchronized(this) {
            if (_broadcastScheduled)
                return;
            _broadcastScheduled = true;
        }
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            delay(5000);
            synchronized(this@StateDownloadSync) { _broadcastScheduled = false; };
            try {
                val json = Serializer.json.encodeToString(buildIndex());
                for (session in StateSync.instance.getAuthorizedSessions())
                    session.sendData(GJSyncOpcodes.downloadIndex, json);
            } catch (ex: Throwable) {
                Logger.w(TAG, "Failed to send download list", ex);
            }
        };
    }

    fun getRemoteIndexes(): List<RemoteIndex> = _remote.values.sortedBy { it.publicKey };
    fun getRemoteIndex(publicKey: String): RemoteIndex? = _remote[publicKey];

    /** Quality keys of local items, to show "On this phone". */
    fun getLocalQualityKeys(): Set<String> = StateDownloads.instance.getDownloadedVideos()
        .map { SyncDownloadItem.qualityKey(itemIdOf(it), it.videoSource.maxOfOrNull { s -> s.height } ?: 0) }.toHashSet();

    fun isOnline(publicKey: String): Boolean = StateSync.instance.getSession(publicKey)?.connected == true;
    fun isSameNetwork(publicKey: String): Boolean = StateSync.instance.getSession(publicKey)?.let { it.connected && it.linkType == LinkType.Direct } == true;

    private fun storeIndex(remote: RemoteIndex) {
        try {
            val dir = storeDir ?: return;
            File(dir, remote.publicKey.sanitizeFileName() + ".json").writeText(Serializer.json.encodeToString(StoredIndex(remote.receivedAt, remote.index)));
        } catch (ex: Throwable) {
            Logger.w(TAG, "Failed to store download list", ex);
        }
    }

    private fun loadStoredIndexes() {
        val dir = storeDir ?: return;
        for (publicKey in StateSync.instance.getAll()) {
            val file = File(dir, publicKey.sanitizeFileName() + ".json");
            if (!file.isFile)
                continue;
            try {
                val stored = Serializer.json.decodeFromString<StoredIndex>(file.readText());
                _remote[publicKey] = RemoteIndex(publicKey, stored.index, stored.receivedAt);
            } catch (ex: Throwable) {
                Logger.w(TAG, "Failed to read stored download list of $publicKey", ex);
            }
        }
    }

    @kotlinx.serialization.Serializable
    private class StoredIndex(val receivedAt: Long, val index: SyncDownloadIndexPackage);

    // ---------- Sync data handler (called from StateSync.handleData) ----------

    fun handleData(session: SyncSession, subOpcode: UByte, data: ByteBuffer): Boolean {
        when (subOpcode) {
            GJSyncOpcodes.downloadIndex -> {
                val index = Serializer.json.decodeFromString<SyncDownloadIndexPackage>(readString(data));
                val remote = RemoteIndex(session.remotePublicKey, index, System.currentTimeMillis());
                _remote[session.remotePublicKey] = remote;
                storeIndex(remote);
                Logger.i(TAG, "Received download list (${index.items.size} items) from ${session.remotePublicKey}");
                onRemoteIndexChanged.emit(session.remotePublicKey);
            }
            GJSyncOpcodes.downloadIndexRequest -> sendIndex(session);
            GJSyncOpcodes.fileChunkRequest -> serveChunk(session, Serializer.json.decodeFromString<SyncFileChunkRequest>(readString(data)));
            GJSyncOpcodes.fileChunkResponse -> {
                val bytes = ByteArray(data.remaining());
                data.get(bytes);
                val response = DownloadSyncWire.decodeChunkResponse(bytes);
                _pending.remove(response.first.reqId)?.complete(response);
            }
            else -> return false;
        }
        return true;
    }

    private fun readString(data: ByteBuffer): String {
        val bytes = ByteArray(data.remaining());
        data.get(bytes);
        return String(bytes, Charsets.UTF_8);
    }

    private fun serveChunk(session: SyncSession, request: SyncFileChunkRequest) {
        fun reply(header: SyncFileChunkHeader, bytes: ByteArray = ByteArray(0), length: Int = 0) =
            session.send(Opcode.DATA.value, GJSyncOpcodes.fileChunkResponse, ByteBuffer.wrap(DownloadSyncWire.encodeChunkResponse(header, bytes, length)), null);

        if (session.linkType != LinkType.Direct) {
            reply(SyncFileChunkHeader(request.reqId, SyncFileChunkHeader.STATUS_ERROR, message = "Copy works only on the same Wi-Fi"));
            return;
        }
        val file = findLocalFile(request.itemId, request.kind, request.index);
        if (file == null || !file.isFile) {
            reply(SyncFileChunkHeader(request.reqId, SyncFileChunkHeader.STATUS_NOT_FOUND, message = "File not found"));
            return;
        }
        try {
            val length = request.length.coerceIn(0, DownloadSyncWire.MAX_CHUNK);
            val buffer = ByteArray(length);
            var read = 0;
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(request.offset);
                while (read < length) {
                    val n = raf.read(buffer, read, length - read);
                    if (n < 0) break;
                    read += n;
                }
            };
            reply(SyncFileChunkHeader(request.reqId, SyncFileChunkHeader.STATUS_OK, request.offset, file.length()), buffer, read);
        } catch (ex: Throwable) {
            reply(SyncFileChunkHeader(request.reqId, SyncFileChunkHeader.STATUS_ERROR, message = ex.message));
        }
    }

    private fun findLocalFile(itemId: String, kind: String, index: Int): File? {
        val item = StateDownloads.instance.getDownloadedVideos().firstOrNull { itemIdOf(it) == itemId } ?: return null;
        val path = when (kind) {
            SyncDownloadItem.KIND_VIDEO -> item.videoSource.getOrNull(index)?.filePath;
            SyncDownloadItem.KIND_AUDIO -> item.audioSource.getOrNull(index)?.filePath;
            SyncDownloadItem.KIND_SUBTITLE -> item.subtitlesSources.getOrNull(index)?.filePath;
            else -> null;
        } ?: return null;
        return StateDownloads.instance.resolveFile(path);
    }

    // ---------- Copy to this device ----------

    class CopyResult(val copied: Int, val total: Int, val error: Throwable?);

    /**
     * Copies [items] from the device [publicKey] into the download folder. Resumes from ".gjpart" files.
     * [onProgress] gets (item index, item count, copied bytes, total bytes).
     */
    suspend fun copyFromDevice(publicKey: String, items: List<SyncDownloadItem>, onProgress: (Int, Int, Long, Long) -> Unit, isCancelled: () -> Boolean): CopyResult {
        var copied = 0;
        try {
            if (!isSameNetwork(publicKey))
                throw IllegalStateException("Join the same Wi-Fi as the other device to copy.");
            val session = StateSync.instance.getSession(publicKey) ?: throw IllegalStateException("Device is not connected");
            val downloads = StateDownloads.instance;
            val dir = downloads.prepareDownloadsDirectory();
            val totalBytes = items.sumOf { it.totalSize }.coerceAtLeast(1);
            downloads.checkFreeSpace(dir, items.sumOf { it.totalSize });

            var doneBytes = 0L;
            for ((index, item) in items.withIndex()) {
                if (item.details == null)
                    throw IllegalStateException("\"${item.name}\" has no video details. Update Grayjay on the other device.");
                val localFiles = HashMap<SyncDownloadFile, File>();
                for (file in item.files) {
                    val target = copyFile(session, item, file, dir, { bytes ->
                        doneBytes += bytes;
                        onProgress(index, items.size, doneBytes, totalBytes);
                    }, isCancelled);
                    localFiles[file] = target;
                }
                registerCopy(item, localFiles);
                copied++;
            }
            return CopyResult(copied, items.size, null);
        } catch (ex: Throwable) {
            Logger.e(TAG, "Copy from $publicKey stopped after $copied of ${items.size}", ex);
            return CopyResult(copied, items.size, ex);
        }
    }

    private suspend fun copyFile(session: SyncSession, item: SyncDownloadItem, file: SyncDownloadFile, dir: File, onBytes: (Long) -> Unit, isCancelled: () -> Boolean): File {
        val name = file.fileName.sanitizeFileName(true).ifEmpty { "${item.itemId}-${file.kind}-${file.index}".sanitizeFileName() };
        val target = File(dir, name);
        if (target.isFile && target.length() == file.fileSize) {
            onBytes(file.fileSize);
            return target;
        }
        val part = File(dir, name + PART_SUFFIX);
        val start = if (part.isFile && part.length() <= file.fileSize) part.length() else { part.delete(); 0L };
        onBytes(start);

        RandomAccessFile(part, "rw").use { raf ->
            val chunks = DownloadSyncWire.planChunks(start, file.fileSize);
            val inFlight = ArrayDeque<Pair<Pair<Long, Int>, Deferred<Pair<SyncFileChunkHeader, ByteArray>>>>();
            var next = 0;
            while (next < chunks.size || inFlight.isNotEmpty()) {
                if (isCancelled())
                    throw InterruptedException("Copy stopped");
                while (next < chunks.size && inFlight.size < WINDOW) {
                    val chunk = chunks[next++];
                    inFlight.addLast(Pair(chunk, requestChunk(session, item.itemId, file, chunk.first, chunk.second)));
                }
                val (chunk, deferred) = inFlight.removeFirst();
                val (header, bytes) = awaitChunk(session, item.itemId, file, chunk, deferred);
                if (header.status != SyncFileChunkHeader.STATUS_OK)
                    throw IllegalStateException(header.message ?: "Other device could not send the file");
                if (bytes.size != chunk.second)
                    throw IllegalStateException("Short chunk at ${chunk.first}: ${bytes.size} of ${chunk.second} bytes");
                raf.seek(chunk.first);
                raf.write(bytes);
                onBytes(bytes.size.toLong());
            }
            raf.fd.sync();
        };
        if (part.length() != file.fileSize)
            throw IllegalStateException("Size check failed for $name: ${part.length()} of ${file.fileSize} bytes");
        if (target.exists())
            target.delete();
        if (!part.renameTo(target))
            throw IllegalStateException("Cannot rename ${part.name}");
        return target;
    }

    private fun requestChunk(session: SyncSession, itemId: String, file: SyncDownloadFile, offset: Long, length: Int): Deferred<Pair<SyncFileChunkHeader, ByteArray>> {
        val reqId = _nextReqId.incrementAndGet();
        val deferred = CompletableDeferred<Pair<SyncFileChunkHeader, ByteArray>>();
        _pending[reqId] = deferred;
        session.sendData(GJSyncOpcodes.fileChunkRequest, Serializer.json.encodeToString(SyncFileChunkRequest(reqId, itemId, file.kind, file.index, offset, length)));
        return deferred;
    }

    /** Waits for a chunk; on a timeout it asks again (up to 3 times). */
    private suspend fun awaitChunk(session: SyncSession, itemId: String, file: SyncDownloadFile, chunk: Pair<Long, Int>, first: Deferred<Pair<SyncFileChunkHeader, ByteArray>>): Pair<SyncFileChunkHeader, ByteArray> {
        var deferred = first;
        for (attempt in 0 until 4) {
            try {
                return withTimeout(CHUNK_TIMEOUT_MS) { deferred.await() };
            } catch (ex: kotlinx.coroutines.TimeoutCancellationException) {
                if (!session.connected || session.linkType != LinkType.Direct)
                    throw IllegalStateException("Connection to the other device was lost. Copy again to continue.");
                Logger.w(TAG, "Chunk at ${chunk.first} timed out, asking again");
                deferred = requestChunk(session, itemId, file, chunk.first, chunk.second);
            }
        }
        throw IllegalStateException("The other device did not answer. Copy again to continue.");
    }

    private fun registerCopy(item: SyncDownloadItem, files: Map<SyncDownloadFile, File>) {
        val downloads = StateDownloads.instance;
        val details = Serializer.json.decodeFromJsonElement<SerializedPlatformVideoDetails>(item.details!!);
        val existing = downloads.getCachedVideo(details.id);
        val local = existing ?: VideoLocal(details, item.downloadDate?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) } ?: OffsetDateTime.now());
        if (existing != null)
            downloads.dropMissingSources(existing);

        val videos = ArrayList(local.videoSource);
        val audios = ArrayList(local.audioSource);
        val subtitles = ArrayList(local.subtitlesSources);
        for ((f, file) in files) {
            val stored = downloads.toStoredPath(file.absolutePath);
            if (local.getFilePaths().any { downloads.resolveFile(it).absolutePath == file.absolutePath })
                continue;
            when (f.kind) {
                SyncDownloadItem.KIND_VIDEO -> videos.add(LocalVideoSource(f.name, stored, f.fileSize, f.width, f.height, item.duration, f.container, f.codec, f.bitrate));
                SyncDownloadItem.KIND_AUDIO -> audios.add(LocalAudioSource(f.name, stored, f.fileSize, f.bitrate, f.container, f.codec, f.language ?: Language.UNKNOWN));
                SyncDownloadItem.KIND_SUBTITLE -> subtitles.add(LocalSubtitleSource(f.name, f.language, f.format, stored));
            }
        }
        local.videoSource = videos;
        local.audioSource = audios;
        local.subtitlesSources = subtitles;
        downloads.saveDownloadedVideo(local);
    }

    companion object {
        const val TAG = "StateDownloadSync";
        const val PART_SUFFIX = ".gjpart";
        private const val WINDOW = 16;
        private const val CHUNK_TIMEOUT_MS = 15000L;

        private var _instance: StateDownloadSync? = null;
        val instance: StateDownloadSync
            get() {
                if (_instance == null)
                    _instance = StateDownloadSync();
                return _instance!!;
            };
    }
}
