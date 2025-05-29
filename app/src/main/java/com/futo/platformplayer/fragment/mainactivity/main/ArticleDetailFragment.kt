package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.article.IPlatformArticle
import com.futo.platformplayer.api.media.models.article.IPlatformArticleDetails
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.IPlatformPostDetails
import com.futo.platformplayer.api.media.models.post.TextType
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.models.JSArticleDetails
import com.futo.platformplayer.api.media.platforms.js.models.JSImagesSegment
import com.futo.platformplayer.api.media.platforms.js.models.JSNestedSegment
import com.futo.platformplayer.api.media.platforms.js.models.JSTextSegment
import com.futo.platformplayer.api.media.platforms.js.models.SegmentType
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.fixHtmlWhitespace
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.feedtypes.PreviewLockedView
import com.futo.platformplayer.views.adapters.feedtypes.PreviewNestedVideoView
import com.futo.platformplayer.views.adapters.feedtypes.PreviewPostView
import com.futo.platformplayer.views.adapters.feedtypes.PreviewVideoView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.overlays.RepliesOverlay
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.segments.CommentsList
import com.futo.platformplayer.views.subscriptions.SubscribeButton
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.ContentType
import com.futo.polycentric.core.Models
import com.futo.polycentric.core.Opinion
import com.futo.polycentric.core.PolycentricProfile
import com.futo.polycentric.core.fullyBackfillServersAnnounceExceptions
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

class ArticleDetailFragment : MainFragment {
    override val isMainView: Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _viewDetail: ArticleDetailView? = null;

    constructor() : super() { }

    override fun onBackPressed(): Boolean {
        return false;
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ArticleDetailView(inflater.context).applyFragment(this);
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

        if (parameter is IPlatformArticleDetails) {
            _viewDetail?.clear();
            _viewDetail?.setArticleDetails(parameter);
        } else if (parameter is IPlatformArticle) {
            _viewDetail?.setArticleOverview(parameter);
        } else if(parameter is String) {
            _viewDetail?.setPostUrl(parameter);
        }
    }

    private class ArticleDetailView : ConstraintLayout {
        private lateinit var _fragment: ArticleDetailFragment;
        private var _url: String? = null;
        private var _isLoading = false;
        private var _article: IPlatformArticleDetails? = null;
        private var _articleOverview: IPlatformArticle? = null;
        private var _polycentricProfile: PolycentricProfile? = null;
        private var _version = 0;
        private var _isRepliesVisible: Boolean = false;
        private var _repliesAnimator: ViewPropertyAnimator? = null;

        private val _creatorThumbnail: CreatorThumbnail;
        private val _buttonSubscribe: SubscribeButton;
        private val _channelName: TextView;
        private val _channelMeta: TextView;
        private val _textTitle: TextView;
        private val _textMeta: TextView;
        private val _textSummary: TextView;
        private val _containerSegments: LinearLayout;
        private val _platformIndicator: PlatformIndicator;
        private val _buttonShare: ImageButton;

        private val _layoutRating: LinearLayout;
        private val _imageLikeIcon: ImageView;
        private val _textLikes: TextView;
        private val _imageDislikeIcon: ImageView;
        private val _textDislikes: TextView;

        private val _addCommentView: AddCommentView;

        private val _rating: PillRatingLikesDislikes;

        private val _layoutLoadingOverlay: FrameLayout;
        private val _imageLoader: ImageView;

        private var _overlayContainer: FrameLayout
        private val _repliesOverlay: RepliesOverlay;

        private val _commentsList: CommentsList;

        private var _commentType: Boolean? = null;
        private val _buttonPolycentric: Button
        private val _buttonPlatform: Button

        private val _taskLoadPost = if(!isInEditMode) TaskHandler<String, IPlatformArticleDetails>(
            StateApp.instance.scopeGetter,
            {
                val result = StatePlatform.instance.getContentDetails(it).await();
                if(result !is IPlatformArticleDetails)
                    throw IllegalStateException(context.getString(R.string.expected_media_content_found) + " ${result.contentType}");
                return@TaskHandler result;
            })
            .success { setArticleDetails(it) }
            .exception<Throwable> {
                Logger.w(ChannelFragment.TAG, context.getString(R.string.failed_to_load_post), it);
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_post), it, ::fetchPost, null, _fragment);
            } else TaskHandler(IPlatformPostDetails::class.java) { _fragment.lifecycleScope };

        private val _taskLoadPolycentricProfile = TaskHandler<PlatformID, PolycentricProfile?>(StateApp.instance.scopeGetter, {
            if (!StatePolycentric.instance.enabled)
                return@TaskHandler null

            ApiMethods.getPolycentricProfileByClaim(ApiMethods.SERVER, ApiMethods.FUTO_TRUST_ROOT, it.claimFieldType.toLong(), it.claimType.toLong(), it.value!!)
        })
            .success { it -> setPolycentricProfile(it, animate = true) }
            .exception<Throwable> {
                Logger.w(TAG, "Failed to load claims.", it);
            };

        constructor(context: Context) : super(context) {
            inflate(context, R.layout.fragview_article_detail, this);

            val root = findViewById<FrameLayout>(R.id.root);

            _creatorThumbnail = findViewById(R.id.creator_thumbnail);
            _buttonSubscribe = findViewById(R.id.button_subscribe);
            _channelName = findViewById(R.id.text_channel_name);
            _channelMeta = findViewById(R.id.text_channel_meta);
            _textTitle = findViewById(R.id.text_title);
            _textMeta = findViewById(R.id.text_meta);
            _textSummary = findViewById(R.id.text_summary);
            _containerSegments = findViewById(R.id.container_segments);
            _platformIndicator = findViewById(R.id.platform_indicator);
            _buttonShare = findViewById(R.id.button_share);

            _overlayContainer = findViewById(R.id.overlay_container);

            _layoutRating = findViewById(R.id.layout_rating);
            _imageLikeIcon = findViewById(R.id.image_like_icon);
            _textLikes = findViewById(R.id.text_likes);
            _imageDislikeIcon = findViewById(R.id.image_dislike_icon);
            _textDislikes = findViewById(R.id.text_dislikes);

            _commentsList = findViewById(R.id.comments_list);
            _addCommentView = findViewById(R.id.add_comment_view);

            _rating = findViewById(R.id.rating);

            _layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);
            _imageLoader = findViewById(R.id.image_loader);


            _repliesOverlay = findViewById(R.id.replies_overlay);

            _buttonPolycentric = findViewById(R.id.button_polycentric)
            _buttonPlatform = findViewById(R.id.button_platform)

            _buttonSubscribe.onSubscribed.subscribe {
                //TODO: add overlay to layout
                //UISlideOverlays.showSubscriptionOptionsOverlay(it, _overlayContainer);
            };

            val layoutTop: LinearLayout = findViewById(R.id.layout_top);
            root.removeView(layoutTop);
            _commentsList.setPrependedView(layoutTop);

            /*TODO: Why is this here?
            _commentsList.onCommentsLoaded.subscribe {
                updateCommentType(false);
            };*/

            _commentsList.onRepliesClick.subscribe { c ->
                val replyCount = c.replyCount ?: 0;
                var metadata = "";
                if (replyCount > 0) {
                    metadata += "$replyCount " + context.getString(R.string.replies);
                }

                if (c is PolycentricPlatformComment) {
                    var parentComment: PolycentricPlatformComment = c;
                    _repliesOverlay.load(_commentType!!, metadata, c.contextUrl, c.reference, c,
                        { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) },
                        {
                            val newComment = parentComment.cloneWithUpdatedReplyCount((parentComment.replyCount ?: 0) + 1);
                            _commentsList.replaceComment(parentComment, newComment);
                            parentComment = newComment;
                        });
                } else {
                    _repliesOverlay.load(_commentType!!, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) });
                }

                setRepliesOverlayVisible(isVisible = true, animate = true);
            };

            if (StatePolycentric.instance.enabled) {
                _buttonPolycentric.setOnClickListener {
                    updateCommentType(false)
                }
            } else {
                _buttonPolycentric.visibility = View.GONE
            }

            _buttonPlatform.setOnClickListener {
                updateCommentType(true)
            }

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
            val author = _article?.author ?: _articleOverview?.author ?: return;
            _fragment.navigate<ChannelFragment>(author);
        }

        private fun share() {
            try {
                Logger.i(PreviewPostView.TAG, "sharePost")

                val url = _article?.shareUrl ?: _articleOverview?.shareUrl ?: _url;
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

            val ref = Models.referenceFromBuffer((_article?.url ?: _articleOverview?.url)?.toByteArray() ?: return)
            val extraBytesRef = (_article?.id?.value ?: _articleOverview?.id?.value)?.let { if (it.isNotEmpty()) it.toByteArray() else null }
            val version = _version;

            _rating.onLikeDislikeUpdated.remove(this);

            if (!StatePolycentric.instance.enabled)
                return

            _fragment.lifecycleScope.launch(Dispatchers.IO) {
                if (version != _version) {
                    return@launch;
                }

                try {
                    val queryReferencesResponse = ApiMethods.getQueryReferences(ApiMethods.SERVER, ref, null,null,
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

        fun applyFragment(frag: ArticleDetailFragment): ArticleDetailView {
            _fragment = frag;
            return this;
        }

        fun clear() {
            _commentsList.cancel();
            _taskLoadPost.cancel();
            _taskLoadPolycentricProfile.cancel();
            _version++;

            updateCommentType(null)
            _url = null;
            _article = null;
            _articleOverview = null;
            _creatorThumbnail.clear();
            //_buttonSubscribe.setSubscribeChannel(null); TODO: clear button
            _channelName.text = "";
            setChannelMeta(null);
            _textTitle.text = "";
            _textMeta.text = "";
            setPlatformRating(null);
            _polycentricProfile = null;
            _rating.visibility = View.GONE;
            updatePolycentricRating();
            setRepliesOverlayVisible(isVisible = false, animate = false);

            _containerSegments.removeAllViews();

            _addCommentView.setContext(null, null);
            _platformIndicator.clearPlatform();
        }

        fun setArticleDetails(value: IPlatformArticleDetails) {
            _url = value.url;
            _article = value;

            _creatorThumbnail.setThumbnail(value.author.thumbnail, false);
            _buttonSubscribe.setSubscribeChannel(value.author.url);
            _channelName.text = value.author.name;
            setChannelMeta(value);
            _textTitle.text = value.name;
            _textMeta.text = value.datetime?.toHumanNowDiffString()?.let { "$it ago" } ?: "" //TODO: Include view count?

            _textSummary.text = value.summary
            _textSummary.isVisible = !value.summary.isNullOrEmpty()

            _platformIndicator.setPlatformFromClientID(value.id.pluginId);
            setPlatformRating(value.rating);

            for(seg in value.segments) {
                when(seg.type) {
                    SegmentType.TEXT -> {
                        if(seg is JSTextSegment) {
                            _containerSegments.addView(ArticleTextBlock(context, seg.content, seg.textType))
                        }
                    }
                    SegmentType.IMAGES -> {
                        if(seg is JSImagesSegment) {
                            if(seg.images.size > 0)
                                _containerSegments.addView(ArticleImageBlock(context, seg.images[0], seg.caption))
                        }
                    }
                    SegmentType.NESTED -> {
                        if(seg is JSNestedSegment) {
                            _containerSegments.addView(ArticleContentBlock(context, seg.nested, _fragment, _overlayContainer));
                        }
                    }
                    else ->{}
                }
            }

            //Fetch only when not already called in setPostOverview
            if (_articleOverview == null) {
                fetchPolycentricProfile();
                updatePolycentricRating();
                _addCommentView.setContext(value.url, Models.referenceFromBuffer(value.url.toByteArray()));
            }

            val commentType = !Settings.instance.other.polycentricEnabled || Settings.instance.comments.defaultCommentSection == 1
            updateCommentType(commentType, true);
            setLoading(false);
        }

        fun setArticleOverview(value: IPlatformArticle) {
            clear();
            _url = value.url;
            _articleOverview = value;

            _creatorThumbnail.setThumbnail(value.author.thumbnail, false);
            _buttonSubscribe.setSubscribeChannel(value.author.url);
            _channelName.text = value.author.name;
            setChannelMeta(value);
            _textTitle.text = value.name;
            _textMeta.text = value.datetime?.toHumanNowDiffString()?.let { "$it ago" } ?: "" //TODO: Include view count?

            _platformIndicator.setPlatformFromClientID(value.id.pluginId);
            _addCommentView.setContext(value.url, Models.referenceFromBuffer(value.url.toByteArray()));

            updatePolycentricRating();
            fetchPolycentricProfile();
            fetchPost();
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
            val author = _article?.author ?: _articleOverview?.author ?: return;
                setPolycentricProfile(null, animate = false);
                _taskLoadPolycentricProfile.run(author.id);
        }

        private fun setChannelMeta(value: IPlatformArticle?) {
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

        private fun setPolycentricProfile(polycentricProfile: PolycentricProfile?, animate: Boolean) {
            _polycentricProfile = polycentricProfile;

            val pp = _polycentricProfile;
            if (pp == null) {
                _creatorThumbnail.setHarborAvailable(false, animate, null);
                return;
            }

            _creatorThumbnail.setHarborAvailable(true, animate, pp.system.toProto());
        }

        private fun fetchPost() {
            Logger.i(TAG, "fetchVideo")
            _article = null;

            val url = _url;
            if (!url.isNullOrBlank()) {
                setLoading(true);
                _taskLoadPost.run(url);
            }
        }

        private fun fetchComments() {
            Logger.i(TAG, "fetchComments")
            _article?.let {
                _commentsList.load(true) { StatePlatform.instance.getComments(it); };
            }
        }

        private fun fetchPolycentricComments() {
            Logger.i(TAG, "fetchPolycentricComments")
            val post = _article;
            val ref = (_article?.url ?: _articleOverview?.url)?.toByteArray()?.let { Models.referenceFromBuffer(it) }
            val extraBytesRef = (_article?.id?.value ?: _articleOverview?.id?.value)?.let { if (it.isNotEmpty()) it.toByteArray() else null }

            if (ref == null) {
                Logger.w(TAG, "Failed to fetch polycentric comments because url was not set null")
                _commentsList.clear();
                return
            }

            _commentsList.load(false) { StatePolycentric.instance.getCommentPager(post!!.url, ref, listOfNotNull(extraBytesRef)); };
        }

        private fun updateCommentType(commentType: Boolean?, forceReload: Boolean = false) {
            val changed = commentType != _commentType
            _commentType = commentType

            if (commentType == null) {
                _buttonPlatform.setTextColor(resources.getColor(R.color.gray_ac))
                _buttonPolycentric.setTextColor(resources.getColor(R.color.gray_ac))
            } else {
                _buttonPlatform.setTextColor(resources.getColor(if (commentType) R.color.white else R.color.gray_ac))
                _buttonPolycentric.setTextColor(resources.getColor(if (!commentType) R.color.white else R.color.gray_ac))

                if (commentType) {
                    _addCommentView.visibility = View.GONE;

                    if (forceReload || changed) {
                        fetchComments();
                    }
                } else {
                    _addCommentView.visibility = View.VISIBLE;

                    if (forceReload || changed) {
                        fetchPolycentricComments()
                    }
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

        class ArticleTextBlock : LinearLayout {
            constructor(context: Context?, content: String, textType: TextType) : super(context){
                inflate(context, R.layout.view_segment_text, this);

                findViewById<TextView>(R.id.text_content)?.let {
                    if(textType == TextType.HTML)
                        it.text = Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT);
                    else if(textType == TextType.CODE) {
                        it.text = content;
                        it.setPadding(15.dp(resources));
                        it.setHorizontallyScrolling(true);
                        it.movementMethod = ScrollingMovementMethod();
                        it.setTypeface(Typeface.MONOSPACE);
                        it.setBackgroundResource(R.drawable.background_videodetail_description)
                    }
                    else
                        it.text = content;
                }
            }
        }
        class ArticleImageBlock: LinearLayout {
            constructor(context: Context?, image: String, caption: String? = null) : super(context){
                inflate(context, R.layout.view_segment_image, this);

                findViewById<ImageView>(R.id.image_content)?.let {
                    Glide.with(it)
                        .load(image)
                        .crossfade()
                        .into(it);
                }
                findViewById<TextView>(R.id.text_content)?.let {
                    if(caption?.isNullOrEmpty() == true)
                        it.isVisible = false;
                    else
                        it.text = caption;
                }
            }
        }
        class ArticleContentBlock: LinearLayout {
            constructor(context: Context, content: IPlatformContent?, fragment: ArticleDetailFragment? = null, overlayContainer: FrameLayout? = null): super(context) {
                if(content != null) {
                    var view: View? = null;
                    if(content is IPlatformNestedContent) {
                        view = PreviewNestedVideoView(context, FeedStyle.THUMBNAIL, null);
                        view.bind(content);
                        view.onContentUrlClicked.subscribe { a,b -> }
                    }
                    else if(content is IPlatformVideo) {
                        view = PreviewVideoView(context, FeedStyle.THUMBNAIL, null, true);
                        view.bind(content);
                        view.onVideoClicked.subscribe { a,b -> fragment?.navigate<VideoDetailFragment>(a) }
                        view.onChannelClicked.subscribe { a -> fragment?.navigate<ChannelFragment>(a) }
                        if(overlayContainer != null) {
                            view.onAddToClicked.subscribe { a -> UISlideOverlays.showVideoOptionsOverlay(a, overlayContainer) };
                        }
                        view.onAddToQueueClicked.subscribe { a -> StatePlayer.instance.addToQueue(a) }
                        view.onAddToWatchLaterClicked.subscribe { a ->
                            if(StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(content), true))
                                UIDialogs.toast("Added to watch later\n[${content.name}]")
                        }
                    }
                    else if(content is IPlatformPost) {
                        view = PreviewPostView(context, FeedStyle.THUMBNAIL);
                        view.bind(content);
                        view.onContentClicked.subscribe { a -> fragment?.navigate<PostDetailFragment>(a) }
                        view.onChannelClicked.subscribe { a -> fragment?.navigate<ChannelFragment>(a) }
                    }
                    else if(content is IPlatformArticle) {
                        view = PreviewPostView(context, FeedStyle.THUMBNAIL);
                        view.bind(content);
                        view.onContentClicked.subscribe { a -> fragment?.navigate<ArticleDetailFragment>(a) }
                        view.onChannelClicked.subscribe { a -> fragment?.navigate<ChannelFragment>(a) }
                    }
                    else if(content is IPlatformLockedContent) {
                        view = PreviewLockedView(context, FeedStyle.THUMBNAIL);
                        view.bind(content);
                    }
                    if(view != null)
                        addView(view);
                }
            }
        }


        companion object {
            const val TAG = "PostDetailFragment"
        }
    }

    companion object {
        fun newInstance() = ArticleDetailFragment().apply {}
    }
}
