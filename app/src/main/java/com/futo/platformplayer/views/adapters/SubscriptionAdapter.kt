package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.constructs.Event1

class SubscriptionAdapter : RecyclerView.Adapter<SubscriptionViewHolder> {
    private lateinit var _sortedDataset: List<Subscription>;
    private val _inflater: LayoutInflater;
    private val _confirmationMessage: String;

    var onClick = Event1<Subscription>();
    var onSettings = Event1<Subscription>();
    var sortBy: Int = 3
        set(value) {
            field = value;
            updateDataset();
        }

    constructor(inflater: LayoutInflater, confirmationMessage: String) : super() {
        _inflater = inflater;
        _confirmationMessage = confirmationMessage;

        StateSubscriptions.instance.onSubscriptionsChanged.subscribe { subs, added -> updateDataset(); }
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
                StateSubscriptions.instance.removeSubscription(sub.channel.url);
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
        _sortedDataset = when (sortBy) {
            0 -> StateSubscriptions.instance.getSubscriptions().sortedBy({ u -> u.channel.name })
            1 -> StateSubscriptions.instance.getSubscriptions().sortedByDescending({ u -> u.channel.name })
            2 -> StateSubscriptions.instance.getSubscriptions().sortedBy { it.playbackViews }
            3 -> StateSubscriptions.instance.getSubscriptions().sortedByDescending { it.playbackViews }
            4 -> StateSubscriptions.instance.getSubscriptions().sortedBy { it.playbackSeconds }
            5 -> StateSubscriptions.instance.getSubscriptions().sortedByDescending { it.playbackSeconds }
            else -> throw IllegalStateException("Invalid sorting algorithm selected.");
        }.toList();

        notifyDataSetChanged();
    }
}
