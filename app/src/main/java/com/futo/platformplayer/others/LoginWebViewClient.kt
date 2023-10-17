package com.futo.platformplayer.others

import android.net.Uri
import android.webkit.*
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.matchesDomain
import kotlinx.serialization.encodeToString

class LoginWebViewClient : WebViewClient {
    private val LOG_VERBOSE = false;

    private val _pluginConfig: SourcePluginConfig?;
    private val _authConfig: SourcePluginAuthConfig;

    private val _client = ManagedHttpClient();

    val onLogin = Event1<SourceAuth>();
    val onPageLoaded = Event2<WebView?, String?>()

    constructor(config: SourcePluginConfig) : super() {
        _pluginConfig = config;
        _authConfig = config.authentication!!;
        Logger.i(TAG, "Login [${config.name}]" +
                "\nRequired Headers: ${config.authentication?.headersToFind?.joinToString(", ")}" +
                "\nRequired Domain Headers: ${Serializer.json.encodeToString(config.authentication?.domainHeadersToFind)}" +
                "\nRequired Cookies: ${Serializer.json.encodeToString(config.authentication?.cookiesToFind)}",);
    }
    constructor(auth: SourcePluginAuthConfig) : super() {
        _pluginConfig = null;
        _authConfig = auth;
    }

    private val headersFoundMap: HashMap<String, HashMap<String, String>> = hashMapOf();
    private val cookiesFoundMap = hashMapOf<String, HashMap<String, String>>();
    private var urlFound = false;

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url);
        onPageLoaded.emit(view, url);
    }

    //TODO: Use new WebViewRequirementExtractor when time to test extensively
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if(request == null)
            return super.shouldInterceptRequest(view, request as WebResourceRequest?);

        if (_authConfig.allowedDomains != null && !_authConfig.allowedDomains.contains(request.url.host)) {
            return null;
        }

        val domain = request.url.host;
        val domainLower = request.url.host?.lowercase();
        if(_authConfig.completionUrl == null)
            urlFound = true;
        else urlFound = urlFound || request.url == Uri.parse(_authConfig.completionUrl);

        //HEADERS
        if(domainLower != null) {
            val headersToFind = ((_authConfig.headersToFind?.map { Pair(it.lowercase(), domainLower) } ?: listOf()) +
                    (_authConfig.domainHeadersToFind?.filter { domainLower.matchesDomain(it.key.lowercase())}
                        ?.flatMap { it.value.map { header -> Pair(header.lowercase(), it.key.lowercase()) } } ?: listOf()));

            val foundHeaders = request.requestHeaders.filter { requestHeader -> headersToFind.any { it.first.equals(requestHeader.key, true)} &&
                    (!requestHeader.key.equals("Authorization", ignoreCase = true) || requestHeader.value != "undefined") } //TODO: More universal fix (optional regex?)
            for(header in foundHeaders) {
                for(headerDomain in headersToFind.filter { it.first.equals(header.key, true) }) {
                    if (!headersFoundMap.containsKey(headerDomain.second))
                        headersFoundMap[headerDomain.second] = hashMapOf();
                    headersFoundMap[headerDomain.second]!![header.key.lowercase()] = header.value;
                }
            }
        }


        //COOKIES
        //TODO: This is not an ideal solution, we want to intercept the response, but interception need to be rewritten to support that. Correct implementation commented underneath
        //TODO: For now we assume cookies are legit for all subdomains of a top-level domain, this is the most common scenario anyway
        val cookieString = CookieManager.getInstance().getCookie(request.url.toString());
        if(cookieString != null) {
            val domainParts = domain!!.split(".");
            val cookieDomain = "." + domainParts.drop(domainParts.size - 2).joinToString(".");
            if(_pluginConfig == null || _pluginConfig.allowUrls.any { it == "everywhere" || it.lowercase().matchesDomain(cookieDomain) })
                _authConfig.cookiesToFind?.let { cookiesToFind ->
                    val cookies = cookieString.split(";");
                    for(cookieStr in cookies) {
                        val cookieSplitIndex = cookieStr.indexOf("=");
                        if(cookieSplitIndex <= 0) continue;
                        val cookieKey = cookieStr.substring(0, cookieSplitIndex).trim();
                        val cookieVal = cookieStr.substring(cookieSplitIndex + 1).trim();

                        if (_authConfig.cookiesExclOthers && !cookiesToFind.contains(cookieKey))
                            continue;

                        if (cookiesFoundMap.containsKey(cookieDomain))
                            cookiesFoundMap[cookieDomain]!![cookieKey] = cookieVal;
                        else
                            cookiesFoundMap[cookieDomain] = hashMapOf(Pair(cookieKey, cookieVal));
                    }
                };
        }
        //Correct implementation if we could get the response here, but we cant it seems at least for any request with a body.
        //This checks for the true domain for a cookie
        /*
        var cookiesFound = _authConfig.cookiesToFind?.let { cookiesToFind ->
                for(setCookie in response.responseHeaders.filter { it.key.equals("Set-Cookie", true) }) {
                    val cookieParts = setCookie.value.split(';');
                    if (cookieParts.size == 0)
                        continue;
                    val cookieSplitIndex = cookieParts[0].indexOf("=");
                    val cookieKey = cookieParts[0].substring(0, cookieSplitIndex);
                    val cookieValue = cookieParts[0].substring(cookieSplitIndex + 1);

                    if (_authConfig.cookiesExclOthers && !cookiesToFind.contains(cookieKey))
                        continue;

                    val cookieVariables = cookieParts.drop(1).map {
                        val splitIndex = it.indexOf("=");
                        return@map Pair<String, String>(
                            it.substring(0, splitIndex),
                            it.substring(splitIndex + 1).trim()
                        );
                    }.toMap();
                    val domainToUse = if (cookieVariables.containsKey("domain"))
                        cookieVariables["domain"]!!
                    else domain!!;

                    if (cookiesFoundMap.containsKey(domainToUse))
                        cookiesFoundMap[domainToUse]!![cookieKey] = cookieValue;
                    else
                        cookiesFoundMap[domainToUse] = hashMapOf(Pair(cookieKey, cookieValue));
                }
            return@let cookiesToFind.all { toFind -> cookiesFoundMap.any { it.value.containsKey(toFind) } };
        } ?: true;
        */

        val headersFound = _authConfig.headersToFind?.map { it.lowercase() }?.all { reqHeader -> headersFoundMap.any { it.value.containsKey(reqHeader) } } ?: true
        val domainHeadersFound = _authConfig.domainHeadersToFind?.all {
            if(it.value.isEmpty())
                return@all true;
            if(!headersFoundMap.containsKey(it.key.lowercase()))
                return@all false;
            val foundDomainHeaders = headersFoundMap[it.key.lowercase()] ?: mapOf();
            return@all it.value.all { reqHeader -> foundDomainHeaders.containsKey(reqHeader.lowercase()) };
        } ?: true;
        val cookiesFound = _authConfig.cookiesToFind?.all { toFind -> cookiesFoundMap.any { it.value.containsKey(toFind) } } ?: true;

        if(LOG_VERBOSE) {
            val builder = StringBuilder();
            builder.appendLine("Request (method: ${request.method}, host: ${request.url.host}, url: ${request.url}, path: ${request.url.path}):");
            for (pair in request.requestHeaders) {
                builder.appendLine(" ${pair.key}: ${pair.value}");
            }
            builder.appendLine(" Cookies: ${cookiesFoundMap.values.sumOf { it.values.size }}");
            Logger.i(TAG, builder.toString());
            Logger.i(TAG, "Result (urlFound: $urlFound, headersFound: $headersFound, cookiesFound: $cookiesFound)");
        }

        if (urlFound && headersFound && domainHeadersFound && cookiesFound) {
            onLogin.emit(SourceAuth(
                cookieMap = cookiesFoundMap,
                headers = headersFoundMap /*.associate { headerToFind ->
                    headerToFind to headersFoundMap.firstNotNullOf { requestHeader ->
                        if (requestHeader.key.equals(headerToFind, ignoreCase = true))
                            requestHeader.value
                        else null;
                    }
                } ?: mapOf()*/
            ));
        }

        return super.shouldInterceptRequest(view, request);
    }

    companion object {
        private val TAG = "LoginWebViewClient";
    }
}