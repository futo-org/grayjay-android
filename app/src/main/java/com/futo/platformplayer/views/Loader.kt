package com.futo.platformplayer.views

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.futo.platformplayer.R

class Loader : LinearLayout {
    private val _imageLoader: ImageView;
    private val _automatic: Boolean;
    private val _animatable: Animatable;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_loader, this, true);
        _imageLoader = findViewById(R.id.image_loader);
        _animatable = _imageLoader.drawable as Animatable;

        if (attrs != null) {
            val attrArr = context.obtainStyledAttributes(attrs, R.styleable.LoaderView, 0, 0);
            _automatic = attrArr.getBoolean(R.styleable.LoaderView_automatic, false);
            attrArr.recycle();
        } else {
            _automatic = false;
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