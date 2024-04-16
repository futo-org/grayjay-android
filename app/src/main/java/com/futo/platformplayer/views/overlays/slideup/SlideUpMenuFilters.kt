package com.futo.platformplayer.views.overlays.slideup

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.FilterGroup
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SlideUpMenuFilters {
    val onOK = Event2<List<String>?, Boolean>();

    private val _container: ViewGroup;
    private var _enabledClientsIds: List<String>;
    private val _filterValues: HashMap<String, List<String>>;
    private val _slideUpMenuOverlay: SlideUpMenuOverlay;
    private var _changed: Boolean = false;
    private val _lifecycleScope: CoroutineScope;

    private var _isChannelSearch = false;

    var commonCapabilities: ResultCapabilities? = null;


    constructor(lifecycleScope: CoroutineScope, container: ViewGroup, enabledClientsIds: List<String>, filterValues: HashMap<String, List<String>>, isChannelSearch: Boolean = false) {
        _lifecycleScope = lifecycleScope;
        _container = container;
        _enabledClientsIds = enabledClientsIds;
        _filterValues = filterValues;
        _isChannelSearch = isChannelSearch;
        _slideUpMenuOverlay = SlideUpMenuOverlay(_container.context, _container, container.context.getString(R.string.filters), container.context.getString(R.string.done), true, listOf());
        _slideUpMenuOverlay.onOK.subscribe {
            onOK.emit(_enabledClientsIds, _changed);
            _slideUpMenuOverlay.hide();
        }

        updateCommonCapabilities();
    }

    private fun updateCommonCapabilities() {
        _lifecycleScope.launch(Dispatchers.IO) {
            try {
                val caps = if(!_isChannelSearch)
                    StatePlatform.instance.getCommonSearchCapabilities(_enabledClientsIds);
                else
                    StatePlatform.instance.getCommonSearchChannelContentsCapabilities(_enabledClientsIds);
                synchronized(_filterValues) {
                    if (caps != null) {
                        val keysToRemove = arrayListOf<String>();
                        for (pair in _filterValues) {
                            //Remove filter groups from selected filters that are not selectable anymore
                            val currentFilter =
                                caps.filters.firstOrNull { it.idOrName == pair.key };
                            if (currentFilter == null) {
                                keysToRemove.add(pair.key);
                            } else {
                                //Remove selected filter values that are not selectable anymore
                                _filterValues[pair.key] =
                                    pair.value.filter { currentValue -> currentFilter.filters.any { f -> f.idOrName == currentValue } };
                            }
                        }

                        keysToRemove.forEach { _filterValues.remove(it) };
                    } else {
                        _filterValues.clear();
                    }
                }

                commonCapabilities = caps;

                withContext(Dispatchers.Main) {
                    updateItems();
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to update common capabilities", e)
            }
        }
    }

    private fun updateItems() {
        val caps = commonCapabilities;
        val items = arrayListOf<View>();

        val group = SlideUpMenuRadioGroup(_container.context, _container.context.getString(R.string.sources), StatePlatform.instance.getSortedEnabledClient().map { Pair(it.name, it.id) },
            _enabledClientsIds, true, true);

        group.onSelectedChange.subscribe {
            _enabledClientsIds = it as List<String>;
            updateCommonCapabilities();
        };

        items.add(group);

        if (caps == null) {
            _slideUpMenuOverlay.setItems(items);
            return;
        }

        for (filterGroup in caps.filters) {
            val value: List<String>;
            synchronized(_filterValues) {
                value = _filterValues[filterGroup.idOrName] ?: listOf();
            }

            val g = SlideUpMenuRadioGroup(_container.context, filterGroup.name, filterGroup.filters.map { Pair(it.idOrName, it.idOrName) },
                value, filterGroup.isMultiSelect, false);

            g.onSelectedChange.subscribe {
                synchronized(_filterValues) {
                    _filterValues[filterGroup.idOrName] = it.map { v -> v as String };
                }
                _changed = true;
            };

            items.add(g);
        }

        _slideUpMenuOverlay.setItems(items);
    }

    fun show() {
        _slideUpMenuOverlay.show();
    }

    companion object {
        private const val TAG = "SlideUpMenuFilters";
    }
}