package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R


class ProgressBar : View {
    private val _paintActive = Paint(Paint.ANTI_ALIAS_FLAG);
    private val _paintInactive = Paint(Paint.ANTI_ALIAS_FLAG);
    private val _path = Path();
    private val _progressRect = RectF();

    var progress: Float = 0.0f
            set(value) {
                field = value;
                invalidate();
            };

    var radiusBottomLeft: Float = 0.0f
        set(value) {
            field = value;
            updateCornerRadii();


        };
    var radiusBottomRight: Float = 0.0f
        set(value) {
            field = value;
            updateCornerRadii();
        };
    var radiusTopLeft: Float = 0.0f
        set(value) {
            field = value;
            updateCornerRadii();
        };
    var radiusTopRight: Float = 0.0f
        set(value) {
            field = value;
            updateCornerRadii();
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

    private var _corners: FloatArray = floatArrayOf();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        _paintActive.style = Paint.Style.FILL;
        _paintInactive.style = Paint.Style.FILL;

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ProgressBar, 0, 0);
        try {
            progress = a.getFraction(R.styleable.ProgressBar_progress, 1, 1, 0.0f);
            radiusBottomLeft = a.getDimensionPixelSize(R.styleable.ProgressBar_radiusBottomLeft, 0).toFloat();
            radiusBottomRight = a.getDimensionPixelSize(R.styleable.ProgressBar_radiusBottomRight, 0).toFloat();
            radiusTopLeft = a.getDimensionPixelSize(R.styleable.ProgressBar_radiusTopLeft, 0).toFloat();
            radiusTopRight = a.getDimensionPixelSize(R.styleable.ProgressBar_radiusTopRight, 0).toFloat();
            _paintActive.color = a.getColor(R.styleable.ProgressBar_activeColor, ContextCompat.getColor(context, R.color.colorPrimary));
            _paintInactive.color = a.getColor(R.styleable.ProgressBar_inactiveColor, ContextCompat.getColor(context, R.color.gray_c3));
        } finally {
            a.recycle();
        }
    }

    private fun updateCornerRadii() {
        _corners = floatArrayOf(
            radiusTopLeft, radiusTopLeft,
            radiusTopRight, radiusTopRight,
            radiusBottomRight, radiusBottomRight,
            radiusBottomLeft, radiusBottomLeft
        );

        invalidate();
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);

        val w = width.toFloat();
        val h = height.toFloat();

        _path.reset();
        _progressRect.set(0.0f, 0.0f, w, h);
        _path.addRoundRect(_progressRect, _corners, Path.Direction.CW);
        canvas.drawPath(_path, _paintInactive);

        _path.reset();
        _progressRect.set(0.0f, 0.0f, progress * w, h);
        _path.addRoundRect(_progressRect, _corners, Path.Direction.CW);
        canvas.drawPath(_path, _paintActive);
    }

    companion object {
        val TAG = "ProgressBar";
    }
}