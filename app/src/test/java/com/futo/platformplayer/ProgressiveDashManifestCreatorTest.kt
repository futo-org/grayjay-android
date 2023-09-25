package com.futo.platformplayer

import com.futo.platformplayer.helpers.ProgressiveDashManifestCreator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.xml.sax.InputSource
import org.xmlunit.builder.DiffBuilder
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class ProgressiveDashManifestCreatorTest {
    private val defaultVideoUrl = "http://test.com/video.mp4"
    private val defaultAudioUrl = "http://test.com/audio.mp3"
    private val defaultDuration = 60000L // 1 minute
    private val defaultMimeType = "video/mp4"
    private val defaultId = 1
    private val defaultCodec = "avc1.64001F"
    private val defaultBitrate = 3000000 // 3Mbps
    private val defaultWidth = 1920
    private val defaultHeight = 1080
    private val defaultFps = 30
    private val defaultIndexStart = 0
    private val defaultIndexEnd = 10000
    private val defaultInitStart = 0
    private val defaultInitEnd = 1000

    @Before
    fun setUp() {
        ProgressiveDashManifestCreator.clearCache();
    }

    @Test
    fun testFromVideoProgressiveStreamingUrl() {
        val manifest = ProgressiveDashManifestCreator.fromVideoProgressiveStreamingUrl(
            defaultVideoUrl,
            defaultDuration,
            defaultMimeType,
            defaultId,
            defaultCodec,
            defaultBitrate,
            defaultWidth,
            defaultHeight,
            defaultFps,
            defaultIndexStart,
            defaultIndexEnd,
            defaultInitStart,
            defaultInitEnd
        )

        // Check that the manifest is not null or empty.
        assertNotNull(manifest)
        assertFalse(manifest.isEmpty())

        // Validate the contents of the manifest.
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val manifestDoc = docBuilder.parse(manifest.byteInputStream())

        // Check the root element.
        val mpdElement = manifestDoc.documentElement
        assertEquals("MPD", mpdElement.tagName)

        // If you have an expected XML string, you can compare the whole document using xmlunit.
        val expectedXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <MPD xmlns="urn:mpeg:DASH:schema:MPD:2011" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" mediaPresentationDuration="PT60.000S" minBufferTime="PT1.500S" profiles="urn:mpeg:dash:profile:full:2011" type="static" xsi:schemaLocation="urn:mpeg:DASH:schema:MPD:2011 DASH-MPD.xsd">
                <Period>
                    <AdaptationSet id="0" mimeType="video/mp4" subsegmentAlignment="true">
                        <Role schemeIdUri="urn:mpeg:DASH:role:2011" value="main"/>
                        <Representation bandwidth="3000000" codecs="avc1.64001F" frameRate="30" height="1080" id="1" maxPlayoutRate="1" startWithSAP="1" width="1920">
                            <BaseURL>http://test.com/video.mp4</BaseURL>
                            <SegmentBase indexRange="0-10000">
                                <Initialization range="0-1000"/>
                            </SegmentBase>
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val expectedDoc = docBuilder.parse(InputSource(StringReader(expectedXml)))
        val diff = DiffBuilder.compare(expectedDoc).withTest(manifestDoc).ignoreWhitespace().build()

        assertFalse("Manifest does not match expected XML:\n${diff}", diff.hasDifferences())
    }

    @Test
    fun testFromAudioProgressiveStreamingUrl() {
        val manifest = ProgressiveDashManifestCreator.fromAudioProgressiveStreamingUrl(
            defaultAudioUrl,
            defaultDuration,
            defaultMimeType,
            2, // Assuming stereo audio.
            defaultId,
            defaultCodec,
            defaultBitrate,
            44100, // Assuming CD quality audio.
            defaultIndexStart,
            defaultIndexEnd,
            defaultInitStart,
            defaultInitEnd
        )

        // Check that the manifest is not null or empty.
        assertNotNull(manifest)
        assertFalse(manifest.isEmpty())

        // Validate the contents of the manifest.
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val manifestDoc = docBuilder.parse(manifest.byteInputStream())

        // Check the root element.
        val mpdElement = manifestDoc.documentElement
        assertEquals("MPD", mpdElement.tagName)

        // If you have an expected XML string, you can compare the whole document using xmlunit.
        val expectedXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <MPD xmlns="urn:mpeg:DASH:schema:MPD:2011" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" mediaPresentationDuration="PT60.000S" minBufferTime="PT1.500S" profiles="urn:mpeg:dash:profile:full:2011" type="static" xsi:schemaLocation="urn:mpeg:DASH:schema:MPD:2011 DASH-MPD.xsd">
                <Period>
                    <AdaptationSet id="0" mimeType="video/mp4" subsegmentAlignment="true">
                        <Role schemeIdUri="urn:mpeg:DASH:role:2011" value="main"/>
                        <Representation bandwidth="3000000" codecs="avc1.64001F" id="1" maxPlayoutRate="1" startWithSAP="1">
                            <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
                            <BaseURL>http://test.com/audio.mp3</BaseURL>
                            <SegmentBase indexRange="0-10000">
                                <Initialization range="0-1000"/>
                            </SegmentBase>
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val expectedDoc = docBuilder.parse(InputSource(StringReader(expectedXml)))
        val diff = DiffBuilder.compare(expectedDoc).withTest(manifestDoc).ignoreWhitespace().build()

        assertFalse("Manifest does not match expected XML:\n${diff}", diff.hasDifferences())
    }
}
