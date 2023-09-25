package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R


class CircularProgressBar : View {
    private val _paintActive = Paint(Paint.ANTI_ALIAS_FLAG);
    private val _paintInactive = Paint(Paint.ANTI_ALIAS_FLAG);
    private val _path = Path();

    var progress: Float = 0.0f
            set(value) {
                field = value;
                invalidate();
            };

    var strokeWidth: Float
        get() {
            return _paintInactive.strokeWidth;
        }
        set(value) {
            _paintActive.strokeWidth = value;
            _paintInactive.strokeWidth = value;
            invalidate();
        };

    var activeColor: Int
        get() {
            return _paintActive.color;
        }
        set(value) {
            _paintActive.color = value;
            invalidate();
        };
    var inactiveColor: Int
        get() {
            return _paintInactive.color;
        }
        set(value) {
            _paintInactive.color = value;
            invalidate();
        };

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        _paintActive.style = Paint.Style.STROKE;
        _paintInactive.style = Paint.Style.STROKE;

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ProgressBar, 0, 0);
        try {
            progress = a.getFraction(R.styleable.ProgressBar_progress, 1, 1, 0.0f);
            _paintActive.color = a.getColor(R.styleable.ProgressBar_activeColor, ContextCompat.getColor(context, R.color.colorPrimary));
            _paintInactive.color = a.getColor(R.styleable.ProgressBar_inactiveColor, ContextCompat.getColor(context, R.color.gray_c3));
        } finally {
            a.recycle();
        }

        val b = context.theme.obtainStyledAttributes(attrs, R.styleable.CircularProgressBar, 0, 0);
        try {
            strokeWidth = b.getDimensionPixelSize(R.styleable.CircularProgressBar_strokeWidth, 10).toFloat();
        } finally {
            b.recycle();
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);

        val w = width.toFloat();
        val h = height.toFloat();
        val size = Math.min(w, h) - strokeWidth;
        val paddingLeft = (w - size) / 2;
        val paddingTop = (h - size) / 2;

        _path.reset();
        _path.addArc(paddingLeft, paddingTop, paddingLeft + size,  paddingTop + size, 90.0f, 360.0f);
        canvas.drawPath(_path, _paintInactive);

        _path.reset();
        _path.addArc(paddingLeft, paddingTop, paddingLeft + size,  paddingTop + size, 90.0f, progress * 360.0f);
        canvas.drawPath(_path, _paintActive);
    }

    companion object {
        val TAG = "ProgressBar";
    }
}