package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.images.GlideHelper
import com.futo.platformplayer.models.ImageVariable

class ToggleTagView : LinearLayout {
    private val _root: FrameLayout;
    private val _textTag: TextView;
    private var _text: String = "";
    private var _image: ImageView;

    var isActive: Boolean = false
        private set;
    var isButton: Boolean = false
        private set;

    var onClick = Event1<Boolean>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_toggle_tag, this, true);
        _root = findViewById(R.id.root);
        _textTag = findViewById(R.id.text_tag);
        _image = findViewById(R.id.image_tag);
        _root.setOnClickListener {
            if(!isButton)
                setToggle(!isActive);
            onClick.emit(isActive);
        }
    }

    fun setToggle(isActive: Boolean) {
        this.isActive = isActive;
        if(isActive) {
            _root.setBackgroundResource(R.drawable.background_pill_toggled);
            _textTag.alpha = 1f;
        }
        else {
            _root.setBackgroundResource(R.drawable.background_pill_untoggled);
            _textTag.alpha = 0.5f;
        }
    }

    fun setInfo(imageResource: Int, text: String, isActive: Boolean, isButton: Boolean = false) {
        _text = text;
        _textTag.text = text;
        setToggle(isActive);
        _image.setImageResource(imageResource);
        _image.visibility = View.VISIBLE;
        this.isButton = isButton;
    }
    fun setInfo(image: ImageVariable, text: String, isActive: Boolean, isButton: Boolean = false) {
        _text = text;
        _textTag.text = text;
        setToggle(isActive);
        image.setImageView(_image, R.drawable.ic_error_pred);
        _image.visibility = View.VISIBLE;
        this.isButton = isButton;
    }
    fun setInfo(text: String, isActive: Boolean, isButton: Boolean = false) {
        _image.visibility = View.GONE;
        _text = text;
        _textTag.text = text;
        setToggle(isActive);
        this.isButton = isButton;
    }
}