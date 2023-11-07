package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.views.SupportView

class SupportOverlay : LinearLayout {
    val onClose = Event0();

    private val _topbar: OverlayTopbar;
    private val _support: SupportView;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_support, this)
        _topbar = findViewById(R.id.topbar);
        _support = findViewById(R.id.support);

        _topbar.onClose.subscribe(this, onClose::emit);
    }


    fun setPolycentricProfile(profile: PolycentricProfile?, animate: Boolean) {
        _support.setPolycentricProfile(profile, animate)
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
    }
}