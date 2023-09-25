package com.futo.platformplayer.helpers

import org.w3c.dom.DOMException
import org.w3c.dom.Document
import org.w3c.dom.Element

object ProgressiveDashManifestCreator {
    private val PROGRESSIVE_STREAMS_CACHE: ManifestCreatorCache<String, String> = ManifestCreatorCache()

    fun fromVideoProgressiveStreamingUrl(progressiveStreamingBaseUrl: String, streamDuration: Long, mimeType: String, id: Int, codec: String, bitrate: Int, width: Int, height: Int, fps: Int, indexStart: Int, indexEnd: Int, initStart: Int, initEnd: Int): String {
        if (PROGRESSIVE_STREAMS_CACHE.containsKey(progressiveStreamingBaseUrl)) {
            return PROGRESSIVE_STREAMS_CACHE[progressiveStreamingBaseUrl]!!.second
        }

        val doc = DashManifestCreatorsUtils.generateVideoDocumentAndDoCommonElementsGeneration(streamDuration, mimeType, id, codec, bitrate, width, height, fps)
        generateBaseUrlElement(doc, progressiveStreamingBaseUrl)
        generateSegmentBaseElement(doc, indexStart, indexEnd)
        generateInitializationElement(doc, initStart, initEnd)
        return DashManifestCreatorsUtils.buildAndCacheResult(progressiveStreamingBaseUrl, doc, PROGRESSIVE_STREAMS_CACHE)
    }

    fun fromAudioProgressiveStreamingUrl(progressiveStreamingBaseUrl: String, streamDuration: Long, mimeType: String, audioChannels: Int, id: Int, codec: String, bitrate: Int, sampleRate: Int, indexStart: Int, indexEnd: Int, initStart: Int, initEnd: Int): String {
        if (PROGRESSIVE_STREAMS_CACHE.containsKey(progressiveStreamingBaseUrl)) {
            return PROGRESSIVE_STREAMS_CACHE[progressiveStreamingBaseUrl]!!.second
        }

        val doc = DashManifestCreatorsUtils.generateAudioDocumentAndDoCommonElementsGeneration(streamDuration, mimeType, audioChannels, id, codec, bitrate, sampleRate)
        generateBaseUrlElement(doc, progressiveStreamingBaseUrl)
        generateSegmentBaseElement(doc, indexStart, indexEnd)
        generateInitializationElement(doc, initStart, initEnd)
        return DashManifestCreatorsUtils.buildAndCacheResult(progressiveStreamingBaseUrl, doc, PROGRESSIVE_STREAMS_CACHE)
    }

    fun clearCache() {
        PROGRESSIVE_STREAMS_CACHE.clear();
    }

    private fun generateBaseUrlElement(doc: Document, baseUrl: String) {
        try {
            val representationElement = doc.getElementsByTagName(DashManifestCreatorsUtils.REPRESENTATION).item(0) as Element
            val baseURLElement = doc.createElement(DashManifestCreatorsUtils.BASE_URL)
            baseURLElement.textContent = baseUrl
            representationElement.appendChild(baseURLElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }

    private fun generateSegmentBaseElement(doc: Document, indexStart: Int, indexEnd: Int) {
        try {
            val representationElement = doc.getElementsByTagName(DashManifestCreatorsUtils.REPRESENTATION).item(0) as Element
            val segmentBaseElement = doc.createElement(DashManifestCreatorsUtils.SEGMENT_BASE)
            val range: String = "$indexStart-$indexEnd"
            if (indexStart < 0 || indexEnd < 0) {
                throw Exception("ItagItem's indexStart or indexEnd are < 0: $range")
            }
            DashManifestCreatorsUtils.setAttribute(segmentBaseElement, doc, "indexRange", range)
            representationElement.appendChild(segmentBaseElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }

    private fun generateInitializationElement(doc: Document, initStart: Int, initEnd: Int) {
        try {
            val segmentBaseElement = doc.getElementsByTagName(DashManifestCreatorsUtils.SEGMENT_BASE).item(0) as Element
            val initializationElement = doc.createElement(DashManifestCreatorsUtils.INITIALIZATION)
            val range = "$initStart-$initEnd"
            if (initStart < 0 || initEnd < 0) {
                throw Exception("ItagItem's initStart and/or initEnd are/is < 0: $range")
            }
            DashManifestCreatorsUtils.setAttribute(initializationElement, doc, "range", range)
            segmentBaseElement.appendChild(initializationElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }
}