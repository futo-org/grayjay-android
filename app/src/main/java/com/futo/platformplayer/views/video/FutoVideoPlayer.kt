package com.futo.platformplayer.views.video

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setMargins
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.behavior.GestureControlView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.ui.TimeBar
import com.google.android.exoplayer2.video.VideoSize
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
    private val _videoView: StyledPlayerView;

    val videoControls: PlayerControlView;
    private val _videoControls_fullscreen: PlayerControlView;
    val background: FrameLayout;
    private val _layoutControls: FrameLayout;
    val gestureControl: GestureControlView;

    //Custom buttons
    private val _control_fullscreen: ImageButton;
    private val _control_videosettings: ImageButton;
    private val _control_minimize: ImageButton;
    private val _control_rotate_lock: ImageButton;
    private val _control_cast: ImageButton;
    private val _control_play: ImageButton;
    private val _control_chapter: TextView;
    private val _time_bar: TimeBar;

    private val _control_fullscreen_fullscreen: ImageButton;
    private val _control_videosettings_fullscreen: ImageButton;
    private val _control_minimize_fullscreen: ImageButton;
    private val _control_rotate_lock_fullscreen: ImageButton;
    private val _control_cast_fullscreen: ImageButton;
    private val _control_play_fullscreen: ImageButton;
    private val _time_bar_fullscreen: TimeBar;
    private val _overlay_brightness: FrameLayout;
    private val _control_chapter_fullscreen: TextView;

    private val _title_fullscreen: TextView;
    private val _author_fullscreen: TextView;
    private var _shouldRestartHideJobOnPlaybackStateChange: Boolean = false;

    private var _lastSourceFit: Int? = null;
    private var _originalBottomMargin: Int = 0;

    private var _isControlsLocked: Boolean = false;

    private val _time_bar_listener: TimeBar.OnScrubListener;

    var isFitMode : Boolean = false
        private set;

    private var _currentChapter: IChapter? = null;


    //Events
    val onMinimize = Event1<FutoVideoPlayer>();
    val onVideoSettings = Event1<FutoVideoPlayer>();
    val onToggleFullScreen = Event1<Boolean>();
    val onSourceChanged = Event3<IVideoSource?, IAudioSource?, Boolean>();
    val onSourceEnded = Event0();

    val onChapterChanged = Event2<IChapter?, Boolean>();

    val onVideoClicked = Event0();
    val onTimeBarChanged = Event2<Long, Long>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(PLAYER_STATE_NAME, context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.video_view, this, true);
        _root = findViewById(R.id.videoview_root);
        _videoView = findViewById(R.id.video_player);

        val subs = _videoView.subtitleView;

        videoControls = findViewById(R.id.video_player_controller);
        _control_fullscreen = videoControls.findViewById(R.id.exo_fullscreen);
        _control_videosettings = videoControls.findViewById(R.id.exo_settings);
        _control_minimize = videoControls.findViewById(R.id.exo_minimize);
        _control_rotate_lock = videoControls.findViewById(R.id.exo_rotate_lock);
        _control_cast = videoControls.findViewById(R.id.exo_cast);
        _control_play = videoControls.findViewById(com.google.android.exoplayer2.ui.R.id.exo_play);
        _time_bar = videoControls.findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress);
        _control_chapter = videoControls.findViewById(R.id.text_chapter_current);

        _videoControls_fullscreen = findViewById(R.id.video_player_controller_fullscreen);
        _control_fullscreen_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_fullscreen);
        _control_minimize_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_minimize);
        _control_videosettings_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_settings);
        _control_rotate_lock_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_rotate_lock);
        _control_cast_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_cast);
        _control_play_fullscreen = videoControls.findViewById(com.google.android.exoplayer2.ui.R.id.exo_play);
        _control_chapter_fullscreen = _videoControls_fullscreen.findViewById(R.id.text_chapter_current);
        _time_bar_fullscreen = _videoControls_fullscreen.findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress);

        val castVisibility = if (Settings.instance.casting.enabled) View.VISIBLE else View.GONE
        _control_cast.visibility = castVisibility
        _control_cast_fullscreen.visibility = castVisibility

        _overlay_brightness = findViewById(R.id.overlay_brightness);

        _title_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_title);
        _author_fullscreen = _videoControls_fullscreen.findViewById(R.id.exo_author);

        background = findViewById(R.id.layout_controls_background);
        _layoutControls = findViewById(R.id.layout_controls);
        gestureControl = findViewById(R.id.gesture_control);

        _videoView?.videoSurfaceView?.let { gestureControl.setupTouchArea(it, _layoutControls, background); };
        gestureControl.onSeek.subscribe { seekFromCurrent(it); };
        gestureControl.onSoundAdjusted.subscribe { setVolume(it) };
        gestureControl.onToggleFullscreen.subscribe { setFullScreen(!isFullScreen) };
        gestureControl.onBrightnessAdjusted.subscribe {
            if (it == 1.0f) {
                _overlay_brightness.visibility = View.GONE;
            } else {
                _overlay_brightness.visibility = View.VISIBLE;
                _overlay_brightness.setBackgroundColor(Color.valueOf(0.0f, 0.0f, 0.0f, (1.0f - it)).toArgb());
            }
        };

        if(!isInEditMode) {
            _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            val player = StatePlayer.instance.getPlayerOrCreate(context);
            //player.modifyState(PLAYER_STATE_NAME, { it.scaleType = MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING})
            changePlayer(player);

            videoControls.player = player.player;
            _videoControls_fullscreen.player = player.player;
        }

        val attrShowSettings = if(attrs != null)
            context.obtainStyledAttributes(attrs, R.styleable.FutoVideoPlayer, 0, 0).getBoolean(R.styleable.FutoVideoPlayer_showSettings, false) ?: false;
        else false;
        val attrShowFullScreen = if(attrs != null)
            context.obtainStyledAttributes(attrs, R.styleable.FutoVideoPlayer, 0, 0).getBoolean(R.styleable.FutoVideoPlayer_showFullScreen, false) ?: false;
        else false;
        val attrShowMinimize = if(attrs != null)
            context.obtainStyledAttributes(attrs, R.styleable.FutoVideoPlayer, 0, 0).getBoolean(R.styleable.FutoVideoPlayer_showMinimize, false) ?: false;
        else false;

        if (!attrShowSettings)
            _control_videosettings.visibility = View.GONE;
        if (!attrShowFullScreen)
            _control_fullscreen.visibility = View.GONE;
        if (!attrShowMinimize)
            _control_minimize.visibility = View.GONE;

        _time_bar_listener = object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                gestureControl.restartHideJob();
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                gestureControl.restartHideJob();

                updateCurrentChapter(position);
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
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

        var lastPos = 0L;
        videoControls.setProgressUpdateListener { position, bufferedPosition ->
            onTimeBarChanged.emit(position, bufferedPosition);

            val delta = position - lastPos;
            if(delta > 1000 || delta < 0) {
                lastPos = position;
                updateCurrentChapter();
            }
        }

        if(!isInEditMode) {
            gestureControl.hideControls();
        }
    }

    fun attachPlayer() {
        exoPlayer?.attach(_videoView, PLAYER_STATE_NAME);
    }

    fun updateCurrentChapter(pos: Long? = null) {
        val chaptPos = pos ?: position;
        val currentChapter = getCurrentChapter(chaptPos);
        if(_currentChapter != currentChapter) {
            _currentChapter = currentChapter;
            if (currentChapter != null) {
                _control_chapter.text = " • " + currentChapter.name;
                _control_chapter_fullscreen.text = " • " + currentChapter.name;
            } else {
                _control_chapter.text = "";
                _control_chapter_fullscreen.text = "";
            }
            onChapterChanged.emit(currentChapter, pos != null);
        }
    }

    fun setArtwork(drawable: Drawable?) {
        if (drawable != null) {
            _videoView.defaultArtwork = drawable;
            _videoView.useArtwork = true;
            fitOrFill(isFullScreen);
        } else {
            _videoView.defaultArtwork = null;
            _videoView.useArtwork = false;
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

    fun setFullScreen(fullScreen: Boolean) {
        if (isFullScreen == fullScreen) {
            return;
        }

        if (fullScreen) {
            val lp = background.layoutParams as ConstraintLayout.LayoutParams;
            lp.bottomMargin = 0;
            background.layoutParams = lp;

            gestureControl.hideControls();
            //videoControlsBar.visibility = View.GONE;
            _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

            _videoControls_fullscreen.show();
            videoControls.hide();
        }
        else {
            val lp = background.layoutParams as ConstraintLayout.LayoutParams;
            lp.bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6.0f, Resources.getSystem().displayMetrics).toInt();
            background.layoutParams = lp;

            gestureControl.hideControls();
            //videoControlsBar.visibility = View.VISIBLE;
            _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

            videoControls.show();
            _videoControls_fullscreen.hide();
        }

        fitOrFill(fullScreen);
        gestureControl.setFullscreen(fullScreen);
        onToggleFullScreen.emit(fullScreen);
        isFullScreen = fullScreen;
    }

    private fun fitOrFill(fullScreen: Boolean) {
        if (fullScreen) {
            fillHeight();
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
        _lastSourceFit = null;
        if(isFullScreen)
            fillHeight();
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

    override fun onPlaybackStateChanged(playbackState: Int) {
        Logger.v(TAG, "onPlaybackStateChanged $playbackState");
        val timeLeft = abs(position - duration);

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
    fun fitHeight(videoSize : VideoSize? = null){
        Logger.i(TAG, "Video Fit Height");
        if(_originalBottomMargin != 0) {
            val layoutParams = _videoView.layoutParams as ConstraintLayout.LayoutParams;
            layoutParams.setMargins(0, 0, 0, _originalBottomMargin);
            _videoView.layoutParams = layoutParams;
        }

        var h = videoSize?.height ?: lastVideoSource?.height ?: exoPlayer?.player?.videoSize?.height ?: 0;
        var w = videoSize?.width ?: lastVideoSource?.width ?: exoPlayer?.player?.videoSize?.width ?: 0;

        if(h == 0 && w == 0) {
            Logger.i(TAG, "UNKNOWN VIDEO FIT: (videoSize: ${videoSize != null}, player.videoSize: ${exoPlayer?.player?.videoSize != null})");
            w = 1280;
            h = 720;
        }


        if(_lastSourceFit == null){
            val metrics = StateApp.instance.displayMetrics ?: resources.displayMetrics;

            val viewWidth = Math.min(metrics.widthPixels, metrics.heightPixels); //TODO: Get parent width. was this.width
            val deviceHeight = Math.max(metrics.widthPixels, metrics.heightPixels);
            val maxHeight = deviceHeight * 0.6;

            val determinedHeight = if(w > h)
                ((h * (viewWidth.toDouble() / w)).toInt().toInt())
            else
                ((h * (viewWidth.toDouble() / w)).toInt().toInt());
            _lastSourceFit = determinedHeight;
            _lastSourceFit = Math.max(_lastSourceFit!!, 250);
            _lastSourceFit = Math.min(_lastSourceFit!!, maxHeight.toInt());
            if((_lastSourceFit ?: 0) < 300 || (_lastSourceFit ?: 0) > viewWidth) {
                Log.d(TAG, "WEIRD HEIGHT DETECTED: ${_lastSourceFit}, Width: ${w}, Height: ${h}, VWidth: ${viewWidth}");
            }
            if(_lastSourceFit != determinedHeight)
                _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
            else
                _videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
        }

        val marginBottom = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7f, resources.displayMetrics).toInt();
        val rootParams = RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, _lastSourceFit!! + marginBottom)
        rootParams.bottomMargin = marginBottom;
        _root.layoutParams = rootParams
        isFitMode = true;
    }
    fun fillHeight(){
        Logger.i(TAG, "Video Fill Height");
        val width = resources.displayMetrics.heightPixels;
        val height = resources.displayMetrics.widthPixels;

        val layoutParams = _videoView.layoutParams as ConstraintLayout.LayoutParams;
        _originalBottomMargin = if(layoutParams.bottomMargin > 0) layoutParams.bottomMargin else _originalBottomMargin;
        layoutParams.setMargins(0);
        _videoView.layoutParams = layoutParams;
        _videoView.invalidate();

        val rootParams = RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        _root.layoutParams = rootParams;
        _root.invalidate();
        isFitMode = false;
    }

    //Animated Calls
    fun setEndPadding(value: Float) {
        setPadding(0, 0, value.toInt(), 0)
    }

    fun updateRotateLock() {
        if(!Settings.instance.playback.isAutoRotate()) {
            _control_rotate_lock.visibility = View.GONE;
            _control_rotate_lock_fullscreen.visibility = View.GONE;
        }
        else {
            _control_rotate_lock.visibility = View.VISIBLE;
            _control_rotate_lock_fullscreen.visibility = View.VISIBLE;
        }
        if(StatePlayer.instance.rotationLock) {
            _control_rotate_lock_fullscreen.setImageResource(R.drawable.ic_screen_rotation);
            _control_rotate_lock.setImageResource(R.drawable.ic_screen_rotation);
        }
        else {
            _control_rotate_lock_fullscreen.setImageResource(R.drawable.ic_screen_lock_rotation);
            _control_rotate_lock.setImageResource(R.drawable.ic_screen_lock_rotation);
        }
    }

    fun setGestureSoundFactor(soundFactor: Float) {
        gestureControl.setSoundFactor(soundFactor);
    }
}