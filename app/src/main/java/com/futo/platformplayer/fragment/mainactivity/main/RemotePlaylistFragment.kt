package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ShareCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.models.JSPager
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiPager
import com.futo.platformplayer.api.media.structures.ReusablePager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.VideoListEditorViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemotePlaylistFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: RemotePlaylistView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter);
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = RemotePlaylistView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class RemotePlaylistView : LinearLayout {
        private val _fragment: RemotePlaylistFragment;

        private var _remotePlaylist: IPlatformPlaylistDetails? = null;
        private var _remotePlaylistPagerWindow: IPager<IPlatformVideo>? = null;
        private var _url: String? = null;
        private val _videos: ArrayList<IPlatformVideo> = arrayListOf();

        private val _taskLoadPlaylist: TaskHandler<String, IPlatformPlaylistDetails>;
        private var _nextPageHandler: TaskHandler<IPager<IPlatformVideo>, List<IPlatformVideo>>;

        private var _imagePlaylistThumbnail: ImageView;
        private var _textName: TextView;
        private var _textMetadata: TextView;
        private var _loaderOverlay: FrameLayout;
        private var _imageLoader: ImageView;
        private var _overlayContainer: FrameLayout;
        private var _buttonShare: ImageButton;
        private var _recyclerPlaylist: RecyclerView;
        private var _llmPlaylist: LinearLayoutManager;
        private val _adapterVideos: InsertedViewAdapterWithLoader<VideoListEditorViewHolder>;
        private val _scrollListener: RecyclerView.OnScrollListener

        constructor(fragment: RemotePlaylistFragment, inflater: LayoutInflater) : super(inflater.context) {
            inflater.inflate(R.layout.fragment_remote_playlist, this);

            _fragment = fragment;

            _textName = findViewById(R.id.text_name);
            _textMetadata = findViewById(R.id.text_metadata);
            _imagePlaylistThumbnail = findViewById(R.id.image_playlist_thumbnail);
            _loaderOverlay = findViewById(R.id.layout_loading_overlay);
            _imageLoader = findViewById(R.id.image_loader);
            _recyclerPlaylist = findViewById(R.id.recycler_playlist);
            _llmPlaylist = LinearLayoutManager(context);
            _adapterVideos = InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                childCountGetter = { _videos.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(_videos[position], false); },
                childViewHolderFactory = { viewGroup, _ ->
                    val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_playlist, viewGroup, false);
                    val holder = VideoListEditorViewHolder(view, null);
                    holder.onClick.subscribe {
                        showConvertConfirmationModal(false);
                    };
                    return@InsertedViewAdapterWithLoader holder;
                }
            );

            _recyclerPlaylist.adapter = _adapterVideos;
            _recyclerPlaylist.layoutManager = _llmPlaylist;

            _overlayContainer = findViewById(R.id.overlay_container);
            val buttonPlayAll = findViewById<LinearLayout>(R.id.button_play_all);
            val buttonShuffle = findViewById<LinearLayout>(R.id.button_shuffle);

            _buttonShare = findViewById(R.id.button_share);
            _buttonShare.setOnClickListener {
                val remotePlaylist = _remotePlaylist ?: return@setOnClickListener;

                _fragment.startActivity(ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setText(remotePlaylist.shareUrl)
                    .intent);
            };

            buttonPlayAll.setOnClickListener {
                showConvertConfirmationModal(false);
            };
            buttonShuffle.setOnClickListener {
                showConvertConfirmationModal(false);
            };

            _taskLoadPlaylist = TaskHandler<String, IPlatformPlaylistDetails>(
                StateApp.instance.scopeGetter,
                {
                    return@TaskHandler StatePlatform.instance.getPlaylist(it);
                })
                .success {
                    _remotePlaylist = it;
                    val c = it.contents;
                    _remotePlaylistPagerWindow = if (c is ReusablePager) c.getWindow() else c;
                    setName(it.name);
                    setVideos(_remotePlaylistPagerWindow!!.getResults());
                    setVideoCount(it.videoCount);
                    setLoading(false);
                }
                .exception<Throwable> {
                    Logger.w(TAG, "Failed to load playlist.", it);
                    val c = context ?: return@exception;
                    UIDialogs.showGeneralRetryErrorDialog(c, context.getString(R.string.failed_to_load_playlist), it, ::fetchPlaylist, null, fragment);
                };

            _nextPageHandler = TaskHandler<IPager<IPlatformVideo>, List<IPlatformVideo>>({fragment.lifecycleScope}, {
                if (it is IAsyncPager<*>)
                    it.nextPageAsync();
                else
                    it.nextPage();

                processPagerExceptions(it);
                return@TaskHandler it.getResults();
            }).success {
                _adapterVideos.setLoading(false);
                addVideos(it);
                //TODO: ensureEnoughContentVisible()
            }.exception<Throwable> {
                Logger.w(TAG, "Failed to load next page.", it);
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_next_page), it, {
                    loadNextPage();
                }, null, fragment);
            };

            _scrollListener = object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val visibleItemCount = _recyclerPlaylist.childCount
                    val firstVisibleItem = _llmPlaylist.findFirstVisibleItemPosition()
                    val visibleThreshold = 15
                    if (!_adapterVideos.isLoading && firstVisibleItem + visibleItemCount + visibleThreshold >= _videos.size) {
                        loadNextPage()
                    }
                }
            }

            _recyclerPlaylist.addOnScrollListener(_scrollListener)
        }

        private fun loadNextPage() {
            val pager: IPager<IPlatformVideo> = _remotePlaylistPagerWindow ?: return;
            val hasMorePages = pager.hasMorePages();
            Logger.i(TAG, "loadNextPage() hasMorePages=$hasMorePages, page size=${pager.getResults().size}");

            if (pager.hasMorePages()) {
                _adapterVideos.setLoading(true);
                _nextPageHandler.run(pager);
            }
        }

        private fun processPagerExceptions(pager: IPager<*>) {
            if(pager is MultiPager<*> && pager.allowFailure) {
                val ex = pager.getResultExceptions();
                for(kv in ex) {
                    val jsVideoPager: JSPager<*>? = if(kv.key is MultiPager<*>)
                        (kv.key as MultiPager<*>).findPager { it is JSPager<*> } as JSPager<*>?;
                    else if(kv.key is JSPager<*>)
                        kv.key as JSPager<*>;
                    else null;

                    context?.let {
                        _fragment.lifecycleScope.launch(Dispatchers.Main) {
                            try {
                                if(jsVideoPager != null)
                                    UIDialogs.toast(it, context.getString(R.string.plugin_pluginname_failed_message).replace("{pluginName}", jsVideoPager.getPluginConfig().name).replace("{message}", kv.value.message ?: ""), false);
                                else
                                    UIDialogs.toast(it, kv.value.message ?: "", false);
                            } catch (e: Throwable) {
                                Logger.e(TAG, "Failed to show toast.", e)
                            }
                        }
                    }
                }
            }
        }

        fun onShown(parameter: Any?) {
            _taskLoadPlaylist.cancel();
            _nextPageHandler.cancel();

            if (parameter is IPlatformPlaylist) {
                _remotePlaylist = null;
                _url = parameter.url;

                setVideoCount(parameter.videoCount);
                setName(parameter.name);
                setVideos(null);

                fetchPlaylist();
                showConvertPlaylistButton();
            } else if (parameter is String) {
                _remotePlaylist = null;
                _url = parameter;

                setName(null);
                setVideos(null);
                setVideoCount(-1);

                fetchPlaylist();
                showConvertPlaylistButton();
            }
        }

        private fun showConvertConfirmationModal(savePlaylist: Boolean) {
            val remotePlaylist = _remotePlaylist
            if (remotePlaylist == null) {
                UIDialogs.toast(context.getString(R.string.please_wait_for_playlist_to_finish_loading))
                return
            }

            val c = context ?: return
            UIDialogs.showConfirmationDialog(
                c,
                "Conversion to local playlist is required for this action",
                {
                    setLoading(true)

                    UIDialogs.showDialogProgress(context) {
                        it.setText("Converting playlist..")
                        it.setProgress(0f)

                        _fragment.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val playlist = remotePlaylist.toPlaylist { progress ->
                                    _fragment.lifecycleScope.launch(Dispatchers.Main) {
                                        it.setProgress(progress.toDouble() / remotePlaylist.videoCount)
                                    }
                                }

                                if (savePlaylist) {
                                    StatePlaylists.instance.playlistStore.save(playlist)
                                }

                                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                                    UIDialogs.toast("Playlist converted")
                                    it.dismiss()
                                    _fragment.navigate<PlaylistFragment>(playlist)
                                }
                            } catch (ex: Throwable) {
                                UIDialogs.appToast("Failed to convert playlist.\n" + ex.message)
                            }
                        }
                    }
                })
        }

        private fun showConvertPlaylistButton() {
            _fragment.topBar?.assume<NavigationTopBarFragment>()?.setMenuItems(arrayListOf(Pair(R.drawable.ic_copy) {
                showConvertConfirmationModal(true);
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

        private fun setName(name: String?) {
            _textName.text = name ?: "";
        }

        private fun setVideoCount(videoCount: Int = -1) {
            _textMetadata.text = if (videoCount == -1) "" else "${videoCount} " + context.getString(R.string.videos);
        }

        private fun setVideos(videos: List<IPlatformVideo>?) {
            if (!videos.isNullOrEmpty()) {
                val video = videos.first();
                _imagePlaylistThumbnail.let {
                    Glide.with(it)
                        .load(video.thumbnails.getHQThumbnail())
                        .placeholder(R.drawable.placeholder_video_thumbnail)
                        .crossfade()
                        .into(it);
                };
            } else {
                _textMetadata.text = "0 " + context.getString(R.string.videos);
                Glide.with(_imagePlaylistThumbnail)
                    .load(R.drawable.placeholder_video_thumbnail)
                    .into(_imagePlaylistThumbnail)
            }

            synchronized(_videos) {
                _videos.clear();
                _videos.addAll(videos ?: listOf());
                _adapterVideos.notifyDataSetChanged();
            }
        }

        private fun addVideos(videos: List<IPlatformVideo>) {
            synchronized(_videos) {
                val index = _videos.size;
                _videos.addAll(videos);
                _adapterVideos.notifyItemRangeInserted(_adapterVideos.childToParentPosition(index), videos.size);
            }
        }

        private fun setLoading(isLoading: Boolean) {
            if (isLoading){
                (_imageLoader.drawable as Animatable?)?.start()
                _loaderOverlay.visibility = View.VISIBLE;
            }
            else {
                _loaderOverlay.visibility = View.GONE;
                (_imageLoader.drawable as Animatable?)?.stop()
            }
        }
    }

    companion object {
        private const val TAG = "RemotePlaylistFragment";
        fun newInstance() = RemotePlaylistFragment().apply {}
    }
}