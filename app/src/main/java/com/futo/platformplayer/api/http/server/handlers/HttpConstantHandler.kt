package com.futo.platformplayer.api.http.server.handlers

import com.futo.platformplayer.api.http.server.HttpContext

class HttpConstantHandler(method: String, path: String, val content: String, val contentType: String? = null) : HttpHandler(method, path) {
    override fun handle(httpContext: HttpContext) {
        val headers = this.headers.clone();
        if(contentType != null)
            headers["Content-Type"] = contentType;

        httpContext.respondCode(200, headers, content);
    }
}