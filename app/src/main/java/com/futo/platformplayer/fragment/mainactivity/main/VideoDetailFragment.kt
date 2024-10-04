package com.futo.platformplayer.fragment.mainactivity.main

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.SimpleOrientationListener
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.SettingsActivity
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.listeners.AutoRotateChangeListener
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.PlatformVideoWithTime
import com.futo.platformplayer.models.UrlVideoWithTime
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.containers.SingleViewTouchableMotionLayout


class VideoDetailFragment : MainFragment {
    override val isMainView : Boolean = false;
    override val hasBottomBar: Boolean = true;
    override val isOverlay : Boolean = true;
    override val isHistory: Boolean = false;

    private var _isActive: Boolean = false;

    private var _viewDetail : VideoDetailView? = null;
    private var _view : SingleViewTouchableMotionLayout? = null;
    private lateinit var _autoRotateChangeListener: AutoRotateChangeListener
    private lateinit var _orientationListener: SimpleOrientationListener
    private var _currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    var isFullscreen : Boolean = false;
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
    constructor() : super() {
    }

    fun nextVideo() {
        _viewDetail?.nextVideo(true, true, true);
    }

    fun previousVideo() {
        _viewDetail?.prevVideo(true);
    }

    private fun onStateChanged(state: VideoDetailFragment.State) {
        updateOrientation()
    }

    private fun updateOrientation() {
        val a = activity ?: return
        val isMaximized = state == State.MAXIMIZED
        val isFullScreenPortraitAllowed = Settings.instance.playback.fullscreenPortrait;
        val bypassRotationPrevention = Settings.instance.other.bypassRotationPrevention;
        val fullAutorotateLock = Settings.instance.playback.fullAutorotateLock
        val currentRequestedOrientation = a.requestedOrientation
        val isReversePortraitAllowed = Settings.instance.playback.reversePortrait
        var currentOrientation = if (_currentOrientation == -1) currentRequestedOrientation else _currentOrientation
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT && !isReversePortraitAllowed)
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val isAutoRotate = Settings.instance.playback.isAutoRotate()
        val isFs = isFullscreen

        if (fullAutorotateLock) {
            if (isFs && isMaximized) {
                if (isFullScreenPortraitAllowed) {
                    if (isAutoRotate) {
                        a.requestedOrientation = currentOrientation
                    }
                } else if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    if (isAutoRotate || currentOrientation != currentRequestedOrientation && (currentRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || currentRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT)) {
                        a.requestedOrientation = currentOrientation
                    }
                } else {
                    a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            } else if (bypassRotationPrevention) {
                a.requestedOrientation = currentOrientation
            } else if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                a.requestedOrientation = currentOrientation
            } else {
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            if (isFs && isMaximized) {
                if (isFullScreenPortraitAllowed) {
                    a.requestedOrientation = currentOrientation
                } else if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    a.requestedOrientation = currentOrientation
                } else if (currentRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || currentRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    //Don't change anything
                } else {
                    a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            } else if (bypassRotationPrevention) {
                a.requestedOrientation = currentOrientation
            } else if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                a.requestedOrientation = currentOrientation
            } else {
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        Log.i(TAG, "updateOrientation (isFs = ${isFs}, currentOrientation = ${currentOrientation}, fullAutorotateLock = ${fullAutorotateLock}, currentRequestedOrientation = ${currentRequestedOrientation}, isMaximized = ${isMaximized}, isAutoRotate = ${isAutoRotate}, isFullScreenPortraitAllowed = ${isFullScreenPortraitAllowed}) resulted in requested orientation ${activity?.requestedOrientation}");
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

    override fun onHide() {
        super.onHide();
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
                _viewDetail?.stopAllGestures()

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

        _view?.let {
            if (it.progress >= 0.5 && it.progress < 1.0)
                maximizeVideoDetail();
            if (it.progress < 0.5 && it.progress > 0.0)
                minimizeVideoDetail();
        }

        _autoRotateChangeListener = AutoRotateChangeListener(requireContext(), Handler()) { _ ->
            if (updateAutoFullscreen()) {
                return@AutoRotateChangeListener
            }
            updateOrientation()
        }

        _loadUrlOnCreate?.let { _viewDetail?.setVideo(it.url, it.timeSeconds, it.playWhenReady) };
        maximizeVideoDetail();

        SettingsActivity.settingsActivityClosed.subscribe(this) {
            updateOrientation()
        }

        StatePlayer.instance.onRotationLockChanged.subscribe(this) {
            if (updateAutoFullscreen()) {
                return@subscribe
            }
            updateOrientation()
        }

        _orientationListener = SimpleOrientationListener(requireActivity(), lifecycleScope)
        _orientationListener.onOrientationChanged.subscribe {
            _currentOrientation = it
            Logger.i(TAG, "Current orientation changed (_currentOrientation = ${_currentOrientation})")

            if (updateAutoFullscreen()) {
                return@subscribe
            }
            updateOrientation()
        }
        return _view!!;
    }

    private fun updateAutoFullscreen(): Boolean {
        if (Settings.instance.playback.isAutoRotate()) {
            if (state == State.MAXIMIZED && !isFullscreen && (_currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || _currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)) {
                _viewDetail?.setFullscreen(true)
                return true
            }

            if (state == State.MAXIMIZED && isFullscreen && !Settings.instance.playback.fullscreenPortrait && (_currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || (_currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT && Settings.instance.playback.reversePortrait ))) {
                _viewDetail?.setFullscreen(false)
                return true
            }
        }
        return false
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
            Logger.v(TAG, "called onStop() shouldStop: $shouldStop");
        }
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        Logger.v(TAG, "onDestroyMainView");
        _autoRotateChangeListener?.unregister()
        _orientationListener.stopListening()

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