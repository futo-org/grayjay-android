package com.futo.platformplayer.views.adapters.viewholders

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.StoreItem
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.google.android.material.imageview.ShapeableImageView

class StoreItemViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<StoreItem>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.view_store_item, _viewGroup, false)) {

    private val _image: ShapeableImageView;
    private val _name: TextView;
    private var _storeItem: StoreItem? = null;
    
    init {
        _image = _view.findViewById(R.id.image_item);
        _name = _view.findViewById(R.id.text_item);
        _view.findViewById<LinearLayout>(R.id.root).setOnClickListener {
            val s = _storeItem ?: return@setOnClickListener;

            try {
                val uri = Uri.parse(s.url);
                val intent = Intent(Intent.ACTION_VIEW);
                intent.data = uri;
                _view.context.startActivity(intent);
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to open URI: '${it}'.", e);
            }
        }
    }

    override fun bind(value: StoreItem) {
        Glide.with(_image)
            .load(value.image)
            .crossfade()
            .into(_image);

        _name.text = value.name;
        _storeItem = value;
    }

    companion object {
        private const val TAG = "StoreItemViewHolder";
    }
}