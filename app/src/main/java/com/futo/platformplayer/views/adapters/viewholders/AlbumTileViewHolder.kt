package com.futo.platformplayer.views.adapters.viewholders

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.views.adapters.AnyAdapter


class AlbumTileViewHolder(val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<Album>(
    LayoutInflater.from(_viewGroup.context).inflate(
        R.layout.list_album_tile,
        _viewGroup, false)) {

    val onClick = Event1<Album?>();

    protected  var _root: ConstraintLayout;
    protected var _album: Album? = null;
    protected val _imageThumbnail: ImageView
    protected val _textName: TextView
    protected val _textMetadata: TextView

    init {
        _root = _view.findViewById(R.id.root);
        _imageThumbnail = _view.findViewById(R.id.image_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        _textMetadata = _view.findViewById(R.id.text_metadata);

        _view.setOnClickListener { onClick.emit(_album) };
    }

    fun setWidth(dp: Int) {
        _root.updateLayoutParams {
            this.width = (dp - 12).dp(_viewGroup.context.resources);
            this.height = (dp + 48).dp(_viewGroup.context.resources);
        }
        _imageThumbnail.updateLayoutParams {
            this.width = (dp - 12).dp(_viewGroup.context.resources);
            this.height = (dp - 12).dp(_viewGroup.context.resources);
        }
    }

    fun setAutoSize(totalWidth: Float) {
        val viewWidth = 98;
        val dpWidth = totalWidth;
        val columns = Math.max(((dpWidth) / viewWidth).toInt(), 1);
        val remainder = dpWidth - columns * viewWidth;
        val targetSize = viewWidth + (remainder / columns).toInt();
        setWidth(targetSize);
    }

    override fun bind(album: Album) {
        _album = album;
        _imageThumbnail?.let {
            if (album.thumbnail != null)
                Glide.with(it)
                    .load(album.thumbnail)
                    .placeholder(R.drawable.unknown_music)
                    .into(it)
            else
                Glide.with(it).load(R.drawable.unknown_music).into(it);
        };

        _textName.text = album.name;
        _textMetadata.text = album.artist ?: "";
    }

    companion object {
        fun getAutoSizeColumns(totalWidth: Float): Int {
            val viewWidth = 98;
            val dpWidth = totalWidth;
            val columns = Math.max(((dpWidth) / viewWidth).toInt(), 1);
            return columns;
        }
    }
}