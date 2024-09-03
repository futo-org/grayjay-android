package com.futo.platformplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.hardware.SensorManager
import android.view.OrientationEventListener
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SimpleOrientationListener(
    private val activity: Activity,
    private val lifecycleScope: CoroutineScope
) {
    private var lastOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lastStableOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val stabilityThresholdTime = 500L

    val onOrientationChanged = Event1<Int>()

    private val orientationListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) {
            val newOrientation = when {
                orientation in 45..134 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                orientation in 135..224 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                orientation in 225..314 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                orientation in 315..360 || orientation in 0..44 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else -> lastOrientation
            }

            if (newOrientation != lastStableOrientation) {
                lastStableOrientation = newOrientation

                lifecycleScope.launch(Dispatchers.Main) {
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
        orientationListener.disable()
    }

    companion object {
        private val TAG = "SimpleOrientationListener"
    }
}