package com.futo.platformplayer.api.http.server

class HttpHeaders : HashMap<String, String> {

    constructor() : super(){}
    constructor(vararg headers: Pair<String,String>) :
            super(headers.map{ Pair(it.first.lowercase(), it.second) }.toMap()) { }
    constructor(headers: Map<String,String>) :
            super(headers.mapKeys { it.key.lowercase() }) { }

    override fun put(key: String, value: String): String? {
        return super.put(key.lowercase(), value)
    }
    override fun get(key: String): String? {
        return super.get(key.lowercase());
    }

    override fun containsKey(key: String): Boolean {
        return super.containsKey(key.lowercase())
    }

    override fun clone() : HttpHeaders {
        return HttpHeaders(this);
    }
}