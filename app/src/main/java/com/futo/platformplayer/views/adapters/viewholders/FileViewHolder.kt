package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.states.FileEntry
import com.futo.platformplayer.views.adapters.AnyAdapter


class FileViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<FileEntry>(
    LayoutInflater.from(_viewGroup.context).inflate(
        R.layout.list_file,
        _viewGroup, false)) {

    val onClick = Event1<FileEntry?>();
    val onDelete = Event1<FileEntry?>();

    protected var _file: FileEntry? = null;
    protected val _imageThumbnail: ImageView
    protected val _buttonDelete: ImageButton;
    protected val _textName: TextView
    //protected val _textMetadata: TextView

    init {
        _imageThumbnail = _view.findViewById(R.id.image_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        //_textMetadata = _view.findViewById(R.id.text_metadata);
        _buttonDelete = _view.findViewById(R.id.button_delete);

        _view.setOnClickListener { onClick.emit(_file) };
        _buttonDelete.setOnClickListener { onDelete.emit(_file) }
    }


    override fun bind(file: FileEntry) {
        _file = file;
        _imageThumbnail?.let {
            if(file.isDirectory)
                it.setImageResource(R.drawable.ic_folder);
            else {
                Glide.with(it)
                    .load(file.thumbnail)
                    .placeholder(R.drawable.ic_song)
                    .into(it)
            }
        };
        _buttonDelete.isVisible = file.removable;

        _textName.text = file.name;
        //if(file.isDirectory)
        //    _textMetadata.text = "Directory";
        //else
        //    _textMetadata.text = "";
    }

}