package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.futo.platformplayer.R

class SlideUpMenuItem : RelativeLayout {

    private lateinit var _root: RelativeLayout;
    private lateinit var _image: ImageView;
    private lateinit var _text: TextView;
    private lateinit var _subtext: TextView;

    var selectedOption: Boolean = false;

    private var _parentClickListener: (()->Unit)? = null;

    var itemTag: Any? = null;

    constructor(context: Context, attrs: AttributeSet? = null): super(context, attrs) {
        init();
    }

    constructor(context: Context, imageRes: Int = 0, mainText: String, subText: String = "", tag: Any?, call: (()->Unit)? = null, invokeParent: Boolean = true): super(context){
        init();
        _image.setImageResource(imageRes);
        _text.text = mainText;
        _subtext.text = subText;
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

    fun setParentClickListener(listener: (()->Unit)?) {
        _parentClickListener = listener;
    }
}