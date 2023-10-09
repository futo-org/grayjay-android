package com.futo.platformplayer.listeners

import android.content.Context
import android.view.OrientationEventListener
import com.futo.platformplayer.Settings
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger

class OrientationManager : OrientationEventListener {

    val onOrientationChanged = Event1<Orientation>();

    var orientation : Orientation = Orientation.PORTRAIT;

    constructor(context: Context) : super(context) { }

    //TODO: Something weird is going on here
    //TODO: Old implementation felt pretty good for me, but now with 0 deadzone still feels bad, even though code should be identical?
    override fun onOrientationChanged(orientationAnglep: Int) {
        if (orientationAnglep == -1) return

        val deadZone = Settings.instance.playback.getAutoRotateDeadZoneDegrees()
        val isInDeadZone = when (orientation) {
            Orientation.PORTRAIT -> orientationAnglep in 0 until (60 - deadZone) || orientationAnglep in (300 + deadZone) .. 360
            Orientation.REVERSED_LANDSCAPE -> orientationAnglep in (60 + deadZone) until (140 - deadZone)
            Orientation.REVERSED_PORTRAIT -> orientationAnglep in (140 + deadZone) until (220 - deadZone)
            Orientation.LANDSCAPE -> orientationAnglep in (220 + deadZone) until (300 - deadZone)
        }

        if (isInDeadZone) {
            return;
        }

        val newOrientation = when (orientationAnglep) {
            in 60 until 140 -> Orientation.REVERSED_LANDSCAPE
            in 140 until 220 -> Orientation.REVERSED_PORTRAIT
            in 220 until 300 -> Orientation.LANDSCAPE
            else -> Orientation.PORTRAIT
        }

        Logger.i("OrientationManager", "Orientation=$newOrientation orientationAnglep=$orientationAnglep");

        if (newOrientation != orientation) {
            orientation = newOrientation
            onOrientationChanged.emit(newOrientation)
        }
    }

    //TODO: Perhaps just use ActivityInfo orientations instead..
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
        REVERSED_PORTRAIT,
        REVERSED_LANDSCAPE
    }
}