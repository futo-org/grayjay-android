package com.futo.platformplayer.api.http.server

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.http.server.exceptions.EmptyRequestException
import com.futo.platformplayer.api.http.server.handlers.HttpFuntionHandler
import com.futo.platformplayer.api.http.server.handlers.HttpHandler
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.stream.IntStream.range

class ManagedHttpServer(private val _requestedPort: Int = 0) {
    private val _client : ManagedHttpClient = ManagedHttpClient();
    private val _logVerbose: Boolean = false;

    var active : Boolean = false
        private set;
    private var _stopCount = 0;
    var port = 0
            private set;

    private val _handlers = mutableListOf<HttpHandler>();
    private var _workerPool: ExecutorService? = null;

    @Synchronized
    fun start() {
        if (active)
            return;
        active = true;
        _workerPool = Executors.newCachedThreadPool();

        Thread {
            try {
                val socket = ServerSocket(_requestedPort);
                port = socket.localPort;

                val stopCount = _stopCount;
                while (_stopCount == stopCount) {
                    if(_logVerbose)
                        Logger.i(TAG, "Waiting for connection...");
                    val s = socket.accept() ?: continue;

                    try {
                        handleClientRequest(s);
                    }
                    catch(ex : Exception) {
                        Logger.e(TAG, "Client disconnected due to: " + ex.message, ex);
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to accept socket.", e);
                stop();
            }
        }.start();

        Logger.i(TAG, "Started HTTP Server ${port}. \n" + getAddresses().map { it.hostAddress }.joinToString("\n"));
    }
    @Synchronized
    fun stop() {
        _stopCount++;
        active = false;
        _workerPool?.shutdown();
        _workerPool = null;
        port = 0;
    }

    private fun handleClientRequest(socket: Socket) {
        _workerPool?.submit {
            val requestReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val responseStream = socket.getOutputStream();

            val requestId = UUID.randomUUID().toString().substring(0, 5);
            try {
                keepAliveLoop(requestReader, responseStream, requestId) { req ->
                    req.use { httpContext ->
                        if(!httpContext.path.startsWith("/plugin/"))
                            Logger.i(TAG, "[${req.id}] ${httpContext.method}: ${httpContext.path}")
                        else
                            ;//Logger.v(TAG, "[${req.id}] ${httpContext.method}: ${httpContext.path}")
                        val handler = getHandler(httpContext.method, httpContext.path);
                        if (handler != null) {
                            handler.handle(httpContext);
                        } else {
                            Logger.i(TAG, "[${req.id}] 404 on ${httpContext.method}: ${httpContext.path}");
                            httpContext.respondCode(404);
                        }
                        if(_logVerbose)
                            Logger.i(TAG, "[${req.id}] Responded [${req.statusCode}] ${httpContext.method}: ${httpContext.path}")
                    };
                }
            }
            catch(emptyRequest: EmptyRequestException) {
                if(_logVerbose)
                    Logger.i(TAG, "[${requestId}] Request ended due to empty request: ${emptyRequest.message}");
            }
            catch (e: Throwable) {
                Logger.e(TAG, "Failed to handle client request.", e);
            }
            finally {
                requestReader.close();
                responseStream.close();
            }
        };
    }

    fun getHandler(method: String, path: String) : HttpHandler? {
        synchronized(_handlers) {
            //TODO: Support regex paths?
            if(method == "HEAD")
                return _handlers.firstOrNull { it.path == path && (it.allowHEAD || it.method == "HEAD") }
            return _handlers.firstOrNull { it.method == method && it.path == path };
        }
    }
    fun addHandler(handler: HttpHandler, withHEAD: Boolean = false) : HttpHandler {
        synchronized(_handlers) {
            _handlers.add(handler);
            handler.allowHEAD = withHEAD;
        }
        return handler;
    }
    fun removeHandler(method: String, path: String) {
        synchronized(_handlers) {
            val handler = getHandler(method, path);
            if(handler != null)
                _handlers.remove(handler);
        }
    }
    fun removeAllHandlers(tag: String? = null) {
        synchronized(_handlers) {
            if(tag == null)
                _handlers.clear();
            else
                _handlers.removeIf { it.tag == tag };
        }
    }
    fun addBridgeHandlers(obj: Any, tag: String? = null) {
        val tagToUse = tag ?: obj.javaClass.name;
        val getMethods = obj::class.java.declaredMethods
            .filter { it.getAnnotation(HttpGET::class.java) != null }
            .map { Pair<Method, HttpGET>(it, it.getAnnotation(HttpGET::class.java)!!) }
            .toList();
        val postMethods = obj::class.java.declaredMethods
            .filter { it.getAnnotation(HttpPOST::class.java) != null }
            .map { Pair<Method, HttpPOST>(it, it.getAnnotation(HttpPOST::class.java)!!) }
            .toList();

        val getFields = obj::class.java.declaredFields
            .filter { it.getAnnotation(HttpGET::class.java) != null && it.type == String::class.java }
            .map { Pair<Field, HttpGET>(it, it.getAnnotation(HttpGET::class.java)!!) }
            .toList();

        for(getMethod in getMethods)
            if(getMethod.first.parameterTypes.firstOrNull() == HttpContext::class.java && getMethod.first.parameterCount == 1)
                addHandler(HttpFuntionHandler("GET", getMethod.second.path) { getMethod.first.invoke(obj, it) }).apply {
                    if(!getMethod.second.contentType.isEmpty())
                        this.withContentType(getMethod.second.contentType);
                }.withContentType(getMethod.second.contentType ?: "");
        for(postMethod in postMethods)
            if(postMethod.first.parameterTypes.firstOrNull() == HttpContext::class.java && postMethod.first.parameterCount == 1)
                addHandler(HttpFuntionHandler("POST", postMethod.second.path) { postMethod.first.invoke(obj, it) }).apply {
                    if(!postMethod.second.contentType.isEmpty())
                        this.withContentType(postMethod.second.contentType);
                }.withContentType(postMethod.second.contentType ?: "");

        for(getField in getFields) {
            getField.first.isAccessible = true;
            addHandler(HttpFuntionHandler("GET", getField.second.path) {
                val value = getField.first.get(obj) as String?;
                if(value != null) {
                    val headers = HttpHeaders(
                        Pair("Content-Type", getField.second.contentType)
                    );
                    it.respondCode(200, headers, value);
                }
                else
                    it.respondCode(204);
            }).withContentType(getField.second.contentType ?: "");
        }
    }

    private fun keepAliveLoop(requestReader: BufferedReader, responseStream: OutputStream, requestId: String, handler: (HttpContext)->Unit) {
        val stopCount = _stopCount;
        var keepAlive = false;
        var requestsMax = 0;
        var requestsTotal = 0;
        do {
            val req = HttpContext(requestReader, responseStream, requestId);

            //Handle Request
            handler(req);

            requestsTotal++;
            if(req.keepAlive) {
                keepAlive = true;
                if(req.keepAliveMax > 0)
                    requestsMax = req.keepAliveMax;

                req.skipBody();
            } else {
                keepAlive = false;
            }
        }
        while (keepAlive && (requestsMax == 0 || requestsTotal < requestsMax) && _stopCount == stopCount);
    }

    fun getAddressByIP(addresses: List<InetAddress>) : String = getAddress(addresses.map { it.address }.toList());
    fun getAddress(addresses: List<ByteArray> = listOf()): String {
        if(addresses.isEmpty())
            return getAddresses().first().hostAddress ?: "";
        else
            //Matches the closest address to the list of provided addresses
            return getAddresses().maxBy {
                val availableAddress = it.address;
                return@maxBy addresses.map { deviceAddress ->
                    var matches = 0;
                    for(index in range(0, Math.min(availableAddress.size, deviceAddress.size))) {
                        if(availableAddress[index] == deviceAddress[index])
                            matches++;
                        else
                            break;
                    }
                    return@map matches;
                }.max();
            }.hostAddress ?: "";
    }
    private fun getAddresses(): List<InetAddress> {
        val addresses = arrayListOf<InetAddress>();

        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress) {
                        val ipString: String = addr.hostAddress;
                        val isIPv4 = ipString.indexOf(':') < 0;
                        if (!isIPv4)
                            continue;
                        addresses.add(addr);
                    }
                }
            }
        }
        catch (ignored: Exception) { }

        return addresses;
    }

    companion object {
        val TAG = "ManagedHttpServer";
    }
}