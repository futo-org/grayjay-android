package com.futo.platformplayer.casting.dlna

import com.futo.platformplayer.logging.Logger
import org.fcast.sender_sdk.ApplicationInfo
import org.fcast.sender_sdk.CastingDeviceException
import org.fcast.sender_sdk.CastingDeviceInterface
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceEventHandler
import org.fcast.sender_sdk.DeviceFeature
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.FwrtcSignaller
import org.fcast.sender_sdk.IpAddr
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaItem
import org.fcast.sender_sdk.MediaTrackType
import org.fcast.sender_sdk.Metadata
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.Queue
import org.fcast.sender_sdk.QueueItem
import org.fcast.sender_sdk.QueuePosition
import org.fcast.sender_sdk.SubtitleSource
import org.fcast.sender_sdk.tryIpAddrFromStr
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Casting device backed by a UPnP/DLNA MediaRenderer (AVTransport + RenderingControl over SOAP).
 *
 * Implements the sender SDK device interface so it can be wrapped by [com.futo.platformplayer.casting.CastingDevice]
 * like the FCast and Chromecast devices. All network calls run on a single background thread.
 */
class DlnaCastingDevice(
    private var _name: String,
    val location: String,
    private var _description: DlnaDescription? = null
) : CastingDeviceInterface {
    private val _client = DlnaSoap.createClient()
    private var _executor: ScheduledExecutorService? = null
    private var _pollTask: ScheduledFuture<*>? = null
    private var _handler: DeviceEventHandler? = null

    @Volatile private var _connected = false
    private var _connectedState: DeviceConnectionState.Connected? = null
    private var _consecutiveFailures = 0

    private var _lastState: String? = null
    private var _lastTime = -1.0
    private var _lastDuration = -1.0
    private var _lastVolume = -1.0
    private var _pollCount = 0
    private var _pendingSeek: Double? = null
    private var _seekTarget: Double? = null
    private var _seekTask: ScheduledFuture<*>? = null
    private var _userStopped = false
    private var _hasLoaded = false
    @Volatile private var _showingImage = false

    val host: String get() = URL(location).host
    val port: Int get() = URL(location).let { if (it.port > 0) it.port else it.defaultPort }

    override fun castingProtocol(): ProtocolType = ProtocolType.F_CAST // Unused for DLNA, see CastingDevice.protocolType
    override fun isReady(): Boolean = true
    override fun supportsFeature(feature: DeviceFeature): Boolean = when (feature) {
        DeviceFeature.SET_VOLUME -> _description?.renderingControlUrl != null
        DeviceFeature.LOAD_URL -> true
        else -> false
    }

    override fun name(): String = _name
    override fun setName(name: String) { _name = name }

    override fun connect(appInfo: ApplicationInfo?, eventHandler: DeviceEventHandler, reconnectIntervalMillis: ULong) {
        if (_executor != null) {
            throw CastingDeviceException.DeviceAlreadyStarted("DLNA device is already started")
        }

        _handler = eventHandler
        val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "DlnaCastingDevice").apply { isDaemon = true } }
        _executor = executor

        eventHandler.connectionStateChanged(DeviceConnectionState.Connecting)
        executor.execute {
            try {
                val description = _description ?: fetchDescription()
                _description = description

                val remote = URL(description.avTransportControlUrl)
                val (usedRemote, local) = Socket().use { socket ->
                    socket.connect(InetSocketAddress(remote.host, if (remote.port > 0) remote.port else remote.defaultPort), CONNECT_TIMEOUT_MS)
                    Pair(socket.inetAddress.hostAddress!!, socket.localAddress.hostAddress!!)
                }

                val connectedState = DeviceConnectionState.Connected(toIpAddr(usedRemote), toIpAddr(local), null)
                _connectedState = connectedState
                _connected = true
                _consecutiveFailures = 0
                eventHandler.connectionStateChanged(connectedState)
                _pollTask = executor.scheduleWithFixedDelay({ poll() }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to connect to DLNA renderer at $location", e)
                eventHandler.connectionStateChanged(DeviceConnectionState.Disconnected)
            }
        }
    }

    override fun disconnect() {
        val wasConnected = _connected || _executor != null
        _connected = false
        _pollTask?.cancel(false)
        _pollTask = null
        _executor?.shutdown()
        _executor = null
        val handler = _handler
        _handler = null
        if (wasConnected) {
            handler?.connectionStateChanged(DeviceConnectionState.Disconnected)
        }
    }

    override fun load(request: LoadRequest, progressUpdateIntervalMillis: ULong?) {
        val (url, contentType, resumePosition, metadata) = when (request) {
            is LoadRequest.Video -> LoadParams(request.url, request.contentType, request.resumePosition, request.metadata)
            is LoadRequest.Url -> LoadParams(request.url, request.contentType, request.resumePosition ?: 0.0, request.metadata)
            else -> throw CastingDeviceException.UnsupportedFeature("DLNA renderers can only play media from a URL")
        }

        submit("load") {
            _userStopped = false
            _hasLoaded = true
            _showingImage = false
            _lastTime = -1.0
            _lastDuration = -1.0
            _pendingSeek = if (resumePosition > 1.0) resumePosition else null
            setUri(url, contentType, metadata?.title, metadata?.thumbnailUrl)
        }
    }

    /**
     * Shows a still image (e.g. a status screen) and waits until the renderer accepted it. Playback events are not
     * reported while it is shown.
     */
    fun showImage(url: String, title: String?, timeoutMs: Long = 5000) {
        val executor = _executor ?: return
        val future = executor.submit {
            try {
                _showingImage = true
                _pendingSeek = null
                setUri(url, "image/jpeg", title, null)
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to show image: ${e.message}")
            }
        }
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            Logger.w(TAG, "Showing image did not complete: ${e.message}")
        }
    }

    private fun setUri(url: String, contentType: String, title: String?, thumbnailUrl: String?) {
        val avt = requireDescription().avTransportControlUrl

        //Some renderers refuse a new URI while playing, and Samsung locks up when it arrives before Stop completes.
        soapOrNull(avt, AV_TRANSPORT, "Stop", INSTANCE)
        for (i in 0 until STOP_WAIT_ATTEMPTS) {
            val state = DlnaSoap.tag(soapOrNull(avt, AV_TRANSPORT, "GetTransportInfo", INSTANCE), "CurrentTransportState")
            if (state == null || state == "STOPPED" || state == "NO_MEDIA_PRESENT") break
            Thread.sleep(STOP_WAIT_INTERVAL_MS)
        }

        val didl = DlnaSoap.buildDidl(url, contentType, title, thumbnailUrl)
        soap(avt, AV_TRANSPORT, "SetAVTransportURI",
            INSTANCE + "<CurrentURI>${DlnaSoap.escape(url)}</CurrentURI><CurrentURIMetaData>${DlnaSoap.escape(didl)}</CurrentURIMetaData>")
        soap(avt, AV_TRANSPORT, "Play", "$INSTANCE<Speed>1</Speed>")
    }

    /**
     * Debounced: dragging a seek bar produces dozens of calls per second, which renderers reject while transitioning.
     */
    @Synchronized
    override fun seek(timeSeconds: Double) {
        val executor = _executor ?: throw CastingDeviceException.FailedToSendCommand("Device is not connected")
        _seekTarget = timeSeconds
        _seekTask?.cancel(false)
        _seekTask = executor.schedule({ performSeek(SEEK_RETRIES) }, SEEK_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun performSeek(retriesLeft: Int) {
        val target = synchronized(this) { _seekTarget } ?: return
        val desc = _description ?: return
        val result = try {
            DlnaSoap.invoke(_client, desc.avTransportControlUrl, AV_TRANSPORT, "Seek",
                "$INSTANCE<Unit>REL_TIME</Unit><Target>${DlnaSoap.formatTime(target)}</Target>")
        } catch (e: Throwable) {
            DlnaSoap.Result(-1, e.message ?: "")
        }
        synchronized(this) {
            if (_seekTarget != target) return //A newer seek is scheduled
            if (result.isOk || retriesLeft <= 0) {
                if (!result.isOk) Logger.e(TAG, "Seek to $target failed (${result.code}): ${result.error ?: result.body}")
                _seekTarget = null
                return
            }
            _seekTask = _executor?.schedule({ performSeek(retriesLeft - 1) }, SEEK_RETRY_MS, TimeUnit.MILLISECONDS)
        }
    }

    override fun pausePlayback() = submit("pause") {
        soap(requireDescription().avTransportControlUrl, AV_TRANSPORT, "Pause", INSTANCE)
    }

    override fun resumePlayback() = submit("resume") {
        //Samsung resumes playback by itself after a seek and then rejects Play with "Transition not available".
        if (_lastState == "PLAYING") return@submit
        //Without our own load the renderer would replay whatever URI it last had, possibly from another sender.
        if (!_hasLoaded) return@submit
        soap(requireDescription().avTransportControlUrl, AV_TRANSPORT, "Play", "$INSTANCE<Speed>1</Speed>")
    }

    override fun stopPlayback() = submit("stop") {
        _userStopped = true
        _pendingSeek = null
        soap(requireDescription().avTransportControlUrl, AV_TRANSPORT, "Stop", INSTANCE)
    }

    override fun changeVolume(volume: Double) = submit("changeVolume") {
        val rc = requireDescription().renderingControlUrl ?: throw CastingDeviceException.UnsupportedFeature("Renderer has no RenderingControl service")
        val desired = (volume.coerceIn(0.0, 1.0) * 100.0).roundToInt()
        soap(rc, RENDERING_CONTROL, "SetVolume", "$INSTANCE<Channel>Master</Channel><DesiredVolume>$desired</DesiredVolume>")
        _lastVolume = desired / 100.0
    }

    override fun changeSpeed(speed: Double) {
        throw CastingDeviceException.UnsupportedFeature("DLNA renderers do not support changing playback speed")
    }

    override fun getDeviceInfo(): DeviceInfo =
        DeviceInfo(_name, ProtocolType.F_CAST, getAddresses(), port.toUShort(), mapOf(TXT_LOCATION to location))

    override fun getAddresses(): List<IpAddr> = try { listOf(toIpAddr(host)) } catch (_: Throwable) { listOf() }
    override fun setAddresses(addrs: List<IpAddr>) { }
    override fun getPort(): UShort = port.toUShort()
    override fun setPort(port: UShort) { }

    override fun playlistItemNext() = unsupported()
    override fun playlistItemPrevious() = unsupported()
    override fun setPlaylistItemIndex(index: UInt) = unsupported()
    override fun startMirroringSession(signaller: FwrtcSignaller) = unsupported()
    override fun changeTrack(id: UInt?, trackType: MediaTrackType) { }
    override fun queueRemove(position: QueuePosition) = unsupported()
    override fun queueAdd(item: QueueItem, position: QueuePosition) = unsupported()
    override fun queueSelect(position: QueuePosition) = unsupported()
    override fun loadQueue(queue: Queue) = unsupported()
    override fun queueInsert(item: MediaItem, playbackDuration: Double?, position: QueuePosition) = unsupported()
    override fun addSubtitleSource(subtitle: SubtitleSource) { }
    override fun setProgressUpdateInterval(intervalMillis: ULong) { }

    private fun poll() {
        if (!_connected) return
        val desc = _description ?: return
        val handler = _handler ?: return

        try {
            val transport = soap(desc.avTransportControlUrl, AV_TRANSPORT, "GetTransportInfo", INSTANCE)
            val position = soap(desc.avTransportControlUrl, AV_TRANSPORT, "GetPositionInfo", INSTANCE)
            if (_consecutiveFailures > 0) {
                _consecutiveFailures = 0
                _connectedState?.let { handler.connectionStateChanged(it) }
            }
            if (_showingImage) {
                _lastState = null
                return
            }

            val state = DlnaSoap.tag(transport, "CurrentTransportState")
            val time = DlnaSoap.parseTime(DlnaSoap.tag(position, "RelTime"))
            val duration = DlnaSoap.parseTime(DlnaSoap.tag(position, "TrackDuration"))

            if (duration != null && duration > 0 && abs(duration - _lastDuration) > 0.5) {
                _lastDuration = duration
                handler.durationChanged(duration)
            }
            if (time != null && state != "STOPPED" && state != "NO_MEDIA_PRESENT" && time != _lastTime) {
                _lastTime = time
                handler.timeChanged(time)
            }

            if (state != null && state != _lastState) {
                val previous = _lastState
                _lastState = state
                Logger.i(TAG, "Transport state $previous -> $state")
                when (state) {
                    "PLAYING" -> {
                        handler.playbackStateChanged(PlaybackState.PLAYING)
                        _pendingSeek?.let { target ->
                            _pendingSeek = null
                            soapOrNull(desc.avTransportControlUrl, AV_TRANSPORT, "Seek", "$INSTANCE<Unit>REL_TIME</Unit><Target>${DlnaSoap.formatTime(target)}</Target>")
                        }
                    }
                    "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> handler.playbackStateChanged(PlaybackState.PAUSED)
                    "TRANSITIONING" -> handler.playbackStateChanged(PlaybackState.BUFFERING)
                    "STOPPED", "NO_MEDIA_PRESENT" -> {
                        val reachedEnd = previous == "PLAYING" && _lastDuration > 0 && _lastTime >= _lastDuration - END_THRESHOLD_S
                        if (_hasLoaded && !_userStopped && reachedEnd) {
                            handler.playbackStateChanged(PlaybackState.ENDED)
                        } else {
                            handler.playbackStateChanged(PlaybackState.IDLE)
                        }
                    }
                }
            }

            val rc = desc.renderingControlUrl
            if (rc != null && _pollCount++ % VOLUME_POLL_EVERY == 0) {
                val volume = DlnaSoap.tag(soap(rc, RENDERING_CONTROL, "GetVolume", "$INSTANCE<Channel>Master</Channel>"), "CurrentVolume")?.toDoubleOrNull()
                if (volume != null && abs(volume / 100.0 - _lastVolume) > 0.001) {
                    _lastVolume = volume / 100.0
                    handler.volumeChanged(_lastVolume)
                }
            }
        } catch (e: Throwable) {
            _consecutiveFailures++
            Logger.w(TAG, "Failed to poll DLNA renderer ($_consecutiveFailures): ${e.message}")
            if (_consecutiveFailures == 1) {
                handler.connectionStateChanged(DeviceConnectionState.Reconnecting)
            } else if (_consecutiveFailures >= MAX_POLL_FAILURES) {
                disconnect()
            }
        }
    }

    private fun fetchDescription(): DlnaDescription {
        val response = _client.get(location)
        val body = response.use { if (it.isOk) it.body?.string() else null }
            ?: throw CastingDeviceException.FailedToSendCommand("Failed to fetch device description from $location")
        return DlnaDescription.parse(location, body)
            ?: throw CastingDeviceException.FailedToSendCommand("Device at $location is not a DLNA media renderer")
    }

    private fun requireDescription(): DlnaDescription =
        _description ?: throw CastingDeviceException.FailedToSendCommand("Device is not connected")

    private fun soap(controlUrl: String, service: String, action: String, args: String): String {
        val result = DlnaSoap.invoke(_client, controlUrl, service, action, args)
        if (!result.isOk) {
            throw CastingDeviceException.FailedToSendCommand("$action failed (${result.code}): ${result.error ?: "unknown error"}")
        }
        return result.body
    }

    private fun soapOrNull(controlUrl: String, service: String, action: String, args: String): String? = try {
        soap(controlUrl, service, action, args)
    } catch (e: Throwable) {
        Logger.w(TAG, "Ignored failure: ${e.message}")
        null
    }

    private fun submit(name: String, action: () -> Unit) {
        val executor = _executor ?: throw CastingDeviceException.FailedToSendCommand("Device is not connected")
        executor.execute {
            try {
                action()
            } catch (e: Throwable) {
                Logger.e(TAG, "DLNA command '$name' failed: ${e.message}")
                _handler?.playbackError("$name: ${e.message}")
            }
        }
    }

    private fun unsupported(): Nothing = throw CastingDeviceException.UnsupportedFeature("Not supported by DLNA renderers")

    private fun toIpAddr(address: String): IpAddr = tryIpAddrFromStr(address.substringBefore('%'))

    private data class LoadParams(val url: String, val contentType: String, val resumePosition: Double, val metadata: Metadata?)

    companion object {
        private const val TAG = "DlnaCastingDevice"
        const val TXT_LOCATION = "dlna.location"

        private const val AV_TRANSPORT = DlnaDescription.SERVICE_AV_TRANSPORT
        private const val RENDERING_CONTROL = DlnaDescription.SERVICE_RENDERING_CONTROL
        private const val INSTANCE = "<InstanceID>0</InstanceID>"
        private const val POLL_INTERVAL_MS = 1000L
        private const val VOLUME_POLL_EVERY = 3
        private const val MAX_POLL_FAILURES = 10
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val END_THRESHOLD_S = 3.0
        private const val STOP_WAIT_ATTEMPTS = 10
        private const val STOP_WAIT_INTERVAL_MS = 300L
        private const val SEEK_DEBOUNCE_MS = 300L
        private const val SEEK_RETRY_MS = 400L
        private const val SEEK_RETRIES = 5
    }
}
