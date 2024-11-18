package com.futo.platformplayer.views.lists

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.views.adapters.ItemMoveCallback
import com.futo.platformplayer.views.adapters.VideoListEditorAdapter
import java.util.*

class VideoListEditorView : FrameLayout {
    private val _videos : ArrayList<IPlatformVideo> = ArrayList();

    private var _adapterVideos: VideoListEditorAdapter? = null;

    val onVideoOrderChanged = Event1<List<IPlatformVideo>>()
    val onVideoRemoved = Event1<IPlatformVideo>();
    val onVideoClicked = Event1<IPlatformVideo>();
    val isEmpty get() = _videos.isEmpty();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        val recyclerPlaylist = RecyclerView(context, attrs);
        recyclerPlaylist.isSaveEnabled = false;

        recyclerPlaylist.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(recyclerPlaylist);

        val callback = ItemMoveCallback();
        val touchHelper = ItemTouchHelper(callback);
        val adapterVideos = VideoListEditorAdapter(touchHelper);
        recyclerPlaylist.adapter = adapterVideos;
        recyclerPlaylist.layoutManager = LinearLayoutManager(context);
        touchHelper.attachToRecyclerView(recyclerPlaylist);

        callback.onRowMoved.subscribe { fromPosition, toPosition ->
            synchronized(_videos) {
                if (fromPosition < toPosition) {
                    for (i in fromPosition until toPosition)
                        Collections.swap(_videos, i, i + 1)
                }
                else {
                    for (i in fromPosition downTo toPosition + 1)
                        Collections.swap(_videos, i, i - 1)
                }
                onVideoOrderChanged.emit(_videos.toList());
                adapterVideos.notifyItemMoved(fromPosition, toPosition);
            }
        };

        adapterVideos.onRemove.subscribe { v ->
            val executeDelete = {
                synchronized(_videos) {
                    val index = _videos.indexOf(v);
                    if(index >= 0) {
                        _videos.removeAt(index);
                        onVideoRemoved.emit(v);
                    }
                    adapterVideos.notifyItemRemoved(index);
                }
            }

            if (Settings.instance.other.playlistDeleteConfirmation) {
                UIDialogs.showConfirmationDialog(context, "Please confirm to delete", action = {
                    executeDelete()
                }, cancelAction = {

                }, doNotAskAgainAction = {
                    Settings.instance.other.playlistDeleteConfirmation = false
                    Settings.instance.save()
                })
            } else {
                executeDelete()
            }

        };
        adapterVideos.onClick.subscribe(onVideoClicked::emit);

        _adapterVideos = adapterVideos;
    }

    fun setVideos(videos: List<IPlatformVideo>?, canEdit: Boolean) {
        synchronized(_videos) {
            _videos.clear();
            _videos.addAll(videos ?: listOf());
            _adapterVideos?.setVideos(_videos, canEdit);
        }
    }

    fun addVideos(videos: List<IPlatformVideo>) {
        synchronized(_videos) {
            val index = _videos.size;
            _videos.addAll(videos);
            _adapterVideos?.notifyItemRangeInserted(index, videos.size);
        }
    }
}