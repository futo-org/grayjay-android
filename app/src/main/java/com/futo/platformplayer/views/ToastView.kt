package com.futo.platformplayer.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.futo.platformplayer.R
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger

class ToastView : LinearLayout {
    private val root: LinearLayout;
    private val title: TextView;
    private val text: TextView;
    private val action: TextView;
    init {
        inflate(context, R.layout.toast, this);
        root = findViewById(R.id.root);
        title = findViewById(R.id.title);
        text = findViewById(R.id.text);
        action = findViewById(R.id.action);
    }

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        setToast(ToastView.Toast("", false))
        root.visibility = GONE;
    }

    fun hide(animate: Boolean, onFinished: (()->Unit)? = null) {
        Logger.i("MainActivity", "Hiding toast");
        if(!animate) {
            root.visibility = GONE;
            alpha = 0f;
            onFinished?.invoke();
        }
        else {
            animate()
                .alpha(0f)
                .setDuration(700)
                .translationY(20.dp(context.resources).toFloat())
                .withEndAction { root.visibility = GONE; onFinished?.invoke(); }
                .start();
        }
    }
    fun show(animate: Boolean) {
        Logger.i("MainActivity", "Showing toast");
        if(!animate) {
            root.visibility = VISIBLE;
            alpha = 1f;
        }
        else {
            alpha = 0f;
            root.visibility = VISIBLE;
            translationY = 20.dp(context.resources).toFloat();
            animate()
                .alpha(1f)
                .setDuration(300)
                .translationY(0f)
                .start();
        }
    }


    fun setToast(toast: Toast) {
        if(toast.title.isNullOrEmpty())
            title.isVisible = false;
        else {
            title.text = toast.title;
            title.isVisible = true;
        }
        text.text = toast.msg;
        if(toast.color != null)
            text.setTextColor(toast.color);
        else
            text.setTextColor(Color.WHITE);

        if(toast.action == null) {
            action.isVisible = false;
            action.setOnClickListener(null);
        }
        else {
            action.text = toast.action.label;
            action.setOnClickListener { toast.action.onClick() };
            action.isVisible = true;
        }
    }
    fun setToastAnimated(toast: Toast) {
        hide(true) {
            setToast(toast);
            show(true);
        };
    }

    class Toast(
        val msg: String,
        val long: Boolean,
        val color: Int? = null,
        val title: String? = null,
        val action: Action? = null,
        val isError: Boolean = false
    );

    class Action(
        val label: String,
        val onClick: () -> Unit
    );
}