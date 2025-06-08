package com.futo.platformplayer.others

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import com.futo.platformplayer.getSubdomainWildcardQuery
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.matchesDomain

class WebViewRequirementExtractor {
    private val allowedUrls: List<String>;
    private val headersToFind: List<String>?;
    private val domainHeadersToFind: Map<String, List<String>>?;
    private val cookiesToFind: List<String>?;
    private val completionUrl: String?;

    private val exclOtherCookies: Boolean;


    private val headersFoundMap: HashMap<String, HashMap<String, String>> = hashMapOf();
    private val cookiesFoundMap = hashMapOf<String, HashMap<String, String>>();
    private var urlFound = false;


    constructor(allowedUrls: List<String>?, headers: List<String>?, domainHeaders: Map<String, List<String>>?, cookies: List<String>?, url: String?, exclOtherCookies: Boolean = false) {
        this.allowedUrls = allowedUrls ?: listOf("everywhere");
        this.exclOtherCookies = exclOtherCookies;
        headersToFind = headers;
        domainHeadersToFind = domainHeaders;
        cookiesToFind = cookies;
        completionUrl = url;
    }


    fun handleRequest(request: WebResourceRequest, logVerbose: Boolean = false): ExtractedData? {

        val domain = request.url.host;
        val domainLower = request.url.host?.lowercase();
        if (completionUrl == null) {
            urlFound = true;
        } else {
            urlFound = urlFound || request.url == Uri.parse(completionUrl)
        }

        //HEADERS
        if(domainLower != null) {
            val headersToFind = ((headersToFind?.map { Pair(it.lowercase(), domainLower) } ?: listOf()) +
                    (domainHeadersToFind?.filter { domainLower.matchesDomain(it.key.lowercase())}
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
            //val domainParts = domain!!.split(".");
            val cookieDomain = domain!!.getSubdomainWildcardQuery()//"." + domainParts.drop(domainParts.size - 2).joinToString(".");
            if(allowedUrls.any { it == "everywhere" || domain.matchesDomain(it) })
                cookiesToFind?.let { cookiesToFind ->
                    val cookies = cookieString.split(";");
                    for(cookieStr in cookies) {
                        val cookieSplitIndex = cookieStr.indexOf("=");
                        if(cookieSplitIndex <= 0) continue;
                        val cookieKey = cookieStr.substring(0, cookieSplitIndex).trim();
                        val cookieVal = cookieStr.substring(cookieSplitIndex + 1).trim();

                        if (exclOtherCookies && !cookiesToFind.contains(cookieKey))
                            continue;

                        if (cookiesFoundMap.containsKey(cookieDomain))
                            cookiesFoundMap[cookieDomain]!![cookieKey] = cookieVal;
                        else
                            cookiesFoundMap[cookieDomain] = hashMapOf(Pair(cookieKey, cookieVal));
                    }
                };
        }

        val headersFound = headersToFind?.map { it.lowercase() }?.all { reqHeader -> headersFoundMap.any { it.value.containsKey(reqHeader) } } ?: true
        val domainHeadersFound = domainHeadersToFind?.all {
            if(it.value.isEmpty())
                return@all true;
            if(!headersFoundMap.containsKey(it.key.lowercase()))
                return@all false;
            val foundDomainHeaders = headersFoundMap[it.key.lowercase()] ?: mapOf();
            return@all it.value.all { reqHeader -> foundDomainHeaders.containsKey(reqHeader.lowercase()) };
        } ?: true;
        val cookiesFound = cookiesToFind?.all { toFind -> cookiesFoundMap.any { it.value.containsKey(toFind) } } ?: true;

        if(logVerbose) {
            val builder = StringBuilder();
            builder.appendLine("Request (method: ${request.method}, host: ${request.url.host}, url: ${request.url}, path: ${request.url.path}):");
            for (pair in request.requestHeaders) {
                builder.appendLine(" ${pair.key}: ${pair.value}");
            }
            builder.appendLine(" Cookies: ${cookiesFoundMap.values.sumOf { it.values.size }}");
            Logger.i(TAG, builder.toString());
            Logger.i(TAG, "Result (urlFound: $urlFound, headersFound: $headersFound, cookiesFound: $cookiesFound)");
        }

        if (urlFound && headersFound && domainHeadersFound && cookiesFound)
            return ExtractedData(cookiesFoundMap, headersFoundMap);
        return null;
    }



    data class ExtractedData(
        val cookies: HashMap<String, HashMap<String, String>>,
        val headers: HashMap<String, HashMap<String, String>>
    );
    companion object {
        val TAG = "WebViewRequirementExtractor";
    }
}