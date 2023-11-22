package com.futo.platformplayer.casting

import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import com.futo.platformplayer.*
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.http.server.ManagedHttpServer
import com.futo.platformplayer.api.http.server.handlers.*
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.builders.DashBuilder
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.exceptions.UnsupportedCastException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.parsers.HLS
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.*
import java.net.InetAddress
import java.util.*
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.collections.HashMap
import com.futo.platformplayer.stores.CastingDeviceInfoStorage
import com.futo.platformplayer.stores.FragmentedStorage
import javax.jmdns.ServiceTypeListener

class StateCasting {
    private val _scopeIO = CoroutineScope(Dispatchers.IO);
    private val _scopeMain = CoroutineScope(Dispatchers.Main);
    private var _jmDNS: JmDNS? = null;
    private val _storage: CastingDeviceInfoStorage = FragmentedStorage.get();

    private val _castServer = ManagedHttpServer(9999);
    private var _started = false;

    var devices: HashMap<String, CastingDevice> = hashMapOf();
    var rememberedDevices: ArrayList<CastingDevice> = arrayListOf();
    val onDeviceAdded = Event1<CastingDevice>();
    val onDeviceChanged = Event1<CastingDevice>();
    val onDeviceRemoved = Event1<CastingDevice>();
    val onActiveDeviceConnectionStateChanged = Event2<CastingDevice, CastConnectionState>();
    val onActiveDevicePlayChanged = Event1<Boolean>();
    val onActiveDeviceTimeChanged = Event1<Double>();
    var activeDevice: CastingDevice? = null;
    private val _client = ManagedHttpClient();

    val isCasting: Boolean get() = activeDevice != null;

    private val _chromecastServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            Logger.i(TAG, "ChromeCast service added: " + event.info);
            addOrUpdateDevice(event);
        }

        override fun serviceRemoved(event: ServiceEvent) {
            Logger.i(TAG, "ChromeCast service removed: " + event.info);
            synchronized(devices) {
                val device = devices[event.info.name];
                if (device != null) {
                    onDeviceRemoved.emit(device);
                }
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            Logger.v(TAG, "ChromeCast service resolved: " + event.info);
            addOrUpdateDevice(event);
        }

        fun addOrUpdateDevice(event: ServiceEvent) {
            addOrUpdateChromeCastDevice(event.info.name, event.info.inetAddresses, event.info.port);
        }
    }

    private val _airPlayServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            Logger.i(TAG, "AirPlay service added: " + event.info);
            addOrUpdateDevice(event);
        }

        override fun serviceRemoved(event: ServiceEvent) {
            Logger.i(TAG, "AirPlay service removed: " + event.info);
            synchronized(devices) {
                val device = devices[event.info.name];
                if (device != null) {
                    onDeviceRemoved.emit(device);
                }
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            Logger.i(TAG, "AirPlay service resolved: " + event.info);
            addOrUpdateDevice(event);
        }

        fun addOrUpdateDevice(event: ServiceEvent) {
            addOrUpdateAirPlayDevice(event.info.name, event.info.inetAddresses, event.info.port);
        }
    }

    private val _fastCastServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            Logger.i(TAG, "FastCast service added: " + event.info);
            addOrUpdateDevice(event);
        }

        override fun serviceRemoved(event: ServiceEvent) {
            Logger.i(TAG, "FastCast service removed: " + event.info);
            synchronized(devices) {
                val device = devices[event.info.name];
                if (device != null) {
                    onDeviceRemoved.emit(device);
                }
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            Logger.i(TAG, "FastCast service resolved: " + event.info);
            addOrUpdateDevice(event);
        }

        fun addOrUpdateDevice(event: ServiceEvent) {
            addOrUpdateFastCastDevice(event.info.name, event.info.inetAddresses, event.info.port);
        }
    }

    private val _serviceTypeListener = object : ServiceTypeListener {
        override fun serviceTypeAdded(event: ServiceEvent?) {
            if (event == null) {
                return;
            }

            Logger.i(TAG, "Service type added (name: ${event.name}, type: ${event.type})");
        }

        override fun subTypeForServiceTypeAdded(event: ServiceEvent?) {
            if (event == null) {
                return;
            }

            Logger.i(TAG, "Sub type for service type added (name: ${event.name}, type: ${event.type})");
        }
    }

    fun onStop() {
        val ad = activeDevice ?: return;
        Logger.i(TAG, "Stopping active device because of onStop.");
        ad.stop();
    }

    @Synchronized
    fun start(context: Context) {
        if (_started)
            return;
        _started = true;

        Logger.i(TAG, "CastingService starting...");

        rememberedDevices.clear();
        rememberedDevices.addAll(_storage.deviceInfos.map { deviceFromCastingDeviceInfo(it) });

        _scopeIO.launch {
            try {
                val jmDNS = JmDNS.create(InetAddress.getLocalHost());
                jmDNS.addServiceListener("_googlecast._tcp.local.", _chromecastServiceListener);
                jmDNS.addServiceListener("_airplay._tcp.local.", _airPlayServiceListener);
                jmDNS.addServiceListener("_fastcast._tcp.local.", _fastCastServiceListener);

                if (BuildConfig.DEBUG) {
                    jmDNS.addServiceTypeListener(_serviceTypeListener);
                }

                _jmDNS = jmDNS;
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to start casting service.", e);
            }
        }
        _castServer.start();
        enableDeveloper(context.contentResolver, true);

        Logger.i(TAG, "CastingService started.");
    }

    @Synchronized
    fun stop() {
        if (!_started)
            return;

        _started = false;

        Logger.i(TAG, "CastingService stopping.")

        val jmDNS = _jmDNS;
        if (jmDNS != null) {
            _scopeIO.launch {
                try {
                    jmDNS.removeServiceListener("_googlecast._tcp.local.", _chromecastServiceListener);
                    jmDNS.removeServiceListener("_airplay._tcp", _airPlayServiceListener);

                    if (BuildConfig.DEBUG) {
                        jmDNS.removeServiceTypeListener(_serviceTypeListener);
                    }

                    jmDNS.close();
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to stop mDNS.", e);
                }
            }
        }

        _scopeIO.cancel();
        _scopeMain.cancel();

        Logger.i(TAG, "Stopping active device because StateCasting is being stopped.")
        val d = activeDevice;
        activeDevice = null;
        d?.stop();

        _castServer.stop();
        _castServer.removeAllHandlers();

        Logger.i(TAG, "CastingService stopped.")
    }

    @Synchronized
    fun connectDevice(device: CastingDevice) {
        if (activeDevice == device)
            return;

        val ad = activeDevice;
        if (ad != null) {
            Logger.i(TAG, "Stopping previous device because a new one is being connected.")
            ad.onPlayChanged.clear();
            ad.onTimeChanged.clear();
            ad.onConnectionStateChanged.clear();
            ad.stop();
        }

        device.onConnectionStateChanged.subscribe { castConnectionState ->
            Logger.i(TAG, "Active device connection state changed: $castConnectionState");

            if (castConnectionState == CastConnectionState.DISCONNECTED) {
                Logger.i(TAG, "Clearing events: $castConnectionState");

                device.onPlayChanged.clear();
                device.onTimeChanged.clear();
                device.onConnectionStateChanged.clear();
                activeDevice = null;
            }

            invokeInMainScopeIfRequired {
                StateApp.withContext(false) { context ->
                    context.let {
                        when (castConnectionState) {
                            CastConnectionState.CONNECTED -> UIDialogs.toast(it, "Connected to device")
                            CastConnectionState.CONNECTING -> UIDialogs.toast(it, "Connecting to device...")
                            CastConnectionState.DISCONNECTED -> UIDialogs.toast(it, "Disconnected from device")
                        }
                    }
                };
                onActiveDeviceConnectionStateChanged.emit(device, castConnectionState);
            };
        };
        device.onPlayChanged.subscribe {
            invokeInMainScopeIfRequired { onActiveDevicePlayChanged.emit(it) };
        }
        device.onTimeChanged.subscribe {
            invokeInMainScopeIfRequired { onActiveDeviceTimeChanged.emit(it) };
        };

        addRememberedDevice(device);
        Logger.i(TAG, "Device added to active discovery. Active discovery now contains ${_storage.getDevicesCount()} devices.")

        try {
            device.start();
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to connect to device.");
            device.onConnectionStateChanged.clear();
            device.onPlayChanged.clear();
            device.onTimeChanged.clear();
            return;
        }

        activeDevice = device;
        Logger.i(TAG, "Connect to device ${device.name}");
    }

    fun addRememberedDevice(deviceInfo: CastingDeviceInfo) {
        val device = deviceFromCastingDeviceInfo(deviceInfo);
        addRememberedDevice(device);
    }

    fun addRememberedDevice(device: CastingDevice) {
        if (_storage.addDevice(device.getDeviceInfo())) {
            rememberedDevices.add(device);
        }
    }

    fun removeRememberedDevice(device: CastingDevice) {
        val name = device.name ?: return;
        _storage.removeDevice(name);
        rememberedDevices.remove(device);
    }

    private fun invokeInMainScopeIfRequired(action: () -> Unit){
        if(Looper.getMainLooper().thread != Thread.currentThread()) {
            _scopeMain.launch { action(); }
            return;
        }

        action();
    }

    fun castIfAvailable(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoSource?, audioSource: IAudioSource?, subtitleSource: ISubtitleSource?, ms: Long = -1): Boolean {
        val ad = activeDevice ?: return false;
        if (ad.connectionState != CastConnectionState.CONNECTED) {
            return false;
        }

        val resumePosition = if (ms > 0L) (ms.toDouble() / 1000.0) else 0.0;

        var sourceCount = 0;
        if (videoSource != null) sourceCount++;
        if (audioSource != null) sourceCount++;
        if (subtitleSource != null) sourceCount++;

        if (sourceCount < 1) {
            throw Exception("At least one source should be specified.");
        }

        if (sourceCount > 1) {
            if (ad is AirPlayCastingDevice) {
                StateApp.withContext(false) { context -> UIDialogs.toast(context, "AirPlay does not support DASH. Try ChromeCast or FastCast for casting this video."); };
                ad.stopCasting();
                return false;
            }

            if (videoSource is LocalVideoSource || audioSource is LocalAudioSource || subtitleSource is LocalSubtitleSource) {
                castLocalDash(video, videoSource as LocalVideoSource?, audioSource as LocalAudioSource?, subtitleSource as LocalSubtitleSource?, resumePosition);
            } else {
                StateApp.instance.scope.launch(Dispatchers.IO) {
                    try {
                        if (ad is FastCastCastingDevice) {
                            castDashDirect(contentResolver, video, videoSource as IVideoUrlSource?, audioSource as IAudioUrlSource?, subtitleSource, resumePosition);
                        } else {
                            castDashIndirect(contentResolver, video, videoSource as IVideoUrlSource?, audioSource as IAudioUrlSource?, subtitleSource, resumePosition);
                        }
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to start casting DASH videoSource=${videoSource} audioSource=${audioSource}.", e);
                    }
                }
            }
        } else {
            if (videoSource is IVideoUrlSource)
                ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", videoSource.container, videoSource.getVideoUrl(), resumePosition, video.duration.toDouble());
            else if (audioSource is IAudioUrlSource)
                ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", audioSource.container, audioSource.getAudioUrl(), resumePosition, video.duration.toDouble());
            else if(videoSource is IHLSManifestSource) {
                if (ad is ChromecastCastingDevice && video.isLive) {
                    castHlsIndirect(video, videoSource.url, resumePosition);
                } else {
                    ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", videoSource.container, videoSource.url, resumePosition, video.duration.toDouble());
                }
            } else if(audioSource is IHLSManifestAudioSource) {
                if (ad is ChromecastCastingDevice && video.isLive) {
                    castHlsIndirect(video, audioSource.url, resumePosition);
                } else {
                    ad.loadVideo(if (video.isLive) "LIVE" else "BUFFERED", audioSource.container, audioSource.url, resumePosition, video.duration.toDouble());
                }
            } else if (videoSource is LocalVideoSource)
                castLocalVideo(video, videoSource, resumePosition);
            else if (audioSource is LocalAudioSource)
                castLocalAudio(video, audioSource, resumePosition);
            else {
                var str = listOf(
                    if(videoSource != null) "Video: ${videoSource::class.java.simpleName}" else null,
                    if(audioSource != null) "Audio: ${audioSource::class.java.simpleName}" else null,
                    if(subtitleSource != null) "Subtitles: ${subtitleSource::class.java.simpleName}" else null
                ).filterNotNull().joinToString(", ");
                throw UnsupportedCastException(str);
            }
        }

        return true;
    }

    fun resumeVideo(): Boolean {
        val ad = activeDevice ?: return false;
        ad.resumeVideo();
        return true;
    }

    fun pauseVideo(): Boolean {
        val ad = activeDevice ?: return false;
        ad.pauseVideo();
        return true;
    }

    fun stopVideo(): Boolean {
        val ad = activeDevice ?: return false;
        ad.stopVideo();
        return true;
    }

    fun videoSeekTo(timeSeconds: Double): Boolean {
        val ad = activeDevice ?: return false;
        ad.seekVideo(timeSeconds);
        return true;
    }

    private fun castLocalVideo(video: IPlatformVideoDetails, videoSource: LocalVideoSource, resumePosition: Double) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = "http://${ad.localAddress.toString().trim('/')}:${_castServer.port}";
        val id = UUID.randomUUID();
        val videoPath = "/video-${id}"
        val videoUrl = url + videoPath;

        _castServer.addHandler(
            HttpFileHandler("GET", videoPath, videoSource.container, videoSource.filePath)
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        Logger.i(TAG, "Casting local video (videoUrl: $videoUrl).");
        ad.loadVideo("BUFFERED", videoSource.container, videoUrl, resumePosition, video.duration.toDouble());

        return listOf(videoUrl);
    }

    private fun castLocalAudio(video: IPlatformVideoDetails, audioSource: LocalAudioSource, resumePosition: Double) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = "http://${ad.localAddress.toString().trim('/')}:${_castServer.port}";
        val id = UUID.randomUUID();
        val audioPath = "/audio-${id}"
        val audioUrl = url + audioPath;

        _castServer.addHandler(
            HttpFileHandler("GET", audioPath, audioSource.container, audioSource.filePath)
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");

        Logger.i(TAG, "Casting local audio (audioUrl: $audioUrl).");
        ad.loadVideo("BUFFERED", audioSource.container, audioUrl, resumePosition, video.duration.toDouble());

        return listOf(audioUrl);
    }


    private fun castLocalDash(video: IPlatformVideoDetails, videoSource: LocalVideoSource?, audioSource: LocalAudioSource?, subtitleSource: LocalSubtitleSource?, resumePosition: Double) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = "http://${ad.localAddress.toString().trim('/')}:${_castServer.port}";
        val id = UUID.randomUUID();

        val dashPath = "/dash-${id}"
        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val dashUrl = url + dashPath;
        val videoUrl = url + videoPath;
        val audioUrl = url + audioPath;
        val subtitleUrl = url + subtitlePath;

        _castServer.addHandler(
                HttpConstantHandler("GET", dashPath, DashBuilder.generateOnDemandDash(videoSource, videoUrl, audioSource, audioUrl, subtitleSource, subtitleUrl),
                    "application/dash+xml")
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
        if (videoSource != null) {
            _castServer.addHandler(
                HttpFileHandler("GET", videoPath, videoSource.container, videoSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
            _castServer.addHandler(
                HttpOptionsAllowHandler(videoPath)
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withHeader("Connection", "keep-alive"))
                .withTag("cast");
        }
        if (audioSource != null) {
            _castServer.addHandler(
                HttpFileHandler("GET", audioPath, audioSource.container, audioSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
            _castServer.addHandler(
                HttpOptionsAllowHandler(audioPath)
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withHeader("Connection", "keep-alive"))
                .withTag("cast");
        }
        if (subtitleSource != null) {
            _castServer.addHandler(
                HttpFileHandler("GET", subtitlePath, subtitleSource.format ?: "text/vtt", subtitleSource.filePath)
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
            _castServer.addHandler(
                HttpOptionsAllowHandler(subtitlePath)
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withHeader("Connection", "keep-alive"))
                .withTag("cast");
        }

        Logger.i(TAG, "added new castLocalDash handlers (dashPath: $dashPath, videoPath: $videoPath, audioPath: $audioPath, subtitlePath: $subtitlePath).");
        ad.loadVideo("BUFFERED", "application/dash+xml", dashUrl, resumePosition, video.duration.toDouble());

        return listOf(dashUrl, videoUrl, audioUrl, subtitleUrl);
    }

    private suspend fun castDashDirect(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: ISubtitleSource?, resumePosition: Double) : List<String> {
        val ad = activeDevice ?: return listOf();

        val url = "http://${ad.localAddress.toString().trim('/')}:${_castServer.port}";
        val id = UUID.randomUUID();
        val subtitlePath = "/subtitle-${id}";

        val videoUrl = videoSource?.getVideoUrl();
        val audioUrl = audioSource?.getAudioUrl();

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
                    _castServer.addHandler(
                        HttpConstantHandler("GET", subtitlePath, content!!, subtitleSource?.format ?: "text/vtt")
                            .withHeader("Access-Control-Allow-Origin", "*"), true
                    ).withTag("cast");
                }

                subtitlesUrl = url + subtitlePath;
            } else {
                subtitlesUrl = subtitlesUri.toString();
            }
        }

        val content = DashBuilder.generateOnDemandDash(videoSource, videoUrl, audioSource, audioUrl, subtitleSource, subtitlesUrl);

        Logger.i(TAG, "Direct dash cast to casting device (videoUrl: $videoUrl, audioUrl: $audioUrl).");
        ad.loadContent("application/dash+xml", content, resumePosition, video.duration.toDouble());

        return listOf(videoSource?.getVideoUrl() ?: "", audioSource?.getAudioUrl() ?: "");
    }

    private fun castHlsIndirect(video: IPlatformVideoDetails, sourceUrl: String, resumePosition: Double): List<String> {
        _castServer.removeAllHandlers("castHlsIndirectMaster")

        val ad = activeDevice ?: return listOf();
        val url = "http://${ad.localAddress.toString().trim('/')}:${_castServer.port}";

        val id = UUID.randomUUID();
        val hlsPath = "/hls-${id}"
        val hlsUrl = url + hlsPath
        Logger.i(TAG, "HLS url: $hlsUrl");

        _castServer.addHandler(HttpFuntionHandler("GET", hlsPath) { masterContext ->
            _castServer.removeAllHandlers("castHlsIndirectVariant")

            val headers = masterContext.headers.clone()
            headers["Content-Type"] = "application/vnd.apple.mpegurl";

            val masterPlaylist = HLS.downloadAndParseMasterPlaylist(_client, sourceUrl)
            val newVariantPlaylistRefs = arrayListOf<HLS.VariantPlaylistReference>()
            val newMediaRenditions = arrayListOf<HLS.MediaRendition>()
            val newMasterPlaylist = HLS.MasterPlaylist(newVariantPlaylistRefs, newMediaRenditions, masterPlaylist.sessionDataList, masterPlaylist.independentSegments)

            for (variantPlaylistRef in masterPlaylist.variantPlaylistsRefs) {
                val playlistId = UUID.randomUUID();
                val newPlaylistPath = "/hls-playlist-${playlistId}"
                val newPlaylistUrl = url + newPlaylistPath;

                _castServer.addHandler(HttpFuntionHandler("GET", newPlaylistPath) { vpContext ->
                    val vpHeaders = vpContext.headers.clone()
                    vpHeaders["Content-Type"] = "application/vnd.apple.mpegurl";

                    val variantPlaylist = HLS.downloadAndParseVariantPlaylist(_client, variantPlaylistRef.url)
                    val proxiedVariantPlaylist = proxyVariantPlaylist(url, playlistId, variantPlaylist)
                    val proxiedVariantPlaylist_m3u8 = proxiedVariantPlaylist.buildM3U8()
                    vpContext.respondCode(200, vpHeaders, proxiedVariantPlaylist_m3u8);
                }.withHeader("Access-Control-Allow-Origin", "*"), true).withTag("castHlsIndirectVariant")

                newVariantPlaylistRefs.add(HLS.VariantPlaylistReference(
                    newPlaylistUrl,
                    variantPlaylistRef.streamInfo
                ))
            }

            for (mediaRendition in masterPlaylist.mediaRenditions) {
                val playlistId = UUID.randomUUID();
                val newPlaylistPath = "/hls-playlist-${playlistId}"
                val newPlaylistUrl = url + newPlaylistPath;

                if (mediaRendition.uri != null) {
                    _castServer.addHandler(HttpFuntionHandler("GET", newPlaylistPath) { vpContext ->
                        val vpHeaders = vpContext.headers.clone()
                        vpHeaders["Content-Type"] = "application/vnd.apple.mpegurl";

                        val variantPlaylist = HLS.downloadAndParseVariantPlaylist(_client, mediaRendition.uri)
                        val proxiedVariantPlaylist = proxyVariantPlaylist(url, playlistId, variantPlaylist)
                        val proxiedVariantPlaylist_m3u8 = proxiedVariantPlaylist.buildM3U8()
                        vpContext.respondCode(200, vpHeaders, proxiedVariantPlaylist_m3u8);
                    }.withHeader("Access-Control-Allow-Origin", "*"), true).withTag("castHlsIndirectVariant")
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
        }.withHeader("Access-Control-Allow-Origin", "*"), true).withTag("castHlsIndirectMaster")

        Logger.i(TAG, "added new castHlsIndirect handlers (hlsPath: $hlsPath).");
        ad.loadVideo("BUFFERED", "application/vnd.apple.mpegurl", hlsUrl, resumePosition, video.duration.toDouble());

        return listOf(hlsUrl);
    }

    private fun proxyVariantPlaylist(url: String, playlistId: UUID, variantPlaylist: HLS.VariantPlaylist, proxySegments: Boolean = true): HLS.VariantPlaylist {
        val newSegments = arrayListOf<HLS.Segment>()

        if (proxySegments) {
            variantPlaylist.segments.forEachIndexed { index, segment ->
                val sequenceNumber = variantPlaylist.mediaSequence + index.toLong()
                newSegments.add(proxySegment(url, playlistId, segment, sequenceNumber))
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
            newSegments
        )
    }

    private fun proxySegment(url: String, playlistId: UUID, segment: HLS.Segment, index: Long): HLS.Segment {
        val newSegmentPath = "/hls-playlist-${playlistId}-segment-${index}"
        val newSegmentUrl = url + newSegmentPath;

        if (_castServer.getHandler("GET", newSegmentPath) == null) {
            _castServer.addHandler(
                HttpProxyHandler("GET", newSegmentPath, segment.uri, true)
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("castHlsIndirectVariant")
        }

        return HLS.Segment(
            segment.duration,
            newSegmentUrl
        )
    }

    private suspend fun castDashIndirect(contentResolver: ContentResolver, video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: ISubtitleSource?, resumePosition: Double) : List<String> {
        val ad = activeDevice ?: return listOf();
        val proxyStreams = ad !is FastCastCastingDevice;

        val url = "http://${ad.localAddress.toString().trim('/')}:${_castServer.port}";
        Logger.i(TAG, "DASH url: $url");

        val id = UUID.randomUUID();

        val dashPath = "/dash-${id}"
        val videoPath = "/video-${id}"
        val audioPath = "/audio-${id}"
        val subtitlePath = "/subtitle-${id}"

        val dashUrl = url + dashPath;
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
                    _castServer.addHandler(
                        HttpConstantHandler("GET", subtitlePath, content!!, subtitleSource?.format ?: "text/vtt")
                            .withHeader("Access-Control-Allow-Origin", "*"), true
                    ).withTag("cast");
                }

                subtitlesUrl = url + subtitlePath;
            } else {
                subtitlesUrl = subtitlesUri.toString();
            }
        }

        _castServer.addHandler(
            HttpConstantHandler("GET", dashPath, DashBuilder.generateOnDemandDash(videoSource, videoUrl, audioSource, audioUrl, subtitleSource, subtitlesUrl),
                "application/dash+xml")
                .withHeader("Access-Control-Allow-Origin", "*"), true
        ).withTag("cast");
        if (videoSource != null) {
            _castServer.addHandler(
                HttpProxyHandler("GET", videoPath, videoSource.getVideoUrl())
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
            _castServer.addHandler(
                HttpOptionsAllowHandler(videoPath)
                .withHeader("Access-Control-Allow-Origin", "*")
                .withHeader("Connection", "keep-alive"))
                .withTag("cast");
        }
        if (audioSource != null) {
            _castServer.addHandler(
                HttpProxyHandler("GET", audioPath, audioSource.getAudioUrl())
                    .withInjectedHost()
                    .withHeader("Access-Control-Allow-Origin", "*"), true
            ).withTag("cast");
            _castServer.addHandler(
                HttpOptionsAllowHandler(audioPath)
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withHeader("Connection", "keep-alivcontexte"))
                .withTag("cast");
        }

        Logger.i(TAG, "added new castDash handlers (dashPath: $dashPath, videoPath: $videoPath, audioPath: $audioPath).");
        ad.loadVideo("BUFFERED", "application/dash+xml", dashUrl, resumePosition, video.duration.toDouble());

        return listOf(dashUrl, videoUrl ?: "", audioUrl ?: "", subtitlesUrl ?: "", videoSource?.getVideoUrl() ?: "", audioSource?.getAudioUrl() ?: "", subtitlesUri.toString());
    }

    private fun deviceFromCastingDeviceInfo(deviceInfo: CastingDeviceInfo): CastingDevice {
        return when (deviceInfo.type) {
            CastProtocolType.CHROMECAST -> {
                ChromecastCastingDevice(deviceInfo);
            }
            CastProtocolType.AIRPLAY -> {
                AirPlayCastingDevice(deviceInfo);
            }
            CastProtocolType.FASTCAST -> {
                FastCastCastingDevice(deviceInfo);
            }
            else -> throw Exception("${deviceInfo.type} is not a valid casting protocol")
        }
    }

    private fun addOrUpdateChromeCastDevice(name: String, addresses: Array<InetAddress>, port: Int) {
        return addOrUpdateCastDevice<ChromecastCastingDevice>(name,
            deviceFactory = { ChromecastCastingDevice(name, addresses, port) },
            deviceUpdater = { d ->
                if (d.isReady) {
                    return@addOrUpdateCastDevice false;
                }

                val changed = addresses.contentEquals(d.addresses) || d.name != name || d.port != port;
                if (changed) {
                    d.name = name;
                    d.addresses = addresses;
                    d.port = port;
                }

                return@addOrUpdateCastDevice changed;
            }
        );
    }

    private fun addOrUpdateAirPlayDevice(name: String, addresses: Array<InetAddress>, port: Int) {
        return addOrUpdateCastDevice<AirPlayCastingDevice>(name,
            deviceFactory = { AirPlayCastingDevice(name, addresses, port) },
            deviceUpdater = { d ->
                if (d.isReady) {
                    return@addOrUpdateCastDevice false;
                }

                val changed = addresses.contentEquals(addresses) || d.name != name || d.port != port;
                if (changed) {
                    d.name = name;
                    d.port = port;
                    d.addresses = addresses;
                }

                return@addOrUpdateCastDevice changed;
            }
        );
    }

    private fun addOrUpdateFastCastDevice(name: String, addresses: Array<InetAddress>, port: Int) {
        return addOrUpdateCastDevice<FastCastCastingDevice>(name,
            deviceFactory = { FastCastCastingDevice(name, addresses, port) },
            deviceUpdater = { d ->
                if (d.isReady) {
                    return@addOrUpdateCastDevice false;
                }

                val changed = addresses.contentEquals(addresses) || d.name != name || d.port != port;
                if (changed) {
                    d.name = name;
                    d.port = port;
                    d.addresses = addresses;
                }

                return@addOrUpdateCastDevice changed;
            }
        );
    }

    private inline fun <reified TCastDevice> addOrUpdateCastDevice(name: String, deviceFactory: () -> TCastDevice, deviceUpdater: (device: TCastDevice) -> Boolean) where TCastDevice : CastingDevice {
        var invokeEvents: (() -> Unit)? = null;

        synchronized(devices) {
            val device = devices[name];
            if (device != null) {
                if (device !is TCastDevice) {
                    Logger.w(TAG, "Device name conflict between device types. Ignoring device.");
                } else {
                    val changed = deviceUpdater(device as TCastDevice);
                    if (changed) {
                        invokeEvents = {
                            onDeviceChanged.emit(device);
                        }
                    } else {

                    }
                }
            } else {
                val newDevice = deviceFactory();
                devices[name] = newDevice;

                invokeEvents = {
                    onDeviceAdded.emit(newDevice);
                };
            }
        }

        invokeEvents?.let { _scopeMain.launch { it(); }; };
    }

    fun enableDeveloper(contentResolver: ContentResolver, enableDev: Boolean){
        _castServer.removeAllHandlers("dev");
        if(enableDev) {
            _castServer.addHandler(HttpFuntionHandler("GET", "/dashPlayer") { context ->
                if (context.query.containsKey("dashUrl")) {
                    val dashUrl = context.query["dashUrl"];
                    val html = "<div>\n" +
                            " <video id=\"test\" width=\"1280\" height=\"720\" controls>\n" +
                            " </video>\n" +
                            " \n" +
                            " \n" +
                            "    <script src=\"https://cdn.dashjs.org/latest/dash.all.min.js\"></script>\n" +
                            "    <script>\n" +
                            "    <!--setup the video element and attach it to the Dash player-->\n" +
                            "            (function(){\n" +
                            "                var url = \"${dashUrl}\";\n" +
                            "                var player = dashjs.MediaPlayer().create();\n" +
                            "                player.initialize(document.querySelector(\"#test\"), url, true);\n" +
                            "            })();\n" +
                            "    </script>\n" +
                            "</div>";
                    context.respondCode(200, html, "text/html");
                }
            }).withTag("dev");
        }
    }

    companion object {
        val instance: StateCasting = StateCasting();

        private val TAG = "StateCasting";
    }
}

