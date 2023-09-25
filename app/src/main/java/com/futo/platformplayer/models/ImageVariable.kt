package com.futo.platformplayer.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import java.io.File

data class ImageVariable(val url: String? = null, val resId: Int? = null, val bitmap: Bitmap? = null) {

    fun setImageView(imageView: ImageView, fallbackResId: Int = -1) {
        if(bitmap != null) {
            Glide.with(imageView)
                .load(bitmap)
                .into(imageView)
        } else if(resId != null) {
            Glide.with(imageView)
                .load(resId)
                .into(imageView)
        } else if(!url.isNullOrEmpty()) {
            Glide.with(imageView)
                .load(url)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(imageView);
        } else if (fallbackResId != -1) {
            Glide.with(imageView)
                .load(fallbackResId)
                .into(imageView)
        } else {
            Glide.with(imageView)
                .clear(imageView)
        }
    }


    companion object {
        fun fromUrl(url: String): ImageVariable {
            return ImageVariable(url, null, null);
        }
        fun fromResource(id: Int): ImageVariable {
            return ImageVariable(null, id, null);
        }
        fun fromBitmap(bitmap: Bitmap): ImageVariable {
            return ImageVariable(null, null, bitmap);
        }
        fun fromFile(file: File): ImageVariable {
            return ImageVariable.fromBitmap(BitmapFactory.decodeFile(file.absolutePath));
        }
    }
}