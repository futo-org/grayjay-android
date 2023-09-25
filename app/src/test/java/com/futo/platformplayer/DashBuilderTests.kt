package com.futo.platformplayer

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.builders.DashBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.xml.sax.InputSource
import org.xmlunit.builder.DiffBuilder
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory


class DashBuilderTest {
    private lateinit var videoSource: IVideoSource
    private lateinit var audioSource: IAudioSource
    private lateinit var subtitleSource: ISubtitleSource
    private val streamMetaData = StreamMetaData(0, 1000, 1001, 2000)

    @Before
    fun setup() {
        videoSource = mock(IVideoSource::class.java, withSettings().extraInterfaces(IStreamMetaDataSource::class.java))
        audioSource = mock(IAudioSource::class.java, withSettings().extraInterfaces(IStreamMetaDataSource::class.java))
        subtitleSource = mock(ISubtitleSource::class.java)

        `when`(videoSource.duration).thenReturn(10000L)
        `when`(videoSource.container).thenReturn("video/mp4")
        `when`(videoSource.codec).thenReturn("h264")
        `when`((videoSource as IStreamMetaDataSource).streamMetaData).thenReturn(streamMetaData)

        `when`(audioSource.duration).thenReturn(10000L)
        `when`(audioSource.container).thenReturn("audio/mp4")
        `when`(audioSource.codec).thenReturn("aac")
        `when`((audioSource as IStreamMetaDataSource).streamMetaData).thenReturn(streamMetaData)

        `when`(subtitleSource.format).thenReturn("text/vtt")
    }

    @Test
    fun testGenerateOnDemandDash() {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val dashManifest = DashBuilder.generateOnDemandDash(videoSource, "videoUrl", audioSource, "audioUrl", subtitleSource, "subtitleUrl")
        val doc = docBuilder.parse(InputSource(StringReader(dashManifest)))

        val expectedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="urn:mpeg:dash:schema:mpd:2011" xsi:schemaLocation="urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd" type="static" mediaPresentationDuration="PT10000S" minBufferTime="PT2S" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
               <Period>
                  <AdaptationSet mimeType="audio/mp4" codecs="aac" subsegmentAlignment="true" subsegmentStartsWithSAP="1">
                     <Representation mimeType="audio/mp4" codecs="aac" startWithSAP="1" bandwidth="100000" id="1">
                        <BaseURL>audioUrl</BaseURL>
                        <SegmentBase indexRange="1001-2000">
                           <Initialization sourceURL="audioUrl" range="0-1000"/>
                        </SegmentBase>
                     </Representation>
                  </AdaptationSet>
                  <AdaptationSet mimeType="text/vtt" lang="en" default="true">
                     <Representation mimeType="text/vtt" startWithSAP="1" bandwidth="1000" id="1">
                        <BaseURL>subtitleUrl</BaseURL>
                     </Representation>
                  </AdaptationSet>
                  <AdaptationSet mimeType="video/mp4" codecs="h264" subsegmentAlignment="true" subsegmentStartsWithSAP="1">
                     <Representation mimeType="video/mp4" codecs="h264" width="0" height="0" startWithSAP="1" bandwidth="100000" id="1">
                        <BaseURL>videoUrl</BaseURL>
                        <SegmentBase indexRange="1001-2000">
                           <Initialization sourceURL="videoUrl" range="0-1000"/>
                        </SegmentBase>
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