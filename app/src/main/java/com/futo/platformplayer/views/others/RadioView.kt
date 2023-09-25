package com.futo.platformplayer.views.others

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class RadioView : LinearLayout {
    private val _root: FrameLayout;
    private val _textTag: TextView;
    private var _text: String = "";
    private var _selected: Boolean = false;
    private var _handleClick: Boolean = true;

    val selected get() = _selected;
    var onClick = Event0();
    var onSelectedChange = Event1<Boolean>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_tag, this, true);
        _root = findViewById(R.id.root);
        _textTag = findViewById(R.id.text_tag);
        _root.setOnClickListener {
            onClick.emit();
            if (_handleClick) {
                setIsSelected(!_selected)
            }
        };

        _root.setBackgroundResource(R.drawable.background_radio_unselected);
        _textTag.setTextColor(resources.getColor(R.color.gray_67));
    }

    fun setInfo(text: String, selected: Boolean) {
        _text = text;
        _textTag.text = text;
        setIsSelected(selected);
    }

    fun setIsSelected(selected: Boolean) {
        val changed = _selected != selected;
        if (!changed) {
            return;
        }

        _selected = selected;
        _root.setBackgroundResource(if (selected) R.drawable.background_radio_selected else R.drawable.background_radio_unselected);
        _textTag.setTextColor(resources.getColor(if (selected) R.color.white else R.color.gray_67));
        onSelectedChange.emit(_selected);
    }

    fun setHandleClick(handleClick: Boolean) {
        _handleClick = handleClick;
    }
}