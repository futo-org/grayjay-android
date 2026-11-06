package com.futo.platformplayer.tv.fragments

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TvVideoDetailsFragment : Fragment() {
    private var video: IPlatformVideoDetails? = null
    private var descriptionDialog: TvMenuDialog? = null
    private lateinit var root: FrameLayout
    private lateinit var column: LinearLayout

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = FrameLayout(requireContext())
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        load()
    }

    override fun onDestroyView() {
        descriptionDialog?.dismiss()
        descriptionDialog = null
        super.onDestroyView()
    }

    private fun load() {
        root.removeAllViews()
        column = TvUi.column(requireContext()).apply {
            setPadding(context.dp(32), context.dp(32), context.dp(32), context.dp(24))
        }
        root.addView(column)
        val loading = TvUi.text(requireContext(), getString(R.string.tv_loading), 16f, true)
        column.addView(loading)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val url = requireArguments().getString("url")!!
                val details = video
                    ?: host.currentVideo?.takeIf { it.url == url }
                    ?: StatePlatform.instance.getContentDetails(url).await() as? IPlatformVideoDetails
                    ?: throw IllegalArgumentException("Not a video")
                video = details
                show(details)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isAdded || view == null) return@launch
                loading.text = getString(R.string.tv_error)
                column.addView(
                    TvUi.button(requireContext(), getString(R.string.tv_retry)) { load() }.apply { requestFocus() })
            }
        }
    }

    private fun show(video: IPlatformVideoDetails) {
        root.removeAllViews()
        val context = requireContext()
        val poster = TvUi.image(context).apply {
            clipToOutline = false
            background = null
        }
        TvUi.load(poster, video.thumbnails.getHQThumbnail())
        root.addView(poster, FrameLayout.LayoutParams(context.dp(590), context.dp(332), Gravity.TOP or Gravity.END))
        root.addView(
            View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(TvUi.background, 0xF80F0F0F.toInt(), 0xA00F0F0F.toInt(), 0x100F0F0F))
            },
            FrameLayout.LayoutParams(-1, context.dp(332)))
        root.addView(
            View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x000F0F0F, TvUi.background))
            },
            FrameLayout.LayoutParams(-1, context.dp(140)).apply { topMargin = context.dp(192) })
        column = TvUi.column(context).apply { setPadding(context.dp(32), context.dp(110), context.dp(36), context.dp(28)) }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(column)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        column.addView(TvUi.text(context, video.author.name, 16f, true).apply { setPadding(0, 0, 0, context.dp(8)) })
        column.addView(
            TvUi.heading(context, video.name).apply {
                textSize = 30f
                lines(3)
            },
            LinearLayout.LayoutParams(context.dp(550), -2))
        val metadata = listOfNotNull(
            if (video.isLive) getString(R.string.tv_live) else TvUi.duration(video.duration),
            if (video.viewCount >= 0) getString(R.string.tv_view_count, TvUi.count(video.viewCount)) else null,
            video.datetime?.toLocalDate()?.toString())
            .joinToString("   ·   ")
        column.addView(
            TvUi.text(context, metadata, 14f, true).apply { setPadding(0, context.dp(12), 0, context.dp(14)) })
        val position = StateHistory.instance.getHistoryPosition(video.url)
        val resume = !video.isLive && position > 5 && position < video.duration - 5
        val actions = TvUi.row(context)
        val play = TvUi.button(context, getString(if (resume) R.string.tv_resume else R.string.tv_play)) {
            host.play(video, if (resume) position * 1000 else 0)
        }.apply { id = R.id.tv_play }
        actions.addView(play)
        if (resume) actions.addView(TvUi.button(context, getString(R.string.tv_restart)) { host.play(video) })
        if (video.author.url.isNotBlank())
            actions.addView(
                TvUi.button(context, getString(R.string.tv_channel)) {
                    host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
                })
        val saved = TvUi.button(context, "") {}.apply { id = R.id.tv_save }
        fun updateSaved() {
            saved.text = getString(
                if (StatePlaylists.instance.getWatchLaterFromUrl(video.url) != null) R.string.tv_saved
                else R.string.tv_save)
        }
        updateSaved()
        saved.setOnClickListener {
            if (StatePlaylists.instance.getWatchLaterFromUrl(video.url) != null)
                StatePlaylists.instance.removeFromWatchLater(video.url, true)
            else StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video), true)
            updateSaved()
        }
        actions.addView(saved)
        column.addView(actions)
        if (video.description.isNotBlank()) {
            val description = HtmlCompat.fromHtml(video.description, HtmlCompat.FROM_HTML_MODE_COMPACT)
            column.addView(
                TvUi.text(context, description, 15f, true).apply {
                    lines(3)
                    isFocusable = true
                    setPadding(context.dp(8), context.dp(12), context.dp(8), context.dp(12))
                    setBackgroundResource(R.drawable.tv_card_focus)
                    setOnClickListener {
                        descriptionDialog = TvMenuDialog(context, bottomSheet = true).also { dialog ->
                            dialog.panel.title(video.name)
                            val menu = dialog.panel.menu()
                            menu.addView(TvUi.text(context, description, 16f).apply {
                                setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(20))
                                setLineSpacing(context.dp(4).toFloat(), 1f)
                            })
                            val reading = (menu.parent as ScrollView).apply {
                                isFocusable = true
                                setBackgroundResource(R.drawable.tv_channel_reading)
                            }
                            dialog.setOnShowListener { reading.requestFocus() }
                            dialog.setOnDismissListener { if (isAttachedToWindow) requestFocus() }
                            dialog.show()
                        }
                    }
                },
                LinearLayout.LayoutParams(context.dp(650), -2).apply { topMargin = context.dp(10) })
        }
        play.requestFocus()
    }
}
