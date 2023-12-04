package com.futo.platformplayer.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import com.futo.platformplayer.R

class LoaderView : LinearLayout {
    private val _imageLoader: ImageView;
    private val _automatic: Boolean;
    private var _isWhite: Boolean;
    private val _animatable: Animatable;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_loader, this);
        _imageLoader = findViewById(R.id.image_loader);
        _animatable = _imageLoader.drawable as Animatable;

        if (attrs != null) {
            val attrArr = context.obtainStyledAttributes(attrs, R.styleable.LoaderView, 0, 0);
            _automatic = attrArr.getBoolean(R.styleable.LoaderView_automatic, false);
            _isWhite = attrArr.getBoolean(R.styleable.LoaderView_isWhite, false);
            attrArr.recycle();
        } else {
            _automatic = false;
            _isWhite = false;
        }

        visibility = View.GONE;

        if (_isWhite) {
            _imageLoader.setColorFilter(Color.WHITE)
        }
    }
    constructor(context: Context, automatic: Boolean, height: Int = -1, isWhite: Boolean = false) : super(context) {
        inflate(context, R.layout.view_loader, this);
        _imageLoader = findViewById(R.id.image_loader);
        _animatable = _imageLoader.drawable as Animatable;
        _automatic = automatic;
        _isWhite = isWhite;

        if(height > 0) {
            layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, height);
        }

        visibility = View.GONE;
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (_automatic) {
            start();
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (_automatic) {
            stop();
        }
    }

    fun start() {
        _animatable.start();
        visibility = View.VISIBLE;
    }

    fun stop() {
        _animatable.stop();
        visibility = View.GONE;
    }
}