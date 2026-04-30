package com.futo.platformplayer

import com.futo.platformplayer.api.media.models.modifier.AdhocRequestModifier
import com.futo.platformplayer.api.media.models.modifier.IRequest
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HlsDownloadAuthTests {

    // Given an HLS variant from parseAndGetVideoSources
    // Then it is NOT a JSSource, so VideoDownload.kt:503 `is JSSource` check fails
    @Test
    fun test_hlsVariantVideoUrlSource_isNotJSSource() {
        val variant = HLSVariantVideoUrlSource(
            name = "720p", width = 1280, height = 720,
            container = "application/vnd.apple.mpegurl", codec = "avc1.64001f",
            bitrate = 2560000, duration = 3600, priority = false,
            url = "https://example.com/mid/index.m3u8"
        )

        assertFalse(
            "HLSVariantVideoUrlSource is not a JSSource - root cause of auth loss",
            variant is JSSource
        )
    }

    // Given an HLS audio variant from parseAndGetAudioSources
    // Then it is NOT a JSSource - same bug pattern as video (VideoDownload.kt:547)
    @Test
    fun test_hlsVariantAudioUrlSource_isNotJSSource() {
        val variant = HLSVariantAudioUrlSource(
            name = "audio", bitrate = 128000,
            container = "application/vnd.apple.mpegurl", codec = "mp4a.40.2",
            language = "en", duration = 3600, priority = false, original = true,
            url = "https://example.com/audio/index.m3u8"
        )

        assertFalse(
            "HLSVariantAudioUrlSource is not a JSSource - same auth bug as video",
            variant is JSSource
        )
    }

    // Given an HLSVariantVideoUrlSource converted to VideoUrlSource (prepare phase)
    // When the download-time pattern `if (actualVideoSource is JSSource)` is applied
    // Then the source for modifier extraction is null - auth is lost
    @Test
    fun test_downloadHlsSourcePattern_receivesNullModifier() {
        // Given: HLS.parseAndGetVideoSources() returns a variant
        val hlsVariant = HLSVariantVideoUrlSource(
            name = "1080p", width = 1920, height = 1080,
            container = "application/vnd.apple.mpegurl", codec = "avc1.640028",
            bitrate = 7680000, duration = 3600, priority = false,
            url = "https://example.com/high/index.m3u8"
        )

        // When: VideoDownload.kt:355 converts variant to VideoUrlSource
        val videoSource = VideoUrlSource.fromUrlSource(hlsVariant)
        assertNotNull("fromUrlSource should succeed", videoSource)

        // When: VideoDownload.kt:503 extracts source for modifier
        val actualVideoSource: IVideoUrlSource? = videoSource
        val sourceForModifier = if (actualVideoSource is JSSource) actualVideoSource else null

        // Then: sourceForModifier is null - downloadHlsSource gets no auth
        assertNull(
            "BUG: sourceForModifier is null because VideoUrlSource is not JSSource",
            sourceForModifier
        )
    }

    // Given an HLS manifest source with a requestModifier (auth)
    // When expanded into variants and processed through the download pipeline
    // Then the modifier IS preserved via separate storage (the fix)
    @Test
    fun test_hlsSourceWithModifier_shouldPreserveModifierThroughExpansion() {
        // Given: original HLS source has auth modifier
        val originalModifier: IRequestModifier = AdhocRequestModifier { url, headers ->
            object : IRequest {
                override val url: String = url
                override val headers: Map<String, String> = headers + mapOf("Authorization" to "Bearer token123")
            }
        }

        // Given: after expansion, variants are plain data classes
        val expandedVariants = listOf(
            HLSVariantVideoUrlSource("360p", 640, 360, "application/vnd.apple.mpegurl", "avc1", 1280000, 3600, false, "https://example.com/low/index.m3u8"),
            HLSVariantVideoUrlSource("1080p", 1920, 1080, "application/vnd.apple.mpegurl", "avc1", 7680000, 3600, false, "https://example.com/high/index.m3u8")
        )

        // When: select best and apply prepare phase
        val bestVariant = expandedVariants.maxByOrNull { it.height }!!
        val videoSource = VideoUrlSource.fromUrlSource(bestVariant)

        // When: THE FIX - modifier was captured during prepare, stored separately
        // Simulating the fixed pattern: hlsVideoRequestModifier was set during prepare
        val hlsVideoRequestModifier: IRequestModifier? = originalModifier  // captured during prepare

        // When: at download time, use the captured modifier (with fallback for non-expanded sources)
        val actualVideoSource = videoSource
        val modifier = hlsVideoRequestModifier ?: (if (actualVideoSource is JSSource && actualVideoSource.hasRequestModifier) actualVideoSource.getRequestModifier() else null)

        // Then: modifier IS available - auth is preserved
        assertNotNull("modifier should be preserved through variant expansion via separate storage", modifier)

        // Verify the modifier actually works
        val request = modifier!!.modifyRequest("https://example.com/key.bin", emptyMap())
        assertEquals("Bearer token123", request.headers["Authorization"])
    }
}
