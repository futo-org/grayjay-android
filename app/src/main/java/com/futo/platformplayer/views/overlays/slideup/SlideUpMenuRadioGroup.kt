package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.others.RadioGroupView

class SlideUpMenuRadioGroup : LinearLayout {
    private lateinit var _root: LinearLayout;
    private lateinit var _radioGroupView: RadioGroupView;
    private lateinit var _inputMethodManager: InputMethodManager;
    private lateinit var _textHeader: TextView;

    val selectedOptions: List<Any?> get() = _radioGroupView.selectedOptions;
    val onSelectedChange = Event1<List<Any?>>();
    val onSelectedPairChange = Event1<List<Any?>>();

    constructor(context: Context, name: String, options: List<Pair<String, Any?>>, initiallySelectedOptions: List<Any?>, multiSelect: Boolean, atLeastOne: Boolean): super(context) {
        init(name, options, initiallySelectedOptions, multiSelect, atLeastOne);
    }

    private fun init(name: String, options: List<Pair<String, Any?>>, initiallySelectedOptions: List<Any?>, multiSelect: Boolean, atLeastOne: Boolean) {
        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_radio_group, this, true);

        _textHeader = findViewById(R.id.text_header);
        _textHeader.text = name;

        _root = findViewById(R.id.slide_up_menu_text_input_root);
        _radioGroupView = findViewById(R.id.radio_group);
        _radioGroupView.setOptions(options, initiallySelectedOptions, multiSelect, atLeastOne);
        _radioGroupView.onSelectedChange.subscribe { onSelectedChange.emit(it) };
    }
}