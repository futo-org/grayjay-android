package com.futo.platformplayer.views.platform

import android.content.Context
import android.util.AttributeSet
import com.futo.platformplayer.states.StatePlatform

class PlatformIndicator : androidx.appcompat.widget.AppCompatImageView {
    constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
    }

    fun clearPlatform() {
        setImageResource(0);
    }
    fun setPlatformFromClientID(platformType : String?) {
        if(platformType == null)
            setImageResource(0);
        else {
            val result = StatePlatform.instance.getPlatformIcon(platformType);
            if (result != null)
                result.setImageView(this);
            else
                setImageResource(0);
        }
    }
    fun setPlatformFromClientName(name: String?) {
        if(name == null)
            setImageResource(0);
        else {
            val result = StatePlatform.instance.getPlatformIconByName(name);
            if (result != null)
                result.setImageView(this);
            else
                setImageResource(0);
        }
    }
}