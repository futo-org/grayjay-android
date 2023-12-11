package com.futo.platformplayer.api.media.platforms.js.internal

import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourceCaptchaData
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.matchesDomain

class JSHttpClient : ManagedHttpClient {
    private val _jsClient: JSClient?;
    private val _jsConfig: SourcePluginConfig?;
    private val _auth: SourceAuth?;
    private val _captcha: SourceCaptchaData?;

    var doUpdateCookies: Boolean = true;
    var doApplyCookies: Boolean = true;
    var doAllowNewCookies: Boolean = true;
    val isLoggedIn: Boolean get() = _auth != null;

    private var _currentCookieMap: HashMap<String, HashMap<String, String>>;

    constructor(jsClient: JSClient?, auth: SourceAuth? = null, captcha: SourceCaptchaData? = null, config: SourcePluginConfig? = null) : super() {
        _jsClient = jsClient;
        _jsConfig = config;
        _auth = auth;
        _captcha = captcha;

        _currentCookieMap = hashMapOf();
        if(!auth?.cookieMap.isNullOrEmpty()) {
            for(domainCookies in auth!!.cookieMap!!)
                _currentCookieMap.put(domainCookies.key, HashMap(domainCookies.value));
        }
        if(!captcha?.cookieMap.isNullOrEmpty()) {
            for(domainCookies in captcha!!.cookieMap!!) {
                if(_currentCookieMap.containsKey(domainCookies.key))
                    _currentCookieMap[domainCookies.key]?.putAll(domainCookies.value);
                else
                    _currentCookieMap.put(domainCookies.key, HashMap(domainCookies.value));
            }
        }

    }

    override fun clone(): ManagedHttpClient {
        val newClient = JSHttpClient(_jsClient, _auth);
        newClient._currentCookieMap = HashMap(_currentCookieMap.toList().associate { Pair(it.first, HashMap(it.second)) })
        return newClient;
    }

    override fun beforeRequest(request: okhttp3.Request): okhttp3.Request {
        val domain = request.url.host.lowercase();
        val auth = _auth;

        val newBuilder = if(auth != null || doApplyCookies)
            request.newBuilder();
        else
            null;
        if (auth != null) {
            //TODO: Possibly add doApplyHeaders
            for (header in auth.headers.filter { domain.matchesDomain(it.key) }.flatMap { it.value.entries })
                newBuilder?.header(header.key, header.value);
        }

        if(doApplyCookies) {
            if (_currentCookieMap.isNotEmpty()) {
                val cookiesToApply = hashMapOf<String, String>();
                synchronized(_currentCookieMap) {
                    for(cookie in _currentCookieMap
                        .filter { domain.matchesDomain(it.key) }
                        .flatMap { it.value.toList() })
                        cookiesToApply[cookie.first] = cookie.second;
                };

                if(cookiesToApply.size > 0) {
                    val cookieString = cookiesToApply.map { it.key + "=" + it.value }.joinToString("; ");

                    val existingCookies = request.headers["Cookie"];
                    if(!existingCookies.isNullOrEmpty())
                        newBuilder?.header("Cookie", existingCookies.trim(';') + "; " + cookieString);
                    else
                        newBuilder?.header("Cookie", cookieString);
                }
                //printTestCode(request.url, request.body, auth.headers, cookieString, request.headers.filter { !auth.headers.containsKey(it.key) });
            }
        }

        if(_jsClient != null)
            _jsClient.validateUrlOrThrow(request.url.toString());
        else if (_jsConfig != null && !_jsConfig.isUrlAllowed(request.url.toString()))
            throw ScriptImplementationException(_jsConfig, "Attempted to access non-whitelisted url: ${request.url.toString()}\nAdd it to your config");

        return newBuilder?.build() ?: request;
    }

    override fun afterRequest(resp: okhttp3.Response): okhttp3.Response {
        if(doUpdateCookies) {
            val domain = resp.request.url.host.lowercase();
            val domainParts = domain.split(".");
            val defaultCookieDomain =
                "." + domainParts.drop(domainParts.size - 2).joinToString(".");
            for (header in resp.headers) {
                if ((_auth != null || _currentCookieMap.isNotEmpty()) && header.first.lowercase() == "set-cookie") {
                    val cookie = cookieStringToPair(header.second);
                    var cookieValue = cookie.second;
                    var domainToUse = domain;

                    if (cookie.first.isNotEmpty() && cookie.second.isNotEmpty()) {
                        val cookieParts = cookie.second.split(";");
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

                    val cookieMap = if (_currentCookieMap.containsKey(domainToUse))
                        _currentCookieMap[domainToUse]!!;
                    else {
                        val newMap = hashMapOf<String, String>();
                        _currentCookieMap[domainToUse] = newMap
                        newMap;
                    }
                    if(cookieMap.containsKey(cookie.first) || doAllowNewCookies)
                        cookieMap[cookie.first] = cookieValue;
                }
            }
        }
        return resp;
    }

    private fun cookieStringToPair(cookie: String): Pair<String, String> {
        val cookieKey = cookie.substring(0, cookie.indexOf("="));
        val cookieVal = cookie.substring(cookie.indexOf("=") + 1);
        return Pair(cookieKey.trim(), cookieVal.trim());
    }
}