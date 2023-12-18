package com.futo.platformplayer.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class SearchView : FrameLayout {

    val textSearch: TextView;
    val buttonClear: ImageButton;

    var onSearchChanged = Event1<String>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_search_bar, this);

        textSearch = findViewById(R.id.edit_search)
        buttonClear = findViewById(R.id.button_clear_search)

        buttonClear.setOnClickListener { textSearch.text = "" };
        textSearch.addTextChangedListener {
            onSearchChanged.emit(it.toString());
        };
    }
}