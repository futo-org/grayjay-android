package com.futo.platformplayer.views.adapters

import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionAdapter : RecyclerView.Adapter<SubscriptionViewHolder> {
    private lateinit var _sortedDataset: List<Subscription>;
    private val _inflater: LayoutInflater;
    private val _confirmationMessage: String;
    private val _onDatasetChanged: ((List<Subscription>)->Unit)?;

    var onClick = Event1<Subscription>();
    var onSettings = Event1<Subscription>();
    var sortBy: Int = 5
        set(value) {
            field = value
            updateDataset()
        }
    var query: String? = null
        set(value) {
            field = value;
            updateDataset();
        }

    constructor(inflater: LayoutInflater, confirmationMessage: String, sortByDefault: Int, onDatasetChanged: ((List<Subscription>)->Unit)? = null) : super() {
        _inflater = inflater;
        _confirmationMessage = confirmationMessage;
        _onDatasetChanged = onDatasetChanged;
        sortBy = sortByDefault

        StateSubscriptions.instance.onSubscriptionsChanged.subscribe { _, _ -> if(Looper.myLooper() != Looper.getMainLooper())
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) { updateDataset() }
            else
                updateDataset();
        }
        updateDataset();
    }

    override fun getItemCount() = _sortedDataset.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val holder = SubscriptionViewHolder(viewGroup);
        holder.onClick.subscribe(onClick::emit);
        holder.onSettings.subscribe(onSettings::emit);
        holder.onTrash.subscribe {
            val sub = holder.subscription ?: return@subscribe;
            UIDialogs.showConfirmationDialog(_inflater.context, _confirmationMessage, {
                StateSubscriptions.instance.removeSubscription(sub.channel.url, true);
            });
        };
        holder.onSettings.subscribe {
            onSettings.emit(it);
        };

        return holder;
    }

    override fun onBindViewHolder(viewHolder: SubscriptionViewHolder, position: Int) {
        viewHolder.bind(_sortedDataset[position]);
    }

    private fun updateDataset() {
        val queryLower = query?.lowercase() ?: "";
        _sortedDataset = when (sortBy) {
            0 -> StateSubscriptions.instance.getSubscriptions().sortedBy({ u -> u.channel.name.lowercase() })
            1 -> StateSubscriptions.instance.getSubscriptions().sortedByDescending({ u -> u.channel.name.lowercase() })
            2 -> StateSubscriptions.instance.getSubscriptions().sortedBy { it.playbackViews * VIEW_PRIORITY + it.playbackSeconds }
            3 -> StateSubscriptions.instance.getSubscriptions().sortedByDescending { it.playbackViews * VIEW_PRIORITY + it.playbackSeconds }
            4 -> StateSubscriptions.instance.getSubscriptions().sortedBy { it.playbackSeconds }
            5 -> StateSubscriptions.instance.getSubscriptions().sortedByDescending { it.playbackSeconds }
            else -> throw IllegalStateException("Invalid sorting algorithm selected.");
        }
            .filter { (queryLower.isNullOrBlank() || it.channel.name.lowercase().contains(queryLower)) }
            .toList();

        _onDatasetChanged?.invoke(_sortedDataset);

        notifyDataSetChanged();
    }


    companion object {
        val VIEW_PRIORITY = 36000 * 3;
    }
}
