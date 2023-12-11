package com.futo.platformplayer.views.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.constructs.Event1

class EnabledSourceViewHolder : ViewHolder {
    private val _imageSource: ImageView;
    private val _textSource: TextView;
    private val _textSourceSubtitle: TextView;
    private val _imageDragDrop: ImageView;
    private val _buttonRemove: LinearLayout;

    var onRemove = Event1<IPlatformClient>();
    var onClick = Event1<IPlatformClient>();
    var source: IPlatformClient? = null
        private set

    @SuppressLint("ClickableViewAccessibility")
    constructor(view: View, touchHelper: ItemTouchHelper) : super(view) {
        _imageSource = view.findViewById(R.id.image_source);
        _textSource = view.findViewById(R.id.text_source);
        _textSourceSubtitle = itemView.findViewById(R.id.text_source_subtitle);
        _imageDragDrop = view.findViewById(R.id.image_drag_drop);
        _buttonRemove = view.findViewById(R.id.button_remove);
        val root = view.findViewById<LinearLayout>(R.id.root);

        root.setOnClickListener {
            source?.let { onClick.emit(it); };
        };

        _imageDragDrop.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                touchHelper.startDrag(this);
            }

            false
        };

        _buttonRemove.setOnClickListener {
            source?.let { onRemove.emit(it); };
        };
    }

    fun setCanRemove(canRemove: Boolean) {
        _buttonRemove.visibility = if (canRemove) { View.VISIBLE } else { View.GONE };
    }

    fun bind(client: IPlatformClient) {
        client.icon?.setImageView(_imageSource);

        _textSource.text = client.name;
        _textSourceSubtitle.text = itemView.context.getString(R.string.tap_to_open);
        source = client
    }
}