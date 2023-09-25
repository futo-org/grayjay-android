package com.futo.platformplayer.api.http.server.handlers

import com.futo.platformplayer.api.http.server.HttpContext

class HttpOptionsAllowHandler(path: String) : HttpHandler("OPTIONS", path) {
    override fun handle(httpContext: HttpContext) {
        //Just allow whatever is requested

        val requestedOrigin = httpContext.headers.getOrDefault("Access-Control-Request-Origin", "");
        val requestedMethods = httpContext.headers.getOrDefault("Access-Control-Request-Method", "");
        val requestedHeaders = httpContext.headers.getOrDefault("Access-Control-Request-Headers", "");

        val newHeaders = headers.clone();
        newHeaders.put("Allow", requestedMethods);
        newHeaders.put("Access-Control-Allow-Methods", requestedMethods);
        newHeaders.put("Access-Control-Allow-Headers", "*");

        httpContext.respondCode(200, newHeaders);
    }
}