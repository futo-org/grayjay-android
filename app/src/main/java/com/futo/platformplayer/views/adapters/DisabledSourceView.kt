package com.futo.platformplayer.views.adapters

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins

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

        if (client is JSClient && StatePlugins.instance.hasUpdateAvailable(client.config)) {
            _textSourceSubtitle.text = context.getString(R.string.update_available_exclamation)
            _textSourceSubtitle.setTextColor(context.getColor(R.color.light_blue_400))
            _textSourceSubtitle.typeface = resources.getFont(R.font.inter_regular)
        } else {
            _textSourceSubtitle.text = context.getString(R.string.tap_to_open)
            _textSourceSubtitle.setTextColor(context.getColor(R.color.gray_ac))
            _textSourceSubtitle.typeface = resources.getFont(R.font.inter_extra_light)
        }

        _buttonAdd.setOnClickListener { onAdd.emit(source) }
        _root.setOnClickListener { onClick.emit(); };
    }
}