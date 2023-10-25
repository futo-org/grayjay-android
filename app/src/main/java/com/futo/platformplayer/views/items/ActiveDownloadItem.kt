package com.futo.platformplayer.views.items

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.*
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.views.others.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActiveDownloadItem: LinearLayout {
    private var _finalized: Boolean = false;
    private val _download: VideoDownload;

    private val _videoName: TextView;
    private val _videoImage: ImageView;
    private val _videoSize: TextView;
    private val _videoDuration: TextView;
    private val _videoAuthor: TextView;
    private val _videoInfo: TextView;
    private val _videoBar: ProgressBar;
    private val _videoSpeed: TextView;
    private val _videoState: TextView;

    private val _videoCancel: TextView;

    private val _scope: CoroutineScope;

    constructor(context: Context, download: VideoDownload, lifetimeScope: CoroutineScope): super(context) {
        inflate(context, R.layout.list_download, this)
        _scope = lifetimeScope;
        _download = download;

        _videoName = findViewById(R.id.downloaded_video_name);
        _videoImage = findViewById(R.id.downloaded_video_image);
        _videoSize = findViewById(R.id.downloaded_video_size);
        _videoDuration = findViewById(R.id.downloaded_video_duration);
        _videoAuthor = findViewById(R.id.downloaded_author);
        _videoInfo = findViewById(R.id.downloaded_video_info);
        _videoBar = findViewById(R.id.download_video_progress);
        _videoState = findViewById(R.id.download_video_state);
        _videoSpeed = findViewById(R.id.download_video_speed);

        _videoCancel = findViewById(R.id.download_cancel);

        _videoName.text = download.name;
        _videoDuration.text = download.videoEither.duration.toHumanTime(false);
        _videoAuthor.text = download.videoEither.author.name;

        _videoState.setOnClickListener {
            UIDialogs.toast(context, _videoState.text.toString(), false);
        }

        Glide.with(_videoImage)
            .load(download.thumbnail)
            .crossfade()
            .into(_videoImage);

        updateDownloadUI();

        _videoCancel.setOnClickListener {
            StateDownloads.instance.removeDownload(_download);
            StateDownloads.instance.preventPlaylistDownload(_download);
        };

        _download.onProgressChanged.subscribe(this) {
            _scope.launch(Dispatchers.Main) {
                try {
                    updateDownloadUI()
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to update download UI.", e);
                }
            }
        };
        _download.onStateChanged.subscribe(this) {
            _scope.launch(Dispatchers.Main) {
                try {
                    updateDownloadUI()
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to update download UI.", e);
                }
            }
        }
    }

    fun finalize() {
        _finalized = true;
        _download.onProgressChanged.remove(this);
        _download.onStateChanged.remove(this);
    }

    fun updateDownloadUI() {
        _videoInfo.text = _download.getDownloadInfo();

        val size = (_download.videoFileSize ?: 0) + (_download.audioFileSize ?: 0);
        if(size > 0)
            _videoSize.text = size.toHumanBytesSize(false);
        else
            _videoSize.text = "?";

        _videoBar.progress = _download.progress.toFloat();
        _videoSpeed.text = "${_download.downloadSpeed.toHumanBytesSpeed()} ${(_download.progress * 100).toInt()}%";

        _videoState.text = if(!Settings.instance.downloads.shouldDownload())
            context.getString(R.string.waiting_for_unmetered) + (if(!_download.error.isNullOrEmpty()) "\n(" + context.getString(R.string.last_error) + ": " + _download.error + ")" else "");
        else if(_download.state == VideoDownload.State.QUEUED && !_download.error.isNullOrEmpty())
            _download.state.toString() + "\n(" + context.getString(R.string.last_error) + ": " + _download.error + ")";
        else
            _download.state.toString();
        _videoState.setTextColor(Color.GRAY);
        when(_download.state) {
            VideoDownload.State.DOWNLOADING -> {
                _videoBar.visibility = VISIBLE;
                _videoSpeed.visibility = VISIBLE;
            };
            VideoDownload.State.ERROR -> {
                _videoState.setTextColor(Color.RED);
                _videoState.text = _download.error ?: context.getString(R.string.error);
                _videoBar.visibility = GONE;
                _videoSpeed.visibility = GONE;
            }
            else -> {
                _videoBar.visibility = GONE;
                _videoSpeed.visibility = GONE;
            }
        }
    }

    companion object {
        private const val TAG = "ActiveDownloadItem"
    }
}