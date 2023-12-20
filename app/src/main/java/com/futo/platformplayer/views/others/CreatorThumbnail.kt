package com.futo.platformplayer.views.others

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.views.IdenticonView
import userpackage.Protocol

class CreatorThumbnail : ConstraintLayout {
    private val _root: ConstraintLayout;
    private val _imageChannelThumbnail: ImageView;
    private val _imageNewActivity: ImageView;
    private val _imageNeoPass: ImageView;
    private val _identicon: IdenticonView;
    private var _harborAnimator: ObjectAnimator? = null;
    private var _imageAnimator: ObjectAnimator? = null;

    var onClick = Event1<Pair<String, Any>>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_creator_thumbnail, this, true);

        _root = findViewById(R.id.root);
        _imageChannelThumbnail = findViewById(R.id.image_channel_thumbnail);
        _identicon = findViewById(R.id.identicon);
        _imageChannelThumbnail.clipToOutline = true;
        _identicon.clipToOutline = true;
        _imageChannelThumbnail.visibility = View.GONE
        _imageNewActivity = findViewById(R.id.image_new_activity);
        _imageNeoPass = findViewById(R.id.image_neopass);

        if (!isInEditMode) {
            setHarborAvailable(false, animate = false, system = null);
            setNewActivity(false);
        }
    }

    fun clear() {
        _imageChannelThumbnail.visibility = View.GONE;
        _imageChannelThumbnail.setImageResource(R.drawable.placeholder_channel_thumbnail);
        setHarborAvailable(false, animate = false, system = null);
        setNewActivity(false);
    }

    fun setThumbnail(url: String?, animate: Boolean) {
        if (url == null) {
            clear();
            return;
        }

        _imageChannelThumbnail.visibility = View.VISIBLE;

        _harborAnimator?.cancel();
        _harborAnimator = null;

        _imageAnimator?.cancel();
        _imageAnimator = null;

        if (url.startsWith("polycentric://")) {
            try {
                val dataLink = PolycentricCache.getDataLinkFromUrl(url)
                setHarborAvailable(true, animate, dataLink?.system);
            } catch (e: Throwable) {
                setHarborAvailable(false, animate, null);
            }
        } else {
            setHarborAvailable(false, animate, null);
        }

        if (animate) {
            Glide.with(_imageChannelThumbnail)
                .load(url)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .crossfade()
                .into(_imageChannelThumbnail);
        } else {
            Glide.with(_imageChannelThumbnail)
                .load(url)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(_imageChannelThumbnail);
        }
    }

    fun setHarborAvailable(available: Boolean, animate: Boolean, system: Protocol.PublicKey?) {
        _harborAnimator?.cancel();
        _harborAnimator = null;

        if (available) {
            _imageNeoPass.visibility = View.VISIBLE;
            if (animate) {
                _harborAnimator = ObjectAnimator.ofFloat(_imageNeoPass, "alpha", 0.0f, 1.0f).setDuration(100);
                _harborAnimator?.start();
            }
        } else {
            _imageNeoPass.visibility = View.GONE;
        }

        if (system != null) {
            _identicon.hashString = system.toString()
            _identicon.visibility = View.VISIBLE
        } else {
            _identicon.visibility = View.GONE
        }
    }

    fun setChannelImageResource(resource: Int?, animate: Boolean) {
        setChannelImage(resource?.let { { _imageChannelThumbnail.setImageResource(it) } }, animate);
    }

    fun setChannelImageBitmap(bitmap: Bitmap?, animate: Boolean) {
        setChannelImage(bitmap?.let { { _imageChannelThumbnail.setImageBitmap(it) } }, animate);
    }

    fun setChannelImage(setter: (() -> Unit)?, animate: Boolean) {
        _imageAnimator?.cancel();
        _imageAnimator = null;

        if (setter != null) {
            _imageChannelThumbnail.visibility = View.VISIBLE;
            setter();
            if (animate) {
                _imageAnimator = ObjectAnimator.ofFloat(_imageChannelThumbnail, "alpha", 0.0f, 1.0f).setDuration(100);
                _imageAnimator?.start();
            }
        } else {
            _imageChannelThumbnail.visibility = View.GONE;
        }
    }

    fun setNewActivity(available: Boolean) {
        _imageNewActivity.visibility = if (available) View.VISIBLE else View.GONE;
    }

    companion object {
        private const val TAG = "CreatorThumbnail";
    }
}