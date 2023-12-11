package com.futo.platformplayer.views.livechat

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.api.media.models.live.LiveEventComment
import com.futo.platformplayer.api.media.models.live.LiveEventDonation
import com.futo.platformplayer.views.overlays.LiveChatOverlay

class LiveChatListAdapter : RecyclerView.Adapter<LiveChatListItem> {

    private val _dataSet: ArrayList<LiveChatOverlay.ChatMessage>;


    constructor(dataSet: ArrayList<LiveChatOverlay.ChatMessage>): super() {
        this._dataSet = dataSet;
    }

    override fun getItemCount(): Int = _dataSet.size;
    override fun getItemViewType(position: Int): Int {
        if (position < 0) {
            return -1;
        }
        val item = _dataSet.getOrNull(position) ?: return -1;

        return when (item.event) {
            is LiveEventComment -> 1
            is LiveEventDonation -> 2
            else -> -1
        };
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): LiveChatListItem {
        return when(viewType) {
            1 -> createLiveChatListItem(viewGroup);
            2 -> createLiveChatDonationListItem(viewGroup);
            else -> EmptyItem(viewGroup);
        };
    }

    private fun createLiveChatDonationListItem(viewGroup: ViewGroup): LiveChatDonationListItem = LiveChatDonationListItem(viewGroup).apply {
    }
    private fun createLiveChatListItem(viewGroup: ViewGroup): LiveChatListItem = LiveChatMessageListItem(viewGroup).apply {
    };

    override fun onBindViewHolder(holder: LiveChatListItem, position: Int) {
        val value = _dataSet[position];

        holder.bind(value);
    }

    companion object {
        private val TAG = "LiveChatListAdapter";
    }

    class EmptyItem(viewGroup: ViewGroup): LiveChatListItem(LinearLayout(viewGroup.context)) {
        override fun bind(chat: LiveChatOverlay.ChatMessage) {}
    }
}