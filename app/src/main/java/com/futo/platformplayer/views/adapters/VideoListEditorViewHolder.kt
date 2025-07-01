package com.futo.platformplayer.views.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.views.others.ProgressBar
import com.futo.platformplayer.views.platform.PlatformIndicator

class VideoListEditorViewHolder : ViewHolder {
    private val _root: ConstraintLayout;
    private val _imageThumbnail: ImageView;
    private val _textName: TextView;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _textVideoDuration: TextView;
    private val _containerDuration: LinearLayout;
    private val _containerLive: LinearLayout;
    private val _imageRemove: ImageButton;
    private val _imageOptions: ImageButton;
    private val _imageDragDrop: ImageButton;
    private val _platformIndicator: PlatformIndicator;
    private val _layoutDownloaded: FrameLayout;
    private val _timeBar: ProgressBar

    var video: IPlatformVideo? = null
        private set;

    val onClick = Event1<IPlatformVideo>();
    val onRemove = Event1<IPlatformVideo>();
    val onOptions = Event1<IPlatformVideo>();

    @SuppressLint("ClickableViewAccessibility")
    constructor(view: View, touchHelper: ItemTouchHelper? = null) : super(view) {
        _root = view.findViewById(R.id.root);
        _imageThumbnail = view.findViewById(R.id.image_video_thumbnail);
        _textName = view.findViewById(R.id.text_video_name);
        _textAuthor = view.findViewById(R.id.text_author);
        _textMetadata = view.findViewById(R.id.text_video_metadata);
        _textVideoDuration = view.findViewById(R.id.thumbnail_duration);
        _containerDuration = view.findViewById(R.id.thumbnail_duration_container);
        _containerLive = view.findViewById(R.id.thumbnail_live_container);
        _imageRemove = view.findViewById(R.id.image_trash);
        _imageOptions = view.findViewById(R.id.image_settings);
        _imageDragDrop = view.findViewById<ImageButton>(R.id.image_drag_drop);
        _platformIndicator = view.findViewById(R.id.thumbnail_platform);
        _timeBar = view.findViewById(R.id.time_bar);
        _layoutDownloaded = view.findViewById(R.id.layout_downloaded);

        _imageDragDrop.setOnTouchListener { _, event ->
            if (touchHelper != null && event.action == MotionEvent.ACTION_DOWN) {
                touchHelper.startDrag(this);
            }
            false
        };

        _root.setOnClickListener {
            val v = video ?: return@setOnClickListener;
            onClick.emit(v);
        };

        _imageRemove?.setOnClickListener {
            val v = video ?: return@setOnClickListener;
            onRemove.emit(v);
        };
        _imageOptions?.setOnClickListener {
            val v = video ?: return@setOnClickListener;
            onOptions.emit(v);
        }
    }

    fun bind(v: IPlatformVideo, canEdit: Boolean) {
        Glide.with(_imageThumbnail)
            .load(v.thumbnails.getHQThumbnail())
            .placeholder(R.drawable.placeholder_video_thumbnail)
            .crossfade()
            .into(_imageThumbnail);
        _textName.text = v.name;
        _textAuthor.text = v.author.name;

        if(v.duration > 0) {
            _textVideoDuration.text = v.duration.toHumanTime(false);
            _textVideoDuration.visibility = View.VISIBLE;
        }
        else
            _textVideoDuration.visibility = View.GONE;

        val historyPosition = StateHistory.instance.getHistoryPosition(v.url)
        _timeBar.progress = historyPosition.toFloat() / v.duration.toFloat();

        if(v.isLive) {
            _containerDuration.visibility = View.GONE;
            _containerLive.visibility = View.VISIBLE;
        }
        else {
            _containerLive.visibility = View.GONE;
            _containerDuration.visibility = View.VISIBLE;
        }

        if (canEdit) {
            _imageRemove.visibility = View.VISIBLE;
            _imageDragDrop.visibility = View.VISIBLE;
        } else {
            _imageRemove.visibility = View.GONE;
            _imageDragDrop.visibility = View.GONE;
        }

        var metadata = "";
        if (v.viewCount > 0)
            metadata += "${v.viewCount.toHumanNumber()} ${itemView.context.getString(R.string.views)} • ";
        metadata += v.datetime?.toHumanNowDiffString() ?: "";

        _platformIndicator.setPlatformFromClientID(v.id.pluginId);

        _textMetadata.text = metadata;

        _layoutDownloaded.visibility = if (StateDownloads.instance.isDownloaded(v.id)) View.VISIBLE else View.GONE;
        video = v;
    }
}