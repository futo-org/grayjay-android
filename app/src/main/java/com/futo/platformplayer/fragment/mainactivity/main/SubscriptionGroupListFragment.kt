package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.activities.AddSourceOptionsActivity
import com.futo.platformplayer.fragment.mainactivity.topbar.AddTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateSubscriptionGroups
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.ItemMoveCallback
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionGroupListViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections

class SubscriptionGroupListFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _touchHelper: ItemTouchHelper? = null;

    private var _subs: ArrayList<SubscriptionGroup> = arrayListOf();
    private var _list: AnyAdapterView<SubscriptionGroup, SubscriptionGroupListViewHolder>? = null;
    private var _overlay: FrameLayout? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_subscriptions_group_list, container, false);
        _overlay = view.findViewById(R.id.overlay);
        val recycler = view.findViewById<RecyclerView>(R.id.list);
        val callback = ItemMoveCallback();
        _touchHelper = ItemTouchHelper(callback);
        _touchHelper?.attachToRecyclerView(recycler);

        _subs.clear();
        _subs.addAll(StateSubscriptionGroups.instance.getSubscriptionGroups().sortedBy { it.priority });
        _list = recycler.asAny(_subs, RecyclerView.VERTICAL){
            it.onClick.subscribe {
                navigate<SubscriptionGroupFragment>(it);
            };
            it.onSettings.subscribe {

            };
            it.onDelete.subscribe { group ->
                context?.let { context ->
                    UIDialogs.showDialog(context, R.drawable.ic_trash, "Delete Group", "Are you sure you want to this group?\n[${group.name}]?", null, 0,
                        UIDialogs.Action("Cancel", {}),
                        UIDialogs.Action("Delete", {
                            StateSubscriptionGroups.instance.deleteSubscriptionGroup(group.id, true);

                            val loc = _subs.indexOf(group);
                            _subs.remove(group);
                            _list?.adapter?.notifyItemRangeRemoved(loc);
                            StateSubscriptionGroups.instance.deleteSubscriptionGroup(group.id, true);

                        }, UIDialogs.ActionStyle.DANGEROUS));
                }
            };
            it.onDragDrop.subscribe {
                _touchHelper?.startDrag(it);
            };
        };

        callback.onRowMoved.subscribe(::groupMoved);
        return view;
    }

    private fun groupMoved(fromPosition: Int, toPosition: Int) {
        Logger.i("SubscriptionGroupListFragment", "Moved ${fromPosition} to ${toPosition}");
        synchronized(_subs) {
            if (fromPosition < toPosition) {
                for (i in fromPosition until toPosition) {
                    Collections.swap(_subs, i, i + 1)
                }
            } else {
                for (i in fromPosition downTo toPosition + 1) {
                    Collections.swap(_subs, i, i - 1)
                }
            }
        }
        _list?.adapter?.notifyItemMoved(fromPosition, toPosition);

        synchronized(_subs) {
            for(i in 0 until _subs.size) {
                val sub = _subs[i];
                if(sub.priority != i) {
                    sub.priority = i;
                    StateSubscriptionGroups.instance.updateSubscriptionGroup(sub, true);
                }
            }
        }
    }


    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);

        updateGroups();

        StateSubscriptionGroups.instance.onGroupsChanged.subscribe(this) {
            updateGroups();
        }

        if(topBar is AddTopBarFragment) {
            (topBar as AddTopBarFragment).onAdd.clear();
            (topBar as AddTopBarFragment).onAdd.subscribe {
                _overlay?.let {
                    UISlideOverlays.showCreateSubscriptionGroup(it)
                }
            };
        }
    }

    private fun updateGroups() {
        lifecycleScope.launch(Dispatchers.Main) {
            _subs.clear();
            _subs.addAll(StateSubscriptionGroups.instance.getSubscriptionGroups().sortedBy { it.priority });
            _list?.adapter?.notifyContentChanged();
        }
    }

    override fun onHide() {
        super.onHide();
        StateSubscriptionGroups.instance.onGroupsChanged.remove(this);

        if(topBar is AddTopBarFragment)
            (topBar as AddTopBarFragment).onAdd.remove(this);
    }

    override fun onBackPressed(): Boolean {
        return false;
    }

    companion object {
        fun newInstance() = SubscriptionGroupListFragment().apply {}
    }
}