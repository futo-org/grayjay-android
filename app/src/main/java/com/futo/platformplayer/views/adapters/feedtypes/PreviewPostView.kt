package com.futo.platformplayer.views.adapters.feedtypes

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.IPlatformPostDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.fixHtmlWhitespace
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

class PreviewPostView : LinearLayout {
    private var _content: IPlatformContent? = null;

    private val _imageAuthorThumbnail: ImageView;
    private val _textAuthorName: TextView;
    private val _imageNeopassChannel: ImageView;
    private val _textMetadata: TextView;
    private val _textTitle: TextView;
    private val _textDescription: TextView;
    private val _platformIndicator: PlatformIndicator;

    private val _layoutImages: LinearLayout?;
    private val _imageImage: ImageView?;
    private val _layoutImageCount: LinearLayout?;
    private val _textImageCount: TextView?;

    private val _layoutRating: LinearLayout?;
    private val _imageLikeIcon: ImageView?;
    private val _textLikes: TextView?;
    private val _imageDislikeIcon: ImageView?;
    private val _textDislikes: TextView?;

    private val _layoutComments: LinearLayout?;
    private val _textComments: TextView?;

    private var _neopassAnimator: ObjectAnimator? = null;

    private val _taskLoadValidClaims = TaskHandler<PlatformID, PolycentricCache.CachedOwnedClaims>(StateApp.instance.scopeGetter,
        { PolycentricCache.instance.getValidClaimsAsync(it).await() })
        .success { it -> updateClaimsLayout(it, animate = true) }
        .exception<Throwable> {
            Logger.w(TAG, "Failed to load claims.", it);
        };

    val content: IPlatformContent? get() = _content;

    val onContentClicked = Event1<IPlatformContent>();
    val onChannelClicked = Event1<PlatformAuthorLink>();

    constructor(context: Context, feedStyle: FeedStyle): super(context) {
        inflate(feedStyle);

        _imageAuthorThumbnail = findViewById(R.id.image_author_thumbnail);
        _textAuthorName = findViewById(R.id.text_author_name);
        _imageNeopassChannel = findViewById(R.id.image_neopass_channel);
        _textMetadata = findViewById(R.id.text_metadata);
        _textTitle = findViewById(R.id.text_title);
        _textDescription = findViewById(R.id.text_description);
        _platformIndicator = findViewById(R.id.platform_indicator);

        _layoutImages = findViewById(R.id.layout_images);
        _imageImage = findViewById(R.id.image_image);
        _layoutImageCount = findViewById(R.id.layout_image_count);
        _textImageCount = findViewById(R.id.text_image_count);

        _layoutRating = findViewById(R.id.layout_rating);
        _imageLikeIcon = findViewById(R.id.image_like_icon);
        _textLikes = findViewById(R.id.text_likes);
        _imageDislikeIcon = findViewById(R.id.image_dislike_icon);
        _textDislikes = findViewById(R.id.text_dislikes);

        _layoutComments = findViewById(R.id.layout_comments);
        _textComments = findViewById(R.id.text_comments);

        val root = findViewById<ConstraintLayout>(R.id.root);
        root.isClickable = true;
        root.setOnClickListener {
            _content?.let {
                onContentClicked.emit(it);
            }
        }

        _imageAuthorThumbnail.setOnClickListener { emitChannelClicked(); };
        _textAuthorName.setOnClickListener { emitChannelClicked(); };
        _textMetadata.setOnClickListener { emitChannelClicked(); };
    }

    private fun emitChannelClicked() {
        val channel = _content?.author ?: return;
        onChannelClicked.emit(channel);
    }

    fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_post_preview
            //else -> R.layout.list_post_preview
            else -> R.layout.list_post_thumbnail
        }, this)
    }

    fun bind(content: IPlatformContent) {
        _taskLoadValidClaims.cancel();
        _content = content;

        if (content.author.id.claimType > 0) {
            val cachedClaims = PolycentricCache.instance.getCachedValidClaims(content.author.id);
            if (cachedClaims != null) {
                updateClaimsLayout(cachedClaims, animate = false);
            } else {
                updateClaimsLayout(null, animate = false);
                _taskLoadValidClaims.run(content.author.id);
            }
        } else {
            updateClaimsLayout(null, animate = false);
        }

        _textAuthorName.text = content.author.name;
        _textMetadata.text = content.datetime?.toHumanNowDiffString()?.let { "$it ago" } ?: "";

        if (content.author.thumbnail != null)
            Glide.with(_imageAuthorThumbnail)
                .load(content.author.thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(_imageAuthorThumbnail)
        else
            Glide.with(_imageAuthorThumbnail).load(R.drawable.placeholder_channel_thumbnail).into(_imageAuthorThumbnail);

        _imageAuthorThumbnail.clipToOutline = true;
        _platformIndicator.setPlatformFromClientID(content.id.pluginId);

        val description = if(content is IPlatformPost) {
            if(content.description.isNotEmpty())
                content.description
            else if(content is IPlatformPostDetails)
                content.content
            else
                ""
        } else "";

        if (content.name.isNullOrEmpty()) {
            _textTitle.visibility = View.GONE;
        } else {
            _textTitle.text = content.name;
            _textTitle.visibility = View.VISIBLE;
        }

        _textDescription.text = description.fixHtmlWhitespace();

        if (content is IPlatformPost) {
            setImages(content.thumbnails.filterNotNull());
        } else {
            setImages(null);
        }

        //TODO: Rating not implemented
        _layoutRating?.visibility = View.GONE;

        //TODO: Comments not implemented
        _layoutComments?.visibility = View.GONE;
    }

    private fun setImages(images: List<Thumbnails>?) {
        //Update image count if exists
        if (images == null) {
            _layoutImageCount?.visibility = View.GONE;
        } else {
            if (images.size <= 1) {
                _layoutImageCount?.visibility = View.GONE;
            } else {
                _layoutImageCount?.visibility = View.VISIBLE;
                _textImageCount?.text = "${images.size} Images";
            }
        }

        //Set single image if exists
        _imageImage?.let { imageImage ->
            if (!images.isNullOrEmpty()) {
                imageImage.visibility = View.VISIBLE;

                val image = images.firstNotNullOfOrNull { it.getLQThumbnail() };
                if (image != null)
                    Glide.with(imageImage)
                        .load(image)
                        .placeholder(R.drawable.placeholder_video_thumbnail)
                        .listener(object: RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                imageImage.visibility = View.GONE;
                                return false;
                            }

                            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                return false;
                            }
                        })
                        .crossfade()
                        .into(imageImage)
                else
                    imageImage.visibility = View.GONE;
            } else {
                imageImage.visibility = View.GONE;
            }
        }

        //Set multi image if exists
        _layoutImages?.let { layoutImages ->
            for (child in layoutImages.children) {
                if (child is ImageView) {
                    Glide.with(child).clear(child);
                }
            }

            layoutImages.removeAllViews();

            if (!images.isNullOrEmpty()) {
                val displayMetrics = Resources.getSystem().displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val maxWidth = screenWidth
                var currentWidth = 0
                val four_dp = 4.dp(resources)

                for (url in images.mapNotNull { it.getLQThumbnail() }) {
                    val shapeableImageView = ShapeableImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, four_dp.toFloat()).build()
                    }

                    Glide.with(context)
                        .asDrawable()
                        .load(url)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                shapeableImageView.setImageDrawable(resource);
                                val ratio = shapeableImageView.drawable.intrinsicWidth.toFloat() / shapeableImageView.drawable.intrinsicHeight.toFloat()
                                val projectedWidth = 105.dp(resources).toFloat() * ratio

                                if (currentWidth + projectedWidth <= maxWidth) {
                                    shapeableImageView.layoutParams = LayoutParams(
                                        projectedWidth.toInt(),
                                        LayoutParams.MATCH_PARENT
                                    ).apply {
                                        marginStart = four_dp
                                        marginEnd = four_dp
                                    }

                                    currentWidth += projectedWidth.toInt() + 2 * four_dp
                                    _layoutImages.addView(shapeableImageView)
                                }
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {

                            }
                        })
                }

                layoutImages.visibility = View.VISIBLE;
            } else {
                layoutImages.visibility = View.GONE;
            }
        };
    }

    private fun updateClaimsLayout(claims: PolycentricCache.CachedOwnedClaims?, animate: Boolean) {
        _neopassAnimator?.cancel();
        _neopassAnimator = null;

        val harborAvailable = claims != null && !claims.ownedClaims.isNullOrEmpty();
        if (harborAvailable) {
            _imageNeopassChannel.visibility = View.VISIBLE
            if (animate) {
                _neopassAnimator = ObjectAnimator.ofFloat(_imageNeopassChannel, "alpha", 0.0f, 1.0f).setDuration(500)
                _neopassAnimator?.start()
            }
        } else {
            _imageNeopassChannel.visibility = View.GONE
        }

        //TODO: Necessary if we decide to use creator thumbnail with neopass indicator instead
        //_creatorThumbnail?.setHarborAvailable(harborAvailable, animate)
    }

    companion object {
        val TAG = "PreviewPostView";
    }
}