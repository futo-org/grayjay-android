package com.futo.platformplayer

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.activities.SettingsActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.fragment.mainactivity.main.SubscriptionGroupFragment
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.parsers.HLS
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.states.StateSubscriptionGroups
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionGroupBarViewHolder
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuFilters
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuGroup
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuRecycler
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput
import com.futo.platformplayer.views.pills.RoundButton
import com.futo.platformplayer.views.pills.RoundButtonGroup
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import isDownloadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UISlideOverlays {
    companion object {
        private const val TAG = "UISlideOverlays";

        fun showOverlay(container: ViewGroup, title: String, okButton: String?, onOk: ()->Unit,  vararg views: View): SlideUpMenuOverlay {
            var menu = SlideUpMenuOverlay(container.context, container, title, okButton, true, *views);

            menu.onOK.subscribe {
                menu.hide();
                onOk.invoke();
            };
            menu.show();
            return menu;
        }

        fun showSubscriptionOptionsOverlay(subscription: Subscription, container: ViewGroup): SlideUpMenuOverlay {
            val items = arrayListOf<View>();

            val originalNotif = subscription.doNotifications;
            val originalLive = subscription.doFetchLive;
            val originalStream = subscription.doFetchStreams;
            val originalVideo = subscription.doFetchVideos;
            val originalPosts = subscription.doFetchPosts;

            val menu = SlideUpMenuOverlay(container.context, container, "Subscription Settings", null, true, listOf());

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO){
                val plugin = StatePlatform.instance.getChannelClient(subscription.channel.url);
                val capabilities = plugin.getChannelCapabilities();

                withContext(Dispatchers.Main) {
                    items.addAll(listOf(
                        SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_notifications,
                            "Notifications",
                            "",
                            tag = "notifications",
                            call = {
                                subscription.doNotifications = menu?.selectOption(null, "notifications", true, true) ?: subscription.doNotifications;
                            },
                            invokeParent = false
                        ),
                        if(StateSubscriptionGroups.instance.getSubscriptionGroups().isNotEmpty())
                            SlideUpMenuGroup(container.context, "Subscription Groups",
                                "You can select which groups this subscription is part of.",
                                -1, listOf()) else null,
                        if(StateSubscriptionGroups.instance.getSubscriptionGroups().isNotEmpty())
                            SlideUpMenuRecycler(container.context, "as") {
                                val groups = ArrayList<SubscriptionGroup>(StateSubscriptionGroups.instance.getSubscriptionGroups()
                                    .map { SubscriptionGroup.Selectable(it, it.urls.contains(subscription.channel.url)) }
                                    .sortedBy { !it.selected });
                                var adapter: AnyAdapterView<SubscriptionGroup, SubscriptionGroupBarViewHolder>? = null;
                                adapter = it.asAny(groups, RecyclerView.HORIZONTAL) {
                                    it.onClick.subscribe {
                                        if(it is SubscriptionGroup.Selectable) {
                                            val actualGroup = StateSubscriptionGroups.instance.getSubscriptionGroup(it.id)
                                                ?: return@subscribe;
                                            groups.clear();
                                            if(it.selected)
                                                actualGroup.urls.remove(subscription.channel.url);
                                            else
                                                actualGroup.urls.add(subscription.channel.url);

                                            StateSubscriptionGroups.instance.updateSubscriptionGroup(actualGroup);
                                            groups.addAll(StateSubscriptionGroups.instance.getSubscriptionGroups()
                                                .map { SubscriptionGroup.Selectable(it, it.urls.contains(subscription.channel.url)) }
                                                .sortedBy { !it.selected });
                                            adapter?.notifyContentChanged();
                                        }
                                    }
                                };
                                return@SlideUpMenuRecycler adapter;
                            } else null,
                        SlideUpMenuGroup(container.context, "Fetch Settings",
                            "Depending on the platform you might not need to enable a type for it to be available.",
                            -1, listOf()),
                        if(capabilities.hasType(ResultCapabilities.TYPE_LIVE)) SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_live_tv,
                            "Livestreams",
                            "Check for livestreams",
                            tag = "fetchLive",
                            call = {
                                subscription.doFetchLive = menu?.selectOption(null, "fetchLive", true, true) ?: subscription.doFetchLive;
                            },
                            invokeParent = false
                        ) else null,
                        if(capabilities.hasType(ResultCapabilities.TYPE_STREAMS)) SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_play,
                            "Streams",
                            "Check for streams",
                            tag = "fetchStreams",
                            call = {
                                subscription.doFetchStreams = menu?.selectOption(null, "fetchStreams", true, true) ?: subscription.doFetchStreams;
                            },
                            invokeParent = false
                        ) else null,
                        if(capabilities.hasType(ResultCapabilities.TYPE_VIDEOS))
                            SlideUpMenuItem(
                                container.context,
                                R.drawable.ic_play,
                                "Videos",
                                "Check for videos",
                                tag = "fetchVideos",
                                call = {
                                    subscription.doFetchVideos = menu?.selectOption(null, "fetchVideos", true, true) ?: subscription.doFetchVideos;
                                },
                                invokeParent = false
                            ) else if(capabilities.hasType(ResultCapabilities.TYPE_MIXED) || capabilities.types.isEmpty())
                            SlideUpMenuItem(
                                container.context,
                                R.drawable.ic_play,
                                "Content",
                                "Check for content",
                                tag = "fetchVideos",
                                call = {
                                    subscription.doFetchVideos = menu?.selectOption(null, "fetchVideos", true, true) ?: subscription.doFetchVideos;
                                },
                                invokeParent = false
                            ) else null,
                        if(capabilities.hasType(ResultCapabilities.TYPE_POSTS)) SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_chat,
                            "Posts",
                            "Check for posts",
                            tag = "fetchPosts",
                            call = {
                                subscription.doFetchPosts = menu?.selectOption(null, "fetchPosts", true, true) ?: subscription.doFetchPosts;
                            },
                            invokeParent = false
                        ) else null/*,,

                        SlideUpMenuGroup(container.context, "Actions",
                            "Various things you can do with this subscription",
                            -1, listOf())
                        SlideUpMenuItem(container.context, R.drawable.ic_list, "Add to Group", "", "btnAddToGroup", {
                            showCreateSubscriptionGroup(container, subscription.channel);
                        }, false)*/
                        ).filterNotNull());

                    menu.setItems(items);

                    if(subscription.doNotifications)
                        menu.selectOption(null, "notifications", true, true);
                    if(subscription.doFetchLive)
                        menu.selectOption(null, "fetchLive", true, true);
                    if(subscription.doFetchStreams)
                        menu.selectOption(null, "fetchStreams", true, true);
                    if(subscription.doFetchVideos)
                        menu.selectOption(null, "fetchVideos", true, true);
                    if(subscription.doFetchPosts)
                        menu.selectOption(null, "fetchPosts", true, true);

                    menu.onOK.subscribe {
                        subscription.save();
                        menu.hide(true);

                        if(subscription.doNotifications && !originalNotif) {
                            val mainContext = StateApp.instance.contextOrNull;
                            if(Settings.instance.subscriptions.subscriptionsBackgroundUpdateInterval == 0) {
                                UIDialogs.toast(container.context, "Enable 'Background Update' in settings for notifications to work");

                                if(mainContext is MainActivity) {
                                    UIDialogs.showDialog(mainContext, R.drawable.ic_settings, "Background Updating Required",
                                        "You need to set a Background Updating interval for notifications", null, 0,
                                        UIDialogs.Action("Cancel", {}),
                                        UIDialogs.Action("Configure", {
                                            val intent = Intent(mainContext, SettingsActivity::class.java);
                                            intent.putExtra("query", mainContext.getString(R.string.background_update));
                                            mainContext.startActivity(intent);
                                        }, UIDialogs.ActionStyle.PRIMARY));
                                }
                                return@subscribe;
                            }
                            else if(!(mainContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()) {
                                UIDialogs.toast(container.context, "Android notifications are disabled");
                                if(mainContext is MainActivity) {
                                    mainContext.requestNotificationPermissions("Notifications are required for subscription updating and notifications to work");
                                }
                            }
                        }
                    };
                    menu.onCancel.subscribe {
                        subscription.doNotifications = originalNotif;
                        subscription.doFetchLive = originalLive;
                        subscription.doFetchStreams = originalStream;
                        subscription.doFetchVideos = originalVideo;
                        subscription.doFetchPosts = originalPosts;
                    };

                    menu.setOk("Save");

                    menu.show();
                }
            }

            return menu;
        }

        fun showAddToGroupOverlay(channel: IPlatformVideo, container: ViewGroup) {

        }

        fun showHlsPicker(video: IPlatformVideoDetails, source: Any, sourceUrl: String, container: ViewGroup): SlideUpMenuOverlay {
            val items = arrayListOf<View>(LoaderView(container.context))
            val slideUpMenuOverlay = SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.download_video), null, true, items)

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                val masterPlaylistResponse = ManagedHttpClient().get(sourceUrl)
                check(masterPlaylistResponse.isOk) { "Failed to get master playlist: ${masterPlaylistResponse.code}" }

                val masterPlaylistContent = masterPlaylistResponse.body?.string()
                    ?: throw Exception("Master playlist content is empty")

                val videoButtons = arrayListOf<SlideUpMenuItem>()
                val audioButtons = arrayListOf<SlideUpMenuItem>()
                //TODO: Implement subtitles
                //val subtitleButtons = arrayListOf<SlideUpMenuItem>()

                var selectedVideoVariant: HLSVariantVideoUrlSource? = null
                var selectedAudioVariant: HLSVariantAudioUrlSource? = null
                //TODO: Implement subtitles
                //var selectedSubtitleVariant: HLSVariantSubtitleUrlSource? = null

                val masterPlaylist: HLS.MasterPlaylist
                try {
                    masterPlaylist = HLS.parseMasterPlaylist(masterPlaylistContent, sourceUrl)

                    masterPlaylist.getAudioSources().forEach { it ->

                        val estSize = VideoHelper.estimateSourceSize(it);
                        val prefix = if(estSize > 0) "±" + estSize.toHumanBytesSize() + " " else "";
                        audioButtons.add(SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_music,
                            it.name,
                            listOf(it.language, it.codec).mapNotNull { x -> x.ifEmpty { null } }.joinToString(", "),
                            (prefix + it.codec).trim(),
                            tag = it,
                            call = {
                                selectedAudioVariant = it
                                slideUpMenuOverlay.selectOption(audioButtons, it)
                                slideUpMenuOverlay.setOk(container.context.getString(R.string.download))
                            },
                            invokeParent = false
                        ))
                    }

                    /*masterPlaylist.getSubtitleSources().forEach { it ->
                        subtitleButtons.add(SlideUpMenuItem(container.context, R.drawable.ic_music, it.name, listOf(it.format).mapNotNull { x -> x.ifEmpty { null } }.joinToString(", "), it, {
                            selectedSubtitleVariant = it
                            slideUpMenuOverlay.selectOption(subtitleButtons, it)
                            slideUpMenuOverlay.setOk(container.context.getString(R.string.download))
                        }, false))
                    }*/

                    masterPlaylist.getVideoSources().forEach {
                        val estSize = VideoHelper.estimateSourceSize(it);
                        val prefix = if(estSize > 0) "±" + estSize.toHumanBytesSize() + " " else "";
                        videoButtons.add(SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_movie,
                            it.name,
                            "${it.width}x${it.height}",
                            (prefix + it.codec).trim(),
                            tag = it,
                            call = {
                                selectedVideoVariant = it
                                slideUpMenuOverlay.selectOption(videoButtons, it)
                                slideUpMenuOverlay.setOk(container.context.getString(R.string.download))
                            },
                            invokeParent = false
                        ))
                    }

                    val newItems = arrayListOf<View>()
                    if (videoButtons.isNotEmpty()) {
                        newItems.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.video), videoButtons, videoButtons))
                    }
                    if (audioButtons.isNotEmpty()) {
                        newItems.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.audio), audioButtons, audioButtons))
                    }
                    //TODO: Implement subtitles
                    /*if (subtitleButtons.isNotEmpty()) {
                        newItems.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.subtitles), subtitleButtons, subtitleButtons))
                    }*/

                    slideUpMenuOverlay.onOK.subscribe {
                        //TODO: Fix SubtitleRawSource issue
                        StateDownloads.instance.download(video, selectedVideoVariant, selectedAudioVariant, null);
                        slideUpMenuOverlay.hide()
                    }

                    withContext(Dispatchers.Main) {
                        slideUpMenuOverlay.setItems(newItems)
                    }
                } catch (e: Throwable) {
                    if (masterPlaylistContent.lines().any { it.startsWith("#EXTINF:") }) {
                        withContext(Dispatchers.Main) {
                            if (source is IHLSManifestSource) {
                                StateDownloads.instance.download(video, HLSVariantVideoUrlSource("variant", 0, 0, "application/vnd.apple.mpegurl", "", null, 0, false, sourceUrl), null, null)
                                UIDialogs.toast(container.context, "Variant video HLS playlist download started")
                                slideUpMenuOverlay.hide()
                            } else if (source is IHLSManifestAudioSource) {
                                StateDownloads.instance.download(video, null, HLSVariantAudioUrlSource("variant", 0, "application/vnd.apple.mpegurl", "", "", null, false, sourceUrl), null)
                                UIDialogs.toast(container.context, "Variant audio HLS playlist download started")
                                slideUpMenuOverlay.hide()
                            } else {
                                throw NotImplementedError()
                            }
                        }
                    } else {
                        throw e
                    }
                }
            }

            return slideUpMenuOverlay.apply { show() }

        }

        fun showDownloadVideoOverlay(video: IPlatformVideoDetails, container: ViewGroup, contentResolver: ContentResolver? = null): SlideUpMenuOverlay? {
            val items = arrayListOf<View>();
            var menu: SlideUpMenuOverlay? = null;

            var descriptor = video.video;
            if(video is VideoLocal)
                descriptor = video.videoSerialized.video;


            val requiresAudio = descriptor is VideoUnMuxedSourceDescriptor;
            var selectedVideo: IVideoSource? = null;
            var selectedAudio: IAudioSource? = null;
            var selectedSubtitle: ISubtitleSource? = null;

            val videoSources = descriptor.videoSources;
            val audioSources = if(descriptor is VideoUnMuxedSourceDescriptor) descriptor.audioSources else null;
            val subtitleSources = video.subtitles;

            if(videoSources.isEmpty() && (audioSources?.size ?: 0) == 0) {
                UIDialogs.toast(container.context.getString(R.string.no_downloads_available), false);
                return null;
            }

            if(!VideoHelper.isDownloadable(video)) {
                Logger.i(TAG, "Attempted to open downloads without valid sources for [${video.name}]: ${video.url}");
                UIDialogs.toast( container.context.getString(R.string.no_downloadable_sources_yet));
                return null;
            }

            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.video), videoSources,
                listOf(listOf(SlideUpMenuItem(
                    container.context,
                    R.drawable.ic_movie,
                    container.context.getString(R.string.none),
                    container.context.getString(R.string.audio_only),
                    tag = "none",
                    call = {
                        selectedVideo = null;
                        menu?.selectOption(videoSources, "none");
                        if(selectedAudio != null || !requiresAudio)
                            menu?.setOk(container.context.getString(R.string.download));
                    },
                    invokeParent = false
                )) +
                videoSources
                .filter { it.isDownloadable() }
                .map {
                    when (it) {
                        is IVideoUrlSource -> {
                            val estSize = VideoHelper.estimateSourceSize(it);
                            val prefix = if(estSize > 0) "±" + estSize.toHumanBytesSize() + " " else "";
                            SlideUpMenuItem(
                                container.context,
                                R.drawable.ic_movie,
                                it.name,
                                "${it.width}x${it.height}",
                                (prefix + it.codec).trim(),
                                tag = it,
                                call = {
                                    selectedVideo = it
                                    menu?.selectOption(videoSources, it);
                                    if(selectedAudio != null || !requiresAudio)
                                        menu?.setOk(container.context.getString(R.string.download));
                                },
                                invokeParent = false
                            )
                        }

                        is JSDashManifestRawSource -> {
                            val estSize = VideoHelper.estimateSourceSize(it);
                            val prefix = if(estSize > 0) "±" + estSize.toHumanBytesSize() + " " else "";
                            SlideUpMenuItem(
                                container.context,
                                R.drawable.ic_movie,
                                it.name,
                                "${it.width}x${it.height}",
                                (prefix + it.codec).trim(),
                                tag = it,
                                call = {
                                    selectedVideo = it
                                    menu?.selectOption(videoSources, it);
                                    if(selectedAudio != null || !requiresAudio)
                                        menu?.setOk(container.context.getString(R.string.download));
                                },
                                invokeParent = false
                            )
                        }

                        is IHLSManifestSource -> {
                            SlideUpMenuItem(
                                container.context,
                                R.drawable.ic_movie,
                                it.name,
                                "HLS",
                                tag = it,
                                call = {
                                    showHlsPicker(video, it, it.url, container)
                                },
                                invokeParent = false
                            )
                        }

                        else -> {
                            Logger.w(TAG, "Unhandled source type for UISlideOverlay download items");
                            null;//throw Exception("Unhandled source type")
                        }
                    }
                }.filterNotNull()).flatten().toList()
            ));

            if(Settings.instance.downloads.getDefaultVideoQualityPixels() > 0 && videoSources.isNotEmpty()) {
                //TODO: Add HLS support here
                selectedVideo = VideoHelper.selectBestVideoSource(
                    videoSources.filter { it is IVideoSource && it.isDownloadable() }.asIterable(),
                    Settings.instance.downloads.getDefaultVideoQualityPixels(),
                    FutoVideoPlayerBase.PREFERED_VIDEO_CONTAINERS
                ) as IVideoSource?;
            }

            if (audioSources != null) {
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.audio), audioSources, audioSources
                    .filter { VideoHelper.isDownloadable(it) }
                    .map {
                        when (it) {
                            is IAudioUrlSource -> {
                                val estSize = VideoHelper.estimateSourceSize(it);
                                val prefix = if(estSize > 0) "±" + estSize.toHumanBytesSize() + " " else "";
                                SlideUpMenuItem(
                                    container.context,
                                    R.drawable.ic_music,
                                    it.name,
                                    "${it.bitrate}",
                                    (prefix + it.codec).trim(),
                                    tag = it,
                                    call = {
                                        selectedAudio = it
                                        menu?.selectOption(audioSources, it);
                                        menu?.setOk(container.context.getString(R.string.download));
                                    },
                                    invokeParent = false
                                );
                            }

                            is JSDashManifestRawAudioSource -> {
                                val estSize = VideoHelper.estimateSourceSize(it);
                                val prefix = if(estSize > 0) "±" + estSize.toHumanBytesSize() + " " else "";
                                SlideUpMenuItem(
                                    container.context,
                                    R.drawable.ic_music,
                                    it.name,
                                    "${it.bitrate}",
                                    (prefix + it.codec).trim(),
                                    tag = it,
                                    call = {
                                        selectedAudio = it
                                        menu?.selectOption(audioSources, it);
                                        menu?.setOk(container.context.getString(R.string.download));
                                    },
                                    invokeParent = false
                                );
                            }

                            is IHLSManifestAudioSource -> {
                                SlideUpMenuItem(
                                    container.context,
                                    R.drawable.ic_movie,
                                    it.name,
                                    "HLS Audio",
                                    tag = it,
                                    call = {
                                        showHlsPicker(video, it, it.url, container)
                                    },
                                    invokeParent = false
                                )
                            }

                            else -> {
                                Logger.w(TAG, "Unhandled source type for UISlideOverlay download items");
                                null;//throw Exception("Unhandled source type")
                            }
                        }
                    }.filterNotNull()));

                //TODO: Add HLS support here
                selectedAudio = VideoHelper.selectBestAudioSource(audioSources.filter { it is IAudioSource && it.isDownloadable() }.asIterable(),
                    FutoVideoPlayerBase.PREFERED_AUDIO_CONTAINERS,
                    Settings.instance.playback.getPrimaryLanguage(container.context),
                    if(Settings.instance.downloads.isHighBitrateDefault()) 9999999 else 1) as IAudioSource?;
            }

            if(contentResolver != null && subtitleSources.isNotEmpty()) {
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.subtitles), subtitleSources, subtitleSources.map {
                        SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_edit,
                            it.name,
                            "",
                            tag = it,
                            call = {
                                if (selectedSubtitle == it) {
                                    selectedSubtitle = null;
                                    menu?.selectOption(subtitleSources, null);
                                } else {
                                    selectedSubtitle = it;
                                    menu?.selectOption(subtitleSources, it);
                                }
                            },
                            invokeParent = false
                        );
                    })
                );
            }

            menu = SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.download_video), null, true, items);

            if(selectedVideo != null) {
                menu.selectOption(videoSources, selectedVideo);
            }
            if(selectedAudio != null) {
                audioSources?.let { audioSources -> menu.selectOption(audioSources, selectedAudio); };
            }
            if(selectedAudio != null || (!requiresAudio && selectedVideo != null)) {
                menu.setOk(container.context.getString(R.string.download));
            }

            menu.onOK.subscribe {
                val sv = selectedVideo
                if (sv is IHLSManifestSource) {
                    showHlsPicker(video, sv, sv.url, container)
                    return@subscribe
                }

                val sa = selectedAudio
                if (sa is IHLSManifestAudioSource) {
                    showHlsPicker(video, sa, sa.url, container)
                    return@subscribe
                }

                menu.hide();
                val subtitleToDownload = selectedSubtitle;
                if(selectedAudio != null || !requiresAudio) {
                    if (subtitleToDownload == null) {
                        StateDownloads.instance.download(video, selectedVideo, selectedAudio, null);
                    } else {
                        //TODO: Clean this up somewhere else, maybe pre-fetch instead of dup calls
                        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                            try {
                                val subtitleUri = subtitleToDownload.getSubtitlesURI();
                                //TODO: Remove uri dependency, should be able to work with raw aswell?
                                if (subtitleUri != null && contentResolver != null) {
                                    val subtitlesRaw = StateDownloads.instance.downloadSubtitles(subtitleToDownload, contentResolver);

                                    withContext(Dispatchers.Main) {
                                        StateDownloads.instance.download(video, selectedVideo, selectedAudio, subtitlesRaw);
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        StateDownloads.instance.download(video, selectedVideo, selectedAudio, null);
                                    }
                                }
                            } catch (e: Throwable) {
                                Logger.e(TAG, "Failed download subtitles.", e);
                            }
                        }
                    }
                }
            };
            return menu.apply { show() };
        }
        fun showDownloadVideoOverlay(video: IPlatformVideo, container: ViewGroup, useDetails: Boolean = false) {
            val handleUnknownDownload: ()->Unit = {
                showUnknownVideoDownload(container.context.getString(R.string.video), container) { px, bitrate ->
                    StateDownloads.instance.download(video, px, bitrate)
                };
            };
            if(!useDetails)
                handleUnknownDownload();
            else {
                val scope = StateApp.instance.scopeOrNull;

                if(scope != null) {
                    val loader = showLoaderOverlay(container.context.getString(R.string.fetching_video_details), container);
                    scope.launch(Dispatchers.IO) {
                        try {
                            val videoDetails = StatePlatform.instance.getContentDetails(video.url, false).await();
                            if(videoDetails !is IPlatformVideoDetails)
                                throw IllegalStateException("Not a video details");

                            withContext(Dispatchers.Main) {
                                if(showDownloadVideoOverlay(videoDetails, container, StateApp.instance.contextOrNull?.contentResolver) == null)
                                    loader.hide(true);
                            }
                        }
                        catch(ex: Throwable) {
                            Logger.e(TAG, "Fetching details for download failed due to: " + ex.message, ex);
                            withContext(Dispatchers.Main) {
                                UIDialogs.toast(container.context.getString(R.string.failed_to_fetch_details_for_download) + "\n" + ex.message);
                                handleUnknownDownload();
                                loader.hide(true);
                            }
                        }
                    }
                }
                else handleUnknownDownload();
            }
        }
        fun showDownloadPlaylistOverlay(playlist: Playlist, container: ViewGroup) {
            showUnknownVideoDownload(container.context.getString(R.string.playlist), container) { px, bitrate ->
                StateDownloads.instance.download(playlist, px, bitrate);
            };
        }
        fun showDownloadWatchlaterOverlay(container: ViewGroup) {
            showUnknownVideoDownload(container.context.getString(R.string.watch_later), container, { px, bitrate ->
                StateDownloads.instance.downloadWatchLater(px, bitrate);
            })
        }
        private fun showUnknownVideoDownload(toDownload: String, container: ViewGroup, cb: (Long?, Long?)->Unit) {
            val items = arrayListOf<View>();
            var menu: SlideUpMenuOverlay? = null;

            var targetPxSize: Long = 0;
            var targetBitrate: Long = 0;

            val resolutions = listOf(
                Triple<String, String, Long>(container.context.getString(R.string.none), container.context.getString(R.string.none), -1),
                Triple<String, String, Long>("480P", "720x480", 720*480),
                Triple<String, String, Long>("720P", "1280x720", 1280*720),
                Triple<String, String, Long>("1080P", "1920x1080", 1920*1080),
                Triple<String, String, Long>("1440P", "2560x1440", 2560*1440),
                Triple<String, String, Long>("2160P", "3840x2160", 3840*2160)
            );

            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.target_resolution), "Video", resolutions.map {
                SlideUpMenuItem(
                    container.context,
                    R.drawable.ic_movie,
                    it.first,
                    it.second,
                    tag = it.third,
                    call = {
                        targetPxSize = it.third;
                        menu?.selectOption("Video", it.third);
                    },
                    invokeParent = false
                )
            }));

            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.target_bitrate), "Bitrate", listOf(
                SlideUpMenuItem(
                    container.context,
                    R.drawable.ic_movie,
                    container.context.getString(R.string.low_bitrate),
                    "",
                    tag = 1,
                    call = {
                        targetBitrate = 1;
                        menu?.selectOption("Bitrate", 1);
                        menu?.setOk(container.context.getString(R.string.download));
                    },
                    invokeParent = false
                ),
                SlideUpMenuItem(
                    container.context,
                    R.drawable.ic_movie,
                    container.context.getString(R.string.high_bitrate),
                    "",
                    tag = 9999999,
                    call = {
                        targetBitrate = 9999999;
                        menu?.selectOption("Bitrate", 9999999);
                        menu?.setOk(container.context.getString(R.string.download));
                    },
                    invokeParent = false
                )
            )));


            menu = SlideUpMenuOverlay(container.context, container, "Download " + toDownload, null, true, items);

            if(Settings.instance.downloads.getDefaultVideoQualityPixels() != 0) {
                val defTarget = Settings.instance.downloads.getDefaultVideoQualityPixels();
                if(defTarget == -1) {
                    targetPxSize = -1;
                    menu.selectOption("Video", (-1).toLong());
                }
                else {
                    targetPxSize = resolutions.drop(1).minBy { Math.abs(defTarget - it.third) }.third;
                    menu.selectOption("Video", targetPxSize);
                }
            }
            if(Settings.instance.downloads.isHighBitrateDefault()) {
                targetBitrate = 9999999;
                menu.selectOption("Bitrate", 9999999);
                menu.setOk(container.context.getString(R.string.download));
            }
            else {
                targetBitrate = 1;
                menu.selectOption("Bitrate", 1);
                menu.setOk(container.context.getString(R.string.download));
            }

            menu.onOK.subscribe {
                menu.hide();
                cb(if(targetPxSize > 0) targetPxSize else null, if(targetBitrate > 0) targetBitrate else null);
            };
            menu.show();
        }

        fun showLoaderOverlay(text: String, container: ViewGroup): SlideUpMenuOverlay {
            val dp70 = 70.dp(container.context.resources);
            val dp15 = 15.dp(container.context.resources);
            val overlay = SlideUpMenuOverlay(container.context, container, text, null, true, listOf(
                LoaderView(container.context, true, dp70).apply {
                    this.setPadding(0, dp15, 0, dp15);
                }
            ), true);
            overlay.show();
            return overlay;
        }

        fun showCreateSubscriptionGroup(container: ViewGroup, initialChannel: IPlatformChannel? = null, onCreate: ((String) -> Unit)? = null): SlideUpMenuOverlay {
            val nameInput = SlideUpMenuTextInput(container.context, container.context.getString(R.string.name));
            val addSubGroupOverlay = SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.create_new_subgroup), container.context.getString(R.string.ok), false, nameInput);

            addSubGroupOverlay.onOK.subscribe {
                val text = nameInput.text;
                if (text.isBlank()) {
                    return@subscribe;
                }

                addSubGroupOverlay.hide();
                nameInput.deactivate();
                nameInput.clear();
                if(onCreate == null)
                {
                    //TODO: Do this better, temp
                    StateApp.instance.contextOrNull?.let {
                        if(it is MainActivity) {
                            val subGroup = SubscriptionGroup(text);
                            if(initialChannel != null) {
                                subGroup.urls.add(initialChannel.url);
                                if(initialChannel.thumbnail != null)
                                    subGroup.image = ImageVariable(initialChannel.thumbnail);
                            }
                            it.navigate(it.getFragment<SubscriptionGroupFragment>(), subGroup);
                        }
                    }
                }
                else
                    onCreate(text)
            };

            addSubGroupOverlay.onCancel.subscribe {
                nameInput.deactivate();
                nameInput.clear();
            };

            addSubGroupOverlay.show();
            nameInput.activate();

            return addSubGroupOverlay
        }
        fun showCreatePlaylistOverlay(container: ViewGroup, onCreate: (String) -> Unit): SlideUpMenuOverlay {
            val nameInput = SlideUpMenuTextInput(container.context, container.context.getString(R.string.name));
            val addPlaylistOverlay = SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.create_new_playlist), container.context.getString(R.string.ok), false, nameInput);

            addPlaylistOverlay.onOK.subscribe {
                val text = nameInput.text;
                if (text.isBlank()) {
                    return@subscribe;
                }

                addPlaylistOverlay.hide();
                nameInput.deactivate();
                nameInput.clear();
                onCreate(text)
            };

            addPlaylistOverlay.onCancel.subscribe {
                nameInput.deactivate();
                nameInput.clear();
            };

            addPlaylistOverlay.show();
            nameInput.activate();

            return addPlaylistOverlay
        }

        fun showVideoOptionsOverlay(video: IPlatformVideo, container: ViewGroup, vararg actions: SlideUpMenuItem): SlideUpMenuOverlay {
            val items = arrayListOf<View>();
            val lastUpdated = StatePlaylists.instance.getLastUpdatedPlaylist();

            val isLimited = video?.url != null && StatePlatform.instance.getContentClientOrNull(video!!.url)?.let {
                if (it is JSClient)
                    return@let it.config.reduceFunctionsInLimitedVersion && BuildConfig.IS_PLAYSTORE_BUILD
                else false;
            } ?: false;

            if (lastUpdated != null) {
                items.add(
                    SlideUpMenuGroup(container.context, container.context.getString(R.string.recently_used_playlist), "recentlyusedplaylist",
                        SlideUpMenuItem(container.context,
                            R.drawable.ic_playlist_add,
                            lastUpdated.name,
                            "${lastUpdated.videos.size} " + container.context.getString(R.string.videos),
                            tag = "",
                            call = {
                                if(StatePlaylists.instance.addToPlaylist(lastUpdated.id, video))
                                    UIDialogs.appToast("Added to playlist [${lastUpdated?.name}]", false);
                                StateDownloads.instance.checkForOutdatedPlaylists();
                            }))
                );
            }

            val allPlaylists = StatePlaylists.instance.getPlaylists();
            val queue = StatePlayer.instance.getQueue();
            val watchLater = StatePlaylists.instance.getWatchLater();
            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.actions), "actions",
                (listOf(
                    if(!isLimited)
                        SlideUpMenuItem(
                            container.context,
                            R.drawable.ic_download,
                            container.context.getString(R.string.download),
                            container.context.getString(R.string.download_the_video),
                            tag = "download",
                            call = {
                                showDownloadVideoOverlay(video, container, true);
                            },
                            invokeParent = false
                        ) else null,
                    SlideUpMenuItem(
                        container.context,
                        R.drawable.ic_share,
                        container.context.getString(R.string.share),
                        "Share the video",
                        tag = "share",
                        call = {
                            val url = if(video.shareUrl.isNotEmpty()) video.shareUrl else video.url;
                            container.context.startActivity(Intent.createChooser(Intent().apply {
                                action = Intent.ACTION_SEND;
                                putExtra(Intent.EXTRA_TEXT, url);
                                type = "text/plain";
                            }, null));
                        },
                        invokeParent = false
                    ),
                    SlideUpMenuItem(
                        container.context,
                        R.drawable.ic_visibility_off,
                        container.context.getString(R.string.hide_creator_from_home),
                        "",
                        tag = "hide_creator",
                        call = {
                            StateMeta.instance.addHiddenCreator(video.author.url);
                            UIDialogs.toast(container.context, "[${video.author.name}] hidden, you may need to reload home");
                        }))
                        + actions).filterNotNull()
            ));
            items.add(
                SlideUpMenuGroup(container.context, container.context.getString(R.string.add_to), "addto",
                    SlideUpMenuItem(container.context,
                        R.drawable.ic_queue_add,
                        container.context.getString(R.string.add_to_queue),
                        "${queue.size} " + container.context.getString(R.string.videos),
                        tag = "queue",
                        call = { StatePlayer.instance.addToQueue(video); }),
                    SlideUpMenuItem(container.context,
                        R.drawable.ic_watchlist_add,
                        "${container.context.getString(R.string.add_to)} " + StatePlayer.TYPE_WATCHLATER + "",
                        "${watchLater.size} " + container.context.getString(R.string.videos),
                        tag = "watch later",
                        call = { StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video), true); }),
                    SlideUpMenuItem(container.context,
                        R.drawable.ic_history,
                        container.context.getString(R.string.add_to_history),
                        "Mark as watched",
                        tag = "history",
                        call = { StateHistory.instance.markAsWatched(video); }),
            ));

            val playlistItems = arrayListOf<SlideUpMenuItem>();
            playlistItems.add(SlideUpMenuItem(
                container.context,
                R.drawable.ic_playlist_add,
                container.context.getString(R.string.new_playlist),
                container.context.getString(R.string.add_to_new_playlist),
                tag = "add_to_new_playlist",
                call = {
                    showCreatePlaylistOverlay(container) {
                        val playlist = Playlist(it, arrayListOf(SerializedPlatformVideo.fromVideo(video)));
                        StatePlaylists.instance.createOrUpdatePlaylist(playlist);
                    };
                },
                invokeParent = false
            ))

            for (playlist in allPlaylists) {
                playlistItems.add(SlideUpMenuItem(container.context,
                    R.drawable.ic_playlist_add,
                    "${container.context.getString(R.string.add_to)} " + playlist.name + "",
                    "${playlist.videos.size} " + container.context.getString(R.string.videos),
                    tag = "",
                    call = {
                        if(StatePlaylists.instance.addToPlaylist(playlist.id, video))
                            UIDialogs.appToast("Added to playlist [${playlist.name}]", false);
                        StateDownloads.instance.checkForOutdatedPlaylists();
                    }));
            }

            if(playlistItems.size > 0)
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.playlists), "", playlistItems));

            return SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.video_options), null, true, items).apply { show() };
        }


        fun showAddToOverlay(video: IPlatformVideo, container: ViewGroup, slideUpMenuOverlayUpdated: (SlideUpMenuOverlay) -> Unit): SlideUpMenuOverlay {

            val items = arrayListOf<View>();

            val lastUpdated = StatePlaylists.instance.getLastUpdatedPlaylist();

            if (lastUpdated != null) {
                items.add(
                    SlideUpMenuGroup(container.context, container.context.getString(R.string.recently_used_playlist), "recentlyusedplaylist",
                        SlideUpMenuItem(container.context,
                            R.drawable.ic_playlist_add,
                            lastUpdated.name,
                            "${lastUpdated.videos.size} " + container.context.getString(R.string.videos),
                            tag = "",
                            call = {
                                if(StatePlaylists.instance.addToPlaylist(lastUpdated.id, video))
                                    UIDialogs.appToast("Added to playlist [${lastUpdated?.name}]", false);
                                StateDownloads.instance.checkForOutdatedPlaylists();
                            }))
                );
            }

            val allPlaylists = StatePlaylists.instance.getPlaylists().sortedByDescending { maxOf(it.datePlayed, it.dateUpdate, it.dateCreation) };
            val queue = StatePlayer.instance.getQueue();
            val watchLater = StatePlaylists.instance.getWatchLater();
            items.add(
                SlideUpMenuGroup(container.context, container.context.getString(R.string.other), "other",
                    SlideUpMenuItem(container.context,
                        R.drawable.ic_queue_add,
                        container.context.getString(R.string.queue),
                        "${queue.size} " + container.context.getString(R.string.videos),
                        tag = "queue",
                        call = { StatePlayer.instance.addToQueue(video); }),
                    SlideUpMenuItem(container.context,
                        R.drawable.ic_watchlist_add,
                        StatePlayer.TYPE_WATCHLATER,
                        "${watchLater.size} " + container.context.getString(R.string.videos),
                        tag = "watch later",
                        call = { StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video), true);
                            UIDialogs.appToast("Added to watch later", false);
                        }),
                    )
            );

            val playlistItems = arrayListOf<SlideUpMenuItem>();
            playlistItems.add(SlideUpMenuItem(
                container.context,
                R.drawable.ic_playlist_add,
                container.context.getString(R.string.new_playlist),
                container.context.getString(R.string.add_to_new_playlist),
                tag = "add_to_new_playlist",
                call = {
                    slideUpMenuOverlayUpdated(showCreatePlaylistOverlay(container) {
                        val playlist = Playlist(it, arrayListOf(SerializedPlatformVideo.fromVideo(video)));
                        StatePlaylists.instance.createOrUpdatePlaylist(playlist);
                    });
                },
                invokeParent = false
            ))

            for (playlist in allPlaylists) {
                playlistItems.add(SlideUpMenuItem(container.context,
                    R.drawable.ic_playlist_add,
                    playlist.name,
                    "${playlist.videos.size} " + container.context.getString(R.string.videos),
                    tag = "",
                    call = {
                        if(StatePlaylists.instance.addToPlaylist(playlist.id, video))
                            UIDialogs.appToast("Added to playlist [${playlist.name}]", false);
                        StateDownloads.instance.checkForOutdatedPlaylists();
                    }));
            }

            if(playlistItems.size > 0)
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.playlists), "", playlistItems));

            return SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.add_to), null, true, items).apply { show() };
        }

        fun showFiltersOverlay(lifecycleScope: CoroutineScope, container: ViewGroup, enabledClientsIds: List<String>, filterValues: HashMap<String, List<String>>, isChannelSearch: Boolean = false): SlideUpMenuFilters {
            val overlay = SlideUpMenuFilters(lifecycleScope, container, enabledClientsIds, filterValues, isChannelSearch);
            overlay.show();
            return overlay;
        }


        fun showMoreButtonOverlay(container: ViewGroup, buttonGroup: RoundButtonGroup, ignoreTags: List<Any> = listOf(), invokeParents: Boolean = true, onPinnedbuttons: ((List<RoundButton>)->Unit)? = null): SlideUpMenuOverlay {
            val visible = buttonGroup.getVisibleButtons().filter { !ignoreTags.contains(it.tagRef) };
            val hidden = buttonGroup.getInvisibleButtons().filter { !ignoreTags.contains(it.tagRef) };

            val views = arrayOf(
                hidden
                    .map { btn -> SlideUpMenuItem(
                        container.context,
                        btn.iconResource,
                        btn.text.text.toString(),
                        "",
                        tag = "",
                        call = {
                            btn.handler?.invoke(btn);
                        },
                        invokeParent = invokeParents
                    ) as View  }.toTypedArray(),
                arrayOf(SlideUpMenuItem(
                    container.context,
                    R.drawable.ic_pin,
                    container.context.getString(R.string.change_pins),
                    container.context.getString(R.string.decide_which_buttons_should_be_pinned),
                    tag = "",
                    call = {
                        showOrderOverlay(container, container.context.getString(R.string.select_your_pins_in_order),  (visible + hidden).map { Pair(it.text.text.toString(), it.tagRef!!) }) {
                            val selected = it
                                .map { x -> visible.find { it.tagRef == x } ?: hidden.find { it.tagRef == x } }
                                .filter { it != null }
                                .map { it!! }
                                .toList();

                            onPinnedbuttons?.invoke(selected + (visible + hidden).filter { !selected.contains(it) });
                        }
                    },
                    invokeParent = false
                ))
            ).flatten().toTypedArray();

            return SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.more_options), null, true, *views).apply { show() };
        }

        fun showOrderOverlay(container: ViewGroup, title: String, options: List<Pair<String, Any>>, onOrdered: (List<Any>)->Unit) {
            val selection: MutableList<Any> = mutableListOf();

            var overlay: SlideUpMenuOverlay? = null;

            overlay = SlideUpMenuOverlay(container.context, container, title, container.context.getString(R.string.save), true,
                options.map { SlideUpMenuItem(
                    container.context,
                    R.drawable.ic_move_up,
                    it.first,
                    "",
                    tag = it.second,
                    call = {
                        if(overlay!!.selectOption(null, it.second, true, true)) {
                            if(!selection.contains(it.second))
                                selection.add(it.second);
                        } else
                            selection.remove(it.second);
                    },
                    invokeParent = false
                )
                });
            overlay.onOK.subscribe {
                onOrdered.invoke(selection);
                overlay.hide();
            };

            overlay.show();
        }
    }
}