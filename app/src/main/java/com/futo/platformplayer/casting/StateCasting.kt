package com.futo.platformplayer.casting

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
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
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalAudioContentSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalVideoContentSource
import com.futo.platformplayer.awaitCancelConverted
import com.futo.platformplayer.builders.DashBuilder
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcast.sender_sdk.Metadata
import java.net.Inet6Address
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

abstract class StateCasting {
    val _scopeIO = CoroutineScope(Dispatchers.IO);
    val _scopeMain = CoroutineScope(Dispatchers.Main);
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
    var activeDevice: CastingDevice? = null;
    private var _videoExecutor: JSRequestExecutor? = null
    private var _audioExecutor: JSRequestExecutor? = null
    private val _client = ManagedHttpClient();
    var _resumeCastingDevice: CastingDeviceInfo? = null;
    val isCasting: Boolean get() = activeDevice != null;
    private val _castId = AtomicInteger(0)

    abstract fun handleUrl(url: String)
    abstract fun onStop()
    abstract fun start(context: Context)
    abstract fun stop()

    abstract fun deviceFromInfo(deviceInfo: CastingDeviceInfo): CastingDevice?
    abstract fun startUpdateTimeJob(
        onTimeJobTimeChanged_s: Event1<Long>, setTime: (Long) -> Unit
    ): Job?

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
                activeDevice = null;
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

        try {
            device.connect();
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to connect to device.");
            device.onConnectionStateChanged.clear();
            device.onPlayChanged.clear();
            device.onTimeChanged.clear();
            device.onVolumeChanged.clear();
            device.onDurationChanged.clear();
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
    suspend fun castIfAvailable(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoSource?, audioSource: IAudioSource?, subtitleSource: ISubtitleSource?, ms: Long = -1, speed: Double?, onLoadingEstimate: ((Int) -> Unit)? = null, onLoading: ((Boolean) -> Unit)? = null): Boolean {
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

            if (sourceCount > 1) {
                if (videoSource is LocalVideoSource || audioSource is LocalAudioSource || subtitleSource is LocalSubtitleSource) {
                    if (deviceProto == CastProtocolType.AIRPLAY) {
                        Logger.i(TAG, "Casting as local HLS");
                        castLocalHls(video, videoSource as LocalVideoSource?, audioSource as LocalAudioSource?, subtitleSource as LocalSubtitleSource?, resumePosition, speed);
                    } else {
                        Logger.i(TAG, "Casting as local DASH");
                        castLocalDash(video, videoSource as LocalVideoSource?, audioSource as LocalAudioSource?, subtitleSource as LocalSubtitleSource?, resumePosition, speed);
                    }
                } else {
                    val isRawDash =
                        videoSource is JSDashManifestRawSource || audioSource is JSDashManifestRawAudioSource
                    if (isRawDash) {
                        Logger.i(TAG, "Casting as raw DASH");

                        castDashRaw(contentResolver, video, videoSource as JSDashManifestRawSource?, audioSource as JSDashManifestRawAudioSource?, subtitleSource, resumePosition, speed, castId, onLoadingEstimate, onLoading);
                    } else {
                        if (deviceProto == CastProtocolType.FCAST) {
                            Logger.i(TAG, "Casting as DASH direct");
                            castDashDirect(contentResolver, video, videoSource as IVideoUrlSource?, audioSource as IAudioUrlSource?, subtitleSource, resumePosition, speed);
                        } else if (deviceProto == CastProtocolType.AIRPLAY) {
                            Logger.i(TAG, "Casting as HLS indirect");
                            castHlsIndirect(contentResolver, video, videoSource as IVideoUrlSource?, audioSource as IAudioUrlSource?, subtitleSource, resumePosition, speed);
                        } else {
                            Logger.i(TAG, "Casting as DASH indirect");
                            castDashIndirect(contentResolver, video, videoSource as IVideoUrlSource?, audioSource as IAudioUrlSource?, subtitleSource, resumePosition, speed);
                        }
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

    private fun castLocalHls(video: IPlatformVideoDetails, videoSource: LocalVideoSource?, audioSource: LocalAudioSource?, subtitleSource: LocalSubtitleSource?, resumePosition: Double, speed: Double?): List<String> {
        val ad = activeDevice ?: return listOf()

        val url = getLocalUrl(ad)
        val id = UUID.randomUUID()

        val hlsPath = "/hls-${id}"
        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val hlsUrl = url + hlsPath
        val videoUrl = url + videoPath
        val audioUrl = url + audioPath
        val subtitleUrl = url + subtitlePath

        val mediaRenditions = arrayListOf<HLS.MediaRendition>()
        val variantPlaylistReferences = arrayListOf<HLS.VariantPlaylistReference>()

        if (videoSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFileHandler("GET", videoPath, videoSource.container, videoSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castLocalHls")

            val duration = videoSource.duration
            val videoVariantPlaylistPath = "/video-playlist-${id}"
            val videoVariantPlaylistUrl = url + videoVariantPlaylistPath
            val videoVariantPlaylistSegments = listOf(HLS.MediaSegment(duration.toDouble(), videoUrl))
            val videoVariantPlaylist = HLS.VariantPlaylist(3, duration.toInt(), 0, 0, null, null, null, videoVariantPlaylistSegments)

            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", videoVariantPlaylistPath, videoVariantPlaylist.buildM3U8(),
                    "application/vnd.apple.mpegurl")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castLocalHls")

            variantPlaylistReferences.add(HLS.VariantPlaylistReference(videoVariantPlaylistUrl, HLS.StreamInfo(
                videoSource.bitrate, "${videoSource.width}x${videoSource.height}", videoSource.codec, null, null, if (audioSource != null) "audio" else null, if (subtitleSource != null) "subtitles" else null, null, null)))
        }

        if (audioSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFileHandler("GET", audioPath, audioSource.container, audioSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castLocalHls")

            val duration = audioSource.duration ?: videoSource?.duration ?: throw Exception("Duration unknown")
            val audioVariantPlaylistPath = "/audio-playlist-${id}"
            val audioVariantPlaylistUrl = url + audioVariantPlaylistPath
            val audioVariantPlaylistSegments = listOf(HLS.MediaSegment(duration.toDouble(), audioUrl))
            val audioVariantPlaylist = HLS.VariantPlaylist(3, duration.toInt(), 0, 0, null, null, null, audioVariantPlaylistSegments)

            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", audioVariantPlaylistPath, audioVariantPlaylist.buildM3U8(),
                    "application/vnd.apple.mpegurl")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castLocalHls")

            mediaRenditions.add(HLS.MediaRendition("AUDIO", audioVariantPlaylistUrl, "audio", "df", "default", true, true, true))
        }

        if (subtitleSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpFileHandler("GET", subtitlePath, subtitleSource.format ?: "text/vtt", subtitleSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castLocalHls")

            val duration = videoSource?.duration ?: audioSource?.duration ?: throw Exception("Duration unknown")
            val subtitleVariantPlaylistPath = "/subtitle-playlist-${id}"
            val subtitleVariantPlaylistUrl = url + subtitleVariantPlaylistPath
            val subtitleVariantPlaylistSegments = listOf(HLS.MediaSegment(duration.toDouble(), subtitleUrl))
            val subtitleVariantPlaylist = HLS.VariantPlaylist(3, duration.toInt(), 0, 0, null, null, null, subtitleVariantPlaylistSegments)

            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", subtitleVariantPlaylistPath, subtitleVariantPlaylist.buildM3U8(),
                    "application/vnd.apple.mpegurl")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castLocalHls")

            mediaRenditions.add(HLS.MediaRendition("SUBTITLES", subtitleVariantPlaylistUrl, "subtitles", "df", "default", true, true, true))
        }

        val masterPlaylist = HLS.MasterPlaylist(variantPlaylistReferences, mediaRenditions, listOf(), true)
        _castServer.addHandlerWithAllowAllOptions(
            HttpConstantHandler("GET", hlsPath, masterPlaylist.buildM3U8(),
                "application/vnd.apple.mpegurl")
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("castLocalHls")

        Logger.i(TAG, "added new castLocalHls handlers (hlsPath: $hlsPath, videoPath: $videoPath, audioPath: $audioPath, subtitlePath: $subtitlePath).")
        ad.loadVideo("BUFFERED", "application/vnd.apple.mpegurl", hlsUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video))

        return listOf(hlsUrl, videoUrl, audioUrl, subtitleUrl)
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

    private suspend fun castDashDirect(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: ISubtitleSource?, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();
        val proxyStreams = shouldProxyStreams(ad, videoSource, audioSource)

        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();

        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val videoUrl = if(proxyStreams) url + videoPath else videoSource?.getVideoUrl();
        val audioUrl = if(proxyStreams) url + audioPath else audioSource?.getAudioUrl();

        val subtitlesUri = if (subtitleSource != null) withContext(Dispatchers.IO) {
            return@withContext subtitleSource.getSubtitlesURI();
        } else null;

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

        if (videoSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", videoPath, videoSource.getVideoUrl(), true)
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }
        if (audioSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", audioPath, audioSource.getAudioUrl(), true)
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }

        val content = DashBuilder.generateOnDemandDash(videoSource, videoUrl, audioSource, audioUrl, subtitleSource, subtitlesUrl);

        Logger.i(TAG, "Direct dash cast to casting device (videoUrl: $videoUrl, audioUrl: $audioUrl).");
        Logger.v(TAG) { "Dash manifest: $content" };
        ad.loadContent("application/dash+xml", content, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(videoUrl ?: "", audioUrl ?: "", subtitlesUrl ?: "", videoSource?.getVideoUrl() ?: "", audioSource?.getAudioUrl() ?: "", subtitlesUri.toString());
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

                val req = requestModifier?.modifyRequest(sourceUrl, mapOf())
                val masterPlaylistResponse = _client.get(req?.url ?: sourceUrl, (req?.headers ?: mapOf()).toMutableMap())

                check(masterPlaylistResponse.isOk) { "Failed to get master playlist: ${masterPlaylistResponse.code}" }

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

                            val response = _client.get(variantPlaylistRef.url)
                            check(response.isOk) { "Failed to get variant playlist: ${response.code}" }

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

                                val response = _client.get(mediaRendition.uri)
                                check(response.isOk) { "Failed to get variant playlist: ${response.code}" }

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

    private suspend fun castHlsIndirect(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: ISubtitleSource?, resumePosition: Double, speed: Double?) : List<String> {
        val ad = activeDevice ?: return listOf();
        val url = getLocalUrl(ad);
        val id = UUID.randomUUID();

        val hlsPath = "/hls-${id}"

        val hlsUrl = url + hlsPath;
        Logger.i(TAG, "HLS url: $hlsUrl");

        val mediaRenditions = arrayListOf<HLS.MediaRendition>()
        val variantPlaylistReferences = arrayListOf<HLS.VariantPlaylistReference>()

        if (audioSource != null) {
            val audioPath = "/audio-${id}"
            val audioUrl = url + audioPath

            val duration = audioSource.duration ?: videoSource?.duration ?: throw Exception("Duration unknown")
            val audioVariantPlaylistPath = "/audio-playlist-${id}"
            val audioVariantPlaylistUrl = url + audioVariantPlaylistPath
            val audioVariantPlaylistSegments = listOf(HLS.MediaSegment(duration.toDouble(), audioUrl))
            val audioVariantPlaylist = HLS.VariantPlaylist(3, duration.toInt(), 0, 0, null, null, null, audioVariantPlaylistSegments)

            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", audioVariantPlaylistPath, audioVariantPlaylist.buildM3U8(),
                    "application/vnd.apple.mpegurl")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castHlsIndirectVariant");

            mediaRenditions.add(HLS.MediaRendition("AUDIO", audioVariantPlaylistUrl, "audio", "df", "default", true, true, true))

            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", audioPath, audioSource.getAudioUrl(), true)
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castHlsIndirectVariant");
        }

        val subtitlesUri = if (subtitleSource != null) withContext(Dispatchers.IO) {
            return@withContext subtitleSource.getSubtitlesURI();
        } else null;

        var subtitlesUrl: String? = null;
        if (subtitlesUri != null) {
            val subtitlePath = "/subtitles-${id}"
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
                    ).withTag("castHlsIndirectVariant");
                }

                subtitlesUrl = url + subtitlePath;
            } else {
                subtitlesUrl = subtitlesUri.toString();
            }
        }

        if (subtitlesUrl != null) {
            val duration = videoSource?.duration ?: audioSource?.duration ?: throw Exception("Duration unknown")
            val subtitleVariantPlaylistPath = "/subtitle-playlist-${id}"
            val subtitleVariantPlaylistUrl = url + subtitleVariantPlaylistPath
            val subtitleVariantPlaylistSegments = listOf(HLS.MediaSegment(duration.toDouble(), subtitlesUrl))
            val subtitleVariantPlaylist = HLS.VariantPlaylist(3, duration.toInt(), 0, 0, null, null, null, subtitleVariantPlaylistSegments)

            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", subtitleVariantPlaylistPath, subtitleVariantPlaylist.buildM3U8(),
                    "application/vnd.apple.mpegurl")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castHlsIndirectVariant");

            mediaRenditions.add(HLS.MediaRendition("SUBTITLES", subtitleVariantPlaylistUrl, "subtitles", "df", "default", true, true, true))
        }

        if (videoSource != null) {
            val videoPath = "/video-${id}"
            val videoUrl = url + videoPath

            val duration = videoSource.duration
            val videoVariantPlaylistPath = "/video-playlist-${id}"
            val videoVariantPlaylistUrl = url + videoVariantPlaylistPath
            val videoVariantPlaylistSegments = listOf(HLS.MediaSegment(duration.toDouble(), videoUrl))
            val videoVariantPlaylist = HLS.VariantPlaylist(3, duration.toInt(), 0, 0, null, null, null, videoVariantPlaylistSegments)

            _castServer.addHandlerWithAllowAllOptions(
                HttpConstantHandler("GET", videoVariantPlaylistPath, videoVariantPlaylist.buildM3U8(),
                    "application/vnd.apple.mpegurl")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castHlsIndirectVariant");

            variantPlaylistReferences.add(HLS.VariantPlaylistReference(videoVariantPlaylistUrl, HLS.StreamInfo(
                videoSource.bitrate ?: 0,
                "${videoSource.width}x${videoSource.height}",
                videoSource.codec,
                null,
                null,
                if (audioSource != null) "audio" else null,
                if (subtitleSource != null) "subtitles" else null,
                null, null)))

            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", videoPath, videoSource.getVideoUrl(), true)
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castHlsIndirectVariant");
        }

        val masterPlaylist = HLS.MasterPlaylist(variantPlaylistReferences, mediaRenditions, listOf(), true)
        _castServer.addHandlerWithAllowAllOptions(
            HttpConstantHandler("GET", hlsPath, masterPlaylist.buildM3U8(),
                "application/vnd.apple.mpegurl")
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("castHlsIndirectMaster")

        Logger.i(TAG, "added new castHls handlers (hlsPath: $hlsPath).");
        ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", "application/vnd.apple.mpegurl", hlsUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(hlsUrl, videoSource?.getVideoUrl() ?: "", audioSource?.getAudioUrl() ?: "", subtitlesUri.toString());
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
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }
        if (audioSource != null) {
            _castServer.addHandlerWithAllowAllOptions(
                HttpProxyHandler("GET", audioPath, audioSource.getAudioUrl(), true)
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        }

        Logger.i(TAG, "added new castDash handlers (dashPath: $dashPath, videoPath: $videoPath, audioPath: $audioPath).");
        ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", "application/dash+xml", dashUrl, resumePosition, video.duration.toDouble(), speed, metadataFromVideo(video));

        return listOf(dashUrl, videoUrl ?: "", audioUrl ?: "", subtitlesUrl ?: "", videoSource?.getVideoUrl() ?: "", audioSource?.getAudioUrl() ?: "", subtitlesUri.toString());
    }

    fun cleanExecutors() {
        if (_videoExecutor != null) {
            _videoExecutor?.cleanup()
            _videoExecutor = null
        }

        if (_audioExecutor != null) {
            _audioExecutor?.cleanup()
            _audioExecutor = null
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

        val subtitlesUri = if (subtitleSource != null) withContext(Dispatchers.IO) {
            return@withContext subtitleSource.getSubtitlesURI();
        } else null;

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

        for (representation in representationRegex.findAll(dashContent)) {
            val mediaType = representation.groups[1]?.value ?: throw Exception("Media type should be found")
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

        if (audioSource != null && audioSource.hasRequestExecutor) {
            val oldExecutor = _audioExecutor;
            oldExecutor?.closeAsync();
            _audioExecutor = audioSource.getRequestExecutor()
        }

        if (videoSource != null && videoSource.hasRequestExecutor) {
            val oldExecutor = _videoExecutor;
            oldExecutor?.closeAsync();
            _videoExecutor = videoSource.getRequestExecutor()
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
        if (audioSource != null) {
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
        var instance: StateCasting = if (Settings.instance.casting.experimentalCasting) {
            StateCastingExp()
        } else {
            StateCastingLegacy()
        }
        private val representationRegex = Regex(
            "<Representation .*?mimeType=\"(.*?)\".*?>(.*?)<\\/Representation>",
            RegexOption.DOT_MATCHES_ALL
        )
        private val mediaInitializationRegex =
            Regex("(media|initiali[sz]ation)=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL);

        private val TAG = "StateCasting";
    }
}