package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.views.others.Checkbox
import com.futo.platformplayer.views.adapters.AnyAdapter

class ImportPlaylistsViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<SelectablePlaylist>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_import_playlist, _viewGroup, false)) {

    private val _checkbox: Checkbox;
    private val _imageThumbnail: ImageView;
    private val _textName: TextView;
    private val _textMetadata: TextView;
    private val _root: LinearLayout;
    private var _playlist: SelectablePlaylist? = null;

    val onSelectedChange = Event1<SelectablePlaylist>();

    init {
        _checkbox = _view.findViewById(R.id.checkbox);
        _imageThumbnail = _view.findViewById(R.id.image_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        _textMetadata = _view.findViewById(R.id.text_metadata);
        _root = _view.findViewById(R.id.root);

        _checkbox.onValueChanged.subscribe {
            _playlist?.selected = it;
            _playlist?.let { onSelectedChange.emit(it); };
        };

        _root.setOnClickListener {
            _checkbox.value = !_checkbox.value;
            _playlist?.selected = _checkbox.value;
            _playlist?.let { onSelectedChange.emit(it); };
        };
    }

    override fun bind(playlist: SelectablePlaylist) {
        _textName.text = playlist.playlist.name;
        _textMetadata.text = "${playlist.playlist.videos.size} " + _view.context.getString(R.string.videos);
        _checkbox.value = playlist.selected;

        val thumbnail = playlist.playlist.videos.firstOrNull()?.thumbnails?.getHQThumbnail();
        if (thumbnail != null)
            Glide.with(_imageThumbnail)
                .load(thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(_imageThumbnail);
        else
            Glide.with(_imageThumbnail).clear(_imageThumbnail);

        _playlist = playlist;
    }
}

class SelectablePlaylist(
    val playlist: Playlist,
    var selected: Boolean = false
) { }