package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ShareCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: PlaylistView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter, isBack);
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = PlaylistView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view = null;
    }

    override fun onResume() {
        super.onResume()
        _view?.onResume();
    }

    override fun onPause() {
        super.onPause()
        _view?.onPause();
    }

    @SuppressLint("ViewConstructor")
    class PlaylistView : VideoListEditorView {
        private val _fragment: PlaylistFragment;

        private var _playlist: Playlist? = null;
        private var _remotePlaylist: IPlatformPlaylistDetails? = null;
        private var _editPlaylistNameInput: SlideUpMenuTextInput? = null;
        private var _editPlaylistOverlay: SlideUpMenuOverlay? = null;
        private var _url: String? = null;

        private val _taskLoadPlaylist: TaskHandler<String, IPlatformPlaylistDetails>;

        constructor(fragment: PlaylistFragment, inflater: LayoutInflater) : super(inflater) {
            _fragment = fragment;

            val nameInput = SlideUpMenuTextInput(context, context.getString(R.string.name));
            val editPlaylistOverlay = SlideUpMenuOverlay(context, overlayContainer, context.getString(R.string.edit_playlist), context.getString(R.string.ok), false, nameInput);

            _buttonDownload.visibility = View.VISIBLE;
            editPlaylistOverlay.onOK.subscribe {
                val text = nameInput.text;
                if (text.isBlank()) {
                    return@subscribe;
                }

                setName(text);
                _playlist?.let {
                    it.name = text;
                    StatePlaylists.instance.createOrUpdatePlaylist(it);
                }

                editPlaylistOverlay.hide();
                nameInput.deactivate();
                nameInput.clear();
            }

            editPlaylistOverlay.onCancel.subscribe {
                nameInput.deactivate();
                nameInput.clear();
            };

            _editPlaylistOverlay = editPlaylistOverlay;
            _editPlaylistNameInput = nameInput;

            setOnShare {
                val playlist = _playlist ?: return@setOnShare;
                val reconstruction = StatePlaylists.instance.playlistStore.getReconstructionString(playlist);

                UISlideOverlays.showOverlay(overlayContainer, context.getString(R.string.playlist) + " [${playlist.name}]", null, {},
                    SlideUpMenuItem(context, R.drawable.ic_list, context.getString(R.string.share_as_text), context.getString(R.string.share_as_a_list_of_video_urls), 1, {
                        _fragment.startActivity(ShareCompat.IntentBuilder(context)
                            .setType("text/plain")
                            .setText(reconstruction)
                            .intent);
                    }),
                    SlideUpMenuItem(context, R.drawable.ic_move_up, context.getString(R.string.share_as_import), context.getString(R.string.share_as_a_import_file_for_grayjay), 2, {
                        val shareUri = StatePlaylists.instance.createPlaylistShareJsonUri(context, playlist);
                        _fragment.startActivity(ShareCompat.IntentBuilder(context)
                            .setType("application/json")
                            .setStream(shareUri)
                            .intent);
                    })
                );
            };

            _taskLoadPlaylist = TaskHandler<String, IPlatformPlaylistDetails>(
                StateApp.instance.scopeGetter,
                {
                    return@TaskHandler StatePlatform.instance.getPlaylist(it);
                })
                .success {
                    setLoading(false);
                    _remotePlaylist = it;
                    setName(it.name);
                    setVideos(it.contents.getResults(), false);
                    setVideoCount(it.videoCount);
                    //TODO: Implement support for pagination
                }
                .exception<Throwable> {
                    Logger.w(TAG, "Failed to load playlist.", it);
                    val c = context ?: return@exception;
                    UIDialogs.showGeneralRetryErrorDialog(c, context.getString(R.string.failed_to_load_playlist), it, ::fetchPlaylist);
                };
        }

        fun onShown(parameter: Any ?, isBack: Boolean) {
            _taskLoadPlaylist.cancel();

            if (parameter is Playlist?) {
                _playlist = parameter;
                _remotePlaylist = null;
                _url = null;

                if(parameter != null) {
                    setName(parameter.name);
                    setVideos(parameter.videos, true);
                    setVideoCount(parameter.videos.size);
                    setButtonDownloadVisible(true);
                    setButtonEditVisible(true);
                } else {
                    setName(null);
                    setVideos(null, false);
                    setVideoCount(-1);
                    setButtonDownloadVisible(false);
                    setButtonEditVisible(false);
                }

                //TODO: Do I have to remove the showConvertPlaylistButton(); button here?
            } else if (parameter is IPlatformPlaylist) {
                _playlist = null;
                _remotePlaylist = null;
                _url = parameter.url;

                setVideoCount(parameter.videoCount);
                setName(parameter.name);
                setVideos(null, false);
                setButtonDownloadVisible(false);
                setButtonEditVisible(false);

                fetchPlaylist();
                showConvertPlaylistButton();
            } else if (parameter is String) {
                _playlist = null;
                _remotePlaylist = null;
                _url = parameter;

                setName(null);
                setVideos(null, false);
                setVideoCount(-1);
                setButtonDownloadVisible(false);
                setButtonEditVisible(false);

                fetchPlaylist();
                showConvertPlaylistButton();
            }

            updateDownloadState();
        }

        fun onResume() {
            StateDownloads.instance.onDownloadsChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        updateDownloadState();
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update download state onDownloadedChanged.")
                    }
                }
            };
            StateDownloads.instance.onDownloadedChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        updateDownloadState();
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update download state onDownloadedChanged.")
                    }
                }
            };
        }

        fun onPause() {
            StateDownloads.instance.onDownloadsChanged.remove(this);
            StateDownloads.instance.onDownloadedChanged.remove(this);
        }

        private fun showConvertPlaylistButton() {
            _fragment.topBar?.assume<NavigationTopBarFragment>()?.setMenuItems(arrayListOf(Pair(R.drawable.ic_copy) {
                val remotePlaylist = _remotePlaylist;
                if (remotePlaylist == null) {
                    UIDialogs.toast(context.getString(R.string.please_wait_for_playlist_to_finish_loading));
                    return@Pair;
                }

                setLoading(true);
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        StatePlaylists.instance.playlistStore.save(remotePlaylist.toPlaylist());

                        withContext(Dispatchers.Main) {
                            setLoading(false);
                            UIDialogs.toast(context.getString(R.string.playlist_copied_as_local_playlist));
                        }
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            setLoading(false);
                        }

                        throw e;
                    }
                }
            }));
        }

        private fun fetchPlaylist() {
            Logger.i(TAG, "fetchPlaylist")

            val url = _url;
            if (!url.isNullOrBlank()) {
                setLoading(true);
                _taskLoadPlaylist.run(url);
            }
        }

        private fun updateDownloadState() {
            val playlist = _playlist ?: return;
            val isDownloading = StateDownloads.instance.getDownloading().any { it.groupType == VideoDownload.GROUP_PLAYLIST && it.groupID == playlist.id };
            val isDownloaded = StateDownloads.instance.isPlaylistCached(playlist.id);

            val dp10 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics);

            if(isDownloaded && !isDownloading)
                _buttonDownload.setBackgroundResource(R.drawable.background_button_round_green);
            else
                _buttonDownload.setBackgroundResource(R.drawable.background_button_round);

            if(isDownloading) {
                _buttonDownload.setImageResource(R.drawable.ic_loader_animated);
                _buttonDownload.drawable.assume<Animatable, Unit> { it.start() };
                _buttonDownload.setOnClickListener {
                    UIDialogs.showConfirmationDialog(context, context.getString(R.string.are_you_sure_you_want_to_delete_the_downloaded_videos), {
                        StateDownloads.instance.deleteCachedPlaylist(playlist.id);
                    });
                }
            }
            else if(isDownloaded) {
                _buttonDownload.setImageResource(R.drawable.ic_download_off);
                _buttonDownload.setOnClickListener {
                    UIDialogs.showConfirmationDialog(context, context.getString(R.string.are_you_sure_you_want_to_delete_the_downloaded_videos), {
                        StateDownloads.instance.deleteCachedPlaylist(playlist.id);
                    });
                }
            }
            else {
                _buttonDownload.setImageResource(R.drawable.ic_download);
                _buttonDownload.setOnClickListener {
                    UISlideOverlays.showDownloadPlaylistOverlay(playlist, overlayContainer);
                }
            }
            _buttonDownload.setPadding(dp10.toInt());
        }

        override fun canEdit(): Boolean { return _playlist != null; }

        override fun onEditClick() {
            _editPlaylistNameInput?.activate();
            _editPlaylistOverlay?.show();
        }

        override fun onPlayAllClick() {
            val playlist = _playlist;
            val remotePlaylist = _remotePlaylist;
            if (playlist != null) {
                StatePlayer.instance.setPlaylist(playlist, focus = true);
            } else if (remotePlaylist != null) {
                StatePlayer.instance.setPlaylist(remotePlaylist, focus = true, shuffle = false);
            }
        }

        override fun onShuffleClick() {
            val playlist = _playlist;
            val remotePlaylist = _remotePlaylist;
            if (playlist != null) {
                StatePlayer.instance.setPlaylist(playlist, focus = true, shuffle = true);
            } else if (remotePlaylist != null) {
                StatePlayer.instance.setPlaylist(remotePlaylist, focus = true, shuffle = true);
            }
        }

        override fun onVideoOrderChanged(videos: List<IPlatformVideo>) {
            val playlist = _playlist ?: return;
            playlist.videos = ArrayList(videos.map { it as SerializedPlatformVideo });
            StatePlaylists.instance.createOrUpdatePlaylist(playlist);
        }
        override fun onVideoRemoved(video: IPlatformVideo) {
            val playlist = _playlist ?: return;
            playlist.videos = ArrayList(playlist.videos.filter { it != video });
            StatePlaylists.instance.createOrUpdatePlaylist(playlist);
        }
        override fun onVideoClicked(video: IPlatformVideo) {
            val playlist = _playlist;
            val remotePlaylist = _remotePlaylist;
            if (playlist != null) {
                val index = playlist.videos.indexOf(video);
                if (index == -1)
                    return;

                StatePlayer.instance.setPlaylist(playlist, index, true);
            } else if (remotePlaylist != null) {
                val index = remotePlaylist.contents.getResults().indexOf(video);
                if (index == -1)
                    return;

                StatePlayer.instance.setPlaylist(remotePlaylist, index, true);
            }
        }
    }

    companion object {
        private const val TAG = "PlaylistFragment";
        fun newInstance() = PlaylistFragment().apply {}
    }
}