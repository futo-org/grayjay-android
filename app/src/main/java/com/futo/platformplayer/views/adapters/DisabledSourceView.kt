package com.futo.platformplayer.views.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class DisabledSourceView : LinearLayout {
    private val _root: LinearLayout;
    private val _imageSource: ImageView;
    private val _textSource: TextView;
    private val _textSourceSubtitle: TextView;

    private val _buttonAdd: LinearLayout;

    val onClick = Event0();
    val onAdd = Event1<IPlatformClient>();
    val source: IPlatformClient;

    constructor(context: Context, client: IPlatformClient) : super(context) {
        inflate(context, R.layout.list_source_disabled, this);
        source = client;

        _root = findViewById<LinearLayout>(R.id.root);
        _imageSource = findViewById(R.id.image_source);
        _textSource = findViewById(R.id.text_source);
        _textSourceSubtitle = findViewById(R.id.text_source_subtitle);
        _buttonAdd = findViewById(R.id.button_add);

        client.icon?.setImageView(_imageSource);

        _textSource.text = client.name;
        _textSourceSubtitle.text = context.getString(R.string.tap_to_open);

        _buttonAdd.setOnClickListener { onAdd.emit(source) }
        _root.setOnClickListener { onClick.emit(); };
    }
}