package com.futo.platformplayer.casting

import android.os.Looper
import android.util.Log
import com.futo.platformplayer.getConnectedSocket
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.protos.ChromeCast
import com.futo.platformplayer.toHexString
import com.futo.platformplayer.toInetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ChromecastCastingDevice : CastingDevice {
    //See for more info: https://developers.google.com/cast/docs/media/messages

    override val protocol: CastProtocolType get() = CastProtocolType.CHROMECAST;
    override val isReady: Boolean get() = name != null && addresses != null && addresses?.isNotEmpty() == true && port != 0;
    override var usedRemoteAddress: InetAddress? = null;
    override var localAddress: InetAddress? = null;
    override val canSetVolume: Boolean get() = true;
    override val canSetSpeed: Boolean get() = false; //TODO: Implement

    var addresses: Array<InetAddress>? = null;
    var port: Int = 0;

    private var _streamType: String? = null;
    private var _contentType: String? = null;
    private var _contentId: String? = null;

    private var _socket: SSLSocket? = null;
    private var _outputStream: DataOutputStream? = null;
    private var _outputStreamLock = Object();
    private var _inputStream: DataInputStream? = null;
    private var _inputStreamLock = Object();
    private var _scopeIO: CoroutineScope? = null;
    private var _requestId = 1;
    private var _started: Boolean = false;
    private var _sessionId: String? = null;
    private var _transportId: String? = null;
    private var _launching = false;
    private var _mediaSessionId: Int? = null;
    private var _thread: Thread? = null;
    private var _pingThread: Thread? = null;
    private var _launchRetries = 0
    private val MAX_LAUNCH_RETRIES = 3
    private var _lastLaunchTime_ms = 0L
    private var _retryJob: Job? = null

    constructor(name: String, addresses: Array<InetAddress>, port: Int) : super() {
        this.name = name;
        this.addresses = addresses;
        this.port = port;
    }

    constructor(deviceInfo: CastingDeviceInfo) : super() {
        this.name = deviceInfo.name;
        this.addresses = deviceInfo.addresses.map { a -> a.toInetAddress() }.filterNotNull().toTypedArray();
        this.port = deviceInfo.port;
    }

    override fun getAddresses(): List<InetAddress> {
        return addresses?.toList() ?: listOf();
    }

    override fun loadVideo(streamType: String, contentType: String, contentId: String, resumePosition: Double, duration: Double, speed: Double?) {
        if (invokeInIOScopeIfRequired({ loadVideo(streamType, contentType, contentId, resumePosition, duration, speed) })) {
            return;
        }

        Logger.i(TAG, "Start streaming (streamType: $streamType, contentType: $contentType, contentId: $contentId, resumePosition: $resumePosition, duration: $duration, speed: $speed)");

        setTime(resumePosition);
        setDuration(duration);
        _streamType = streamType;
        _contentType = contentType;
        _contentId = contentId;

        playVideo();
    }

    override fun loadContent(contentType: String, content: String, resumePosition: Double, duration: Double, speed: Double?) {
        //TODO: Can maybe be implemented by sending data:contentType,base64...
        throw NotImplementedError();
    }

    private fun connectMediaChannel(transportId: String) {
        val connectObject = JSONObject();
        connectObject.put("type", "CONNECT");
        connectObject.put("connType", 0);
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.tp.connection", connectObject.toString());
    }

    private fun requestMediaStatus() {
        val transportId = _transportId ?: return;

        val loadObject = JSONObject();
        loadObject.put("type", "GET_STATUS");
        loadObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.media", loadObject.toString());
    }

    private fun playVideo() {
        val transportId = _transportId ?: return;
        val contentId = _contentId ?: return;
        val streamType = _streamType ?: return;
        val contentType = _contentType ?: return;

        val loadObject = JSONObject();
        loadObject.put("type", "LOAD");

        val mediaObject =  JSONObject();
        mediaObject.put("contentId", contentId);
        mediaObject.put("streamType", streamType);
        mediaObject.put("contentType", contentType);

        if (time > 0.0) {
            val seekTime = time;
            loadObject.put("currentTime", seekTime);
        }

        loadObject.put("media", mediaObject);
        loadObject.put("requestId", _requestId++);


        //TODO: This replace is necessary to get rid of backward slashes added by the JSON Object serializer
        val json = loadObject.toString().replace("\\/","/");
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.media", json);
    }

    override fun changeVolume(volume: Double) {
        if (invokeInIOScopeIfRequired({ changeVolume(volume) })) {
            return;
        }

        setVolume(volume)
        val setVolumeObject = JSONObject();
        setVolumeObject.put("type", "SET_VOLUME");

        val volumeObject = JSONObject();
        volumeObject.put("level", volume)
        setVolumeObject.put("volume", volumeObject);

        setVolumeObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.receiver", setVolumeObject.toString());
    }

    override fun seekVideo(timeSeconds: Double) {
        if (invokeInIOScopeIfRequired({ seekVideo(timeSeconds) })) {
            return;
        }

        val transportId = _transportId ?: return;
        val mediaSessionId = _mediaSessionId ?: return;

        val loadObject = JSONObject();
        loadObject.put("type", "SEEK");
        loadObject.put("mediaSessionId", mediaSessionId);
        loadObject.put("requestId", _requestId++);
        loadObject.put("currentTime", timeSeconds);
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.media", loadObject.toString());
    }

    override fun resumeVideo() {
        if (invokeInIOScopeIfRequired(::resumeVideo)) {
            return;
        }

        val transportId = _transportId ?: return;
        val mediaSessionId = _mediaSessionId ?: return;

        val loadObject = JSONObject();
        loadObject.put("type", "PLAY");
        loadObject.put("mediaSessionId", mediaSessionId);
        loadObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.media", loadObject.toString());
    }

    override fun pauseVideo() {
        if (invokeInIOScopeIfRequired(::pauseVideo)) {
            return;
        }

        val transportId = _transportId ?: return;
        val mediaSessionId = _mediaSessionId ?: return;

        val loadObject = JSONObject();
        loadObject.put("type", "PAUSE");
        loadObject.put("mediaSessionId", mediaSessionId);
        loadObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.media", loadObject.toString());
    }

    override fun stopVideo() {
        if (invokeInIOScopeIfRequired(::stopVideo)) {
            return;
        }

        val transportId = _transportId ?: return;
        val mediaSessionId = _mediaSessionId ?: return;
        _contentId = null;
        _contentType = null;
        _streamType = null;

        val loadObject = JSONObject();
        loadObject.put("type", "STOP");
        loadObject.put("mediaSessionId", mediaSessionId);
        loadObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", transportId, "urn:x-cast:com.google.cast.media", loadObject.toString());
    }

    private fun launchPlayer() {
        if (invokeInIOScopeIfRequired(::launchPlayer)) {
            return;
        }

        val launchObject = JSONObject();
        launchObject.put("type", "LAUNCH");
        launchObject.put("appId", "CC1AD845");
        launchObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.receiver", launchObject.toString());
        _lastLaunchTime_ms = System.currentTimeMillis()
    }

    private fun getStatus() {
        if (invokeInIOScopeIfRequired(::getStatus)) {
            return;
        }

        val launchObject = JSONObject();
        launchObject.put("type", "GET_STATUS");
        launchObject.put("requestId", _requestId++);
        sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.receiver", launchObject.toString());
    }

    private fun invokeInIOScopeIfRequired(action: () -> Unit): Boolean {
        if(Looper.getMainLooper().thread == Thread.currentThread()) {
            _scopeIO?.launch { action(); }
            return true;
        }

        return false;
    }

    override fun stopCasting() {
        if (invokeInIOScopeIfRequired(::stopCasting)) {
            return;
        }

        val sessionId = _sessionId;
        if (sessionId != null) {
            val launchObject = JSONObject();
            launchObject.put("type", "STOP");
            launchObject.put("sessionId", sessionId);
            launchObject.put("requestId", _requestId++);
            sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.receiver", launchObject.toString());

            _contentId = null;
            _contentType = null;
            _streamType = null;
            _sessionId = null;
            _launchRetries = 0
            _transportId = null;
        }

        Logger.i(TAG, "Stopping active device because stopCasting was called.")
        stop();
    }

    override fun start() {
        if (_started) {
            return;
        }

        _started = true;
        _sessionId = null;
        _launchRetries = 0
        _mediaSessionId = null;

        Logger.i(TAG, "Starting...");

        _launching = true;

        ensureThreadsStarted();
        Logger.i(TAG, "Started.");
    }

    fun ensureThreadsStarted() {
        val adrs = addresses ?: return;

        val thread = _thread
        val pingThread = _pingThread
        if (thread == null || !thread.isAlive || pingThread == null || !pingThread.isAlive) {
            Log.i(TAG, "Restarting threads because one of the threads has died")

            _scopeIO?.cancel();
            Logger.i(TAG, "Cancelled previous scopeIO because a new one is starting.")
            _scopeIO = CoroutineScope(Dispatchers.IO);

            _thread = Thread {
                connectionState = CastConnectionState.CONNECTING;

                var connectedSocket: Socket? = null
                while (_scopeIO?.isActive == true) {
                    try {
                        val resultSocket = getConnectedSocket(adrs.toList(), port);
                        if (resultSocket == null) {
                            Thread.sleep(1000);
                            continue;
                        }

                        connectedSocket = resultSocket
                        usedRemoteAddress = connectedSocket.inetAddress;
                        localAddress = connectedSocket.localAddress;
                        break;
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to get setup initial connection to ChromeCast device.", e)
                        Thread.sleep(1000);
                    }
                }

                val sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, null);

                val factory = sslContext.socketFactory;

                val address = InetSocketAddress(usedRemoteAddress, port)

                //Connection loop
                while (_scopeIO?.isActive == true) {
                    _sessionId = null;
                    _launchRetries = 0
                    _mediaSessionId = null;

                    Logger.i(TAG, "Connecting to Chromecast.");
                    connectionState = CastConnectionState.CONNECTING;

                    try {
                        _socket?.close()
                        if (connectedSocket != null) {
                            Logger.i(TAG, "Using connected socket.")
                            _socket = factory.createSocket(connectedSocket, connectedSocket.inetAddress.hostAddress, connectedSocket.port, true) as SSLSocket
                            connectedSocket = null
                        } else {
                            Logger.i(TAG, "Using new socket.")
                            val s = Socket().apply { this.connect(address, 2000) }
                            _socket = factory.createSocket(s, s.inetAddress.hostAddress, s.port, true) as SSLSocket
                        }

                        _socket?.startHandshake();
                        Logger.i(TAG, "Successfully connected to Chromecast at $usedRemoteAddress:$port");

                        try {
                            _outputStream = DataOutputStream(_socket?.outputStream);
                            _inputStream = DataInputStream(_socket?.inputStream);
                        } catch (e: Throwable) {
                            Logger.i(TAG, "Failed to authenticate to Chromecast.", e);
                        }
                    } catch (e: Throwable) {
                        _socket?.close();
                        Logger.i(TAG, "Failed to connect to Chromecast.", e);

                        connectionState = CastConnectionState.CONNECTING;
                        Thread.sleep(1000);
                        continue;
                    }

                    localAddress = _socket?.localAddress;

                    try {
                        val connectObject = JSONObject();
                        connectObject.put("type", "CONNECT");
                        connectObject.put("connType", 0);
                        sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.connection", connectObject.toString());
                    } catch (e: Throwable) {
                        Logger.i(TAG, "Failed to send connect message to Chromecast.", e);
                        _socket?.close();

                        connectionState = CastConnectionState.CONNECTING;
                        Thread.sleep(1000);
                        continue;
                    }

                    getStatus();

                    val buffer = ByteArray(409600);

                    Logger.i(TAG, "Started receiving.");
                    while (_scopeIO?.isActive == true) {
                        try {
                            val inputStream = _inputStream ?: break;

                            val message = synchronized(_inputStreamLock)
                            {
                                Log.d(TAG, "Receiving next packet...");
                                val b1 = inputStream.readUnsignedByte();
                                val b2 = inputStream.readUnsignedByte();
                                val b3 = inputStream.readUnsignedByte();
                                val b4 = inputStream.readUnsignedByte();
                                val size =
                                    ((b1.toLong() shl 24) or (b2.toLong() shl 16) or (b3.toLong() shl 8) or b4.toLong()).toInt();
                                if (size > buffer.size) {
                                    Logger.w(TAG, "Skipping packet that is too large $size bytes.")
                                    inputStream.skip(size.toLong());
                                    return@synchronized null
                                }

                                Log.d(TAG, "Received header indicating $size bytes. Waiting for message.");
                                inputStream.read(buffer, 0, size);

                                //TODO: In the future perhaps this size-1 will cause issues, why is there a 0 on the end?
                                val messageBytes = buffer.sliceArray(IntRange(0, size - 1));
                                Log.d(TAG, "Received $size bytes: ${messageBytes.toHexString()}.");
                                val msg = ChromeCast.CastMessage.parseFrom(messageBytes);
                                if (msg.namespace != "urn:x-cast:com.google.cast.tp.heartbeat") {
                                    Logger.i(TAG, "Received message: $msg");
                                }
                                return@synchronized msg
                            }

                            if (message != null) {
                                try {
                                    handleMessage(message);
                                } catch (e: Throwable) {
                                    Logger.w(TAG, "Failed to handle message.", e);
                                    break
                                }
                            }
                        } catch (e: java.net.SocketException) {
                            Logger.e(TAG, "Socket exception while receiving.", e);
                            break;
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Exception while receiving.", e);
                            break;
                        }
                    }
                    _socket?.close();
                    Logger.i(TAG, "Socket disconnected.");

                    connectionState = CastConnectionState.CONNECTING;
                    Thread.sleep(1000);
                }

                Logger.i(TAG, "Stopped connection loop.");
                connectionState = CastConnectionState.DISCONNECTED;
            }.apply { start() };

            //Start ping loop
            _pingThread = Thread {
                Logger.i(TAG, "Started ping loop.")

                val pingObject = JSONObject();
                pingObject.put("type", "PING");

                while (_scopeIO?.isActive == true) {
                    try {
                        sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.heartbeat", pingObject.toString());
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to send ping.");
                    }

                    Thread.sleep(5000);
                }

                Logger.i(TAG, "Stopped ping loop.");
            }.apply { start() };
        } else {
            Log.i(TAG, "Threads still alive, not restarted")
        }
    }

    private fun sendChannelMessage(sourceId: String, destinationId: String, namespace: String, json: String) {
        try {
            val castMessage = ChromeCast.CastMessage.newBuilder()
                .setProtocolVersion(ChromeCast.CastMessage.ProtocolVersion.CASTV2_1_0)
                .setSourceId(sourceId)
                .setDestinationId(destinationId)
                .setNamespace(namespace)
                .setPayloadType(ChromeCast.CastMessage.PayloadType.STRING)
                .setPayloadUtf8(json)
                .build();

            sendMessage(castMessage.toByteArray());

            if (namespace != "urn:x-cast:com.google.cast.tp.heartbeat") {
                //Log.d(TAG, "Sent channel message: $castMessage");
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to send channel message (sourceId: $sourceId, destinationId: $destinationId, namespace: $namespace, json: $json)", e);
            _socket?.close();
            Logger.i(TAG, "Socket disconnected.");

            connectionState = CastConnectionState.CONNECTING;
        }
    }

    private fun handleMessage(message: ChromeCast.CastMessage) {
        if (message.payloadType == ChromeCast.CastMessage.PayloadType.STRING) {
            val jsonObject = JSONObject(message.payloadUtf8);
            val type = jsonObject.getString("type");
            if (type == "RECEIVER_STATUS") {
                val status = jsonObject.getJSONObject("status");

                var sessionIsRunning = false;
                if (status.has("applications")) {
                    val applications = status.getJSONArray("applications");

                    for (i in 0 until applications.length()) {
                        val applicationUpdate = applications.getJSONObject(i);

                        val appId = applicationUpdate.getString("appId");
                        Logger.i(TAG, "Status update received appId (appId: $appId)");

                        if (appId == "CC1AD845") {
                            sessionIsRunning = true;

                            if (_sessionId == null) {
                                connectionState = CastConnectionState.CONNECTED;
                                _sessionId = applicationUpdate.getString("sessionId");
                                _launchRetries = 0

                                val transportId = applicationUpdate.getString("transportId");
                                connectMediaChannel(transportId);
                                Logger.i(TAG, "Connected to media channel $transportId");
                                _transportId = transportId;

                                requestMediaStatus();
                                playVideo();
                            }
                        }
                    }
                }

                if (!sessionIsRunning) {
                    if (System.currentTimeMillis() - _lastLaunchTime_ms > 5000) {
                        _sessionId = null
                        _mediaSessionId = null
                        setTime(0.0)
                        _transportId = null

                        if (_launching && _launchRetries < MAX_LAUNCH_RETRIES) {
                            Logger.i(TAG, "No player yet; attempting launch #${_launchRetries + 1}")
                            _launchRetries++
                            launchPlayer()
                        } else if (!_launching && _launchRetries < MAX_LAUNCH_RETRIES) {
                            // Maybe the first GET_STATUS came back empty; still try launching
                            Logger.i(TAG, "Player not found; triggering launch #${_launchRetries + 1}")
                            _launching = true
                            _launchRetries++
                            launchPlayer()
                        } else {
                            Logger.e(TAG, "Player not found after $_launchRetries attempts; giving up.")
                            Logger.i(TAG, "Unable to start media receiver on device")
                            stop()
                        }
                    } else {
                        if (_retryJob == null) {
                            Logger.i(TAG, "Scheduled retry job over 5 seconds")
                            _retryJob = _scopeIO?.launch(Dispatchers.IO) {
                                delay(5000)
                                getStatus()
                                _retryJob = null
                            }
                        }
                    }
                } else {
                    _launching = false
                    _launchRetries = 0
                }

                val volume = status.getJSONObject("volume");
                //val volumeControlType = volume.getString("controlType");
                val volumeLevel = volume.getString("level").toDouble();
                val volumeMuted = volume.getBoolean("muted");
                //val volumeStepInterval = volume.getString("stepInterval").toFloat();
                setVolume(if (volumeMuted) 0.0 else volumeLevel);

                Logger.i(TAG, "Status update received volume (level: $volumeLevel, muted: $volumeMuted)");
            } else if (type == "MEDIA_STATUS") {
                val statuses = jsonObject.getJSONArray("status");
                for (i in 0 until statuses.length()) {
                    val status = statuses.getJSONObject(i);
                    _mediaSessionId = status.getInt("mediaSessionId");

                    val playerState = status.getString("playerState");
                    val currentTime = status.getDouble("currentTime");
                    if (status.has("media")) {
                        val media = status.getJSONObject("media")
                        if (media.has("duration")) {
                            setDuration(media.getDouble("duration"))
                        }
                    }

                    isPlaying = playerState == "PLAYING";
                    if (isPlaying ||  playerState == "PAUSED") {
                        setTime(currentTime);
                    }

                    val playbackRate = status.getInt("playbackRate");
                    Logger.i(TAG, "Media update received (mediaSessionId: $_mediaSessionId, playedState: $playerState, currentTime: $currentTime, playbackRate: $playbackRate)");

                    if (_contentType == null) {
                        stopVideo();
                    }
                }
            } else if (type == "CLOSE") {
                if (message.sourceId == "receiver-0") {
                    Logger.i(TAG, "Close received.");
                    stop();
                } else if (_transportId == message.sourceId) {
                    throw Exception("Transport id closed.")
                }
            }
        } else {
            throw Exception("Payload type ${message.payloadType} is not implemented.");
        }
    }

    private fun sendMessage(data: ByteArray) {
        val outputStream = _outputStream;
        if (outputStream == null) {
            Logger.w(TAG, "Failed to send ${data.size} bytes, output stream is null.");
            return;
        }

        synchronized(_outputStreamLock)
        {
            val serializedSizeBE = ByteArray(4);
            serializedSizeBE[0] = (data.size shr 24 and 0xff).toByte();
            serializedSizeBE[1] = (data.size shr 16 and 0xff).toByte();
            serializedSizeBE[2] = (data.size shr 8 and 0xff).toByte();
            serializedSizeBE[3] = (data.size and 0xff).toByte();
            outputStream.write(serializedSizeBE);
            outputStream.write(data);
        }

        //Log.d(TAG, "Sent ${data.size} bytes.");
    }

    override fun stop() {
        Logger.i(TAG, "Stopping...");
        usedRemoteAddress = null;
        localAddress = null;
        _started = false;

        _retryJob?.cancel()
        _retryJob = null

        val socket = _socket;
        val scopeIO = _scopeIO;

        if (scopeIO != null && socket != null) {
            Logger.i(TAG, "Cancelling scopeIO with open socket.")

            scopeIO.launch {
                socket.close();
                connectionState = CastConnectionState.DISCONNECTED;
                scopeIO.cancel();
                Logger.i(TAG, "Cancelled scopeIO with open socket.")
            }
        } else {
            scopeIO?.cancel();
            Logger.i(TAG, "Cancelled scopeIO without open socket.")
        }

        _pingThread = null;
        _thread = null;
        _scopeIO = null;
        _socket = null;
        _outputStream = null;
        _inputStream = null;
        _mediaSessionId = null;
        connectionState = CastConnectionState.DISCONNECTED;
    }

    override fun getDeviceInfo(): CastingDeviceInfo {
        return CastingDeviceInfo(name!!, CastProtocolType.CHROMECAST, addresses!!.filter { a -> a.hostAddress != null }.map { a -> a.hostAddress!!  }.toTypedArray(), port);
    }

    companion object {
        val TAG = "ChromecastCastingDevice";

        val trustAllCerts: Array<TrustManager> = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) { }
            override fun getAcceptedIssuers(): Array<X509Certificate> { return emptyArray(); }
        });
    }
}