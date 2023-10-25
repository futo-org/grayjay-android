package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.lists.VideoListEditorView

class QueueEditorOverlay : LinearLayout {

    private val _topbar : OverlayTopbar;
    private val _editor : VideoListEditorView;

    val onClose = Event0();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_queue, this)
        _topbar = findViewById(R.id.topbar);
        _editor = findViewById(R.id.editor);

        _topbar.onClose.subscribe(this, onClose::emit);
        _editor.onVideoOrderChanged.subscribe { StatePlayer.instance.setQueueWithExisting(it) }
        _editor.onVideoRemoved.subscribe { v -> StatePlayer.instance.removeFromQueue(v) }
        _editor.onVideoClicked.subscribe { v -> StatePlayer.instance.setQueuePosition(v) }

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
}