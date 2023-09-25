package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.lists.VideoListEditorView

class OverlayTopbar : ConstraintLayout {

    private val _name: TextView;
    private val _meta: TextView;

    private val _button_close: ImageView;

    val onClose = Event0();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_topbar, this);

        _name = findViewById(R.id.text_name);
        _meta = findViewById(R.id.text_meta);
        _button_close = findViewById(R.id.button_close);

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.OverlayTopbar, 0, 0);
        val attrText = attrArr.getText(R.styleable.OverlayTopbar_title) ?: "";
        _name.text = attrText;

        val attrMetaText = attrArr.getText(R.styleable.OverlayTopbar_metadata) ?: "";
        _meta.text = attrMetaText;

        _button_close.setOnClickListener {
            onClose.emit();
        };
    }


    fun setInfo(name: String, meta: String) {
        _name.text = name;
        _meta.text = meta;
    }
}