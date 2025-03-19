package com.futo.platformplayer.fragment.mainactivity.main

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.exceptions.ContentNotAvailableYetException
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.engine.exceptions.ScriptAgeException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.exceptions.UnsupportedCastException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.views.video.FutoShortPlayer
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
class ShortView : ConstraintLayout {
    private var mainFragment: MainFragment? = null
    private val player: FutoShortPlayer
    private val overlayLoading: FrameLayout
    private val overlayLoadingSpinner: ImageView

    private var url: String? = null
    private var video: IPlatformVideo? = null
    private var videoDetails: IPlatformVideoDetails? = null

    private var playWhenReady = false

    private var _lastVideoSource: IVideoSource? = null
    private var _lastAudioSource: IAudioSource? = null
    private var _lastSubtitleSource: ISubtitleSource? = null

    private var loadVideoJob: Job? = null

    private val bottomSheet: CommentsModalBottomSheet = CommentsModalBottomSheet()

    // Required constructor for XML inflation
    constructor(context: Context) : super(context) {
        inflate(context, R.layout.view_short, this)
        player = findViewById(R.id.short_player)
        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        setupComposeView()
    }

    // Required constructor for XML inflation with attributes
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        inflate(context, R.layout.view_short, this)
        player = findViewById(R.id.short_player)
        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        setupComposeView()
    }

    // Required constructor for XML inflation with attributes and style
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        inflate(context, R.layout.view_short, this)
        player = findViewById(R.id.short_player)
        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        setupComposeView()
    }

    constructor(inflater: LayoutInflater, fragment: MainFragment) : super(inflater.context) {
        inflater.inflate(R.layout.view_short, this, true)
        player = findViewById(R.id.short_player)
        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        setupComposeView()

        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )

        this.mainFragment = fragment
    }

    private fun setupComposeView () {
        val composeView: ComposeView = findViewById(R.id.compose_view_test_button)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // In Compose world
                MaterialTheme {
                    var checked by remember { mutableStateOf(false) }

                    val tint = Color.White

                    val alpha = 0.2f
                    val rippleConfiguration =
                        RippleConfiguration(color = tint, rippleAlpha = RippleAlpha(alpha, alpha, alpha, alpha))

                    val view = LocalView.current

                    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
                        IconToggleButton(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                view.playSoundEffect(SoundEffectConstants.CLICK)
                            },
                        ) {
                            if (checked) {
                                Icon(
                                    Icons.Filled.ThumbUp, contentDescription = "Liked", tint = tint,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.ThumbUp, contentDescription = "Not Liked", tint = tint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun setMainFragment(fragment: MainFragment) {
        this.mainFragment = fragment
    }

    fun setVideo(url: String) {
        if (url == this.url) {
            return
        }

        loadVideo(url)
    }

    fun setVideo(video: IPlatformVideo) {
        if (url == video.url) {
            return
        }
        this.video = video

        loadVideo(video.url)
    }

    fun setVideo(videoDetails: IPlatformVideoDetails) {
        if (url == videoDetails.url) {
            return
        }

        this.videoDetails = videoDetails
    }

    fun play() {
        player.attach()
        playVideo()
    }

    fun stop() {
        playWhenReady = false

        player.clear()
        player.detach()
    }

    fun detach() {
        loadVideoJob?.cancel()
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            (overlayLoadingSpinner.drawable as Animatable?)?.start()
            overlayLoading.visibility = VISIBLE
        } else {
            overlayLoading.visibility = GONE
            (overlayLoadingSpinner.drawable as Animatable?)?.stop()
        }
    }

    private fun loadVideo(url: String) {
        loadVideoJob?.cancel()

        loadVideoJob = CoroutineScope(Dispatchers.Main).launch {
            setLoading(true)
            _lastVideoSource = null
            _lastAudioSource = null
            _lastSubtitleSource = null

            val result = try {
                withContext(StateApp.instance.scope.coroutineContext) {
                    StatePlatform.instance.getContentDetails(url).await()
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: NoPlatformClientException) {
                Logger.w(TAG, "exception<NoPlatformClientException>", e)

                UIDialogs.showDialog(
                    context, R.drawable.ic_sources, "No source enabled to support this video\n(${url})", null, null, 0, UIDialogs.Action(
                        "Close", { }, UIDialogs.ActionStyle.PRIMARY
                    )
                )
                return@launch
            } catch (e: ScriptLoginRequiredException) {
                Logger.w(TAG, "exception<ScriptLoginRequiredException>", e)
                UIDialogs.showDialog(context, R.drawable.ic_security, "Authentication", e.message, null, 0, UIDialogs.Action("Cancel", {}), UIDialogs.Action("Login", {
                    val id = e.config.let { if (it is SourcePluginConfig) it.id else null }
                    val didLogin =
                        if (id == null) false else StatePlugins.instance.loginPlugin(context, id) {
                            loadVideo(url)
                        }
                    if (!didLogin) UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, "Failed to login")
                }, UIDialogs.ActionStyle.PRIMARY)
                )
                return@launch
            } catch (e: ContentNotAvailableYetException) {
                Logger.w(TAG, "exception<ContentNotAvailableYetException>", e)
                UIDialogs.showSingleButtonDialog(context, R.drawable.ic_schedule, "Video is available in ${e.availableWhen}.", "Close") { }
                return@launch
            } catch (e: ScriptImplementationException) {
                Logger.w(TAG, "exception<ScriptImplementationException>", e)
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video_scriptimplementationexception), e, { loadVideo(url) }, null, mainFragment)
                return@launch
            } catch (e: ScriptAgeException) {
                Logger.w(TAG, "exception<ScriptAgeException>", e)
                UIDialogs.showDialog(
                    context, R.drawable.ic_lock, "Age restricted video", e.message, null, 0, UIDialogs.Action("Close", { }, UIDialogs.ActionStyle.PRIMARY)
                )
                return@launch
            } catch (e: ScriptUnavailableException) {
                Logger.w(TAG, "exception<ScriptUnavailableException>", e)
                if (video?.datetime == null || video?.datetime!! < OffsetDateTime.now()
                        .minusHours(1)
                ) {
                    UIDialogs.showDialog(
                        context, R.drawable.ic_lock, context.getString(R.string.unavailable_video), context.getString(R.string.this_video_is_unavailable), null, 0, UIDialogs.Action(context.getString(R.string.close), { }, UIDialogs.ActionStyle.PRIMARY)
                    )
                }

                video?.let { StatePlatform.instance.clearContentDetailCache(it.url) }
                return@launch
            } catch (e: ScriptException) {
                Logger.w(TAG, "exception<ScriptException>", e)

                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video_scriptexception), e, { loadVideo(url) }, null, mainFragment)
                return@launch
            } catch (e: Throwable) {
                Logger.w(ChannelFragment.TAG, "Failed to load video.", e)
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video), e, { loadVideo(url) }, null, mainFragment)
                return@launch
            }

            if (result !is IPlatformVideoDetails) {
                Logger.w(
                    TAG, "Wrong content type", IllegalStateException("Expected media content, found ${result.contentType}")
                )
                return@launch
            }

            // if it's been canceled then don't set the video details
            if (!isActive) {
                return@launch
            }

            videoDetails = result
            video = result

            setLoading(false)

            if (playWhenReady) playVideo()
        }
    }

    private fun playVideo(resumePositionMs: Long = 0) {
        val video = videoDetails

        if (video === null) {
            playWhenReady = true
            return
        }

        bottomSheet.show(mainFragment!!.childFragmentManager, CommentsModalBottomSheet.TAG)

        try {
            val videoSource = _lastVideoSource
                ?: player.getPreferredVideoSource(video, Settings.instance.playback.getCurrentPreferredQualityPixelCount())
            val audioSource = _lastAudioSource
                ?: player.getPreferredAudioSource(video, Settings.instance.playback.getPrimaryLanguage(context))
            val subtitleSource = _lastSubtitleSource
                ?: (if (video is VideoLocal) video.subtitlesSources.firstOrNull() else null)
            Logger.i(TAG, "loadCurrentVideo(videoSource=$videoSource, audioSource=$audioSource, subtitleSource=$subtitleSource, resumePositionMs=$resumePositionMs)")

            if (videoSource == null && audioSource == null) {
                UIDialogs.showDialog(
                    context, R.drawable.ic_lock, context.getString(R.string.unavailable_video), context.getString(R.string.this_video_is_unavailable), null, 0, UIDialogs.Action(context.getString(R.string.close), { }, UIDialogs.ActionStyle.PRIMARY)
                )
                StatePlatform.instance.clearContentDetailCache(video.url)
                return
            }

            val thumbnail = video.thumbnails.getHQThumbnail()
            if (videoSource == null && !thumbnail.isNullOrBlank()) Glide.with(context).asBitmap()
                .load(thumbnail).into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        player.setArtwork(resource.toDrawable(resources))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        player.setArtwork(null)
                    }
                })
            else player.setArtwork(null)
            player.setSource(videoSource, audioSource, play = true, keepSubtitles = false, resume = resumePositionMs > 0)
            if (subtitleSource != null) player.swapSubtitles(mainFragment!!.lifecycleScope, subtitleSource)
            player.seekTo(resumePositionMs)

            _lastVideoSource = videoSource
            _lastAudioSource = audioSource
            _lastSubtitleSource = subtitleSource
        } catch (ex: UnsupportedCastException) {
            Logger.e(TAG, "Failed to load cast media", ex)
            UIDialogs.showGeneralErrorDialog(context, context.getString(R.string.unsupported_cast_format), ex)
        } catch (ex: Throwable) {
            Logger.e(TAG, "Failed to load media", ex)
            UIDialogs.showGeneralErrorDialog(context, context.getString(R.string.failed_to_load_media), ex)
        }
    }

    companion object {
        const val TAG = "VideoDetailView"
    }

    class CommentsModalBottomSheet : BottomSheetDialogFragment() {
        override fun onCreateDialog(
            savedInstanceState: Bundle?,
        ): Dialog {
            val bottomSheetDialog = BottomSheetDialog(
                requireContext()
            )
            bottomSheetDialog.setContentView(R.layout.modal_comments)

            val composeView = bottomSheetDialog.findViewById<ComposeView>(R.id.compose_view)

            composeView?.apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    // In Compose world
                    MaterialTheme {
                        val view = LocalView.current
                        IconButton(onClick = {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }) {
                            Icon(
                                Icons.Outlined.ThumbUp, contentDescription = "Close Bottom Sheet"
                            )
                        }
                    }
                }
            }
            return bottomSheetDialog
        }

        companion object {
            const val TAG = "ModalBottomSheet"
        }
    }
}
