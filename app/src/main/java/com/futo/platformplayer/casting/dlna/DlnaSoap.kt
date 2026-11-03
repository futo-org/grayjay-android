package com.futo.platformplayer.casting.dlna

import com.futo.platformplayer.api.http.ManagedHttpClient
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

object DlnaSoap {
    fun createClient() = ManagedHttpClient(
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
    )

    class Result(val code: Int, val body: String) {
        val isOk: Boolean get() = code in 200..299
        val error: String? get() = tag(body, "errorDescription") ?: tag(body, "faultstring")
    }

    fun invoke(client: ManagedHttpClient, controlUrl: String, service: String, action: String, args: String): Result {
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$service\">$args</u:$action></s:Body></s:Envelope>"

        val headers = mutableMapOf(
            "Content-Type" to "text/xml; charset=\"utf-8\"",
            "SOAPACTION" to "\"$service#$action\""
        )
        return client.post(controlUrl, envelope, headers).use { response ->
            Result(response.code, response.body?.string() ?: "")
        }
    }

    /**
     * DIDL-Lite metadata for SetAVTransportURI. Many renderers (Samsung included) refuse a URI without it.
     */
    fun buildDidl(url: String, contentType: String, title: String?, thumbnailUrl: String?): String {
        val upnpClass = when {
            contentType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
            contentType.startsWith("image/") -> "object.item.imageItem.photo"
            else -> "object.item.videoItem"
        }
        val sb = StringBuilder()
        sb.append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
        sb.append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
        sb.append("<dc:title>").append(escape(title ?: "Grayjay")).append("</dc:title>")
        sb.append("<upnp:class>").append(upnpClass).append("</upnp:class>")
        if (!thumbnailUrl.isNullOrBlank()) {
            sb.append("<upnp:albumArtURI>").append(escape(thumbnailUrl)).append("</upnp:albumArtURI>")
        }
        sb.append("<res protocolInfo=\"http-get:*:").append(escape(contentType)).append(":").append(DLNA_FEATURES).append("\">")
        sb.append(escape(url)).append("</res>")
        sb.append("</item></DIDL-Lite>")
        return sb.toString()
    }

    fun escape(value: String): String {
        val sb = StringBuilder(value.length + 16)
        for (c in value) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun tag(xml: String?, name: String): String? {
        if (xml == null) return null
        val match = Regex("<(?:[A-Za-z0-9_]+:)?$name(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_]+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * Parses UPnP H+:MM:SS[.F+] times. Returns null for missing or NOT_IMPLEMENTED values.
     */
    fun parseTime(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val parts = value.trim().split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return hours * 3600.0 + minutes * 60.0 + seconds
    }

    fun formatTime(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).toLong()
        return String.format(Locale.US, "%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    const val DLNA_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
}
