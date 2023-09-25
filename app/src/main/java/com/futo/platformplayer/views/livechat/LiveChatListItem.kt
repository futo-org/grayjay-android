package com.futo.platformplayer.views.livechat

import android.graphics.Color
import android.graphics.drawable.LevelListDrawable
import android.text.Spannable
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.live.LiveEventComment
import com.futo.platformplayer.dp
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.overlays.LiveChatOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class LiveChatListItem(view: View): RecyclerView.ViewHolder(view) {
    protected val _view = view;
    abstract fun bind(chat: LiveChatOverlay.ChatMessage);
}