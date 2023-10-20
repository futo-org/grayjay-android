package com.futo.platformplayer.api.media.platforms.js.internal

import android.net.Uri
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourceCaptchaData
import com.futo.platformplayer.matchesDomain

class JSHttpClient : ManagedHttpClient {
    private val _jsClient: JSClient?;
    private val _auth: SourceAuth?;
    private val _captcha: SourceCaptchaData?;

    var doUpdateCookies: Boolean = true;
    var doApplyCookies: Boolean = true;
    var doAllowNewCookies: Boolean = true;
    val isLoggedIn: Boolean get() = _auth != null;

    private var _currentCookieMap: HashMap<String, HashMap<String, String>>;

    constructor(jsClient: JSClient?, auth: SourceAuth? = null, captcha: SourceCaptchaData? = null) : super() {
        _jsClient = jsClient;
        _auth = auth;
        _captcha = captcha;

        _currentCookieMap = hashMapOf();
        if(!auth?.cookieMap.isNullOrEmpty()) {
            for(domainCookies in auth!!.cookieMap!!)
                _currentCookieMap.put(domainCookies.key, HashMap(domainCookies.value));
        }
        if(!captcha?.cookieMap.isNullOrEmpty()) {
            for(domainCookies in captcha!!.cookieMap!!)
                _currentCookieMap.put(domainCookies.key, HashMap(domainCookies.value));
        }

    }

    override fun clone(): ManagedHttpClient {
        val newClient = JSHttpClient(_jsClient, _auth);
        newClient._currentCookieMap = if(_currentCookieMap != null)
            HashMap(_currentCookieMap.toList().associate { Pair(it.first, HashMap(it.second)) })
        else
            hashMapOf();
        return newClient;
    }

    override fun beforeRequest(request: Request) {
        val domain = Uri.parse(request.url).host!!.lowercase();

        val auth = _auth;
        if (auth != null) {
            //TODO: Possibly add doApplyHeaders
            for (header in auth.headers.filter { domain.matchesDomain(it.key) }.flatMap { it.value.entries })
                request.headers[header.key] = header.value;
        }

        if(doApplyCookies) {
            if (!_currentCookieMap.isNullOrEmpty()) {
                val cookiesToApply = hashMapOf<String, String>();
                synchronized(_currentCookieMap!!) {
                    for(cookie in _currentCookieMap!!
                        .filter { domain.matchesDomain(it.key) }
                        .flatMap { it.value.toList() })
                        cookiesToApply[cookie.first] = cookie.second;
                };

                if(cookiesToApply.size > 0) {
                    val cookieString = cookiesToApply.map { it.key + "=" + it.value }.joinToString("; ");

                    val existingCookies = request.headers["Cookie"];
                    if(!existingCookies.isNullOrEmpty())
                        request.headers["Cookie"] = existingCookies.trim(';') + "; " + cookieString;
                    else
                        request.headers["Cookie"] = cookieString;
                }
                //printTestCode(request.url, request.body, auth.headers, cookieString, request.headers.filter { !auth.headers.containsKey(it.key) });
            }
        }

        _jsClient?.validateUrlOrThrow(request.url);
        super.beforeRequest(request)
    }

    override fun afterRequest(request: Request, resp: Response) {
        super.afterRequest(request, resp)

        if(doUpdateCookies) {
            val domain = Uri.parse(request.url).host!!.lowercase();
            val domainParts = domain!!.split(".");
            val defaultCookieDomain =
                "." + domainParts.drop(domainParts.size - 2).joinToString(".");
            for (header in resp.headers) {
                if ((_auth != null || _currentCookieMap.isNotEmpty()) && header.key.lowercase() == "set-cookie") {
                    val newCookies = cookieStringToMap(header.value);
                    for (cookie in newCookies) {
                        val endIndex = cookie.value.indexOf(";");
                        var cookieValue = cookie.value;
                        var domainToUse = domain;

                        if (endIndex > 0) {
                            val cookieParts = cookie.value.split(";");
                            if (cookieParts.size == 0)
                                continue;
                            cookieValue = cookieParts[0].trim();

                            val cookieVariables = cookieParts.drop(1).map {
                                val splitIndex = it.indexOf("=");
                                if (splitIndex < 0)
                                    return@map Pair(it.trim().lowercase(), "");
                                return@map Pair<String, String>(
                                    it.substring(0, splitIndex).lowercase().trim(),
                                    it.substring(splitIndex + 1).trim()
                                );
                            }.toMap();
                            domainToUse = if (cookieVariables.containsKey("domain"))
                                cookieVariables["domain"]!!.lowercase();
                            else defaultCookieDomain;
                        }

                        val cookieMap = if (_currentCookieMap!!.containsKey(domainToUse))
                            _currentCookieMap!![domainToUse]!!;
                        else {
                            val newMap = hashMapOf<String, String>();
                            _currentCookieMap!!.put(domainToUse, newMap)
                            newMap;
                        }
                        if(cookieMap.containsKey(cookie.key) || doAllowNewCookies)
                            cookieMap.put(cookie.key, cookieValue);
                    }
                }
            }
        }
    }


    private fun cookieStringToMap(parts: List<String>): Map<String, String> {
        val map = hashMapOf<String, String>();
        for(cookie in parts) {
            val cookieKey = cookie.substring(0, cookie.indexOf("="));
            val cookieVal = cookie.substring(cookie.indexOf("=") + 1);
            map.put(cookieKey.trim(), cookieVal.trim());
        }
        return map;
    }

    //Prints out code for test reproduction..
    fun printTestCode(url: String, body: ByteArray?, headers: Map<String, String>, cookieString: String, allHeaders: Map<String, String>? = null) {
        var code = "Code: \n";
        code += "\nurl = \"${url}\";";
        if(body != null)
            code += "\nbody = \"${String(body).replace("\"", "\\\"")}\";";
        if(headers != null)
            for(header in headers) {
                code += "\nclient.Headers.Add(\"${header.key}\", \"${header.value}\");";
            }
        if(cookieString != null)
            code +=  "\nclient.Headers.Add(\"Cookie\", \"${cookieString}\");";

        if(allHeaders != null) {
            code += "\n//OTHER HEADERS:"
            for (header in allHeaders) {
                code += "\nclient.Headers.Add(\"${header.key}\", \"${header.value}\");";
            }
        }

        Logger.i("Testing", code);
    }

}