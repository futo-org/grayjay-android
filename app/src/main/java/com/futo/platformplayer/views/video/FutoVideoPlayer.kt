package com.futo.platformplayer.views.video

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.chapters.ChapterType
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.formatDuration
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.behavior.GestureControlView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs


class FutoVideoPlayer : FutoVideoPlayerBase {
    companion object {
        private const val TAG = "FutoVideoPlayer"
        private const val PLAYER_STATE_NAME : String = "DetailPlayer";
    }

    var isFullScreen: Boolean = false
            private set;

    //Views
    private val _root: ConstraintLayout;
    private val _videoView: PlayerView;

    val videoControls: PlayerControlView;
    private val _videoControls_fullscreen: PlayerControlView;
    val background: FrameLayout;
    private val _layoutControls: FrameLayout;
    val gestureControl: GestureControlView;

    //Custom buttons
    private val _control_fullscreen: ImageButton;
    private val _control_autoplay: ImageButton;
    private val _control_videosettings: ImageButton;
    private val _control_minimize: ImageButton;
    private val _control_rotate_lock: ImageButton;
    private val _control_loop: ImageButton;
    private val _control_cast: ImageButton;
    private val _control_play: ImageButton;
    private val _control_pause: ImageButton;
    private val _control_chapter: TextView;
    private val _control_time: TextView;
    private val _control_duration: TextView;
    private val _time_bar: TimeBar;
    private val _buttonPrevious: ImageButton;
    private val _buttonNext: ImageButton;

    private val _control_fullscreen_fullscreen: ImageButton;
    private val _control_videosettings_fullscreen: ImageButton;
    private val _control_minimize_fullscreen: ImageButton;
    private val _control_rotate_lock_fullscreen: ImageButton;
    private val _control_autoplay_fullscreen: ImageButton;
    private val _control_loop_fullscreen: ImageButton;
    private val _control_cast_fullscreen: ImageButton;
    private val _control_play_fullscreen: ImageButton;
    private val _time_bar_fullscreen: TimeBar;
    private val _overlay_brightness: FrameLayout;
    private val _control_chapter_fullscreen: TextView;
    private val _buttonPrevious_fullscreen: ImageButton;
    private val _buttonNext_fullscreen: ImageButton;
    private val _control_time_fullscreen: TextView;
    private val _control_duration_fullscreen: TextView;
    private val _control_pause_fullscreen: ImageButton;

    private val _title_fullscreen: TextView;
    private val _author_fullscreen: TextView;
    private var _shouldRestartHideJobOnPlaybackStateChange: Boolean = false;

    private var _lastSourceFit: Float? = null;
    private var _lastWindowWidth: Int = resources.configuration.screenWidthDp
    private var _lastWindowHeight: Int = resources.configuration.screenHeightDp
    private var _originalBottomMargin: Int = 0;

    private var _isControlsLocked: Boolean = false;

    private val _time_bar_listener: TimeBar.OnScrubListener;

    var isFitMode : Boolean = false
        private set;

    private var _isScrubbing = false;
    private val _currentChapterLoopLock = Object();
    private var _currentChapterLoopActive = false;
    private var _currentChapterLoopId: Int = 0;
    private var _currentChapter: IChapter? = null;
    private var _promptedForPermissions: Boolean = false;
    @UnstableApi
    private var _desiredResizeModePortrait: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT

    //Events
    val onMinimize = Event1<FutoVideoPlayer>();
    val onVideoSettings = Event1<FutoVideoPlayer>();
    val onToggleFullScreen = Event1<Boolean>();
    val onSourceChanged = Event3<IVideoSource?, IAudioSource?, Boolean>();
    val onSourceEnded = Event0();
    val onPrevious = Event0();
    val onNext = Event0();

    val onChapterChanged = Event2<IChapter?, Boolean>();

    val onVideoClicked = Event0();
    val onTimeBarChanged = Event2<Long, Long>();

    @OptIn(UnstableApi::class)
    constructor(context: Context, attrs: AttributeSet? = null) : super(PLAYER_STATE_NAME, context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.video_view, this, true);
        _root = findViewById(R.id.videoview_root);
        _videoView = findViewById(R.id.video_player);

        videoControls = findViewById(R.id.video_player_controller);
        _control_fullscreen = videoControls.findViewById(R.id.button_fullscreen);
        _control_autoplay = videoControls.findViewById(R.id.button_autoplay);
        _control_videosettings = videoControls.findViewById(R.id.button_settings);
        _control_minimize = videoControls.findViewById(R.id.button_minimize);
        _control_rotate_lock = videoControls.findViewById(R.id.button_rotate_lock);
        _control_loop = videoControls.findViewById(R.id.button_loop);
        _control_cast = videoControls.findViewById(R.id.button_cast);
        _control_play = videoControls.findViewById(R.id.button_play);
        _control_pause = videoControls.findViewById(R.id.button_pause);
        _time_bar = videoControls.findViewById(R.id.time_progress);
        _control_chapter = videoControls.findViewById(R.id.text_chapter_current);
        _buttonNext = videoControls.findViewById(R.id.button_next);
        _buttonPrevious = videoControls.findViewById(R.id.button_previous);
        _control_time = videoControls.findViewById(R.id.text_position);
        _control_duration = videoControls.findViewById(R.id.text_duration);

        _videoControls_fullscreen = findViewById(R.id.video_player_controller_fullscreen);
        _control_autoplay_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_autoplay);
        _control_fullscreen_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_fullscreen);
        _control_minimize_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_minimize);
        _control_videosettings_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_settings);
        _control_rotate_lock_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_rotate_lock);
        _control_loop_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_loop);
        _control_cast_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_cast);
        _control_play_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_play);
        _control_chapter_fullscreen = _videoControls_fullscreen.findViewById(R.id.text_chapter_current);
        _time_bar_fullscreen = _videoControls_fullscreen.findViewById(R.id.time_progress);
        _buttonPrevious_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_previous);
        _buttonNext_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_next);
        _control_time_fullscreen = _videoControls_fullscreen.findViewById(R.id.text_position);
        _control_duration_fullscreen = _videoControls_fullscreen.findViewById(R.id.text_duration);
        _control_pause_fullscreen = _videoControls_fullscreen.findViewById(R.id.button_pause);

        val castVisibility = if (Settings.instance.casting.enabled) View.VISIBLE else View.GONE
        _control_cast.visibility = castVisibility
        _control_cast_fullscreen.visibility = castVisibility

        _buttonPrevious.setOnClickListener { onPrevious.emit() };
        _buttonNext.setOnClickListener { onNext.emit() };
        _buttonPrevious_fullscreen.setOnClickListener { onPrevious.emit() };
        _buttonNext_fullscreen.setOnClickListener { onNext.emit() };
        _control_play.setOnClickListener {
            exoPlayer?.player?.let {
                if (it.contentPosition >= it.duration) {
                    it.seekTo(0)
                }
                exoPlayer?.player?.play();
            }
            updatePlayPause();
        };
        _control_play_fullscreen.setOnClickListener {
            exoPlayer?.player?.let {
                if (it.contentPosition >= it.duration) {
                    it.seekTo(0)
                }
                exoPlayer?.player?.play();
            }
            updatePlayPause();
        };
        _control_pause.setOnClickListener {
            exoPlayer?.player?.pause();
            updatePlayPause();
        };
        _control_pause_fullscreen.setOnClickListener {
            exoPlayer?.player?.pause();
            updatePlayPause();
        };

        val scrubListener = object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                exoPlayer?.player?.seekTo(position);
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                exoPlayer?.player?.seekTo(position);
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                exoPlayer?.player?.seekTo(position);
            }
        };
        _time_bar.addListener(scrubListener)
        _time_bar_fullscreen.addListener(scrubListener)

        _overlay_brightness = findViewById(R.id.overlay_brightness);

        _title_fullscreen = _videoControls_fullscreen.findViewById(R.id.text_title);
        _author_fullscreen = _videoControls_fullscreen.findViewById(R.id.text_author);

        background = findViewById(R.id.layout_controls_background);
        _layoutControls = findViewById(R.id.layout_controls);
        gestureControl = findViewById(R.id.gesture_control);

        gestureControl.setupTouchArea(_layoutControls, background);
        gestureControl.onSeek.subscribe { seekFromCurrent(it); };
        gestureControl.onSoundAdjusted.subscribe {
            if (Settings.instance.gestureControls.useSystemVolume) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
            } else {
                setVolume(it)
            }
        };
        gestureControl.onToggleFullscreen.subscribe { setFullScreen(!isFullScreen) };
        gestureControl.onBrightnessAdjusted.subscribe {
            if (Settings.instance.gestureControls.useSystemBrightness) {
                setSystemBrightness(it)
            } else {
                if (it == 1.0f) {
                    _overlay_brightness.visibility = View.GONE;
                } else {
                    _overlay_brightness.visibility = View.VISIBLE;
                    _overlay_brightness.setBackgroundColor(Color.valueOf(0.0f, 0.0f, 0.0f, (1.0f - it)).toArgb());
                }
            }
        };
        gestureControl.onPan.subscribe { x, y ->
            _videoView.translationX = x
            _videoView.translationY = y
        }
        gestureControl.onZoom.subscribe {
            _videoView.scaleX = it
            _videoView.scaleY = it
        }

        gestureControl.setZoomPanEnabled(_videoView.videoSurfaceView!!)

        if(!isInEditMode) {
            _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            val player = StatePlayer.instance.getPlayerOrCreate(context);
            changePlayer(player);

            videoControls.player = player.player;
            _videoControls_fullscreen.player = player.player;
        }

        val attrShowSettings = if(attrs != null) {
            val attrArr = context.obtainStyledAttributes(attrs, R.styleable.FutoVideoPlayer, 0, 0)
            val result = attrArr.getBoolean(R.styleable.FutoVideoPlayer_showSettings, false)
            attrArr.recycle()
            result
        } else false;

        val attrShowFullScreen = if(attrs != null) {
            val attrArr = context.obtainStyledAttributes(attrs, R.styleable.FutoVideoPlayer, 0, 0)
            val result = attrArr.getBoolean(R.styleable.FutoVideoPlayer_showFullScreen, false)
            attrArr.recycle()
            result
        } else false;

        val attrShowMinimize = if(attrs != null) {
            val attrArr = context.obtainStyledAttributes(attrs, R.styleable.FutoVideoPlayer, 0, 0)
            val result = attrArr.getBoolean(R.styleable.FutoVideoPlayer_showMinimize, false)
            attrArr.recycle()
            result
        } else false;

        if (!attrShowSettings)
            _control_videosettings.visibility = View.GONE;
        if (!attrShowFullScreen)
            _control_fullscreen.visibility = View.GONE;
        if (!attrShowMinimize)
            _control_minimize.visibility = View.GONE;

        var lastScrubPosition = 0L;
        _time_bar_listener = object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                _isScrubbing = true;
                Logger.i(TAG, "Scrubbing started");
                gestureControl.restartHideJob();
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                gestureControl.restartHideJob();

                val playerPosition = position;
                val scrubDelta = abs(lastScrubPosition - position);
                lastScrubPosition = position;

                if(scrubDelta > 1000 || Math.abs(position - playerPosition) > 500)
                    _currentChapterUpdateExecuter.execute {
                        updateCurrentChapter(position);
                    }
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                _isScrubbing = false;
                Logger.i(TAG, "Scrubbing stopped");
                gestureControl.restartHideJob();
            }
        };

        _time_bar.addListener(_time_bar_listener);
        _time_bar_fullscreen.addListener(_time_bar_listener);

        _control_fullscreen.setOnClickListener {
            setFullScreen(true);
        }
        _control_videosettings.setOnClickListener {
            onVideoSettings.emit(this);
        }
        _control_minimize.setOnClickListener {
            onMinimize.emit(this);
        };
        _control_rotate_lock.setOnClickListener {
            StatePlayer.instance.rotationLock = !StatePlayer.instance.rotationLock;
            updateRotateLock();
        };
        _control_cast.setOnClickListener {
            UIDialogs.showCastingDialog(context);
        };


        _control_loop.setOnClickListener {
            StatePlayer.instance.loopVideo = !StatePlayer.instance.loopVideo;
            updateLoopVideoUI();
        }
        _control_loop_fullscreen.setOnClickListener {
            StatePlayer.instance.loopVideo = !StatePlayer.instance.loopVideo;
            updateLoopVideoUI();
        }

        _control_minimize_fullscreen.setOnClickListener {
            onMinimize.emit(this);
        };
        _control_fullscreen_fullscreen.setOnClickListener {
            setFullScreen(false);
        }
        _control_videosettings_fullscreen.setOnClickListener {
            onVideoSettings.emit(this);
        }
        _control_rotate_lock_fullscreen.setOnClickListener {
            StatePlayer.instance.rotationLock = !StatePlayer.instance.rotationLock;
            updateRotateLock();
        };
        _control_cast_fullscreen.setOnClickListener {
            UIDialogs.showCastingDialog(context);
        };

        _control_autoplay.setOnClickListener {
            StatePlayer.instance.autoplay = !StatePlayer.instance.autoplay;
            updateAutoplayButton()
        }
        updateAutoplayButton()

        _control_autoplay_fullscreen.setOnClickListener {
            StatePlayer.instance.autoplay = !StatePlayer.instance.autoplay;
            updateAutoplayButton()
        }
        updateAutoplayButton()

        val progressUpdateListener = { position: Long, bufferedPosition: Long ->
            val currentTime = position.formatDuration()
            val currentDuration = duration.formatDuration()
            _control_time.text = currentTime;
            _control_time_fullscreen.text = currentTime;
            _control_duration.text = currentDuration;
            _control_duration_fullscreen.text = currentDuration;
            _time_bar_fullscreen.setDuration(duration);
            _time_bar.setDuration(duration);
            _time_bar_fullscreen.setPosition(position);
            _time_bar.setPosition(position);
            _time_bar_fullscreen.setBufferedPosition(bufferedPosition);
            _time_bar.setBufferedPosition(bufferedPosition);

            onTimeBarChanged.emit(position, bufferedPosition);

            if(!_currentChapterLoopActive)
                synchronized(_currentChapterLoopLock) {
                    if(!_currentChapterLoopActive) {
                        _currentChapterLoopActive = true;
                        updateChaptersLoop(++_currentChapterLoopId);
                    }
                }
        };

        _videoControls_fullscreen.setProgressUpdateListener(progressUpdateListener);
        videoControls.setProgressUpdateListener(progressUpdateListener);

        StatePlayer.instance.onQueueChanged.subscribe(this) {
            CoroutineScope(Dispatchers.Main).launch(Dispatchers.Main) {
                setLoopVisible(!StatePlayer.instance.hasQueue)
                updateNextPrevious();
            }
        }
        StatePlayer.instance.onVideoChanging.subscribe(this) {
            CoroutineScope(Dispatchers.Main).launch(Dispatchers.Main) {
                updateNextPrevious();
            }
        }

        updateLoopVideoUI();

        if(!isInEditMode) {
            gestureControl.hideControls();
        }
    }

    private fun updateAutoplayButton() {
        _control_autoplay.setColorFilter(ContextCompat.getColor(context, if (StatePlayer.instance.autoplay) com.futo.futopay.R.color.primary else R.color.white))
        _control_autoplay_fullscreen.setColorFilter(ContextCompat.getColor(context, if (StatePlayer.instance.autoplay) com.futo.futopay.R.color.primary else R.color.white))
    }

    private fun setSystemBrightness(brightness: Float) {
        Log.i(TAG, "setSystemBrightness $brightness")
        if (android.provider.Settings.System.canWrite(context)) {
            Log.i(TAG, "setSystemBrightness canWrite $brightness")
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, (brightness * 255.0f).toInt().coerceAtLeast(1).coerceAtMost(255));
        } else if (!_promptedForPermissions) {
            Log.i(TAG, "setSystemBrightness prompt $brightness")
            _promptedForPermissions = true
            UIDialogs.showConfirmationDialog(context, "System brightness controls require explicit permission", action = {
                openAndroidPermissionsMenu()
            })
        } else {
            Log.i(TAG, "setSystemBrightness no permission?")
            //No permissions but already prompted, ignore
        }
    }

    private fun openAndroidPermissionsMenu() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.setData(Uri.parse("package:" + context.packageName))
        context.startActivity(intent)
    }

    fun updateNextPrevious() {
        val vidPrev = StatePlayer.instance.getPrevQueueItem(true);
        val vidNext = StatePlayer.instance.getNextQueueItem(true);
        _buttonNext.visibility = if (vidNext != null) View.VISIBLE else View.GONE
        _buttonNext_fullscreen.visibility = if (vidNext != null) View.VISIBLE else View.GONE
        _buttonPrevious.visibility = if (vidPrev != null) View.VISIBLE else View.GONE
        _buttonPrevious_fullscreen.visibility = if (vidPrev != null) View.VISIBLE else View.GONE
    }

    private val _currentChapterUpdateInterval: Long = 1000L / Settings.instance.playback.getChapterUpdateFrames();
    private var _currentChapterUpdateLastPos = 0L;
    private val _currentChapterUpdateExecuter = Executors.newSingleThreadScheduledExecutor();
    private fun updateChaptersLoop(loopId: Int) {
        if(_currentChapterLoopId == loopId) {
            _currentChapterLoopActive = true;
            _currentChapterUpdateExecuter.schedule({
                try {
                    if(!_isScrubbing) {
                        var pos: Long =  runBlocking(Dispatchers.Main) { position; };
                        val delta = pos - _currentChapterUpdateLastPos;
                        if(delta > _currentChapterUpdateInterval || delta < 0) {
                            _currentChapterUpdateLastPos = pos;
                            if(updateCurrentChapter(pos))
                                Logger.i(TAG, "Updated chapter to [${_currentChapter?.name}] with speed ${delta}ms (${pos - (_currentChapter?.timeStart?.times(1000)?.toLong() ?: 0)}ms late [${_currentChapter?.timeStart}s])");
                        }
                    }
                    if(playing)
                        updateChaptersLoop(loopId);
                    else
                        _currentChapterLoopActive = false;
                }
                catch(ex: Throwable) {
                    _currentChapterLoopActive = false;
                }
            }, _currentChapterUpdateInterval, TimeUnit.MILLISECONDS);
        }
        else
            _currentChapterLoopActive = false;
    }

    fun setLoopVisible(visible: Boolean) {
        _control_loop.visibility = if (visible) View.VISIBLE else View.GONE;
        _control_loop_fullscreen.visibility = if (visible) View.VISIBLE else View.GONE;
    }

    fun stopAllGestures() {
        gestureControl.stopAllGestures();
    }

    fun attachPlayer() {
        exoPlayer?.attach(_videoView, PLAYER_STATE_NAME);
    }

    fun updateCurrentChapter(chaptPos: Long, isScrub: Boolean = false): Boolean {
        val currentChapter = getCurrentChapter(chaptPos);
        if(_currentChapter != currentChapter) {
            _currentChapter = currentChapter;

            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                if (currentChapter != null) {
                    _control_chapter.text = " • " + currentChapter.name;
                    _control_chapter_fullscreen.text = " • " + currentChapter.name;
                } else {
                    _control_chapter.text = "";
                    _control_chapter_fullscreen.text = "";
                }
                onChapterChanged.emit(currentChapter, isScrub);
                val chapt = _currentChapter;

                if(chapt?.type == ChapterType.SKIPONCE)
                    ignoreChapter(chapt);
            }
            return true;
        }
        return false;
    }

    @OptIn(UnstableApi::class)
    fun setArtwork(drawable: Drawable?) {
        if (drawable != null) {
            _videoView.defaultArtwork = drawable;
            _videoView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FILL;
            fitOrFill(isFullScreen);
        } else {
            _videoView.defaultArtwork = null;
            _videoView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF;
        }
    }

    fun hideControls(animated: Boolean) {
        gestureControl.hideControls(animated);
    }

    fun setMetadata(title: String, author: String) {
        _title_fullscreen.text = title;
        _author_fullscreen.text = author;
    }

    fun setPlaybackRate(playbackRate: Float) {
        val exoPlayer = exoPlayer?.player;
        Logger.i(TAG, "setPlaybackRate playbackRate=$playbackRate exoPlayer=${exoPlayer}");

        val param = PlaybackParameters(playbackRate);
        exoPlayer?.playbackParameters = param;
    }

    fun getPlaybackRate(): Float {
        return exoPlayer?.player?.playbackParameters?.speed ?: 1.0f;
    }

    @OptIn(UnstableApi::class)
    fun setFullScreen(fullScreen: Boolean) {
        updateRotateLock()

        if (isFullScreen == fullScreen) {
            return;
        }

        if (fullScreen) {
            val lp = background.layoutParams as ConstraintLayout.LayoutParams;
            lp.bottomMargin = 0;
            background.layoutParams = lp;
            _videoView.setBackgroundColor(Color.parseColor("#FF000000"))

            gestureControl.hideControls();
            //videoControlsBar.visibility = View.GONE;
            _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

            _videoControls_fullscreen.show();
            videoControls.hideImmediately();
            videoControls.visibility = View.GONE;
        }
        else {
            val lp = background.layoutParams as ConstraintLayout.LayoutParams;
            lp.bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6.0f, Resources.getSystem().displayMetrics).toInt();
            background.layoutParams = lp;
            _videoView.setBackgroundColor(Color.parseColor("#00000000"))

            gestureControl.hideControls();
            //videoControlsBar.visibility = View.VISIBLE;
            _videoView.resizeMode = _desiredResizeModePortrait;

            videoControls.show();
            _videoControls_fullscreen.hideImmediately();
            _videoControls_fullscreen.visibility = View.GONE;
        }

        fitOrFill(fullScreen);
        gestureControl.setFullscreen(fullScreen);
        onToggleFullScreen.emit(fullScreen);
        isFullScreen = fullScreen;
    }

    private fun fitOrFill(fullScreen: Boolean) {
        if (fullScreen) {
            fillHeight(false);
        } else {
            fitHeight();
        }
    }

    fun lockControlsAlpha(locked : Boolean) {
        if(locked && _isControlsLocked != locked) {
            _isControlsLocked = locked;
            _layoutControls.visibility = View.GONE;
        }
        else if(!locked && _isControlsLocked != locked)
            _isControlsLocked = locked;
    }

    override fun play() {
        super.play();
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        gestureControl.resetZoomPan()
        _lastSourceFit = null;
        if(isFullScreen)
            fillHeight(false);
        else if(_root.layoutParams.height != MATCH_PARENT)
            fitHeight(videoSize);
    }

    override fun beforeSourceChanged() {
        super.beforeSourceChanged();
        attachPlayer();
    }
    override fun onSourceChanged(videoSource: IVideoSource?, audioSource: IAudioSource?, resume: Boolean) {
        onSourceChanged.emit(videoSource, audioSource, resume);
    }

    private fun updatePlayPause() {
        if (exoPlayer?.player?.isPlaying == true) {
            _control_pause.visibility = View.VISIBLE
            _control_play.visibility = View.GONE
            _control_pause_fullscreen.visibility = View.VISIBLE
            _control_play_fullscreen.visibility = View.GONE
        } else {
            _control_pause.visibility = View.GONE
            _control_play.visibility = View.VISIBLE
            _control_pause_fullscreen.visibility = View.GONE
            _control_play_fullscreen.visibility = View.VISIBLE
        }
    }

    override fun onIsPlayingChanged(playing: Boolean) {
        super.onIsPlayingChanged(playing)
        updatePlayPause();
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        Logger.v(TAG, "onPlaybackStateChanged $playbackState");
        updatePlayPause()

        if (playbackState == ExoPlayer.STATE_ENDED) {
            if (abs(position - duration) < 2000) {
                onSourceEnded.emit();
            }

            _shouldRestartHideJobOnPlaybackStateChange = true;
        } else {
            setIsReplay(false);

            if (_shouldRestartHideJobOnPlaybackStateChange) {
                gestureControl.restartHideJob();
                _shouldRestartHideJobOnPlaybackStateChange = false;
            }
        }
    }

    fun setIsReplay(isReplay: Boolean) {
        if (isReplay) {
            _control_play.setImageResource(R.drawable.ic_replay);
            _control_play_fullscreen.setImageResource(R.drawable.ic_replay);
        } else {
            _control_play.setImageResource(R.drawable.ic_play_white_nopad);
            _control_play_fullscreen.setImageResource(R.drawable.ic_play_white_nopad);
        }
    }

    //Sizing
    @OptIn(UnstableApi::class)
    fun fitHeight(videoSize: VideoSize? = null) {
        Logger.i(TAG, "Video Fit Height")
        if (_originalBottomMargin != 0) {
            val layoutParams = _videoView.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.setMargins(0, 0, 0, _originalBottomMargin)
            _videoView.layoutParams = layoutParams
        }

        var h = videoSize?.height ?: lastVideoSource?.height ?: exoPlayer?.player?.videoSize?.height
        ?: 0
        var w =
            videoSize?.width ?: lastVideoSource?.width ?: exoPlayer?.player?.videoSize?.width ?: 0

        if (h == 0 && w == 0) {
            Logger.i(
                TAG,
                "UNKNOWN VIDEO FIT: (videoSize: ${videoSize != null}, player.videoSize: ${exoPlayer?.player?.videoSize != null})"
            );
            w = 1280
            h = 720
        }

        val configuration = resources.configuration

        val windowWidth = configuration.screenWidthDp
        val windowHeight = configuration.screenHeightDp

        if (_lastSourceFit == null || windowWidth != _lastWindowWidth || windowHeight != _lastWindowHeight) {
            val maxHeight = windowHeight * 0.4f
            val minHeight = windowHeight * 0.1f

            val determinedHeight = windowWidth / w.toFloat() * h.toFloat()

            _lastSourceFit = determinedHeight
            _lastSourceFit = _lastSourceFit!!.coerceAtLeast(minHeight)
            _lastSourceFit = _lastSourceFit!!.coerceAtMost(maxHeight)

            _desiredResizeModePortrait = if (_lastSourceFit != determinedHeight)
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            else
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM

            _lastWindowWidth = windowWidth
            _lastWindowHeight = windowHeight
        }
        _videoView.resizeMode = _desiredResizeModePortrait

        val marginBottom =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7f, resources.displayMetrics)
        val height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            _lastSourceFit!!,
            resources.displayMetrics
        )
        val rootParams = LayoutParams(LayoutParams.MATCH_PARENT, (height + marginBottom).toInt())
        rootParams.bottomMargin = marginBottom.toInt()
        _root.layoutParams = rootParams
        isFitMode = true
    }

    @OptIn(UnstableApi::class)
    fun fillHeight(isMiniPlayer: Boolean) {
        Logger.i(TAG, "Video Fill Height");
        val layoutParams = _videoView.layoutParams as ConstraintLayout.LayoutParams;
        _originalBottomMargin =
            if (layoutParams.bottomMargin > 0) layoutParams.bottomMargin else _originalBottomMargin;
        layoutParams.setMargins(0);
        _videoView.layoutParams = layoutParams;
        _videoView.invalidate();

        val rootParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        _root.layoutParams = rootParams;
        _root.invalidate();

        if(isMiniPlayer){
            _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        isFitMode = false;
    }

    //Animated Calls
    fun setEndPadding(value: Float) {
        setPadding(0, 0, value.toInt(), 0)
    }

    fun updateRotateLock() {
        _control_rotate_lock.visibility = View.VISIBLE;
        _control_rotate_lock_fullscreen.visibility = View.VISIBLE;

        if(StatePlayer.instance.rotationLock) {
            _control_rotate_lock_fullscreen.setImageResource(R.drawable.ic_screen_lock_rotation_active);
            _control_rotate_lock.setImageResource(R.drawable.ic_screen_lock_rotation_active);
        }
        else {
            _control_rotate_lock_fullscreen.setImageResource(R.drawable.ic_screen_lock_rotation);
            _control_rotate_lock.setImageResource(R.drawable.ic_screen_lock_rotation);
        }
    }
    fun updateLoopVideoUI() {
        if(StatePlayer.instance.loopVideo) {
            _control_loop.setImageResource(R.drawable.ic_loop_active);
            _control_loop_fullscreen.setImageResource(R.drawable.ic_loop_active);
        }
        else {
            _control_loop.setImageResource(R.drawable.ic_loop);
            _control_loop_fullscreen.setImageResource(R.drawable.ic_loop);
        }
    }

    fun setGestureSoundFactor(soundFactor: Float) {
        gestureControl.setSoundFactor(soundFactor);
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        gestureControl.resetZoomPan()
    }
}