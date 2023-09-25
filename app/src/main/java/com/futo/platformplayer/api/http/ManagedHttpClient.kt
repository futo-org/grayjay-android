package com.futo.platformplayer.api.http

import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.ensureNotMainThread
import com.futo.platformplayer.logging.Logger
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Dictionary
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

open class ManagedHttpClient {
    protected val _builderTemplate: OkHttpClient.Builder;

    private var client: OkHttpClient;

    private var onBeforeRequest : ((Request) -> Unit)? = null;
    private var onAfterRequest : ((Request, Response) -> Unit)? = null;

    var user_agent = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"

    constructor(builder: OkHttpClient.Builder = OkHttpClient.Builder()) {
        _builderTemplate = builder;
        client = builder.build();
    }

    open fun clone(): ManagedHttpClient {
        val clonedClient = ManagedHttpClient(_builderTemplate);
        clonedClient.user_agent = user_agent;
        return clonedClient;
    }

    fun tryHead(url: String): Map<String, String>? {
        try {
            val result = head(url);
            if(result.isOk)
                return result.getHeadersFlat();
            else
                return null;
        }
        catch(ex: Throwable) {
            //Ignore
            return null;
        }
    }

    fun socket(url: String, headers: MutableMap<String, String> = HashMap(), listener: SocketListener): Socket {

        val requestBuilder: okhttp3.Request.Builder = okhttp3.Request.Builder()
            .url(url);
        if(user_agent != null && !user_agent.isEmpty() && !headers.any { it.key.lowercase() == "user-agent" })
            requestBuilder.addHeader("User-Agent", user_agent)

        for (pair in headers.entries)
            requestBuilder.header(pair.key, pair.value);

        val request = requestBuilder.build();

        val websocket = client.newWebSocket(request, object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                super.onOpen(webSocket, response);
                listener.open();
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                listener.message(text);
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason);
                listener.closing(code, reason);
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason);
                listener.closed(code, reason);
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                super.onFailure(webSocket, t, response);
                listener.failure(t);
            }
        });
        return Socket(websocket);
    }

    fun get(url : String, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return execute(Request(url, "GET", null, headers));
    }

    fun head(url : String, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return execute(Request(url, "HEAD", null, headers));
    }

    fun post(url : String, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return execute(Request(url, "POST", ByteArray(0), headers));
    }
    fun post(url : String, body : String, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return post(url, body.toByteArray(), headers);
    }
    fun post(url : String, body : ByteArray, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return execute(Request(url, "POST", body, headers));
    }

    fun requestMethod(method: String, url : String, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return execute(Request(url, method, null, headers));
    }
    fun requestMethod(method: String, url : String, body: String?, headers : MutableMap<String, String> = HashMap<String, String>()) : Response {
        return execute(Request(url, method, body?.toByteArray(), headers));
    }

    fun execute(request : Request) : Response {
        ensureNotMainThread();

        beforeRequest(request);

        Logger.v(TAG, "HTTP Request [${request.method}] ${request.url} - [${if(request.body != null) request.body.size else 0}]");

        var requestBody: RequestBody? = null
        if (request.body != null) {
            val ct = request.getContentType();
            if(ct != null)
                requestBody = request.body.toRequestBody(ct.toMediaTypeOrNull(), 0, request.body.size);
            else
                requestBody = request.body.toRequestBody(null, 0, request.body.size);
        }

        val requestBuilder: okhttp3.Request.Builder = okhttp3.Request.Builder()
            .method(request.method, requestBody)
            .url(request.url);
        if(user_agent != null && !user_agent.isEmpty() && !request.headers.any { it.key.lowercase() == "user-agent" })
            requestBuilder.addHeader("User-Agent", user_agent)

        for (pair in request.headers.entries)
            requestBuilder.header(pair.key, pair.value);

        val response: okhttp3.Response;
        val resp: Response;

        val time = measureTimeMillis {
            val call = client.newCall(requestBuilder.build());
            request.onCallCreated?.emit(call);
            response = call.execute()
            resp = Response(
                response.code,
                response.request.url.toString(),
                response.message,
                response.headers.toMultimap(),
                response.body
            )
        }
        if(true)
            Logger.v(TAG, "HTTP Response [${request.method}] ${request.url} - [${time}ms]");

        afterRequest(request, resp);
        return resp;
    }

    //Set Listeners
    fun setOnBeforeRequest(listener : (Request)->Unit) {
        this.onBeforeRequest = listener;
    }
    fun setOnAfterRequest(listener : (Request, Response)->Unit) {
        this.onAfterRequest = listener;
    }

    open fun beforeRequest(request: Request) {
        onBeforeRequest?.invoke(request);
    }
    open fun afterRequest(request: Request, resp: Response) {
        onAfterRequest?.invoke(request, resp);
    }


    class Request
    {
        val url : String;
        val method : String;
        val body : ByteArray?;
        val headers : MutableMap<String, String>;

        val onCallCreated = Event1<Call>();

        constructor(url : String, method : String, body : ByteArray?, headers : MutableMap<String, String> = HashMap<String, String>()) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.headers = headers;
        }

        fun getContentType(): String? {
            val ct = headers.keys.find { it.lowercase() == "content-type" };
            if(ct != null)
                return headers[ct];
            return null;
        }
    }

    //TODO: Wrap ResponseBody into a non-library class?
    class Response
    {
        val code : Int;
        val url : String;
        val message : String;
        val headers : Map<String, List<String>>;
        val body : ResponseBody?;

        val isOk : Boolean get() = code >= 200 && code < 300;

        constructor(code : Int, url : String, msg : String, headers : Map<String, List<String>>, body : ResponseBody?) {
            this.code = code;
            this.url = url;
            this.message = msg;
            this.headers = headers;
            this.body = body;
        }

        fun getHeader(key: String): List<String>? {
            for(header in headers) {
                if (header.key.equals(key, ignoreCase = true)) {
                    return header.value;
                };
            }

            return null;
        }

        fun getHeaderFlat(key: String): String? {
            for(header in headers) {
                if (header.key.equals(key, ignoreCase = true)) {
                    return header.value.joinToString(", ")
                };
            }

            return null;
        }

        fun getHeadersFlat(): MutableMap<String, String> {
            val map = HashMap<String, String>();
            for(header in headers)
                map.put(header.key, header.value.joinToString(", "));
            return map;
        }
    }

    class Socket {
        private val socket: WebSocket;

        constructor(socket: WebSocket) {
            this.socket = socket;
        }

        fun send(msg: String) {
            socket.send(msg);
        }

        fun close(code: Int, reason: String) {
            socket.close(code, reason);
        }
    }
    interface SocketListener {
        fun open();
        fun message(msg: String);
        fun closing(code: Int, reason: String);
        fun closed(code: Int, reason: String);
        fun failure(exception: Throwable);
    }

    companion object {
        val TAG = "ManagedHttpClient";
    }
}