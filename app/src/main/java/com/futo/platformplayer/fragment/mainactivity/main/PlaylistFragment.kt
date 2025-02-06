package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ShareCompat
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

class PlaylistFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: PlaylistView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter);
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
        private var _editPlaylistNameInput: SlideUpMenuTextInput? = null;
        private var _editPlaylistOverlay: SlideUpMenuOverlay? = null;
        private var _url: String? = null;

        private val _taskLoadPlaylist: TaskHandler<String, Playlist>;

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
                    SlideUpMenuItem(
                        context,
                        R.drawable.ic_list,
                        context.getString(R.string.share_as_text),
                        context.getString(R.string.share_as_a_list_of_video_urls),
                        tag = 1,
                        call = {
                            _fragment.startActivity(ShareCompat.IntentBuilder(context)
                                .setType("text/plain")
                                .setText(reconstruction)
                                .intent);
                        }),
                    SlideUpMenuItem(
                        context,
                        R.drawable.ic_move_up,
                        context.getString(R.string.share_as_import),
                        context.getString(R.string.share_as_a_import_file_for_grayjay),
                        tag = 2,
                        call = {
                            val shareUri = StatePlaylists.instance.createPlaylistShareJsonUri(context, playlist);
                            _fragment.startActivity(ShareCompat.IntentBuilder(context)
                                .setType("application/json")
                                .setStream(shareUri)
                                .intent);
                        })
                );
            };

            _taskLoadPlaylist = TaskHandler<String, Playlist>(
                StateApp.instance.scopeGetter,
                {
                    return@TaskHandler StatePlatform.instance.getPlaylist(it).toPlaylist();
                })
                .success {
                    setName(it.name);
                    //TODO: Implement support for pagination
                    setVideos(it.videos, false);
                    setMetadata(it.videos.size, it.videos.sumOf { it.duration });
                    setLoading(false);
                }
                .exception<Throwable> {
                    Logger.w(TAG, "Failed to load playlist.", it);
                    val c = context ?: return@exception;
                    UIDialogs.showGeneralRetryErrorDialog(c, context.getString(R.string.failed_to_load_playlist), it, ::fetchPlaylist, null, fragment);
                };
        }

        private fun copyPlaylist(playlist: Playlist) {
            StatePlaylists.instance.playlistStore.save(playlist)
            _fragment.topBar?.assume<NavigationTopBarFragment>()?.setMenuItems(
                arrayListOf()
            )
            UIDialogs.toast("Playlist saved")
        }

        fun onShown(parameter: Any?) {
            _taskLoadPlaylist.cancel()

            if (parameter is Playlist?) {
                _playlist = parameter
                _url = null

                if (parameter != null) {
                    setName(parameter.name)
                    setVideos(parameter.videos, true)
                    setMetadata(parameter.videos.size, parameter.videos.sumOf { it.duration })
                    setButtonDownloadVisible(true)
                    setButtonEditVisible(true)

                    if (!StatePlaylists.instance.playlistStore.hasItem { it.id == parameter.id }) {
                        _fragment.topBar?.assume<NavigationTopBarFragment>()
                            ?.setMenuItems(arrayListOf(Pair(R.drawable.ic_copy) {
                                copyPlaylist(parameter)
                            }))
                    }
                } else {
                    setName(null)
                    setVideos(null, false)
                    setMetadata(-1, -1);
                    setButtonDownloadVisible(false)
                    setButtonEditVisible(false)
                }
            } else if (parameter is IPlatformPlaylist) {
                _playlist = null
                _url = parameter.url

                setMetadata(parameter.videoCount, -1);
                setName(parameter.name)
                setVideos(null, false)
                setButtonDownloadVisible(false)
                setButtonEditVisible(false)

                fetchPlaylist()
            } else if (parameter is String) {
                _playlist = null
                _url = parameter

                setName(null)
                setVideos(null, false)
                setMetadata(-1, -1);
                setButtonDownloadVisible(false)
                setButtonEditVisible(false)

                fetchPlaylist()
            }

            _playlist?.let {
                updateDownloadState(VideoDownload.GROUP_PLAYLIST, it.id, this::download)
            }
        }

        fun onResume() {
            StateDownloads.instance.onDownloadsChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        _playlist?.let {
                            updateDownloadState(VideoDownload.GROUP_PLAYLIST, it.id, this@PlaylistView::download);
                        }
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update download state onDownloadedChanged.")
                    }
                }
            };
            StateDownloads.instance.onDownloadedChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        _playlist?.let {
                            updateDownloadState(VideoDownload.GROUP_PLAYLIST, it.id, this@PlaylistView::download);
                        }
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update download state onDownloadedChanged.")
                    }
                }
            };
        }

        private fun download() {
            val playlist = _playlist ?: return
            if (!StatePlaylists.instance.playlistStore.hasItem { it.id == playlist.id }) {
                UIDialogs.showConfirmationDialog(context, "Playlist must be saved to download", {
                    copyPlaylist(playlist)
                    download()
                })
                return
            }

            _playlist?.let {
                UISlideOverlays.showDownloadPlaylistOverlay(it, overlayContainer);
            }
        }

        fun onPause() {
            StateDownloads.instance.onDownloadsChanged.remove(this);
            StateDownloads.instance.onDownloadedChanged.remove(this);
        }

        private fun fetchPlaylist() {
            Logger.i(TAG, "fetchPlaylist")

            val url = _url;
            if (!url.isNullOrBlank()) {
                setLoading(true);
                _taskLoadPlaylist.run(url);
            }
        }


        override fun canEdit(): Boolean { return _playlist != null; }

        override fun onEditClick() {
            val playlist = _playlist ?: return
            if (!StatePlaylists.instance.playlistStore.hasItem { it.id == playlist.id }) {
                UIDialogs.showConfirmationDialog(context, "Playlist must be saved to edit the name", {
                    copyPlaylist(playlist)
                    onEditClick()
                })
                return
            }

            _editPlaylistNameInput?.activate();
            _editPlaylistOverlay?.show();
        }

        override fun onPlayAllClick() {
            val playlist = _playlist;
            if (playlist != null) {
                StatePlayer.instance.setPlaylist(playlist, focus = true);
            }
        }

        override fun onShuffleClick() {
            val playlist = _playlist;
            if (playlist != null) {
                StatePlayer.instance.setPlaylist(playlist, focus = true, shuffle = true);
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
            if (playlist != null) {
                val index = playlist.videos.indexOf(video);
                if (index == -1)
                    return;

                StatePlayer.instance.setPlaylist(playlist, index, true);
            }
        }
    }

    companion object {
        private const val TAG = "PlaylistFragment";
        fun newInstance() = PlaylistFragment().apply {}
    }
}