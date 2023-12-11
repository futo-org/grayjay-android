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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
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
    private var _inputStream: DataInputStream? = null;
    private var _scopeIO: CoroutineScope? = null;
    private var _requestId = 1;
    private var _started: Boolean = false;
    private var _sessionId: String? = null;
    private var _transportId: String? = null;
    private var _launching = false;
    private var _mediaSessionId: Int? = null;

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

        time = resumePosition;
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

        this.volume = volume
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
            _transportId = null;
        }

        Logger.i(TAG, "Stopping active device because stopCasting was called.")
        stop();
    }

    override fun start() {
        val adrs = addresses ?: return;
        if (_started) {
            return;
        }

        _started = true;
        _sessionId = null;
        _mediaSessionId = null;

        Logger.i(TAG, "Starting...");

        _launching = true;

        _scopeIO?.cancel();
        Logger.i(TAG, "Cancelled previous scopeIO because a new one is starting.")
        _scopeIO = CoroutineScope(Dispatchers.IO);

        Thread {
            connectionState = CastConnectionState.CONNECTING;

            while (_scopeIO?.isActive == true) {
                try {
                    val connectedSocket = getConnectedSocket(adrs.toList(), port);
                    if (connectedSocket == null) {
                        Thread.sleep(3000);
                        continue;
                    }

                    usedRemoteAddress = connectedSocket.inetAddress;
                    localAddress = connectedSocket.localAddress;
                    connectedSocket.close();
                    break;
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to get setup initial connection to ChromeCast device.", e)
                }
            }

            val sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, null);

            val factory = sslContext.socketFactory;

            //Connection loop
            while (_scopeIO?.isActive == true) {
                Logger.i(TAG, "Connecting to Chromecast.");
                connectionState = CastConnectionState.CONNECTING;

                try {
                    _socket?.close()
                    _socket = factory.createSocket(usedRemoteAddress, port) as SSLSocket;
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
                    Thread.sleep(3000);
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
                    Thread.sleep(3000);
                    continue;
                }

                getStatus();

                val buffer = ByteArray(4096);

                Logger.i(TAG, "Started receiving.");
                while (_scopeIO?.isActive == true) {
                    try {
                        val inputStream = _inputStream ?: break;
                        Log.d(TAG, "Receiving next packet...");
                        val b1 = inputStream.readUnsignedByte();
                        val b2 = inputStream.readUnsignedByte();
                        val b3 = inputStream.readUnsignedByte();
                        val b4 = inputStream.readUnsignedByte();
                        val size = ((b1.toLong() shl 24) or (b2.toLong() shl 16) or (b3.toLong() shl 8) or b4.toLong()).toInt();
                        if (size > buffer.size) {
                            Logger.w(TAG, "Skipping packet that is too large $size bytes.")
                            inputStream.skip(size.toLong());
                            continue;
                        }

                        Log.d(TAG, "Received header indicating $size bytes. Waiting for message.");
                        inputStream.read(buffer, 0, size);

                        //TODO: In the future perhaps this size-1 will cause issues, why is there a 0 on the end?
                        val messageBytes = buffer.sliceArray(IntRange(0, size - 1));
                        Log.d(TAG, "Received $size bytes: ${messageBytes.toHexString()}.");
                        val message = ChromeCast.CastMessage.parseFrom(messageBytes);
                        if (message.namespace != "urn:x-cast:com.google.cast.tp.heartbeat") {
                            Logger.i(TAG, "Received message: $message");
                        }

                        try {
                            handleMessage(message);
                        } catch (e:Throwable) {
                            Logger.w(TAG, "Failed to handle message.", e);
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
                Thread.sleep(3000);
            }

            Logger.i(TAG, "Stopped connection loop.");
            connectionState = CastConnectionState.DISCONNECTED;
        }.start();

        //Start ping loop
        Thread {
            Logger.i(TAG, "Started ping loop.")

            val pingObject = JSONObject();
            pingObject.put("type", "PING");

            while (_scopeIO?.isActive == true) {
                try {
                    sendChannelMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.heartbeat", pingObject.toString());
                    Thread.sleep(5000);
                } catch (e: Throwable) {

                }
            }

            Logger.i(TAG, "Stopped ping loop.");
        }.start();

        Logger.i(TAG, "Started.");
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
                    _sessionId = null;
                    _mediaSessionId = null;
                    time = 0.0;
                    _transportId = null;
                    Logger.w(TAG, "Session not found.");

                    if (_launching) {
                        Logger.i(TAG, "Player not found, launching.");
                        launchPlayer();
                    } else {
                        Logger.i(TAG, "Player not found, disconnecting.");
                        stop();
                    }
                } else {
                    _launching = false;
                }

                val volume = status.getJSONObject("volume");
                //val volumeControlType = volume.getString("controlType");
                val volumeLevel = volume.getString("level").toDouble();
                val volumeMuted = volume.getBoolean("muted");
                //val volumeStepInterval = volume.getString("stepInterval").toFloat();
                this.volume = if (volumeMuted) 0.0 else volumeLevel;

                Logger.i(TAG, "Status update received volume (level: $volumeLevel, muted: $volumeMuted)");
            } else if (type == "MEDIA_STATUS") {
                val statuses = jsonObject.getJSONArray("status");
                for (i in 0 until statuses.length()) {
                    val status = statuses.getJSONObject(i);
                    _mediaSessionId = status.getInt("mediaSessionId");

                    val playerState = status.getString("playerState");
                    val currentTime = status.getDouble("currentTime");

                    isPlaying = playerState == "PLAYING";
                    if (isPlaying) {
                        time = currentTime;
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

        val serializedSizeBE = ByteArray(4);
        serializedSizeBE[0] = (data.size shr 24 and 0xff).toByte();
        serializedSizeBE[1] = (data.size shr 16 and 0xff).toByte();
        serializedSizeBE[2] = (data.size shr 8 and 0xff).toByte();
        serializedSizeBE[3] = (data.size and 0xff).toByte();
        outputStream.write(serializedSizeBE);
        outputStream.write(data);

        //Log.d(TAG, "Sent ${data.size} bytes.");
    }

    override fun stop() {
        Logger.i(TAG, "Stopping...");
        usedRemoteAddress = null;
        localAddress = null;
        _started = false;

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