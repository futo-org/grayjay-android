package com.futo.platformplayer.helpers

import org.w3c.dom.DOMException
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object DashManifestCreatorsUtils {
    val MPD = "MPD"
    val PERIOD = "Period"
    val ADAPTATION_SET = "AdaptationSet"
    val ROLE = "Role"
    val REPRESENTATION = "Representation"
    val AUDIO_CHANNEL_CONFIGURATION = "AudioChannelConfiguration"
    val BASE_URL = "BaseURL"
    val SEGMENT_BASE = "SegmentBase"
    val INITIALIZATION = "Initialization"

    fun setAttribute(element: Element, doc: Document, name: String?, value: String?) {
        val attr = doc.createAttribute(name)
        attr.value = value
        element.setAttributeNode(attr)
    }

    fun generateVideoDocumentAndDoCommonElementsGeneration(streamDuration: Long, mimeType: String, id: Int, codec: String, bitrate: Int, width: Int, height: Int, fps: Int): Document {
        val doc: Document = generateDocumentAndMpdElement(streamDuration)
        generatePeriodElement(doc)
        generateAdaptationSetElement(doc, mimeType)
        generateRoleElement(doc)
        generateVideoRepresentationElement(doc, id, codec, bitrate, width, height, fps)
        return doc
    }

    //Audio
    fun generateAudioDocumentAndDoCommonElementsGeneration(streamDuration: Long, mimeType: String, audioChannels: Int, id: Int, codec: String, bitrate: Int, sampleRate: Int): Document {
        val doc: Document = generateDocumentAndMpdElement(streamDuration)
        generatePeriodElement(doc)
        generateAdaptationSetElement(doc, mimeType)
        generateRoleElement(doc)
        generateAudioRepresentationElement(doc, id, codec, bitrate, sampleRate)
        generateAudioChannelConfigurationElement(doc, audioChannels)
        return doc
    }

    private fun generateDocumentAndMpdElement(duration: Long): Document {
        try {
            val doc: Document = newDocument()
            val mpdElement =
                doc.createElement(MPD)
            doc.appendChild(mpdElement)
            setAttribute(mpdElement, doc, "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
            setAttribute(mpdElement, doc, "xmlns", "urn:mpeg:DASH:schema:MPD:2011")
            setAttribute(mpdElement, doc, "xsi:schemaLocation", "urn:mpeg:DASH:schema:MPD:2011 DASH-MPD.xsd")
            setAttribute(mpdElement, doc, "minBufferTime", "PT1.500S")
            setAttribute(mpdElement, doc, "profiles", "urn:mpeg:dash:profile:full:2011")
            setAttribute(mpdElement, doc, "type", "static")
            setAttribute(mpdElement, doc, "mediaPresentationDuration", String.format(Locale.ENGLISH, "PT%.3fS", duration / 1000.0))
            return doc
        } catch (e: Exception) {
            throw Exception("Could not generate the DASH manifest or append the MPD doc to it", e)
        }
    }

    private fun generatePeriodElement(doc: Document) {
        try {
            val mpdElement = doc.getElementsByTagName(MPD).item(0) as Element
            val periodElement = doc.createElement(PERIOD)
            mpdElement.appendChild(periodElement)
        } catch (e: DOMException) {
            throw Exception(PERIOD, e)
        }
    }

    private fun generateAdaptationSetElement(doc: Document, mimeType: String) {
        try {
            val periodElement = doc.getElementsByTagName(PERIOD).item(0) as Element
            val adaptationSetElement = doc.createElement(ADAPTATION_SET)
            setAttribute(adaptationSetElement, doc, "id", "0")
            if (mimeType.isEmpty()) {
                throw Exception("the MediaFormat or its mime type is null or empty")
            }

            setAttribute(adaptationSetElement, doc, "mimeType", mimeType)
            setAttribute(adaptationSetElement, doc, "subsegmentAlignment", "true")
            periodElement.appendChild(adaptationSetElement)
        } catch (e: DOMException) {
            throw Exception(ADAPTATION_SET, e)
        }
    }

    private fun generateRoleElement(doc: Document) {
        try {
            val adaptationSetElement = doc.getElementsByTagName(ADAPTATION_SET).item(0) as Element
            val roleElement = doc.createElement(ROLE)
            setAttribute(roleElement, doc, "schemeIdUri", "urn:mpeg:DASH:role:2011")
            setAttribute(roleElement, doc, "value", "main")
            adaptationSetElement.appendChild(roleElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }

    private fun generateVideoRepresentationElement(doc: Document, id: Int, codec: String, bitrate: Int, width: Int, height: Int, fps: Int) {
        try {
            val adaptationSetElement = doc.getElementsByTagName(ADAPTATION_SET).item(0) as Element
            val representationElement = doc.createElement(REPRESENTATION)
            if (id <= 0) {
                throw Exception("the id of the ItagItem is <= 0")
            }
            setAttribute(representationElement, doc, "id", id.toString())
            if (codec.isEmpty()) {
                throw Exception("the codec value of the ItagItem is null or empty")
            }
            setAttribute(representationElement, doc, "codecs", codec)
            setAttribute(representationElement, doc, "startWithSAP", "1")
            setAttribute(representationElement, doc, "maxPlayoutRate", "1")
            if (bitrate <= 0) {
                throw Exception("the bitrate of the ItagItem is <= 0")
            }
            setAttribute(representationElement, doc, "bandwidth", bitrate.toString())
            if (height <= 0 && width <= 0) {
                throw Exception("both width and height of the ItagItem are <= 0")
            }
            if (width > 0) {
                setAttribute(
                    representationElement,
                    doc,
                    "width",
                    width.toString()
                )
            }
            setAttribute(representationElement, doc, "height", height.toString())
            if (fps > 0) {
                setAttribute(representationElement, doc, "frameRate", fps.toString())
            }
            adaptationSetElement.appendChild(representationElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }

    private fun generateAudioRepresentationElement(doc: Document, id: Int, codec: String, bitrate: Int, sampleRate: Int) {
        try {
            val adaptationSetElement = doc.getElementsByTagName(ADAPTATION_SET).item(0) as Element
            val representationElement = doc.createElement(REPRESENTATION)
            if (id <= 0) {
                throw Exception("the id of the ItagItem is <= 0")
            }
            setAttribute(representationElement, doc, "id", id.toString())
            if (codec.isEmpty()) {
                throw Exception("the codec value of the ItagItem is null or empty")
            }
            setAttribute(representationElement, doc, "codecs", codec)
            setAttribute(representationElement, doc, "startWithSAP", "1")
            setAttribute(representationElement, doc, "maxPlayoutRate", "1")
            if (bitrate <= 0) {
                throw Exception("the bitrate of the ItagItem is <= 0")
            }
            setAttribute(representationElement, doc, "bandwidth", bitrate.toString())
            val audioSamplingRateAttribute = doc.createAttribute("audioSamplingRate")
            audioSamplingRateAttribute.value = sampleRate.toString()
            adaptationSetElement.appendChild(representationElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }

    private fun generateAudioChannelConfigurationElement(doc: Document, audioChannels: Int) {
        try {
            val representationElement = doc.getElementsByTagName(REPRESENTATION).item(0) as Element
            val audioChannelConfigurationElement = doc.createElement(AUDIO_CHANNEL_CONFIGURATION)
            setAttribute(audioChannelConfigurationElement, doc, "schemeIdUri", "urn:mpeg:dash:23003:3:audio_channel_configuration:2011")
            if (audioChannels <= 0) {
                throw Exception("the number of audioChannels in the ItagItem is <= 0: $audioChannels")
            }
            setAttribute(audioChannelConfigurationElement, doc, "value", audioChannels.toString())
            representationElement.appendChild(audioChannelConfigurationElement)
        } catch (e: DOMException) {
            throw Exception(e)
        }
    }

    fun buildAndCacheResult(originalBaseStreamingUrl: String, doc: Document, manifestCreatorCache: ManifestCreatorCache<String, String>): String {
        try {
            val documentXml: String = documentToXml(doc)
            manifestCreatorCache.put(originalBaseStreamingUrl, documentXml)
            return documentXml
        } catch (e: Exception) {
            throw Exception("Could not convert the DASH manifest generated to a string", e)
        }
    }

    private fun newDocument(): Document {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        try {
            documentBuilderFactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            documentBuilderFactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        } catch (ignored: Exception) {
            // Ignore exceptions as setting these attributes to secure XML generation is not
            // supported by all platforms (like the Android implementation)
        }
        return documentBuilderFactory.newDocumentBuilder().newDocument()
    }

    private fun documentToXml(doc: Document): String {
        val transformerFactory = TransformerFactory.newInstance()
        try {
            transformerFactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            transformerFactory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        } catch (ignored: Exception) {
            // Ignore exceptions as setting these attributes to secure XML generation is not
            // supported by all platforms (like the Android implementation)
        }
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.STANDALONE, "no")
        val result = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(result))
        return result.toString()
    }
}