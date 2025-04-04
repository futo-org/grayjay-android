package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.lists.VideoListEditorView
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput

class QueueEditorOverlay : LinearLayout {

    private val _topbar : OverlayTopbar;
    private val _editor : VideoListEditorView;
    private val _btnSettings: ImageView;

    private val _overlayContainer: FrameLayout;


    val onOptions = Event1<IPlatformVideo>();
    val onClose = Event0();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_queue, this)
        _topbar = findViewById(R.id.topbar);
        _editor = findViewById(R.id.editor);
        _btnSettings = findViewById(R.id.button_settings);
        _overlayContainer = findViewById(R.id.overlay_container_queue);


        _topbar.onClose.subscribe(this, onClose::emit);
        _editor.onVideoOrderChanged.subscribe { StatePlayer.instance.setQueueWithExisting(it) }
        _editor.onVideoOptions.subscribe { v ->
            onOptions?.emit(v);
        }
        _editor.onVideoRemoved.subscribe { v ->
            StatePlayer.instance.removeFromQueue(v);
            _topbar.setInfo(context.getString(R.string.queue), "${StatePlayer.instance.queueSize} " + context.getString(R.string.videos));
        }
        _editor.onVideoClicked.subscribe { v -> StatePlayer.instance.setQueuePosition(v) }

        _btnSettings.setOnClickListener {
            handleSettings();
        }

        _topbar.setInfo(context.getString(R.string.queue), "");
    }

    fun updateQueue() {
        val queue = StatePlayer.instance.getQueue();
        _editor.setVideos(queue, true);
        _topbar.setInfo(context.getString(R.string.queue), "${queue.size} " + context.getString(R.string.videos));
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
    }

    fun handleSettings() {
        UISlideOverlays.showQueueOptionsOverlay(context, _overlayContainer);
    }
}