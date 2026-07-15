package com.futo.platformplayer.casting

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.http.server.HttpHeaders
import com.futo.platformplayer.api.http.server.ManagedHttpServer
import com.futo.platformplayer.api.http.server.handlers.HttpConstantHandler
import com.futo.platformplayer.api.http.server.handlers.HttpContentUriHandler
import com.futo.platformplayer.api.http.server.handlers.HttpFileHandler
import com.futo.platformplayer.api.http.server.handlers.HttpFunctionHandler
import com.futo.platformplayer.api.http.server.handlers.HttpProxyHandler
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestMergingRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSUMPSource
import com.futo.platformplayer.sabr.CastSupersededException
import com.futo.platformplayer.sabr.SabrBlockedException
import com.futo.platformplayer.sabr.SabrFormat
import com.futo.platformplayer.sabr.SabrFormatSubstitutedException
import com.futo.platformplayer.sabr.SabrReloadRequiredException
import com.futo.platformplayer.sabr.SabrSession
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalAudioContentSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalVideoContentSource
import com.futo.platformplayer.awaitCancelConverted
import com.futo.platformplayer.builders.DashBuilder
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.exceptions.UnsupportedCastException
import com.futo.platformplayer.findPreferredAddress
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.parsers.HLS
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.CastingDeviceInfoStorage
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.toUrlAddress
import com.futo.platformplayer.views.casting.CastView
import com.futo.platformplayer.views.casting.CastView.Companion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.Metadata
import org.fcast.sender_sdk.NsdDeviceDiscoverer
import org.fcast.sender_sdk.ProtocolType
import java.net.Inet6Address
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.fcast.sender_sdk.DeviceInfo as RsDeviceInfo

class StateCasting {
    var _scopeIO = CoroutineScope(Dispatchers.IO); private set
    var _scopeMain = CoroutineScope(Dispatchers.Main); private set
    private val _storage: CastingDeviceInfoStorage = FragmentedStorage.get();

    val _castServer = ManagedHttpServer();
    var _started = false;

    var devices: HashMap<String, CastingDevice> = hashMapOf();
    val onDeviceAdded = Event1<CastingDevice>();
    val onDeviceChanged = Event1<CastingDevice>();
    val onDeviceRemoved = Event1<CastingDevice>();
    val onActiveDeviceConnectionStateChanged = Event2<CastingDevice, CastConnectionState>();
    val onActiveDevicePlayChanged = Event1<Boolean>();
    val onActiveDeviceTimeChanged = Event1<Double>();
    val onActiveDeviceDurationChanged = Event1<Double>();
    val onActiveDeviceVolumeChanged = Event1<Double>();
    val onActiveDeviceMediaItemEnd = Event0()
    var activeDevice: CastingDevice? = null;
    private var _videoExecutor: JSRequestExecutor? = null
    private var _audioExecutor: JSRequestExecutor? = null
    @Volatile private var _sabrCastProxy: com.futo.platformplayer.sabr.SabrCastProxy? = null

    private var _handBackState: SabrSession.Transferable? = null
    private var _handBackVideoId: String? = null

    @Synchronized
    fun takeHandBackState(videoId: String): SabrSession.Transferable? {
        val state = _handBackState ?: return null
        if (_handBackVideoId != videoId) return null

        _handBackState = null
        _handBackVideoId = null
        Logger.i(TAG, "Continuing the playback casting left behind for $videoId")
        return state
    }

    @Synchronized
    private fun installCastProxy(proxy: com.futo.platformplayer.sabr.SabrCastProxy, castId: Int, device: CastingDevice): Boolean {
        if (castId != _castId.get() || activeDevice !== device || device.connectionState != CastConnectionState.CONNECTED)
            return false

        _sabrCastProxy?.release()
        _sabrCastProxy = proxy
        return true
    }

    @Synchronized
    private fun retireProxy(proxy: com.futo.platformplayer.sabr.SabrCastProxy) {
        if (_sabrCastProxy === proxy) {
            _castProxyServing = false
            _sabrCastProxy = null
            _castServer.removeAllHandlers("castUMP")
        }
        proxy.release()
    }

    @Volatile private var _castProxyServing = false

    @Synchronized
    fun releaseCastProxyIfIdle() {
        if (activeDevice != null && _castProxyServing) return
        releaseCastProxy()
    }

    @Synchronized
    fun releaseCastProxy() {
        _castProxyServing = false
        val proxy = _sabrCastProxy
        if (proxy != null) {
            _sabrCastProxy = null
            proxy.release()
            _castServer.removeAllHandlers("castUMP")
        }
        _handBackState = null
        _handBackVideoId = null
    }

    private var _preparingCastProxy: com.futo.platformplayer.sabr.SabrCastProxy? = null

    @Synchronized
    private fun setPreparingCastProxy(proxy: com.futo.platformplayer.sabr.SabrCastProxy?) {
        _preparingCastProxy = proxy
    }

    @Synchronized
    private fun retireCastProxy() {
        _castProxyServing = false
        val proxy = _sabrCastProxy ?: _preparingCastProxy ?: return
        if (_sabrCastProxy === proxy) _sabrCastProxy = null
        if (_preparingCastProxy === proxy) _preparingCastProxy = null

        _handBackState = proxy.exportTransferable()
        _handBackVideoId = if (_handBackState != null) proxy.videoId else null
        proxy.release()

        _castServer.removeAllHandlers("castUMP")
    }

    private val _client = ManagedHttpClient();
    var _resumeCastingDevice: CastingDeviceInfo? = null;
    val isCasting: Boolean get() = activeDevice != null;
    private val _castId = AtomicInteger(0)

    private val _context = CastContext()
    var _deviceDiscoverer: NsdDeviceDiscoverer? = null

    class DiscoveryEventHandler(
        private val onDeviceAdded: (RsDeviceInfo) -> Unit,
        private val onDeviceRemoved: (String) -> Unit,
        private val onDeviceUpdated: (RsDeviceInfo) -> Unit,
    ) : org.fcast.sender_sdk.DeviceDiscovererEventHandler {
        override fun deviceAvailable(deviceInfo: RsDeviceInfo) {
            onDeviceAdded(deviceInfo)
        }

        override fun deviceChanged(deviceInfo: RsDeviceInfo) {
            onDeviceUpdated(deviceInfo)
        }

        override fun deviceRemoved(deviceName: String) {
            onDeviceRemoved(deviceName)
        }
    }

    init {
        if (BuildConfig.DEBUG) {
            org.fcast.sender_sdk.initLogger(org.fcast.sender_sdk.LogLevelFilter.DEBUG)
        }
    }

    fun handleUrl(url: String) {
        try {
            val foundDeviceInfo = org.fcast.sender_sdk.deviceInfoFromUrl(url)!!
            val foundDevice = _context.createDeviceFromInfo(foundDeviceInfo)
            connectDevice(CastingDevice(foundDevice))
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to handle URL: $e")
        }
    }

    fun onStop() {
        val ad = activeDevice ?: return
        _resumeCastingDevice = ad.getDeviceInfo()
        Log.i(TAG, "_resumeCastingDevice set to '${ad.name}'")
        Logger.i(TAG, "Stopping active device because of onStop.")
        try {
            ad.disconnect()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to disconnect from device: $e")
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (_started)
            return
        _started = true

        Log.i(TAG, "_resumeCastingDevice set null start")
        _resumeCastingDevice = null

        Logger.i(TAG, "CastingService starting...")

        _scopeIO = CoroutineScope(Dispatchers.IO)
        _scopeMain = CoroutineScope(Dispatchers.Main)

        try {
            _castServer.start()
        } catch (ex: Throwable) {
            _started = false
            Logger.e(TAG, "Failed to start the cast server", ex)
            return
        }
        enableDeveloper(true)

        Logger.i(TAG, "CastingService started.")

        _deviceDiscoverer = NsdDeviceDiscoverer(
            context,
            DiscoveryEventHandler(
                { deviceInfo -> // Added
                    Logger.i(TAG, "Device added: ${deviceInfo.name}")
                    val device = _context.createDeviceFromInfo(deviceInfo)
                    val deviceHandle = CastingDevice(device)
                    devices[deviceHandle.device.name()] = deviceHandle
                    invokeInMainScopeIfRequired {
                        onDeviceAdded.emit(deviceHandle)
                    }
                },
                { deviceName -> // Removed
                    invokeInMainScopeIfRequired {
                        if (devices.containsKey(deviceName)) {
                            val device = devices.remove(deviceName)
                            if (device != null) {
                                onDeviceRemoved.emit(device)
                            }
                        }
                    }
                },
                { deviceInfo -> // Updated
                    Logger.i(TAG, "Device updated: $deviceInfo")
                    val handle = devices[deviceInfo.name]
                    if (handle != null && handle is CastingDevice) {
                        handle.device.setPort(deviceInfo.port)
                        handle.device.setAddresses(deviceInfo.addresses)
                        invokeInMainScopeIfRequired {
                            onDeviceChanged.emit(handle)
                        }
                    }
                },
            )
        )
    }

    @Synchronized
    fun stop() {
        if (!_started) {
            return
        }

        _started = false

        Logger.i(TAG, "CastingService stopping.")

        _scopeIO.cancel()
        _scopeMain.cancel()

        Logger.i(TAG, "Stopping active device because StateCasting is being stopped.")
        val d = activeDevice
        activeDevice = null
        try {
            d?.disconnect()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to disconnect device: $e")
        }

        _castServer.stop()
        _castServer.removeAllHandlers()

        releaseCastProxy()

        Logger.i(TAG, "CastingService stopped.")

        _deviceDiscoverer = null
    }

    fun startUpdateTimeJob(
        onTimeJobTimeChanged_s: Event1<Long>,
        setTime: (Long) -> Unit
    ): Job? {
        return CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val t_s = activeDevice?.expectedCurrentTime
                if (t_s != null) {
                    val t_ms = (t_s * 1000.0).toLong()
                    setTime(t_ms)
                    onTimeJobTimeChanged_s.emit(t_s.toLong())
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun deviceFromInfo(deviceInfo: CastingDeviceInfo): CastingDevice? {
        try {
            val rsAddrs =
                deviceInfo.addresses.map { org.fcast.sender_sdk.tryIpAddrFromStr(it) }
            val rsDeviceInfo = RsDeviceInfo(
                name = deviceInfo.name,
                protocol = when (deviceInfo.type) {
                    com.futo.platformplayer.casting.CastProtocolType.CHROMECAST -> ProtocolType.CHROMECAST
                    com.futo.platformplayer.casting.CastProtocolType.FCAST -> ProtocolType.F_CAST
                    else -> throw IllegalArgumentException()
                },
                addresses = rsAddrs,
                port = deviceInfo.port.toUShort(),
            )

            return CastingDevice(_context.createDeviceFromInfo(rsDeviceInfo))
        } catch (_: Throwable) {
            return null
        }
    }

    fun onResume() {
        val ad = activeDevice
        if (ad != null) {
            ad.ensureThreadStarted()
        } else {
            val resumeCastingDevice = _resumeCastingDevice
            if (resumeCastingDevice != null) {
                val dev = deviceFromInfo(resumeCastingDevice) ?: return
                connectDevice(dev)
                _resumeCastingDevice = null
                Log.i(TAG, "_resumeCastingDevice set to null onResume")
            }
        }
    }

    fun cancel() {
        _castId.incrementAndGet()
    }

    fun invokeInMainScopeIfRequired(action: () -> Unit) {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            _scopeMain.launch { action() }
            return;
        }

        action();
    }

    private val _castingDialogLock = Any();
    private var _currentDialog: AlertDialog? = null;

    @Synchronized
    fun connectDevice(device: CastingDevice) {
        if (activeDevice == device) {
            return
        }

        val ad = activeDevice;
        if (ad != null) {
            Logger.i(TAG, "Stopping previous device because a new one is being connected.")
            device.onConnectionStateChanged.clear();
            device.onPlayChanged.clear();
            device.onTimeChanged.clear();
            device.onVolumeChanged.clear();
            device.onDurationChanged.clear();
            device.onMediaItemEnd.clear();
            ad.disconnect()
        }

        device.onConnectionStateChanged.subscribe { castConnectionState ->
            Logger.i(TAG, "Active device connection state changed: $castConnectionState")

            if (castConnectionState == CastConnectionState.DISCONNECTED) {
                Logger.i(TAG, "Clearing events: $castConnectionState");

                device.onConnectionStateChanged.clear();
                device.onPlayChanged.clear();
                device.onTimeChanged.clear();
                device.onVolumeChanged.clear();
                device.onDurationChanged.clear();
                device.onMediaItemEnd.clear();
                activeDevice = null;
                _castId.incrementAndGet();
                retireCastProxy();
            }

            invokeInMainScopeIfRequired {
                StateApp.withContext(false) { context ->
                    context.let {
                        Logger.i(TAG, "Casting state changed to ${castConnectionState}");
                        when (castConnectionState) {
                            CastConnectionState.CONNECTED -> {
                                Logger.i(TAG, "Casting connected to [${device.name}]");
                                UIDialogs.appToast("Connected to device")
                                synchronized(_castingDialogLock) {
                                    if(_currentDialog != null) {
                                        _currentDialog?.hide();
                                        _currentDialog = null;
                                    }
                                }
                            }
                            CastConnectionState.CONNECTING -> {
                                Logger.i(TAG, "Casting connecting to [${device.name}]");
                                UIDialogs.toast(it, "Connecting to device...")
                                synchronized(_castingDialogLock) {
                                    if(_currentDialog == null) {
                                        _currentDialog = UIDialogs.showDialog(context, R.drawable.ic_loader_animated, true,
                                            "Connecting to [${device.name}]",
                                            "Make sure you are on the same network\n\nVPNs and guest networks can cause issues", null, -2,
                                            UIDialogs.Action("Disconnect", {
                                                try {
                                                    device.disconnect()
                                                } catch (e: Throwable) {
                                                    Logger.e(TAG, "Failed to disconnect from device: $e")
                                                }
                                            }));
                                    }
                                }
                            }
                            CastConnectionState.DISCONNECTED -> {
                                UIDialogs.toast(it, "Disconnected from device")
                                synchronized(_castingDialogLock) {
                                    if(_currentDialog != null) {
                                        _currentDialog?.hide();
                                        _currentDialog = null;
                                    }
                                }
                            }
                        }
                    }
                };
                onActiveDeviceConnectionStateChanged.emit(device, castConnectionState);
            };
        };
        device.onPlayChanged.subscribe {
            invokeInMainScopeIfRequired { onActiveDevicePlayChanged.emit(it) };
        };
        device.onDurationChanged.subscribe {
            invokeInMainScopeIfRequired { onActiveDeviceDurationChanged.emit(it) };
        };
        device.onVolumeChanged.subscribe {
            invokeInMainScopeIfRequired { onActiveDeviceVolumeChanged.emit(it) };
        };
        device.onTimeChanged.subscribe {
            invokeInMainScopeIfRequired { onActiveDeviceTimeChanged.emit(it) };
        };
        device.onMediaItemEnd.subscribe {
            invokeInMainScopeIfRequired { onActiveDeviceMediaItemEnd.emit() }
        }

        try {
            device.connect();
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to connect to device.");
            device.onConnectionStateChanged.clear();
            device.onPlayChanged.clear();
            device.onTimeChanged.clear();
            device.onVolumeChanged.clear();
            device.onDurationChanged.clear();
            device.onMediaItemEnd.clear();
            return;
        }

        activeDevice = device
        Logger.i(TAG, "Connect to device ${device.name}")
    }

    fun metadataFromVideo(video: IPlatformVideoDetails, videoThumbnailOverrideUrl: String? = null): Metadata {
        return Metadata(
            title = video.name, thumbnailUrl = videoThumbnailOverrideUrl ?: video.thumbnails.getHQThumbnail()
        )
    }

    @Throws
    suspend fun castIfAvailable(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoSource?, audioSource: IAudioSource?, subtitleSource: ISubtitleSource?, ms: Long = -1, speed: Double?, preferredVideoHeight: Int = -1, sabrState: SabrSession.Transferable? = null, onError: ((Throwable) -> Unit)? = null, onLoadingEstimate: ((Int) -> Unit)? = null, onLoading: ((Boolean) -> Unit)? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val ad = activeDevice ?: return@withContext false;
            if (ad.connectionState != CastConnectionState.CONNECTED) {
                return@withContext false;
            }
            val deviceProto = ad.protocolType

            val resumePosition = if (ms > 0L) (ms.toDouble() / 1000.0) else 0.0;
            val castId = _castId.incrementAndGet()

            var sourceCount = 0;
            if (videoSource != null) sourceCount++;
            if (audioSource != null) sourceCount++;
            if (subtitleSource != null) sourceCount++;

            if (sourceCount < 1) {
                throw Exception("At least one source should be specified.");
            }

            val castState = sabrState
                ?: (videoSource as? JSUMPSource)?.let { takeRunningCastState(it.videoId) };

            cleanExecutors()
            _castServer.removeAllHandlers("cast")
            _castServer.removeAllHandlers("castSingular")
            _castServer.removeAllHandlers("castProxiedHlsVariant")

            if (sourceCount > 1) {
                if (videoSource is LocalVideoSource || audioSource is LocalAudioSource || subtitleSource is LocalSubtitleSource) {
                    Logger.i(TAG, "Casting as local DASH");
                    castLocalDash(video, videoSource as LocalVideoSource?, audioSource as LocalAudioSource?, subtitleSource as LocalSubtitleSource?, resumePosition, speed);
                } else if (videoSource is JSUMPSource) {
                    Logger.i(TAG, "Casting as JSUMPSource (SABR)");
                    castUMP(video, videoSource as JSUMPSource, subtitleSource, resumePosition, speed, castId, preferredVideoHeight, castState, onError, onLoadingEstimate, onLoading);
                } else {
                    val isRawDash =
                        videoSource is JSDashManifestRawSource || audioSource is JSDashManifestRawAudioSource
                    if (isRawDash) {
                        Logger.i(TAG, "Casting as raw DASH");

                        castDashRaw(contentResolver, video, videoSource as JSDashManifestRawSource?, audioSource as JSDashManifestRawAudioSource?, subtitleSource, resumePosition, speed, castId, onLoadingEstimate, onLoading);
                    } else {
                        Logger.i(TAG, "Casting as DASH indirect");
                        castDashIndirect(contentResolver, video, videoSource as IVideoUrlSource?, audioSource as IAudioUrlSource?, subtitleSource, resumePosition, speed);
                    }
                }
            } else {
                val proxyStreams = shouldProxyStreams(ad, videoSource, audioSource)
                val url = getLocalUrl(ad);
                val id = UUID.randomUUID();


                if (videoSource is IVideoUrlSource) {
                    val videoPath = "/video-$id"
                    val upstreamUrl = videoSource.getVideoUrl()
                    val videoUrl = if (proxyStreams) url + videoPath else upstreamUrl
                    val jsReqMod = (videoSource as? JSSource)?.getRequestModifier()

                    if (proxyStreams) {
                        _castServer.addHandlerWithAllowAllOptions(
                            HttpProxyHandler("GET", videoPath, upstreamUrl, true)
                                .withIRequestModifier(jsReqMod)
                                .withInjectedHost()
                                .withHeader("Access-Control-Allow-Origin", "*"),
                            true
                        ).withTag("castSingular")
                    }

                    Logger.i(TAG, "Casting as singular video (proxy=$proxyStreams, url=$videoUrl)")
                    ad.loadVideo(
                        if (video.isLive) "LIVE" else "BUFFERED",
                        videoSource.container,
                        videoUrl,
                        resumePosition,
                        video.duration.toDouble(),
                        speed,
                        metadataFromVideo(video)
                    )
                } else if (audioSource is IAudioUrlSource) {
                    val audioPath = "/audio-$id"
                    val upstreamUrl = audioSource.getAudioUrl()
                    val audioUrl = if (proxyStreams) url + audioPath else upstreamUrl
                    val jsReqMod = (audioSource as? JSSource)?.getRequestModifier()

                    if (proxyStreams) {
                        _castServer.addHandlerWithAllowAllOptions(
                            HttpProxyHandler("GET", audioPath, upstreamUrl, true)
                                .withIRequestModifier(jsReqMod)
                                .withInjectedHost()
                                .withHeader("Access-Control-Allow-Origin", "*"),
                            true
                        ).withTag("castSingular")
                    }

                    Logger.i(TAG, "Casting as singular audio (proxy=$proxyStreams, url=$audioUrl)")
                    ad.loadVideo(
                        if (video.isLive) "LIVE" else "BUFFERED",
                        audioSource.container,
                        audioUrl,
                        resumePosition,
                        video.duration.toDouble(),
                        speed,
                        metadataFromVideo(video)
                    )
                } else if (videoSource is IHLSManifestSource) {
                    if (proxyStreams || deviceProto == CastProtocolType.CHROMECAST) {
                        Logger.i(TAG, "Casting as proxied HLS");
                        castProxiedHls(video, videoSource.url, videoSource.codec, resumePosition, speed, (videoSource as JSSource?)?.getRequestModifier());
                    } else {
                        Logger.i(TAG, "Casting as non-proxied HLS");
                        ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", videoSource.container, videoSource.url, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));
                    }
                } else if (audioSource is IHLSManifestAudioSource) {
                    if (proxyStreams || deviceProto == CastProtocolType.CHROMECAST) {
                        Logger.i(TAG, "Casting as proxied audio HLS");
                        castProxiedHls(video, audioSource.url, audioSource.codec, resumePosition, speed, (audioSource as JSSource?)?.getRequestModifier());
                    } else {
                        Logger.i(TAG, "Casting as non-proxied audio HLS");
                        ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", audioSource.container, audioSource.url, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));
                    }
                } else if (videoSource is LocalVideoSource) {
                    Logger.i(TAG, "Casting as local video");
                    castLocalVideo(video, videoSource, resumePosition, speed);
                } else if (audioSource is LocalAudioSource) {
                    Logger.i(TAG, "Casting as local audio");
                    castLocalAudio(video, audioSource, resumePosition, speed);
                } else if (videoSource is LocalVideoContentSource) {
                    Logger.i(TAG, "Casting as local video");
                    castLocalVideo(contentResolver, video, videoSource, resumePosition, speed);
                } else if (audioSource is LocalAudioContentSource) {
                    Logger.i(TAG, "Casting as local audio");
                    castLocalAudio(contentResolver, video, audioSource, resumePosition, speed);
                } else if (videoSource is JSDashManifestRawSource) {
                    Logger.i(TAG, "Casting as JSDashManifestRawSource video");
                    castDashRaw(contentResolver, video, videoSource as JSDashManifestRawSource?, null, null, resumePosition, speed, castId, onLoadingEstimate, onLoading);
                } else if (audioSource is JSDashManifestRawAudioSource) {
                    Logger.i(TAG, "Casting as JSDashManifestRawSource audio");
                    castDashRaw(contentResolver, video, null, audioSource as JSDashManifestRawAudioSource?, null, resumePosition, speed, castId, onLoadingEstimate, onLoading);
                } else if (videoSource is JSUMPSource) {
                    Logger.i(TAG, "Casting as JSUMPSource (SABR)");
                    castUMP(video, videoSource as JSUMPSource, subtitleSource, resumePosition, speed, castId, preferredVideoHeight, castState, onError, onLoadingEstimate, onLoading);
                } else {
                    var str = listOf(
                        if(videoSource != null) "Video: ${videoSource::class.java.simpleName}" else null,
                        if(audioSource != null) "Audio: ${audioSource::class.java.simpleName}" else null,
                        if(subtitleSource != null) "Subtitles: ${subtitleSource::class.java.simpleName}" else null
                    ).filterNotNull().joinToString(", ");
                    throw UnsupportedCastException(str);
                }
            }

            return@withContext true;
        }
    }

    private fun HttpProxyHandler.withIRequestModifier(requestModifier: IRequestModifier?): HttpProxyHandler {
        if (requestModifier == null) return this
        return withRequestModifier { url, headers -> requestModifier.modifyRequest(url, headers) }
    }

    fun resumeVideo(): Boolean {
        val ad = activeDevice ?: return false;
        try {
            ad.resumePlayback();
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to resume playback: $e")
            return false
        }
        return true;
    }

    fun pauseVideo(): Boolean {
        val ad = activeDevice ?: return false;
        try {
            ad.pausePlayback();
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to pause playback: $e")
            return false
        }
        return true;
    }

    fun stopVideo(): Boolean {
        _castProxyServing = false;
        val ad = activeDevice ?: return false;
        try {
            ad.stopPlayback();
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to stop playback: $e")
            return false
        }
        return true;
    }

    fun videoSeekTo(timeSeconds: Double): Boolean {
        val ad = activeDevice ?: return false;
        try {
            ad.seekTo(timeSeconds);
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to seek: $e")
            return false
        }
        return true;
    }

    fun changeVolume(volume: Double): Boolean {
        val ad = activeDevice ?: return false;
        try {
            ad.changeVolume(volume);
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to change volume: $e")
            return false
        }
        return true;
    }

    fun changeSpeed(speed: Double): Boolean {
        val ad = activeDevice ?: return false;
        try {
            ad.changeSpeed(speed);
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to change speed: $e")
            return false
        }
        return true;
    }

    private fun castLocalVideo(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: LocalVideoContentSource, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();
        val videoPath = "/video-${id}"
        val videoUrl = url + videoPath;
        val thumbnailPath = "/thumbnail-${id}"
        val thumbnailUrl = url + thumbnailPath;
        val thumbnailContentUrl = video.thumbnails.getHQThumbnail()

        if (thumbnailContentUrl != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpContentUriHandler("GET", thumbnailPath, contentResolver, thumbnailContentUrl.toUri())
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }

        _castServer.addHandlerWithAllowAllOptions(
            HttpContentUriHandler("GET", videoPath, contentResolver, videoSource.contentUrl.toUri())
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        Logger.i(TAG, "Casting local video (videoUrl: $videoUrl).");
        ad.loadVideo("BUFFERED", videoSource.container, videoUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video, if (thumbnailContentUrl != null) thumbnailUrl else null));

        return listOf(videoUrl);
    }

    private fun castLocalAudio(contentResolver: ContentResolver, video: IPlatformVideoDetails, audioSource: LocalAudioContentSource, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();
        val audioPath = "/audio-${id}"
        val audioUrl = url + audioPath;
        val thumbnailPath = "/thumbnail-${id}"
        val thumbnailUrl = url + thumbnailPath;
        val thumbnailContentUrl = video.thumbnails.getHQThumbnail()

        if (thumbnailContentUrl != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpContentUriHandler("GET", thumbnailPath, contentResolver, thumbnailContentUrl.toUri())
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }

        _castServer.addHandlerWithAllowAllOptions(
            HttpContentUriHandler("GET", audioPath, contentResolver, audioSource.contentUrl.toUri())
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        Logger.i(TAG, "Casting local audio (audioUrl: $audioUrl).");
        ad.loadVideo("BUFFERED", audioSource.container, audioUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video, if (thumbnailContentUrl != null) thumbnailUrl else null));

        return listOf(audioUrl);
    }

    private fun castLocalVideo(video: IPlatformVideoDetails, videoSource: LocalVideoSource, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();
        val videoPath = "/video-${id}"
        val videoUrl = url + videoPath;

        _castServer.addHandlerWithAllowAllOptions(
            HttpFileHandler("GET", videoPath, videoSource.container, videoSource.filePath)
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        Logger.i(TAG, "Casting local video (videoUrl: $videoUrl).");
        ad.loadVideo("BUFFERED", videoSource.container, videoUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(videoUrl);
    }

    private fun castLocalAudio(video: IPlatformVideoDetails, audioSource: LocalAudioSource, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();
        val audioPath = "/audio-${id}"
        val audioUrl = url + audioPath;

        _castServer.addHandlerWithAllowAllOptions(
            HttpFileHandler("GET", audioPath, audioSource.container, audioSource.filePath)
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        Logger.i(TAG, "Casting local audio (audioUrl: $audioUrl).");
        ad.loadVideo("BUFFERED", audioSource.container, audioUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(audioUrl);
    }

    private fun castLocalDash(video: IPlatformVideoDetails, videoSource: LocalVideoSource?, audioSource: LocalAudioSource?, subtitleSource: LocalSubtitleSource?, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();

        val dashPath = "/dash-${id}"
        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val dashUrl = url + dashPath;
        val videoUrl = url + videoPath;
        val audioUrl = url + audioPath;
        val subtitleUrl = url + subtitlePath;

        val dashContent = DashBuilder.generateOnDemandDash(videoSource, videoUrl, audioSource, audioUrl, subtitleSource, subtitleUrl);
        Logger.v(TAG) { "Dash manifest: $dashContent" };

        _castServer.addHandlerWithAllowAllOptions(
            HttpConstantHandler("GET", dashPath, dashContent,
                "application/dash+xml")
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");
        if (videoSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFileHandler("GET", videoPath, videoSource.container, videoSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }
        if (audioSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFileHandler("GET", audioPath, audioSource.container, audioSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }
        if (subtitleSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFileHandler("GET", subtitlePath, subtitleSource.format ?: "text/vtt", subtitleSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }

        Logger.i(TAG, "added new castLocalDash handlers (dashPath: $dashPath, videoPath: $videoPath, audioPath: $audioPath, subtitlePath: $subtitlePath).");
        ad.loadVideo("BUFFERED", "application/dash+xml", dashUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(dashUrl, videoUrl, audioUrl, subtitleUrl);
    }

    private fun castProxiedHls(
        video: IPlatformVideoDetails,
        sourceUrl: String,
        codec: String?,
        resumePosition: Double,
        speed: Double?,
        requestModifier: IRequestModifier?
    ): List<String> {
        _castServer.removeAllHandlers("castProxiedHlsMaster")

        val ad = activeDevice ?: return listOf();
        val url = getLocalUrl(ad);

        val id = UUID.randomUUID();
        val hlsPath = "/hls-${id}"
        val hlsUrl = url + hlsPath
        Logger.i(TAG, "HLS url: $hlsUrl");

        _castServer.addHandlerWithAllowAllOptions(
            HttpFunctionHandler(
                "GET", hlsPath
            ) { masterContext ->
                _castServer.removeAllHandlers("castProxiedHlsVariant")

                val headers = masterContext.headers.clone()
                headers["Content-Type"] = "application/vnd.apple.mpegurl";

                val masterPlaylistResponse = _client.get(sourceUrl, mutableMapOf(), requestModifier)

                if (!masterPlaylistResponse.isOk) {
                    try { masterPlaylistResponse.body?.close() } catch (_: Throwable) {}
                    throw IllegalStateException("Failed to get master playlist: ${masterPlaylistResponse.code}")
                }

                val masterPlaylistContent = masterPlaylistResponse.body?.string()
                    ?: throw Exception("Master playlist content is empty")

                val masterPlaylist: HLS.MasterPlaylist
                try {
                    masterPlaylist = HLS.parseMasterPlaylist(masterPlaylistContent, sourceUrl)
                } catch (e: Throwable) {
                    if (masterPlaylistContent.lines().any { it.startsWith("#EXTINF:") }) {
                        //This is a variant playlist, not a master playlist
                        Logger.i(TAG, "HLS casting as variant playlist (codec: $codec): $hlsUrl");

                        val vpHeaders = masterContext.headers.clone()
                        vpHeaders["Content-Type"] = "application/vnd.apple.mpegurl";

                        val variantPlaylist =
                            HLS.parseVariantPlaylist(masterPlaylistContent, sourceUrl)
                        val proxiedVariantPlaylist =
                            proxyVariantPlaylist(url, id, variantPlaylist,  video.isLive, requestModifier)
                        val proxiedVariantPlaylist_m3u8 = proxiedVariantPlaylist.buildM3U8()
                        masterContext.respondCode(200, vpHeaders, proxiedVariantPlaylist_m3u8);
                        return@HttpFunctionHandler
                    } else {
                        throw e
                    }
                }

                Logger.i(TAG, "HLS casting as master playlist: $hlsUrl");

                val newVariantPlaylistRefs = arrayListOf<HLS.VariantPlaylistReference>()
                val newMediaRenditions = arrayListOf<HLS.MediaRendition>()
                val newMasterPlaylist = HLS.MasterPlaylist(
                    newVariantPlaylistRefs,
                    newMediaRenditions,
                    masterPlaylist.sessionDataList,
                    masterPlaylist.independentSegments
                )

                for (variantPlaylistRef in masterPlaylist.variantPlaylistsRefs) {
                    val playlistId = UUID.randomUUID();
                    val newPlaylistPath = "/hls-playlist-${playlistId}"
                    val newPlaylistUrl = url + newPlaylistPath;

                    _castServer.addHandlerWithAllowAllOptions(
                        HttpFunctionHandler(
                            "GET", newPlaylistPath
                        ) { vpContext ->
                            val vpHeaders = vpContext.headers.clone()
                            vpHeaders["Content-Type"] = "application/vnd.apple.mpegurl";

                            val response = _client.get(variantPlaylistRef.url, mutableMapOf(), requestModifier)
                            if (!response.isOk) {
                                    try { response.body?.close() } catch (_: Throwable) {}
                                    throw IllegalStateException("Failed to get variant playlist: ${response.code}")
                                }

                            val vpContent = response.body?.string()
                                ?: throw Exception("Variant playlist content is empty")

                            val variantPlaylist =
                                HLS.parseVariantPlaylist(vpContent, variantPlaylistRef.url)
                            val proxiedVariantPlaylist =
                                proxyVariantPlaylist(url, playlistId, variantPlaylist, video.isLive, requestModifier)
                            val proxiedVariantPlaylist_m3u8 = proxiedVariantPlaylist.buildM3U8()
                            vpContext.respondCode(200, vpHeaders, proxiedVariantPlaylist_m3u8);
                        }.withHeader("Access-Control-Allow-Origin", "*"), true
                    ).withTag("castProxiedHlsVariant")

                    newVariantPlaylistRefs.add(
                        HLS.VariantPlaylistReference(
                            newPlaylistUrl, variantPlaylistRef.streamInfo
                        )
                    )
                }

                for (mediaRendition in masterPlaylist.mediaRenditions) {
                    val playlistId = UUID.randomUUID()

                    var newPlaylistUrl: String? = null
                    if (mediaRendition.uri != null) {
                        val newPlaylistPath = "/hls-playlist-${playlistId}"
                        newPlaylistUrl = url + newPlaylistPath

                        _castServer.addHandlerWithAllowAllOptions(
                            HttpFunctionHandler(
                                "GET", newPlaylistPath
                            ) { vpContext ->
                                val vpHeaders = vpContext.headers.clone()
                                vpHeaders["Content-Type"] = "application/vnd.apple.mpegurl";

                                val response = _client.get(mediaRendition.uri, mutableMapOf(), requestModifier)
                                if (!response.isOk) {
                                    try { response.body?.close() } catch (_: Throwable) {}
                                    throw IllegalStateException("Failed to get variant playlist: ${response.code}")
                                }

                                val vpContent = response.body?.string()
                                    ?: throw Exception("Variant playlist content is empty")

                                val variantPlaylist =
                                    HLS.parseVariantPlaylist(vpContent, mediaRendition.uri)
                                val proxiedVariantPlaylist = proxyVariantPlaylist(
                                    url, playlistId, variantPlaylist, video.isLive, requestModifier
                                )
                                val proxiedVariantPlaylist_m3u8 = proxiedVariantPlaylist.buildM3U8()
                                vpContext.respondCode(200, vpHeaders, proxiedVariantPlaylist_m3u8);
                            }.withHeader("Access-Control-Allow-Origin", "*"), true
                        ).withTag("castProxiedHlsVariant")
                    }

                    newMediaRenditions.add(HLS.MediaRendition(
                        mediaRendition.type,
                        newPlaylistUrl,
                        mediaRendition.groupID,
                        mediaRendition.language,
                        mediaRendition.name,
                        mediaRendition.isDefault,
                        mediaRendition.isAutoSelect,
                        mediaRendition.isForced
                    ))
                }

                masterContext.respondCode(200, headers, newMasterPlaylist.buildM3U8());
            }.withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("castProxiedHlsMaster")

        Logger.i(TAG, "added new castHlsIndirect handlers (hlsPath: $hlsPath).");

        //ChromeCast is sometimes funky with resume position 0
        val hackfixResumePosition =
            if (ad.protocolType == CastProtocolType.CHROMECAST && !video.isLive && resumePosition == 0.0) 0.1 else resumePosition;
        ad.loadVideo(
            if (video.isLive) "LIVE" else "BUFFERED",
            "application/vnd.apple.mpegurl",
            hlsUrl,
            hackfixResumePosition,
            video.duration.toDouble(),
            speed,
            metadataFromVideo(video)
        );

        return listOf(hlsUrl);
    }

    private fun proxyVariantPlaylist(url: String, playlistId: UUID, variantPlaylist: HLS.VariantPlaylist, isLive: Boolean, requestModifier: IRequestModifier?, proxySegments: Boolean = true): HLS.VariantPlaylist {
        val newSegments = arrayListOf<HLS.Segment>()

        if (proxySegments) {
            variantPlaylist.segments.forEachIndexed { index, segment ->
                val sequenceNumber = (variantPlaylist.mediaSequence ?: 0) + index.toLong()
                newSegments.add(proxySegment(url, playlistId, segment, sequenceNumber, requestModifier))
            }
        } else {
            newSegments.addAll(variantPlaylist.segments)
        }

        return HLS.VariantPlaylist(
            variantPlaylist.version,
            variantPlaylist.targetDuration,
            variantPlaylist.mediaSequence,
            variantPlaylist.discontinuitySequence,
            variantPlaylist.programDateTime,
            variantPlaylist.playlistType,
            variantPlaylist.streamInfo,
            newSegments
        )
    }

    private fun proxySegment(url: String, playlistId: UUID, segment: HLS.Segment, index: Long, requestModifier: IRequestModifier?): HLS.Segment {
        if (segment is HLS.MediaSegment) {
            val newSegmentPath = "/hls-playlist-${playlistId}-segment-${index}"
            val newSegmentUrl = url + newSegmentPath;

            if (_castServer.getHandler("GET", newSegmentPath) == null) {
                _castServer.addHandlerWithAllowAllOptions(
                    HttpProxyHandler("GET", newSegmentPath, segment.uri, true)
                        .withIRequestModifier(requestModifier)
                        .withInjectedHost()
                        .withHeader("Access-Control-Allow-Origin", "*"), true
                ).withTag("castProxiedHlsVariant")
            }

            return HLS.MediaSegment(
                segment.duration,
                newSegmentUrl
            )
        } else {
            return segment
        }
    }

    private fun shouldProxyStreams(castingDevice: CastingDevice, videoSource: IVideoSource?, audioSource: IAudioSource?): Boolean {
        val hasRequestModifier = (videoSource as? JSSource)?.hasRequestModifier == true || (audioSource as? JSSource)?.hasRequestModifier == true
        return Settings.instance.casting.alwaysProxyRequests || castingDevice.protocolType != CastProtocolType.FCAST || hasRequestModifier
    }

    private suspend fun castDashIndirect(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: ISubtitleSource?, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();
        val proxyStreams = shouldProxyStreams(ad, videoSource, audioSource)

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();

        val dashPath = "/dash-${id}"
        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val dashUrl = url + dashPath;
        Logger.i(TAG, "DASH url: $dashUrl");

        val videoUrl = if(proxyStreams) url + videoPath else videoSource?.getVideoUrl();
        val audioUrl = if(proxyStreams) url + audioPath else audioSource?.getAudioUrl();

        val subtitlesUri = if (subtitleSource != null) withContext(Dispatchers.IO) {
            return@withContext subtitleSource.getSubtitlesURI();
        } else null;

        //_castServer.removeAllHandlers("cast");
        //Logger.i(TAG, "removed all old castDash handlers.");

        var subtitlesUrl: String? = null;
        if (subtitlesUri != null) {
            if(subtitlesUri.scheme == "file") {
                var content: String? = null;
                val inputStream = contentResolver.openInputStream(subtitlesUri);
                inputStream?.use { stream ->
                    val reader = stream.bufferedReader();
                    content = reader.use { it.readText() };
                }

                if (content != null) {
                    _castServer.addHandlerWithAllowAllOptions(
                        HttpConstantHandler("GET", subtitlePath, content!!, subtitleSource?.format ?: "text/vtt")
                            .withHeader("Access-Control-Allow-Origin", "*"), true
                    ).withTag("cast");
                }

                subtitlesUrl = url + subtitlePath;
            } else {
                subtitlesUrl = subtitlesUri.toString();
            }
        }

        val dashContent = DashBuilder.generateOnDemandDash(videoSource, videoUrl, audioSource, audioUrl, subtitleSource, subtitlesUrl);
        Logger.v(TAG) { "Dash manifest: $dashContent" };

        _castServer.addHandlerWithAllowAllOptions(
            HttpConstantHandler("GET", dashPath, dashContent,
                "application/dash+xml")
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        if (videoSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", videoPath, videoSource.getVideoUrl(), true)
                    .withIRequestModifier((videoSource as? JSSource)?.getRequestModifier())
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }
        if (audioSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", audioPath, audioSource.getAudioUrl(), true)
                    .withIRequestModifier((audioSource as? JSSource)?.getRequestModifier())
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }

        Logger.i(TAG, "added new castDash handlers (dashPath: $dashPath, videoPath: $videoPath, audioPath: $audioPath).");
        ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", "application/dash+xml", dashUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(dashUrl, videoUrl ?: "", audioUrl ?: "", subtitlesUrl ?: "", videoSource?.getVideoUrl() ?: "", audioSource?.getAudioUrl() ?: "", subtitlesUri.toString());
    }

    @Synchronized
    fun cleanExecutors() {
        if (_videoExecutor != null) {
            _videoExecutor?.cleanup()
            _videoExecutor = null
        }

        if (_audioExecutor != null) {
            _audioExecutor?.cleanup()
            _audioExecutor = null
        }

        val proxy = _sabrCastProxy
        if (proxy != null) {
            _sabrCastProxy = null
            proxy.release()
            _castServer.removeAllHandlers("castUMP")
        }

    }

    private fun getLocalUrl(ad: CastingDevice): String {
        var address = ad.localAddress!!
        if (Settings.instance.casting.allowLinkLocalIpv4) {
            if (address.isLinkLocalAddress && address is Inet6Address) {
                address = findPreferredAddress() ?: address
                Logger.i(TAG, "Selected casting address: $address")
            }
        } else {
            if (address.isLinkLocalAddress) {
                address = findPreferredAddress() ?: address
                Logger.i(TAG, "Selected casting address: $address")
            }
        }
        return "http://${address.toUrlAddress().trim('/')}:${_castServer.port}";
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun injectSubtitleAdaptationSet(
        mpd: String,
        subtitleUrl: String,
        mimeType: String,
        lang: String = "und",
        label: String = "Subtitles"
    ): String {
        val adaptation = """
        <AdaptationSet contentType="text" mimeType="${escapeXml(mimeType)}" lang="${escapeXml(lang)}" default="true">
          <Role schemeIdUri="urn:mpeg:dash:role:2011" value="subtitle"/>
          <Label>${escapeXml(label)}</Label>
          <Representation id="caption_0" mimeType="${escapeXml(mimeType)}" lang="${escapeXml(lang)}" default="true" bandwidth="1000">
            <BaseURL>${escapeXml(subtitleUrl)}</BaseURL>
          </Representation>
        </AdaptationSet>
    """.trimIndent()

        val periodClose = Regex("</Period\\s*>", RegexOption.IGNORE_CASE)

        return if (periodClose.containsMatchIn(mpd)) {
            mpd.replaceFirst(periodClose, Regex.escapeReplacement(adaptation + "\n</Period>"))
        } else {
            mpd
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun castDashRaw(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: JSDashManifestRawSource?, audioSource: JSDashManifestRawAudioSource?, subtitleSource: ISubtitleSource?, resumePosition: Double, speed: Double?, castId: Int, onLoadingEstimate: ((Int) -> Unit)? = null, onLoading: ((Boolean) -> Unit)? = null) : List<String> {
        val ad = activeDevice ?: return listOf();

        cleanExecutors()
        _castServer.removeAllHandlers("castDashRaw")

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();

        val dashPath = "/dash-${id}"
        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val dashUrl = url + dashPath;
        Logger.i(TAG, "DASH url: $dashUrl");

        val videoUrl = url + videoPath
        val audioUrl = url + audioPath

        val subtitleMimeTypeFull = subtitleSource?.format ?: "text/vtt"
        val subtitleMimeTypeForMpd = subtitleMimeTypeFull.substringBefore(';').trim()

        val subtitlesUri = if (subtitleSource != null) withContext(Dispatchers.IO) {
            subtitleSource.getSubtitlesURI()
        } else null

        var subtitlesUrl: String? = null
        if (subtitlesUri != null) {
            when (subtitlesUri.scheme) {
                "file", "content" -> {
                    val content = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(subtitlesUri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        }
                    }

                    if (!content.isNullOrEmpty()) {
                        _castServer.addHandlerWithAllowAllOptions(
                            HttpConstantHandler("GET", subtitlePath, content, subtitleMimeTypeFull)
                                .withHeader("Access-Control-Allow-Origin", "*"),
                            true
                        ).withTag("castDashRaw")

                        subtitlesUrl = url + subtitlePath
                    }
                }

                "http", "https" -> {
                    // Receiver will fetch directly (works only if it doesn’t need auth/headers)
                    subtitlesUrl = subtitlesUri.toString()
                }

                else -> {
                    Logger.w(TAG, "Unsupported subtitlesUri scheme: ${subtitlesUri.scheme}")
                }
            }
        }

        var dashContent: String = withContext(Dispatchers.IO) {
            stopVideo()

            //TODO: Include subtitlesURl in the future
            val deferred = if (audioSource != null && videoSource != null) {
                JSDashManifestMergingRawSource(videoSource, audioSource).generateAsync(_scopeIO)
            } else if (audioSource != null) {
                audioSource.generateAsync(_scopeIO)
            } else if (videoSource != null) {
                videoSource.generateAsync(_scopeIO)
            } else {
                Logger.e(TAG, "Expected at least audio or video to be set")
                null
            }

            if (deferred != null) {
                try {
                    withContext(Dispatchers.Main) {
                        if (deferred.estDuration >= 0) {
                            onLoadingEstimate?.invoke(deferred.estDuration)
                        } else {
                            onLoading?.invoke(true)
                        }
                    }
                    deferred.awaitCancelConverted()
                } finally {
                    if (castId == _castId.get()) {
                        withContext(Dispatchers.Main) {
                            onLoading?.invoke(false)
                        }
                    }
                }
            } else {
                return@withContext null
            }
        } ?: throw Exception("Dash is null")

        if (castId != _castId.get()) {
            Log.i(TAG, "Get DASH cancelled.")
            return emptyList()
        }

        if (subtitlesUrl != null) {
            dashContent = injectSubtitleAdaptationSet(
                dashContent,
                subtitlesUrl!!,
                subtitleMimeTypeForMpd
            )
        }

        var hasAudioInDash = false
        for (representation in representationRegex.findAll(dashContent)) {
            val mediaType = representation.groups[1]?.value ?: throw Exception("Media type should be found")

            if (mediaType.startsWith("audio/")) {
                hasAudioInDash = true
            }

            dashContent = mediaInitializationRegex.replace(dashContent) {
                if (it.range.first < representation.range.first || it.range.last > representation.range.last) {
                    return@replace it.value
                }

                if (mediaType.startsWith("video/")) {
                    return@replace "${it.groups[1]!!.value}=\"${videoUrl}?url=${URLEncoder.encode(it.groups[2]!!.value, "UTF-8").replace("%24Number%24", "\$Number\$")}&amp;mediaType=${URLEncoder.encode(mediaType, "UTF-8")}\""
                } else if (mediaType.startsWith("audio/")) {
                    return@replace "${it.groups[1]!!.value}=\"${audioUrl}?url=${URLEncoder.encode(it.groups[2]!!.value, "UTF-8").replace("%24Number%24", "\$Number\$")}&amp;mediaType=${URLEncoder.encode(mediaType, "UTF-8")}\""
                } else {
                    throw Exception("Expected audio or video")
                }
            }
        }

        if (videoSource != null && !videoSource.hasRequestExecutor) {
            throw Exception("Video source without request executor not supported")
        }

        if (audioSource != null && !audioSource.hasRequestExecutor) {
            throw Exception("Audio source without request executor not supported")
        }

        if (videoSource != null && videoSource.hasRequestExecutor) {
            val oldVideoExecutor = _videoExecutor
            oldVideoExecutor?.closeAsync()
            _videoExecutor = videoSource.getRequestExecutor()
        }

        if (audioSource != null) {
            val oldExecutor = _audioExecutor
            oldExecutor?.closeAsync()
            _audioExecutor = audioSource.getRequestExecutor()
        } else if (hasAudioInDash && videoSource != null) {
            val oldExecutor = _audioExecutor
            oldExecutor?.closeAsync()
            _audioExecutor = _videoExecutor
        }

        //TOOD: Else also handle the non request executor case, perhaps add ?url=$originalUrl to the query parameters, ... propagate this for all other flows also

        Logger.v(TAG) { "Dash manifest: $dashContent" };

        _castServer.addHandlerWithAllowAllOptions(
            HttpConstantHandler("GET", dashPath, dashContent,
                "application/dash+xml")
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("castDashRaw");

        if (videoSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", videoPath) { httpContext ->
                    val originalUrl = httpContext.query["url"]?.let { URLDecoder.decode(it, "UTF-8") } ?: return@HttpFunctionHandler
                    val mediaType = httpContext.query["mediaType"]?.let { URLDecoder.decode(it, "UTF-8") } ?: return@HttpFunctionHandler

                    val videoExecutor = _videoExecutor;
                    if (videoExecutor != null) {
                        val data = videoExecutor.executeRequest("GET", originalUrl, null, httpContext.headers)
                        httpContext.respondBytes(200, HttpHeaders().apply {
                            put("Content-Type", mediaType)
                        }, data);
                    } else {
                        throw NotImplementedError()
                    }
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castDashRaw");
        }
        if (audioSource != null || (audioSource == null && hasAudioInDash)) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", audioPath) { httpContext ->
                    val originalUrl = httpContext.query["url"]?.let { URLDecoder.decode(it, "UTF-8") } ?: return@HttpFunctionHandler
                    val mediaType = httpContext.query["mediaType"]?.let { URLDecoder.decode(it, "UTF-8") } ?: return@HttpFunctionHandler

                    val audioExecutor = _audioExecutor;
                    if (audioExecutor != null) {
                        val data = audioExecutor.executeRequest("GET", originalUrl, null, httpContext.headers)
                        httpContext.respondBytes(200, HttpHeaders().apply {
                            put("Content-Type", mediaType)
                        }, data);
                    } else {
                        throw NotImplementedError()
                    }
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castDashRaw");
        }

        Logger.i(TAG, "added new castDash handlers (dashPath: $dashPath, videoPath: $videoPath, audioPath: $audioPath).");
        ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", "application/dash+xml", dashUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf()
    }

    private fun videoCodecRank(codecs: String): Int {
        val c = codecs.lowercase();
        return when {
            c.startsWith("avc1") || c.startsWith("avc3") -> 0
            c.startsWith("vp9") || c.startsWith("vp09") -> 1
            c.startsWith("av01") -> 2
            c.startsWith("vp8") -> 3
            else -> 4
        }
    }

    private fun audioCodecRank(codecs: String): Int {
        val c = codecs.lowercase();
        return when {
            c.startsWith("mp4a") -> 0
            c.startsWith("opus") -> 1
            c.startsWith("vorbis") -> 2
            else -> 3
        }
    }

    fun previewCastVideoFormat(source: JSUMPSource, preferredHeight: Int): SabrFormat? =
        selectCastVideoFormat(source, preferredHeight)

    private fun castCanDecode(format: SabrFormat): Boolean {
        if (format.codecs.isBlank()) return false;
        return videoCodecRank(format.codecs) <= 1;
    }

    private fun selectCastVideoFormat(source: JSUMPSource, preferredHeight: Int): SabrFormat? {
        val decodable = source.videoFormats.filter { castCanDecode(it) };
        if (decodable.isEmpty()) {
            Logger.w(TAG, "No cast-decodable video format (have: ${source.videoFormats.joinToString { it.codecs }})");
            return null;
        }

        val pool = decodable.filter { it.height > 0 }.ifEmpty { decodable };
        if (pool.isEmpty()) return null;

        val bestAt = { height: Int ->
            pool.filter { it.height == height }
                .minWithOrNull(compareBy({ videoCodecRank(it.codecs) }, { -it.bitrate }))
        };

        val selected = if (preferredHeight > 0) {
            bestAt(preferredHeight)
                ?: pool.filter { it.height < preferredHeight }.maxByOrNull { it.height }?.let { bestAt(it.height) }
                ?: pool.filter { it.height > preferredHeight }.minByOrNull { it.height }?.let { bestAt(it.height) }
        } else {
            val cap = pool.filter { it.height <= CAST_AUTO_MAX_HEIGHT }.maxByOrNull { it.height }?.height
                ?: pool.minOf { it.height };
            bestAt(cap)
        };

        if (selected != null && videoCodecRank(selected.codecs) > 0)
            Logger.i(TAG, "UMP cast using non-AVC video (${selected.codecs} ${selected.height}p); older receivers may not decode this.");

        return selected;
    }

    private fun selectCastAudioFormat(source: JSUMPSource): SabrFormat? {
        val decodable = source.audioFormats.filter { it.codecs.isNotBlank() && audioCodecRank(it.codecs) <= 2 };
        if (decodable.isEmpty()) {
            Logger.w(TAG, "No cast-decodable audio format (have: ${source.audioFormats.joinToString { it.codecs }})");
            return null;
        }
        val pool = decodable.filter { it.isOriginalAudio }.ifEmpty { decodable };
        return pool.minWithOrNull(compareBy({ audioCodecRank(it.codecs) }, { -it.bitrate }));
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    private fun takeRunningCastState(videoId: String): SabrSession.Transferable? {
        val proxy = _sabrCastProxy ?: return null
        if (proxy.videoId != videoId) return null
        return proxy.exportTransferable()
    }

    private suspend fun castUMP(video: IPlatformVideoDetails, source: JSUMPSource, subtitleSource: ISubtitleSource?, resumePosition: Double, speed: Double?, castId: Int, preferredVideoHeight: Int = -1, sabrState: SabrSession.Transferable? = null, onError: ((Throwable) -> Unit)? = null, onLoadingEstimate: ((Int) -> Unit)? = null, onLoading: ((Boolean) -> Unit)? = null) : List<String> {
        val ad = activeDevice ?: throw Exception("The cast device disconnected before the stream could be prepared");

        cleanExecutors();
        _castServer.removeAllHandlers("castUMP");

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();
        val dashPath = "/dash-$id";
        val videoInitPath = "/umpvi-$id";
        val videoSegPath = "/umpvs-$id";
        val audioInitPath = "/umpai-$id";
        val audioSegPath = "/umpas-$id";
        val subtitlePath = "/umpsub-$id";
        val timePath = "/umptime-$id";

        val bestVideo = selectCastVideoFormat(source, preferredVideoHeight);
        val bestAudio = selectCastAudioFormat(source);

        if (source.videoFormats.isNotEmpty() && bestVideo == null)
            throw UnsupportedCastException("this video has no format the receiver can decode");
        if (source.audioFormats.isNotEmpty() && bestAudio == null)
            throw UnsupportedCastException("this video has no audio format the receiver can decode");

        val session = source.toStreamSpec({ ManagedHttpClient().apply {
            user_agent = FutoVideoPlayerBase.DEFAULT_USER_AGENT;
            setCallTimeout(FutoVideoPlayerBase.SABR_CALL_TIMEOUT_MS);
            setReadTimeout(FutoVideoPlayerBase.SABR_READ_TIMEOUT_MS);
        } }, ownsClient = true).createSession();
        sabrState?.let { session.restore(it) };

        val proxy = com.futo.platformplayer.sabr.SabrCastProxy(session, bestVideo, bestAudio);

        var installed = false;
        setPreparingCastProxy(proxy);
        try {

        proxy.playheadUs = {
            val d = activeDevice;
            if (d != null && castId == _castId.get() && d.hasReportedTime)
                (d.expectedCurrentTime.coerceAtLeast(0.0) * 1_000_000.0).toLong()
            else null
        };

        proxy.onBackoff = { delayMs ->
            _scopeMain.launch {
                if (castId == _castId.get()) {
                    if (delayMs != null && delayMs >= SABR_BACKOFF_UI_THRESHOLD_MS)
                        onLoadingEstimate?.invoke(delayMs.toInt());
                    else if (delayMs == null)
                        onLoading?.invoke(false);
                }
            }
        };
        proxy.onFatalError = { error ->
            _scopeMain.launch {
                if (castId == _castId.get()) {
                    onLoading?.invoke(false);
                    stopVideo();
                    retireProxy(proxy);

                    if (onError != null) {
                        onError.invoke(error);
                        return@launch;
                    }

                    UIDialogs.appToast(when (error) {
                        is SabrBlockedException -> "Casting stopped: YouTube rejected the playback token. Reload the video and try again."
                        is SabrReloadRequiredException -> "Casting stopped: the stream expired. Reload the video and try again."
                        is SabrFormatSubstitutedException -> "Casting stopped: the stream format is out of sync. Update your plugins."
                        else -> "Casting stopped: ${error.message ?: "stream failed"}"
                    });
                }
            }
        };

        val manifest = withContext(Dispatchers.IO) {
            stopVideo();
            try {
                if(!proxy.prepare((resumePosition * 1_000_000.0).toLong()))
                    throw Exception("Failed to prepare SABR cast stream");
                proxy.buildManifest(url + videoInitPath, url + videoSegPath, url + audioInitPath, url + audioSegPath, url + timePath)
                    ?: throw Exception("Failed to build SABR cast manifest");
            } catch(ex: Throwable) {
                proxy.release();
                throw ex;
            } finally {
                if(castId == _castId.get())
                    onLoading?.let { withContext(Dispatchers.Main) { it(false) } }
            }
        };

        if (castId != _castId.get()) {
            Logger.i(TAG, "A newer cast superseded this one while it was preparing");
            proxy.release();
            throw CastSupersededException("Superseded by a newer cast");
        }

        if(!installCastProxy(proxy, castId, ad)) {
            Logger.i(TAG, "Cast device went away while preparing; dropping the SABR cast");
            proxy.release();
            throw Exception("The cast device disconnected while the stream was being prepared");
        }

        installed = true;

        try {
        val subtitleMime = subtitleSource?.format ?: "text/vtt";
        var subtitlesUrl: String? = null;
        if(subtitleSource != null && !proxy.isLive) {
            val subUri = withContext(Dispatchers.IO) { subtitleSource.getSubtitlesURI() };
            val content = when(subUri?.scheme) {
                "file", "content" -> withContext(Dispatchers.IO) {
                    StateApp.instance.contextOrNull?.contentResolver?.openInputStream(subUri)?.use { it.bufferedReader().readText() }
                }
                else -> null
            };
            if(!content.isNullOrEmpty()) {
                _castServer.addHandlerWithAllowAllOptions(
                    HttpConstantHandler("GET", subtitlePath, content, subtitleMime)
                        .withHeader("Access-Control-Allow-Origin", "*"), true
                ).withTag("castUMP");
                subtitlesUrl = url + subtitlePath;
            } else if(subUri?.scheme == "http" || subUri?.scheme == "https") {
                subtitlesUrl = subUri.toString();
            }
        }

        val finalManifest = if(subtitlesUrl != null)
            injectSubtitleAdaptationSet(manifest, subtitlesUrl!!, subtitleMime.substringBefore(';').trim(),
                subtitleSource?.language?.takeIf { it.isNotBlank() } ?: "und", subtitleSource?.name ?: "Subtitles")
        else manifest;

        Logger.v(TAG) { "UMP DASH manifest: $finalManifest" };

        if(proxy.isLive) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", timePath) { ctx ->
                    val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString();
                    ctx.respondBytes(200, HttpHeaders().apply {
                        put("Content-Type", "text/plain")
                        put("Cache-Control", "no-cache, no-store, must-revalidate")
                    }, now.toByteArray());
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", dashPath) { ctx ->
                    val live = proxy.buildManifest(url + videoInitPath, url + videoSegPath, url + audioInitPath, url + audioSegPath, url + timePath);
                    if(live == null) ctx.respondCode(503, "SABR live manifest unavailable")
                    else ctx.respondBytes(200, HttpHeaders().apply {
                        put("Content-Type", "application/dash+xml")
                        put("Cache-Control", "no-cache, no-store, must-revalidate")
                    }, live.toByteArray());
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
        } else {
            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", dashPath, finalManifest, "application/dash+xml")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
        }

        if(bestVideo != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", videoInitPath) { ctx ->
                    val data = proxy.getInit(SabrSession.ROLE_VIDEO);
                    if(data == null) ctx.respondCode(504, "SABR init unavailable")
                    else ctx.respondBytes(200, HttpHeaders().apply { put("Content-Type", bestVideo.containerMimeType) }, data);
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", videoSegPath) { ctx ->
                    val seq = ctx.query["n"]?.toIntOrNull();
                    val data = seq?.let { proxy.getSegment(SabrSession.ROLE_VIDEO, it) };
                    if(data == null) ctx.respondCode(404, "SABR segment unavailable")
                    else ctx.respondBytes(200, HttpHeaders().apply { put("Content-Type", bestVideo.containerMimeType) }, data);
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
        }
        if(bestAudio != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", audioInitPath) { ctx ->
                    val data = proxy.getInit(SabrSession.ROLE_AUDIO);
                    if(data == null) ctx.respondCode(504, "SABR init unavailable")
                    else ctx.respondBytes(200, HttpHeaders().apply { put("Content-Type", bestAudio.containerMimeType) }, data);
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
            _castServer.addHandlerWithAllowAllOptions(
                HttpFunctionHandler("GET", audioSegPath) { ctx ->
                    val seq = ctx.query["n"]?.toIntOrNull();
                    val data = seq?.let { proxy.getSegment(SabrSession.ROLE_AUDIO, it) };
                    if(data == null) ctx.respondCode(404, "SABR segment unavailable")
                    else ctx.respondBytes(200, HttpHeaders().apply { put("Content-Type", bestAudio.containerMimeType) }, data);
                }.withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castUMP");
        }

        val streamType = if(proxy.isLive) "LIVE" else "BUFFERED";
        val startPosition = when {
            proxy.isLive -> proxy.servableStartSeconds() ?: 0.0;
            ad.protocolType == CastProtocolType.CHROMECAST && resumePosition == 0.0 -> 0.1;
            else -> resumePosition;
        };
        val duration = if (!proxy.isLive && proxy.durationSeconds > 0.0) proxy.durationSeconds else video.duration.toDouble();

        proxy.onReceiverLost = {
            _scopeMain.launch {
                if (castId == _castId.get() && _sabrCastProxy === proxy && activeDevice === ad) {
                    val targetSeconds = proxy.servableStartSeconds();
                    if (targetSeconds != null) {
                        Logger.i(TAG, "Receiver drifted out of the servable window; seeking it to ${targetSeconds}s");
                        try {
                            ad.seekTo(targetSeconds);
                        } catch (ex: Throwable) {
                            Logger.w(TAG, "Failed to seek the receiver back into the window", ex);
                        }
                    }
                }
            }
        };

        ad.loadVideo(streamType, "application/dash+xml", url + dashPath, startPosition, duration, speed, metadataFromVideo(video));
        _castProxyServing = true;
        return listOf();
        } catch (ex: Throwable) {
            Logger.e(TAG, "Failed to start the SABR cast; tearing it down", ex);
            retireProxy(proxy);
            throw ex;
        }

        } finally {
            setPreparingCastProxy(null);
            if (!installed) proxy.release();
        }
    }

    fun addRememberedDevice(deviceInfo: CastingDeviceInfo): CastingDeviceInfo? {
        return when (val device = deviceFromInfo(deviceInfo)) {
            null -> null
            else -> addRememberedDevice(device)
        }
    }

    fun addRememberedDevice(device: CastingDevice): CastingDeviceInfo {
        val deviceInfo = device.getDeviceInfo()
        return _storage.addDevice(deviceInfo)
    }

    fun getRememberedCastingDevices(): List<CastingDevice> {
        return _storage.getDevices().map { deviceFromInfo(it) }.filterNotNull()
    }

    fun getRememberedCastingDeviceNames(): List<String> {
        return _storage.getDeviceNames()
    }

    fun removeRememberedDevice(device: CastingDevice) {
        val name = device.name ?: return
        _storage.removeDevice(name)
    }

    fun enableDeveloper(enableDev: Boolean) {
        _castServer.removeAllHandlers("dev");
        if (enableDev) {
            _castServer.addHandler(HttpFunctionHandler("GET", "/dashPlayer") { context ->
                if (context.query.containsKey("dashUrl")) {
                    val dashUrl = context.query["dashUrl"];
                    val html =
                        "<div>\n" + " <video id=\"test\" width=\"1280\" height=\"720\" controls>\n" + " </video>\n" + " \n" + " \n" + "    <script src=\"https://cdn.dashjs.org/latest/dash.all.min.js\"></script>\n" + "    <script>\n" + "    <!--setup the video element and attach it to the Dash player-->\n" + "            (function(){\n" + "                var url = \"${dashUrl}\";\n" + "                var player = dashjs.MediaPlayer().create();\n" + "                player.initialize(document.querySelector(\"#test\"), url, true);\n" + "            })();\n" + "    </script>\n" + "</div>";
                    context.respondCode(200, html, "text/html");
                }
            }).withTag("dev");
        }
    }

    companion object {
        var instance = StateCasting()
        private val representationRegex = Regex(
            "<Representation .*?mimeType=\"(.*?)\".*?>(.*?)<\\/Representation>",
            RegexOption.DOT_MATCHES_ALL
        )
        private val mediaInitializationRegex =
            Regex("(media|initiali[sz]ation)=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL);

        private val TAG = "StateCasting";
        private const val SABR_BACKOFF_UI_THRESHOLD_MS = 1500L;
        private const val HAND_BACK_TTL_MS = 60_000L;

        const val CAST_AUTO_MAX_HEIGHT = 1080;
    }
}