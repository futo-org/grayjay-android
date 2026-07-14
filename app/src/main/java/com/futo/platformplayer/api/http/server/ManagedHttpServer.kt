package com.futo.platformplayer.api.http.server

import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.http.server.exceptions.EmptyRequestException
import com.futo.platformplayer.api.http.server.handlers.HttpFunctionHandler
import com.futo.platformplayer.api.http.server.handlers.HttpHandler
import com.futo.platformplayer.api.http.server.handlers.HttpOptionsAllowHandler
import com.futo.platformplayer.logging.Logger
import java.io.BufferedInputStream
import java.io.OutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.stream.IntStream.range

class ManagedHttpServer(private val _requestedPort: Int = 0) {
    private val _client : ManagedHttpClient = ManagedHttpClient();
    private val _logVerbose: Boolean = false;

    var active : Boolean = false
        private set;
    @Volatile private var _stopCount = 0;
    @Volatile private var _serverSocket: ServerSocket? = null;
    var port = 0
            private set;

    private val _handlers = hashMapOf<String, HashMap<String, HttpHandler>>()
    private val _headHandlers = hashMapOf<String, HttpHandler>()
    @Volatile private var _workerPool: ExecutorService? = null;
    private val _sockets = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>());

    @Synchronized
    fun start() {
        if (active)
            return;

        val socket = ServerSocket(_requestedPort);
        _serverSocket = socket;
        port = socket.localPort;
        active = true;
        _workerPool = Executors.newCachedThreadPool();

        val stopCount = _stopCount;
        Thread {
            var failures = 0;
            while (_stopCount == stopCount) {
                if(_logVerbose)
                    Logger.i(TAG, "Waiting for connection...");

                val s = try {
                    socket.accept()
                } catch (e: Throwable) {
                    if (_stopCount != stopCount || socket.isClosed) return@Thread;

                    failures++;
                    Logger.e(TAG, "Failed to accept socket ($failures/$MAX_ACCEPT_FAILURES).", e);
                    if (failures >= MAX_ACCEPT_FAILURES) {
                        Logger.e(TAG, "Giving up on the HTTP server accept loop.");
                        stopGeneration(stopCount);
                        return@Thread;
                    }
                    try { Thread.sleep(ACCEPT_RETRY_DELAY_MS); } catch (_: InterruptedException) { return@Thread; }
                    continue;
                } ?: continue;
                failures = 0;

                try {
                    handleClientRequest(s);
                }
                catch(ex : Exception) {
                    Logger.e(TAG, "Client disconnected due to: " + ex.message, ex);
                    try { s.close(); } catch (_: Throwable) {}
                }
            }
        }.start();

        Logger.i(TAG, "Started HTTP Server ${port}. \n" + getAddresses().map { it.hostAddress }.joinToString("\n"));
    }

    @Synchronized
    private fun stopGeneration(stopCount: Int) {
        if (_stopCount != stopCount) return;
        stop();
    }

    @Synchronized
    fun stop() {
        _stopCount++;
        active = false;
        try { _serverSocket?.close(); } catch (_: Throwable) {}
        _serverSocket = null;

        for (socket in _sockets) try { socket.close(); } catch (_: Throwable) {}
        _sockets.clear();

        _workerPool?.shutdownNow();
        _workerPool = null;
        port = 0;
    }

    private fun handleClientRequest(socket: Socket) {
        val pool = _workerPool;
        if (pool == null) {
            try { socket.close(); } catch (_: Throwable) {}
            return;
        }

        _sockets.add(socket);
        try {
            pool.submit {
            try { socket.soTimeout = KEEPALIVE_TIMEOUT_MS; } catch (_: Throwable) {}

            val requestId = UUID.randomUUID().toString().substring(0, 5);
            try {
                val requestStream = BufferedInputStream(socket.getInputStream());
                val responseStream = socket.getOutputStream();
                keepAliveLoop(requestStream, responseStream, requestId) { req ->
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
            catch(timeout: java.net.SocketTimeoutException) {
                if(_logVerbose)
                    Logger.i(TAG, "[${requestId}] Idle keep-alive connection timed out");
            }
            catch(closed: HttpContext.ClientClosedException) {
                if(_logVerbose)
                    Logger.i(TAG, "[${requestId}] The client closed the connection");
            }
            catch(broken: java.net.SocketException) {
                if(broken.message?.contains("Broken pipe", true) != true &&
                   broken.message?.contains("reset", true) != true)
                    Logger.e(TAG, "Failed to handle client request.", broken);
                else if(_logVerbose)
                    Logger.i(TAG, "[${requestId}] The client closed the connection while we were responding");
            }
            catch (e: Throwable) {
                Logger.e(TAG, "Failed to handle client request.", e);
            }
            finally {
                _sockets.remove(socket);
                try { socket.close(); } catch (_: Throwable) {}
            }
            };
        } catch (ex: Throwable) {
            Logger.e(TAG, "Failed to submit client request.", ex);
            _sockets.remove(socket);
            try { socket.close(); } catch (_: Throwable) {}
        }
    }

    fun getHandler(method: String, path: String) : HttpHandler? {
        synchronized(_handlers) {
            if (method == "HEAD") {
                return _headHandlers[path]
            }

            val handlerMap = _handlers[method] ?: return null
            return handlerMap[path]
        }
    }
    fun addHandler(handler: HttpHandler, withHEAD: Boolean = false) : HttpHandler {
        synchronized(_handlers) {
            handler.allowHEAD = withHEAD;

            var handlerMap: HashMap<String, HttpHandler>? = _handlers[handler.method];
            if (handlerMap == null) {
                handlerMap = hashMapOf()
                _handlers[handler.method] = handlerMap
            }

            handlerMap[handler.path] = handler;
            if (handler.allowHEAD || handler.method == "HEAD") {
                _headHandlers[handler.path] = handler
            }
        }
        return handler;
    }

    fun addHandlerWithAllowAllOptions(handler: HttpHandler, withHEAD: Boolean = false, tag: String? = null) : HttpHandler {
        val allowedMethods = arrayListOf(handler.method, "OPTIONS")
        if (withHEAD) {
            allowedMethods.add("HEAD")
        }

        val tag = tag ?: handler.tag
        if (tag != null) {
            addHandler(HttpOptionsAllowHandler(handler.path, allowedMethods).withTag(tag))
        } else {
            addHandler(HttpOptionsAllowHandler(handler.path, allowedMethods))
        }

        return addHandler(handler, withHEAD).let { if (tag != null) it.withTag(tag) else it }
    }

    fun removeHandler(method: String, path: String) {
        synchronized(_handlers) {
            val handlerMap = _handlers[method] ?: return
            val handler = handlerMap.remove(path) ?: return
            if (method == "HEAD" || handler.allowHEAD) {
                _headHandlers.remove(path)
            }
        }
    }
    fun removeAllHandlers(tag: String? = null) {
        synchronized(_handlers) {
            if(tag == null) {
                _handlers.clear();
                _headHandlers.clear();
                return;
            }

            val removedPaths = HashSet<String>()
            for (pair in _handlers) {
                val toRemove = ArrayList<String>()
                for (innerPair in pair.value) {
                    if (innerPair.value.tag == tag) {
                        toRemove.add(innerPair.key)

                        if (pair.key == "HEAD" || innerPair.value.allowHEAD) {
                            _headHandlers.remove(innerPair.key)
                        }
                    }
                }

                for (x in toRemove) {
                    pair.value.remove(x)
                    removedPaths.add(x)
                }
            }

            _handlers["OPTIONS"]?.let { options ->
                for (path in removedPaths)
                    if (_handlers.none { it.key != "OPTIONS" && it.value.containsKey(path) })
                        options.remove(path)
            }
        }
    }
    fun addBridgeHandlers(obj: Any, tag: String? = null) {
        //val tagToUse = tag ?: obj.javaClass.name;
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
                addHandler(HttpFunctionHandler("GET", getMethod.second.path) { getMethod.first.invoke(obj, it) }).apply {
                    if(!getMethod.second.contentType.isEmpty())
                        this.withContentType(getMethod.second.contentType);
                }.withContentType(getMethod.second.contentType);
        for(postMethod in postMethods)
            if(postMethod.first.parameterTypes.firstOrNull() == HttpContext::class.java && postMethod.first.parameterCount == 1)
                addHandler(HttpFunctionHandler("POST", postMethod.second.path) { postMethod.first.invoke(obj, it) }).apply {
                    if(!postMethod.second.contentType.isEmpty())
                        this.withContentType(postMethod.second.contentType);
                }.withContentType(postMethod.second.contentType);

        for(getField in getFields) {
            getField.first.isAccessible = true;
            addHandler(HttpFunctionHandler("GET", getField.second.path) {
                val value = getField.first.get(obj) as String?;
                if(value != null) {
                    val headers = HttpHeaders(
                        Pair("Content-Type", getField.second.contentType)
                    );
                    it.respondCode(200, headers, value);
                }
                else
                    it.respondCode(204);
            }).withContentType(getField.second.contentType);
        }
    }

    private fun keepAliveLoop(requestReader: BufferedInputStream, responseStream: OutputStream, requestId: String, handler: (HttpContext)->Unit) {
        val stopCount = _stopCount;
        var keepAlive: Boolean;
        do {
            val req = HttpContext(requestReader, responseStream, requestId);

            //Handle Request
            handler(req);

            if(req.keepAlive) {
                keepAlive = true;
                req.skipBody();
            } else {
                keepAlive = false;
            }
        }
        while (keepAlive && _stopCount == stopCount);
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
                        val ipString: String = addr.hostAddress ?: continue
                        val isIPv4 = ipString.indexOf(':') < 0
                        if (!isIPv4) {
                            continue
                        }

                        addresses.add(addr)
                    }
                }
            }
        }
        catch (ignored: Exception) { }

        return addresses;
    }

    companion object {
        val TAG = "ManagedHttpServer";
        private const val KEEPALIVE_TIMEOUT_MS = 60_000;
        private const val MAX_ACCEPT_FAILURES = 20;
        private const val ACCEPT_RETRY_DELAY_MS = 200L;
    }
}