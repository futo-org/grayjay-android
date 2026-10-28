package com.futo.platformplayer.views.video.thumbnails

import android.graphics.Bitmap
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target

internal data class SeekThumbnailRequest(val model: Any, val frame: SeekThumbnailFrame? = null) {
    fun request(glide: RequestManager): RequestBuilder<Bitmap> {
        return glide.asBitmap()
            .load(model)
            .diskCacheStrategy(if(model is String) DiskCacheStrategy.DATA else DiskCacheStrategy.NONE)
            .override(Target.SIZE_ORIGINAL)
            .dontTransform()
            .apply {
                frame?.let { transform(SeekThumbnailCrop(it.x, it.y, it.width, it.height)); };
            };
    }
}
