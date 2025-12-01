package com.futo.platformplayer.views.casting

import android.content.Context
import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.formatDuration
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.TargetTapLoaderView
import com.futo.platformplayer.views.behavior.GestureControlView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CastView : ConstraintLayout {
    private val _thumbnail: ImageView;
    private val _buttonMinimize: ImageButton;
    private val _buttonSettings: ImageButton;
    private val _buttonLoop: ImageButton;
    private val _buttonPlay: ImageButton;
    private val _buttonAutoplay: ImageButton;
    private val _buttonPrevious: ImageButton;
    private val _buttonNext: ImageButton;
    private val _buttonPause: ImageButton;
    private val _buttonCast: CastButton;
    private val _textPosition: TextView;
    private val _textDuration: TextView;
    private val _textDivider: TextView;
    private val _timeBar: DefaultTimeBar;
    private val _background: FrameLayout;
    private val _gestureControlView: GestureControlView;
    private val _loaderGame: TargetTapLoaderView
    private var _scope: CoroutineScope = CoroutineScope(Dispatchers.Main);
    private var _updateTimeJob: Job? = null;
    private var _inPictureInPicture: Boolean = false;
    private var _chapters: List<IChapter>? = null;
    private var _currentChapter: IChapter? = null;
    private var _speedHoldPrevRate = 1.0
    private var _speedHoldWasPlaying = false

    val onChapterChanged = Event2<IChapter?, Boolean>();
    val onMinimizeClick = Event0();
    val onSettingsClick = Event0();
    val onPrevious = Event0();
    val onNext = Event0();
    val onTimeJobTimeChanged_s = Event1<Long>()
    val loaderGameVisibilityChanged = Event1<Boolean>();

    @OptIn(UnstableApi::class)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_cast, this, true);

        _thumbnail = findViewById(R.id.image_thumbnail);
        _buttonMinimize = findViewById(R.id.button_minimize);
        _buttonSettings = findViewById(R.id.button_settings);
        _buttonLoop = findViewById(R.id.button_loop);
        _buttonAutoplay = findViewById(R.id.button_autoplay);
        _buttonPlay = findViewById(R.id.button_play);
        _buttonPrevious = findViewById(R.id.button_previous);
        _buttonNext = findViewById(R.id.button_next);
        _buttonPause = findViewById(R.id.button_pause);
        _buttonCast = findViewById(R.id.button_cast);
        _textPosition = findViewById(R.id.text_position);
        _textDivider = findViewById(R.id.text_divider);
        _textDuration = findViewById(R.id.text_duration);
        _timeBar = findViewById(R.id.time_progress);
        _background = findViewById(R.id.layout_background);
        _gestureControlView = findViewById(R.id.gesture_control);
        _loaderGame = findViewById(R.id.loader_overlay)
        _loaderGame.visibility = View.GONE
        loaderGameVisibilityChanged.emit(false)

        _gestureControlView.fullScreenGestureEnabled = false
        _gestureControlView.setupTouchArea();
        _gestureControlView.onSpeedHoldStart.subscribe {
            val d = StateCasting.instance.activeDevice ?: return@subscribe;
            _speedHoldWasPlaying = d.isPlaying
            _speedHoldPrevRate = d.speed
            try {
                if (d.canSetSpeed()) {
                    d.changeSpeed(Settings.instance.playback.getHoldPlaybackSpeed())
                }
                d.resumePlayback()
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to change playback speed to hold playback speed: $e")
            }
        }
        _gestureControlView.onSpeedHoldEnd.subscribe {
            try {
                val d = StateCasting.instance.activeDevice ?: return@subscribe;
                if (!_speedHoldWasPlaying) {
                    d.pausePlayback()
                }
                d.changeSpeed(_speedHoldPrevRate)
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to change playback speed to previous hold playback speed: $e")
            }
        }
        _gestureControlView.onTogglePlayPause.subscribe {
            StateCasting.instance.activeDevice?.let { d ->
                if (d.isPlaying) {
                    d.pausePlayback()
                } else {
                    d.resumePlayback()
                }
            }
        }

        _gestureControlView.onSeek.subscribe {
            val d = StateCasting.instance.activeDevice ?: return@subscribe;
            StateCasting.instance.videoSeekTo( d.expectedCurrentTime + it / 1000);
        };

        _buttonLoop.setOnClickListener {
            StatePlayer.instance.loopVideo = !StatePlayer.instance.loopVideo;
            _buttonLoop.setImageResource(if(StatePlayer.instance.loopVideo) R.drawable.ic_loop_active else R.drawable.ic_loop);
        }
        _buttonLoop.setImageResource(if(StatePlayer.instance.loopVideo) R.drawable.ic_loop_active else R.drawable.ic_loop);

        _timeBar.addListener(object : TimeBar.OnScrubListener {
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

        updateNextPrevious();
        _buttonPrevious.setOnClickListener { onPrevious.emit() };
        _buttonNext.setOnClickListener { onNext.emit() };

        _buttonAutoplay.setOnClickListener {
            StatePlayer.instance.autoplay = !StatePlayer.instance.autoplay;
            updateAutoplayButton()
        }
        updateAutoplayButton()
    }

    private fun updateAutoplayButton() {
        _buttonAutoplay.setColorFilter(ContextCompat.getColor(context, if (StatePlayer.instance.autoplay) com.futo.futopay.R.color.primary else R.color.white))
        _buttonAutoplay.setColorFilter(ContextCompat.getColor(context, if (StatePlayer.instance.autoplay) com.futo.futopay.R.color.primary else R.color.white))
    }

    private fun updateCurrentChapter(chaptPos: Long, isScrub: Boolean = false): Boolean {
        val currentChapter = getCurrentChapter(chaptPos);
        if(_currentChapter != currentChapter) {
            _currentChapter = currentChapter;
            /*runBlocking(Dispatchers.Main) {
                if (currentChapter != null) {
                    //TODO: Add chapter controls
                    //_control_chapter.text = " • " + currentChapter.name;
                    //_control_chapter_fullscreen.text = " • " + currentChapter.name;
                } else {
                    //TODO: Add chapter controls
                    //_control_chapter.text = "";
                    //_control_chapter_fullscreen.text = "";
                }
            }*/

            onChapterChanged.emit(currentChapter, isScrub);
            return true;
        }
        return false;
    }

    fun setChapters(chapters: List<IChapter>?) {
        _chapters = chapters;
    }

    fun getCurrentChapter(pos: Long): IChapter? {
        return _chapters?.let { chaps -> chaps.find { pos.toDouble() / 1000 > it.timeStart && pos.toDouble() / 1000 < it.timeEnd } };
    }

    private fun updateNextPrevious() {
        val vidPrev = StatePlayer.instance.getPrevQueueItem(true);
        val vidNext = StatePlayer.instance.getNextQueueItem(true);
        _buttonNext.visibility = if (vidNext != null) View.VISIBLE else View.GONE
        _buttonPrevious.visibility = if (vidPrev != null) View.VISIBLE else View.GONE
    }

    fun stopTimeJob() {
        _updateTimeJob?.cancel();
        _updateTimeJob = null;
    }

    fun cancel() {
        stopTimeJob()
        setLoading(false)
        visibility = View.GONE
    }

    fun stopAllGestures() {
        _gestureControlView.stopAllGestures();
    }

    fun setLoopVisible(visible: Boolean) {
        _buttonLoop.visibility = if (visible) View.VISIBLE else View.GONE;
    }

    fun setIsPlaying(isPlaying: Boolean) {
        stopTimeJob()

        if(isPlaying) {
            StateCasting.instance.startUpdateTimeJob(
                onTimeJobTimeChanged_s
            ) { setTime(it) }

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

    @OptIn(UnstableApi::class)
    fun setVideoDetails(video: IPlatformVideoDetails, position: Long) {
        Glide.with(_thumbnail)
            .load(video.thumbnails.getHQThumbnail())
            .placeholder(R.drawable.placeholder_video_thumbnail)
            .downsample(DownsampleStrategy.AT_MOST).override(1080, 1080)
            .into(_thumbnail);
        _textPosition.text = (position * 1000).formatDuration();
        _textDuration.text = (video.duration * 1000).formatDuration();
        _timeBar.setPosition(position);
        _timeBar.setDuration(video.duration);
        setLoading(false)
    }

    @OptIn(UnstableApi::class)
    fun setTime(ms: Long) {
        updateCurrentChapter(ms);
        _textPosition.text = ms.formatDuration();
        _timeBar.setPosition(ms / 1000);
        StatePlayer.instance.updateMediaSessionPlaybackState(getPlaybackStateCompat(), ms);
    }

    fun cleanup() {
        _buttonCast.cleanup();
        _gestureControlView.cleanup();
        _updateTimeJob?.cancel();
        _updateTimeJob = null;
        _scope.cancel();
        setLoading(false)
    }

    private fun getPlaybackStateCompat(): Int {
        val d = StateCasting.instance.activeDevice ?: return PlaybackState.STATE_NONE;

        return when(d.isPlaying) {
            true -> PlaybackStateCompat.STATE_PLAYING;
            else -> PlaybackStateCompat.STATE_PAUSED;
        }
    }

    fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            _loaderGame.visibility = View.VISIBLE
            _loaderGame.startLoader()
            loaderGameVisibilityChanged.emit(true)
        } else {
            _loaderGame.visibility = View.GONE
            _loaderGame.stopAndResetLoader()
            loaderGameVisibilityChanged.emit(false)
        }
    }

    fun setLoading(expectedDurationMs: Int) {
        _loaderGame.visibility = View.VISIBLE
        _loaderGame.startLoader(expectedDurationMs.toLong())
        loaderGameVisibilityChanged.emit(true)
    }

    companion object {
        private val TAG = "CastView";
    }
}