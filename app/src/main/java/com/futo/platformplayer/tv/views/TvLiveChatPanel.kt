package com.futo.platformplayer.tv.views

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.LiveChatManager
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.JSVideoDetails
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.views.overlays.LiveChatOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TvLiveChatPanel(
    context: Context,
    scope: CoroutineScope,
    private val video: IPlatformVideoDetails,
    openVideo: (String) -> Unit,
    close: () -> Unit
) : TvPlayerPanel(context, close) {
    private val chatScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val chat = LiveChatOverlay(context)
    private val window = chat.findViewById<WebView>(R.id.chatWindowContainer)
    private var manager: LiveChatManager? = null
    private var loadJob: Job? = null
    private var paused = false
    private var disposed = false

    init {
        id = R.id.tv_live_chat_panel
        val label = context.getString(if (video.isLive) R.string.live_chat else R.string.vod_chat)
        title(label)
        chat.findViewById<TextView>(R.id.text_name).text = label
        chat.onClose.subscribe(this, close)
        chat.onRaidNow.subscribe(this) { openVideo(it.targetUrl) }
        chat.onUrlClick.subscribe(this) { uri, origin ->
            if (uri.scheme == "https" || uri.scheme == "http") chat.loadInChat(uri, origin)
        }
        listOf(R.id.button_close, R.id.button_back_to_chat, R.id.raid_button_go, R.id.raid_button_prevent).forEach {
            chat.findViewById<View>(it).apply {
                isFocusable = true
                backgroundTintList = null
                if (this is TextView) setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.tv_panel_item)
            }
        }
        chat.findViewById<ViewGroup>(R.id.donation_list).setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                child.isFocusable = true
                child.foreground = ContextCompat.getDrawable(context, R.drawable.tv_card_focus)
            }

            override fun onChildViewRemoved(parent: View, child: View) = Unit
        })
        chat.findViewById<RecyclerView>(R.id.chatContainer).apply {
            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(context.dp(8), context.dp(4), context.dp(8), context.dp(8))
            }
            val chatLayout = ChatLayout(context)
            layoutManager = chatLayout
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE && !canScrollVertically(1))
                        chatLayout.followLatest = true
                }
            })
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            contentDescription = label
            foreground = ContextCompat.getDrawable(context, R.drawable.tv_card_focus)
            setOnKeyListener { _, key, event ->
                val direction = when (key) {
                    KeyEvent.KEYCODE_DPAD_UP -> -1
                    KeyEvent.KEYCODE_DPAD_DOWN -> 1
                    else -> return@setOnKeyListener false
                }
                if (direction < 0 && !canScrollVertically(-1)) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    stopScroll()
                    chatLayout.followLatest = direction > 0 && !canScrollVertically(1)
                    smoothScrollBy(0, direction * height / 3)
                }
                true
            }
        }
        load()
    }

    override fun back(): Boolean {
        if (chat.findViewById<View>(R.id.overlay).isShown) {
            chat.hideOverlay()
            chat.findViewById<View>(R.id.button_close).requestFocus()
            return true
        }
        return chat.returnToChat()
    }

    fun setVideoPosition(position: Long) {
        manager?.setVideoPosition(position)
    }

    fun pause() {
        paused = true
        manager?.stop()
        window.onPause()
    }

    fun resume() {
        paused = false
        window.onResume()
        manager?.start()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        chatScope.cancel()
        chat.cancel()
        manager = null
        window.stopLoading()
        (window.parent as? ViewGroup)?.removeView(window)
        window.destroy()
    }

    private fun load() {
        loadJob?.cancel()
        chat.cancel()
        manager = null
        getChildAt(0).visibility = View.VISIBLE
        message(context.getString(R.string.tv_loading))
        loadJob = chatScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val pager = loadPart {
                        if (video.isLive) StatePlatform.instance.getLiveEvents(video.url)
                        else (video as? JSVideoDetails)?.getVODEvents(video.url)
                    }
                    val descriptor = if (video.isLive && Settings.instance.playback.useLiveChatWindow)
                        loadPart { StatePlatform.instance.getLiveChatWindow(video.url) } else null
                    pager?.let { LiveChatManager(chatScope, it, video.viewCount) } to descriptor
                }
                if (result.first == null && result.second == null) {
                    message(context.getString(R.string.tv_live_chat_unavailable), retry = ::load)
                    return@launch
                }
                manager = result.first
                chat.load(chatScope, manager, result.second, if (manager != null) video.viewCount else null)
                getChildAt(0).visibility = View.GONE
                body.removeAllViews()
                body.addView(chat, FrameLayout.LayoutParams(-1, -1))
                chat.findViewById<View>(R.id.button_close).requestFocus()
                if (paused) window.onPause() else manager?.start()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvLiveChat", "Failed to load chat", error)
                message(context.getString(R.string.live_chat_failed_to_load), retry = ::load)
            }
        }
    }

    private fun <T> loadPart(load: () -> T): T? = try {
        load()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.w("TvLiveChat", "Failed to retrieve chat source", error)
        null
    }

    private class ChatLayout(context: Context) : LinearLayoutManager(context) {
        var followLatest = true

        init {
            stackFromEnd = true
        }

        override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State?, position: Int) {
            if (followLatest && itemCount > 0)
                super.smoothScrollToPosition(recyclerView, state, position.coerceAtMost(itemCount - 1))
        }
    }
}
