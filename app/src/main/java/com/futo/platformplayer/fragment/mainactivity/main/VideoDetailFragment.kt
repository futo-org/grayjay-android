package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.SettingsActivity
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.PlatformVideoWithTime
import com.futo.platformplayer.models.UrlVideoWithTime
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.containers.SingleViewTouchableMotionLayout
import kotlin.math.min

@UnstableApi
class VideoDetailFragment : MainFragment {
    override val isMainView : Boolean = false;
    override val hasBottomBar: Boolean = true;
    override val isOverlay : Boolean = true;
    override val isHistory: Boolean = false;

    private var _isActive: Boolean = false;

    private var _viewDetail : VideoDetailView? = null;
    private var _view : SingleViewTouchableMotionLayout? = null;

    var isFullscreen : Boolean = false;
    /**
     * whether the view is in the process of switching from full-screen maximized to minimized
     * this is used to detect that the app is skipping the non full-screen maximized state
     */
    var isMinimizingFromFullScreen : Boolean = false;
    val onFullscreenChanged = Event1<Boolean>();
    var isTransitioning : Boolean = false
        private set;
    var isInPictureInPicture : Boolean = false
        private set;

    private var _state: State = State.CLOSED

    var state: State
        get() = _state
        set(value) {
            _state = value
            onStateChanged(value)
        }

    val currentUrl get() = _viewDetail?.currentUrl;

    val onMinimize = Event0();
    val onTransitioning = Event1<Boolean>();
    val onMaximized = Event0();

    private var _isInitialMaximize = true;

    private val _maximizeProgress get() = _view?.progress ?: 0.0f;

    private var _loadUrlOnCreate: UrlVideoWithTime? = null;
    private var _leavingPiP = false;

//region Fragment
    constructor() : super()

    fun nextVideo() {
        _viewDetail?.nextVideo(true, true, true);
    }

    fun previousVideo() {
        _viewDetail?.prevVideo(true);
    }

    private fun isSmallWindow(): Boolean {
        return min(
            resources.configuration.screenWidthDp,
            resources.configuration.screenHeightDp
        ) < resources.getInteger(R.integer.landscape_threshold) * 2
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val isLandscapeVideo: Boolean = _viewDetail?.isLandscapeVideo() ?: false

        val isSmallWindow = isSmallWindow()

        if (
            isSmallWindow
            && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
            && !isFullscreen
            && state == State.MAXIMIZED
        ) {
            _viewDetail?.setFullscreen(true)
        } else if (
            isSmallWindow
            && isFullscreen
            && !Settings.instance.playback.fullscreenPortrait
            && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
            && isLandscapeVideo
        ) {
            _viewDetail?.setFullscreen(false)
        }
    }

    private fun onStateChanged(state: State) {
        if (
            isSmallWindow()
            && state == State.MAXIMIZED
            && !isFullscreen
            && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            _viewDetail?.setFullscreen(true)
        }

        updateOrientation()
    }

    private fun onVideoChanged(videoWidth : Int, videoHeight: Int) {
        if (
            isSmallWindow()
            && state == State.MAXIMIZED
            && !isFullscreen
            && videoHeight > videoWidth
        ) {
            _viewDetail?.setFullscreen(true)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun updateOrientation() {
        val a = activity ?: return
        val isFullScreenPortraitAllowed = Settings.instance.playback.fullscreenPortrait
        val isReversePortraitAllowed = Settings.instance.playback.reversePortrait
        val rotationLock = StatePlayer.instance.rotationLock

        val isLandscapeVideo: Boolean = _viewDetail?.isLandscapeVideo() ?: false

        val isSmallWindow = isSmallWindow()

        // For small windows if the device isn't landscape right now and full screen portrait isn't allowed then we should force landscape
        if (isSmallWindow && isFullscreen && !isFullScreenPortraitAllowed && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !rotationLock && isLandscapeVideo) {
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        // For small windows if the device isn't in a portrait orientation and we're in the maximized state then we should force portrait
        else if (isSmallWindow && !isMinimizingFromFullScreen && !isFullscreen && state == State.MAXIMIZED && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else if (rotationLock) {
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            when (Settings.instance.playback.autoRotate) {
                0 -> {
                    a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                }

                1 -> {
                    a.requestedOrientation = if (isReversePortraitAllowed) {
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    }
                }

                2 -> {
                    a.requestedOrientation = if (isReversePortraitAllowed) {
                        ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        Logger.i(TAG, "onShownWithView parameter=$parameter")

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

    fun preventPictureInPicture() {
        Logger.i(TAG, "preventPictureInPicture() preventPictureInPicture = true");
        _viewDetail?.preventPictureInPicture = true;
    }

    fun minimizeVideoDetail() {
        _viewDetail?.setFullscreen(false);
        if(_view != null)
            _view!!.transitionToStart();
    }
    fun maximizeVideoDetail(instant: Boolean = false) {
        if((_maximizeProgress > 0.9f || instant) && state != State.MAXIMIZED) {
            state = State.MAXIMIZED;
            onMaximized.emit();
        }
        _view?.let {
            if(!instant) {
                it.transitionToEnd();
            } else {
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

        StatePlayer.instance.clearQueue();
        StatePlayer.instance.setPlayerClosed();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _view = inflater.inflate(R.layout.fragment_video_detail, container, false) as SingleViewTouchableMotionLayout;
        _viewDetail = _view!!.findViewById<VideoDetailView>(R.id.fragview_videodetail).also {
            it.applyFragment(this);
            it.onFullscreenChanged.subscribe(::onFullscreenChanged);
            it.onVideoChanged.subscribe(::onVideoChanged)
            it.onMinimize.subscribe {
                isMinimizingFromFullScreen = true
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
                _viewDetail?.stopAllGestures()

                if (state != State.MINIMIZED && progress < 0.1) {
                    state = State.MINIMIZED;
                    isMinimizingFromFullScreen = false
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

        _view?.let {
            if (it.progress >= 0.5 && it.progress < 1.0)
                maximizeVideoDetail();
            if (it.progress < 0.5 && it.progress > 0.0)
                minimizeVideoDetail();
        }

        _loadUrlOnCreate?.let { _viewDetail?.setVideo(it.url, it.timeSeconds, it.playWhenReady) };
        maximizeVideoDetail();

        SettingsActivity.settingsActivityClosed.subscribe(this) {
            updateOrientation()
        }

        StatePlayer.instance.onRotationLockChanged.subscribe(this) {
            updateOrientation()
        }
        return _view!!;
    }

    fun onUserLeaveHint() {
        val viewDetail = _viewDetail;
        Logger.i(TAG, "onUserLeaveHint preventPictureInPicture=${viewDetail?.preventPictureInPicture} isCasting=${StateCasting.instance.isCasting} isBackgroundPictureInPicture=${Settings.instance.playback.isBackgroundPictureInPicture()} allowBackground=${viewDetail?.allowBackground}");

        if(viewDetail?.preventPictureInPicture == false && !StateCasting.instance.isCasting && Settings.instance.playback.isBackgroundPictureInPicture() && !viewDetail.allowBackground) {
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
        Logger.v(TAG, "onResume");
        _isActive = true;
        _leavingPiP = false;

        _viewDetail?.let {
            Logger.v(TAG, "onResume preventPictureInPicture=false");
            it.preventPictureInPicture = false;

            if (state != State.CLOSED) {
                it.onResume();
            }
        }

        StateCasting.instance.onResume();
    }
    override fun onPause() {
        super.onPause();
        Logger.v(TAG, "onPause");
        _isActive = false;

        if(!isInPictureInPicture && state != State.CLOSED)
            _viewDetail?.onPause();
    }

    override fun onStop() {
        Logger.v(TAG, "onStop");

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

        Logger.v(TAG, "shouldStop: $shouldStop");
        if(shouldStop) {
            _viewDetail?.onStop();
            StateCasting.instance.onStop();
        }
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        Logger.v(TAG, "onDestroyMainView");

        SettingsActivity.settingsActivityClosed.remove(this)
        StatePlayer.instance.onRotationLockChanged.remove(this)

        _viewDetail?.let {
            _viewDetail = null;
            it.onDestroy();
        }
        _view = null;
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

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
            activity?.window?.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
            activity?.window?.insetsController?.let { controller ->
                controller.show(WindowInsets.Type.statusBars())
                controller.show(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun onFullscreenChanged(fullscreen : Boolean) {
        isFullscreen = fullscreen;
        onFullscreenChanged.emit(isFullscreen);

        if (isFullscreen) {
            hideSystemUI()
        } else {
            showSystemUI()
        }

        updateOrientation();
        _view?.allowMotion = !fullscreen;
    }

    companion object {
        private const val TAG = "VideoDetailFragment";

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