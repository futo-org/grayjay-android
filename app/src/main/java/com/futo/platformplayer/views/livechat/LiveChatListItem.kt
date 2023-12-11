package com.futo.platformplayer.views.livechat

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.views.overlays.LiveChatOverlay

abstract class LiveChatListItem(view: View): RecyclerView.ViewHolder(view) {
    protected val _view = view;
    abstract fun bind(chat: LiveChatOverlay.ChatMessage);
}