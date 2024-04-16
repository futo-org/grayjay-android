package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.IPlatformPostDetails
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.fixHtmlWhitespace
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.adapters.feedtypes.PreviewPostView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.others.Toggle
import com.futo.platformplayer.views.overlays.RepliesOverlay
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.segments.CommentsList
import com.futo.platformplayer.views.subscriptions.SubscribeButton
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.ContentType
import com.futo.polycentric.core.Models
import com.futo.polycentric.core.Opinion
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import userpackage.Protocol
import java.lang.Integer.min

class PostDetailFragment : MainFragment {
    override val isMainView: Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _viewDetail: PostDetailView? = null;

    constructor() : super() { }

    override fun onBackPressed(): Boolean {
        return false;
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = PostDetailView(inflater.context).applyFragment(this);
        _viewDetail = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _viewDetail?.onDestroy();
        _viewDetail = null;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);

        if (parameter is IPlatformPostDetails) {
            _viewDetail?.clear();
            _viewDetail?.setPostDetails(parameter);
        } else if (parameter is IPlatformPost) {
            _viewDetail?.setPostOverview(parameter);
        } else if(parameter is String) {
            _viewDetail?.setPostUrl(parameter);
        }
    }

    private class PostDetailView : ConstraintLayout {
        private lateinit var _fragment: PostDetailFragment;
        private var _url: String? = null;
        private var _isLoading = false;
        private var _post: IPlatformPostDetails? = null;
        private var _postOverview: IPlatformPost? = null;
        private var _polycentricProfile: PolycentricCache.CachedPolycentricProfile? = null;
        private var _version = 0;
        private var _isRepliesVisible: Boolean = false;
        private var _repliesAnimator: ViewPropertyAnimator? = null;

        private val _creatorThumbnail: CreatorThumbnail;
        private val _buttonSubscribe: SubscribeButton;
        private val _channelName: TextView;
        private val _channelMeta: TextView;
        private val _textTitle: TextView;
        private val _textMeta: TextView;
        private val _textContent: TextView;
        private val _platformIndicator: PlatformIndicator;
        private val _buttonShare: ImageButton;

        private val _buttonSupport: LinearLayout;
        private val _buttonStore: LinearLayout;
        private val _layoutMonetization: LinearLayout;

        private val _layoutRating: LinearLayout;
        private val _imageLikeIcon: ImageView;
        private val _textLikes: TextView;
        private val _imageDislikeIcon: ImageView;
        private val _textDislikes: TextView;

        private val _textComments: TextView;
        private val _textCommentType: TextView;
        private val _addCommentView: AddCommentView;
        private val _toggleCommentType: Toggle;

        private val _rating: PillRatingLikesDislikes;

        private val _layoutLoadingOverlay: FrameLayout;
        private val _imageLoader: ImageView;

        private val _imageActive: ImageView;
        private val _layoutThumbnails: FlexboxLayout;

        private val _repliesOverlay: RepliesOverlay;

        private val _commentsList: CommentsList;

        private val _taskLoadPost = if(!isInEditMode) TaskHandler<String, IPlatformPostDetails>(
            StateApp.instance.scopeGetter,
            {
                val result = StatePlatform.instance.getContentDetails(it).await();
                if(result !is IPlatformPostDetails)
                    throw IllegalStateException(context.getString(R.string.expected_media_content_found) + " ${result.contentType}");
                return@TaskHandler result;
            })
            .success { setPostDetails(it) }
            .exception<Throwable> {
                Logger.w(ChannelFragment.TAG, context.getString(R.string.failed_to_load_post), it);
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_post), it, ::fetchPost);
            } else TaskHandler(IPlatformPostDetails::class.java) { _fragment.lifecycleScope };

        private val _taskLoadPolycentricProfile = TaskHandler<PlatformID, PolycentricCache.CachedPolycentricProfile?>(StateApp.instance.scopeGetter, { PolycentricCache.instance.getProfileAsync(it) })
            .success { it -> setPolycentricProfile(it, animate = true) }
            .exception<Throwable> {
                Logger.w(TAG, "Failed to load claims.", it);
            };

        constructor(context: Context) : super(context) {
            inflate(context, R.layout.fragview_post_detail, this);

            val root = findViewById<FrameLayout>(R.id.root);

            _creatorThumbnail = findViewById(R.id.creator_thumbnail);
            _buttonSubscribe = findViewById(R.id.button_subscribe);
            _channelName = findViewById(R.id.text_channel_name);
            _channelMeta = findViewById(R.id.text_channel_meta);
            _textTitle = findViewById(R.id.text_title);
            _textMeta = findViewById(R.id.text_meta);
            _textContent = findViewById(R.id.text_content);
            _platformIndicator = findViewById(R.id.platform_indicator);
            _buttonShare = findViewById(R.id.button_share);

            _buttonSupport = findViewById(R.id.button_support);
            _buttonStore = findViewById(R.id.button_store);
            _layoutMonetization = findViewById(R.id.layout_monetization);

            _layoutRating = findViewById(R.id.layout_rating);
            _imageLikeIcon = findViewById(R.id.image_like_icon);
            _textLikes = findViewById(R.id.text_likes);
            _imageDislikeIcon = findViewById(R.id.image_dislike_icon);
            _textDislikes = findViewById(R.id.text_dislikes);

            _commentsList = findViewById(R.id.comments_list);
            _textCommentType = findViewById(R.id.text_comment_type);
            _toggleCommentType = findViewById(R.id.toggle_comment_type);
            _textComments = findViewById(R.id.text_comments);
            _addCommentView = findViewById(R.id.add_comment_view);

            _rating = findViewById(R.id.rating);

            _layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);
            _imageLoader = findViewById(R.id.image_loader);

            _imageActive = findViewById(R.id.image_active);
            _layoutThumbnails = findViewById(R.id.layout_thumbnails);

            _repliesOverlay = findViewById(R.id.replies_overlay);

            _textContent.setPlatformPlayerLinkMovementMethod(context);

            _buttonSubscribe.onSubscribed.subscribe {
                //TODO: add overlay to layout
                //UISlideOverlays.showSubscriptionOptionsOverlay(it, _overlayContainer);
            };

            val layoutTop: LinearLayout = findViewById(R.id.layout_top);
            root.removeView(layoutTop);
            _commentsList.setPrependedView(layoutTop);

            _commentsList.onCommentsLoaded.subscribe {
                updateCommentType(false);
            };

            _commentsList.onRepliesClick.subscribe { c ->
                val replyCount = c.replyCount ?: 0;
                var metadata = "";
                if (replyCount > 0) {
                    metadata += "$replyCount " + context.getString(R.string.replies);
                }

                if (c is PolycentricPlatformComment) {
                    var parentComment: PolycentricPlatformComment = c;
                    _repliesOverlay.load(_toggleCommentType.value, metadata, c.contextUrl, c.reference, c,
                        { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) },
                        {
                            val newComment = parentComment.cloneWithUpdatedReplyCount((parentComment.replyCount ?: 0) + 1);
                            _commentsList.replaceComment(parentComment, newComment);
                            parentComment = newComment;
                        });
                } else {
                    _repliesOverlay.load(_toggleCommentType.value, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) });
                }

                setRepliesOverlayVisible(isVisible = true, animate = true);
            };


            _toggleCommentType.onValueChanged.subscribe {
                updateCommentType(true);
            };

            _textCommentType.setOnClickListener {
                _toggleCommentType.setValue(!_toggleCommentType.value, true);
                updateCommentType(true);
            };

            _layoutMonetization.visibility = View.GONE;

            _buttonSupport.setOnClickListener {
                val author = _post?.author ?: _postOverview?.author;
                author?.let { _fragment.navigate<ChannelFragment>(it).selectTab(2); };
            };

            _buttonStore.setOnClickListener {
                _polycentricProfile?.profile?.systemState?.store?.let {
                    try {
                        val uri = Uri.parse(it);
                        val intent = Intent(Intent.ACTION_VIEW);
                        intent.data = uri;
                        context.startActivity(intent);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to open URI: '${it}'.", e);
                    }
                }
            };

            _addCommentView.onCommentAdded.subscribe {
                _commentsList.addComment(it);
            };

            _repliesOverlay.onClose.subscribe { setRepliesOverlayVisible(isVisible = false, animate = true); };

            _buttonShare.setOnClickListener { share() };

            _creatorThumbnail.onClick.subscribe { openChannel() };
            _channelName.setOnClickListener { openChannel() };
            _channelMeta.setOnClickListener { openChannel() };
        }

        private fun openChannel() {
            val author = _post?.author ?: _postOverview?.author ?: return;
            _fragment.navigate<ChannelFragment>(author);
        }

        private fun share() {
            try {
                Logger.i(PreviewPostView.TAG, "sharePost")

                val url = _post?.shareUrl ?: _postOverview?.shareUrl ?: _url;
                _fragment.startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND;
                    putExtra(Intent.EXTRA_TEXT, url);
                    type = "text/plain"; //TODO: Determine alt types?
                }, null));
            } catch (e: Throwable) {
                //Ignored
                Logger.e(PreviewPostView.TAG, "Failed to share.", e);
            }
        }

        private fun updatePolycentricRating() {
            _rating.visibility = View.GONE;

            val ref = Models.referenceFromBuffer((_post?.url ?: _postOverview?.url)?.toByteArray() ?: return)
            val extraBytesRef = (_post?.id?.value ?: _postOverview?.id?.value)?.let { if (it.isNotEmpty()) it.toByteArray() else null }
            val version = _version;

            _rating.onLikeDislikeUpdated.remove(this);
            _fragment.lifecycleScope.launch(Dispatchers.IO) {
                if (version != _version) {
                    return@launch;
                }

                try {
                    val queryReferencesResponse = ApiMethods.getQueryReferences(PolycentricCache.SERVER, ref, null,null,
                        arrayListOf(
                            Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder().setFromType(
                                ContentType.OPINION.value).setValue(
                                ByteString.copyFrom(Opinion.like.data)).build(),
                            Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder().setFromType(
                                ContentType.OPINION.value).setValue(
                                ByteString.copyFrom(Opinion.dislike.data)).build()
                        ),
                        extraByteReferences = listOfNotNull(extraBytesRef)
                    );

                    if (version != _version) {
                        return@launch;
                    }

                    val likes = queryReferencesResponse.countsList[0];
                    val dislikes = queryReferencesResponse.countsList[1];
                    val hasLiked = StatePolycentric.instance.hasLiked(ref.toByteArray())/* || extraBytesRef?.let { StatePolycentric.instance.hasLiked(it) } ?: false*/;
                    val hasDisliked = StatePolycentric.instance.hasDisliked(ref.toByteArray())/* || extraBytesRef?.let { StatePolycentric.instance.hasDisliked(it) } ?: false*/;

                    withContext(Dispatchers.Main) {
                        if (version != _version) {
                            return@withContext;
                        }

                        _rating.visibility = VISIBLE;
                        _rating.setRating(RatingLikeDislikes(likes, dislikes), hasLiked, hasDisliked);
                        _rating.onLikeDislikeUpdated.subscribe(this) { args ->
                            if (args.hasLiked) {
                                args.processHandle.opinion(ref, Opinion.like);
                            } else if (args.hasDisliked) {
                                args.processHandle.opinion(ref, Opinion.dislike);
                            } else {
                                args.processHandle.opinion(ref, Opinion.neutral);
                            }

                            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                                try {
                                    Logger.i(TAG, "Started backfill");
                                    args.processHandle.fullyBackfillServersAnnounceExceptions();
                                    Logger.i(TAG, "Finished backfill");
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "Failed to backfill servers", e)
                                }
                            }

                            StatePolycentric.instance.updateLikeMap(ref, args.hasLiked, args.hasDisliked)
                        };
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to get polycentric likes/dislikes.", e);
                    _rating.visibility = View.GONE;
                }
            }
        }

        private fun setPlatformRating(rating: IRating?) {
            if (rating == null) {
                _layoutRating.visibility = View.GONE;
                return;
            }

            _layoutRating.visibility = View.VISIBLE;

            when (rating) {
                is RatingLikeDislikes -> {
                    _textLikes.visibility = View.VISIBLE;
                    _imageLikeIcon.visibility = View.VISIBLE;
                    _textLikes.text = rating.likes.toHumanNumber();

                    _imageDislikeIcon.visibility = View.VISIBLE;
                    _textDislikes.visibility = View.VISIBLE;
                    _textDislikes.text = rating.dislikes.toHumanNumber();
                }
                is RatingLikes -> {
                    _textLikes.visibility = View.VISIBLE;
                    _imageLikeIcon.visibility = View.VISIBLE;
                    _textLikes.text = rating.likes.toHumanNumber();

                    _imageDislikeIcon.visibility = View.GONE;
                    _textDislikes.visibility = View.GONE;
                }
                else -> {
                    _textLikes.visibility = View.GONE;
                    _imageLikeIcon.visibility = View.GONE;
                    _imageDislikeIcon.visibility = View.GONE;
                    _textDislikes.visibility = View.GONE;
                }
            }
        }

        fun applyFragment(frag: PostDetailFragment): PostDetailView {
            _fragment = frag;
            return this;
        }

        fun clear() {
            _commentsList.cancel();
            _taskLoadPost.cancel();
            _taskLoadPolycentricProfile.cancel();
            _version++;

            _toggleCommentType.setValue(false, false);
            _url = null;
            _post = null;
            _postOverview = null;
            _creatorThumbnail.clear();
            //_buttonSubscribe.setSubscribeChannel(null); TODO: clear button
            _channelName.text = "";
            setChannelMeta(null);
            _textTitle.text = "";
            _textMeta.text = "";
            _textContent.text = "";
            setPlatformRating(null);
            _polycentricProfile = null;
            _rating.visibility = View.GONE;
            updatePolycentricRating();
            setRepliesOverlayVisible(isVisible = false, animate = false);
            setImages(null, null);

            _addCommentView.setContext(null, null);
            _platformIndicator.clearPlatform();
        }

        fun setPostDetails(value: IPlatformPostDetails) {
            _url = value.url;
            _post = value;

            _creatorThumbnail.setThumbnail(value.author.thumbnail, false);
            _buttonSubscribe.setSubscribeChannel(value.author.url);
            _channelName.text = value.author.name;
            setChannelMeta(value);
            _textTitle.text = value.name;
            _textMeta.text = value.datetime?.toHumanNowDiffString()?.let { "$it ago" } ?: "" //TODO: Include view count?
            _textContent.text = value.content.fixHtmlWhitespace();
            _platformIndicator.setPlatformFromClientID(value.id.pluginId);
            setPlatformRating(value.rating);
            setImages(value.thumbnails.filterNotNull(), value.images);

            //Fetch only when not already called in setPostOverview
            if (_postOverview == null) {
                fetchPolycentricProfile();
                updatePolycentricRating();
                _addCommentView.setContext(value.url, Models.referenceFromBuffer(value.url.toByteArray()));
            }

            updateCommentType(true);
            setLoading(false);
        }

        fun setPostOverview(value: IPlatformPost) {
            clear();
            _url = value.url;
            _postOverview = value;

            _creatorThumbnail.setThumbnail(value.author.thumbnail, false);
            _buttonSubscribe.setSubscribeChannel(value.author.url);
            _channelName.text = value.author.name;
            setChannelMeta(value);
            _textTitle.text = value.name;
            _textMeta.text = value.datetime?.toHumanNowDiffString()?.let { "$it ago" } ?: "" //TODO: Include view count?
            _textContent.text = value.description.fixHtmlWhitespace();
            _platformIndicator.setPlatformFromClientID(value.id.pluginId);
            _addCommentView.setContext(value.url, Models.referenceFromBuffer(value.url.toByteArray()));

            updatePolycentricRating();
            fetchPolycentricProfile();
            fetchPost();
        }

        private fun setImages(images: List<Thumbnails>?, fullImages: List<String>?) {
            for (child in _layoutThumbnails.children) {
                if (child is ImageView) {
                    Glide.with(child).clear(child);
                }
            }

            _layoutThumbnails.removeAllViews();

            if (images.isNullOrEmpty() || fullImages.isNullOrEmpty()) {
                _imageActive.visibility = View.GONE;
                _layoutThumbnails.visibility = View.GONE;
                return;
            }

            _imageActive.visibility = View.VISIBLE;

            Glide.with(_imageActive)
                .load(fullImages[0])
                .crossfade()
                .into(_imageActive);

            if (images.size > 1) {
                val dp_6f = 6.dp(resources).toFloat()
                val dp_5 = 5.dp(resources)
                val dp_12 = 12.dp(resources)
                val dp_90 = 90.dp(resources)

                for (i in 0 until min(images.size, fullImages.size)) {
                    val image = images[i];
                    val fullImage = fullImages[i];

                    _layoutThumbnails.addView(ShapeableImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = FlexboxLayout.LayoutParams(dp_90, dp_90).apply { setContentPadding(dp_5, dp_12, dp_5, 0) }
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, dp_6f).build()
                    }.apply {
                        Glide.with(this)
                            .load(image.getLQThumbnail())
                            .crossfade()
                            .into(this);

                        setOnClickListener {
                            Glide.with(_imageActive)
                                .load(fullImage)
                                .crossfade()
                                .into(_imageActive);
                        }
                    });
                }

                _layoutThumbnails.visibility = View.VISIBLE;
            } else {
                _layoutThumbnails.visibility = View.GONE;
            }
        }

        private fun setRepliesOverlayVisible(isVisible: Boolean, animate: Boolean) {
            if (_isRepliesVisible == isVisible) {
                return;
            }

            _isRepliesVisible = isVisible;
            _repliesAnimator?.cancel();

            if (isVisible) {
                _repliesOverlay.visibility = View.VISIBLE;

                if (animate) {
                    _repliesOverlay.translationY = _repliesOverlay.height.toFloat();

                    _repliesAnimator = _repliesOverlay.animate()
                        .setDuration(300)
                        .translationY(0f)
                        .withEndAction {
                            _repliesAnimator = null;
                        }.apply { start() };
                }
            } else {
                if (animate) {
                    _repliesOverlay.translationY = 0f;

                    _repliesAnimator = _repliesOverlay.animate()
                        .setDuration(300)
                        .translationY(_repliesOverlay.height.toFloat())
                        .withEndAction {
                            _repliesOverlay.visibility = GONE;
                            _repliesAnimator = null;
                        }.apply { start(); }
                } else {
                    _repliesOverlay.visibility = View.GONE;
                    _repliesOverlay.translationY = _repliesOverlay.height.toFloat();
                }
            }
        }

        private fun fetchPolycentricProfile() {
            val author = _post?.author ?: _postOverview?.author ?: return;
            val cachedPolycentricProfile = PolycentricCache.instance.getCachedProfile(author.url, true);
            if (cachedPolycentricProfile != null) {
                setPolycentricProfile(cachedPolycentricProfile, animate = false);
                if (cachedPolycentricProfile.expired) {
                    _taskLoadPolycentricProfile.run(author.id);
                }
            } else {
                setPolycentricProfile(null, animate = false);
                _taskLoadPolycentricProfile.run(author.id);
            }
        }

        private fun setChannelMeta(value: IPlatformPost?) {
            val subscribers = value?.author?.subscribers;
            if(subscribers != null && subscribers > 0) {
                _channelMeta.visibility = View.VISIBLE;
                _channelMeta.text = if((value.author.subscribers ?: 0) > 0) value.author.subscribers!!.toHumanNumber() + " " + context.getString(R.string.subscribers) else "";
            } else {
                _channelMeta.visibility = View.GONE;
                _channelMeta.text = "";
            }
        }

        fun setPostUrl(url: String) {
            clear();
            _url = url;
            fetchPost();
        }

        fun onDestroy() {
            _commentsList.cancel();
            _taskLoadPost.cancel();
            _repliesOverlay.cleanup();
        }

        private fun setPolycentricProfile(cachedPolycentricProfile: PolycentricCache.CachedPolycentricProfile?, animate: Boolean) {
            _polycentricProfile = cachedPolycentricProfile;

            if (cachedPolycentricProfile?.profile == null) {
                _layoutMonetization.visibility = View.GONE;
                _creatorThumbnail.setHarborAvailable(false, animate, null);
                return;
            }

            _layoutMonetization.visibility = View.VISIBLE;
            _creatorThumbnail.setHarborAvailable(true, animate, cachedPolycentricProfile.profile.system.toProto());
        }

        private fun fetchPost() {
            Logger.i(TAG, "fetchVideo")
            _post = null;

            val url = _url;
            if (!url.isNullOrBlank()) {
                setLoading(true);
                _taskLoadPost.run(url);
            }
        }

        private fun fetchComments() {
            Logger.i(TAG, "fetchComments")
            _post?.let {
                _commentsList.load(true) { StatePlatform.instance.getComments(it); };
            }
        }

        private fun fetchPolycentricComments() {
            Logger.i(TAG, "fetchPolycentricComments")
            val post = _post;
            val ref = (_post?.url ?: _postOverview?.url)?.toByteArray()?.let { Models.referenceFromBuffer(it) }
            val extraBytesRef = (_post?.id?.value ?: _postOverview?.id?.value)?.let { if (it.isNotEmpty()) it.toByteArray() else null }

            if (ref == null) {
                Logger.w(TAG, "Failed to fetch polycentric comments because url was not set null")
                _commentsList.clear();
                return
            }

            _commentsList.load(false) { StatePolycentric.instance.getCommentPager(post!!.url, ref, listOfNotNull(extraBytesRef)); };
        }

        private fun updateCommentType(reloadComments: Boolean) {
            if (_toggleCommentType.value) {
                _textCommentType.text = "Platform";
                _addCommentView.visibility = View.GONE;

                if (reloadComments) {
                    fetchComments();
                }
            } else {
                _textCommentType.text = "Polycentric";
                _addCommentView.visibility = View.VISIBLE;

                if (reloadComments) {
                    fetchPolycentricComments()
                }
            }
        }

        private fun setLoading(isLoading : Boolean) {
            if (_isLoading == isLoading) {
                return;
            }

            _isLoading = isLoading;

            if(isLoading) {
                (_imageLoader.drawable as Animatable?)?.start()
                _layoutLoadingOverlay.visibility = View.VISIBLE;
            }
            else {
                _layoutLoadingOverlay.visibility = View.GONE;
                (_imageLoader.drawable as Animatable?)?.stop()
            }
        }

        companion object {
            const val TAG = "PostDetailFragment"
        }
    }

    companion object {
        fun newInstance() = PostDetailFragment().apply {}
    }
}
