package com.futo.platformplayer.casting.dlna

import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.handlers.HttpHandler
import com.futo.platformplayer.logging.Logger

/**
 * Serves a [FlatMp4Muxer] output with HTTP Range support for DLNA renderers.
 */
class FlatMp4HttpHandler(path: String, private val muxer: FlatMp4Muxer) : HttpHandler("GET", path) {
    override fun handle(httpContext: HttpContext) {
        val size = muxer.size
        var start = 0L
        var end = size - 1
        val range = httpContext.getHeader("range")
        val isRange = range != null && range.startsWith("bytes=")
        if (isRange) {
            val parts = range!!.substring(6).split("-", limit = 2)
            val first = parts[0].trim().toLongOrNull()
            val last = parts.getOrNull(1)?.trim()?.toLongOrNull()
            if (first != null) {
                start = first
                if (last != null) end = minOf(last, size - 1)
            } else if (last != null) {
                start = maxOf(size - last, 0)
            }
        }

        val responseHeaders = this.headers.clone()
        responseHeaders["Content-Type"] = muxer.contentType
        responseHeaders["Accept-Ranges"] = "bytes"
        responseHeaders["transferMode.dlna.org"] = "Streaming"
        responseHeaders["contentFeatures.dlna.org"] = DlnaSoap.DLNA_FEATURES

        if (start >= size || start > end) {
            responseHeaders["Content-Range"] = "bytes */$size"
            httpContext.respondCode(416, responseHeaders)
            return
        }

        responseHeaders["Content-Length"] = (end - start + 1).toString()
        if (isRange) {
            responseHeaders["Content-Range"] = "bytes $start-$end/$size"
        }

        httpContext.respond(if (isRange) 206 else 200, responseHeaders) { output ->
            try {
                muxer.writeTo(output, start, end)
            } catch (e: Throwable) {
                //Renderers routinely drop connections while seeking.
                Logger.v(TAG, "Muxed stream write ended early ($start-$end): ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "FlatMp4HttpHandler"
    }
}
