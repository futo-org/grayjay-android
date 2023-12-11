package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fragment.mainactivity.topbar.ImportTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.viewholders.ImportPlaylistsViewHolder
import com.futo.platformplayer.views.adapters.viewholders.SelectablePlaylist

class ImportPlaylistsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: ImportPlaylistsView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter);
    }

    override fun onHide() {
        super.onHide();

        val tb = this.topBar as ImportTopBarFragment?;
        tb?.onImport?.remove(this);
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ImportPlaylistsView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view?.cleanup();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class ImportPlaylistsView : LinearLayout {
        private val _fragment: ImportPlaylistsFragment;

        private var _spinner: ImageView;
        private var _textSelectDeselectAll: TextView;
        private var _textNothingToImport: TextView;
        private var _textCounter: TextView;
        private var _adapterView: AnyAdapterView<SelectablePlaylist, ImportPlaylistsViewHolder>;
        private var _links: List<String> = listOf();
        private val _items: ArrayList<SelectablePlaylist> = arrayListOf();
        private var _currentLoadIndex = 0;

        private var _taskLoadPlaylist: TaskHandler<String, Playlist?>;

        constructor(fragment: ImportPlaylistsFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment;
            inflater.inflate(R.layout.fragment_import, this);

            _textNothingToImport = findViewById(R.id.nothing_to_import);
            _textSelectDeselectAll = findViewById(R.id.text_select_deselect_all);
            _textCounter = findViewById(R.id.text_select_counter);
            _spinner = findViewById(R.id.channel_loader);

            _adapterView = findViewById<RecyclerView>(R.id.recycler_import).asAny( _items) {
                it.onSelectedChange.subscribe {
                    updateSelected();
                };
            };

            _textSelectDeselectAll.setOnClickListener {
                val itemsSelected = _items.count { i -> i.selected };
                if (itemsSelected > 0) {
                    for (i in _items) {
                        i.selected = false;
                    }
                } else {
                    for (i in _items) {
                        i.selected = true;
                    }
                }

                _adapterView.adapter.notifyContentChanged();
                updateSelected();
            };

            setLoading(false);

            _taskLoadPlaylist = TaskHandler<String, Playlist?>({fragment.lifecycleScope}, { link -> StatePlatform.instance.getPlaylist(link).toPlaylist(); })
                .success {
                    if (it != null) {
                        _items.add(SelectablePlaylist(it));
                        _adapterView.adapter.notifyItemInserted(_items.size - 1);
                    }

                    loadNext();
                }.exceptionWithParameter<Throwable> { ex, para ->
                    //setLoading(false);
                    Logger.w(ChannelFragment.TAG, "Failed to load results.", ex);
                    UIDialogs.toast(context, context.getString(R.string.failed_to_fetch) + "\n${para}", false)
                    //UIDialogs.showDataRetryDialog(layoutInflater, { load(); });
                    loadNext();
                };
        }

        fun cleanup() {
            _taskLoadPlaylist.cancel();
        }

        fun onShown(parameter: Any?) {
            updateSelected();

            val itemsRemoved = _items.size;
            if (itemsRemoved > 0) {
                _items.clear();
                _adapterView.adapter.notifyItemRangeRemoved(0, itemsRemoved);
            }

            _links = (parameter as Array<String>).toList();
            _currentLoadIndex = 0;
            if (_links.isNotEmpty()) {
                load();
                _textNothingToImport.visibility = View.GONE;
            } else {
                setLoading(false);
                _textNothingToImport.visibility = View.VISIBLE;
            }

            val tb = _fragment.topBar as ImportTopBarFragment?;
            tb?.let {
                it.title = context.getString(R.string.import_playlists);
                it.onImport.subscribe(this) {
                    val playlistsToImport = _items.filter { i -> i.selected }.toList();
                    for (playlistToImport in playlistsToImport) {
                        StatePlaylists.instance.createOrUpdatePlaylist(playlistToImport.playlist);
                    }

                    UIDialogs.toast("${playlistsToImport.size} " + context.getString(R.string.playlists_imported));
                    _fragment.closeSegment();
                };
            }
        }

        private fun load() {
            setLoading(true);
            _taskLoadPlaylist.run(_links[_currentLoadIndex]);
        }

        private fun loadNext() {
            _currentLoadIndex++;
            if (_currentLoadIndex < _links.size) {
                load();
            } else {
                setLoading(false);
            }
        }

        private fun updateSelected() {
            val itemsSelected = _items.count { i -> i.selected };
            if (itemsSelected > 0) {
                _textSelectDeselectAll.text = context.getString(R.string.deselect_all);
                _textCounter.text = context.getString(R.string.index_out_of_size_selected).replace("{index}", itemsSelected.toString()).replace("{size}", _items.size.toString());
                (_fragment.topBar as ImportTopBarFragment?)?.setImportEnabled(true);
            } else {
                _textSelectDeselectAll.text = context.getString(R.string.select_all);
                _textCounter.text = "";
                (_fragment.topBar as ImportTopBarFragment?)?.setImportEnabled(false);
            }
        }

        private fun setLoading(isLoading: Boolean) {
            if(isLoading){
                (_spinner.drawable as Animatable?)?.start();
                _spinner.visibility = View.VISIBLE;
            }
            else {
                _spinner.visibility = View.GONE;
                (_spinner.drawable as Animatable?)?.stop();
            }
        }
    }

    companion object {
        val TAG = "ImportSubscriptionsFragment";
        fun newInstance() = ImportPlaylistsFragment().apply {}
    }
}