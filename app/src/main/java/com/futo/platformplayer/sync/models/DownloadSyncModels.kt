package com.futo.platformplayer.sync.models

import com.futo.platformplayer.api.media.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Downloads sync wire format, version 1. All sub-ops use opcode DATA (4).
 * Stock Grayjay ignores unknown sub-ops, so the format is backward compatible.
 *
 *  240 downloadIndex         JSON SyncDownloadIndexPackage (gzip). Sent in syncStateExchange, on change, and as a reply to 241.
 *  241 downloadIndexRequest  JSON SyncDownloadIndexRequest. Asks the peer to send 240.
 *  242 fileChunkRequest      JSON SyncFileChunkRequest. Asks for [offset, offset+length) of one file. length <= MAX_CHUNK.
 *  243 fileChunkResponse     Binary, no gzip: [u32 LE header length][UTF-8 JSON SyncFileChunkHeader][raw bytes].
 *
 * JSON is camelCase. Unknown fields must be ignored. Copy must only run on a Direct (LAN) link:
 * the relay closes a connection after 20 MB.
 */

@Serializable
class SyncDownloadIndexPackage(
    val version: Int = 1,
    val deviceName: String? = null,
    /** Unix time in ms when the sender made this index. */
    val generatedAt: Long = 0,
    val items: List<SyncDownloadItem> = listOf()
);

@Serializable
class SyncDownloadIndexRequest(val version: Int = 1);

@Serializable
class SyncDownloadItem(
    /** Stable key of the item on the sender: "<platform>:<videoId>" (else the URL). */
    val itemId: String,
    val platform: String = "",
    val videoId: String? = null,
    val pluginId: String? = null,
    val url: String = "",
    val name: String = "",
    val author: String = "",
    val thumbnail: String? = null,
    /** Seconds. */
    val duration: Long = 0,
    /** Unix time in ms. */
    val downloadDate: Long? = null,
    /** False if the sender cannot read the files now (for example, SD card out). */
    val available: Boolean = true,
    val files: List<SyncDownloadFile> = listOf(),
    /** Optional full video details in the Android SerializedPlatformVideoDetails form. Needed to add a copy on Android. */
    val details: JsonElement? = null
) {
    val totalSize: Long get() = files.sumOf { it.fileSize };
    /** Quality key to tell if this device has the same download: "<itemId>|<max height>". */
    val qualityKey: String get() = qualityKey(itemId, files.filter { it.kind == KIND_VIDEO }.maxOfOrNull { it.height } ?: 0);

    companion object {
        const val KIND_VIDEO = "video";
        const val KIND_AUDIO = "audio";
        const val KIND_SUBTITLE = "subtitle";

        fun qualityKey(itemId: String, height: Int): String = "$itemId|$height";
    }
}

@Serializable
class SyncDownloadFile(
    /** "video", "audio" or "subtitle". */
    val kind: String,
    /** Index in the list of that kind. */
    val index: Int,
    val fileName: String,
    val fileSize: Long,
    val name: String = "",
    val container: String = "",
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val language: String? = null,
    val format: String? = null
);

@Serializable
class SyncFileChunkRequest(
    val reqId: Long,
    val itemId: String,
    val kind: String,
    val index: Int,
    val offset: Long,
    val length: Int
);

@Serializable
class SyncFileChunkHeader(
    val reqId: Long,
    /** STATUS_OK, STATUS_NOT_FOUND or STATUS_ERROR. */
    val status: Int,
    val offset: Long = 0,
    /** Full size of the file on the sender. */
    val totalSize: Long = 0,
    val message: String? = null
) {
    companion object {
        const val STATUS_OK = 0;
        const val STATUS_NOT_FOUND = 1;
        const val STATUS_ERROR = 2;
    }
}

object DownloadSyncWire {
    /** Largest chunk. Fits in one sync packet (max 65519 bytes) with the header. */
    const val MAX_CHUNK = 60000;

    fun encodeChunkResponse(header: SyncFileChunkHeader, bytes: ByteArray, length: Int = bytes.size): ByteArray {
        val headerBytes = Serializer.json.encodeToString(header).toByteArray(Charsets.UTF_8);
        val buffer = ByteBuffer.allocate(4 + headerBytes.size + length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(headerBytes.size);
        buffer.put(headerBytes);
        buffer.put(bytes, 0, length);
        return buffer.array();
    }

    /** Returns the header and the data bytes. */
    fun decodeChunkResponse(data: ByteArray): Pair<SyncFileChunkHeader, ByteArray> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        val headerLength = buffer.getInt();
        if (headerLength < 0 || headerLength > data.size - 4)
            throw IllegalArgumentException("Bad chunk header length $headerLength");
        val header = Serializer.json.decodeFromString<SyncFileChunkHeader>(String(data, 4, headerLength, Charsets.UTF_8));
        val bytes = data.copyOfRange(4 + headerLength, data.size);
        return Pair(header, bytes);
    }

    /** Chunk requests for the rest of a file: from [start] to [size], [chunk] bytes each. */
    fun planChunks(start: Long, size: Long, chunk: Int = MAX_CHUNK): List<Pair<Long, Int>> {
        val result = arrayListOf<Pair<Long, Int>>();
        var offset = start;
        while (offset < size) {
            val length = minOf(chunk.toLong(), size - offset).toInt();
            result.add(Pair(offset, length));
            offset += length;
        }
        return result;
    }
}
