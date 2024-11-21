package com.futo.platformplayer.builders

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource

class DashBuilder : XMLBuilder {

    constructor(durationS: Long, profile: String) {
        writeXmlHeader();
        writeTag("MPD", mapOf(
            Pair("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance"),
            Pair("xmlns", "urn:mpeg:dash:schema:mpd:2011"),
            Pair("xsi:schemaLocation", "urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd"),
            Pair("type", "static"),
            Pair("mediaPresentationDuration", "PT${durationS}S"),
            Pair("minBufferTime", "PT2S"),
            Pair("profiles", profile)
        ), false);

        //Temporary...always Period wrapped
        writeTag("Period", mapOf(), false);
    }

    //AdaptationSets
    fun withAdaptationSet(parameters: Map<String, String>, writeBody: (DashBuilder)->Unit) {
        tag("AdaptationSet", parameters, {
            writeBody(it as DashBuilder);
        });
    }

    //Representation
    fun withRepresentation(id: String, parameters: Map<String, String>, writeBody: (DashBuilder)->Unit) {
        val modParas = parameters.toMutableMap();
        modParas.put("id", id);
        tag("Representation", modParas) {
           writeBody(it as DashBuilder);
        };
    }
    fun withRepresentationOnDemand(id: String, audioSource: IAudioSource, audioUrl: String) {
        if(audioSource !is IStreamMetaDataSource)
            throw NotImplementedError("Currently onDemand dash only works with IStreamMetaDataSource");
        if (audioSource.streamMetaData == null)
            throw Exception("Stream metadata information missing, the video will need to be redownloaded to be casted")

        withRepresentation(id, mapOf(
            Pair("mimeType", audioSource.container),
            Pair("codecs", audioSource.codec),
            Pair("startWithSAP", "1"),
            Pair("bandwidth", "100000")
        )
        ) {
            it.withSegmentBase(
                audioUrl,
                audioSource.streamMetaData!!.fileInitStart!!.toLong(),
                audioSource.streamMetaData!!.fileInitEnd!!.toLong(),
                audioSource.streamMetaData!!.fileIndexStart!!.toLong(),
                audioSource.streamMetaData!!.fileIndexEnd!!.toLong()
            )
        }
    }
    fun withRepresentationOnDemand(id: String, videoSource: IVideoSource, videoUrl: String) {
        if(videoSource !is IStreamMetaDataSource || videoSource.streamMetaData == null)
            throw NotImplementedError("Currently onDemand dash only works with IStreamMetaDataSource");
        if (videoSource.streamMetaData == null)
            throw Exception("Stream metadata information missing, the video will need to be redownloaded to be casted")

        withRepresentation(id, mapOf(
            Pair("mimeType", videoSource.container),
            Pair("codecs", videoSource.codec),
            Pair("width", videoSource.width.toString()),
            Pair("height", videoSource.height.toString()),
            Pair("startWithSAP", "1"),
            Pair("bandwidth", "100000")
        )
        ) {
            it.withSegmentBase(
                videoUrl,
                videoSource.streamMetaData!!.fileInitStart!!.toLong(),
                videoSource.streamMetaData!!.fileInitEnd!!.toLong(),
                videoSource.streamMetaData!!.fileIndexStart!!.toLong(),
                videoSource.streamMetaData!!.fileIndexEnd!!.toLong()
            )
        }
    }

    fun withRepresentationOnDemand(id: String, subtitleSource: ISubtitleSource, subtitleUrl: String) {
        withRepresentation(id, mapOf(
            Pair("mimeType", subtitleSource.format ?: "text/vtt"),
            Pair("default", "true"),
            Pair("lang", "en"),
            Pair("bandwidth", "1000")
        )) {
            it.withBaseURL(subtitleUrl)
        }
    }

    fun withBaseURL(url: String) {
        valueTag("BaseURL", url)
    }

    //Segments
    fun withSegmentBase(url: String, initStart: Long, initEnd: Long, segStart: Long, segEnd: Long) {
        valueTag("BaseURL", url);

        tag("SegmentBase", mapOf(Pair("indexRange", "${segStart}-${segEnd}"))) {
            tagClosed("Initialization", Pair("sourceURL", url), Pair("range", "${initStart}-${initEnd}"));
        }
    }


    override fun build() : String {
        writeCloseTag("Period");
        writeCloseTag("MPD");

        return super.build();
    }


    companion object{
        val PROFILE_MAIN = "urn:mpeg:dash:profile:isoff-main:2011";
        val PROFILE_ON_DEMAND = "urn:mpeg:dash:profile:isoff-on-demand:2011";

        fun generateOnDemandDash(vidSource: IVideoSource?, vidUrl: String?, audioSource: IAudioSource?, audioUrl: String?, subtitleSource: ISubtitleSource?, subtitleUrl: String?) : String {
            val duration = vidSource?.duration ?: audioSource?.duration;
            if (duration == null) {
                throw Exception("Either video or audio source needs to be set.");
            }

            val dashBuilder = DashBuilder(duration, PROFILE_ON_DEMAND);

            //Audio
            if(audioSource != null && audioUrl != null) {
                dashBuilder.withAdaptationSet(mapOf(
                        Pair("mimeType", audioSource.container),
                        Pair("codecs", audioSource.codec),
                        Pair("subsegmentAlignment", "true"),
                        Pair("subsegmentStartsWithSAP", "1")
                    )) {
                    //TODO: Verify if & really should be replaced like this?
                    it.withRepresentationOnDemand("1", audioSource, audioUrl.replace("&", "&amp;"));
                }
            }
            // Subtitles
            if (subtitleSource != null && subtitleUrl != null) {
                dashBuilder.withAdaptationSet(
                    mapOf(
                        Pair("mimeType", subtitleSource.format ?: "text/vtt"),
                        Pair("lang", "df"),
                        Pair("default", "true")
                    )
                ) {
                    //TODO: Verify if & really should be replaced like this?
                    it.withRepresentationOnDemand("caption_en", subtitleSource, subtitleUrl.replace("&", "&amp;"))
                }
            }
            //Video
            if (vidSource != null && vidUrl != null) {
                dashBuilder.withAdaptationSet(
                    mapOf(
                        Pair("mimeType", vidSource.container),
                        Pair("codecs", vidSource.codec),
                        Pair("subsegmentAlignment", "true"),
                        Pair("subsegmentStartsWithSAP", "1")
                    )
                ) {
                    it.withRepresentationOnDemand("2", vidSource, vidUrl.replace("&", "&amp;"));
                }
            }

            return dashBuilder.build();
        }
    }


}