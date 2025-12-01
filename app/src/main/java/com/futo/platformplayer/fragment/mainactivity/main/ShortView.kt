package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.exceptions.ContentNotAvailableYetException
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.engine.exceptions.ScriptAgeException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.exceptions.UnsupportedCastException
import com.futo.platformplayer.fragment.mainactivity.special.CommentsModalBottomSheet
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanBitrate
import com.futo.platformplayer.toHumanBytesSize
import com.futo.platformplayer.views.buttons.ShortsButton
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuButtonList
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuGroup
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTitle
import com.futo.platformplayer.views.pills.OnLikeDislikeUpdatedArgs
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.video.FutoShortPlayer
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import com.futo.platformplayer.views.video.FutoVideoPlayerBase.Companion.PREFERED_AUDIO_CONTAINERS
import com.futo.platformplayer.views.video.FutoVideoPlayerBase.Companion.PREFERED_VIDEO_CONTAINERS
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.ContentType
import com.futo.polycentric.core.Models
import com.futo.polycentric.core.Opinion
import com.futo.polycentric.core.fullyBackfillServersAnnounceExceptions
import com.google.android.material.button.MaterialButton
//import com.google.android.material.button.MaterialButton
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import userpackage.Protocol

@UnstableApi
class ShortView : FrameLayout {
    private lateinit var fragment: MainFragment
    private val player: FutoShortPlayer

    private val channelInfo: LinearLayout
    private val creatorThumbnail: CreatorThumbnail
    private val channelName: TextView
    private val videoTitle: TextView
    private val videoSubtitle: TextView
    private val platformIndicator: PlatformIndicator

    //TODO: Replace with non-material button
    private val backButton: MaterialButton
    private val backButtonContainer: ConstraintLayout

    private val likeButton: ShortsButton
    //private val likeCount: TextView
    private val dislikeButton: ShortsButton
    //private val dislikeCount: TextView

    private val commentsButton: ShortsButton
    private val shareButton: ShortsButton
    private val refreshButton: ShortsButton
    private val qualityButton: ShortsButton

    private val playPauseOverlay: FrameLayout
    private val playPauseIcon: ImageView

    private val overlayLoading: FrameLayout
    private val overlayLoadingSpinner: ImageView
    private lateinit var overlayQualityContainer: FrameLayout

    private var overlayQualitySelector: SlideUpMenuOverlay? = null

    private var video: IPlatformVideo? = null
        set(value) {
            field = value
            onVideoUpdated.emit(value)
        }
    private var videoDetails: IPlatformVideoDetails? = null

    private var playWhenReady = false

    private var _lastVideoSource: IVideoSource? = null
    private var _lastAudioSource: IAudioSource? = null
    private var _lastSubtitleSource: ISubtitleSource? = null

    private var loadVideoTask: TaskHandler<String, IPlatformVideoDetails>? = null
    private var loadLikesTask: TaskHandler<IPlatformVideo, Pair<Protocol.Reference, Protocol.QueryReferencesResponse>>? =
        null

    val onResetTriggered = Event0()
    private val onPlayingToggled = Event1<Boolean>()
    private val onLikesLoaded = Event3<RatingLikeDislikes, Boolean, Boolean>()
    private val onLikeDislikeUpdated = Event1<OnLikeDislikeUpdatedArgs>()
    private val onVideoUpdated = Event1<IPlatformVideo?>()

    //TODO: Replace with non-material UI? Only true dependency on Material left
    private val bottomSheet: CommentsModalBottomSheet = CommentsModalBottomSheet()

    var likes: Long = 0
        set(value) {
            field = value
            likeButton.withPrimaryText(value.toString());
            //likeCount.text = value.toString()
        }

    var dislikes: Long = 0
        set(value) {
            field = value
            dislikeButton.withPrimaryText(value.toString());
            //dislikeCount.text = value.toString()
        }

    constructor(inflater: LayoutInflater, fragment: MainFragment, overlayQualityContainer: FrameLayout) : this(inflater.context) {
        this.overlayQualityContainer = overlayQualityContainer

        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )

        this.fragment = fragment
        bottomSheet.mainFragment = fragment
    }

    // Required constructor for XML inflation
    constructor(context: Context) : this(context, null, null)

    // Required constructor for XML inflation with attributes
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, null)

    // Required constructor for XML inflation with attributes and style
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int? = null) : super(
        context, attrs, defStyleAttr ?: 0
    ) {
        // Inflate the layout once here
        inflate(context, R.layout.view_short, this)

        // Initialize all val properties using findViewById
        player = findViewById(R.id.short_player)
        channelInfo = findViewById(R.id.channel_info)
        creatorThumbnail = findViewById(R.id.creator_thumbnail)
        channelName = findViewById(R.id.channel_name)
        videoTitle = findViewById(R.id.video_title)
        videoSubtitle = findViewById(R.id.video_subtitle)
        platformIndicator = findViewById(R.id.short_platform_indicator)
        backButton = findViewById(R.id.back_button)
        backButtonContainer = findViewById(R.id.back_button_container)
        likeButton = findViewById(R.id.like_button)
        //likeCount = findViewById(R.id.like_count)
        dislikeButton = findViewById(R.id.dislike_button)
        //dislikeCount = findViewById(R.id.dislike_count)
        commentsButton = findViewById(R.id.comments_button)
        shareButton = findViewById(R.id.share_button)
        refreshButton = findViewById(R.id.refresh_button)
        qualityButton = findViewById(R.id.quality_button)
        playPauseOverlay = findViewById(R.id.play_pause_overlay)
        playPauseIcon = findViewById(R.id.play_pause_icon)
        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        player.setOnClickListener {
            if (player.activelyPlaying) {
                player.pause()
                onPlayingToggled.emit(false)
            } else {
                player.play()
                onPlayingToggled.emit(true)
            }
        }

        player.onPlayChanged.subscribe {
            if (it) {
                Logger.i(TAG, "Keep screen on set because isPlaying")
                fragment.activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                Logger.i(TAG, "Keep screen on cleared because not isPlaying")
                fragment.activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        onPlayingToggled.subscribe { playing ->
            if (playing) {
                playPauseIcon.setImageResource(R.drawable.ic_play)
                playPauseIcon.contentDescription = context.getString(R.string.play)
            } else {
                playPauseIcon.setImageResource(R.drawable.ic_pause)
                playPauseIcon.contentDescription = context.getString(R.string.pause)
            }
            showPlayPauseIcon()
        }

        onVideoUpdated.subscribe {
            Logger.i(TAG, "Shorts videoUpdated [${it?.name}] (isDetail: ${it is IPlatformVideoDetails}, thumbnail: ${it?.author?.thumbnail})");
            videoTitle.text = it?.name
            videoSubtitle.text = if(it is IPlatformVideoDetails) it?.description; else "";
            platformIndicator.setPlatformFromClientID(it?.id?.pluginId)
            creatorThumbnail.setThumbnail(it?.author?.thumbnail, true)
            channelName.text = it?.author?.name
        }

        backButton.setOnClickListener {
            fragment.closeSegment()
        }

        channelInfo.setOnClickListener {
            fragment.navigate<ChannelFragment>(video?.author)
        }

        videoTitle.setOnClickListener {
            if (!bottomSheet.isAdded) {
                bottomSheet.show(fragment.childFragmentManager, CommentsModalBottomSheet.TAG)
            }
        }

        commentsButton.onClick.subscribe {
            if (!bottomSheet.isAdded) {
                bottomSheet.show(fragment.childFragmentManager, CommentsModalBottomSheet.TAG)
            }
        }

        shareButton.onClick.subscribe {
            val url = video?.shareUrl ?: video?.url
            fragment.startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }, null))
        }

        refreshButton.onClick.subscribe {
            onResetTriggered.emit()
        }

        refreshButton.setOnLongClickListener {
            UIDialogs.toast(context, "Reload all platform shorts pagers")
            false
        }

        qualityButton.onClick.subscribe {
            showVideoSettings()
        }

        likeButton.onClick.subscribe {
            val checked = likeButton.iconId == R.drawable.ic_thumb_up_s // !likeButton.isChecked
            StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_like)) {
                if (checked) {
                    likes++
                } else {
                    likes--
                }

                if(checked)
                    likeButton.withIcon(R.drawable.ic_thumb_up_s_filled) //.isChecked = checked
                else
                    likeButton.withIcon(R.drawable.ic_thumb_up_s)

                if (dislikeButton.iconId == R.drawable.ic_thumb_down_s_filled && checked) {
                    //dislikeButton.isChecked = false
                    dislikeButton.withIcon(R.drawable.ic_thumb_down_s)
                    dislikes--
                }

                onLikeDislikeUpdated.emit(
                    OnLikeDislikeUpdatedArgs(
                        it, likes, checked, dislikes, !checked
                    )
                )
            }
        }

        dislikeButton.onClick.subscribe {
            val checked =  dislikeButton.iconId == R.drawable.ic_thumb_down_s //!dislikeButton.isChecked
            StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_like)) {
                if (checked) {
                    dislikes++
                } else {
                    dislikes--
                }

                //dislikeButton.isChecked = checked
                if(checked)
                    dislikeButton.withIcon(R.drawable.ic_thumb_down_s_filled) //.isChecked = checked
                else
                    dislikeButton.withIcon(R.drawable.ic_thumb_down_s)

                if (likeButton.iconId == R.drawable.ic_thumb_up_s_filled && checked) {
                    //likeButton.isChecked = false
                    likeButton.withIcon(R.drawable.ic_thumb_up_s);
                    likes--
                }

                onLikeDislikeUpdated.emit(
                    OnLikeDislikeUpdatedArgs(
                        it, likes, !checked, dislikes, checked
                    )
                )
            }
        }

        onLikesLoaded.subscribe(tag) { rating, liked, disliked ->
            likes = rating.likes
            dislikes = rating.dislikes
            //likeButton.isChecked = liked
            //dislikeButton.isChecked = disliked

            dislikeButton.visibility = VISIBLE
            likeButton.visibility = VISIBLE
        }

        player.onPlaybackStateChanged.subscribe {
            val videoSource = _lastVideoSource

            if (videoSource is IDashManifestSource || videoSource is IHLSManifestSource) {
                val videoTracks =
                    player.exoPlayer?.player?.currentTracks?.groups?.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
                val audioTracks =
                    player.exoPlayer?.player?.currentTracks?.groups?.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }

                val videoTrackFormats = mutableListOf<Format>()
                val audioTrackFormats = mutableListOf<Format>()

                if (videoTracks != null) {
                    for (i in 0 until videoTracks.mediaTrackGroup.length) videoTrackFormats.add(videoTracks.mediaTrackGroup.getFormat(i))
                }
                if (audioTracks != null) {
                    for (i in 0 until audioTracks.mediaTrackGroup.length) audioTrackFormats.add(audioTracks.mediaTrackGroup.getFormat(i))
                }

                updateQualitySourcesOverlay(videoDetails, null, videoTrackFormats.distinctBy { it.height }
                    .sortedBy { it.height }, audioTrackFormats.distinctBy { it.bitrate }
                    .sortedBy { it.bitrate })
            } else {
                updateQualitySourcesOverlay(videoDetails, null)
            }
        }
    }

    private fun showPlayPauseIcon() {
        val overlay = playPauseOverlay

        overlay.alpha = 0f
        overlay.scaleX = 0f
        overlay.scaleY = 0f
        overlay.visibility = VISIBLE

        overlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f)).start()

        overlay.postDelayed({
            hidePlayPauseIcon()
        }, 1500)
    }

    private fun hidePlayPauseIcon() {
        val overlay = playPauseOverlay

        overlay.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(300)
            .setInterpolator(AccelerateInterpolator()).withEndAction {
                overlay.visibility = GONE
            }.start()
    }

    // TODO merge this with the updateQualitySourcesOverlay for the normal video player
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun updateQualitySourcesOverlay(videoDetails: IPlatformVideoDetails?, videoLocal: VideoLocal? = null, liveStreamVideoFormats: List<Format>? = null, liveStreamAudioFormats: List<Format>? = null) {
        Logger.i(TAG, "updateQualitySourcesOverlay")

        val video: IPlatformVideoDetails?
        val localVideoSources: List<LocalVideoSource>?
        val localAudioSource: List<LocalAudioSource>?
        val localSubtitleSources: List<LocalSubtitleSource>?

        val videoSources: List<IVideoSource>?
        val audioSources: List<IAudioSource>?

        if (videoDetails is VideoLocal) {
            video = videoLocal?.videoSerialized
            localVideoSources = videoDetails.videoSource.toList()
            localAudioSource = videoDetails.audioSource.toList()
            localSubtitleSources = videoDetails.subtitlesSources.toList()
            videoSources = null
            audioSources = null
        } else {
            video = videoDetails
            videoSources = video?.video?.videoSources?.toList()
            audioSources =
                if (video?.video?.isUnMuxed == true) (video.video as VideoUnMuxedSourceDescriptor).audioSources.toList()
                else null
            if (videoLocal != null) {
                localVideoSources = videoLocal.videoSource.toList()
                localAudioSource = videoLocal.audioSource.toList()
                localSubtitleSources = videoLocal.subtitlesSources.toList()
            } else {
                localVideoSources = null
                localAudioSource = null
                localSubtitleSources = null
            }
        }

        val doDedup = Settings.instance.playback.simplifySources

        val bestVideoSources = if (doDedup) (videoSources?.map { it.height * it.width }?.distinct()
            ?.map { x -> VideoHelper.selectBestVideoSource(videoSources.filter { x == it.height * it.width }, -1, FutoVideoPlayerBase.PREFERED_VIDEO_CONTAINERS) }
            ?.plus(videoSources.filter { it is IHLSManifestSource || it is IDashManifestSource }))?.distinct()
            ?.filterNotNull()?.toList() ?: listOf() else videoSources?.toList() ?: listOf()
        val bestAudioContainer =
            audioSources?.let { VideoHelper.selectBestAudioSource(it, FutoVideoPlayerBase.PREFERED_AUDIO_CONTAINERS)?.container }
        val bestAudioSources =
            if (doDedup) audioSources?.filter { it.container == bestAudioContainer }
                ?.plus(audioSources.filter { it is IHLSManifestAudioSource || it is IDashManifestSource })
                ?.distinct()?.toList() ?: listOf() else audioSources?.toList() ?: listOf()

        val canSetSpeed = true
        val currentPlaybackRate = player.getPlaybackRate()
        overlayQualitySelector =
            SlideUpMenuOverlay(
                this.context, overlayQualityContainer, context.getString(
                    R.string.quality
                ), null, true, if (canSetSpeed) SlideUpMenuTitle(this.context).apply { setTitle(context.getString(R.string.playback_rate)) } else null, if (canSetSpeed) SlideUpMenuButtonList(this.context, null, "playback_rate").apply {
                    setButtons(listOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.25"), currentPlaybackRate.toString())
                    onClick.subscribe { v ->

                        player.setPlaybackRate(v.toFloat())
                        setSelected(v)

                    }
                } else null, if (localVideoSources?.isNotEmpty() == true) SlideUpMenuGroup(
                    this.context, context.getString(R.string.offline_video), "video", *localVideoSources.map {
                        SlideUpMenuItem(this.context, R.drawable.ic_movie, it.name, "${it.width}x${it.height}", tag = it, call = { handleSelectVideoTrack(it) })
                    }.toList().toTypedArray()
                )
                else null, if (localAudioSource?.isNotEmpty() == true) SlideUpMenuGroup(
                    this.context, context.getString(R.string.offline_audio), "audio", *localAudioSource.map {
                        SlideUpMenuItem(this.context, R.drawable.ic_music, it.name, it.bitrate.toHumanBitrate(), tag = it, call = { handleSelectAudioTrack(it) })
                    }.toList().toTypedArray()
                )
                else null, if (localSubtitleSources?.isNotEmpty() == true) SlideUpMenuGroup(
                    this.context, context.getString(R.string.offline_subtitles), "subtitles", *localSubtitleSources.map {
                        SlideUpMenuItem(this.context, R.drawable.ic_edit, it.name, "", tag = it, call = { handleSelectSubtitleTrack(it) })
                    }.toList().toTypedArray()
                )
                else null, if (liveStreamVideoFormats?.isEmpty() == false) SlideUpMenuGroup(
                    this.context, context.getString(R.string.stream_video), "video", (listOf(
                        SlideUpMenuItem(this.context, R.drawable.ic_movie, "Auto", tag = "auto", call = { player.selectVideoTrack(-1) })
                    ) + (liveStreamVideoFormats.map {
                        SlideUpMenuItem(
                            this.context, R.drawable.ic_movie, it.label ?: it.containerMimeType
                            ?: it.bitrate.toString(), "${it.width}x${it.height}", tag = it, call = { player.selectVideoTrack(it.height) })
                    }))
                )
                else null, if (liveStreamAudioFormats?.isEmpty() == false) SlideUpMenuGroup(
                    this.context, context.getString(R.string.stream_audio), "audio", *liveStreamAudioFormats.map {
                        SlideUpMenuItem(this.context, R.drawable.ic_music, "${it.label ?: it.containerMimeType} ${it.bitrate}", "", tag = it, call = { player.selectAudioTrack(it.bitrate) })
                    }.toList().toTypedArray()
                )
                else null, if (bestVideoSources.isNotEmpty()) SlideUpMenuGroup(
                    this.context, context.getString(R.string.video), "video", *bestVideoSources.map {
                        val estSize = VideoHelper.estimateSourceSize(it)
                        val prefix = if (estSize > 0) "±" + estSize.toHumanBytesSize() + " " else ""
                        SlideUpMenuItem(this.context, R.drawable.ic_movie, it.name, if (it.width > 0 && it.height > 0) "${it.width}x${it.height}" else "", (prefix + it.codec.trim()).trim(), tag = it, call = { handleSelectVideoTrack(it) })
                    }.toList().toTypedArray()
                )
                else null, if (bestAudioSources.isNotEmpty()) SlideUpMenuGroup(
                    this.context, context.getString(R.string.audio), "audio", *bestAudioSources.map {
                        val estSize = VideoHelper.estimateSourceSize(it)
                        val prefix = if (estSize > 0) "±" + estSize.toHumanBytesSize() + " " else ""
                        SlideUpMenuItem(this.context, R.drawable.ic_music, it.name, it.bitrate.toHumanBitrate(), (prefix + it.codec.trim()).trim(), tag = it, call = { handleSelectAudioTrack(it) })
                    }.toList().toTypedArray()
                )
                else null, if (video?.subtitles?.isNotEmpty() == true) SlideUpMenuGroup(
                    this.context, context.getString(R.string.subtitles), "subtitles", *video.subtitles.map {
                        SlideUpMenuItem(this.context, R.drawable.ic_edit, it.name, "", tag = it, call = { handleSelectSubtitleTrack(it) })
                    }.toList().toTypedArray()
                )
                else null
            )
    }

    private fun handleSelectVideoTrack(videoSource: IVideoSource) {
        Logger.i(TAG, "handleSelectAudioTrack(videoSource=$videoSource)")
        if (_lastVideoSource == videoSource) return

        _lastVideoSource = videoSource

        playVideo(player.position)
    }

    private fun handleSelectAudioTrack(audioSource: IAudioSource) {
        Logger.i(TAG, "handleSelectAudioTrack(audioSource=$audioSource)")
        if (_lastAudioSource == audioSource) return

        _lastAudioSource = audioSource

        playVideo(player.position)
    }

    private fun handleSelectSubtitleTrack(subtitleSource: ISubtitleSource) {
        Logger.i(TAG, "handleSelectSubtitleTrack(subtitleSource=$subtitleSource)")
        var toSet: ISubtitleSource? = subtitleSource
        if (_lastSubtitleSource == subtitleSource) toSet = null

        fragment.lifecycleScope.launch(Dispatchers.Main) {
            try {
                player.swapSubtitles(toSet)
            } catch (e: Throwable) {
                Logger.e(TAG, "handleSelectSubtitleTrack failed", e)
            }
        }

        _lastSubtitleSource = toSet
    }

    private fun showVideoSettings() {
        Logger.i(TAG, "showVideoSettings")

        overlayQualitySelector?.selectOption("video", _lastVideoSource)
        overlayQualitySelector?.selectOption("audio", _lastAudioSource)
        overlayQualitySelector?.selectOption("subtitles", _lastSubtitleSource)

        if (_lastVideoSource is IDashManifestSource || _lastVideoSource is IHLSManifestSource) {
            val videoTracks =
                player.exoPlayer?.player?.currentTracks?.groups?.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }

            var selectedQuality: Format? = null

            if (videoTracks != null) {
                for (i in 0 until videoTracks.mediaTrackGroup.length) {
                    if (videoTracks.mediaTrackGroup.getFormat(i).height == player.targetTrackVideoHeight) {
                        selectedQuality = videoTracks.mediaTrackGroup.getFormat(i)
                    }
                }
            }

            var videoMenuGroup: SlideUpMenuGroup? = null
            for (view in overlayQualitySelector!!.groupItems) {
                if (view is SlideUpMenuGroup && view.groupTag == "video") {
                    videoMenuGroup = view
                }
            }

            if (selectedQuality != null) {
                videoMenuGroup?.getItem("auto")?.setSubText("")
                overlayQualitySelector?.selectOption("video", selectedQuality)
            } else {
                videoMenuGroup?.getItem("auto")
                    ?.setSubText("${player.exoPlayer?.player?.videoFormat?.width}x${player.exoPlayer?.player?.videoFormat?.height}")
                overlayQualitySelector?.selectOption("video", "auto")
            }
        }

        val currentPlaybackRate = player.getPlaybackRate()
        overlayQualitySelector?.groupItems?.firstOrNull { it is SlideUpMenuButtonList && it.id == "playback_rate" }
            ?.let {
                (it as SlideUpMenuButtonList).setSelected(currentPlaybackRate.toString())
            }

        overlayQualitySelector?.show()
    }

    @Suppress("unused")
    fun setMainFragment(fragment: MainFragment, overlayQualityContainer: FrameLayout) {
        this.fragment = fragment
        this.bottomSheet.mainFragment = fragment
        this.overlayQualityContainer = overlayQualityContainer
    }

    fun changeVideo(video: IPlatformVideo, isChannelShortsMode: Boolean) {
        if (this.video?.url == video.url) {
            return
        }
        this.video = video

        refreshButton.visibility = if (isChannelShortsMode) {
            GONE
        } else {
            GONE //TODO: Revert?
        }
        backButtonContainer.visibility = if (isChannelShortsMode) {
            VISIBLE
        } else {
            GONE
        }

        loadVideo(video.url)
    }

    @Suppress("unused")
    fun changeVideo(videoDetails: IPlatformVideoDetails) {
        if (video?.url == videoDetails.url) {
            return
        }

        this.video = videoDetails
        this.videoDetails = videoDetails
    }

    fun play() {
        loadLikes(this.video!!)
        player.clear()
        player.attach()
        player.clear()
        playVideo()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        playWhenReady = false

        player.clear()
        player.detach()
    }

    fun cancel() {
        loadVideoTask?.cancel()
        loadLikesTask?.cancel()
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

    private fun loadLikes(video: IPlatformVideo) {
        likeButton.visibility = GONE
        dislikeButton.visibility = GONE

        loadLikesTask?.cancel()
        loadLikesTask =
            TaskHandler<IPlatformVideo, Pair<Protocol.Reference, Protocol.QueryReferencesResponse>>(
                StateApp.instance.scopeGetter, {
                    val ref = Models.referenceFromBuffer(video.url.toByteArray())
                    val extraBytesRef =
                        video.id.value?.let { if (it.isNotEmpty()) it.toByteArray() else null }

                    val queryReferencesResponse = ApiMethods.getQueryReferences(
                        ApiMethods.SERVER, ref, null, null, arrayListOf(
                            Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                                .setFromType(ContentType.OPINION.value).setValue(
                                    ByteString.copyFrom(Opinion.like.data)
                                )
                                .build(), Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                                .setFromType(ContentType.OPINION.value).setValue(
                                    ByteString.copyFrom(Opinion.dislike.data)
                                ).build()
                        ), extraByteReferences = listOfNotNull(extraBytesRef)
                    )

                    Pair(ref, queryReferencesResponse)
                }).success { (ref, queryReferencesResponse) ->
                val likes = queryReferencesResponse.countsList[0]
                val dislikes = queryReferencesResponse.countsList[1]
                val hasLiked = StatePolycentric.instance.hasLiked(ref.toByteArray())
                val hasDisliked = StatePolycentric.instance.hasDisliked(ref.toByteArray())
                onLikesLoaded.emit(RatingLikeDislikes(likes, dislikes), hasLiked, hasDisliked)
                onLikeDislikeUpdated.subscribe(this) { args ->
                    if (args.hasLiked) {
                        args.processHandle.opinion(ref, Opinion.like)
                    } else if (args.hasDisliked) {
                        args.processHandle.opinion(ref, Opinion.dislike)
                    } else {
                        args.processHandle.opinion(ref, Opinion.neutral)
                    }

                    fragment.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            Logger.i(TAG, "Started backfill")
                            args.processHandle.fullyBackfillServersAnnounceExceptions()
                            Logger.i(TAG, "Finished backfill")
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to backfill servers", e)
                        }
                    }

                    StatePolycentric.instance.updateLikeMap(
                        ref, args.hasLiked, args.hasDisliked
                    )
                }
            }

        loadLikesTask?.run(video)
    }

    private fun loadVideo(url: String) {
        loadVideoTask?.cancel()
        videoDetails = null
        _lastVideoSource = null
        _lastAudioSource = null
        _lastSubtitleSource = null

        setLoading(true)

        Logger.i(TAG, "Shorts loadVideo [${url}]");
        val timeLoadVideoStart = System.currentTimeMillis();
        loadVideoTask = TaskHandler<String, IPlatformVideoDetails>(
            StateApp.instance.scopeGetter, {
                val result = StatePlatform.instance.getContentDetails(it).await()
                if (result !is IPlatformVideoDetails) throw IllegalStateException("Expected media content, found ${result.contentType}")
                return@TaskHandler result
            }).success { result ->
                val timeLoadVideo = System.currentTimeMillis() - timeLoadVideoStart;
                Logger.i(TAG, "Shorts loadVideo [${url}] took ${timeLoadVideo}ms");
                videoDetails = result
                video = result

                if(Settings.instance.playback.shortsPregenerate)
                    fragment.lifecycleScope.launch(Dispatchers.IO) {
                        if(result != null) {
                            val prefVid = VideoHelper.selectBestVideoSource(result.video, Settings.instance.playback.getCurrentPreferredQualityPixelCount(), PREFERED_VIDEO_CONTAINERS);
                            val prefAud = VideoHelper.selectBestAudioSource(result.video, PREFERED_AUDIO_CONTAINERS, Settings.instance.playback.getPrimaryLanguage(context));

                            if(prefVid != null && prefVid is JSDashManifestRawSource) {
                                Logger.i(TAG, "Shorts pregenerating video (${result.name})");
                                prefVid.pregenerateAsync(fragment.lifecycleScope);
                            }
                            if(prefAud != null && prefAud is JSDashManifestRawAudioSource) {
                                Logger.i(TAG, "Shorts pregenerating audio (${result.name})");
                                prefAud.pregenerateAsync(fragment.lifecycleScope);
                            }
                        }
                    }

                bottomSheet.video = result

                setLoading(false)

                if (playWhenReady) playVideo()
        }.exception<NoPlatformClientException> {
            Logger.w(TAG, "exception<NoPlatformClientException>", it)
            UIDialogs.showDialog(
                context, R.drawable.ic_sources, "No source enabled to support this video\n(${url})", null, null, 0, UIDialogs.Action("Close", { }, UIDialogs.ActionStyle.PRIMARY)
            )
        }.exception<ScriptLoginRequiredException> { e ->
            Logger.w(TAG, "exception<ScriptLoginRequiredException>", e)
            UIDialogs.showDialog(
                context, R.drawable.ic_security, "Authentication", e.message, null, 0, UIDialogs.Action("Cancel", {}), UIDialogs.Action("Login", {
                val id = e.config.let { if (it is SourcePluginConfig) it.id else null }
                val didLogin =
                    if (id == null) false else StatePlugins.instance.loginPlugin(context, id) {
                        loadVideo(url)
                    }
                if (!didLogin) UIDialogs.showDialogOk(context, R.drawable.ic_error_pred, "Failed to login")
            }, UIDialogs.ActionStyle.PRIMARY)
            )
        }.exception<ContentNotAvailableYetException> {
            Logger.w(TAG, "exception<ContentNotAvailableYetException>", it)
            UIDialogs.showSingleButtonDialog(context, R.drawable.ic_schedule, "Video is available in ${it.availableWhen}.", "Close") { }
        }.exception<ScriptImplementationException> {
            Logger.w(TAG, "exception<ScriptImplementationException>", it)
            UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video_scriptimplementationexception), it, { loadVideo(url) }, null, fragment)
        }.exception<ScriptAgeException> {
            Logger.w(TAG, "exception<ScriptAgeException>", it)
            UIDialogs.showDialog(
                context, R.drawable.ic_lock, "Age restricted video", it.message, null, 0, UIDialogs.Action("Close", { }, UIDialogs.ActionStyle.PRIMARY)
            )
        }.exception<ScriptUnavailableException> {
            Logger.w(TAG, "exception<ScriptUnavailableException>", it)
            UIDialogs.showDialog(
                context, R.drawable.ic_lock, context.getString(R.string.unavailable_video), context.getString(R.string.this_video_is_unavailable), null, 0, UIDialogs.Action(context.getString(R.string.close), { }, UIDialogs.ActionStyle.PRIMARY)
            )
        }.exception<ScriptException> {
            Logger.w(TAG, "exception<ScriptException>", it)
            UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video_scriptexception), it, { loadVideo(url) }, null, fragment)
        }.exception<Throwable> {
            Logger.w(ChannelFragment.TAG, "Failed to load video.", it)
            UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video), it, { loadVideo(url) }, null, fragment)
        }

        loadVideoTask?.run(url)
    }

    private fun playVideo(resumePositionMs: Long = 0) {
        val videoDetails = this@ShortView.videoDetails

        if (videoDetails === null) {
            playWhenReady = true
            return
        }

        updateQualitySourcesOverlay(videoDetails, null)

        try {
            val videoSource = _lastVideoSource
                ?: player.getPreferredVideoSource(videoDetails, Settings.instance.playback.getCurrentPreferredQualityPixelCount())
            val audioSource = _lastAudioSource
                ?: player.getPreferredAudioSource(videoDetails, Settings.instance.playback.getPrimaryLanguage(context))
            val subtitleSource = _lastSubtitleSource
                ?: (if (videoDetails is VideoLocal) videoDetails.subtitlesSources.firstOrNull() else null)
            Logger.i(TAG, "loadCurrentVideo(videoSource=$videoSource, audioSource=$audioSource, subtitleSource=$subtitleSource, resumePositionMs=$resumePositionMs)")

            if (videoSource == null && audioSource == null) {
                UIDialogs.showDialog(
                    context, R.drawable.ic_lock, context.getString(R.string.unavailable_video), context.getString(R.string.this_video_is_unavailable), null, 0, UIDialogs.Action(context.getString(R.string.close), { }, UIDialogs.ActionStyle.PRIMARY)
                )
                StatePlatform.instance.clearContentDetailCache(videoDetails.url)
                return
            }

            val thumbnail = videoDetails.thumbnails.getHQThumbnail()
            if (videoSource == null && !thumbnail.isNullOrBlank()) Glide.with(context).asBitmap()
                .load(thumbnail).downsample(DownsampleStrategy.AT_MOST).override(1080, 1080).into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        player.setArtwork(resource.toDrawable(resources))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        player.setArtwork(null)
                    }
                })
            else player.setArtwork(null)

            fragment.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    player.setSource(videoSource, audioSource, play = true, keepSubtitles = false, resume = resumePositionMs > 0)
                    if (subtitleSource != null) player.swapSubtitles(subtitleSource)
                    player.seekTo(resumePositionMs)
                } catch (e: Throwable) {
                    Logger.e(TAG, "playVideo failed", e)
                }
            }

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

}
