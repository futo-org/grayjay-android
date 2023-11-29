package com.futo.platformplayer.views.adapters.feedtypes

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.images.GlideHelper.Companion.loadThumbnails
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.platform.PlatformIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PreviewNestedVideoView : PreviewVideoView {

    protected val _platformIndicatorNested: PlatformIndicator;
    protected val _containerLoader: LinearLayout;
    protected val _loaderView: LoaderView;
    protected val _containerUnavailable: LinearLayout;
    protected val _textNestedUrl: TextView;

    private var _content: IPlatformContent? = null;
    private var _contentNested: IPlatformContentDetails? = null;

    private var _contentSupported = false;

    val onContentUrlClicked = Event2<String, ContentType>();

    constructor(context: Context, feedStyle: FeedStyle, exoPlayer: PlayerManager? = null): super(context, feedStyle, exoPlayer) {
        _platformIndicatorNested = findViewById(R.id.thumbnail_platform_nested);
        _containerLoader = findViewById(R.id.container_loader);
        _loaderView = findViewById(R.id.loader);
        _containerUnavailable = findViewById(R.id.container_unavailable);
        _textNestedUrl = findViewById(R.id.text_nested_url);

        _imageChannel?.setOnClickListener { _contentNested?.let { onChannelClicked.emit(it.author) }  };
        _textChannelName.setOnClickListener { _contentNested?.let { onChannelClicked.emit(it.author) }  };
        _textVideoMetadata.setOnClickListener { _contentNested?.let { onChannelClicked.emit(it.author) }  };
        _button_add_to.setOnClickListener {
            if(_contentNested is IPlatformVideo)
                _contentNested?.let { onAddToClicked.emit(it as IPlatformVideo) }
            else _content?.let {
                if(it is IPlatformNestedContent)
                    loadNested(it) {
                        if(it is IPlatformVideo)
                            onAddToClicked.emit(it);
                        else
                            UIDialogs.toast(context, "Content is not a video");
                    }
            }
        };
        _button_add_to_queue.setOnClickListener {
            if(_contentNested is IPlatformVideo)
                _contentNested?.let { onAddToQueueClicked.emit(it as IPlatformVideo) }
            else _content?.let {
                if(it is IPlatformNestedContent)
                    loadNested(it) {
                        if(it is IPlatformVideo)
                            onAddToQueueClicked.emit(it);
                        else
                            UIDialogs.toast(context, "Content is not a video");
                    }
            }
        };
    }

    override fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_video_preview_nested
            else -> R.layout.list_video_thumbnail_nested
        }, this)
    }

    override fun onOpenClicked() {
        if(_contentNested is IPlatformVideoDetails)
            super.onOpenClicked();
        else if(_content is IPlatformNestedContent) {
            (_content as IPlatformNestedContent).let {
                onContentUrlClicked.emit(it.contentUrl, if(_contentSupported) it.nestedContentType else ContentType.URL);
            };
        }
    }


    override fun bind(content: IPlatformContent) {
        _content = content;
        _contentNested = null;

        super.bind(content);

        _platformIndicator.setPlatformFromClientID(content.id.pluginId);
        _platformIndicatorNested.setPlatformFromClientID(content.id.pluginId);

        if(content is IPlatformNestedContent) {
            _textNestedUrl.text = content.contentUrl;
            _imageVideo.loadThumbnails(content.contentThumbnails, true) {
                it.placeholder(R.drawable.placeholder_video_thumbnail)
                    .into(_imageVideo);
            };


            _contentSupported = content.contentSupported;
            if(!_contentSupported) {
                _containerUnavailable.visibility = View.VISIBLE;
                _containerLoader.visibility = View.GONE;
                _loaderView.stop();
            }
            else {
                if(_feedStyle == FeedStyle.THUMBNAIL)
                    _platformIndicator.setPlatformFromClientID(content.contentPlugin);
                else
                    _platformIndicatorNested.setPlatformFromClientID(content.contentPlugin);
                _containerUnavailable.visibility = View.GONE;
                if(_feedStyle == FeedStyle.PREVIEW)
                    loadNested(content);
            }
        }
        else {
            _contentSupported = false;
            _containerUnavailable.visibility = View.VISIBLE;
            _containerLoader.visibility = View.GONE;
            _loaderView.stop();
        }
    }

    private fun loadNested(content: IPlatformNestedContent, onCompleted: ((IPlatformContentDetails)->Unit)? = null) {
        Logger.i(TAG, "Loading nested content [${content.contentUrl}]");
        _containerLoader.visibility = View.VISIBLE;
        _loaderView.start();
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            val def = StatePlatform.instance.getContentDetails(content.contentUrl);
            def.invokeOnCompletion {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    Logger.i(TAG, "Loaded nested content [${content.contentUrl}] (${_content == content})");
                    if(it != null) {
                        Logger.e(TAG, "Failed to load nested", it);
                        if(_content == content) {
                            _containerUnavailable.visibility = View.VISIBLE;
                            _containerLoader.visibility = View.GONE;
                            _loaderView.stop();
                        }
                        //TODO: Handle exception
                    }
                    else if(_content == content) {
                        _containerLoader.visibility = View.GONE;
                        _loaderView.stop();
                        val nestedContent = def.getCompleted();
                        _contentNested = nestedContent;
                        if(nestedContent is IPlatformVideoDetails) {
                            super.bind(nestedContent);
                            if(_feedStyle == FeedStyle.PREVIEW) {
                                _platformIndicator.setPlatformFromClientID(content.id.pluginId);
                                _platformIndicatorNested.setPlatformFromClientID(nestedContent.id.pluginId);
                            }
                            else
                                _platformIndicatorNested.setPlatformFromClientID(content.id.pluginId);
                        }
                        else {
                            _containerUnavailable.visibility = View.VISIBLE;
                        }
                        if(onCompleted != null)
                            onCompleted(nestedContent);
                    }
                }
            };
        }
    }

    override fun preview(video: IPlatformContentDetails?, paused: Boolean) {
        if(video != null)
            super.preview(video, paused);
        else if(_content is IPlatformVideoDetails) _contentNested?.let {
            super.preview(it, paused);
        };
    }

    companion object {
        val TAG = "PreviewNestedVideoView";
    }
}