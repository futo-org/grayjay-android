package com.futo.platformplayer.tv.fragments

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerControlView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.playback.PlaybackTracking
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.playlists.TvPlaylistQueue
import com.futo.platformplayer.tv.views.TvCommentsPanel
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvPlayerPanel
import com.futo.platformplayer.tv.views.TvVideoInfoPanel
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.views.video.FutoVideoPlayer
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class TvPlayerFragment : Fragment() {
    private val playbackTracking = PlaybackTracking()
    private var trackerJob: Job? = null
    private var playerView: FutoVideoPlayer? = null
    private var controls: PlayerControlView? = null
    private var revealKey: Int? = null
    private var details: IPlatformVideoDetails? = null
    private var position = 0L
    private var positionLoaded = false
    private var resumePlaying = true
    private var sourceLoaded = false
    private var errorDialog: TvMenuDialog? = null
    private lateinit var videoTitle: TextView
    private lateinit var videoAuthor: TextView
    private lateinit var stage: FrameLayout
    private lateinit var panelContainer: FrameLayout
    private var sidePanel: TvPlayerPanel? = null
    private var resizeAnimation: ValueAnimator? = null
    private var panelAction = 0
    private var sourceJob: Job? = null
    private var quality = -1
    private var subtitle = -1
    private var queue: TvPlaylistQueue? = null
    private var listener: Player.Listener? = null
    private var firstLoadHint: TextView? = null
    private var loadingIndicator: CircularProgressIndicator? = null
    private var generatingSource = false

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getLong("position") ?: requireArguments().getLong("position")
        positionLoaded = savedInstanceState?.getBoolean("positionLoaded") ?: requireArguments().containsKey("position")
        resumePlaying = savedInstanceState?.getBoolean("playing") ?: true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        stage = FrameLayout(context).apply { id = R.id.tv_player_stage }
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))
        panelContainer = FrameLayout(context).apply { visibility = View.GONE }
        root.addView(
            panelContainer,
            FrameLayout.LayoutParams(context.dp(360), -1, Gravity.END).apply {
                setMargins(0, context.dp(16), context.dp(16), context.dp(16))
            })
        val player = FutoVideoPlayer(context).apply {
            id = R.id.tv_player
            setFullScreen(true)
            hideControls(false)
            lockControlsAlpha(true)
            gestureControl.visibility = View.GONE
            background.visibility = View.GONE
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            attachPlayer()
        }
        playerView = player
        stage.addView(player, FrameLayout.LayoutParams(-1, -1))
        loadingIndicator = CircularProgressIndicator(context).apply {
            id = R.id.tv_player_loading
            isIndeterminate = true
            indicatorSize = context.dp(40)
            trackThickness = context.dp(3)
            setIndicatorColor(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        stage.addView(loadingIndicator, FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.CENTER))
        player.loaderGameVisibilityChanged.subscribe(this) { loading ->
            activity?.runOnUiThread {
                generatingSource = loading
                player.findViewById<View>(R.id.loader_overlay).visibility = View.GONE
                updateLoading()
            }
        }
        val controller = inflater.inflate(R.layout.tv_player, stage, false) as PlayerControlView
        controls = controller
        controller.player = player.exoPlayer?.player
        controller.setShowNextButton(false)
        controller.setShowPreviousButton(false)
        stage.addView(controller)
        firstLoadHint = TvUi.text(context, getString(R.string.tv_first_video_loading), 14f, true).apply {
            id = R.id.tv_first_video_loading
            gravity = Gravity.CENTER
            translationY = context.dp(48).toFloat()
            setPadding(context.dp(24), 0, context.dp(24), 0)
            visibility = if (state?.getBoolean("firstLoadHint") ?: !firstVideoOpened) View.VISIBLE else View.GONE
        }
        stage.addView(firstLoadHint, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        firstVideoOpened = true
        videoTitle = controller.findViewById(R.id.tv_player_title)
        videoTitle.setText(R.string.tv_loading)
        videoAuthor = controller.findViewById(R.id.tv_player_author)
        val hint = controller.findViewById<TextView>(R.id.tv_player_action_hint)
        val actions = listOf(
            androidx.media3.ui.R.id.exo_play_pause,
            androidx.media3.ui.R.id.exo_rew,
            androidx.media3.ui.R.id.exo_ffwd,
            R.id.tv_player_channel,
            R.id.tv_player_comments,
            R.id.tv_player_settings,
            R.id.tv_video_info)
        actions.forEach { id ->
            controller.findViewById<View>(id).setOnFocusChangeListener { button, focused ->
                if (focused) {
                    hint.text = button.contentDescription
                    hint.gravity = if (id in actions.take(3)) Gravity.START else Gravity.END
                }
                button.animate()
                    .scaleX(if (focused) 1.08f else 1f)
                    .scaleY(if (focused) 1.08f else 1f)
                    .setDuration(130)
                    .start()
            }
        }
        controller.findViewById<View>(androidx.media3.ui.R.id.exo_progress).apply {
            setOnFocusChangeListener { _, focused ->
                if (focused) {
                    hint.setText(R.string.tv_seek)
                    hint.gravity = Gravity.START
                }
            }
            setOnKeyListener { view, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        view.onKeyDown(key, event)
                        if (player.exoPlayer?.player?.playWhenReady == true) player.pause() else play()
                    }
                    true
                } else false
            }
        }
        controller.findViewById<View>(R.id.tv_player_channel).setOnClickListener {
            details?.author?.takeIf { it.url.isNotBlank() }?.let { host.openChannel(it.url, it.name, it.thumbnail) }
        }
        controller.findViewById<View>(R.id.tv_player_comments).setOnClickListener { showComments() }
        controller.findViewById<View>(R.id.tv_player_settings).setOnClickListener { showSettings() }
        controller.findViewById<View>(R.id.tv_video_info).setOnClickListener { showInfo() }
        controller.addVisibilityListener {
            if (it != View.VISIBLE && sidePanel == null) player.requestFocus()
        }
        player.requestFocus()
        player.onReloadRequired.subscribe(this) {
            activity?.runOnUiThread {
                if (view == null) return@runOnUiThread
                position = player.position
                positionLoaded = true
                loadVideo(refresh = true)
            }
        }
        player.onDatasourceError.subscribe(this) { showError() }
        player.onPlayChanged.subscribe(this) { playing ->
            playbackTracking.updateTracker(player.position, playing, true)
            val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            root.keepScreenOn = playing && resumed
            controller.showTimeoutMs = if (playing) 5_000 else 0
            controller.post { hint.text = controller.findFocus()?.contentDescription ?: "" }
            if (playing && !resumed) player.pause()
        }
        player.exoPlayer
            ?.player
            ?.addListener(
                object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        showError()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        updateLoading()
                        if (state == Player.STATE_READY || state == Player.STATE_ENDED) firstLoadHint?.visibility = View.GONE
                        if (state == Player.STATE_ENDED && queue?.next(automatic = true) != true) showControls()
                    }
                }.also { listener = it })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (requireArguments().containsKey(TvPlaylistQueue.KEY)) {
            queue = TvPlaylistQueue(
                requireArguments(), savedInstanceState, checkNotNull(controls), ::playQueuedVideo,
                viewLifecycleOwner.lifecycleScope)
        }
        parentFragmentManager.setFragmentResultListener("tv_resume", viewLifecycleOwner) { _, result ->
            position = result.getLong("position")
            positionLoaded = true
            resumePlaying = true
            if (sourceLoaded) {
                playerView?.seekTo(position)
                playerView?.play()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    playerView?.takeIf { sourceLoaded }?.let { player ->
                        playbackTracking.updatePlaybackTracking(details, player.position, player.playing)
                        playbackTracking.updateTracker(player.position, player.playing)
                    }
                    delay(500)
                }
            }
        }
        loadVideo()
    }

    override fun onPause() {
        if (sourceLoaded) resumePlaying = playerView?.exoPlayer?.player?.playWhenReady == true
        savePosition()
        playerView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (sourceLoaded && resumePlaying) playerView?.play()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        queue?.save(outState)
        outState.putLong("position", if (sourceLoaded) playerView?.position ?: position else position)
        outState.putBoolean("positionLoaded", positionLoaded)
        outState.putBoolean("playing", resumePlaying)
        outState.putBoolean("firstLoadHint", firstLoadHint?.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        trackerJob?.cancel()
        trackerJob = null
        playbackTracking.cleanupTracker()
        queue = null
        errorDialog?.dismiss()
        errorDialog = null
        resizeAnimation?.cancel()
        resizeAnimation = null
        (sidePanel as? TvCommentsPanel)?.dispose()
        sidePanel = null
        playerView?.let { player ->
            listener?.let { player.exoPlayer?.player?.removeListener(it) }
            player.onDatasourceError.remove(this)
            player.onPlayChanged.remove(this)
            player.onReloadRequired.remove(this)
            player.loaderGameVisibilityChanged.remove(this)
            player.stopAllGestures()
            player.stopAndReleaseSabrSession()
            player.clear()
            player.exoPlayer?.detach()
            player.changePlayer(null)
        }
        sourceLoaded = false
        loadingIndicator = null
        generatingSource = false
        controls?.player = null
        controls = null
        revealKey = null
        listener = null
        playerView = null
        firstLoadHint = null
        super.onDestroyView()
    }

    fun hideControls(): Boolean {
        sidePanel?.let {
            if (!it.back()) closePanel()
            return true
        }
        val controller = controls ?: return false
        if (!controller.isFullyVisible) return false
        controller.hide()
        return true
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (errorDialog?.isShowing == true) return false
        if (queue?.handleKey(event) == true) return true
        val player = playerView ?: return false
        val code = event.keyCode
        val media = code in
            setOf(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_STOP)
        if (media) {
            if (event.action == KeyEvent.ACTION_DOWN)
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> play()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                        if (player.exoPlayer?.player?.playWhenReady == true) player.pause() else play()
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(10_000)
                    KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-10_000)
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        player.pause()
                        host.supportFragmentManager.popBackStack()
                    }
                }
            return true
        }
        if (sidePanel != null) return false
        val timeline = controls?.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
        if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) &&
            (controls?.isFullyVisible != true || player.hasFocus() || timeline?.hasFocus() == true)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                controls?.show()
                timeline?.requestFocus()
                seek(if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 5_000 else -5_000)
            }
            return true
        }
        if (revealKey == code) {
            if (event.action == KeyEvent.ACTION_UP) revealKey = null
            return true
        }
        if (code in
            setOf(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (controls?.isFullyVisible != true || player.hasFocus()) {
                    revealKey = code
                    showControls()
                    return true
                }
                controls?.show()
            }
        }
        return false
    }

    private fun loadVideo(refresh: Boolean = false) {
        sourceLoaded = false
        updateLoading()
        trackerJob?.cancel()
        playbackTracking.cleanupTracker()
        sourceJob?.cancel()
        sourceJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val url = requireArguments().getString("url")!!
                if (refresh) {
                    StatePlatform.instance.clearContentDetailCache(url)
                    host.currentVideo = null
                }
                details = details?.takeIf { !refresh && it.url == url }
                    ?: host.currentVideo?.takeIf { it.url == url }
                    ?: StatePlatform.instance.getContentDetails(url).await() as? IPlatformVideoDetails
                    ?: throw IllegalArgumentException("Not a video")
                val player = playerView ?: return@launch
                val video = details!!
                videoTitle.text = video.name
                videoAuthor.text = video.author.name
                controls?.findViewById<View>(R.id.tv_player_channel)?.apply {
                    isEnabled = video.author.url.isNotBlank()
                    alpha = if (isEnabled) 1f else 0.35f
                }
                player.setMetadata(video.name, video.author.name)
                if (!positionLoaded) {
                    val history = StateHistory.instance.getHistoryPosition(video.url)
                    position = if (!video.isLive && history > 5 && history < video.duration - 5) history * 1000 else 0
                    positionLoaded = true
                }
                player.switchToVideoMode()
                val settings = Settings.instance.playback
                val source = qualitySources(video).getOrNull(quality)
                    ?: player.getPreferredVideoSource(video, settings.getCurrentPreferredQualityPixelCount())
                player.setSource(
                    source,
                    player.getPreferredAudioSource(video, settings.getPrimaryLanguage(requireContext())),
                    play = resumePlaying,
                    resume = position > 0)
                video.subtitles.getOrNull(subtitle)?.let {
                    player.swapSubtitles(it)
                    player.pause()
                }
                player.setPlaybackRate(settings.getDefaultPlaybackSpeed().toFloat())
                player.seekTo(position)
                sourceLoaded = true
                updateLoading()
                loadPlaybackTracker(video)
                queue?.loaded(video)
                if (resumePlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    player.play()
                showControls()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError()
            }
        }
    }

    private fun updateLoading() {
        val state = playerView?.exoPlayer?.player?.playbackState
        val loading = !sourceLoaded || generatingSource || state == Player.STATE_IDLE || state == Player.STATE_BUFFERING
        loadingIndicator?.visibility = if (loading && errorDialog?.isShowing != true) View.VISIBLE else View.GONE
    }

    private fun loadPlaybackTracker(video: IPlatformVideoDetails) {
        trackerJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tracker = PlaybackTracking.getTracker(video)
                var attached = false
                try {
                    withContext(Dispatchers.Main) {
                        if (sourceLoaded && details === video) {
                            playbackTracking.setTracker(tracker)
                            attached = true
                            playerView?.let { playbackTracking.updateTracker(it.position, it.playing, true) }
                        }
                    }
                } finally {
                    if (!attached) tracker?.onConcluded()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvPlayerFragment", "Playback tracker failed", error)
            }
        }
    }

    private fun showControls() {
        if (sidePanel != null) return
        controls?.show()
        controls?.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
    }

    private fun playQueuedVideo(url: String) {
        firstLoadHint?.visibility = View.GONE
        savePosition()
        playerView?.pause()
        if (sidePanel != null) closePanel()
        requireArguments().putString("url", url)
        details = null
        sourceLoaded = false
        position = 0
        positionLoaded = true
        resumePlaying = true
        quality = -1
        subtitle = -1
        videoTitle.setText(R.string.tv_loading)
        loadVideo()
    }

    private fun play() {
        val player = playerView ?: return
        if (player.exoPlayer?.player?.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    private fun seek(delta: Long) {
        val player = playerView ?: return
        if (player.exoPlayer?.player?.isCurrentMediaItemSeekable != true) return
        val target = (player.position + delta).coerceAtLeast(0)
        player.seekTo(if (player.duration > 0) target.coerceAtMost(player.duration) else target)
    }

    private fun openPanel(panel: TvPlayerPanel, action: Int) {
        (sidePanel as? TvCommentsPanel)?.dispose()
        sidePanel = panel
        panelAction = action
        panelContainer.removeAllViews()
        panelContainer.addView(panel, FrameLayout.LayoutParams(-1, -1))
        panelContainer.visibility = View.VISIBLE
        panelContainer.alpha = 0f
        panelContainer.translationX = requireContext().dp(16).toFloat()
        panelContainer.animate().alpha(1f).translationX(0f).setDuration(200).start()
        resizeStage(compact = true)
        controls?.hideImmediately()
        panel.requestFocus()
    }

    private fun closePanel() {
        (sidePanel as? TvCommentsPanel)?.dispose()
        sidePanel = null
        panelContainer.animate().cancel()
        panelContainer.visibility = View.GONE
        panelContainer.removeAllViews()
        resizeStage(compact = false)
        controls?.show()
        controls?.findViewById<View>(panelAction)?.requestFocus()
    }

    private fun resizeStage(compact: Boolean) {
        val root = view as? FrameLayout ?: return
        resizeAnimation?.cancel()
        val width = when {
            !compact -> 0
            sidePanel is TvVideoInfoPanel -> root.width / 2
            else -> (root.width * 0.38f).toInt().coerceAtMost(requireContext().dp(360))
        }
        if (compact)
            panelContainer.layoutParams = (panelContainer.layoutParams as FrameLayout.LayoutParams).apply { this.width = width }
        val target = if (compact) root.width - width - requireContext().dp(24) else root.width
        videoTitle.textSize = if (compact) 20f else 25f
        // Keep the video surface size stable so a paused frame survives the transition.
        playerView?.apply {
            layoutParams = layoutParams.apply { this.width = root.width }
            pivotX = 0f
            pivotY = root.height / 2f
        }
        resizeAnimation = ValueAnimator.ofInt(stage.width, target).apply {
            duration = 220
            addUpdateListener {
                val stageWidth = it.animatedValue as Int
                stage.layoutParams = stage.layoutParams.apply { this.width = stageWidth }
                playerView?.apply {
                    scaleX = stageWidth.toFloat() / root.width
                    scaleY = scaleX
                }
            }
            start()
        }
    }

    private fun showInfo() {
        val video = details ?: return
        val panel = TvVideoInfoPanel(requireContext(), video, {
            host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
        }, ::closePanel)
        openPanel(panel, R.id.tv_video_info)
        panel.findViewById<View>(R.id.tv_info_description).requestFocus()
    }

    private fun showComments() {
        val video = details ?: return
        val panel = TvCommentsPanel(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            { StatePlatform.instance.getComments(video) },
            { StatePlatform.instance.getSubComments(it) },
            ::closePanel)
        openPanel(panel, R.id.tv_player_comments)
    }

    private fun showSettings() {
        if (details == null) return
        val panel = TvPlayerPanel(requireContext(), ::closePanel).apply { id = R.id.tv_settings_panel }
        openPanel(panel, R.id.tv_player_settings)
        settingsMenu(panel)
    }

    private fun settingsMenu(panel: TvPlayerPanel) {
        val video = details ?: return
        panel.title(getString(R.string.tv_playback_settings))
        panel.onBack = null
        val menu = panel.menu()
        val source = qualitySources(video).getOrNull(quality)
        menu.addView(
            panel.option(
                getString(R.string.tv_quality),
                source?.let { qualityLabel(it.height) } ?: getString(R.string.tv_auto)) {
                chooseQuality(panel)
            }.apply { id = R.id.tv_quality_option })
        menu.addView(
            panel.option(
                getString(R.string.tv_subtitles),
                video.subtitles.getOrNull(subtitle)?.name ?: getString(R.string.tv_off)) {
                chooseSubtitle(panel)
            }.apply { id = R.id.tv_subtitles_option })
        menu.requestFocus()
    }

    private fun qualitySources(video: IPlatformVideoDetails) =
        video.video.videoSources
            .filter { it.height > 0 }
            .groupBy { it.height }
            .toSortedMap(reverseOrder())
            .values
            .mapNotNull {
                VideoHelper.selectBestVideoSource(it, -1, FutoVideoPlayerBase.PREFERED_VIDEO_CONTAINERS)
            }

    private fun qualityLabel(height: Int) =
        when {
            height >= 2160 -> "${height}p · 4K"
            height >= 720 -> "${height}p · HD"
            else -> "${height}p"
        }

    private fun chooseQuality(panel: TvPlayerPanel) {
        val video = details ?: return
        val sources = qualitySources(video)
        val labels = listOf(getString(R.string.tv_auto)) + sources.map { qualityLabel(it.height) }
        panel.title(getString(R.string.tv_quality), true)
        panel.onBack = {
            settingsMenu(panel)
            true
        }
        val menu = panel.menu()
        labels.forEachIndexed { index, label ->
            menu.addView(
                panel.option(label, checked = quality == index - 1) {
                    sourceJob?.cancel()
                    sourceJob = viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val player = playerView ?: return@launch
                            panel.message(getString(R.string.tv_loading))
                            val source = sources.getOrNull(index - 1) ?: player.getPreferredVideoSource(video)
                            val play = player.exoPlayer?.player?.playWhenReady == true
                            player.setSource(
                                source,
                                player.getPreferredAudioSource(
                                    video, Settings.instance.playback.getPrimaryLanguage(requireContext())),
                                play = play,
                                keepSubtitles = true,
                                resume = true)
                            quality = index - 1
                            if (sidePanel === panel) settingsMenu(panel)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            if (sidePanel === panel) settingsMenu(panel)
                            showError()
                        }
                    }
                })
        }
        menu.getChildAt(quality + 1)?.requestFocus()
    }

    private fun chooseSubtitle(panel: TvPlayerPanel) {
        val video = details ?: return
        panel.title(getString(R.string.tv_subtitles), true)
        panel.onBack = {
            settingsMenu(panel)
            true
        }
        val menu = panel.menu()
        val labels = listOf(getString(R.string.tv_off)) + video.subtitles.map { it.name }
        labels.forEachIndexed { index, label ->
            menu.addView(
                panel.option(
                    label,
                    if (video.subtitles.isEmpty()) getString(R.string.tv_no_subtitles) else null,
                    subtitle == index - 1) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val player = playerView ?: return@launch
                            val playing = player.exoPlayer?.player?.playWhenReady == true
                            player.swapSubtitles(video.subtitles.getOrNull(index - 1))
                            if (!playing) player.pause()
                            subtitle = index - 1
                            if (sidePanel === panel) settingsMenu(panel)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            showError()
                        }
                    }
                })
        }
        menu.getChildAt(subtitle + 1)?.requestFocus()
    }

    private fun showError() {
        activity?.runOnUiThread {
            firstLoadHint?.visibility = View.GONE
            loadingIndicator?.visibility = View.GONE
            if (!isAdded || view == null || errorDialog?.isShowing == true) return@runOnUiThread
            errorDialog = TvMenuDialog(requireContext()).also { dialog ->
                dialog.panel.title(getString(R.string.tv_player_error))
                val menu = dialog.panel.menu()
                val retry = dialog.panel.option(getString(R.string.tv_retry)) {
                    dialog.dismiss()
                    position = playerView?.position ?: position
                    loadVideo(refresh = true)
                }
                menu.addView(retry)
                menu.addView(dialog.panel.option(getString(R.string.tv_close)) {
                    dialog.dismiss()
                    host.supportFragmentManager.popBackStack()
                })
                dialog.setOnShowListener { retry.requestFocus() }
                dialog.show()
            }
        }
    }

    private fun savePosition() {
        val video = details ?: return
        val player = playerView ?: return
        if (!sourceLoaded) return
        position = player.position
        if (!video.isLive && position > 0) {
            StateHistory.instance.getHistoryByVideo(video, true)?.let {
                StateHistory.instance.updateHistoryPosition(video, it, true, position / 1000)
            }
        }
    }

    companion object {
        private var firstVideoOpened = false
    }
}
