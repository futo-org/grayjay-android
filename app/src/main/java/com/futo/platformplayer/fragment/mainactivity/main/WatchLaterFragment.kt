package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchLaterFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: WatchLaterView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = WatchLaterView(this, inflater);
        _view = view;
        return view;
    }

    override fun onResume() {
        super.onResume()
        _view?.onResume();
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class WatchLaterView : VideoListEditorView {
        private val _fragment: WatchLaterFragment;

        constructor(fragment: WatchLaterFragment, inflater: LayoutInflater) : super(inflater) {
            _fragment = fragment;

        }

        fun onShown() {
            setName("Watch Later");
            setVideos(StatePlaylists.instance.getWatchLater(), true);

            setButtonDownloadVisible(true);
            updateDownloadState(VideoDownload.GROUP_WATCHLATER, VideoDownload.GROUP_WATCHLATER, this@WatchLaterView::download);
        }

        fun onResume(){
            StateDownloads.instance.onDownloadsChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        updateDownloadState(VideoDownload.GROUP_WATCHLATER, VideoDownload.GROUP_WATCHLATER, this@WatchLaterView::download);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update download state onDownloadedChanged.")
                    }
                }
            };
            StateDownloads.instance.onDownloadedChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        updateDownloadState(VideoDownload.GROUP_WATCHLATER, VideoDownload.GROUP_WATCHLATER, this@WatchLaterView::download);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update download state onDownloadedChanged.")
                    }
                }
            };
        }

        fun download(){
            UISlideOverlays.showDownloadWatchlaterOverlay(overlayContainer);
        }

        override fun onPlayAllClick() {
            StatePlayer.instance.setQueue(StatePlaylists.instance.getWatchLater(), StatePlayer.TYPE_WATCHLATER, focus = true);
        }

        override fun onShuffleClick() {
            StatePlayer.instance.setQueue(StatePlaylists.instance.getWatchLater(), StatePlayer.TYPE_WATCHLATER, focus = true, shuffle = true);
        }

        override fun onVideoOrderChanged(videos: List<IPlatformVideo>) {
            StatePlaylists.instance.updateWatchLater(ArrayList(videos.map { it as SerializedPlatformVideo }), true);
        }
        override fun onVideoRemoved(video: IPlatformVideo) {
            if (video is SerializedPlatformVideo) {
                StatePlaylists.instance.removeFromWatchLater(video, true);
            }
        }

        override fun onVideoClicked(video: IPlatformVideo) {
            val watchLater = StatePlaylists.instance.getWatchLater();
            val index = watchLater.indexOf(video);
            if (index == -1) {
                return;
            }

            StatePlayer.instance.setQueueWithPosition(watchLater, StatePlayer.TYPE_WATCHLATER, index, focus = true);
        }
    }

    companion object {
        val TAG = "WatchLaterFragment";
        fun newInstance() = WatchLaterFragment().apply {}
    }
}