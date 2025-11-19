package com.futo.platformplayer.views.pills

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.LoaderView

class PillButton : LinearLayout {
    val icon: ImageView;
    val text: TextView;
    val loaderView: LoaderView;
    val onClick = Event0();
    private var _isLoading = false;

    constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.pill_button, this, true);
        icon = findViewById(R.id.pill_icon);
        text = findViewById(R.id.pill_text);
        loaderView = findViewById(R.id.loader)

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.PillButton, 0, 0);
        val attrIconRef = attrArr.getResourceId(R.styleable.PillButton_pillIcon, -1);
        if(attrIconRef != -1)
            icon.setImageResource(attrIconRef);
        else
            icon.visibility = View.GONE;

        val attrText = attrArr.getText(R.styleable.PillButton_pillText) ?: "";
        text.text = attrText;

        findViewById<LinearLayout>(R.id.root).setOnClickListener {
            if (_isLoading) {
                return@setOnClickListener
            }

            onClick.emit();
        };
    }

    fun setLoading(loading: Boolean) {
        if (loading == _isLoading) {
            return
        }

        if (loading) {
            text.visibility = View.GONE
            loaderView.visibility = View.VISIBLE
            loaderView.start()
        } else {
            loaderView.stop()
            text.visibility = View.VISIBLE
            loaderView.visibility = View.GONE
        }

        _isLoading = loading
    }
}