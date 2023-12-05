package com.futo.platformplayer.casting

import android.os.Looper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.getConnectedSocket
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.toInetAddress
import kotlinx.coroutines.*
import java.net.InetAddress
import java.util.UUID

class AirPlayCastingDevice : CastingDevice {
    //See for more info: https://nto.github.io/AirPlay

    override val protocol: CastProtocolType get() = CastProtocolType.AIRPLAY;
    override val isReady: Boolean get() = name != null && addresses != null && addresses?.isNotEmpty() == true && port != 0;
    override var usedRemoteAddress: InetAddress? = null;
    override var localAddress: InetAddress? = null;
    override val canSetVolume: Boolean get() = false;

    var addresses: Array<InetAddress>? = null;
    var port: Int = 0;

    private var _scopeIO: CoroutineScope? = null;
    private var _started: Boolean = false;
    private var _sessionId: String? = null;
    private val _client = ManagedHttpClient();

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

    override fun loadVideo(streamType: String, contentType: String, contentId: String, resumePosition: Double, duration: Double) {
        if (invokeInIOScopeIfRequired({ loadVideo(streamType, contentType, contentId, resumePosition, duration) })) {
            return;
        }

        Logger.i(FCastCastingDevice.TAG, "Start streaming (streamType: $streamType, contentType: $contentType, contentId: $contentId, resumePosition: $resumePosition, duration: $duration)");

        time = resumePosition;
        if (resumePosition > 0.0) {
            val pos = resumePosition / duration;
            Logger.i(TAG, "resumePosition: $resumePosition, duration: ${duration}, pos: $pos")
            post("play", "text/parameters", "Content-Location: $contentId\r\nStart-Position: $pos");
        } else {
            post("play", "text/parameters", "Content-Location: $contentId\r\nStart-Position: 0");
        }
    }

    override fun loadContent(contentType: String, content: String, resumePosition: Double, duration: Double) {
        throw NotImplementedError();
    }

    override fun seekVideo(timeSeconds: Double) {
        if (invokeInIOScopeIfRequired({ seekVideo(timeSeconds) })) {
            return;
        }

        post("scrub?position=${timeSeconds}");
    }

    override fun resumeVideo() {
        if (invokeInIOScopeIfRequired(::resumeVideo)) {
            return;
        }

        isPlaying = true;
        post("rate?value=1.000000");
    }

    override fun pauseVideo() {
        if (invokeInIOScopeIfRequired(::pauseVideo)) {
            return;
        }

        isPlaying = false;
        post("rate?value=0.000000");
    }

    override fun stopVideo() {
        if (invokeInIOScopeIfRequired(::stopVideo)) {
            return;
        }

        post("stop");
    }

    override fun stopCasting() {
        if (invokeInIOScopeIfRequired(::stopCasting)) {
            return;
        }

        post("stop");
        stop();
    }

    override fun start() {
        val adrs = addresses ?: return;
        if (_started) {
            return;
        }

        _started = true;
        _scopeIO?.cancel();
        _scopeIO = CoroutineScope(Dispatchers.IO);

        Logger.i(TAG, "Starting...");

        _scopeIO?.launch {
            try {
                connectionState = CastConnectionState.CONNECTING;

                while (_scopeIO?.isActive == true) {
                    try {
                        val connectedSocket = getConnectedSocket(adrs.toList(), port);
                        if (connectedSocket == null) {
                            delay(3000);
                            continue;
                        }

                        usedRemoteAddress = connectedSocket.inetAddress;
                        localAddress = connectedSocket.localAddress;
                        connectedSocket.close();
                        _sessionId = UUID.randomUUID().toString();
                        break;
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to get setup initial connection to AirPlay device.", e)
                    }
                }

                while (_scopeIO?.isActive == true) {
                    try {
                        val progressInfo = getProgress();
                        if (progressInfo == null) {
                            connectionState = CastConnectionState.CONNECTING;
                            Logger.i(TAG, "Failed to retrieve progress from AirPlay device.");
                            delay(1000);
                            continue;
                        }

                        connectionState = CastConnectionState.CONNECTED;
                        delay(1000);

                        val progressIndex = progressInfo.lowercase().indexOf("position: ");
                        if (progressIndex == -1) {
                            continue;
                        }

                        val progress = progressInfo.substring(progressIndex + "position: ".length).toDoubleOrNull() ?: continue;

                        time = progress;
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to get server info from AirPlay device.", e)
                    }
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to setup AirPlay device connection.", e)
            }
        };

        Logger.i(TAG, "Started.");
    }

    override fun stop() {
        Logger.i(TAG, "Stopping...");
        connectionState = CastConnectionState.DISCONNECTED;

        usedRemoteAddress = null;
        localAddress = null;
        _started = false;
        _scopeIO?.cancel();
        _scopeIO = null;
    }

    override fun getDeviceInfo(): CastingDeviceInfo {
        return CastingDeviceInfo(name!!, CastProtocolType.AIRPLAY, addresses!!.filter { a -> a.hostAddress != null }.map { a -> a.hostAddress!!  }.toTypedArray(), port);
    }

    private fun getProgress(): String? {
        val info = get("scrub");
        Logger.i(TAG, "Progress: ${info ?: "null"}");
        return info;
    }

    private fun getPlaybackInfo(): String? {
        val playbackInfo = get("playback-info");
        Logger.i(TAG, "Playback info: ${playbackInfo ?: "null"}");
        return playbackInfo;
    }

    private fun getServerInfo(): String? {
        val serverInfo = get("server-info");
        Logger.i(TAG, "Server info: ${serverInfo ?: "null"}");
        return serverInfo;
    }

    private fun post(path: String): Boolean {
        try {
            val sessionId = _sessionId ?: return false;

            val headers = hashMapOf(
                "X-Apple-Device-ID" to "0xdc2b61a0ce79",
                "User-Agent" to "MediaControl/1.0",
                "Content-Length" to "0",
                "X-Apple-Session-ID" to sessionId
            );

            val url = "http://${usedRemoteAddress}:${port}/${path}";

            Logger.i(TAG, "POST $url");
            val response = _client.post(url, headers);
            if (!response.isOk) {
                return false;
            }

            return true;
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to POST $path");
            return false;
        }
    }

    private fun post(path: String, contentType: String, body: String): Boolean {
        try {
            val sessionId = _sessionId ?: return false;

            val headers = hashMapOf(
                "X-Apple-Device-ID" to "0xdc2b61a0ce79",
                "User-Agent" to "MediaControl/1.0",
                "X-Apple-Session-ID" to sessionId,
                "Content-Type" to contentType
            );

            val url = "http://${usedRemoteAddress}:${port}/${path}";

            Logger.i(TAG, "POST $url:\n$body");
            val response = _client.post(url, body, headers);
            if (!response.isOk) {
                return false;
            }

            return true;
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to POST $path $body");
            return false;
        }
    }

    private fun get(path: String): String? {
        val sessionId = _sessionId ?: return null;

        try {
            val headers = hashMapOf(
                "X-Apple-Device-ID" to "0xdc2b61a0ce79",
                "Content-Length" to "0",
                "User-Agent" to "MediaControl/1.0",
                "X-Apple-Session-ID" to sessionId
            );

            val url = "http://${usedRemoteAddress}:${port}/${path}";

            Logger.i(TAG, "GET $url");
            val response = _client.get(url, headers);
            if (!response.isOk) {
                return null;
            }

            if (response.body == null) {
                return null;
            }

            return response.body.string();
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to GET $path");
            return null;
        }
    }

    private fun invokeInIOScopeIfRequired(action: () -> Unit): Boolean {
        if(Looper.getMainLooper().thread == Thread.currentThread()) {
            _scopeIO?.launch { action(); }
            return true;
        }

        return false;
    }

    companion object {
        val TAG = "AirPlayCastingDevice";
    }
}