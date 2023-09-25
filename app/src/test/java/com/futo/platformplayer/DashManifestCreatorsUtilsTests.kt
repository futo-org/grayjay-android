package com.futo.platformplayer

import com.futo.platformplayer.helpers.DashManifestCreatorsUtils
import org.junit.Assert
import org.junit.Test
import org.xml.sax.InputSource
import org.xmlunit.builder.DiffBuilder
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory


class DashManifestCreatorsUtilsTest {
    @Test
    fun testGenerateVideoDocumentAndDoCommonElementsGeneration() {
        val doc = DashManifestCreatorsUtils.generateVideoDocumentAndDoCommonElementsGeneration(10000L, "video/mp4", 1, "h264", 2500, 1280, 720, 30)
        val mpdElement = doc.documentElement
        Assert.assertEquals("MPD", mpdElement.tagName)

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val expectedXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <MPD xmlns="urn:mpeg:DASH:schema:MPD:2011" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                mediaPresentationDuration="PT10.000S" minBufferTime="PT1.500S"
                profiles="urn:mpeg:dash:profile:full:2011" type="static"
                xsi:schemaLocation="urn:mpeg:DASH:schema:MPD:2011 DASH-MPD.xsd">
                <Period>
                    <AdaptationSet id="0" mimeType="video/mp4" subsegmentAlignment="true">
                        <Role schemeIdUri="urn:mpeg:DASH:role:2011" value="main" />
                        <Representation bandwidth="2500" codecs="h264" frameRate="30" height="720" id="1"
                            maxPlayoutRate="1" startWithSAP="1" width="1280" />
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val expectedDoc = docBuilder.parse(InputSource(StringReader(expectedXml)))
        val diff = DiffBuilder.compare(expectedDoc).withTest(doc).ignoreWhitespace().build()

        Assert.assertFalse("Doc does not match expected XML:\n${diff}", diff.hasDifferences())
    }

    @Test
    fun testGenerateAudioDocumentAndDoCommonElementsGeneration() {
        val doc = DashManifestCreatorsUtils.generateAudioDocumentAndDoCommonElementsGeneration(10000L, "audio/mp4", 2, 1, "aac", 128, 44100)
        val mpdElement = doc.documentElement
        Assert.assertEquals("MPD", mpdElement.tagName)

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val expectedXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <MPD xmlns="urn:mpeg:DASH:schema:MPD:2011" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                mediaPresentationDuration="PT10.000S" minBufferTime="PT1.500S"
                profiles="urn:mpeg:dash:profile:full:2011" type="static"
                xsi:schemaLocation="urn:mpeg:DASH:schema:MPD:2011 DASH-MPD.xsd">
                <Period>
                    <AdaptationSet id="0" mimeType="audio/mp4" subsegmentAlignment="true">
                        <Role schemeIdUri="urn:mpeg:DASH:role:2011" value="main" />
                        <Representation bandwidth="128" codecs="aac" id="1" maxPlayoutRate="1" startWithSAP="1">
                            <AudioChannelConfiguration
                                schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2" />
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val expectedDoc = docBuilder.parse(InputSource(StringReader(expectedXml)))
        val diff = DiffBuilder.compare(expectedDoc).withTest(doc).ignoreWhitespace().build()

        Assert.assertFalse("Doc does not match expected XML:\n${diff}", diff.hasDifferences())
    }
}
