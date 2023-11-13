package com.futo.platformplayer.views.adapters.viewholders

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.*
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.images.GlideHelper.Companion.loadThumbnails
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.adapters.AnyAdapter


class VideoDownloadViewHolder(_viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<VideoLocal>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_downloaded, _viewGroup, false)) {
    private var _video: VideoLocal? = null;

    private val _videoName: TextView = _view.findViewById(R.id.downloaded_video_name);
    private val _videoImage: ImageView = _view.findViewById(R.id.downloaded_video_image);
    private val _videoDuration: TextView = _view.findViewById(R.id.downloaded_video_duration);
    private val _videoAuthor: TextView = _view.findViewById(R.id.downloaded_author);
    private val _videoInfo: TextView = _view.findViewById(R.id.downloaded_video_info);
    private val _videoAddToQueue: ImageButton = _view.findViewById(R.id.button_add_to_queue);
    private val _videoDelete: LinearLayout = _view.findViewById(R.id.downloaded_video_delete);
    private val _videoExport: LinearLayout = _view.findViewById(R.id.button_export);
    private val _videoSize: TextView = _view.findViewById(R.id.downloaded_video_size);

    val onClick = Event1<VideoLocal>();

    init {
        _view.setOnClickListener {
            _video?.let { onClick.emit(it) }
        };
        _videoDelete.setOnClickListener {
            val id = _video?.id ?: return@setOnClickListener;
            UIDialogs.showConfirmationDialog(_view.context, _view.context.getString(R.string.are_you_sure_you_want_to_delete_this_video), {
                StateDownloads.instance.deleteCachedVideo(id);
            });
        }
        _videoAddToQueue.setOnClickListener {
            val v = _video ?: return@setOnClickListener;
            StatePlayer.instance.addToQueue(v);
        }
        _videoExport.setOnClickListener {
            val v = _video ?: return@setOnClickListener;
            if (StateApp.instance.getExternalDownloadDirectory(_view.context) == null) {
                StateApp.instance.changeExternalDownloadDirectory(_view.context as MainActivity) {
                    if (it == null) {
                        UIDialogs.toast(_view.context, "Download directory must be set to export.");
                        return@changeExternalDownloadDirectory;
                    }

                    StateDownloads.instance.export(v, v.videoSource.firstOrNull(), v.audioSource.firstOrNull(), v.subtitlesSources.firstOrNull());
                };
            } else {
                StateDownloads.instance.export(v, v.videoSource.firstOrNull(), v.audioSource.firstOrNull(), v.subtitlesSources.firstOrNull());
            }
        }
    }

    override fun bind(video: VideoLocal) {
        _video = video;
        _videoName.text = video.name;
        _videoDuration.text = video.duration.toHumanTime(false);
        _videoAuthor.text = video.author.name;
        _videoSize.text = (video.videoSource.sumOf { it.fileSize } + video.audioSource.sumOf { it.fileSize }).toHumanBytesSize(false);

        val tokens = arrayListOf<String>();

        if(video.videoSource.isNotEmpty()) {
            tokens.add(video.videoSource.maxBy { it.width * it.height }.let { "${it.width}x${it.height} (${it.container})" });
        }

        if (video.audioSource.isNotEmpty()) {
            tokens.add(video.audioSource.maxBy { it.bitrate }.let { it.bitrate.toHumanBitrate() });
        }

        _videoInfo.text =tokens.joinToString(" • ");

        _videoImage.loadThumbnails(video.thumbnails, true) {
            it.placeholder(R.drawable.placeholder_video_thumbnail)
                .into(_videoImage);
        };
    }
}