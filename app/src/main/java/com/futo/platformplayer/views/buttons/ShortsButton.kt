package com.futo.platformplayer.views.buttons

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event0
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

class ShortsButton : LinearLayout {
    private val _root: LinearLayout;
    private val _icon: ImageView;
    private val _textPrimary: TextView;
    val onClick = Event0();

    var iconId: Int? = null;

    constructor(context : Context, text: String, icon: Int, action: ()->Unit) : super(context) {
        inflate(context, R.layout.view_shorts_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _root = findViewById(R.id.root);

        withPrimaryText(text);
        withIcon(icon);

        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                action();
                onClick.emit();
                UIDialogs.toast("Clicked button: " + _textPrimary.text);
            };
        }
    }
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_shorts_button, this);
        _icon = findViewById(R.id.image_icon);
        _textPrimary = findViewById(R.id.text_title);
        _root = findViewById(R.id.root);
        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                onClick.emit();
            };
        }

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.ShortsButton, 0, 0);
        val attrIconRef = attrArr.getResourceId(R.styleable.ShortsButton_buttonIcon_s, -1);
        val attrText = attrArr.getText(R.styleable.ShortsButton_buttonText_s) ?: "";
        attrArr.recycle()

        withIcon(attrIconRef);
        withPrimaryText(attrText.toString());
    }

    fun withMargin(bottom: Int, side: Int = 0): ShortsButton {
        setPadding(side, 0, side, bottom)
        return this;
    }
    fun withPrimaryText(text: String): ShortsButton {
        _textPrimary.text = text;

        if(text.isNullOrBlank())
            _textPrimary.visibility = View.GONE;
        else
            _textPrimary.visibility = View.VISIBLE;
        return this;
    }

    fun withIcon(resourceId: Int): ShortsButton {
        if (resourceId != -1) {
            _icon.visibility = View.VISIBLE;
            _icon.setImageResource(resourceId);
        } else
            _icon.visibility = View.GONE;
        _icon.scaleType = ImageView.ScaleType.CENTER_CROP;
        iconId = resourceId;

        return this;
    }


    fun withIcon(bitmap: Bitmap): ShortsButton {
        _icon.visibility = View.VISIBLE;
        _icon.setImageBitmap(bitmap);
        iconId = -1;

        _icon.scaleType = ImageView.ScaleType.CENTER_CROP;

        return this;
    }

    fun setButtonEnabled(enabled: Boolean) {
        if(enabled) {
            alpha = 1f;
            isEnabled = true;
            isClickable = true;
        }
        else {
            alpha = 0.5f;
            isEnabled = false;
            isClickable = false;
        }
    }
}