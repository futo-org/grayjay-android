package com.futo.platformplayer.views.video

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.annotation.OptIn
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlWidevineSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestWidevineSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlWidevineSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSAudioUrlRangeSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestMergingRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSHLSManifestAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlRangeSource
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.video.datasources.PluginMediaDrmCallback
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import getHttpDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs

abstract class FutoVideoPlayerBase : RelativeLayout {
    private val TAG = "FutoVideoPlayerBase"

    private val TEMP_DIRECTORY = StateApp.instance.getTempDirectory();

    private var _mediaSource: MediaSource? = null;

    var lastVideoSource: IVideoSource? = null
        private set;
    var lastAudioSource: IAudioSource? = null
        private set;

    private var _lastVideoMediaSource: MediaSource? = null;
    private var _lastGeneratedDash: String? = null;
    private var _lastAudioMediaSource: MediaSource? = null;
    private var _lastSubtitleMediaSource: MediaSource? = null;
    private var _shouldPlaybackRestartOnConnectivity: Boolean = false;
    private val _referenceObject = Object();
    private var _connectivityLossTime_ms: Long? = null

    private var _ignoredChapters: ArrayList<IChapter> = arrayListOf();
    private var _chapters: List<IChapter>? = null;

    var exoPlayer: PlayerManager? = null
        private set;
    val exoPlayerStateName: String;

    var playing: Boolean = false;
    val position: Long get() = exoPlayer?.player?.currentPosition ?: 0;
    val duration: Long get() = exoPlayer?.player?.duration ?: 0;

    var isAudioMode: Boolean = false
        private set;

    val onPlayChanged = Event1<Boolean>();
    val onStateChange = Event1<Int>();
    val onPositionDiscontinuity = Event1<Long>();
    val onDatasourceError = Event1<Throwable>();

    private var _didCallSourceChange = false;
    private var _lastState: Int = -1;


    var targetTrackVideoHeight = -1
        private set
    private var _targetTrackAudioBitrate = -1

    private var _toResume = false;

    private val _playerEventListener = object: Player.Listener {
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        }

        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            super.onSurfaceSizeChanged(width, height)
            this@FutoVideoPlayerBase.onSurfaceSizeChanged(width, height);
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying);
            this@FutoVideoPlayerBase.onIsPlayingChanged(isPlaying);
            updatePlaying();
        }

        //TODO: Figure out why this is deprecated, and what the alternative is.
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            this@FutoVideoPlayerBase.onPlaybackStateChanged(playbackState);

            if(_lastState != playbackState) {
                _lastState = playbackState;
                onStateChange.emit(playbackState);
            }
            when(playbackState) {
                Player.STATE_READY -> {
                    if(!_didCallSourceChange) {
                        _didCallSourceChange = true;
                        onSourceChanged(lastVideoSource, lastAudioSource, _toResume);
                    }
                }
            }

            updatePlaying();
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            updatePlaying();
        }

        fun updatePlaying() {
            val newPlaying = exoPlayer?.let { it.player.playWhenReady && it.player.playbackState != Player.STATE_ENDED && it.player.playbackState != Player.STATE_IDLE } ?: false
            if (newPlaying == playing) {
                return;
            }

            playing = newPlaying;
            onPlayChanged.emit(playing);
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            this@FutoVideoPlayerBase.onVideoSizeChanged(videoSize);
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason);
            onPositionDiscontinuity.emit(newPosition.positionMs);
        }

        override fun onCues(cueGroup: CueGroup) {
            super.onCues(cueGroup)
            Logger.i(TAG, "CUE GROUP: ${cueGroup.cues.firstOrNull()?.text}");
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error);
            this@FutoVideoPlayerBase.onPlayerError(error);
        }
    };

    constructor(stateName: String, context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) : super(context, attrs, defStyleAttr, defStyleRes) {
        this.exoPlayerStateName = stateName;
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow();

        Logger.v(TAG, "Attached onConnectionAvailable listener.");
        StateApp.instance.onConnectionAvailable.subscribe(_referenceObject) {
            Logger.v(TAG, "onConnectionAvailable connectivityLossTime = $_connectivityLossTime_ms, position = $position, duration = $duration");

            val pos = position;
            val dur = duration;
            var shouldRestartPlayback = false
            if (_shouldPlaybackRestartOnConnectivity && abs(pos - dur) > 2000) {
                if (Settings.instance.playback.restartPlaybackAfterConnectivityLoss == 1) {
                    val lossTime_ms = _connectivityLossTime_ms
                    if (lossTime_ms != null) {
                        val lossDuration_ms = System.currentTimeMillis() - lossTime_ms
                        Logger.v(TAG, "onConnectionAvailable lossDuration=$lossDuration_ms")
                        if (lossDuration_ms < 1000 * 10) {
                            shouldRestartPlayback = true
                        }
                    }
                } else if (Settings.instance.playback.restartPlaybackAfterConnectivityLoss == 2) {
                    val lossTime_ms = _connectivityLossTime_ms
                    if (lossTime_ms != null) {
                        val lossDuration_ms = System.currentTimeMillis() - lossTime_ms
                        Logger.v(TAG, "onConnectionAvailable lossDuration=$lossDuration_ms")
                        if (lossDuration_ms < 1000 * 30) {
                            shouldRestartPlayback = true
                        }
                    }
                } else if (Settings.instance.playback.restartPlaybackAfterConnectivityLoss == 3) {
                    shouldRestartPlayback = true
                }
            }

            Logger.v(TAG, "onConnectionAvailable shouldRestartPlayback = $shouldRestartPlayback");

            if (shouldRestartPlayback) {
                Logger.i(TAG, "Playback ended due to connection loss, resuming playback since connection is restored.");
                exoPlayer?.player?.playWhenReady = true;
                exoPlayer?.player?.prepare();
                exoPlayer?.player?.play();
            }

            _connectivityLossTime_ms = null;
        };
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow();

        Logger.i(TAG, "Detached onConnectionAvailable listener.");
        StateApp.instance.onConnectionAvailable.remove(_referenceObject);
    }

    fun switchToVideoMode() {
        Logger.i(TAG, "Switching to Video Mode");
        isAudioMode = false;
        loadSelectedSources(playing, true);
    }
    fun switchToAudioMode() {
        Logger.i(TAG, "Switching to Audio Mode");
        isAudioMode = true;
        loadSelectedSources(playing, true);
    }

    fun seekTo(ms: Long) {
        Logger.i(TAG, "Seeking to [${ms}ms]");
        exoPlayer?.player?.seekTo(ms);
    }
    fun seekToEnd(ms: Long = 0) {
        val duration = Math.max(exoPlayer?.player?.duration ?: 0, 0);
        exoPlayer?.player?.seekTo(Math.max(duration - ms, 0));
    }
    fun seekFromCurrent(ms: Long) {
        val to = Math.max((exoPlayer?.player?.currentPosition ?: 0) + ms, 0);
        exoPlayer?.player?.seekTo(Math.min(to, exoPlayer?.player?.duration ?: to));
    }

    fun changePlayer(newPlayer: PlayerManager?) {
        exoPlayer?.modifyState(exoPlayerStateName, {state -> state.listener = null});
        newPlayer?.modifyState(exoPlayerStateName, {state -> state.listener = _playerEventListener});
        exoPlayer = newPlayer;
    }

    //TODO: Temporary solution, Implement custom track selector without using constraints
    fun selectVideoTrack(height: Int) {
        targetTrackVideoHeight = height;
        updateTrackSelector();
    }
    fun selectAudioTrack(bitrate: Int) {
        _targetTrackAudioBitrate = bitrate;
        updateTrackSelector();
    }
    @OptIn(UnstableApi::class)
    private fun updateTrackSelector() {
        var builder = DefaultTrackSelector.Parameters.Builder(context);
        if(targetTrackVideoHeight > 0) {
            builder = builder
                .setMinVideoSize(0, targetTrackVideoHeight - 10)
                .setMaxVideoSize(9999, targetTrackVideoHeight + 10);
        }

        if(_targetTrackAudioBitrate > 0) {
            builder = builder.setMaxAudioBitrate(_targetTrackAudioBitrate);
        }

        builder = if (isAudioMode) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
        }

        val trackSelector = exoPlayer?.player?.trackSelector;
        if(trackSelector != null) {
            trackSelector.parameters = builder.build();
        }
    }

    fun setChapters(chapters: List<IChapter>?) {
        _ignoredChapters = arrayListOf();
        _chapters = chapters;
    }
    fun getChapters(): List<IChapter> {
        return _chapters?.let { it.toList() } ?: listOf();
    }
    fun ignoreChapter(chapter: IChapter) {
        synchronized(_ignoredChapters) {
            if(!_ignoredChapters.contains(chapter))
                _ignoredChapters.add(chapter);
        }
    }
    fun getCurrentChapter(pos: Long): IChapter? {
        val toIgnore = synchronized(_ignoredChapters){ _ignoredChapters.toList() };
        return _chapters?.let { chaps -> chaps.find { pos.toDouble() / 1000 > it.timeStart && pos.toDouble() / 1000 < it.timeEnd && (toIgnore.isEmpty() || !toIgnore.contains(it)) } };
    }

    fun setSource(videoSource: IVideoSource?, audioSource: IAudioSource? = null, play: Boolean = false, keepSubtitles: Boolean = false, resume: Boolean = false) {
        swapSources(videoSource, audioSource,resume, play, keepSubtitles);
    }
    fun swapSources(videoSource: IVideoSource?, audioSource: IAudioSource?, resume: Boolean = true, play: Boolean = true, keepSubtitles: Boolean = false): Boolean {
        var videoSourceUsed = videoSource;
        var audioSourceUsed = audioSource;
        if(videoSource is JSDashManifestRawSource && audioSource is JSDashManifestRawAudioSource){
            videoSourceUsed = JSDashManifestMergingRawSource(videoSource, audioSource);
            audioSourceUsed = null;
        }

        val didSetVideo = swapSourceInternal(videoSourceUsed, play, resume);
        val didSetAudio = swapSourceInternal(audioSourceUsed, play, resume);
        if(!keepSubtitles)
            _lastSubtitleMediaSource = null;
        if(didSetVideo && didSetAudio)
            return loadSelectedSources(play, resume);
        else
            return true;
    }
    fun swapSource(videoSource: IVideoSource?, resume: Boolean = true, play: Boolean = true): Boolean {
        var videoSourceUsed = videoSource;
        if(videoSource is JSDashManifestRawSource && lastVideoSource is JSDashManifestMergingRawSource)
            videoSourceUsed = JSDashManifestMergingRawSource(videoSource, (lastVideoSource as JSDashManifestMergingRawSource).audio);
        val didSet = swapSourceInternal(videoSourceUsed, play, resume);
        if(didSet)
            return loadSelectedSources(play, resume);
        else
            return true;
    }
    fun swapSource(audioSource: IAudioSource?, resume: Boolean = true, play: Boolean = true): Boolean {
        if(audioSource is JSDashManifestRawAudioSource && lastVideoSource is JSDashManifestMergingRawSource)
            swapSourceInternal(JSDashManifestMergingRawSource((lastVideoSource as JSDashManifestMergingRawSource).video, audioSource), play, resume);
        else
            swapSourceInternal(audioSource, play, resume);
        return loadSelectedSources(play, resume);
    }

    @OptIn(UnstableApi::class)
    fun swapSubtitles(scope: CoroutineScope, subtitles: ISubtitleSource?) {
        if(subtitles == null)
            clearSubtitles();
        else {
            if(SUPPORTED_SUBTITLES.contains(subtitles.format?.lowercase())) {
                if (!subtitles.hasFetch) {
                    _lastSubtitleMediaSource = SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT)))
                        .createMediaSource(MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitles.url))
                            .setMimeType(subtitles.format)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build(),
                            C.TIME_UNSET);
                    loadSelectedSources(true, true);
                } else {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val subUri = subtitles.getSubtitlesURI() ?: return@launch;
                            withContext(Dispatchers.Main) {
                                try {
                                    _lastSubtitleMediaSource = SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT)))
                                        .createMediaSource(MediaItem.SubtitleConfiguration.Builder(subUri)
                                            .setMimeType(subtitles.format)
                                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                            .build(),
                                            C.TIME_UNSET);
                                    loadSelectedSources(true, true);
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "Failed to load selected sources after subtitle download.", e)
                                }
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to get subtitles URI.", e)
                        }
                    }
                }
            }
            else
                clearSubtitles();
        }
    }
    private fun clearSubtitles() {
        _lastSubtitleMediaSource = null;
        loadSelectedSources(true, true);
    }


    private fun swapSourceInternal(videoSource: IVideoSource?, play: Boolean, resume: Boolean): Boolean {
        _lastGeneratedDash = null;
        val didSet = when(videoSource) {
            is LocalVideoSource -> { swapVideoSourceLocal(videoSource); true; }
            is JSVideoUrlRangeSource -> { swapVideoSourceUrlRange(videoSource); true; }
            is IDashManifestWidevineSource -> { swapVideoSourceDashWidevine(videoSource); true }
            is IDashManifestSource -> { swapVideoSourceDash(videoSource); true;}
            is JSDashManifestRawSource -> swapVideoSourceDashRaw(videoSource, play, resume);
            is IHLSManifestSource -> { swapVideoSourceHLS(videoSource); true; }
            is IVideoUrlWidevineSource -> { swapVideoSourceUrlWidevine(videoSource); true; }
            is IVideoUrlSource -> { swapVideoSourceUrl(videoSource); true; }
            null -> { _lastVideoMediaSource = null; true;}
            else -> throw IllegalArgumentException("Unsupported video source [${videoSource.javaClass.simpleName}]");
        }
        lastVideoSource = videoSource;
        return didSet;
    }
    private fun swapSourceInternal(audioSource: IAudioSource?, play: Boolean, resume: Boolean): Boolean {
        val didSet = when(audioSource) {
            is LocalAudioSource -> {swapAudioSourceLocal(audioSource); true; }
            is JSAudioUrlRangeSource -> { swapAudioSourceUrlRange(audioSource); true; }
            is JSHLSManifestAudioSource -> { swapAudioSourceHLS(audioSource); true; }
            is JSDashManifestRawAudioSource -> swapAudioSourceDashRaw(audioSource, play, resume);
            is IAudioUrlWidevineSource -> { swapAudioSourceUrlWidevine(audioSource); true; }
            is IAudioUrlSource -> { swapAudioSourceUrl(audioSource); true; }
            null -> { _lastAudioMediaSource = null; true; }
            else -> throw IllegalArgumentException("Unsupported video source [${audioSource.javaClass.simpleName}]");
        }
        lastAudioSource = audioSource;
        return didSet;
    }

    //Video loads
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceLocal(videoSource: LocalVideoSource) {
        Logger.i(TAG, "Loading VideoSource [Local]");
        val file = File(videoSource.filePath);
        if(!file.exists())
            throw IllegalArgumentException("File for this video does not exist");
        _lastVideoMediaSource = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)));
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceUrlRange(videoSource: JSVideoUrlRangeSource) {
        Logger.i(TAG, "Loading JSVideoUrlRangeSource");
        if(videoSource.hasItag) {
            //Temporary workaround for Youtube
            try {
                val results = VideoHelper.convertItagSourceToChunkedDashSource(videoSource);
                _lastGeneratedDash = results.second;
                _lastVideoMediaSource = results.first;
                return;
            }
            //If it fails to create the dash workaround, fallback to standard progressive
            catch(ex: Exception) {
                Logger.i(TAG, "Dash manifest workaround failed for video, falling back to progressive due to ${ex.message}");
                _lastVideoMediaSource = ProgressiveMediaSource.Factory(videoSource.getHttpDataSourceFactory())
                    .createMediaSource(MediaItem.fromUri(videoSource.getVideoUrl()));
                return;
            }
        }
        else throw IllegalArgumentException("source without itag data...");
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceUrl(videoSource: IVideoUrlSource) {
        Logger.i(TAG, "Loading VideoSource [Url]");
        val dataSource = if(videoSource is JSSource && videoSource.requiresCustomDatasource)
            videoSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);
        _lastVideoMediaSource = ProgressiveMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(videoSource.getVideoUrl()));
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceUrlWidevine(videoSource: IVideoUrlWidevineSource) {
        Logger.i(TAG, "Loading VideoSource [UrlWidevine]");
        val dataSource = if(videoSource is JSSource && videoSource.requiresCustomDatasource)
            videoSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT)

        val baseCallback = HttpMediaDrmCallback(videoSource.licenseUri, dataSource)

        val callback = if (videoSource.hasLicenseRequestExecutor) {
            PluginMediaDrmCallback(baseCallback, videoSource.getLicenseRequestExecutor()!!, videoSource.licenseUri)
        } else {
            baseCallback
        }

        _lastVideoMediaSource = ProgressiveMediaSource.Factory(dataSource)
            .setDrmSessionManagerProvider {
                DefaultDrmSessionManager.Builder()
                    .setMultiSession(true)
                    .build(callback)
            }
            .createMediaSource(
                MediaItem.fromUri(videoSource.getVideoUrl())
            )
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceDash(videoSource: IDashManifestSource) {
        Logger.i(TAG, "Loading VideoSource [Dash]");
        val dataSource = if(videoSource is JSSource && (videoSource.requiresCustomDatasource))
            videoSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);
        _lastVideoMediaSource = DashMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(videoSource.url))
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceDashWidevine(videoSource: IDashManifestWidevineSource) {
        Logger.i(TAG, "Loading VideoSource [DashWidevine]")
        val dataSource =
            if (videoSource is JSSource && (videoSource.requiresCustomDatasource)) videoSource.getHttpDataSourceFactory()
            else DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT)

        val baseCallback = HttpMediaDrmCallback(videoSource.licenseUri, dataSource)

        val callback = if (videoSource.hasLicenseRequestExecutor) {
            PluginMediaDrmCallback(baseCallback, videoSource.getLicenseRequestExecutor()!!, videoSource.licenseUri)
        } else {
            baseCallback
        }

        _lastVideoMediaSource = DashMediaSource.Factory(dataSource).setDrmSessionManagerProvider {
                DefaultDrmSessionManager.Builder().setMultiSession(true).build(callback)
            }.createMediaSource(MediaItem.fromUri(videoSource.url))
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceDashRaw(videoSource: JSDashManifestRawSource, play: Boolean, resume: Boolean): Boolean {
        Logger.i(TAG, "Loading VideoSource [Dash]");

        if(videoSource.hasGenerate) {
            findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.launch(Dispatchers.IO) {
                try {
                    val generated = videoSource.generate();
                    if (generated != null) {
                        withContext(Dispatchers.Main) {
                            val dataSource = if(videoSource is JSSource && (videoSource.requiresCustomDatasource))
                                videoSource.getHttpDataSourceFactory()
                            else
                                DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);

                            if(dataSource is JSHttpDataSource.Factory && videoSource is JSDashManifestMergingRawSource)
                                dataSource.setRequestExecutor2(videoSource.audio.getRequestExecutor());
                            _lastVideoMediaSource = DashMediaSource.Factory(dataSource)
                                .createMediaSource(
                                    DashManifestParser().parse(
                                        Uri.parse(videoSource.url),
                                        ByteArrayInputStream(
                                            generated?.toByteArray() ?: ByteArray(0)
                                        )
                                    )
                                );
                            if(lastVideoSource == videoSource || (videoSource is JSDashManifestMergingRawSource && videoSource.video == lastVideoSource));
                                loadSelectedSources(play, resume);
                        }
                    }
                }
                catch(ex: Throwable) {
                    Logger.e(TAG, "DashRaw generator failed", ex);
                }
            }
            return false;
        }
        else {
            val dataSource = if(videoSource is JSSource && (videoSource.requiresCustomDatasource))
                videoSource.getHttpDataSourceFactory()
            else
                DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);

            if(dataSource is JSHttpDataSource.Factory && videoSource is JSDashManifestMergingRawSource)
                dataSource.setRequestExecutor2(videoSource.audio.getRequestExecutor());
            _lastVideoMediaSource = DashMediaSource.Factory(dataSource)
                .createMediaSource(DashManifestParser().parse(Uri.parse(videoSource.url),
                    ByteArrayInputStream(videoSource.manifest?.toByteArray() ?: ByteArray(0))));
            return true;
        }
    }
    @OptIn(UnstableApi::class)
    private fun swapVideoSourceHLS(videoSource: IHLSManifestSource) {
        Logger.i(TAG, "Loading VideoSource [HLS]");
        val dataSource = if(videoSource is JSSource && videoSource.requiresCustomDatasource)
            videoSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);
        _lastVideoMediaSource = HlsMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(videoSource.url));
    }


    //Audio loads
    @OptIn(UnstableApi::class)
    private fun swapAudioSourceLocal(audioSource: LocalAudioSource) {
        Logger.i(TAG, "Loading AudioSource [Local]");
        val file = File(audioSource.filePath);
        if(!file.exists())
            throw IllegalArgumentException("File for this audio does not exist");
        _lastAudioMediaSource = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)));
    }
    @OptIn(UnstableApi::class)
    private fun swapAudioSourceUrlRange(audioSource: JSAudioUrlRangeSource) {
        Logger.i(TAG, "Loading JSAudioUrlRangeSource");
        if(audioSource.hasItag) {
            try {
                _lastAudioMediaSource = VideoHelper.convertItagSourceToChunkedDashSource(audioSource);
                if(_lastAudioMediaSource == null)
                    throw java.lang.IllegalStateException("Missing required parameters for dash workaround?");
                return;
            }
            //If it fails to create the dash workaround, fallback to standard progressive
            catch(ex: Exception) {
                Logger.i(TAG, "Dash manifest workaround failed for audio, falling back to progressive due to ${ex.message}");
                _lastAudioMediaSource = ProgressiveMediaSource.Factory(audioSource.getHttpDataSourceFactory())
                    .createMediaSource(MediaItem.fromUri((audioSource as IAudioUrlSource).getAudioUrl()));
                return;
            }
        }
        else throw IllegalArgumentException("source without itag data...")
    }
    @OptIn(UnstableApi::class)
    private fun swapAudioSourceUrl(audioSource: IAudioUrlSource) {
        Logger.i(TAG, "Loading AudioSource [Url]");
        val dataSource = if(audioSource is JSSource && audioSource.requiresCustomDatasource)
            audioSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);
        _lastAudioMediaSource = ProgressiveMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(audioSource.getAudioUrl()));
    }
    @OptIn(UnstableApi::class)
    private fun swapAudioSourceHLS(audioSource: IHLSManifestAudioSource) {
        Logger.i(TAG, "Loading AudioSource [HLS]");
        val dataSource = if(audioSource is JSSource && audioSource.requiresCustomDatasource)
            audioSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);
        _lastAudioMediaSource = HlsMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(audioSource.url));
    }

    @OptIn(UnstableApi::class)
    private fun swapAudioSourceDashRaw(audioSource: JSDashManifestRawAudioSource, play: Boolean, resume: Boolean): Boolean {
        Logger.i(TAG, "Loading AudioSource [DashRaw]");
        val dataSource = if(audioSource is JSSource && (audioSource.requiresCustomDatasource))
            audioSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT);
        if(audioSource.hasGenerate) {
            findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.launch(Dispatchers.IO) {
                val generated = audioSource.generate();
                if(generated != null) {
                    withContext(Dispatchers.Main) {
                        _lastVideoMediaSource = DashMediaSource.Factory(dataSource)
                            .createMediaSource(DashManifestParser().parse(Uri.parse(audioSource.url),
                                ByteArrayInputStream(generated?.toByteArray() ?: ByteArray(0))));
                        loadSelectedSources(play, resume);
                    }
                }
            }
            return false;
        }
        else {
            _lastVideoMediaSource = DashMediaSource.Factory(dataSource)
                .createMediaSource(
                    DashManifestParser().parse(
                        Uri.parse(audioSource.url),
                        ByteArrayInputStream(audioSource.manifest?.toByteArray() ?: ByteArray(0))
                    )
                );
            return true;
        }
    }

    @OptIn(UnstableApi::class)
    private fun swapAudioSourceUrlWidevine(audioSource: IAudioUrlWidevineSource) {
        Logger.i(TAG, "Loading AudioSource [UrlWidevine]")
        val dataSource = if (audioSource is JSSource && audioSource.requiresCustomDatasource)
            audioSource.getHttpDataSourceFactory()
        else
            DefaultHttpDataSource.Factory().setUserAgent(DEFAULT_USER_AGENT)

        val baseCallback = HttpMediaDrmCallback(audioSource.licenseUri, dataSource)

        val callback = if (audioSource.hasLicenseRequestExecutor) {
            PluginMediaDrmCallback(baseCallback, audioSource.getLicenseRequestExecutor()!!, audioSource.licenseUri)
        } else {
            baseCallback
        }

        _lastAudioMediaSource = ProgressiveMediaSource.Factory(dataSource)
            .setDrmSessionManagerProvider {
                DefaultDrmSessionManager.Builder()
                    .setMultiSession(true)
                    .build(callback)
            }
            .createMediaSource(
                MediaItem.fromUri(audioSource.getAudioUrl())
            )
    }


    //Prefered source selection
    fun getPreferredVideoSource(video: IPlatformVideoDetails, targetPixels: Int = -1): IVideoSource? {
        val usePreview = false;
        if(usePreview) {
            if(video.preview != null && video.preview is VideoMuxedSourceDescriptor)
                return (video.preview as VideoMuxedSourceDescriptor).videoSources.last();
            return null;
        }
        else if(video.live != null)
            return video.live;
        else if(video.dash != null)
            return video.dash;
        else if(video.hls != null)
            return video.hls;
        else
            return VideoHelper.selectBestVideoSource(video.video, targetPixels, PREFERED_VIDEO_CONTAINERS)
    }
    fun getPreferredAudioSource(video: IPlatformVideoDetails, preferredLanguage: String?): IAudioSource? {
        return VideoHelper.selectBestAudioSource(video.video, PREFERED_AUDIO_CONTAINERS, preferredLanguage);
    }

    @OptIn(UnstableApi::class)
    private fun loadSelectedSources(play: Boolean, resume: Boolean): Boolean {
        val sourceVideo = if(!isAudioMode || _lastAudioMediaSource == null) _lastVideoMediaSource else null;
        val sourceAudio = _lastAudioMediaSource;
        val sourceSubs = _lastSubtitleMediaSource;

        updateTrackSelector()

        beforeSourceChanged();

        val source = mergeMediaSources(sourceVideo, sourceAudio, sourceSubs);
        if(source == null)
            return false;
        _mediaSource = source;

        reloadMediaSource(play, resume);
        return true;
    }

    @OptIn(UnstableApi::class)
    fun mergeMediaSources(sourceVideo: MediaSource?, sourceAudio: MediaSource?, sourceSubs: MediaSource?): MediaSource? {
        val sources = listOf(sourceVideo, sourceAudio, sourceSubs).filter { it != null }.map { it!! }.toTypedArray()
        if(sources.size == 1) {
            Logger.i(TAG, "Using single source mode")
            return (sourceVideo ?: sourceAudio);
        }
        else if(sources.size >  1) {
            Logger.i(TAG, "Using multi source mode ${sources.size}")
            return MergingMediaSource(true, *sources);
        }
        else {
            Logger.i(TAG, "Using no sources loaded");
            stop();
            return null;
        }
    }


    @OptIn(UnstableApi::class)
    private fun reloadMediaSource(play: Boolean = false, resume: Boolean = true) {
        val player = exoPlayer ?: return
        val positionBefore = player.player.currentPosition;
        if(_mediaSource != null) {
            player.player.setMediaSource(_mediaSource!!);
            _toResume = resume;
            _didCallSourceChange = false;
            player.player.prepare()
            player.player.playWhenReady = play;
            if(resume)
                seekTo(positionBefore);
            else
                seekTo(0);
            this.onSourceChanged(lastVideoSource, lastAudioSource, resume);
        }
        else
            player.player.stop();
    }

    fun clear() {
        exoPlayer?.player?.stop();
        exoPlayer?.player?.clearMediaItems();
        _lastVideoMediaSource = null;
        _lastAudioMediaSource = null;
        _lastSubtitleMediaSource = null;
        _mediaSource = null;
    }

    fun stop(){
        exoPlayer?.player?.stop();
    }
    fun pause(){
        exoPlayer?.player?.pause();
    }
    open fun play(){
        exoPlayer?.player?.play();
    }

    fun setVolume(volume: Float) {
        exoPlayer?.setVolume(volume);
    }

    protected open fun onSurfaceSizeChanged(width: Int, height: Int) {

    }

    @Suppress("DEPRECATION")
    protected open fun onPlayerError(error: PlaybackException) {
        Logger.i(TAG, "onPlayerError error=$error error.errorCode=${error.errorCode} connectivityLoss");

        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                Logger.w(TAG, "ERROR_CODE_IO_BAD_HTTP_STATUS ${error.cause?.javaClass?.simpleName}");
                if(error.cause is HttpDataSource.InvalidResponseCodeException) {
                    val cause = error.cause as HttpDataSource.InvalidResponseCodeException

                    Logger.w(TAG, null) {
                        "ERROR BAD HTTP ${cause.responseCode},\n" +
                                "Video Source: ${lastVideoSource?.toString()}\n" +
                                "Audio Source: ${lastAudioSource?.toString()}\n" +
                                "Dash: ${_lastGeneratedDash}"
                    };
                }
                onDatasourceError.emit(error);
            }
            //PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            //PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            //PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            //PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            //PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                _shouldPlaybackRestartOnConnectivity = true;
                if (playing) {
                    _connectivityLossTime_ms = System.currentTimeMillis()
                }

                Logger.i(TAG, "IO error, set _shouldPlaybackRestartOnConnectivity=true _connectivityLossTime_ms=$_connectivityLossTime_ms");
            }
        }
    }

    protected open fun onVideoSizeChanged(videoSize: VideoSize) {

    }
    protected open fun beforeSourceChanged() {

    }
    protected open fun onSourceChanged(videoSource: IVideoSource?, audioSource: IAudioSource? = null, resume: Boolean = true) { }

    protected open fun onIsPlayingChanged(playing: Boolean) {

    }
    protected open fun onPlaybackStateChanged(playbackState: Int) {
        if (_shouldPlaybackRestartOnConnectivity && playbackState == ExoPlayer.STATE_READY) {
            Logger.i(TAG, "_shouldPlaybackRestartOnConnectivity=false");
            _shouldPlaybackRestartOnConnectivity = false;
        }
    }

    companion object {
        val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0";

        val PREFERED_VIDEO_CONTAINERS_MP4Pref = arrayOf("video/mp4", "video/webm", "video/3gpp");
        val PREFERED_VIDEO_CONTAINERS_WEBMPref = arrayOf("video/webm", "video/mp4", "video/3gpp");
        val PREFERED_VIDEO_CONTAINERS: Array<String> get() { return if(Settings.instance.playback.preferWebmVideo)
            PREFERED_VIDEO_CONTAINERS_WEBMPref else PREFERED_VIDEO_CONTAINERS_MP4Pref }

        val PREFERED_AUDIO_CONTAINERS_MP4Pref = arrayOf("audio/mp3", "audio/mp4", "audio/webm", "audio/opus");
        val PREFERED_AUDIO_CONTAINERS_WEBMPref = arrayOf("audio/webm", "audio/opus", "audio/mp3", "audio/mp4");
        val PREFERED_AUDIO_CONTAINERS: Array<String> get() { return if(Settings.instance.playback.preferWebmAudio)
            PREFERED_AUDIO_CONTAINERS_WEBMPref else PREFERED_AUDIO_CONTAINERS_MP4Pref }

        val SUPPORTED_SUBTITLES = hashSetOf("text/vtt", "application/x-subrip");
    }
}