package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnail
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.views.pills.WidePillButton
import java.time.OffsetDateTime

class TutorialFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: TutorialView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        (topBar as NavigationTopBarFragment?)?.onShown(getString(R.string.tutorials));
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = TutorialView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class TutorialView(fragment: TutorialFragment, inflater: LayoutInflater) :
        ScrollView(inflater.context) {
        init {
            addView(TutorialContainer(fragment, inflater))
        }
    }

    @SuppressLint("ViewConstructor")
    class TutorialContainer : LinearLayout {
        val fragment: TutorialFragment

        constructor(fragment: TutorialFragment, inflater: LayoutInflater) : super(inflater.context) {
            this.fragment = fragment

            orientation = VERTICAL

            addView(createHeader("Initial setup"))
            initialSetupVideos.forEach {
                addView(createTutorialPill(R.drawable.ic_movie, it.name, it.description).apply {
                    onClick.subscribe {
                        fragment.navigate<VideoDetailFragment>(it)
                    }
                })
            }

            addView(createHeader("Features"))
            featuresVideos.forEach {
                addView(createTutorialPill(R.drawable.ic_movie, it.name, it.description).apply {
                    onClick.subscribe {
                        fragment.navigate<VideoDetailFragment>(it)
                    }
                })
            }
        }

        private fun createHeader(t: String): TextView {
            return TextView(context).apply {
                textSize = 24.0f
                typeface = resources.getFont(R.font.inter_regular)
                text = t
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(15.dp(resources), 10.dp(resources), 15.dp(resources), 12.dp(resources))
                }
            }
        }

        private fun createTutorialPill(iconPrefix: Int, t: String, d: String): WidePillButton {
            return WidePillButton(context).apply {
                setIconPrefix(iconPrefix)
                setText(t)
                setDescription(d)
                setIconSuffix(R.drawable.ic_play_notif)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(15.dp(resources), 0, 15.dp(resources), 12.dp(resources))
                }
            }
        }
    }

    class TutorialVideoSourceDescriptor(url: String, duration: Long, width: Int, height: Int) : VideoUnMuxedSourceDescriptor() {
        override val videoSources: Array<IVideoSource> = arrayOf(
            VideoUrlSource("Original", url, width, height, duration, "video/mp4")
        )
        override val audioSources: Array<IAudioSource> = arrayOf()
    }

    class TutorialVideo(
        uuid: String,
        override val name: String,
        override val description: String,
        thumbnailUrl: String,
        videoUrl: String,
        override val duration: Long,
        width: Int = 1920,
        height: Int = 1080
    ) : IPlatformVideoDetails {
        override val id: PlatformID = PlatformID("tutorial", uuid)
        override val contentType: ContentType = ContentType.MEDIA
        override val preview: IVideoSourceDescriptor? = null
        override val live: IVideoSource? = null
        override val dash: IDashManifestSource? = null
        override val hls: IHLSManifestSource? = null
        override val subtitles: List<ISubtitleSource> = emptyList()
        override val shareUrl: String = videoUrl
        override val url: String = videoUrl
        override val datetime: OffsetDateTime? = OffsetDateTime.parse("2023-12-18T00:00:00Z")
        override val thumbnails: Thumbnails = Thumbnails(arrayOf(Thumbnail(thumbnailUrl)))
        override val author: PlatformAuthorLink = PlatformAuthorLink(PlatformID("tutorial", "f422ced6-b551-4b62-818e-27a4f5f4918a"), "Grayjay", "", "https://releases.grayjay.app/tutorials/author.jpeg")
        override val isLive: Boolean = false
        override val rating: IRating = RatingLikes(-1)
        override val viewCount: Long = -1
        override val video: IVideoSourceDescriptor = TutorialVideoSourceDescriptor(videoUrl, duration, width, height)
        override val isShort: Boolean = false;
        override fun getComments(client: IPlatformClient): IPager<IPlatformComment> {
            return EmptyPager()
        }
        override fun getPlaybackTracker(): IPlaybackTracker? = null;
        override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? = null;
    }

    companion object {
        const val TAG = "HomeFragment";

        fun newInstance() = TutorialFragment().apply {}
        val initialSetupVideos = listOf(
            TutorialVideo(
                uuid = "228be579-ec52-4d93-b9eb-ca74ec08c58a",
                name = "How to install",
                description = "Learn how to install Grayjay.",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/how-to-install.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/how-to-install.mp4",
                duration = 52
            ),
            TutorialVideo(
                uuid = "3b99ebfe-2640-4643-bfe0-a0cf04261fc5",
                name = "Getting started",
                description = "Learn how to get started with Grayjay. How do you install plugins?",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/getting-started.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/getting-started.mp4",
                duration = 50
            ),
            TutorialVideo(
                uuid = "793aa009-516c-4581-b82f-a8efdfef4c27",
                name = "Is Grayjay free?",
                description = "Learn how Grayjay is monetized. How do we make money?",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/pay.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/pay.mp4",
                duration = 52
            )
        )

        val featuresVideos = listOf(
            TutorialVideo(
                uuid = "d2238d88-4252-4a91-a12d-b90c049bb7cf",
                name = "Searching",
                description = "Learn about searching in Grayjay. How can I find channels, videos or playlists?",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/search.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/search.mp4",
                duration = 39
            ),
            TutorialVideo(
                uuid = "d2238d88-4252-4a91-a12d-b90c049bb7cf",
                name = "Comments",
                description = "Learn about Polycentric comments in Grayjay.",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/polycentric.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/polycentric.mp4",
                duration = 64
            ),
            TutorialVideo(
                uuid = "94d36959-e3fc-4c24-a988-89147067a179",
                name = "Casting",
                description = "Learn about casting in Grayjay. How do I show video on my TV?\nhttps://fcast.org/",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/how-to-cast.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/how-to-cast.mp4",
                duration = 79
            ),
            TutorialVideo(
                uuid = "5128c2e3-852b-4281-869b-efea2ec82a0e",
                name = "Monetization",
                description = "How can I monetize as a creator?",
                thumbnailUrl = "https://releases.grayjay.app/tutorials/monetization.jpg",
                videoUrl = "https://releases.grayjay.app/tutorials/monetization.mp4",
                duration = 47,
                1080,
                1920
            )
        )
    }
}