package com.futo.platformplayer.fragment.mainactivity.main

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.*
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.listeners.OrientationManager
import com.futo.platformplayer.models.PlatformVideoWithTime
import com.futo.platformplayer.models.UrlVideoWithTime
import com.futo.platformplayer.states.StateSaved
import com.futo.platformplayer.states.VideoToOpen
import com.futo.platformplayer.views.containers.SingleViewTouchableMotionLayout

class VideoDetailFragment : MainFragment {
    override val isMainView : Boolean = false;
    override val hasBottomBar: Boolean = true;
    override val isOverlay : Boolean = true;
    override val isHistory: Boolean = false;

    private var _isActive: Boolean = false;

    private var _viewDetail : VideoDetailView? = null;
    private var _view : SingleViewTouchableMotionLayout? = null;

    var isFullscreen : Boolean = false;
    var isTransitioning : Boolean = false
        private set;
    var isInPictureInPicture : Boolean = false
        private set;

    var state: State = State.CLOSED;
    val currentUrl get() = _viewDetail?.currentUrl;

    val onMinimize = Event0();
    val onTransitioning = Event1<Boolean>();
    val onMaximized = Event0();

    var lastOrientation : OrientationManager.Orientation = OrientationManager.Orientation.PORTRAIT
        private set;

    private var _isInitialMaximize = true;

    private val _maximizeProgress get() = _view?.progress ?: 0.0f;

    private var _loadUrlOnCreate: UrlVideoWithTime? = null;
    private var _leavingPiP = false;

//region Fragment
    constructor() : super() {
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        Logger.i(TAG, "onShownWithView parameter=$parameter")

        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(parameter is IPlatformVideoDetails)
            _viewDetail?.setVideoDetails(parameter, true);
        else if (parameter is IPlatformVideo)
            _viewDetail?.setVideoOverview(parameter);
        else if(parameter is PlatformVideoWithTime)
            _viewDetail?.setVideoOverview(parameter.video, true, parameter.time);
        else if (parameter is UrlVideoWithTime) {
            if (_viewDetail == null) {
                _loadUrlOnCreate = parameter;
            } else {
                _viewDetail?.setVideo(parameter.url, parameter.timeSeconds, parameter.playWhenReady);
            }
        } else if(parameter is String) {
            if (_viewDetail == null) {
                _loadUrlOnCreate = UrlVideoWithTime(parameter, 0, true);
            } else {
                _viewDetail?.setVideo(parameter, 0, true);
            }
        }
    }

    override fun onOrientationChanged(orientation: OrientationManager.Orientation) {
        super.onOrientationChanged(orientation);

        if(!_isActive || state != State.MAXIMIZED)
            return;

        var newOrientation = orientation;
        val d = StateCasting.instance.activeDevice;
        if (d != null && d.connectionState == CastConnectionState.CONNECTED) {
            newOrientation = OrientationManager.Orientation.PORTRAIT;
        } else if(StatePlayer.instance.rotationLock) {
            return;
        }

        if(lastOrientation == newOrientation)
            return;

        activity?.let {
            if (isFullscreen) {
                if(newOrientation == OrientationManager.Orientation.REVERSED_LANDSCAPE && it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    changeOrientation(OrientationManager.Orientation.REVERSED_LANDSCAPE);
                else if(newOrientation == OrientationManager.Orientation.LANDSCAPE && it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
                    changeOrientation(OrientationManager.Orientation.LANDSCAPE);
                else if(Settings.instance.playback.isAutoRotate() && (newOrientation == OrientationManager.Orientation.PORTRAIT || newOrientation == OrientationManager.Orientation.REVERSED_PORTRAIT)) {
                    _viewDetail?.setFullscreen(false);
                }
            }
            else {
                if(Settings.instance.playback.isAutoRotate() && (lastOrientation == OrientationManager.Orientation.PORTRAIT || lastOrientation == OrientationManager.Orientation.REVERSED_PORTRAIT)) {
                    lastOrientation = newOrientation;
                    _viewDetail?.setFullscreen(true);
                }
            }
        }
        lastOrientation = newOrientation;
    }
    override fun onBackPressed(): Boolean {
        Logger.i(TAG, "onBackPressed")

        if (_viewDetail?.onBackPressed() == true) {
            return true;
        }

        if(state == State.MAXIMIZED)
            minimizeVideoDetail();
        else
            closeVideoDetails();
        return true;
    }
    override fun onHide() {
        super.onHide();
    }

    fun preventPictureInPicture() {
        Logger.i(TAG, "preventPictureInPicture() preventPictureInPicture = true");
        _viewDetail?.preventPictureInPicture = true;
    }

    fun minimizeVideoDetail(){
        _viewDetail?.setFullscreen(false);
        if(_view != null)
            _view!!.transitionToStart();
    }
    fun maximizeVideoDetail(instant: Boolean = false) {
        if(_maximizeProgress > 0.9f && state != State.MAXIMIZED) {
            state = State.MAXIMIZED;
            onMaximized.emit();
        }
        _view?.let {
            if(!instant)
                it.transitionToEnd();
            else {
                it.progress = 1f;
                onTransitioning.emit(true);
            }
        };
    }
    fun closeVideoDetails() {
        Logger.i(TAG, "closeVideoDetails()")
        state = State.CLOSED;
        _viewDetail?.onStop();
        close();

        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        StatePlayer.instance.clearQueue();
        StatePlayer.instance.setPlayerClosed();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _view = inflater.inflate(R.layout.fragment_video_detail, container, false) as SingleViewTouchableMotionLayout;
        _viewDetail = _view!!.findViewById<VideoDetailView>(R.id.fragview_videodetail).also {
            it.applyFragment(this);
            it.onFullscreenChanged.subscribe(::onFullscreenChanged);
            it.onMinimize.subscribe {
                _view!!.transitionToStart();
            };
            it.onClose.subscribe {
                Logger.i(TAG, "onClose")
                closeVideoDetails();
            };
            it.onMaximize.subscribe { maximizeVideoDetail(it) };
            it.onPlayChanged.subscribe {
                if(isInPictureInPicture) {
                    val params = _viewDetail?.getPictureInPictureParams();
                    if (params != null)
                        activity?.setPictureInPictureParams(params);
                }
            };
            it.onEnterPictureInPicture.subscribe {
                Logger.i(TAG, "onEnterPictureInPicture")
                isInPictureInPicture = true;
                _viewDetail?.handleEnterPictureInPicture();
                _viewDetail?.invalidate();
            };
            it.onTouchCancel.subscribe {
                val v = _view ?: return@subscribe;
                if (v.progress >= 0.5 && v.progress < 1) {
                    maximizeVideoDetail();
                }
                if (v.progress < 0.5 && v.progress > 0) {
                    minimizeVideoDetail();
                }
            };
        }
        _view!!.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) {

                if (state != State.MINIMIZED && progress < 0.1) {
                    state = State.MINIMIZED;
                    onMinimize.emit();
                }
                else if (state != State.MAXIMIZED && progress > 0.9) {
                    if (_isInitialMaximize) {
                        state = State.CLOSED;
                        _isInitialMaximize = false;
                    }
                    else {
                        state = State.MAXIMIZED;
                        onMaximized.emit();
                    }
                }

                if (isTransitioning && (progress > 0.95 || progress < 0.05)) {
                    isTransitioning = false;
                    onTransitioning.emit(isTransitioning);

                    if(isInPictureInPicture) leavePictureInPictureMode(false); //Workaround to prevent getting stuck in p2p
                }
                else if (!isTransitioning && (progress < 0.95 && progress > 0.05)) {
                    isTransitioning = true;
                    onTransitioning.emit(isTransitioning);

                    if(isInPictureInPicture) leavePictureInPictureMode(false); //Workaround to prevent getting stuck in p2p
                }
            }
            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) { }
            override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) { }
            override fun onTransitionTrigger(p0: MotionLayout?, p1: Int, p2: Boolean, p3: Float) { }
        });

        context
        _view?.let {
            if (it.progress >= 0.5 && it.progress < 1.0)
                maximizeVideoDetail();
            if (it.progress < 0.5 && it.progress > 0.0)
                minimizeVideoDetail();
        }

        _loadUrlOnCreate?.let { _viewDetail?.setVideo(it.url, it.timeSeconds, it.playWhenReady) };

        maximizeVideoDetail();
        return _view!!;
    }

    fun onUserLeaveHint() {
        val viewDetail = _viewDetail;
        Logger.i(TAG, "onUserLeaveHint preventPictureInPicture=${viewDetail?.preventPictureInPicture} isCasting=${StateCasting.instance.isCasting} isBackgroundPictureInPicture=${Settings.instance.playback.isBackgroundPictureInPicture()} allowBackground=${viewDetail?.allowBackground}");

        if(viewDetail?.preventPictureInPicture == false && !StateCasting.instance.isCasting && Settings.instance.playback.isBackgroundPictureInPicture() && viewDetail?.allowBackground != true) {
            _leavingPiP = false;

            val params = _viewDetail?.getPictureInPictureParams();
            if(params != null) {
                Logger.i(TAG, "enterPictureInPictureMode")
                activity?.enterPictureInPictureMode(params);
            }
        }
    }

    fun forcePictureInPicture() {
        val params = _viewDetail?.getPictureInPictureParams();
        if(params != null)
            activity?.enterPictureInPictureMode(params);
    }
    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, isStop: Boolean, newConfig: Configuration) {
        if (isInPictureInPictureMode) {
            _viewDetail?.startPictureInPicture();
        } else if (isInPictureInPicture) {
            leavePictureInPictureMode(isStop);
        }
    }
    fun leavePictureInPictureMode(isStop: Boolean) {
        isInPictureInPicture = false;
        _leavingPiP = true;

        UIDialogs.dismissAllDialogs();

        _viewDetail?.handleLeavePictureInPicture();
        if (isStop) {
            stopIfRequired();
        }
    }

    override fun onResume() {
        super.onResume();
        Logger.i(TAG, "onResume");
        _isActive = true;
        _leavingPiP = false;

        _viewDetail?.let {
            Logger.i(TAG, "onResume preventPictureInPicture=false");
            it.preventPictureInPicture = false;

            if (state != State.CLOSED) {
                it.onResume();
            }
        }

        val realOrientation = if(activity is MainActivity) (activity as MainActivity).orientation else lastOrientation;
        Logger.i(TAG, "Real orientation on boot ${realOrientation}, lastOrientation: ${lastOrientation}");
        if(realOrientation != lastOrientation)
            onOrientationChanged(realOrientation);
    }
    override fun onPause() {
        super.onPause();
        Logger.i(TAG, "onPause");
        _isActive = false;

        if(!isInPictureInPicture && state != State.CLOSED)
            _viewDetail?.onPause();
    }

    override fun onStop() {
        Logger.i(TAG, "onStop");

        stopIfRequired();
        super.onStop();
    }

    private fun stopIfRequired() {
        var shouldStop = true;
        if (_viewDetail?.allowBackground == true) {
            shouldStop = false;
        } else if (Settings.instance.playback.isBackgroundPictureInPicture() && !_leavingPiP) {
            shouldStop = false;
        } else if (Settings.instance.playback.isBackgroundContinue()) {
            shouldStop = false;
        } else if (StateCasting.instance.isCasting) {
            shouldStop = false;
        }

        Logger.i(TAG, "shouldStop: $shouldStop");
        if(shouldStop) {
            _viewDetail?.let {
                val v = it.video ?: return@let;
                StateSaved.instance.setVideoToOpenBlocking(VideoToOpen(v.url, (it.lastPositionMilliseconds / 1000.0f).toLong()));
            }

            _viewDetail?.onStop();
            StateCasting.instance.onStop();
            Logger.i(TAG, "called onStop() shouldStop: $shouldStop");
        }
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        Logger.i(TAG, "onDestroyMainView");
        _viewDetail?.let {
            _viewDetail = null;
            it.onDestroy();
        }
        _view = null;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        StateCasting.instance.onActiveDeviceConnectionStateChanged.subscribe(this) { _, _ ->
            onOrientationChanged(lastOrientation);
        };
    }

    override fun onDestroy() {
        super.onDestroy()

        _viewDetail?.let {
            _viewDetail = null;
            it.onDestroy();
        }

        StateCasting.instance.onActiveDeviceConnectionStateChanged.remove(this);

        Logger.i(TAG, "onDestroy");
        onMinimize.clear();
        onMaximized.clear();
    }

    private fun onFullscreenChanged(fullscreen : Boolean) {
        activity?.let {
            if (fullscreen) {
                var orient = lastOrientation;
                if(orient == OrientationManager.Orientation.PORTRAIT || orient == OrientationManager.Orientation.REVERSED_PORTRAIT)
                    orient = OrientationManager.Orientation.LANDSCAPE;
                changeOrientation(orient);
            }
            else
                changeOrientation(OrientationManager.Orientation.PORTRAIT);
        }
        isFullscreen = fullscreen;
    }
    private fun changeOrientation(orientation: OrientationManager.Orientation) {
        Logger.i(TAG, "Orientation Change:" + orientation.name);
        activity?.let {
            when (orientation) {
                OrientationManager.Orientation.LANDSCAPE -> {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    _view?.allowMotion = false;

                    WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
                    WindowInsetsControllerCompat(it.window, _viewDetail!!).let { controller ->
                        controller.hide(WindowInsetsCompat.Type.statusBars());
                        controller.hide(WindowInsetsCompat.Type.systemBars());
                        controller.systemBarsBehavior =  WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
                    }
                }
                OrientationManager.Orientation.REVERSED_LANDSCAPE -> {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    _view?.allowMotion = false;

                    WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
                    WindowInsetsControllerCompat(it.window, _viewDetail!!).let { controller ->
                        controller.hide(WindowInsetsCompat.Type.statusBars());
                        controller.hide(WindowInsetsCompat.Type.systemBars());
                        controller.systemBarsBehavior =  WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
                    }
                }
                else -> {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    _view?.allowMotion = true;

                    WindowCompat.setDecorFitsSystemWindows(it.window, true)
                    WindowInsetsControllerCompat(it.window, _viewDetail!!).let { controller ->
                        controller.show(WindowInsetsCompat.Type.statusBars());
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = "VideoDetailFragment";

        fun newInstance() = VideoDetailFragment().apply {}
    }

    enum class State {
        CLOSED,
        MINIMIZED,
        MAXIMIZED
    }

//endregion

//region View
    //TODO: Determine if encapsulated would be readable enough
//endregion
}