package com.futo.platformplayer.views.sources

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.models.ImageVariable

class SourceUnderConstructionView : LinearLayout {
    private val _imageSource: ImageView;
    private val _textSource: TextView;
    private val _textSourceSubtitle: TextView;

    private val _buttonAdd: LinearLayout;

    constructor(context: Context, name: String, logo: ImageVariable): super(context) {
        inflate(context, R.layout.list_source_construction, this);

        _imageSource = findViewById(R.id.image_source);
        _textSource = findViewById(R.id.text_source);
        _textSourceSubtitle = findViewById(R.id.text_source_subtitle);
        _buttonAdd = findViewById(R.id.button_add);


        logo.setImageView(_imageSource);
        _textSource.text = name;
    }
}