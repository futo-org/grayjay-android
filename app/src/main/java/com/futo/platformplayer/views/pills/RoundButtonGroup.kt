package com.futo.platformplayer.views.pills

import android.content.Context
import android.graphics.Color
import android.text.Layout
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import com.futo.platformplayer.R

class RoundButtonGroup : LinearLayout {
    var lastWidth = 0;

    private val _lock = Object();

    private var _buttons: List<RoundButton> = listOf();
    private var _numVisible = 0;

    var alwaysShowLastButton = false;

    constructor(context : Context) : super(context) {
        orientation = HORIZONTAL;
    }
    constructor(context : Context, attributes: AttributeSet) : super(context, attributes) {
        orientation = HORIZONTAL;
    }

    fun getVisibleButtons(): List<RoundButton> {
        return _buttons.take(_numVisible).toList();
    }
    fun getInvisibleButtons(): List<RoundButton> {
        return _buttons.drop(_numVisible).toList();
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b);
        val newWidth = width;
        if(newWidth != lastWidth) {
            lastWidth = newWidth;
            setViews();
        }
    }

    fun setButtons(vararg buttons: RoundButton) {
        _buttons = buttons.toList();
        setViews();
    }

    fun setButtonVisibility(filter: (RoundButton)->Boolean) {
        synchronized(_lock) {
            for(button in _buttons)
                button.visibility = if(filter(button)) View.VISIBLE else View.GONE;
        }
    }
    fun getButtonByTag(tag: Any) : RoundButton? {
        synchronized(_lock) {
            return _buttons.find { it.tagRef == tag };
        }
    }

    private fun setViews() {
        if(lastWidth == 0)
            return;

        val buttonSpace = ((lastWidth / RoundButton.WIDTH)).toInt();
        _numVisible = buttonSpace -
                if(alwaysShowLastButton && buttonSpace < _buttons.size) 1 else 0;

        post {
            synchronized(_lock) {
                removeAllViews();
                for (i in 0 until buttonSpace) {
                    if (i < _buttons.size) {
                        val index =
                            if (alwaysShowLastButton && i == buttonSpace - 1) _buttons.size - 1 else i;
                        val button = _buttons[index];
                        button.layoutParams =
                            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).let {
                                it.weight = 1f;
                                return@let it;
                            };
                        addView(button);
                    }
                }
            }
        }
    }
}