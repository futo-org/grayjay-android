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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
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
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.article.IPlatformArticleDetails
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.IPlatformPostDetails
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.platforms.js.models.JSWeb
import com.futo.platformplayer.api.media.platforms.js.models.JSWebDetails
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.fixHtmlWhitespace
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.adapters.ChannelTab
import com.futo.platformplayer.views.adapters.feedtypes.PreviewPostView
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

class WebDetailFragment : MainFragment {
    override val isMainView: Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _viewDetail: WebDetailView? = null;

    constructor() : super() { }

    override fun onBackPressed(): Boolean {
        return false;
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = WebDetailView(inflater.context).applyFragment(this);
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

        if (parameter is JSWeb) {
            _viewDetail?.clear();
            _viewDetail?.setWeb(parameter);
        }
        if (parameter is JSWebDetails) {
            _viewDetail?.clear();
            _viewDetail?.setWebDetails(parameter);
        }
    }

    private class WebDetailView : ConstraintLayout {
        private lateinit var _fragment: WebDetailFragment;
        private var _url: String? = null;
        private var _isLoading = false;
        private var _web: JSWebDetails? = null;

        private val _layoutLoadingOverlay: FrameLayout;
        private val _imageLoader: ImageView;

        private val _webview: WebView;

        private val _taskLoadPost = if(!isInEditMode) TaskHandler<String, JSWebDetails>(
            StateApp.instance.scopeGetter,
            {
                val result = StatePlatform.instance.getContentDetails(it).await();
                if(result !is JSWebDetails)
                    throw IllegalStateException(context.getString(R.string.expected_media_content_found) + " ${result.contentType}");
                return@TaskHandler result;
            })
            .success { setWebDetails(it) }
            .exception<Throwable> {
                Logger.w(ChannelFragment.TAG, context.getString(R.string.failed_to_load_post), it);
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_post), it, ::fetchPost, null, _fragment);
            } else TaskHandler(IPlatformPostDetails::class.java) { _fragment.lifecycleScope };


        constructor(context: Context) : super(context) {
            inflate(context, R.layout.fragview_web_detail, this);

            val root = findViewById<FrameLayout>(R.id.root);

            _layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);
            _imageLoader = findViewById(R.id.image_loader);

            _webview = findViewById(R.id.webview);
            _webview.webViewClient = object: WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url);
                    if(url != "about:blank")
                        setLoading(false);
                }
            }
        }


        fun applyFragment(frag: WebDetailFragment): WebDetailView {
            _fragment = frag;
            return this;
        }

        fun clear() {
            _webview.loadUrl("about:blank");
        }

        fun setWeb(value: JSWeb) {
            _url = value.url;
            setLoading(true);
            clear();
            fetchPost();
        }
        fun setWebDetails(value: JSWebDetails) {
            _web = value;
            setLoading(true);
            _webview.loadUrl("about:blank");
            if(!value.html.isNullOrEmpty())
                _webview.loadData(value.html, "text/html", null);
            else
                _webview.loadUrl(value.url ?: "about:blank");
        }

        private fun fetchPost() {
            Logger.i(WebDetailView.TAG, "fetchWeb")
            _web = null;

            val url = _url;
            if (!url.isNullOrBlank()) {
                setLoading(true);
                _taskLoadPost.run(url);
            }
        }

        fun onDestroy() {
            _webview.loadUrl("about:blank");
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
            const val TAG = "WebDetailFragment"
        }
    }

    companion object {
        fun newInstance() = WebDetailFragment().apply {}
    }
}
