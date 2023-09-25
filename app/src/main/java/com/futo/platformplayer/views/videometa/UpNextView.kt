package com.futo.platformplayer.views.videometa

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber

class UpNextView : LinearLayout {
    private val _layoutContainer: LinearLayout;
    private val _textType: TextView;
    private val _textTitle: TextView;
    private val _imageThumbnail: ImageView;
    private val _textMetadata: TextView;
    private val _imageChannelThumbnail: ImageView;
    private val _textChannelName: TextView;
    private val _textPosition: TextView;
    private val _textUpNext: TextView;
    private val _buttonClear: LinearLayout;
    private val _buttonShuffle: LinearLayout;
    private val _buttonRepeat: LinearLayout;
    private val _buttonView: LinearLayout;
    private val _layoutRepeatDivider: FrameLayout;
    private val _layoutQueueBox: ConstraintLayout;
    private val _layoutEndOfPlaylist: ConstraintLayout;
    private val _textEndOfQueue: TextView;
    private val _buttonRestartNow: LinearLayout;

    val onNextItem = Event0();
    val onRestartQueue = Event0();
    val onOpenQueueClick = Event0();

    private val _activeColor = ContextCompat.getColor(context, R.color.gray_0d);
    private val _inactiveColor = ContextCompat.getColor(context, R.color.gray_16);

    constructor(context: Context, attrs: AttributeSet? = null): super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_up_next, this, true);

        _layoutContainer = findViewById(R.id.videodetail_queue);
        _textType = findViewById(R.id.videodetail_queue_type);
        _textTitle = findViewById(R.id.videodetail_queue_title);
        _textMetadata = findViewById(R.id.videodetail_queue_meta);
        _imageThumbnail = findViewById(R.id.videodetail_queue_thumbnail);
        _textChannelName = findViewById(R.id.videodetail_queue_channel_name);
        _imageChannelThumbnail = findViewById(R.id.videodetail_queue_channel_image);
        _textPosition = findViewById(R.id.videodetail_queue_position);
        _textUpNext = findViewById(R.id.videodetail_up_next);
        _buttonClear = findViewById(R.id.button_clear);
        _buttonShuffle = findViewById(R.id.button_shuffle);
        _buttonRepeat = findViewById(R.id.button_repeat);
        _buttonView = findViewById(R.id.button_view);
        _layoutRepeatDivider = findViewById(R.id.layout_repeat_divider);
        _layoutQueueBox = findViewById(R.id.videodetail_queue_box);
        _layoutEndOfPlaylist = findViewById(R.id.videodetail_end_of_playlist)
        _textEndOfQueue = findViewById(R.id.text_end_of_queue);
        _buttonRestartNow = findViewById(R.id.button_restart_now);

        _buttonClear.setOnClickListener { StatePlayer.instance.clearQueue(); };
        _buttonShuffle.setOnClickListener {
            StatePlayer.instance.setQueueShuffle(!StatePlayer.instance.queueShuffle);
            update();
        };

        _buttonRepeat.setOnClickListener {
            StatePlayer.instance.setQueueRepeat(!StatePlayer.instance.queueRepeat);
            update();
        };

        _buttonView.setOnClickListener { onOpenQueueClick.emit(); };

        _imageThumbnail.setOnClickListener { onNextItem.emit() };
        _textTitle.setOnClickListener { onNextItem.emit(); };

        _buttonRestartNow.setOnClickListener {
            onRestartQueue.emit();
        };
    }

    private fun updateRepeatButton() {
        if (StatePlayer.instance.queueRepeat)
            _buttonRepeat.setBackgroundColor(_activeColor);
        else
            _buttonRepeat.setBackgroundColor(_inactiveColor);
    }

    private fun updateShuffleButton() {
        if (StatePlayer.instance.queueShuffle)
            _buttonShuffle.setBackgroundColor(_activeColor);
        else
            _buttonShuffle.setBackgroundColor(_inactiveColor);
    }

    fun update()
    {
        updateShuffleButton();
        updateRepeatButton();

        val isPlaylist = StatePlayer.instance.getQueueLength() > 1;
        if (!isPlaylist) {
            _layoutContainer.visibility = View.GONE;
            return;
        }

        val nextItem = StatePlayer.instance.getNextQueueItem();

        //End of queue
        if (nextItem == null) {
            if(StatePlayer.instance.getQueueLength() <= 0) {
                _layoutContainer.visibility = View.GONE;
                return;
            }

            _layoutQueueBox.visibility = View.GONE;
            _layoutEndOfPlaylist.visibility = View.VISIBLE;

            when (StatePlayer.instance.getQueueType()){
                StatePlayer.TYPE_WATCHLATER -> {
                    _buttonRestartNow.visibility = View.GONE;
                    _textEndOfQueue.text = resources.getString(R.string.end_of_watch_later_reached);
                }
                //TODO: This case doesn't make sense as queue deletes items as they finish
                StatePlayer.TYPE_QUEUE -> {
                    _buttonRestartNow.visibility = View.VISIBLE;
                    _textEndOfQueue.text = if (StatePlayer.instance.queueRepeat) resources.getString(R.string.the_queue_will_restart_after_the_video_is_finished) else resources.getString(R.string.end_of_queue_reached);
                }
                StatePlayer.TYPE_PLAYLIST -> {
                    _buttonRestartNow.visibility = View.VISIBLE;
                    _textEndOfQueue.text = if (StatePlayer.instance.queueRepeat) resources.getString(R.string.the_playlist_will_restart_after_the_video_is_finished) else resources.getString(R.string.end_of_playlist_reached);
                }
            }
        }
        //Next Item
        else {
            _layoutQueueBox.visibility = View.VISIBLE;
            _layoutEndOfPlaylist.visibility = View.GONE;

            _textTitle.text = nextItem.name ?: "";

            val metadataTokens = mutableListOf<String>();
            if (nextItem.viewCount > 0) {
                metadataTokens.add("${nextItem.viewCount.toHumanNumber()} views");
            }

            if (nextItem.datetime != null) {
                metadataTokens.add(nextItem.datetime!!.toHumanNowDiffString())
            }

            _textMetadata.text = metadataTokens.joinToString(" • ");
            _textChannelName.text = nextItem.author.name ?: "";
            Glide.with(_imageThumbnail)
                .load(nextItem.thumbnails.getHQThumbnail())
                .placeholder(R.drawable.placeholder_video_thumbnail)
                .into(_imageThumbnail);
            Glide.with(_imageChannelThumbnail)
                .load(nextItem.author.thumbnail)
                .placeholder(R.drawable.placeholder_video_thumbnail)
                .into(_imageChannelThumbnail);
        }
        _layoutContainer.visibility = View.VISIBLE;
        _textUpNext.text = StatePlayer.instance.getQueueType();
        _textType.text = if (StatePlayer.instance.queueName != StatePlayer.instance.getQueueType()) { StatePlayer.instance.queueName } else { "" };
        _textPosition.text = "${StatePlayer.instance.getQueueProgress() + 1}/${StatePlayer.instance.getQueueLength()}";

        val repeatButtonEnabled = StatePlayer.instance.getQueueType() != StatePlayer.TYPE_WATCHLATER;
        if (!repeatButtonEnabled) {
            _buttonRepeat.visibility = View.GONE;
            _layoutRepeatDivider.visibility = View.GONE;
        } else {
            _buttonRepeat.visibility = View.VISIBLE;
            _layoutRepeatDivider.visibility = View.VISIBLE;
        }
    }
}