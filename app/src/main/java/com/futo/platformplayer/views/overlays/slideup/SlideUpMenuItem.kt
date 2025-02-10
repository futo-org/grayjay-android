package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.futo.platformplayer.R

class SlideUpMenuItem : ConstraintLayout {

    private lateinit var _root: ConstraintLayout;
    private lateinit var _image: ImageView;
    private lateinit var _text: TextView;
    private lateinit var _subtext: TextView;
    private lateinit var _description: TextView;

    var selectedOption: Boolean = false;

    private var _parentClickListener: (()->Unit)? = null;

    var itemTag: Any? = null;

    constructor(context: Context, attrs: AttributeSet? = null): super(context, attrs) {
        init();
    }

    constructor(
        context: Context,
        imageRes: Int = 0,
        mainText: String,
        subText: String = "",
        description: String? = "",
        tag: Any?,
        call: (() -> Unit)? = null,
        invokeParent: Boolean = true
    ): super(context){
        init();
        _image.setImageResource(imageRes);
        _text.text = mainText;
        _subtext.text = subText;

        if(description.isNullOrEmpty())
            _description.isVisible = false;
        else {
            _description.text = description;
            _description.isVisible = true;
        }
        this.itemTag = tag;

        if (call != null) {
            setOnClickListener {
                call.invoke();
                if(invokeParent)
                    _parentClickListener?.invoke();
            };
        }
    }

    private fun init(){
        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_option, this, true);

        _root = findViewById(R.id.slide_up_menu_item_root);
        _image = findViewById(R.id.slide_up_menu_item_image);
        _text = findViewById(R.id.slide_up_menu_item_text);
        _subtext = findViewById(R.id.slide_up_menu_item_subtext);
        _description = findViewById(R.id.slide_up_menu_item_description);

        setOptionSelected(false);
    }

    fun setOptionSelected(isSelected: Boolean): Boolean {
        selectedOption = isSelected;
        if (!isSelected) {
            _root.setBackgroundResource(R.drawable.background_slide_up_option);
        } else {
            _root.setBackgroundResource(R.drawable.background_slide_up_option_selected);
        }
        return isSelected;
    }

    fun setSubText(subText: String) {
        _subtext.text = subText
    }

    fun setParentClickListener(listener: (()->Unit)?) {
        _parentClickListener = listener;
    }
}