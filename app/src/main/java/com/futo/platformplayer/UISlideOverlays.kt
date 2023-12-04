package com.futo.platformplayer

import android.content.ContentResolver
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantSubtitleUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.parsers.HLS
import com.futo.platformplayer.states.*
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuGroup
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.pills.RoundButton
import com.futo.platformplayer.views.pills.RoundButtonGroup
import com.futo.platformplayer.views.overlays.slideup.*
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.notifyAll
import java.lang.IllegalStateException

class UISlideOverlays {
    companion object {
        private const val TAG = "UISlideOverlays";

        fun showOverlay(container: ViewGroup, title: String, okButton: String?, onOk: ()->Unit,  vararg views: View) {
            var menu = SlideUpMenuOverlay(container.context, container, title, okButton, true, *views);

            menu.onOK.subscribe {
                menu.hide();
                onOk.invoke();
            };
            menu.show();
        }

        fun showSubscriptionOptionsOverlay(subscription: Subscription, container: ViewGroup) {
            val items = arrayListOf<View>();

            val originalNotif = subscription.doNotifications;
            val originalLive = subscription.doFetchLive;
            val originalStream = subscription.doFetchStreams;
            val originalVideo = subscription.doFetchVideos;
            val originalPosts = subscription.doFetchPosts;

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO){
                val plugin = StatePlatform.instance.getChannelClient(subscription.channel.url);
                val capabilities = plugin.getChannelCapabilities();

                withContext(Dispatchers.Main) {

                    var menu: SlideUpMenuOverlay? = null;


                    items.addAll(listOf(
                        SlideUpMenuItem(container.context, R.drawable.ic_notifications, "Notifications", "", "notifications", {
                            subscription.doNotifications = menu?.selectOption(null, "notifications", true, true) ?: subscription.doNotifications;
                        }, false),
                        SlideUpMenuGroup(container.context, "Fetch Settings",
                            "Depending on the platform you might not need to enable a type for it to be available.",
                            -1, listOf()),
                        if(capabilities.hasType(ResultCapabilities.TYPE_LIVE)) SlideUpMenuItem(container.context, R.drawable.ic_live_tv, "Livestreams", "Check for livestreams", "fetchLive", {
                            subscription.doFetchLive = menu?.selectOption(null, "fetchLive", true, true) ?: subscription.doFetchLive;
                        }, false) else null,
                        if(capabilities.hasType(ResultCapabilities.TYPE_STREAMS)) SlideUpMenuItem(container.context, R.drawable.ic_play, "Streams", "Check for streams", "fetchStreams", {
                            subscription.doFetchStreams = menu?.selectOption(null, "fetchStreams", true, true) ?: subscription.doFetchStreams;
                        }, false) else null,
                        if(capabilities.hasType(ResultCapabilities.TYPE_VIDEOS))
                            SlideUpMenuItem(container.context, R.drawable.ic_play, "Videos", "Check for videos", "fetchVideos", {
                            subscription.doFetchVideos = menu?.selectOption(null, "fetchVideos", true, true) ?: subscription.doFetchVideos;
                        }, false) else if(capabilities.hasType(ResultCapabilities.TYPE_MIXED) || capabilities.types.isEmpty())
                            SlideUpMenuItem(container.context, R.drawable.ic_play, "Content", "Check for content", "fetchVideos", {
                                subscription.doFetchVideos = menu?.selectOption(null, "fetchVideos", true, true) ?: subscription.doFetchVideos;
                            }, false) else null,
                        if(capabilities.hasType(ResultCapabilities.TYPE_POSTS)) SlideUpMenuItem(container.context, R.drawable.ic_chat, "Posts", "Check for posts", "fetchPosts", {
                            subscription.doFetchPosts = menu?.selectOption(null, "fetchPosts", true, true) ?: subscription.doFetchPosts;
                        }, false) else null).filterNotNull());

                    menu = SlideUpMenuOverlay(container.context, container, "Subscription Settings", null, true, items);

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
                        audioButtons.add(SlideUpMenuItem(container.context, R.drawable.ic_music, it.name, listOf(it.language, it.codec).mapNotNull { x -> x.ifEmpty { null } }.joinToString(", "), it, {
                            selectedAudioVariant = it
                            slideUpMenuOverlay.selectOption(audioButtons, it)
                            slideUpMenuOverlay.setOk(container.context.getString(R.string.download))
                        }, false))
                    }

                    /*masterPlaylist.getSubtitleSources().forEach { it ->
                        subtitleButtons.add(SlideUpMenuItem(container.context, R.drawable.ic_music, it.name, listOf(it.format).mapNotNull { x -> x.ifEmpty { null } }.joinToString(", "), it, {
                            selectedSubtitleVariant = it
                            slideUpMenuOverlay.selectOption(subtitleButtons, it)
                            slideUpMenuOverlay.setOk(container.context.getString(R.string.download))
                        }, false))
                    }*/

                    masterPlaylist.getVideoSources().forEach {
                        videoButtons.add(SlideUpMenuItem(container.context, R.drawable.ic_movie, it.name, "${it.width}x${it.height}", it, {
                            selectedVideoVariant = it
                            slideUpMenuOverlay.selectOption(videoButtons, it)
                            slideUpMenuOverlay.setOk(container.context.getString(R.string.download))
                        }, false))
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
            var selectedVideo: IVideoUrlSource? = null;
            var selectedAudio: IAudioUrlSource? = null;
            var selectedSubtitle: ISubtitleSource? = null;

            val videoSources = descriptor.videoSources;
            val audioSources = if(descriptor is VideoUnMuxedSourceDescriptor) descriptor.audioSources else null;
            val subtitleSources = video.subtitles;

            if(videoSources.size == 0 && (audioSources?.size ?: 0) == 0) {
                UIDialogs.toast(container.context.getString(R.string.no_downloads_available), false);
                return null;
            }

            if(!VideoHelper.isDownloadable(video)) {
                Logger.i(TAG, "Attempted to open downloads without valid sources for [${video.name}]: ${video.url}");
                UIDialogs.toast( container.context.getString(R.string.no_downloadable_sources_yet));
                return null;
            }

            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.video), videoSources,
                listOf(listOf(SlideUpMenuItem(container.context, R.drawable.ic_movie, container.context.getString(R.string.none), container.context.getString(R.string.audio_only), "none", {
                    selectedVideo = null;
                    menu?.selectOption(videoSources, "none");
                    if(selectedAudio != null || !requiresAudio)
                        menu?.setOk(container.context.getString(R.string.download));
                }, false)) +
                videoSources
                .filter { it.isDownloadable() }
                .map {
                    if (it is IVideoUrlSource) {
                        SlideUpMenuItem(container.context, R.drawable.ic_movie, it.name, "${it.width}x${it.height}", it, {
                            selectedVideo = it
                            menu?.selectOption(videoSources, it);
                            if(selectedAudio != null || !requiresAudio)
                                menu?.setOk(container.context.getString(R.string.download));
                        }, false)
                    } else if (it is IHLSManifestSource) {
                        SlideUpMenuItem(container.context, R.drawable.ic_movie, it.name, "HLS", it, {
                            showHlsPicker(video, it, it.url, container)
                        }, false)
                    } else {
                        throw Exception("Unhandled source type")
                    }
                }).flatten().toList()
            ));

            if(Settings.instance.downloads.getDefaultVideoQualityPixels() > 0 && videoSources.size > 0) {
                //TODO: Add HLS support here
                selectedVideo = VideoHelper.selectBestVideoSource(
                    videoSources.filter { it is IVideoUrlSource && it.isDownloadable() }.asIterable(),
                    Settings.instance.downloads.getDefaultVideoQualityPixels(),
                    FutoVideoPlayerBase.PREFERED_VIDEO_CONTAINERS
                ) as IVideoUrlSource;
            }

            audioSources?.let { audioSources ->
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.audio), audioSources, audioSources
                    .filter { VideoHelper.isDownloadable(it) }
                    .map {
                        if (it is IAudioUrlSource) {
                            SlideUpMenuItem(container.context, R.drawable.ic_music, it.name, "${it.bitrate}", it, {
                                selectedAudio = it
                                menu?.selectOption(audioSources, it);
                                menu?.setOk(container.context.getString(R.string.download));
                            }, false);
                        } else if (it is IHLSManifestAudioSource) {
                            SlideUpMenuItem(container.context, R.drawable.ic_movie, it.name, "HLS Audio", it, {
                                showHlsPicker(video, it, it.url, container)
                            }, false)
                        } else {
                            throw Exception("Unhandled source type")
                        }
                    }));
                val asources = audioSources;
                val preferredAudioSource = VideoHelper.selectBestAudioSource(asources.asIterable(),
                    FutoVideoPlayerBase.PREFERED_AUDIO_CONTAINERS,
                    Settings.instance.playback.getPrimaryLanguage(container.context),
                    if(Settings.instance.downloads.isHighBitrateDefault()) 99999999 else 1);
                menu?.selectOption(asources, preferredAudioSource);

                //TODO: Add HLS support here
                selectedAudio = VideoHelper.selectBestAudioSource(audioSources.filter { it is IAudioUrlSource && it.isDownloadable() }.asIterable(),
                    FutoVideoPlayerBase.PREFERED_AUDIO_CONTAINERS,
                    Settings.instance.playback.getPrimaryLanguage(container.context),
                    if(Settings.instance.downloads.isHighBitrateDefault()) 9999999 else 1) as IAudioUrlSource?;
            }

            //ContentResolver is required for subtitles..
            if(contentResolver != null && subtitleSources.isNotEmpty()) {
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.subtitles), subtitleSources, subtitleSources
                    .map {
                        SlideUpMenuItem(container.context, R.drawable.ic_edit, it.name, "", it, {
                            if (selectedSubtitle == it) {
                                selectedSubtitle = null;
                                menu?.selectOption(subtitleSources, null);
                            } else {
                                selectedSubtitle = it;
                                menu?.selectOption(subtitleSources, it);
                            }
                        }, false);
                    }));
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
                            withContext(Dispatchers.Main) {
                                UIDialogs.toast(container.context.getString(R.string.failed_to_fetch_details_for_download));
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
            showUnknownVideoDownload(container.context.getString(R.string.video), container) { px, bitrate ->
                StateDownloads.instance.download(playlist, px, bitrate);
            };
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
                SlideUpMenuItem(container.context, R.drawable.ic_movie, it.first, it.second, it.third, {
                    targetPxSize = it.third;
                    menu?.selectOption("Video", it.third);
                }, false)
            }));

            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.target_bitrate), "Bitrate", listOf(
                SlideUpMenuItem(container.context, R.drawable.ic_movie, container.context.getString(R.string.low_bitrate), "", 1, {
                    targetBitrate = 1;
                    menu?.selectOption("Bitrate", 1);
                    menu?.setOk(container.context.getString(R.string.download));
                }, false),
                SlideUpMenuItem(container.context, R.drawable.ic_movie, container.context.getString(R.string.high_bitrate), "", 9999999, {
                    targetBitrate = 9999999;
                    menu?.selectOption("Bitrate", 9999999);
                    menu?.setOk(container.context.getString(R.string.download));
                }, false)
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

            if (lastUpdated != null) {
                items.add(
                    SlideUpMenuGroup(container.context, container.context.getString(R.string.recently_used_playlist), "recentlyusedplaylist",
                        SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, lastUpdated.name, "${lastUpdated.videos.size} " + container.context.getString(R.string.videos), "",
                            {
                                StatePlaylists.instance.addToPlaylist(lastUpdated.id, video);
                                StateDownloads.instance.checkForOutdatedPlaylists();
                            }))
                );
            }

            val allPlaylists = StatePlaylists.instance.getPlaylists();
            val queue = StatePlayer.instance.getQueue();
            val watchLater = StatePlaylists.instance.getWatchLater();
            items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.actions), "actions",
                (listOf(
                    SlideUpMenuItem(container.context, R.drawable.ic_download, container.context.getString(R.string.download), container.context.getString(R.string.download_the_video), container.context.getString(R.string.download), {
                            showDownloadVideoOverlay(video, container, true);
                        }, false),
                    SlideUpMenuItem(container.context, R.drawable.ic_visibility_off, container.context.getString(R.string.hide_creator_from_home), "", "hide_creator", {
                        StateMeta.instance.addHiddenCreator(video.author.url);
                        UIDialogs.toast(container.context, "[${video.author.name}] hidden, you may need to reload home");
                    }))
                        + actions)
            ));
            items.add(
                SlideUpMenuGroup(container.context, container.context.getString(R.string.add_to), "addto",
                    SlideUpMenuItem(container.context, R.drawable.ic_queue_add, container.context.getString(R.string.add_to_queue), "${queue.size} " + container.context.getString(R.string.videos), "queue",
                        { StatePlayer.instance.addToQueue(video); }),
                    SlideUpMenuItem(container.context, R.drawable.ic_watchlist_add, "${container.context.getString(R.string.add_to)} " + StatePlayer.TYPE_WATCHLATER + "", "${watchLater.size} " + container.context.getString(R.string.videos), "watch later",
                        { StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video)); })
            ));

            val playlistItems = arrayListOf<SlideUpMenuItem>();
            playlistItems.add(SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, container.context.getString(R.string.new_playlist), container.context.getString(R.string.add_to_new_playlist), "add_to_new_playlist", {
                showCreatePlaylistOverlay(container) {
                    val playlist = Playlist(it, arrayListOf(SerializedPlatformVideo.fromVideo(video)));
                    StatePlaylists.instance.createOrUpdatePlaylist(playlist);
                };
            }, false))

            for (playlist in allPlaylists) {
                playlistItems.add(SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, "${container.context.getString(R.string.add_to)} " + playlist.name + "", "${playlist.videos.size} " + container.context.getString(R.string.videos), "",
                    {
                        StatePlaylists.instance.addToPlaylist(playlist.id, video);
                        StateDownloads.instance.checkForOutdatedPlaylists();
                    }));
            }

            if(playlistItems.size > 0)
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.playlists), "", playlistItems));

            return SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.video_options), null, true, items).apply { show() };
        }


        fun showAddToOverlay(video: IPlatformVideo, container: ViewGroup): SlideUpMenuOverlay {

            val items = arrayListOf<View>();

            val lastUpdated = StatePlaylists.instance.getLastUpdatedPlaylist();

            if (lastUpdated != null) {
                items.add(
                    SlideUpMenuGroup(container.context, container.context.getString(R.string.recently_used_playlist), "recentlyusedplaylist",
                        SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, lastUpdated.name, "${lastUpdated.videos.size} " + container.context.getString(R.string.videos), "",
                            {
                                StatePlaylists.instance.addToPlaylist(lastUpdated.id, video);
                                StateDownloads.instance.checkForOutdatedPlaylists();
                            }))
                );
            }

            val allPlaylists = StatePlaylists.instance.getPlaylists().sortedByDescending { maxOf(it.datePlayed, it.dateUpdate, it.dateCreation) };
            val queue = StatePlayer.instance.getQueue();
            val watchLater = StatePlaylists.instance.getWatchLater();
            items.add(
                SlideUpMenuGroup(container.context, container.context.getString(R.string.other), "other",
                    SlideUpMenuItem(container.context, R.drawable.ic_queue_add, container.context.getString(R.string.queue), "${queue.size} " + container.context.getString(R.string.videos), "queue",
                        { StatePlayer.instance.addToQueue(video); }),
                    SlideUpMenuItem(container.context, R.drawable.ic_watchlist_add, StatePlayer.TYPE_WATCHLATER, "${watchLater.size} " + container.context.getString(R.string.videos), "watch later",
                        { StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video)); }),
                    SlideUpMenuItem(container.context, R.drawable.ic_download, container.context.getString(R.string.download), container.context.getString(R.string.download_the_video), container.context.getString(R.string.download),
                        { showDownloadVideoOverlay(video, container, true); }, false))
            );

            val playlistItems = arrayListOf<SlideUpMenuItem>();
            for (playlist in allPlaylists) {
                playlistItems.add(SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, playlist.name, "${playlist.videos.size} " + container.context.getString(R.string.videos), "",
                    {
                        StatePlaylists.instance.addToPlaylist(playlist.id, video);
                        StateDownloads.instance.checkForOutdatedPlaylists();
                    }));
            }

            if(playlistItems.size > 0)
                items.add(SlideUpMenuGroup(container.context, container.context.getString(R.string.playlists), "", playlistItems));

            return SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.add_to), null, true, items).apply { show() };
        }

        fun showFiltersOverlay(lifecycleScope: CoroutineScope, container: ViewGroup, enabledClientsIds: List<String>, filterValues: HashMap<String, List<String>>): SlideUpMenuFilters {
            val overlay = SlideUpMenuFilters(lifecycleScope, container, enabledClientsIds, filterValues);
            overlay.show();
            return overlay;
        }


        fun showMoreButtonOverlay(container: ViewGroup, buttonGroup: RoundButtonGroup, ignoreTags: List<Any> = listOf(), onPinnedbuttons: ((List<RoundButton>)->Unit)? = null): SlideUpMenuOverlay {
            val visible = buttonGroup.getVisibleButtons().filter { !ignoreTags.contains(it.tagRef) };
            val hidden = buttonGroup.getInvisibleButtons().filter { !ignoreTags.contains(it.tagRef) };

            val views = arrayOf(hidden
                .map { btn -> SlideUpMenuItem(container.context, btn.iconResource, btn.text.text.toString(), "", "", {
                    btn.handler?.invoke(btn);
                }, true) as View  }.toTypedArray() ?: arrayOf(),
                arrayOf(SlideUpMenuItem(container.context, R.drawable.ic_pin, container.context.getString(R.string.change_pins), container.context.getString(R.string.decide_which_buttons_should_be_pinned), "", {
                    showOrderOverlay(container, container.context.getString(R.string.select_your_pins_in_order),  (visible + hidden).map { Pair(it.text.text.toString(), it.tagRef!!) }) {
                        val selected = it
                            .map { x -> visible.find { it.tagRef == x } ?: hidden.find { it.tagRef == x } }
                            .filter { it != null }
                            .map { it!! }
                            .toList();

                        onPinnedbuttons?.invoke(selected + (visible + hidden).filter { !selected.contains(it) });
                    }
                }, false))
            ).flatten().toTypedArray();

            return SlideUpMenuOverlay(container.context, container, container.context.getString(R.string.more_options), null, true, *views).apply { show() };
        }

        fun showOrderOverlay(container: ViewGroup, title: String, options: List<Pair<String, Any>>, onOrdered: (List<Any>)->Unit) {
            val selection: MutableList<Any> = mutableListOf();

            var overlay: SlideUpMenuOverlay? = null;

            overlay = SlideUpMenuOverlay(container.context, container, title, container.context.getString(R.string.save), true,
                options.map { SlideUpMenuItem(container.context, R.drawable.ic_move_up, it.first, "", it.second, {
                        if(overlay!!.selectOption(null, it.second, true, true)) {
                            if(!selection.contains(it.second))
                                selection.add(it.second);
                        }
                        else
                            selection.remove(it.second);
                    }, false)
                });
            overlay.onOK.subscribe {
                onOrdered.invoke(selection);
                overlay.hide();
            };

            overlay.show();
        }
    }
}