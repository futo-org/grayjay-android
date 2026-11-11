package com.futo.platformplayer

import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.sync.models.DownloadSyncWire
import com.futo.platformplayer.sync.models.SyncDownloadFile
import com.futo.platformplayer.sync.models.SyncDownloadIndexPackage
import com.futo.platformplayer.sync.models.SyncDownloadItem
import com.futo.platformplayer.sync.models.SyncFileChunkHeader
import com.futo.platformplayer.sync.models.SyncFileChunkRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DownloadSyncWireTests {
    @Test
    fun chunkResponse_roundTrip() {
        val bytes = ByteArray(1000) { it.toByte() };
        val encoded = DownloadSyncWire.encodeChunkResponse(SyncFileChunkHeader(42, SyncFileChunkHeader.STATUS_OK, 120000, 5_000_000), bytes, 700);
        val headerLength = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN).int;
        assertTrue(String(encoded, 4, headerLength, Charsets.UTF_8).contains("\"reqId\":42"));
        val (header, data) = DownloadSyncWire.decodeChunkResponse(encoded);
        assertEquals(42L, header.reqId);
        assertEquals(120000L, header.offset);
        assertEquals(5_000_000L, header.totalSize);
        assertArrayEquals(bytes.copyOf(700), data);
    }

    @Test
    fun chunkResponse_fitsInOnePacket() {
        val encoded = DownloadSyncWire.encodeChunkResponse(SyncFileChunkHeader(Long.MAX_VALUE, 0, Long.MAX_VALUE, Long.MAX_VALUE), ByteArray(DownloadSyncWire.MAX_CHUNK));
        assertTrue(encoded.size <= 65535 - 16 - 7);
    }

    @Test
    fun planChunks_resumesFromOffset() {
        val chunks = DownloadSyncWire.planChunks(50_000, 170_001, 60_000);
        assertEquals(listOf(Pair(50_000L, 60_000), Pair(110_000L, 60_000), Pair(170_000L, 1)), chunks);
        assertEquals(0, DownloadSyncWire.planChunks(10, 10).size);
    }

    @Test
    fun index_jsonFieldsAreStable() {
        val pkg = SyncDownloadIndexPackage(1, "Pixel", 1700000000000, listOf(SyncDownloadItem(
            itemId = "YouTube:abc", platform = "YouTube", videoId = "abc", url = "https://youtube.com/watch?v=abc", name = "N", author = "A",
            duration = 60, files = listOf(SyncDownloadFile("video", 0, "abc [1280x720].mp4", 1234, height = 720), SyncDownloadFile("audio", 0, "abc.mp4a", 100)))));
        val json = Serializer.json.encodeToString(pkg);
        for (field in listOf("\"version\":1", "\"deviceName\":\"Pixel\"", "\"generatedAt\":", "\"itemId\":\"YouTube:abc\"", "\"files\":", "\"kind\":\"video\"", "\"fileName\":", "\"fileSize\":1234", "\"available\":true"))
            assertTrue(field, json.contains(field));
        val back = Serializer.json.decodeFromString<SyncDownloadIndexPackage>(json);
        assertEquals(1334L, back.items[0].totalSize);
        assertEquals("YouTube:abc|720", back.items[0].qualityKey);
        assertNull(back.items[0].details);
    }

    @Test
    fun index_ignoresUnknownFieldsFromNewerPeers() {
        val json = """{"version":2,"items":[{"itemId":"x","files":[{"kind":"video","index":0,"fileName":"f","fileSize":5,"hdr":true}],"newField":1}]}""";
        val pkg = Serializer.json.decodeFromString<SyncDownloadIndexPackage>(json);
        assertEquals(5L, pkg.items[0].totalSize);
        val req = Serializer.json.decodeFromString<SyncFileChunkRequest>("""{"reqId":1,"itemId":"x","kind":"audio","index":0,"offset":0,"length":10}""");
        assertEquals("audio", req.kind);
    }
}
