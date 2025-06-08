package com.futo.platformplayer.views

import android.content.Context
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    var onEnter = Event1<String>();

    val text: String get() = textSearch.text.toString();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_search_bar, this);

        textSearch = findViewById(R.id.edit_search)
        buttonClear = findViewById(R.id.button_clear_search)

        buttonClear.setOnClickListener {
            textSearch.text = ""
            textSearch?.clearFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(textSearch.windowToken, 0)
            onSearchChanged.emit("")
            onEnter.emit("")
        }
        textSearch.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                textSearch?.clearFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(textSearch.windowToken, 0)
                onEnter.emit(textSearch.text.toString())
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false

        }
        textSearch.addTextChangedListener {
            buttonClear.visibility = if ((it?.length ?: 0) > 0) View.VISIBLE else View.GONE
            onSearchChanged.emit(it.toString())
        };
    }
}