package com.futo.platformplayer.models

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.futo.platformplayer.PresetImages
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient
import java.io.File

@kotlinx.serialization.Serializable
data class ImageVariable(
    val url: String? = null,
    val resId: Int? = null,
    @Transient
    @Contextual
    private val bitmap: Bitmap? = null,
    val presetName: String? = null,
    var subscriptionUrl: String? = null) {

    @SuppressLint("DiscouragedApi")
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
        } else if(!presetName.isNullOrEmpty()) {
            val resId = PresetImages.getPresetResIdByName(presetName);
            imageView.setImageResource(resId);
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
        fun fromPresetName(str: String): ImageVariable {
            return ImageVariable(null, null, null, str);
        }
        fun fromFile(file: File): ImageVariable {
            try {
                return ImageVariable.fromBitmap(BitmapFactory.decodeFile(file.absolutePath));
            }
            catch(ex: Throwable) {
                Logger.e("ImageVariable", "Unsupported image format? " + ex.message, ex);
                return fromResource(R.drawable.ic_error_pred);
            }
        }
    }
}