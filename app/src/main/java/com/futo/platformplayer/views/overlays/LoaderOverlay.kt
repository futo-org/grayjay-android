package com.futo.platformplayer.views.overlays

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.futo.platformplayer.R

class LoaderOverlay(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    private val _container: FrameLayout;
    private val _loader: ImageView;

    init {
        inflate(context, R.layout.overlay_loader, this);
        _container = findViewById(R.id.container);
        _loader = findViewById(R.id.loader);

        val centerLoader: Boolean;
        if (attrs != null) {
            val attrArr = context.obtainStyledAttributes(attrs, R.styleable.LoaderOverlay, 0, 0);
            centerLoader = attrArr.getBoolean(R.styleable.LoaderOverlay_centerLoader, false);
            attrArr.recycle();
        } else {
            centerLoader = false;
        }

        if (centerLoader) {
            (_loader.layoutParams as LayoutParams).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    fun show() {
        this.visibility = View.VISIBLE;
        (_loader.drawable as Animatable?)?.start();
    }
    fun hide() {
        this.visibility = View.GONE;
        (_loader.drawable as Animatable?)?.stop();
    }
}