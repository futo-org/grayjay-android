package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.AddSourceOptionsActivity
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.fragment.mainactivity.topbar.AddTopBarFragment
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.SubscriptionStorage
import com.futo.platformplayer.views.adapters.DisabledSourceView
import com.futo.platformplayer.views.adapters.EnabledSourceAdapter
import com.futo.platformplayer.views.adapters.EnabledSourceViewHolder
import com.futo.platformplayer.views.adapters.ItemMoveCallback
import com.futo.platformplayer.views.sources.SourceUnderConstructionView
import kotlinx.coroutines.runBlocking
import java.util.Collections

class SourcesFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: SourcesView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack)

        if(topBar is AddTopBarFragment)
            (topBar as AddTopBarFragment).onAdd.subscribe {
                startActivity(Intent(requireContext(), AddSourceOptionsActivity::class.java));
            };

        _view?.reloadSources();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = SourcesView(requireContext(), this);
        _view = view;
        return view;
    }

    companion object {
        private const val TAG = "SourcesFragment";
        fun newInstance() = SourcesFragment().apply {}
    }


    private class SourcesView: LinearLayout {
        private val _fragment: SourcesFragment;

        private val enabledSources: MutableList<IPlatformClient> = mutableListOf();
        private val disabledSources: MutableList<IPlatformClient> = mutableListOf();
        private val _recyclerSourcesEnabled: RecyclerView;
        private val _adapterSourcesEnabled: EnabledSourceAdapter;
        private var _didCreateView = false;

        private val _containerEnabled: LinearLayout;
        private val _containerDisabled: LinearLayout;
        private val _containerDisabledViews: LinearLayout;
        private val _containerConstruction: LinearLayout;

        constructor(context: Context, fragment: SourcesFragment): super(context) {
            inflate(context, R.layout.fragment_sources, this);

            _fragment = fragment;

            val recyclerSourcesEnabled = findViewById<RecyclerView>(R.id.recycler_sources_enabled);
            _containerEnabled = findViewById(R.id.container_enabled);
            _containerDisabled = findViewById(R.id.container_disabled);
            _containerDisabledViews = findViewById(R.id.container_disabled_views);
            _containerConstruction = findViewById(R.id.container_construction);

            for(inConstructSource in StatePlugins.instance.getSourcesUnderConstruction(context))
                _containerConstruction.addView(SourceUnderConstructionView(context, inConstructSource.key, inConstructSource.value));

            val callback = ItemMoveCallback();
            val touchHelper = ItemTouchHelper(callback);
            val adapterSourcesEnabled = EnabledSourceAdapter(enabledSources, touchHelper);

            recyclerSourcesEnabled.adapter = adapterSourcesEnabled;
            recyclerSourcesEnabled.layoutManager = LinearLayoutManager(context);
            touchHelper.attachToRecyclerView(recyclerSourcesEnabled);

            //Enabled Sources control
            callback.onRowMoved.subscribe { fromPosition, toPosition ->
                if (fromPosition < toPosition) {
                    for (i in fromPosition until toPosition) {
                        Collections.swap(enabledSources, i, i + 1)
                    }
                } else {
                    for (i in fromPosition downTo toPosition + 1) {
                        Collections.swap(enabledSources, i, i - 1)
                    }
                }

                adapterSourcesEnabled.notifyItemMoved(fromPosition, toPosition);
                onEnabledChanged(enabledSources);
                if(toPosition == 0)
                    onPrimaryChanged(enabledSources.first());

                StatePlatform.instance.setPlatformOrder(enabledSources.map { it.name });
            };
            adapterSourcesEnabled.onRemove.subscribe { source ->
                val subscriptionStorage = FragmentedStorage.get<SubscriptionStorage>();
                val enabledSourcesWithSourceRemoved = enabledSources.filter({ s -> s.id != source.id }).toList();
                val unresolvableBefore = subscriptionStorage.subscriptions.count({ s -> !enabledSources.any({ c -> c.isChannelUrl(s.channel.url) }) });
                val unresolvableAfter = subscriptionStorage.subscriptions.count({ s -> !enabledSourcesWithSourceRemoved.any({ c -> c.isChannelUrl(s.channel.url) }) });

                val removeAction = {
                    val index = enabledSources.indexOf(source);
                    if (index >= 0) {
                        enabledSources.removeAt(index);
                        disabledSources.add(source);
                        adapterSourcesEnabled.notifyItemRemoved(index);
                        updateDisabledSources();
                    }

                    updateContainerVisibility();
                    onEnabledChanged(enabledSources);
                    if(index == 0)
                        onPrimaryChanged(enabledSources.first());

                    if(enabledSources.size <= 1)
                        setCanRemove(false);
                };

                if (unresolvableAfter > unresolvableBefore) {
                    UIDialogs.showConfirmationDialog(context, fragment.getString(R.string.confirm_remove_source), removeAction);
                } else {
                    removeAction();
                }
            };
            adapterSourcesEnabled.onClick.subscribe { source ->
                if (source is JSClient) {
                    fragment.navigate<SourceDetailFragment>(source.config);
                }
            };

            updateContainerVisibility();

            _recyclerSourcesEnabled = recyclerSourcesEnabled;
            _adapterSourcesEnabled = adapterSourcesEnabled;
            //_adapterSourcesDisabled = adapterSourcesDisabled;

            setCanRemove(enabledSources.size > 1);
            _didCreateView = true;
        }

        @SuppressLint("NotifyDataSetChanged")
        fun reloadSources() {
            enabledSources.clear();
            disabledSources.clear();

            enabledSources.addAll(StatePlatform.instance.getSortedEnabledClient());
            disabledSources.addAll(StatePlatform.instance.getAvailableClients().filter { !enabledSources.contains(it) });
            _adapterSourcesEnabled.notifyDataSetChanged();
            setCanRemove(enabledSources.size > 1);
            updateDisabledSources();

            if(_didCreateView) {
                _containerEnabled.visibility = if (enabledSources.isNotEmpty()) { View.VISIBLE } else { View.GONE };
                _containerDisabled.visibility = if (disabledSources.isNotEmpty()) { View.VISIBLE } else { View.GONE };
            }
        }
        private fun updateDisabledSources() {
            _containerDisabledViews.removeAllViews();
            disabledSources.toList().let {
                for(source in disabledSources) {
                    _containerDisabledViews.addView(DisabledSourceView(context, source).apply {
                        this.onAdd.subscribe {
                            enableSource(it)
                        };
                        this.onClick.subscribe {
                            if (source is JSClient)
                                _fragment.navigate<SourceDetailFragment>(source.config);
                        }
                    });
                }
            };
        }

        private fun enableSource(client: IPlatformClient) {
            if (disabledSources.remove(client)) {
                enabledSources.add(client);
                _adapterSourcesEnabled.notifyItemInserted(enabledSources.size - 1);
            }
            updateDisabledSources();

            updateContainerVisibility();
            onEnabledChanged(enabledSources);

            if(enabledSources.size > 1)
                setCanRemove(true);
        }

        private fun setCanRemove(canRemove: Boolean) {
            for (i in 0 until _recyclerSourcesEnabled.childCount) {
                val view: View = _recyclerSourcesEnabled.getChildAt(i)
                val viewHolder = _recyclerSourcesEnabled.getChildViewHolder(view)
                if (viewHolder is EnabledSourceViewHolder) {
                    viewHolder.setCanRemove(canRemove);
                }
            }

            _adapterSourcesEnabled.canRemove = canRemove;
        }

        private fun onPrimaryChanged(client: IPlatformClient) {
            StatePlatform.instance.selectPrimaryClient(client.id);
        }
        private fun onEnabledChanged(clients: List<IPlatformClient>) {
            runBlocking {
                StatePlatform.instance.selectClients(*clients.map { it.id }.toTypedArray());
            }
        }


        fun updateContainerVisibility() {
            _containerEnabled.visibility = if (enabledSources.isNotEmpty()) { View.VISIBLE } else { View.GONE };
            _containerDisabled.visibility = if (disabledSources.isNotEmpty()) { View.VISIBLE } else { View.GONE };
        };
    }
}