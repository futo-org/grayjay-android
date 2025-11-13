package com.curlbind

import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import kotlin.collections.iterator
import kotlin.math.min

@Keep
object Libcurl {
    init {
        System.loadLibrary("curl-impersonate")
        System.loadLibrary("curl-impersonate-jni")
        // CURL_GLOBAL_ALL = 3
        require(ce_global_init(3) == CURLcode.CURLE_OK) { "curl_global_init failed" }
    }

    @Keep
    data class Request(
        var url: String,
        var method: String = "GET",
        var headers: Map<String, String> = emptyMap(),
        var body: ByteArray? = null,
        var impersonateTarget: String = "chrome136",
        var useBuiltInHeaders: Boolean = true,
        var timeoutMs: Int = 30_000,
        var cookieJarPath: String? = null,
        var sendCookies: Boolean = true,
        var persistCookies: Boolean = true,
    )

    @Keep
    data class Response(
        val status: Int,
        val effectiveUrl: String,
        val bodyBytes: ByteArray,
        val headers: Map<String, List<String>>
    )

    object CURLcode {
        const val CURLE_OK = 0
        const val CURLE_UNKNOWN_OPTION = 48
    }

    object CurlInfoConsts {
        const val CURLINFO_STRING = 0x100000
        const val CURLINFO_LONG = 0x200000
        const val CURLINFO_DOUBLE = 0x300000
        const val CURLINFO_SLIST = 0x400000
        const val CURLINFO_PTR = 0x400000
        const val CURLINFO_SOCKET = 0x500000
        const val CURLINFO_OFF_T = 0x600000
        const val CURLINFO_MASK = 0x0fffff
        const val CURLINFO_TYPEMASK = 0xf00000
    }

    object CURLINFO {
        const val NONE = 0
        const val EFFECTIVE_URL = CurlInfoConsts.CURLINFO_STRING + 1
        const val RESPONSE_CODE = CurlInfoConsts.CURLINFO_LONG + 2
    }

    object CURLOPT {
        const val URL = 10002
        const val FOLLOWLOCATION = 52
        const val MAXREDIRS = 68
        const val CONNECTTIMEOUT_MS = 156
        const val TIMEOUT_MS = 155
        const val HTTP_VERSION = 84
        const val ACCEPT_ENCODING = 10102
        const val HTTPHEADER = 10023
        const val COOKIEFILE = 10031
        const val COOKIEJAR = 10082
        const val CUSTOMREQUEST = 10036
        const val IPRESOLVE = 113
        const val POSTFIELDS = 10015
        const val POSTFIELDSIZE = 60
        const val WRITEFUNCTION = 20011
        const val HEADERFUNCTION = 20079
        const val WRITEDATA = 10001
        const val HEADERDATA = 10029
        const val COPYPOSTFIELDS = 10165
        const val CURLOPT_DNS_SERVERS = 10211
        const val CAPATH = 10097
        const val CAINFO = 10065
    }

    object CURL_HTTP_VERSION { const val TWO_TLS = 4 }
    object CURL_IPRESOLVE { const val WHATEVER = 0; const val V4 = 1; const val V6 = 2 }

    @Keep interface WriteCallback { fun onWrite(chunk: ByteArray): Int }
    @Keep interface HeaderCallback { fun onHeader(line: ByteArray): Int }

    @Volatile private var defaultCAPath: String? = null
    @Keep fun setDefaultCAPath(path: String) { defaultCAPath = path }

    fun perform(req: Request): Response {
        val easy = ce_easy_init()
        require(easy != 0L) { "curl_easy_init failed" }

        var slist: Long = 0L
        val bodySink = ByteArrayOutputStream(64 * 1024)
        val rawHeaderLines = ArrayList<String>(64)

        try {
            val imp = ce_easy_impersonate(easy, req.impersonateTarget, req.useBuiltInHeaders)
            if (imp != CURLcode.CURLE_OK && imp != CURLcode.CURLE_UNKNOWN_OPTION) {
                error("curl_easy_impersonate failed: ${ce_easy_strerror(imp)}")
            }

            checkOK(ce_setopt_str(easy, CURLOPT.URL, req.url))
            checkOK(ce_setopt_long(easy, CURLOPT.FOLLOWLOCATION, 1))
            checkOK(ce_setopt_long(easy, CURLOPT.MAXREDIRS, 10))
            checkOK(ce_setopt_long(easy, CURLOPT.CONNECTTIMEOUT_MS, req.timeoutMs.toLong()))
            checkOK(ce_setopt_long(easy, CURLOPT.TIMEOUT_MS, req.timeoutMs.toLong()))
            checkOK(ce_setopt_long(easy, CURLOPT.HTTP_VERSION, CURL_HTTP_VERSION.TWO_TLS.toLong()))
            checkOK(ce_setopt_str(easy, CURLOPT.ACCEPT_ENCODING, "")) // enable auto-decompress

            if (req.headers.isNotEmpty()) {
                for ((k, v) in req.headers) slist = ce_slist_append(slist, "$k: $v")
                if (slist != 0L) checkOK(ce_setopt_ptr(easy, CURLOPT.HTTPHEADER, slist))
            }

            if (req.sendCookies || req.persistCookies) {
                val jar = (req.cookieJarPath ?: defaultCookieJarPath())
                if (req.sendCookies) checkOK(ce_setopt_str(easy, CURLOPT.COOKIEFILE, jar))
                if (req.persistCookies) checkOK(ce_setopt_str(easy, CURLOPT.COOKIEJAR,  jar))
            }

            val method = req.method
            if (!method.equals("GET", ignoreCase = true)) {
                checkOK(ce_setopt_str(easy, CURLOPT.CUSTOMREQUEST, method))
                val body = req.body
                if (body != null && body.isNotEmpty()) {
                    checkOK(ce_set_postfields(easy, body))
                }
            }

            checkOK(ce_set_write_callback(easy, object : WriteCallback {
                override fun onWrite(chunk: ByteArray): Int {
                    bodySink.write(chunk)
                    return chunk.size
                }
            }))
            checkOK(ce_set_header_callback(easy, object : HeaderCallback {
                override fun onHeader(line: ByteArray): Int {
                    // Keep raw but trim CRLF for convenience
                    val s = line.toString(Charset.forName("ISO-8859-1")).trimEnd('\r', '\n')
                    if (s.isNotBlank()) rawHeaderLines.add(s)
                    return line.size
                }
            }))

            checkOK(ce_setopt_str(easy, CURLOPT.CURLOPT_DNS_SERVERS, "1.1.1.1,8.8.8.8"));
            defaultCAPath?.let { checkOK(ce_setopt_str(easy, CURLOPT.CAINFO, it)) }

            val rc = ce_easy_perform(easy)
            if (rc != CURLcode.CURLE_OK) error("curl_easy_perform failed: ${ce_easy_strerror(rc)}")

            val codeArr = longArrayOf(0)
            checkOK(ce_easy_getinfo_long(easy, CURLINFO.RESPONSE_CODE, codeArr))
            val effective = ce_easy_getinfo_string(easy, CURLINFO.EFFECTIVE_URL) ?: req.url

            return Response(
                status = codeArr[0].toInt(),
                effectiveUrl = effective,
                bodyBytes = bodySink.toByteArray(),
                headers = parseHeaders(rawHeaderLines)
            )
        } finally {
            if (slist != 0L) ce_slist_free_all(slist)
            ce_easy_cleanup(easy)
        }
    }

    private fun defaultCookieJarPath(): String {
        val tmp = System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"
        return if (tmp.endsWith("/")) "${tmp}imphttp.cookies.txt" else "$tmp/imphttp.cookies.txt"
    }

    private fun checkOK(code: Int) {
        if (code != CURLcode.CURLE_OK) throw IllegalStateException("libcurl error: ${ce_easy_strerror(code)}")
    }

    private fun parseHeaders(lines: List<String>): Map<String, List<String>> {
        val map = linkedMapOf<String, MutableList<String>>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val name = line.substring(0, idx).trim()
            val value = line.substring(min(idx + 1, line.length)).trim()
            map.getOrPut(name) { mutableListOf() }.add(value)
        }
        return map
    }

    @JvmStatic external fun ce_set_write_callback(easy: Long, cb: WriteCallback?): Int
    @JvmStatic external fun ce_set_header_callback(easy: Long, cb: HeaderCallback?): Int

    @JvmStatic external fun ce_global_init(flags: Long): Int
    @JvmStatic external fun ce_global_cleanup()
    @JvmStatic external fun ce_easy_init(): Long
    @JvmStatic external fun ce_easy_cleanup(easy: Long)
    @JvmStatic external fun ce_easy_perform(easy: Long): Int

    @JvmStatic external fun ce_easy_impersonate(easy: Long, target: String, defaultHeaders: Boolean): Int
    @JvmStatic external fun ce_setopt_long(easy: Long, opt: Int, value: Long): Int
    @JvmStatic external fun ce_setopt_str(easy: Long, opt: Int, value: String): Int
    @JvmStatic external fun ce_setopt_ptr(easy: Long, opt: Int, ptr: Long): Int
    @JvmStatic external fun ce_slist_append(list: Long, header: String): Long
    @JvmStatic external fun ce_slist_free_all(list: Long)
    @JvmStatic external fun ce_easy_getinfo_long(easy: Long, info: Int, outVal: LongArray): Int
    @JvmStatic external fun ce_easy_getinfo_string(easy: Long, info: Int): String?

    @JvmStatic external fun ce_set_postfields(easy: Long, body: ByteArray): Int
    @JvmStatic external fun ce_easy_strerror(code: Int): String
}
