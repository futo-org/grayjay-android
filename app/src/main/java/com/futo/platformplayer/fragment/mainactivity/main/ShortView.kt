package com.futo.platformplayer.fragment.mainactivity.main

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spanned
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.exceptions.ContentNotAvailableYetException
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.PlatformAuthorMembershipLink
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
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
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.dp
import com.futo.platformplayer.engine.exceptions.ScriptAgeException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.exceptions.UnsupportedCastException
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanBitrate
import com.futo.platformplayer.toHumanBytesSize
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.MonetizationView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.overlays.DescriptionOverlay
import com.futo.platformplayer.views.overlays.RepliesOverlay
import com.futo.platformplayer.views.overlays.SupportOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuButtonList
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuGroup
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTitle
import com.futo.platformplayer.views.pills.OnLikeDislikeUpdatedArgs
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.segments.CommentsList
import com.futo.platformplayer.views.video.FutoShortPlayer
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.ContentType
import com.futo.polycentric.core.Models
import com.futo.polycentric.core.Opinion
import com.futo.polycentric.core.PolycentricProfile
import com.futo.polycentric.core.fullyBackfillServersAnnounceExceptions
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import userpackage.Protocol

@UnstableApi
class ShortView : FrameLayout {
    private lateinit var mainFragment: MainFragment
    private val player: FutoShortPlayer

    private val channelInfo: LinearLayout
    private val creatorThumbnail: CreatorThumbnail
    private val channelName: TextView
    private val videoTitle: TextView

    private val likeContainer: FrameLayout
    private val dislikeContainer: FrameLayout
    private val likeButton: MaterialButton
    private val likeCount: TextView
    private val dislikeButton: MaterialButton
    private val dislikeCount: TextView

    private val commentsButton: MaterialButton
    private val shareButton: MaterialButton
    private val refreshButton: MaterialButton
    private val qualityButton: MaterialButton

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
    val onPlayingToggled = Event1<Boolean>()
    val onLikesLoaded = Event3<RatingLikeDislikes, Boolean, Boolean>()
    val onLikeDislikeUpdated = Event1<OnLikeDislikeUpdatedArgs>()
    val onVideoUpdated = Event1<IPlatformVideo?>()

    private val bottomSheet: CommentsModalBottomSheet = CommentsModalBottomSheet()

    var likes: Long = 0
        set(value) {
            field = value
            likeCount.text = value.toString()
        }

    var dislikes: Long = 0
        set(value) {
            field = value
            dislikeCount.text = value.toString()
        }

    // Required constructor for XML inflation
    constructor(context: Context) : super(context) {
        inflate(context, R.layout.view_short, this)
        player = findViewById(R.id.short_player)

        channelInfo = findViewById(R.id.channel_info)
        creatorThumbnail = findViewById(R.id.creator_thumbnail)
        channelName = findViewById(R.id.channel_name)
        videoTitle = findViewById(R.id.video_title)

        likeContainer = findViewById(R.id.like_container)
        dislikeContainer = findViewById(R.id.dislike_container)
        likeButton = findViewById(R.id.like_button)
        likeCount = findViewById(R.id.like_count)
        dislikeButton = findViewById(R.id.dislike_button)
        dislikeCount = findViewById(R.id.dislike_count)

        commentsButton = findViewById(R.id.comments_button)
        shareButton = findViewById(R.id.share_button)
        refreshButton = findViewById(R.id.refresh_button)
        qualityButton = findViewById(R.id.quality_button)

        playPauseOverlay = findViewById(R.id.play_pause_overlay)
        playPauseIcon = findViewById(R.id.play_pause_icon)

        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        init()
    }

    // Required constructor for XML inflation with attributes
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        inflate(context, R.layout.view_short, this)
        player = findViewById(R.id.short_player)

        channelInfo = findViewById(R.id.channel_info)
        creatorThumbnail = findViewById(R.id.creator_thumbnail)
        channelName = findViewById(R.id.channel_name)
        videoTitle = findViewById(R.id.video_title)

        likeContainer = findViewById(R.id.like_container)
        dislikeContainer = findViewById(R.id.dislike_container)
        likeButton = findViewById(R.id.like_button)
        likeCount = findViewById(R.id.like_count)
        dislikeButton = findViewById(R.id.dislike_button)
        dislikeCount = findViewById(R.id.dislike_count)

        commentsButton = findViewById(R.id.comments_button)
        shareButton = findViewById(R.id.share_button)
        refreshButton = findViewById(R.id.refresh_button)
        qualityButton = findViewById(R.id.quality_button)

        playPauseOverlay = findViewById(R.id.play_pause_overlay)
        playPauseIcon = findViewById(R.id.play_pause_icon)

        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        init()
    }

    // Required constructor for XML inflation with attributes and style
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        inflate(context, R.layout.view_short, this)
        player = findViewById(R.id.short_player)

        channelInfo = findViewById(R.id.channel_info)
        creatorThumbnail = findViewById(R.id.creator_thumbnail)
        channelName = findViewById(R.id.channel_name)
        videoTitle = findViewById(R.id.video_title)

        likeContainer = findViewById(R.id.like_container)
        dislikeContainer = findViewById(R.id.dislike_container)
        likeButton = findViewById(R.id.like_button)
        likeCount = findViewById(R.id.like_count)
        dislikeButton = findViewById(R.id.dislike_button)
        dislikeCount = findViewById(R.id.dislike_count)

        commentsButton = findViewById(R.id.comments_button)
        shareButton = findViewById(R.id.share_button)
        refreshButton = findViewById(R.id.refresh_button)
        qualityButton = findViewById(R.id.quality_button)

        playPauseOverlay = findViewById(R.id.play_pause_overlay)
        playPauseIcon = findViewById(R.id.play_pause_icon)

        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)

        init()
    }

    constructor(inflater: LayoutInflater, fragment: MainFragment, overlayQualityContainer: FrameLayout) : super(inflater.context) {
        inflater.inflate(R.layout.view_short, this, true)
        player = findViewById(R.id.short_player)

        channelInfo = findViewById(R.id.channel_info)
        creatorThumbnail = findViewById(R.id.creator_thumbnail)
        channelName = findViewById(R.id.channel_name)
        videoTitle = findViewById(R.id.video_title)

        likeContainer = findViewById(R.id.like_container)
        dislikeContainer = findViewById(R.id.dislike_container)
        likeButton = findViewById(R.id.like_button)
        likeCount = findViewById(R.id.like_count)
        dislikeButton = findViewById(R.id.dislike_button)
        dislikeCount = findViewById(R.id.dislike_count)

        commentsButton = findViewById(R.id.comments_button)
        shareButton = findViewById(R.id.share_button)
        refreshButton = findViewById(R.id.refresh_button)
        qualityButton = findViewById(R.id.quality_button)

        playPauseOverlay = findViewById(R.id.play_pause_overlay)
        playPauseIcon = findViewById(R.id.play_pause_icon)

        overlayLoading = findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = findViewById(R.id.short_view_loader)
        this.overlayQualityContainer = overlayQualityContainer

        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )

        this.mainFragment = fragment
        bottomSheet.mainFragment = fragment

        init()
    }

    private fun init() {
        player.setOnClickListener {
            if (player.activelyPlaying) {
                player.pause()
                onPlayingToggled.emit(false)
            } else {
                player.play()
                onPlayingToggled.emit(true)
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
            videoTitle.text = it?.name
            creatorThumbnail.setThumbnail(it?.author?.thumbnail, true)
            channelName.text = it?.author?.name
        }

        channelInfo.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            mainFragment.navigate<ChannelFragment>(video?.author)
        }

        videoTitle.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            if (!bottomSheet.isAdded) {
                bottomSheet.show(mainFragment.childFragmentManager, CommentsModalBottomSheet.TAG)
            }
        }

        commentsButton.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            if (!bottomSheet.isAdded) {
                bottomSheet.show(mainFragment.childFragmentManager, CommentsModalBottomSheet.TAG)
            }
        }

        shareButton.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            val url = video?.shareUrl ?: video?.url
            mainFragment.startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }, null))
        }

        refreshButton.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            onResetTriggered.emit()
        }

        qualityButton.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            showVideoSettings()
        }

        likeButton.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            val checked = !likeButton.isChecked
            StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_like)) {
                if (checked) {
                    likes++
                } else {
                    likes--
                }

                likeButton.isChecked = checked

                if (dislikeButton.isChecked && checked) {
                    dislikeButton.isChecked = false
                    dislikes--
                }

                onLikeDislikeUpdated.emit(
                    OnLikeDislikeUpdatedArgs(
                        it, likes, likeButton.isChecked, dislikes, dislikeButton.isChecked
                    )
                )
            }
        }

        dislikeButton.setOnClickListener {
            playSoundEffect(SoundEffectConstants.CLICK)
            val checked = !dislikeButton.isChecked
            StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_like)) {
                if (checked) {
                    dislikes++
                } else {
                    dislikes--
                }

                dislikeButton.isChecked = checked

                if (likeButton.isChecked && checked) {
                    likeButton.isChecked = false
                    likes--
                }

                onLikeDislikeUpdated.emit(
                    OnLikeDislikeUpdatedArgs(
                        it, likes, likeButton.isChecked, dislikes, dislikeButton.isChecked
                    )
                )
            }
        }

        onLikesLoaded.subscribe(tag) { rating, liked, disliked ->
            likes = rating.likes
            dislikes = rating.dislikes
            likeButton.isChecked = liked
            dislikeButton.isChecked = disliked

            dislikeContainer.visibility = VISIBLE
            likeContainer.visibility = VISIBLE
        }
    }

    private fun showPlayPauseIcon() {
        val overlay = playPauseOverlay

        overlay.alpha = 0f
        overlay.scaleX = 0f
        overlay.scaleY = 0f
        overlay.visibility = VISIBLE

        overlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

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

        player.swapSubtitles(mainFragment.lifecycleScope, toSet)

        _lastSubtitleSource = toSet
    }

    private fun showVideoSettings() {
        Logger.i(TAG, "showVideoSettings")

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
        this.mainFragment = fragment
        this.bottomSheet.mainFragment = fragment
        this.overlayQualityContainer = overlayQualityContainer
    }

    fun changeVideo(video: IPlatformVideo) {
        if (this.video?.url == video.url) {
            return
        }
        this.video = video

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
        likeContainer.visibility = GONE
        dislikeContainer.visibility = GONE

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

                    mainFragment.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            Logger.i(CommentsModalBottomSheet.Companion.TAG, "Started backfill")
                            args.processHandle.fullyBackfillServersAnnounceExceptions()
                            Logger.i(CommentsModalBottomSheet.Companion.TAG, "Finished backfill")
                        } catch (e: Throwable) {
                            Logger.e(CommentsModalBottomSheet.Companion.TAG, "Failed to backfill servers", e)
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

        loadVideoTask = TaskHandler<String, IPlatformVideoDetails>(
            StateApp.instance.scopeGetter, {
                val result = StatePlatform.instance.getContentDetails(it).await()
                if (result !is IPlatformVideoDetails) throw IllegalStateException("Expected media content, found ${result.contentType}")
                return@TaskHandler result
            }).success { result ->
            videoDetails = result
            video = result

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
            UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video_scriptimplementationexception), it, { loadVideo(url) }, null, mainFragment)
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
            UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video_scriptexception), it, { loadVideo(url) }, null, mainFragment)
        }.exception<Throwable> {
            Logger.w(ChannelFragment.TAG, "Failed to load video.", it)
            UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_video), it, { loadVideo(url) }, null, mainFragment)
        }

        loadVideoTask?.run(url)
    }

    private fun playVideo(resumePositionMs: Long = 0) {
        val videoDetails = this@ShortView.videoDetails

        if (videoDetails === null) {
            playWhenReady = true
            return
        }

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
            if (subtitleSource != null) player.swapSubtitles(mainFragment.lifecycleScope, subtitleSource)
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

    class CommentsModalBottomSheet() : BottomSheetDialogFragment() {
        var mainFragment: MainFragment? = null

        private lateinit var containerContent: FrameLayout
        private lateinit var containerContentMain: LinearLayout
        private lateinit var containerContentReplies: RepliesOverlay
        private lateinit var containerContentDescription: DescriptionOverlay
        private lateinit var containerContentSupport: SupportOverlay

        private lateinit var title: TextView
        private lateinit var subTitle: TextView
        private lateinit var channelName: TextView
        private lateinit var channelMeta: TextView
        private lateinit var creatorThumbnail: CreatorThumbnail
        private lateinit var channelButton: LinearLayout
        private lateinit var monetization: MonetizationView
        private lateinit var platform: PlatformIndicator
        private lateinit var textLikes: TextView
        private lateinit var textDislikes: TextView
        private lateinit var layoutRating: LinearLayout
        private lateinit var imageDislikeIcon: ImageView
        private lateinit var imageLikeIcon: ImageView

        private lateinit var description: TextView
        private lateinit var descriptionContainer: LinearLayout
        private lateinit var descriptionViewMore: TextView

        private lateinit var commentsList: CommentsList
        private lateinit var addCommentView: AddCommentView

        private var polycentricProfile: PolycentricProfile? = null

        private lateinit var buttonPolycentric: Button
        private lateinit var buttonPlatform: Button

        private var tabIndex: Int? = null

        private var contentOverlayView: View? = null

        lateinit var video: IPlatformVideoDetails

        private lateinit var behavior: BottomSheetBehavior<FrameLayout>

        private val _taskLoadPolycentricProfile =
            TaskHandler<PlatformID, PolycentricProfile?>(StateApp.instance.scopeGetter, { ApiMethods.getPolycentricProfileByClaim(ApiMethods.SERVER, ApiMethods.FUTO_TRUST_ROOT, it.claimFieldType.toLong(), it.claimType.toLong(), it.value!!) }).success { it -> setPolycentricProfile(it, animate = true) }
                .exception<Throwable> {
                    Logger.w(TAG, "Failed to load claims.", it)
                }

        override fun onCreateDialog(
            savedInstanceState: Bundle?,
        ): Dialog {
            val bottomSheetDialog =
                BottomSheetDialog(requireContext(), R.style.Custom_BottomSheetDialog_Theme)
            bottomSheetDialog.setContentView(R.layout.modal_comments)

            behavior = bottomSheetDialog.behavior

            // TODO figure out how to not need all of these non null assertions
            containerContent = bottomSheetDialog.findViewById(R.id.content_container)!!
            containerContentMain = bottomSheetDialog.findViewById(R.id.videodetail_container_main)!!
            containerContentReplies =
                bottomSheetDialog.findViewById(R.id.videodetail_container_replies)!!
            containerContentDescription =
                bottomSheetDialog.findViewById(R.id.videodetail_container_description)!!
            containerContentSupport =
                bottomSheetDialog.findViewById(R.id.videodetail_container_support)!!

            title = bottomSheetDialog.findViewById(R.id.videodetail_title)!!
            subTitle = bottomSheetDialog.findViewById(R.id.videodetail_meta)!!
            channelName = bottomSheetDialog.findViewById(R.id.videodetail_channel_name)!!
            channelMeta = bottomSheetDialog.findViewById(R.id.videodetail_channel_meta)!!
            creatorThumbnail = bottomSheetDialog.findViewById(R.id.creator_thumbnail)!!
            channelButton = bottomSheetDialog.findViewById(R.id.videodetail_channel_button)!!
            monetization = bottomSheetDialog.findViewById(R.id.monetization)!!
            platform = bottomSheetDialog.findViewById(R.id.videodetail_platform)!!
            layoutRating = bottomSheetDialog.findViewById(R.id.layout_rating)!!
            textDislikes = bottomSheetDialog.findViewById(R.id.text_dislikes)!!
            textLikes = bottomSheetDialog.findViewById(R.id.text_likes)!!
            imageLikeIcon = bottomSheetDialog.findViewById(R.id.image_like_icon)!!
            imageDislikeIcon = bottomSheetDialog.findViewById(R.id.image_dislike_icon)!!

            description = bottomSheetDialog.findViewById(R.id.videodetail_description)!!
            descriptionContainer =
                bottomSheetDialog.findViewById(R.id.videodetail_description_container)!!
            descriptionViewMore =
                bottomSheetDialog.findViewById(R.id.videodetail_description_view_more)!!

            addCommentView = bottomSheetDialog.findViewById(R.id.add_comment_view)!!
            commentsList = bottomSheetDialog.findViewById(R.id.comments_list)!!
            buttonPolycentric = bottomSheetDialog.findViewById(R.id.button_polycentric)!!
            buttonPlatform = bottomSheetDialog.findViewById(R.id.button_platform)!!

            commentsList.onAuthorClick.subscribe { c ->
                if (c !is PolycentricPlatformComment) {
                    return@subscribe
                }
                val id = c.author.id.value

                Logger.i(TAG, "onAuthorClick: $id")
                if (id != null && id.startsWith("polycentric://") == true) {
                    val navUrl = "https://harbor.social/" + id.substring("polycentric://".length)
                    mainFragment!!.startActivity(Intent(Intent.ACTION_VIEW, navUrl.toUri()))
                }
            }
            commentsList.onRepliesClick.subscribe { c ->
                val replyCount = c.replyCount ?: 0
                var metadata = ""
                if (replyCount > 0) {
                    metadata += "$replyCount " + requireContext().getString(R.string.replies)
                }

                if (c is PolycentricPlatformComment) {
                    var parentComment: PolycentricPlatformComment = c
                    containerContentReplies.load(tabIndex!! != 0, metadata, c.contextUrl, c.reference, c, { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) }, {
                        val newComment = parentComment.cloneWithUpdatedReplyCount(
                            (parentComment.replyCount ?: 0) + 1
                        )
                        commentsList.replaceComment(parentComment, newComment)
                        parentComment = newComment
                    })
                } else {
                    containerContentReplies.load(tabIndex!! != 0, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) })
                }
                animateOpenOverlayView(containerContentReplies)
            }

            if (StatePolycentric.instance.enabled) {
                buttonPolycentric.setOnClickListener {
                    setTabIndex(0)
                    StateMeta.instance.setLastCommentSection(0)
                }
            } else {
                buttonPolycentric.visibility = GONE
            }

            buttonPlatform.setOnClickListener {
                setTabIndex(1)
                StateMeta.instance.setLastCommentSection(1)
            }

            val ref = Models.referenceFromBuffer(video.url.toByteArray())
            addCommentView.setContext(video.url, ref)

            if (Settings.instance.comments.recommendationsDefault && !Settings.instance.comments.hideRecommendations) {
                setTabIndex(2, true)
            } else {
                when (Settings.instance.comments.defaultCommentSection) {
                    0 -> if (Settings.instance.other.polycentricEnabled) setTabIndex(0, true) else setTabIndex(1, true)
                    1 -> setTabIndex(1, true)
                    2 -> setTabIndex(StateMeta.instance.getLastCommentSection(), true)
                }
            }

            containerContentDescription.onClose.subscribe { animateCloseOverlayView() }
            containerContentReplies.onClose.subscribe { animateCloseOverlayView() }

            descriptionViewMore.setOnClickListener {
                animateOpenOverlayView(containerContentDescription)
            }

            updateDescriptionUI(video.description.fixHtmlLinks())

            val dp5 =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
            val dp2 =
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)

            //UI
            title.text = video.name
            channelName.text = video.author.name
            if (video.author.subscribers != null) {
                channelMeta.text = if ((video.author.subscribers
                        ?: 0) > 0
                ) video.author.subscribers!!.toHumanNumber() + " " + requireContext().getString(R.string.subscribers) else ""
                (channelName.layoutParams as MarginLayoutParams).setMargins(
                    0, (dp5 * -1).toInt(), 0, 0
                )
            } else {
                channelMeta.text = ""
                (channelName.layoutParams as MarginLayoutParams).setMargins(0, (dp2).toInt(), 0, 0)
            }

            video.author.let {
                if (it is PlatformAuthorMembershipLink && !it.membershipUrl.isNullOrEmpty()) monetization.setPlatformMembership(video.id.pluginId, it.membershipUrl)
                else monetization.setPlatformMembership(null, null)
            }

            val subTitleSegments: ArrayList<String> = ArrayList()
            if (video.viewCount > 0) subTitleSegments.add("${video.viewCount.toHumanNumber()} ${if (video.isLive) requireContext().getString(R.string.watching_now) else requireContext().getString(R.string.views)}")
            if (video.datetime != null) {
                val diff = video.datetime?.getNowDiffSeconds() ?: 0
                val ago = video.datetime?.toHumanNowDiffString(true)
                if (diff >= 0) subTitleSegments.add("$ago ago")
                else subTitleSegments.add("available in $ago")
            }

            platform.setPlatformFromClientID(video.id.pluginId)
            subTitle.text = subTitleSegments.joinToString(" • ")
            creatorThumbnail.setThumbnail(video.author.thumbnail, false)

            setPolycentricProfile(null, animate = false)
            _taskLoadPolycentricProfile.run(video.author.id)

            when (video.rating) {
                is RatingLikeDislikes -> {
                    val r = video.rating as RatingLikeDislikes
                    layoutRating.visibility = VISIBLE

                    textLikes.visibility = VISIBLE
                    imageLikeIcon.visibility = VISIBLE
                    textLikes.text = r.likes.toHumanNumber()

                    imageDislikeIcon.visibility = VISIBLE
                    textDislikes.visibility = VISIBLE
                    textDislikes.text = r.dislikes.toHumanNumber()
                }

                is RatingLikes -> {
                    val r = video.rating as RatingLikes
                    layoutRating.visibility = VISIBLE

                    textLikes.visibility = VISIBLE
                    imageLikeIcon.visibility = VISIBLE
                    textLikes.text = r.likes.toHumanNumber()

                    imageDislikeIcon.visibility = GONE
                    textDislikes.visibility = GONE
                }

                else -> {
                    layoutRating.visibility = GONE
                }
            }

            monetization.onSupportTap.subscribe {
                containerContentSupport.setPolycentricProfile(polycentricProfile)
                animateOpenOverlayView(containerContentSupport)
            }

            monetization.onStoreTap.subscribe {
                polycentricProfile?.systemState?.store?.let {
                    try {
                        val uri = it.toUri()
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = uri
                        requireContext().startActivity(intent)
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to open URI: '${it}'.", e)
                    }
                }
            }
            monetization.onUrlTap.subscribe {
                mainFragment!!.navigate<BrowserFragment>(it)
            }

            addCommentView.onCommentAdded.subscribe {
                commentsList.addComment(it)
            }

            channelButton.setOnClickListener {
                mainFragment!!.navigate<ChannelFragment>(video.author)
            }

            return bottomSheetDialog
        }

        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            animateCloseOverlayView()
        }

        private fun setPolycentricProfile(profile: PolycentricProfile?, animate: Boolean) {
            polycentricProfile = profile

            val dp35 = 35.dp(requireContext().resources)
            val avatar = profile?.systemState?.avatar?.selectBestImage(dp35 * dp35)
                ?.let { it.toURLInfoSystemLinkUrl(profile.system.toProto(), it.process, profile.systemState.servers.toList()) }

            if (avatar != null) {
                creatorThumbnail.setThumbnail(avatar, animate)
            } else {
                creatorThumbnail.setThumbnail(video.author.thumbnail, animate)
                creatorThumbnail.setHarborAvailable(profile != null, animate, profile?.system?.toProto())
            }

            val username = profile?.systemState?.username
            if (username != null) {
                channelName.text = username
            }

            monetization.setPolycentricProfile(profile)
        }

        private fun setTabIndex(index: Int?, forceReload: Boolean = false) {
            Logger.i(TAG, "setTabIndex (index: ${index}, forceReload: ${forceReload})")
            val changed = tabIndex != index || forceReload
            if (!changed) {
                return
            }

            tabIndex = index
            buttonPlatform.setTextColor(resources.getColor(if (index == 1) R.color.white else R.color.gray_ac, null))
            buttonPolycentric.setTextColor(resources.getColor(if (index == 0) R.color.white else R.color.gray_ac, null))

            if (index == null) {
                addCommentView.visibility = GONE
                commentsList.clear()
            } else if (index == 0) {
                addCommentView.visibility = VISIBLE
                fetchPolycentricComments()
            } else if (index == 1) {
                addCommentView.visibility = GONE
                fetchComments()
            }
        }

        private fun fetchComments() {
            Logger.i(TAG, "fetchComments")
            video.let {
                commentsList.load(true) { StatePlatform.instance.getComments(it) }
            }
        }

        private fun fetchPolycentricComments() {
            Logger.i(TAG, "fetchPolycentricComments")
            val video = video
            val idValue = video.id.value
            if (video.url.isEmpty() != false) {
                Logger.w(TAG, "Failed to fetch polycentric comments because url was null")
                commentsList.clear()
                return
            }

            val ref = Models.referenceFromBuffer(video.url.toByteArray())
            val extraBytesRef = idValue?.let { if (it.isNotEmpty()) it.toByteArray() else null }
            commentsList.load(false) { StatePolycentric.instance.getCommentPager(video.url, ref, listOfNotNull(extraBytesRef)); }
        }

        private fun updateDescriptionUI(text: Spanned) {
            containerContentDescription.load(text)
            description.text = text

            if (description.text.isNotEmpty()) descriptionContainer.visibility = VISIBLE
            else descriptionContainer.visibility = GONE
        }

        private fun animateOpenOverlayView(view: View) {
            if (contentOverlayView != null) {
                Logger.e(TAG, "Content overlay already open")
                return
            }

            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            val animHeight = containerContentMain.height

            view.translationY = animHeight.toFloat()
            view.visibility = VISIBLE

            view.animate().setDuration(300).translationY(0f).withEndAction {
                contentOverlayView = view
            }.start()
        }

        private fun animateCloseOverlayView() {
            val curView = contentOverlayView
            if (curView == null) {
                Logger.e(TAG, "No content overlay open")
                return
            }

            behavior.isDraggable = true

            val animHeight = contentOverlayView!!.height

            curView.animate().setDuration(300).translationY(animHeight.toFloat()).withEndAction {
                curView.visibility = GONE
                contentOverlayView = null
            }.start()
        }

        companion object {
            const val TAG = "ModalBottomSheet"
        }
    }
}
