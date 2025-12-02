package com.futo.platformplayer.views.buttons

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

open class BigButton : LinearLayout {
    private val _root: LinearLayout;
    private val _icon: ShapeableImageView;
    private val _textPrimary: TextView;
    private val _textSecondary: TextView;

    val title: String get() = _textPrimary.text.toString();
    val description: String get() = _textSecondary.text.toString();

    val onClick = Event0();

    constructor(context : Context, text: String, subText: String, icon: Int, action: ()->Unit) : super(context) {
        inflate(context, R.layout.big_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _textSecondary = findViewById(R.id.button_sub_text);
        _root = findViewById(R.id.root);

        _textPrimary.text = text;
        _textSecondary.text = subText;
        _icon.setImageResource(icon);

        _root.setBackgroundResource(R.drawable.background_big_button);

        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                action();
                onClick.emit();
            };
        }
    }
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.big_button, this);
        _icon = findViewById(R.id.button_icon);
        _textPrimary = findViewById(R.id.button_text);
        _textSecondary = findViewById(R.id.button_sub_text);
        _root = findViewById(R.id.root);
        _root.apply {
            isClickable = true;
            setOnClickListener {
                if(!isEnabled)
                    return@setOnClickListener;
                onClick.emit();
            };
        }

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.BigButton, 0, 0);
        val attrIconRef = attrArr.getResourceId(R.styleable.BigButton_buttonIcon, -1);
        val attrBackgroundRef = attrArr.getResourceId(R.styleable.BigButton_buttonBackground, -1);
        val attrText = attrArr.getText(R.styleable.BigButton_buttonText) ?: "";
        val attrTextSecondary = attrArr.getText(R.styleable.BigButton_buttonSubText) ?: "";
        attrArr.recycle()

        withIcon(attrIconRef);
        withBackground(attrBackgroundRef);
        _textPrimary.text = attrText;
        _textSecondary.text = attrTextSecondary;
    }

    fun withMargin(bottom: Int, side: Int = 0): BigButton {
        setPadding(side, 0, side, bottom)
        return this;
    }

    fun setSecondaryText(text: String?) {
        _textSecondary.text = text
    }

    fun withPrimaryText(text: String): BigButton {
        _textPrimary.text = text;
        return this;
    }

    fun withSecondaryText(text: String): BigButton {
        _textSecondary.text = text;
        return this;
    }
    fun withSecondaryTextMaxLines(lines: Int): BigButton {
        _textSecondary.maxLines = lines;
        return this;
    }

    private fun applyIcon(resourceId: Int, rounded: Boolean) {
        if (resourceId != -1) {
            _icon.visibility = View.VISIBLE
            _icon.setImageResource(resourceId)
        } else {
            _icon.visibility = View.GONE
        }
        applyRounded(rounded)
    }

    fun withIcon(resourceId: Int, rounded: Boolean = false): BigButton {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyIcon(resourceId, rounded)
        } else {
            post { applyIcon(resourceId, rounded) }
        }
        return this
    }

    fun withIcon(bitmap: Bitmap, rounded: Boolean = false): BigButton {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyIcon(bitmap, rounded)
        } else {
            post { applyIcon(bitmap, rounded) }
        }
        return this
    }

    private fun applyRounded(rounded: Boolean) {
        if (rounded) {
            val radiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16.0f,
                context.resources.displayMetrics
            )
            val shapeAppearanceModel = ShapeAppearanceModel()
                .toBuilder()
                .setAllCornerSizes(radiusPx)
                .build()

            _icon.scaleType = ImageView.ScaleType.FIT_CENTER
            _icon.shapeAppearanceModel = shapeAppearanceModel
        } else {
            _icon.scaleType = ImageView.ScaleType.CENTER_CROP
            _icon.shapeAppearanceModel = ShapeAppearanceModel()
        }
    }

    private fun applyIcon(bitmap: Bitmap, rounded: Boolean) {
        _icon.visibility = View.VISIBLE
        _icon.setImageBitmap(bitmap)
        applyRounded(rounded)
    }

    fun withBackground(resourceId: Int): BigButton {
        if (resourceId != -1) {
            _root.visibility = View.VISIBLE;
            _root.setBackgroundResource(resourceId);
        } else
            _root.setBackgroundResource(R.drawable.background_big_button);

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