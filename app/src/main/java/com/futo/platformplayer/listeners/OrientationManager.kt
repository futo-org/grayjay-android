package com.futo.platformplayer.listeners

import android.content.Context
import android.view.OrientationEventListener
import com.futo.platformplayer.constructs.Event1

class OrientationManager : OrientationEventListener {

    val onOrientationChanged = Event1<Orientation>();

    var orientation : Orientation = Orientation.PORTRAIT;

    constructor(context: Context) : super(context) { }
    constructor(context: Context, rate: Int) : super(context, rate) { }
    init {

    }

    override fun onOrientationChanged(orientationAnglep: Int) {
        if(orientationAnglep == -1)
            return;

        var newOrientation = Orientation.PORTRAIT;
        if(orientationAnglep > 60 && orientationAnglep < 140)
            newOrientation = Orientation.REVERSED_LANDSCAPE;
        else if(orientationAnglep >= 140 && orientationAnglep <= 220)
            newOrientation = Orientation.REVERSED_PORTRAIT;
        else if(orientationAnglep >= 220 && orientationAnglep <= 300)
            newOrientation = Orientation.LANDSCAPE;
        else
            newOrientation = Orientation.PORTRAIT;

        if(newOrientation != orientation) {
            orientation = newOrientation;
            onOrientationChanged.emit(newOrientation);
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