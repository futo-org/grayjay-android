package com.futo.platformplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.hardware.SensorManager
import android.view.OrientationEventListener
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SimpleOrientationListener(
    private val activity: Activity,
    private val lifecycleScope: CoroutineScope
) {
    private var lastOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lastStableOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var _currentJob: Job? = null

    val onOrientationChanged = Event1<Int>()

    private val orientationListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) {
            //val rotationZone = 45
            val stabilityThresholdTime = when (Settings.instance.playback.stabilityThresholdTime) {
                0 -> 100L
                1 -> 500L
                2 -> 750L
                3 -> 1000L
                4 -> 1500L
                5 -> 2000L
                else -> 500L
            }

            val rotationZone = when (Settings.instance.playback.rotationZone) {
                0 -> 15
                1 -> 30
                2 -> 45
                else -> 45
            }

            val newOrientation = when {
                orientation in (90 - rotationZone)..(90 + rotationZone - 1) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                orientation in (180 - rotationZone)..(180 + rotationZone - 1) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                orientation in (270 - rotationZone)..(270 + rotationZone - 1) -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                orientation in (360 - rotationZone)..(360 + rotationZone - 1) || orientation in 0..(rotationZone - 1) -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else -> lastOrientation
            }

            if (newOrientation != lastStableOrientation) {
                lastStableOrientation = newOrientation

                _currentJob?.cancel()
                _currentJob = lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        delay(stabilityThresholdTime)
                        if (newOrientation == lastStableOrientation) {
                            lastOrientation = newOrientation
                            onOrientationChanged.emit(newOrientation)
                        }
                    } catch (e: Throwable) {
                        Logger.i(TAG, "Failed to trigger onOrientationChanged", e)
                    }
                }
            }
        }
    }

    init {
        orientationListener.enable()
        lastOrientation = activity.resources.configuration.orientation
    }

    fun stopListening() {
        _currentJob?.cancel()
        _currentJob = null
        orientationListener.disable()
    }

    companion object {
        private val TAG = "SimpleOrientationListener"
    }
}