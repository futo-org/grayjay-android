package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.SearchView
import com.futo.platformplayer.views.adapters.viewholders.SelectableCreatorBarViewHolder

class CreatorSelectOverlay: ConstraintLayout {
    private val _buttonSelect: FrameLayout;
    private val _topbar: OverlayTopbar;

    private val _searchBar: SearchView;
    private val _recyclerCreators: AnyAdapterView<SelectableCreatorBarViewHolder.Selectable, SelectableCreatorBarViewHolder>;

    private val _creators: ArrayList<SelectableCreatorBarViewHolder.Selectable> = arrayListOf();
    private val _creatorsFiltered: ArrayList<SelectableCreatorBarViewHolder.Selectable> = arrayListOf();

    private var _selected: MutableList<String> = mutableListOf();

    val onSelected = Event1<List<String>>();
    val onClose = Event0();

    constructor(context: Context, hideSubscriptions: List<String>? = null): super(context) {
        val subs = StateSubscriptions.instance.getSubscriptions();
        if(hideSubscriptions != null) {
            _creators.addAll(subs
                .filter { !hideSubscriptions.contains(it.channel.url) }
                .map { SelectableCreatorBarViewHolder.Selectable(it.channel, false) });
        }
        else
            _creators.addAll(subs
                .map { SelectableCreatorBarViewHolder.Selectable(it.channel, false) });
        filterCreators();
    }
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) { }
    init {
        inflate(context, R.layout.overlay_creator_select, this);
        _topbar = findViewById(R.id.topbar);
        _buttonSelect = findViewById(R.id.button_select);
        val dp6 = 6.dp(resources);
        _searchBar = findViewById(R.id.search_bar);
        _recyclerCreators = findViewById<RecyclerView>(R.id.recycler_creators).asAny(_creatorsFiltered, RecyclerView.HORIZONTAL) { creatorView ->
            creatorView.itemView.setPadding(0, dp6, 0, dp6);
            creatorView.onClick.subscribe {
                if(it.channel.thumbnail == null) {
                    UIDialogs.toast(context, "No thumbnail found");
                    return@subscribe;
                }
                if(_selected.contains(it.channel.url))
                    _selected.remove(it.channel.url);
                else
                    _selected.add(it.channel.url);
                updateSelected();
            };
        };
        _recyclerCreators.view.layoutManager = GridLayoutManager(context, 5).apply {
            this.orientation = LinearLayoutManager.VERTICAL;
        };
        _buttonSelect.setOnClickListener {
            if (_selected.isNotEmpty()) {
                select();
            }
        };
        _topbar.onClose.subscribe {
            onClose.emit();
        }
        _searchBar.onSearchChanged.subscribe {
            filterCreators();
        };
        updateSelected();
        filterCreators();
    }

    fun updateSelected() {
        val changed = arrayListOf<SelectableCreatorBarViewHolder.Selectable>()
        for(creator in _creators) {
            val act = _selected.contains(creator.channel.url);
            if(creator.active != act) {
                creator.active = act;
                changed.add(creator);
            }
        }
        for(change in changed) {
            val index = _creatorsFiltered.indexOf(change);
            _recyclerCreators.notifyContentChanged(index);
        }

        if(_selected.isNotEmpty())
            _buttonSelect.alpha = 1f;
        else
            _buttonSelect.alpha = 0.5f;
    }


    private fun filterCreators(withUpdate: Boolean = true) {
        val query = _searchBar.textSearch.text.toString().lowercase();
        val filteredEnabled = _creators.filter { query.isEmpty() || it.channel.name.lowercase().contains(query) };

        //Optimize
        _creatorsFiltered.clear();
        _creatorsFiltered.addAll(filteredEnabled);
        if(withUpdate)
            _recyclerCreators.notifyContentChanged();
    }

    fun select() {
        if(_creators.isEmpty())
            return;
        onSelected.emit(_selected.toList());
        onClose.emit();
    }
}