package com.futo.platformplayer.views.platform

import android.content.Context
import android.util.AttributeSet
import com.futo.platformplayer.states.StatePlatform

class PlatformIndicator : androidx.appcompat.widget.AppCompatImageView {
    constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
    }

    fun clearPlatform() {
        setImageDrawable(null);
    }
    fun setPlatformFromClientID(platformType : String?) {
        if(platformType == null)
            setImageDrawable(null);
        else {
            val result = StatePlatform.instance.getPlatformIcon(platformType);
            if (result != null)
                result.setImageView(this);
            else
                setImageDrawable(null);
        }
    }
    fun setPlatformFromClientName(name: String?) {
        if(name == null)
            setImageDrawable(null);
        else {
            val result = StatePlatform.instance.getPlatformIconByName(name);
            if (result != null)
                result.setImageView(this);
            else
                setImageDrawable(null);
        }
    }
}