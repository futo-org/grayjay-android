package com.futo.platformplayer.casting.dlna

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parsed UPnP device description of a MediaRenderer (the XML served at the SSDP LOCATION url).
 */
class DlnaDescription(
    val location: String,
    val friendlyName: String,
    val udn: String?,
    val manufacturer: String?,
    val modelName: String?,
    val avTransportControlUrl: String,
    val renderingControlUrl: String?
) {
    companion object {
        const val SERVICE_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        const val SERVICE_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

        /**
         * Returns null when the description does not contain an AVTransport service.
         */
        fun parse(location: String, xml: String): DlnaDescription? {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Throwable) { }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

            val base = firstText(doc.documentElement, "URLBase")?.takeIf { it.isNotBlank() } ?: location
            val device = doc.getElementsByTagName("device").item(0) as? Element ?: return null

            var avTransport: String? = null
            var renderingControl: String? = null
            val services = doc.getElementsByTagName("service")
            for (i in 0 until services.length) {
                val service = services.item(i) as? Element ?: continue
                val type = firstText(service, "serviceType")?.trim() ?: continue
                val control = firstText(service, "controlURL")?.trim() ?: continue
                when {
                    type.startsWith("urn:schemas-upnp-org:service:AVTransport:") -> avTransport = resolve(base, control)
                    type.startsWith("urn:schemas-upnp-org:service:RenderingControl:") -> renderingControl = resolve(base, control)
                }
            }

            return DlnaDescription(
                location = location,
                friendlyName = firstText(device, "friendlyName")?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
                udn = firstText(device, "UDN")?.trim(),
                manufacturer = firstText(device, "manufacturer")?.trim(),
                modelName = firstText(device, "modelName")?.trim(),
                avTransportControlUrl = avTransport ?: return null,
                renderingControlUrl = renderingControl
            )
        }

        private fun firstText(parent: Element, tag: String): String? =
            parent.getElementsByTagName(tag).item(0)?.textContent

        private fun resolve(base: String, path: String): String = URL(URL(base), path).toString()
    }
}
