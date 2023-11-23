package com.futo.platformplayer.api.http.server.handlers

import com.futo.platformplayer.api.http.server.HttpContext

class HttpOptionsAllowHandler(path: String) : HttpHandler("OPTIONS", path) {
    override fun handle(httpContext: HttpContext) {
        val newHeaders = headers.clone()
        newHeaders.put("Access-Control-Allow-Origin", "*")
        newHeaders.put("Access-Control-Allow-Methods", "*")
        newHeaders.put("Access-Control-Allow-Headers", "*")
        httpContext.respondCode(200, newHeaders);
    }
}