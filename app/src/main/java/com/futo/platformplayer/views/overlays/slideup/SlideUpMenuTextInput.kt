package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.futo.platformplayer.R
import org.w3c.dom.Text

class SlideUpMenuTextInput : LinearLayout {
    private lateinit var _root: LinearLayout;
    private lateinit var _editText: EditText;
    private lateinit var _inputMethodManager: InputMethodManager;

    var text: String get() = _editText.text.toString()
        set(v: String) = _editText.setText(v);

    constructor(context: Context, attrs: AttributeSet? = null): super(context, attrs) {
        init();
    }

    constructor(context: Context, name: String? = null): super(context) {
        init(name);
    }

    private fun init(name: String? = null){
        _inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;

        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_text_input, this, true);

        _root = findViewById(R.id.slide_up_menu_text_input_root);
        _editText = findViewById(R.id.edit_text);

        if (name != null) {
            _editText.hint = name;
        }
    }

    fun activate() {
        _editText.requestFocus();
        _inputMethodManager.showSoftInput(_editText, 0);
    }

    fun deactivate() {
        _editText.clearFocus();
        _inputMethodManager.hideSoftInputFromWindow(_editText.windowToken, 0);
    }

    fun clear() {
        _editText.text.clear();
    }
}