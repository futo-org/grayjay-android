package com.futo.platformplayer.views.casting

import android.content.Context
import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.casting.AirPlayCastingDevice
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.behavior.GestureControlView
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.TimeBar
import com.google.android.exoplayer2.ui.TimeBar.OnScrubListener
import kotlinx.coroutines.*

class CastView : ConstraintLayout {
    private val _thumbnail: ImageView;
    private val _buttonMinimize: ImageButton;
    private val _buttonSettings: ImageButton;
    private val _buttonLoop: ImageButton;
    private val _buttonPlay: ImageButton;
    private val _buttonPause: ImageButton;
    private val _buttonCast: CastButton;
    private val _textPosition: TextView;
    private val _textDuration: TextView;
    private val _textDivider: TextView;
    private val _timeBar: DefaultTimeBar;
    private val _background: FrameLayout;
    private val _gestureControlView: GestureControlView;
    private var _scope: CoroutineScope = CoroutineScope(Dispatchers.Main);
    private var _updateTimeJob: Job? = null;
    private var _inPictureInPicture: Boolean = false;
    private var _originalBottomMargin: Int = 0;

    val onMinimizeClick = Event0();
    val onSettingsClick = Event0();

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_cast, this, true);

        _thumbnail = findViewById(R.id.image_thumbnail);
        _buttonMinimize = findViewById(R.id.button_minimize);
        _buttonSettings = findViewById(R.id.button_settings);
        _buttonLoop = findViewById(R.id.button_loop);
        _buttonPlay = findViewById(R.id.button_play);
        _buttonPause = findViewById(R.id.button_pause);
        _buttonCast = findViewById(R.id.button_cast);
        _textPosition = findViewById(R.id.text_position);
        _textDivider = findViewById(R.id.text_divider);
        _textDuration = findViewById(R.id.text_duration);
        _timeBar = findViewById(R.id.time_progress);
        _background = findViewById(R.id.layout_background);
        _gestureControlView = findViewById(R.id.gesture_control);
        _gestureControlView.fullScreenGestureEnabled = false
        _gestureControlView.setupTouchArea();
        _gestureControlView.onSeek.subscribe {
            val d = StateCasting.instance.activeDevice ?: return@subscribe;
            StateCasting.instance.videoSeekTo(d.expectedCurrentTime + it / 1000);
        };

        _buttonLoop.setOnClickListener {
            StatePlayer.instance.loopVideo = !StatePlayer.instance.loopVideo;
            _buttonLoop.setImageResource(if(StatePlayer.instance.loopVideo) R.drawable.ic_loop_active else R.drawable.ic_loop);
        }
        _buttonLoop.setImageResource(if(StatePlayer.instance.loopVideo) R.drawable.ic_loop_active else R.drawable.ic_loop);

        _timeBar.addListener(object : OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                StateCasting.instance.videoSeekTo(position.toDouble());
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                StateCasting.instance.videoSeekTo(position.toDouble());
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                StateCasting.instance.videoSeekTo(position.toDouble());
            }
        });

        _buttonMinimize.setOnClickListener { onMinimizeClick.emit(); };
        _buttonSettings.setOnClickListener { onSettingsClick.emit(); };
        _buttonPlay.setOnClickListener { StateCasting.instance.resumeVideo(); };
        _buttonPause.setOnClickListener { StateCasting.instance.pauseVideo(); };

        if (!isInEditMode) {
            setIsPlaying(false);
        }
    }

    fun stopTimeJob() {
        _updateTimeJob?.cancel();
        _updateTimeJob = null;
    }

    fun setIsPlaying(isPlaying: Boolean) {
        _updateTimeJob?.cancel();

        if(isPlaying) {
            val d = StateCasting.instance.activeDevice;
            if (d is AirPlayCastingDevice) {
                _updateTimeJob = _scope.launch {
                    while (true) {
                        val device = StateCasting.instance.activeDevice;
                        if (device == null || !device.isPlaying) {
                            break;
                        }

                        delay(1000);
                        setTime((device.expectedCurrentTime * 1000.0).toLong());
                    }
                }
            }

            if (!_inPictureInPicture) {
                _buttonPause.visibility = View.VISIBLE;
                _buttonPlay.visibility = View.GONE;
            }
        }
        else if (!_inPictureInPicture) {
            _buttonPause.visibility = View.GONE;
            _buttonPlay.visibility = View.VISIBLE;
        }

        val position = StateCasting.instance.activeDevice?.expectedCurrentTime?.times(1000.0)?.toLong();

        if(StatePlayer.instance.hasMediaSession()) {
            StatePlayer.instance.updateMediaSession(null);
            StatePlayer.instance.updateMediaSessionPlaybackState(getPlaybackStateCompat(), (position ?: 0));
        }
    }

    fun setButtonAlpha(alpha: Float) {
        _background.alpha = alpha;
        _textPosition.alpha = alpha;
        _textDivider.alpha = alpha;
        _textDuration.alpha = alpha;
        _buttonMinimize.alpha = alpha;
        _buttonSettings.alpha = alpha;
        _buttonPause.alpha = alpha;
        _buttonPlay.alpha = alpha;
        _buttonCast.alpha = alpha;
        _timeBar.alpha = alpha;
    }

    fun setProgressBarOverlayed(isOverlayed: Boolean) {
        if(isOverlayed) {
            _thumbnail.layoutParams = _thumbnail.layoutParams.apply {
                (this as MarginLayoutParams).bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0f, resources.displayMetrics).toInt();
            };
        }
        else {
            _thumbnail.layoutParams = _thumbnail.layoutParams.apply {
                (this as MarginLayoutParams).bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7f, resources.displayMetrics).toInt();
            };
        }
    }

    fun setVideoDetails(video: IPlatformVideoDetails, position: Long) {
        Glide.with(_thumbnail)
            .load(video.thumbnails.getHQThumbnail())
            .placeholder(R.drawable.placeholder_video_thumbnail)
            .into(_thumbnail);
        _textPosition.text = position.toHumanTime(false);
        _textDuration.text = video.duration.toHumanTime(false);
        _timeBar.setPosition(position);
        _timeBar.setDuration(video.duration);
    }

    fun setTime(ms: Long) {
        _textPosition.text = ms.toHumanTime(true);
        _timeBar.setPosition(ms / 1000);
        StatePlayer.instance.updateMediaSessionPlaybackState(getPlaybackStateCompat(), ms);
    }

    fun cleanup() {
        _buttonCast.cleanup();
        _gestureControlView.cleanup();
        _updateTimeJob?.cancel();
        _updateTimeJob = null;
        _scope.cancel();
    }

    private fun getPlaybackStateCompat(): Int {
        val d = StateCasting.instance.activeDevice ?: return PlaybackState.STATE_NONE;

        return when(d.isPlaying) {
            true -> PlaybackStateCompat.STATE_PLAYING;
            else -> PlaybackStateCompat.STATE_PAUSED;
        }
    }
}