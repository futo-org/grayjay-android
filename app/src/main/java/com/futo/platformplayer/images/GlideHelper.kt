package com.futo.platformplayer.images

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.futo.platformplayer.api.media.models.Thumbnails

class GlideHelper {


    companion object {
        fun ImageView.loadThumbnails(thumbnails: Thumbnails, isHQ: Boolean = true, continuation: ((RequestBuilder<Drawable>) -> Unit)? = null) {
            val url = if(isHQ) thumbnails.getHQThumbnail() ?: thumbnails.getLQThumbnail() else thumbnails.getLQThumbnail();

            val req = Glide.with(this).load(url);

            if (thumbnails.hasMultiple() && false) { //TODO: Resolve issue where fallback triggered on second loads?
                val fallbackUrl = if (isHQ) thumbnails.getLQThumbnail() else thumbnails.getHQThumbnail();
                if (continuation != null)
                    req.error(continuation(Glide.with(this).load(fallbackUrl)))
                else
                    req.error(Glide.with(this).load(fallbackUrl).into(this));
            }
            else if (continuation != null)
                continuation(req);
            else
                req.into(this);
        }


        fun RequestBuilder<Drawable>.crossfade(): RequestBuilder<Drawable> {
            return this.transition(DrawableTransitionOptions.withCrossFade());
        }
    }

}