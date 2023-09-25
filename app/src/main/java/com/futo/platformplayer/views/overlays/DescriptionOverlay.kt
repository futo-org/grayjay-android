package com.futo.platformplayer.views.overlays

import android.content.Context
import android.text.Spanned
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod

class DescriptionOverlay : LinearLayout {
    val onClose = Event0();

    private val _topbar: OverlayTopbar;
    private val _textDescription: TextView;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_description, this)
        _topbar = findViewById(R.id.topbar);
        _textDescription = findViewById(R.id.text_description);

        _topbar.onClose.subscribe(this, onClose::emit);

        _textDescription.setPlatformPlayerLinkMovementMethod(context);
    }

    fun load(text: Spanned?) {
        _textDescription.text = text;
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
    }
}