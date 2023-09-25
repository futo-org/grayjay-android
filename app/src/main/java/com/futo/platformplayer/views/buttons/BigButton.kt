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
import com.futo.platformplayer.constructs.Event0
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

class BigButton : LinearLayout {
    private val _root: LinearLayout;
    private val _icon: ShapeableImageView;
    private val _textPrimary: TextView;
    private val _textSecondary: TextView;

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
                onClick.emit();
            };
        }

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.BigButton, 0, 0);
        val attrIconRef = attrArr.getResourceId(R.styleable.BigButton_buttonIcon, -1);
        withIcon(attrIconRef);

        val attrBackgroundRef = attrArr.getResourceId(R.styleable.BigButton_buttonBackground, -1);
        withBackground(attrBackgroundRef);

        val attrText = attrArr.getText(R.styleable.BigButton_buttonText) ?: "";
        _textPrimary.text = attrText;

        val attrTextSecondary = attrArr.getText(R.styleable.BigButton_buttonSubText) ?: "";
        _textSecondary.text = attrTextSecondary;
    }

    fun withPrimaryText(text: String): BigButton {
        _textPrimary.text = text;
        return this;
    }

    fun withSecondaryText(text: String): BigButton {
        _textSecondary.text = text;
        return this;
    }

    fun withIcon(resourceId: Int, rounded: Boolean = false): BigButton {
        if (resourceId != -1) {
            _icon.visibility = View.VISIBLE;
            _icon.setImageResource(resourceId);
        } else
            _icon.visibility = View.GONE;

        if (rounded) {
            val shapeAppearanceModel = ShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, context.resources.displayMetrics))
                .build();

            _icon.scaleType = ImageView.ScaleType.FIT_CENTER;
            _icon.shapeAppearanceModel = shapeAppearanceModel;
        } else {
            _icon.scaleType = ImageView.ScaleType.CENTER_CROP;
            _icon.shapeAppearanceModel = ShapeAppearanceModel();
        }

        return this;
    }


    fun withIcon(bitmap: Bitmap, rounded: Boolean = false): BigButton {
        if (bitmap != null) {
            _icon.visibility = View.VISIBLE;
            _icon.setImageBitmap(bitmap);
        } else
            _icon.visibility = View.GONE;

        if (rounded) {
            val shapeAppearanceModel = ShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0f, context.resources.displayMetrics))
                .build();

            _icon.scaleType = ImageView.ScaleType.FIT_CENTER;
            _icon.shapeAppearanceModel = shapeAppearanceModel;
        } else {
            _icon.scaleType = ImageView.ScaleType.CENTER_CROP;
            _icon.shapeAppearanceModel = ShapeAppearanceModel();
        }

        return this;
    }

    fun withBackground(resourceId: Int): BigButton {
        if (resourceId != -1) {
            _root.visibility = View.VISIBLE;
            _root.setBackgroundResource(resourceId);
        } else
            _root.setBackgroundResource(R.drawable.background_big_button);

        return this;
    }
}