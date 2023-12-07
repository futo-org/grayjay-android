package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.PlatformContentPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.views.others.TagsView
import com.futo.platformplayer.views.adapters.HistoryListViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HistoryFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: HistoryView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = HistoryView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view = null;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack)
        _view?.setPager(StateHistory.instance.getHistoryPager());
    }

    @SuppressLint("ViewConstructor")
    class HistoryView : LinearLayout {
        private val _fragment: HistoryFragment;
        private val _adapter: InsertedViewAdapterWithLoader<HistoryListViewHolder>;
        private val _recyclerHistory: RecyclerView;
        private val _clearSearch: ImageButton;
        private val _editSearch: EditText;
        private val _tagsView: TagsView;
        private val _llmHistory: LinearLayoutManager;
        private val _pagerLock = Object();
        private var _nextPageHandler: TaskHandler<IPager<HistoryVideo>, List<HistoryVideo>>;
        private var _pager: IPager<HistoryVideo>? = null;
        private val _results = arrayListOf<HistoryVideo>();
        private var _loading = false;

        private var _automaticNextPageCounter = 0;

        constructor(fragment: HistoryFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment;
            inflater.inflate(R.layout.fragment_history, this);

            _recyclerHistory = findViewById(R.id.recycler_history);
            _clearSearch = findViewById(R.id.button_clear_search);
            _editSearch = findViewById(R.id.edit_search);
            _tagsView = findViewById(R.id.tags_text);
            _tagsView.setPairs(listOf(
                Pair(context.getString(R.string.last_hour), 60L),
                Pair(context.getString(R.string.last_24_hours), 24L * 60L),
                Pair(context.getString(R.string.last_week), 7L * 24L * 60L),
                Pair(context.getString(R.string.last_30_days), 30L * 24L * 60L),
                Pair(context.getString(R.string.last_year), 365L * 30L * 24L * 60L),
                Pair(context.getString(R.string.all_time), -1L)
            ));

            _adapter = InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                { _results.size },
                { view, _ ->
                    val holder = HistoryListViewHolder(view);
                    holder.onRemove.subscribe(::onHistoryVideoRemove);
                    holder.onClick.subscribe(::onHistoryVideoClick);
                    return@InsertedViewAdapterWithLoader holder;
                },
                { viewHolder, position ->
                    var watchTime: String? = null;
                    if (position == 0) {
                        watchTime = _results[position].date.toHumanNowDiffStringMinDay();
                    } else {
                        val previousWatchTime = _results[position - 1].date.toHumanNowDiffStringMinDay();
                        val currentWatchTime = _results[position].date.toHumanNowDiffStringMinDay();
                        if (previousWatchTime != currentWatchTime) {
                            watchTime = currentWatchTime;
                        }
                    }

                    viewHolder.bind(_results[position], watchTime);
                }
            );

            _recyclerHistory.adapter = _adapter;
            _recyclerHistory.isSaveEnabled = false;
            _llmHistory = LinearLayoutManager(context);
            _recyclerHistory.layoutManager = _llmHistory;

            _tagsView.onClick.subscribe { timeMinutesToErase ->
                UIDialogs.showConfirmationDialog(context, context.getString(R.string.are_you_sure_delete_historical), {
                    StateHistory.instance.removeHistoryRange(timeMinutesToErase.second as Long);
                    UIDialogs.toast(context, timeMinutesToErase.first + " " + context.getString(R.string.removed));
                    updatePager();
                });
            };

            _clearSearch.setOnClickListener {
                val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;
                _editSearch.text.clear();
                _clearSearch.visibility = View.GONE;
                setPager(StateHistory.instance.getHistoryPager());
                _editSearch.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(_editSearch.windowToken, 0);
            };

            _editSearch.addTextChangedListener { _ ->
                val text = _editSearch.text;
                _clearSearch.visibility = if (text.isEmpty()) { View.GONE } else { View.VISIBLE };
                updatePager();
            };

            _recyclerHistory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy);

                    val visibleItemCount = _recyclerHistory.childCount;
                    val firstVisibleItem = _llmHistory.findFirstVisibleItemPosition();

                    Logger.i(TAG, "onScrolled _loading = $_loading, firstVisibleItem = $firstVisibleItem, visibleItemCount = $visibleItemCount, _results.size = ${_results.size}")

                    val visibleThreshold = 15;
                    if (!_loading && firstVisibleItem + visibleItemCount + visibleThreshold >= _results.size && firstVisibleItem > 0) {
                        loadNextPage();
                    }
                }
            });

            _nextPageHandler = TaskHandler<IPager<HistoryVideo>, List<HistoryVideo>>({fragment.lifecycleScope}, {
                if (it is IAsyncPager<*>)
                    it.nextPageAsync();
                else
                    it.nextPage();

                return@TaskHandler it.getResults();
            }).success {
                setLoading(false);

                val posBefore = _results.size;
                _results.addAll(it);
                _adapter.notifyItemRangeInserted(_adapter.childToParentPosition(posBefore), it.size);
                ensureEnoughContentVisible(it)
            }.exception<Throwable> {
                Logger.w(TAG, "Failed to load next page.", it);
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_next_page), it, {
                    loadNextPage();
                });
            };
        }

        private fun updatePager() {
            val query = _editSearch.text.toString();
            if (_editSearch.text.isNotEmpty()) {
                setPager(StateHistory.instance.getHistorySearchPager(query));
                //setPager(StateHistory.instance.getHistorySearchPager(query));
            } else {
                setPager(StateHistory.instance.getHistoryPager());
            }
        }

        fun setPager(pager: IPager<HistoryVideo>) {
            Logger.i(TAG, "setPager()");

            synchronized(_pagerLock) {
                loadPagerInternal(pager);
            }
        }

        private fun onHistoryVideoRemove(v: HistoryVideo) {
            val index = _results.indexOf(v);
            if (index == -1) {
                return;
            }

            StateHistory.instance.removeHistory(v.video.url);
            _results.removeAt(index);
            _adapter.notifyItemRemoved(index);
        }

        private fun onHistoryVideoClick(v: HistoryVideo) {
            val index = _results.indexOf(v);
            if (index == -1) {
                return;
            }

            val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;
            val diff = v.video.duration - v.position;
            val vid: Any = if (diff > 5) { v.video.withTimestamp(v.position) } else { v.video };
            StatePlayer.instance.clearQueue();
            _fragment.navigate<VideoDetailFragment>(vid).maximizeVideoDetail();
            _editSearch.clearFocus();
            inputMethodManager.hideSoftInputFromWindow(_editSearch.windowToken, 0);

            _fragment.lifecycleScope.launch(Dispatchers.Main) {
                delay(2000)
                updatePager()
            }
        }

        private fun loadNextPage() {
            synchronized(_pagerLock) {
                val pager: IPager<HistoryVideo> = _pager ?: return;
                val hasMorePages = pager.hasMorePages();
                Logger.i(TAG, "loadNextPage() hasMorePages=$hasMorePages");

                if (pager.hasMorePages()) {
                    setLoading(true);
                    _nextPageHandler.run(pager);
                }
            }
        }

        private fun setLoading(loading: Boolean) {
            Logger.v(TAG, "setLoading loading=${loading}");
            _loading = loading;
            _adapter.setLoading(loading);
        }

        private fun loadPagerInternal(pager: IPager<HistoryVideo>) {
            Logger.i(TAG, "Setting new internal pager on feed");

            _results.clear();
            val toAdd = pager.getResults();
            _results.addAll(toAdd);
            _adapter.notifyDataSetChanged();
            ensureEnoughContentVisible(toAdd)
            _pager = pager;
        }

        private fun ensureEnoughContentVisible(results: List<HistoryVideo>) {
            val canScroll = if (_results.isEmpty()) false else {
                val layoutManager = _llmHistory
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (firstVisibleItemPosition != RecyclerView.NO_POSITION) {
                    val firstVisibleView = layoutManager.findViewByPosition(firstVisibleItemPosition)
                    val itemHeight = firstVisibleView?.height ?: 0
                    val occupiedSpace = _results.size * itemHeight
                    val recyclerViewHeight = _recyclerHistory.height
                    Logger.i(TAG, "ensureEnoughContentVisible loadNextPage occupiedSpace=$occupiedSpace recyclerViewHeight=$recyclerViewHeight")
                    occupiedSpace >= recyclerViewHeight
                } else {
                    false
                }

            }

            Logger.i(TAG, "ensureEnoughContentVisible loadNextPage canScroll=$canScroll _automaticNextPageCounter=$_automaticNextPageCounter")
            if (!canScroll || results.isEmpty()) {
                _automaticNextPageCounter++
                if(_automaticNextPageCounter <= 4)
                    loadNextPage()
            } else {
                _automaticNextPageCounter = 0;
            }
        }
    }

    companion object {
        fun newInstance() = HistoryFragment().apply {}
        private const val TAG = "HistoryFragment"
    }
}