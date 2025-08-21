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
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.images.GlideHelper
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.views.ToggleBar

class ToggleTagView : LinearLayout {
    private val _root: FrameLayout;
    private val _textTag: TextView;
    private var _text: String = "";
    private var _image: ImageView;

    var tag: String? = null
        private set;

    var isActive: Boolean = false
        private set;
    var isButton: Boolean = false
        private set;

    var onClick = Event2<ToggleTagView, Boolean>();
    var onLongClick = Event2<ToggleTagView, Boolean>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_toggle_tag, this, true);
        _root = findViewById(R.id.root);
        _textTag = findViewById(R.id.text_tag);
        _image = findViewById(R.id.image_tag);
        _root.setOnClickListener {
            handleClick();
        }
        _root.setOnLongClickListener {
            if(onLongClick.hasListeners())
                onLongClick.emit(this, isActive);
            else {
                if(!isButton) {
                    setToggle(!isActive);
                }
                onClick.emit(this, isActive);
            }
            return@setOnLongClickListener true;
        }
    }

    fun handleClick() {
        if(!isButton)
            setToggle(!isActive);
        onClick.emit(this, isActive);
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

    fun setInfo(toggle: ToggleBar.Toggle){
        _text = toggle.name;
        _textTag.text = toggle.name;
        setToggle(toggle.isActive);
        if(toggle.iconVariable != null) {
            toggle.iconVariable.setImageView(_image, R.drawable.ic_error_pred);
            _image.visibility = View.GONE;
        }
        else if(toggle.icon > 0) {
            _image.setImageResource(toggle.icon);
            _image.visibility = View.GONE;
        }
        else
            _image.visibility = View.VISIBLE;
        _textTag.visibility = if(!toggle.name.isNullOrEmpty()) View.VISIBLE else View.GONE;
        this.isButton = isButton;
        tag = toggle.tag;
    }

    fun setInfo(imageResource: Int, text: String, isActive: Boolean, isButton: Boolean = false, tag: String? = null) {
        _text = text;
        _textTag.text = text;
        setToggle(isActive);
        _image.setImageResource(imageResource);
        _image.visibility = View.VISIBLE;
        _textTag.visibility = if(!text.isNullOrEmpty()) View.VISIBLE else View.GONE;
        this.isButton = isButton;
        this.tag = tag;
    }
    fun setInfo(image: ImageVariable, text: String, isActive: Boolean, isButton: Boolean = false, tag: String? = null) {
        _text = text;
        _textTag.text = text;
        setToggle(isActive);
        image.setImageView(_image, R.drawable.ic_error_pred);
        _image.visibility = View.VISIBLE;
        _textTag.visibility = if(!text.isNullOrEmpty()) View.VISIBLE else View.GONE;
        this.isButton = isButton;
        this.tag = tag;
    }
    fun setInfo(text: String, isActive: Boolean, isButton: Boolean = false, tag: String? = null) {
        _image.visibility = View.GONE;
        _text = text;
        _textTag.text = text;
        _textTag.visibility = if(!text.isNullOrEmpty()) View.VISIBLE else View.GONE;
        setToggle(isActive);
        this.isButton = isButton;
        this.tag = tag;
    }
}