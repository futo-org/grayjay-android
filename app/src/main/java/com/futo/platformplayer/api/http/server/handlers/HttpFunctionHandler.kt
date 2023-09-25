package com.futo.platformplayer.api.http.server.handlers

import com.futo.platformplayer.api.http.server.HttpContext

class HttpFuntionHandler(method: String, path: String, val handler: (HttpContext)->Unit) : HttpHandler(method, path) {
    override fun handle(httpContext: HttpContext) {
        httpContext.setResponseHeaders(this.headers);
        handler(httpContext);
    }
}