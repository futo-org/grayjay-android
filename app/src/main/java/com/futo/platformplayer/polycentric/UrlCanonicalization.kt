package com.futo.platformplayer.polycentric

/**
 * Conservatively normalize an `http(s)` URL so trivially equivalent forms
 * share one Polycentric thread. Returns the input unchanged when it cannot
 * be parsed or is not `http(s)`.
 *
 * TODO: Platform plugins should provide platform-specific normalization
 * rules.
 */
internal fun normalizeVideoUrl(url: String): String {
    val raw = url.trim()
    if (raw.isEmpty()) return raw

    val parts = splitHttpUrl(raw) ?: return raw
    return genericCanonical(parts)
}

private class HttpUrl(val host: String, val path: String, val query: String?)

private fun splitHttpUrl(raw: String): HttpUrl? {
    val sep = raw.indexOf("://")
    if (sep <= 0) return null
    val scheme = raw.substring(0, sep).lowercase()
    if (scheme != "http" && scheme != "https") return null
    val rest = raw.substring(sep + 3)

    val authEnd = listOf(rest.indexOf('/'), rest.indexOf('?'), rest.indexOf('#'))
        .filter { it >= 0 }
        .minOrNull() ?: rest.length
    val host = rest.substring(0, authEnd).substringBefore(':').let { canonicalHost(it.lowercase()) }
    if (host.isEmpty()) return null

    val afterAuthority = rest.substring(authEnd)
    val query = afterAuthority.substringAfter('?', "").substringBefore('#').ifEmpty { null }
    val path = afterAuthority.substringBefore('?').substringBefore('#')
    return HttpUrl(host, path, query)
}

/**
 * Canonicalize the host: drop mobile vanity prefixes, then enforce `www.`.
 */
private fun canonicalHost(host: String): String {
    val withoutMobile = listOf("m.", "mobile.")
        .firstOrNull { host.startsWith(it) }
        ?.let { host.substring(it.length) }
        ?: host
    return if (withoutMobile.startsWith("www.")) withoutMobile else "www.$withoutMobile"
}

private fun trimSlashes(path: String): String = path.trimStart('/').trimEnd('/')

private fun genericCanonical(u: HttpUrl): String {
    val path = "/" + trimSlashes(u.path)
    val query = u.query?.let { "?$it" } ?: ""
    return "https://${u.host}${if (path == "/") "" else path}$query"
}
