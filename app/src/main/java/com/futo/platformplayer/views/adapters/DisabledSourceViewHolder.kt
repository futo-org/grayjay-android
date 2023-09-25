package com.futo.platformplayer.views.adapters

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

class DisabledSourceViewHolder : ViewHolder {
    private val _imageSource: ImageView;
    private val _textSource: TextView;
    private val _textSourceSubtitle: TextView;

    private val _buttonAdd: LinearLayout;

    var onClick = Event0();
    var onAdd = Event0();
    var source: IPlatformClient? = null
        private set

    constructor(viewGroup: ViewGroup) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_source_disabled, viewGroup, false)) {
        _imageSource = itemView.findViewById(R.id.image_source);
        _textSource = itemView.findViewById(R.id.text_source);
        _textSourceSubtitle = itemView.findViewById(R.id.text_source_subtitle);
        _buttonAdd = itemView.findViewById(R.id.button_add);

        val root = itemView.findViewById<LinearLayout>(R.id.root);
        _buttonAdd.setOnClickListener { onAdd.emit() }
        root.setOnClickListener { onClick.emit(); };
    }

    fun bind(client: IPlatformClient) {
        client.icon?.setImageView(_imageSource);

        _textSource.text = client.name;
        _textSourceSubtitle.text = "Tap to open";
        source = client;
    }
}