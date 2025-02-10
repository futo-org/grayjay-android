package com.futo.platformplayer.views.behavior

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Animatable
import android.media.AudioManager
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.GestureDetectorCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.others.CircularProgressBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class GestureControlView : LinearLayout {
    private val _scope = CoroutineScope(Dispatchers.Main);
    private val _imageFastForward: ImageView;
    private val _textFastForward: TextView;
    private val _imageRewind: ImageView;
    private val _textRewind: TextView;
    private val _layoutFastForward: LinearLayout;
    private val _layoutRewind: LinearLayout;
    private var _rewinding: Boolean = false;
    private var _skipping: Boolean = false;
    private var _animatorSetControls: AnimatorSet? = null;
    private var _animatorSetFastForward: AnimatorSet? = null;
    private var _fastForwardCounter: Int = 1;
    private var _jobAutoFastForward: Job? = null;
    private var _jobExitFastForward: Job? = null;
    private var _jobHideControls: Job? = null;
    private var _controlsVisible: Boolean = true;
    private var _isControlsLocked: Boolean = false;
    private var _layoutControls: ViewGroup? = null;
    private var _background: View? = null;
    private var _soundFactor = 1.0f;
    private var _adjustingSound: Boolean = false;
    private val _layoutControlsSound: FrameLayout;
    private val _progressSound: CircularProgressBar;
    private var _animatorSound: ObjectAnimator? = null;
    private var _brightnessFactor = 1.0f;
    private var _originalBrightnessFactor = 1.0f;
    private var _originalBrightnessMode: Int = 0;
    private var _adjustingBrightness: Boolean = false;
    private val _layoutControlsBrightness: FrameLayout;
    private val _progressBrightness: CircularProgressBar;
    private var _isFullScreen = false;
    private var _animatorBrightness: ObjectAnimator? = null;
    private val _layoutControlsFullscreen: FrameLayout;
    private var _adjustingFullscreenUp: Boolean = false;
    private var _adjustingFullscreenDown: Boolean = false;
    private var _fullScreenFactorUp = 1.0f;
    private var _fullScreenFactorDown = 1.0f;

    private var _scaleGestureDetector: ScaleGestureDetector
    private var _scaleFactor = 1.0f
    private var _translationX = 0.0f
    private var _translationY = 0.0f
    private val _layoutControlsZoom: FrameLayout
    private val _textZoom: TextView
    private var _isZooming = false
    private var _isPanning = false
    private var _isZoomPanEnabled = false
    private var _surfaceView: View? = null
    private var _layoutIndicatorFill: FrameLayout;
    private var _layoutIndicatorFit: FrameLayout;

    private val _gestureController: GestureDetectorCompat;

    val isUserGesturing get() = _rewinding || _skipping || _adjustingBrightness || _adjustingSound || _adjustingFullscreenUp || _adjustingFullscreenDown || _isPanning || _isZooming;

    val onSeek = Event1<Long>();
    val onBrightnessAdjusted = Event1<Float>();
    val onPan = Event2<Float, Float>();
    val onZoom = Event1<Float>();
    val onSoundAdjusted = Event1<Float>();
    val onToggleFullscreen = Event0();

    var fullScreenGestureEnabled = true

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_gesture_controls, this, true);

        _imageFastForward = findViewById(R.id.image_fastforward);
        _textFastForward = findViewById(R.id.text_fastforward);
        _imageRewind = findViewById(R.id.image_rewind);
        _textRewind = findViewById(R.id.text_rewind);
        _layoutFastForward = findViewById(R.id.layout_controls_fast_forward);
        _layoutRewind = findViewById(R.id.layout_controls_rewind);
        _layoutControlsSound = findViewById(R.id.layout_controls_sound);
        _progressSound = findViewById(R.id.progress_sound);
        _layoutControlsBrightness = findViewById(R.id.layout_controls_brightness);
        _layoutControlsZoom = findViewById(R.id.layout_controls_zoom)
        _textZoom = findViewById(R.id.text_zoom)
        _progressBrightness = findViewById(R.id.progress_brightness);
        _layoutControlsFullscreen = findViewById(R.id.layout_controls_fullscreen);
        _layoutIndicatorFill = findViewById(R.id.layout_indicator_fill);
        _layoutIndicatorFit = findViewById(R.id.layout_indicator_fit);

        _scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!_isZoomPanEnabled || !_isFullScreen || !Settings.instance.gestureControls.zoom) {
                    return false
                }

                val newScaleFactor = (_scaleFactor * detector.scaleFactor).coerceAtLeast(1.0f).coerceAtMost(10.0f)
                val scaleFactorChange = newScaleFactor / _scaleFactor
                _scaleFactor = newScaleFactor
                onZoom.emit(_scaleFactor)

                val sx = detector.focusX
                val sy = detector.focusY

                val tx = _translationX + width / 2.0f
                val ty = _translationY + height / 2.0f

                val matrix = Matrix()
                matrix.postTranslate(-sx, -sy)
                matrix.postScale(scaleFactorChange, scaleFactorChange)
                matrix.postTranslate(sx, sy)

                val point = floatArrayOf(tx, ty)
                matrix.mapPoints(point)
                pan(point[0] - width / 2.0f, point[1] - height / 2.0f)

                _layoutControlsZoom.visibility = View.VISIBLE
                _textZoom.text = "${String.format("%.1f", _scaleFactor)}x"
                _isZooming = true

                updateSnappingVisibility()

                return true
            }
        })

        _gestureController = GestureDetectorCompat(context, object : GestureDetector.OnGestureListener {
            override fun onDown(p0: MotionEvent): Boolean { return false; }
            override fun onShowPress(p0: MotionEvent) = Unit;
            override fun onSingleTapUp(p0: MotionEvent): Boolean { return false; }
            override fun onFling(p0: MotionEvent?, p1: MotionEvent, p2: Float, p3: Float): Boolean { return false; }
            override fun onScroll(p0: MotionEvent?, p1: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if(p0 == null)
                    return false;

                if (!_isPanning && p1.pointerCount == 1) {
                    val minDistance = Math.min(width, height)
                    if (_isFullScreen && _adjustingBrightness) {
                        val adjustAmount = (distanceY * 2) / minDistance;
                        _brightnessFactor = (_brightnessFactor + adjustAmount).coerceAtLeast(0.0f).coerceAtMost(1.0f);
                        _progressBrightness.progress = _brightnessFactor;
                        onBrightnessAdjusted.emit(_brightnessFactor);
                    } else if (_isFullScreen && _adjustingSound) {
                        val adjustAmount = (distanceY * 2) / minDistance;
                        _soundFactor = (_soundFactor + adjustAmount).coerceAtLeast(0.0f).coerceAtMost(1.0f);
                        _progressSound.progress = _soundFactor;
                        onSoundAdjusted.emit(_soundFactor);
                    } else if (_adjustingFullscreenUp) {
                        val adjustAmount = (distanceY * 2) / minDistance;
                        _fullScreenFactorUp = (_fullScreenFactorUp + adjustAmount).coerceAtLeast(0.0f).coerceAtMost(1.0f);
                        _layoutControlsFullscreen.alpha = _fullScreenFactorUp;
                    } else if (_adjustingFullscreenDown) {
                        val adjustAmount = (-distanceY * 2) / minDistance;
                        _fullScreenFactorDown = (_fullScreenFactorDown + adjustAmount).coerceAtLeast(0.0f).coerceAtMost(1.0f);
                        _layoutControlsFullscreen.alpha = _fullScreenFactorDown;
                    } else if (p0.pointerCount == 1) {
                        val rx = (p0.x + p1.x) / (2 * width);
                        val ry = (p0.y + p1.y) / (2 * height);
                        if (ry > 0.1 && ry < 0.9) {
                            if (Settings.instance.gestureControls.brightnessSlider && _isFullScreen && rx < 0.2) {
                                startAdjustingBrightness();
                            } else if (Settings.instance.gestureControls.volumeSlider && _isFullScreen && rx > 0.8) {
                                startAdjustingSound();
                            } else if (Settings.instance.gestureControls.toggleFullscreen && fullScreenGestureEnabled && rx in 0.3..0.7) {
                                if (_isFullScreen) {
                                    startAdjustingFullscreenDown();
                                } else {
                                    startAdjustingFullscreenUp();
                                }
                            }
                        }
                    }
                } else if (_isZoomPanEnabled && _isFullScreen && !_isZooming && Settings.instance.gestureControls.pan) {
                    _isPanning = true
                    stopAllGestures()
                    updateSnappingVisibility()
                    pan(_translationX - distanceX, _translationY - distanceY)
                }

                return true;
            }
            override fun onLongPress(p0: MotionEvent) = Unit
        });

        _gestureController.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
                if (_skipping) {
                    return false;
                }

                if (_controlsVisible) {
                    hideControls();
                } else {
                    showControls();
                }

                return true;
            }

            override fun onDoubleTap(ev: MotionEvent): Boolean {
                if (_isControlsLocked || _skipping) {
                    return false;
                }

                val rewinding = (ev.x / width) < 0.5;
                startFastForward(rewinding);
                return true;
            }

            override fun onDoubleTapEvent(ev: MotionEvent): Boolean {
                return false;
            }
        });

        isClickable = true
    }

    fun updateSnappingVisibility() {
        if (willSnapFill()) {
            _layoutIndicatorFill.visibility = View.VISIBLE
            _layoutIndicatorFit.visibility = View.GONE
        } else if (willSnapFit()) {
            _layoutIndicatorFill.visibility = View.GONE
            _layoutIndicatorFit.visibility = View.VISIBLE

            _surfaceView?.let {
                val lp = _layoutIndicatorFit.layoutParams
                lp.width = it.width
                lp.height = it.height
                _layoutIndicatorFit.layoutParams = lp
            }
        } else {
            _layoutIndicatorFill.visibility = View.GONE
            _layoutIndicatorFit.visibility = View.GONE
        }
    }

    fun setZoomPanEnabled(view: View) {
        _isZoomPanEnabled = true
        _surfaceView = view
    }

    fun resetZoomPan() {
        _scaleFactor = 1.0f
        onZoom.emit(_scaleFactor)
        _translationX = 0f
        _translationY = 0f
        onPan.emit(_translationX, _translationY)
    }

    private fun pan(translationX: Float, translationY: Float) {
        val xc = width / 2.0f
        val yc = height / 2.0f

        val xmin = xc - width / 2.0f * _scaleFactor
        val xmax = xc + width / 2.0f * _scaleFactor - width

        val ymin = yc - height / 2.0f * _scaleFactor
        val ymax = yc + height / 2.0f * _scaleFactor - height

        _translationX = translationX.coerceAtLeast(xmin).coerceAtMost(xmax)
        _translationY = translationY.coerceAtLeast(ymin).coerceAtMost(ymax)

        onPan.emit(_translationX, _translationY)
    }

    fun setupTouchArea(layoutControls: ViewGroup? = null, background: View? = null) {
        _layoutControls = layoutControls;
        _background = background;
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val ev = event ?: return super.onTouchEvent(event);

        cancelHideJob();

        if (_skipping) {
            if (ev.action == MotionEvent.ACTION_UP) {
                startExitFastForward();
                stopAutoFastForward();
            } else if (ev.action == MotionEvent.ACTION_DOWN) {
                _jobExitFastForward?.cancel();
                _jobExitFastForward = null;

                startAutoFastForward();
                fastForwardTick();
            }
        }

        if (_adjustingSound && ev.action == MotionEvent.ACTION_UP) {
            stopAdjustingSound();
        }

        if (_adjustingBrightness && ev.action == MotionEvent.ACTION_UP) {
            stopAdjustingBrightness();
        }

        if (_adjustingFullscreenUp && ev.action == MotionEvent.ACTION_UP) {
            if (_fullScreenFactorUp > 0.5) {
                onToggleFullscreen.emit();
            }
            stopAdjustingFullscreenUp();
        }

        if (_adjustingFullscreenDown && ev.action == MotionEvent.ACTION_UP) {
            if (_fullScreenFactorDown > 0.5) {
                onToggleFullscreen.emit();
            }
            stopAdjustingFullscreenDown();
        }

        if ((_isPanning || _isZooming) && ev.action == MotionEvent.ACTION_UP) {
            val surfaceView = _surfaceView
            if (surfaceView != null && willSnapFill()) {
                _scaleFactor = calculateZoomScaleFactor()
                onZoom.emit(_scaleFactor)

                _translationX = 0f
                _translationY = 0f
                onPan.emit(_translationX, _translationY)
            } else if (willSnapFit()) {
                _scaleFactor = 1f
                onZoom.emit(_scaleFactor)

                _translationX = 0f
                _translationY = 0f
                onPan.emit(_translationX, _translationY)
            }

            _layoutControlsZoom.visibility = View.GONE
            _layoutIndicatorFill.visibility = View.GONE
            _layoutIndicatorFit.visibility = View.GONE
            _isZooming = false
            _isPanning = false
        }

        startHideJobIfNecessary();

        _gestureController.onTouchEvent(ev)
        _scaleGestureDetector.onTouchEvent(ev)
        return true;
    }

    private fun calculateZoomScaleFactor(): Float {
        val w = _surfaceView?.width?.toFloat() ?: return 1.0f;
        val h = _surfaceView?.height?.toFloat() ?: return 1.0f;
        if (w == 0.0f || h == 0.0f) {
            return 1.0f;
        }

        return Math.max(width / w, height / h)
    }

    private val _snapTranslationTolerance = 0.1f;
    private val _snapZoomTolerance = 0.1f;

    private fun willSnapFill(): Boolean {
        val surfaceView = _surfaceView
        if (surfaceView != null) {
            val zoomScaleFactor = calculateZoomScaleFactor()
            if (Math.abs(_scaleFactor - zoomScaleFactor) < _snapZoomTolerance && Math.abs(_translationX) / width < _snapTranslationTolerance && Math.abs(_translationY) / height < _snapTranslationTolerance) {
                return true
            }
        }

        return false
    }

    private fun willSnapFit(): Boolean {
        return Math.abs(_scaleFactor - 1.0f) < _snapZoomTolerance && Math.abs(_translationX) / width < _snapTranslationTolerance && Math.abs(_translationY) / height < _snapTranslationTolerance
    }

    fun cancelHideJob() {
        _jobHideControls?.cancel();
        _jobHideControls = null;
    }

    private fun startHideJob() {
        _jobHideControls = _scope.launch(Dispatchers.Main) {
            try {
                ensureActive();
                delay(3000);
                ensureActive();

                hideControls();
            } catch (e: Throwable) {
                if(e !is CancellationException)
                    Logger.e(TAG, "Failed to hide controls", e);
            }
        };
    }

    fun startHideJobIfNecessary() {
        if (_controlsVisible) {
            startHideJob();
        }
    }

    fun restartHideJob() {
        cancelHideJob();
        startHideJobIfNecessary();
    }

    fun showControls(withHideJob: Boolean = true){
        Logger.i(TAG, "showControls()");

        if (_isControlsLocked)
            return;

        if (_controlsVisible) {
            return;
        }

        _animatorSetControls?.cancel();

        val animations = arrayListOf<Animator>();
        _layoutControls?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 0.0f, 1.0f).setDuration(
            ANIMATION_DURATION_CONTROLS
        )); };
        _background?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 0.0f, 1.0f).setDuration(
            ANIMATION_DURATION_CONTROLS
        )); };

        val animatorSet = AnimatorSet();
        animatorSet.doOnStart {
            _background?.visibility = View.VISIBLE;
            _layoutControls?.visibility = View.VISIBLE;
        };
        animatorSet.doOnEnd {
            _animatorSetControls = null;
        };
        animatorSet.playTogether(animations);
        animatorSet.start();
        _animatorSetControls = animatorSet;

        _controlsVisible = true
        if (withHideJob) {
            startHideJobIfNecessary();
        } else {
            cancelHideJob();
        }
    }

    fun hideControls(animate: Boolean = true){
        Logger.v(TAG, "hideControls(animate: $animate)");

        if (!_controlsVisible) {
            return;
        }

        stopFastForward();

        _animatorSetControls?.cancel();

        if (animate) {
            val animations = arrayListOf<Animator>();
            _layoutControls?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 1.0f, 0.0f).setDuration(
                ANIMATION_DURATION_CONTROLS
            )); };
            _background?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 1.0f, 0.0f).setDuration(
                ANIMATION_DURATION_CONTROLS
            )); };

            val animatorSet = AnimatorSet();
            animatorSet.doOnEnd {
                _background?.visibility = View.GONE;
                _layoutControls?.visibility = View.GONE;
                _animatorSetControls = null;
            };

            animatorSet.playTogether(animations);
            animatorSet.start();

            _animatorSetControls = animatorSet;
        } else {
            _background?.visibility = View.GONE;
            _layoutControls?.visibility = View.GONE;
        }

        _controlsVisible = false;
    }

    fun stopAllGestures() {
        stopAdjustingFullscreenDown()
        stopAdjustingBrightness()
        stopAdjustingSound()
        stopAdjustingFullscreenUp()
        stopFastForward()
        stopAutoFastForward()
    }

    fun cleanup() {
        _jobExitFastForward?.cancel();
        _jobExitFastForward = null;
        _jobAutoFastForward?.cancel();
        _jobAutoFastForward = null;
        stopAllGestures();
        cancelHideJob();
        _scope.cancel();
    }

    private fun startFastForward(rewinding: Boolean) {
        _skipping = true;
        _rewinding = rewinding;
        _fastForwardCounter = 0;

        fastForwardTick();
        startAutoFastForward();

        _animatorSetFastForward?.cancel();

        val animations = arrayListOf<Animator>();
        val layout = if (rewinding) { _layoutRewind } else { _layoutFastForward };
        animations.add(ObjectAnimator.ofFloat(layout, "alpha", 0.0f, 1.0f).setDuration(
            ANIMATION_DURATION_FAST_FORWARD
        ));

        if (_controlsVisible) {
            _layoutControls?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 1.0f, 0.0f).setDuration(
                ANIMATION_DURATION_FAST_FORWARD
            )); };
        } else {
            _background?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 0.0f, 1.0f).setDuration(
                ANIMATION_DURATION_FAST_FORWARD
            )); };
        }

        val animatorSet = AnimatorSet();
        animatorSet.doOnStart {
            _background?.visibility = View.VISIBLE;
            layout.visibility = View.VISIBLE;
        };
        animatorSet.doOnEnd {
            _animatorSetFastForward = null;
            if (_controlsVisible) {
                _layoutControls?.visibility = View.GONE;
            }
        };
        animatorSet.playTogether(animations);
        animatorSet.start();
        _animatorSetFastForward = animatorSet;

        if (rewinding) {
            (_imageRewind.drawable as Animatable?)?.start();
        } else {
            (_imageFastForward.drawable as Animatable?)?.start();
        }
    }
    private fun stopFastForward() {
        _jobExitFastForward?.cancel();
        _jobExitFastForward = null;
        stopAutoFastForward();

        _animatorSetFastForward?.cancel();

        val animations = arrayListOf<Animator>();
        val layout = if (_rewinding) { _layoutRewind } else { _layoutFastForward };
        animations.add(ObjectAnimator.ofFloat(layout, "alpha", 1.0f, 0.0f).setDuration(
            ANIMATION_DURATION_FAST_FORWARD
        ));

        if (_controlsVisible) {
            _layoutControls?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 0.0f, 1.0f).setDuration(
                ANIMATION_DURATION_FAST_FORWARD
            )); };
        } else {
            _background?.let { animations.add(ObjectAnimator.ofFloat(it, "alpha", 1.0f, 0.0f).setDuration(
                ANIMATION_DURATION_FAST_FORWARD
            )); };
        }

        val animatorSet = AnimatorSet();
        animatorSet.doOnStart {
            if (_controlsVisible) {
                _layoutControls?.visibility = View.VISIBLE;
            }
        };
        animatorSet.doOnEnd {
            layout.visibility = View.GONE;
            _animatorSetFastForward = null;
            (_imageRewind.drawable as Animatable?)?.stop();
            (_imageFastForward.drawable as Animatable?)?.stop();
        };

        animatorSet.playTogether(animations);
        animatorSet.start();
        _animatorSetFastForward = animatorSet;

        _skipping = false;
    }
    private fun fastForwardTick() {
        _fastForwardCounter++;

        val seekOffset: Long = 10000;
        if (_rewinding) {
            _textRewind.text = "${_fastForwardCounter * 10} " + context.getString(R.string.seconds);
            onSeek.emit(-seekOffset);
        } else {
            _textFastForward.text = "${_fastForwardCounter * 10} " + context.getString(R.string.seconds);
            onSeek.emit(seekOffset);
        }
    }
    private fun startAutoFastForward() {
        _jobAutoFastForward?.cancel();
        _jobAutoFastForward = _scope.launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    ensureActive();
                    delay(300);
                    ensureActive();

                    fastForwardTick();
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to execute fast forward tick.", e);
            }
        };
    }
    private fun startExitFastForward() {
        _jobExitFastForward?.cancel();
        _jobExitFastForward = _scope.launch(Dispatchers.Main) {
            try {
                delay(600);
                stopFastForward();
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to stop fast forward.", e)
            }
        };
    }
    private fun stopAutoFastForward() {
        _jobAutoFastForward?.cancel();
        _jobAutoFastForward = null;
    }

    private fun startAdjustingSound() {
        _adjustingSound = true;
        _progressSound.progress = _soundFactor;

        _layoutControlsSound.visibility = View.VISIBLE;
        _animatorSound?.cancel();
        _animatorSound = ObjectAnimator.ofFloat(_layoutControlsSound, "alpha", 0.0f, 1.0f);
        _animatorSound?.duration = ANIMATION_DURATION_GESTURE_CONTROLS;
        _animatorSound?.start();
    }

    private fun stopAdjustingSound() {
        _adjustingSound = false;

        _animatorSound?.cancel();
        _animatorSound = ObjectAnimator.ofFloat(_layoutControlsSound, "alpha", 1.0f, 0.0f);
        _animatorSound?.duration = ANIMATION_DURATION_GESTURE_CONTROLS;
        _animatorSound?.doOnEnd { _layoutControlsSound.visibility = View.GONE; };
        _animatorSound?.start();
    }

    private fun startAdjustingFullscreenUp() {
        _adjustingFullscreenUp = true;
        _fullScreenFactorUp = 0f;
        _layoutControlsFullscreen.alpha = 0f;
        _layoutControlsFullscreen.visibility = View.VISIBLE;
    }

    private fun stopAdjustingFullscreenUp() {
        _adjustingFullscreenUp = false;
        _layoutControlsFullscreen.visibility = View.GONE;
    }

    private fun startAdjustingFullscreenDown() {
        _adjustingFullscreenDown = true;
        _fullScreenFactorDown = 0f;
        _layoutControlsFullscreen.alpha = 0f;
        _layoutControlsFullscreen.visibility = View.VISIBLE;
    }

    private fun stopAdjustingFullscreenDown() {
        _adjustingFullscreenDown = false;
        _layoutControlsFullscreen.visibility = View.GONE;
    }

    private fun startAdjustingBrightness() {
        _adjustingBrightness = true;
        _progressBrightness.progress = _brightnessFactor;

        _layoutControlsBrightness.visibility = View.VISIBLE;
        _animatorBrightness?.cancel();
        _animatorBrightness = ObjectAnimator.ofFloat(_layoutControlsBrightness, "alpha", 0.0f, 1.0f);
        _animatorBrightness?.duration = ANIMATION_DURATION_GESTURE_CONTROLS;
        _animatorBrightness?.start();
    }

    private fun stopAdjustingBrightness() {
        _adjustingBrightness = false;

        _animatorBrightness?.cancel();
        _animatorBrightness = ObjectAnimator.ofFloat(_layoutControlsBrightness, "alpha", 1.0f, 0.0f);
        _animatorBrightness?.duration = ANIMATION_DURATION_GESTURE_CONTROLS;
        _animatorBrightness?.doOnEnd { _layoutControlsBrightness.visibility = View.GONE; };
        _animatorBrightness?.start();
    }

    fun setFullscreen(isFullScreen: Boolean) {
        resetZoomPan()

        if (isFullScreen) {
            if (Settings.instance.gestureControls.useSystemBrightness) {
                try {
                    _originalBrightnessMode = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE)

                    val brightness = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
                    _brightnessFactor = brightness / 255.0f;
                    Log.i(TAG, "Starting brightness brightness: $brightness, _brightnessFactor: $_brightnessFactor, _originalBrightnessMode: $_originalBrightnessMode")

                    _originalBrightnessFactor = _brightnessFactor
                } catch (e: Throwable) {
                    Settings.instance.gestureControls.useSystemBrightness = false
                    Settings.instance.save()
                    UIDialogs.toast(context, "useSystemBrightness disabled due to an error")
                }
            }

            if (Settings.instance.gestureControls.useSystemVolume) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                _soundFactor = currentVolume.toFloat() / maxVolume.toFloat()
            }

            onBrightnessAdjusted.emit(_brightnessFactor);
            onSoundAdjusted.emit(_soundFactor);
        } else {
            if (Settings.instance.gestureControls.useSystemBrightness) {
                if (Settings.instance.gestureControls.restoreSystemBrightness) {
                    onBrightnessAdjusted.emit(_originalBrightnessFactor)

                    if (android.provider.Settings.System.canWrite(context)) {
                        Log.i(TAG, "Restoring system brightness mode _originalBrightnessMode: $_originalBrightnessMode")

                        android.provider.Settings.System.putInt(
                            context.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                            _originalBrightnessMode
                        )
                    }
                }
            } else {
                onBrightnessAdjusted.emit(1.0f);
            }
            //onSoundAdjusted.emit(1.0f);
            stopAdjustingBrightness();
            stopAdjustingSound();
            stopAdjustingFullscreenUp();
        }

        _isFullScreen = isFullScreen;
    }

    fun setSoundFactor(soundFactor: Float) {
        _soundFactor = soundFactor;
        onSoundAdjusted.emit(_soundFactor);
    }

    companion object {
        const val ANIMATION_DURATION_GESTURE_CONTROLS: Long = 200;
        const val ANIMATION_DURATION_CONTROLS: Long = 400;
        const val ANIMATION_DURATION_FAST_FORWARD: Long = 400;
        const val EXIT_DURATION_FAST_FORWARD: Long = 600;
        const val TAG = "GestureControlView";
    }
}