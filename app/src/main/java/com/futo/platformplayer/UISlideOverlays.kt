package com.futo.platformplayer

import android.content.ContentResolver
import android.view.View
import android.view.ViewGroup
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.*
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuGroup
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.pills.RoundButton
import com.futo.platformplayer.views.pills.RoundButtonGroup
import com.futo.platformplayer.views.overlays.slideup.*
import com.futo.platformplayer.views.video.FutoVideoPlayerBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        fun showDownloadVideoOverlay(contentResolver: ContentResolver, video: IPlatformVideoDetails, container: ViewGroup): SlideUpMenuOverlay? {
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
                UIDialogs.toast("No downloads available", false);
                return null;
            }

            if(!VideoHelper.isDownloadable(video)) {
                Logger.i(TAG, "Attempted to open downloads without valid sources for [${video.name}]: ${video.url}");
                UIDialogs.toast( "No downloadable sources (yet)");
                return null;
            }

            items.add(SlideUpMenuGroup(container.context, "Video", videoSources,
                listOf(listOf(SlideUpMenuItem(container.context, R.drawable.ic_movie, "None", "Audio Only", "none", {
                    selectedVideo = null;
                    menu?.selectOption(videoSources, "none");
                    if(selectedAudio != null || !requiresAudio)
                        menu?.setOk("Download");
                }, false)) +
                videoSources
                .filter { it.isDownloadable() }
                .map {
                    SlideUpMenuItem(container.context, R.drawable.ic_movie, it.name, "${it.width}x${it.height}", it, {
                        selectedVideo = it as IVideoUrlSource;
                        menu?.selectOption(videoSources, it);
                        if(selectedAudio != null || !requiresAudio)
                            menu?.setOk("Download");
                    }, false)
                }).flatten().toList()
            ));

            if(Settings.instance.downloads.getDefaultVideoQualityPixels() > 0 && videoSources.size > 0)
                selectedVideo = VideoHelper.selectBestVideoSource(videoSources.filter { it.isDownloadable() }.asIterable(),
                    Settings.instance.downloads.getDefaultVideoQualityPixels(),
                    FutoVideoPlayerBase.PREFERED_VIDEO_CONTAINERS) as IVideoUrlSource;


            audioSources?.let { audioSources ->
                items.add(SlideUpMenuGroup(container.context, "Audio", audioSources, audioSources
                    .filter { VideoHelper.isDownloadable(it) }
                    .map {
                        SlideUpMenuItem(container.context, R.drawable.ic_music, it.name, "${it.bitrate}", it, {
                            selectedAudio = it as IAudioUrlSource;
                            menu?.selectOption(audioSources, it);
                            menu?.setOk("Download");
                        }, false);
                    }));
                val asources = audioSources;
                val preferredAudioSource = VideoHelper.selectBestAudioSource(asources.asIterable(),
                    FutoVideoPlayerBase.PREFERED_AUDIO_CONTAINERS,
                    Settings.instance.playback.getPrimaryLanguage(container.context),
                    if(Settings.instance.downloads.isHighBitrateDefault()) 99999999 else 1);
                menu?.selectOption(asources, preferredAudioSource);


                selectedAudio = VideoHelper.selectBestAudioSource(audioSources.filter { it.isDownloadable() }.asIterable(),
                    FutoVideoPlayerBase.PREFERED_AUDIO_CONTAINERS,
                    Settings.instance.playback.getPrimaryLanguage(container.context),
                    if(Settings.instance.downloads.isHighBitrateDefault()) 9999999 else 1) as IAudioUrlSource?;
            }

            items.add(SlideUpMenuGroup(container.context, "Subtitles", subtitleSources, subtitleSources
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

            menu = SlideUpMenuOverlay(container.context, container, "Download Video", null, true, items);

            if(selectedVideo != null) {
                menu.selectOption(videoSources, selectedVideo);
            }
            if(selectedAudio != null) {
                audioSources?.let { audioSources -> menu.selectOption(audioSources, selectedAudio); };
            }
            if(selectedAudio != null || (!requiresAudio && selectedVideo != null)) {
                menu.setOk("Download");
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
                                if (subtitleUri != null) {
                                    var subtitles: String? = null;
                                    if ("file" == subtitleUri.scheme) {
                                        val inputStream = contentResolver.openInputStream(subtitleUri);
                                        inputStream?.use { stream ->
                                            val reader = stream.bufferedReader();
                                            subtitles = reader.use { it.readText() };
                                        }
                                    } else if ("http" == subtitleUri.scheme || "https" == subtitleUri.scheme) {
                                        val client = ManagedHttpClient();
                                        val subtitleResponse = client.get(subtitleUri.toString());
                                        if (!subtitleResponse.isOk) {
                                            throw Exception("Cannot fetch subtitles from source '${subtitleUri}': ${subtitleResponse.code}");
                                        }

                                        subtitles = subtitleResponse.body?.toString()
                                            ?: throw Exception("Subtitles are invalid '${subtitleUri}': ${subtitleResponse.code}");
                                    } else {
                                        throw Exception("Unsuported scheme");
                                    }

                                    withContext(Dispatchers.Main) {
                                        StateDownloads.instance.download(video, selectedVideo, selectedAudio, if (subtitles != null) SubtitleRawSource(subtitleToDownload.name, subtitleToDownload.format, subtitles!!) else null);
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
        fun showDownloadVideoOverlay(video: IPlatformVideo, container: ViewGroup) {
            showUnknownVideoDownload("Video", container) { px, bitrate ->
                StateDownloads.instance.download(video, px, bitrate)
            };
        }
        fun showDownloadPlaylistOverlay(playlist: Playlist, container: ViewGroup) {
            showUnknownVideoDownload("Video", container) { px, bitrate ->
                StateDownloads.instance.download(playlist, px, bitrate);
            };
        }
        private fun showUnknownVideoDownload(toDownload: String, container: ViewGroup, cb: (Long?, Long?)->Unit) {
            val items = arrayListOf<View>();
            var menu: SlideUpMenuOverlay? = null;

            var targetPxSize: Long = 0;
            var targetBitrate: Long = 0;

            val resolutions = listOf(
                Triple<String, String, Long>("None", "None", -1),
                Triple<String, String, Long>("480P", "720x480", 720*480),
                Triple<String, String, Long>("720P", "1280x720", 1280*720),
                Triple<String, String, Long>("1080P", "1920x1080", 1920*1080),
                Triple<String, String, Long>("1440P", "2560x1440", 2560*1440),
                Triple<String, String, Long>("2160P", "3840x2160", 3840*2160)
            );

            items.add(SlideUpMenuGroup(container.context, "Target Resolution", "Video", resolutions.map {
                SlideUpMenuItem(container.context, R.drawable.ic_movie, it.first, it.second, it.third, {
                    targetPxSize = it.third;
                    menu?.selectOption("Video", it.third);
                }, false)
            }));

            items.add(SlideUpMenuGroup(container.context, "Target Bitrate", "Bitrate", listOf(
                SlideUpMenuItem(container.context, R.drawable.ic_movie, "Low Bitrate", "", 1, {
                    targetBitrate = 1;
                    menu?.selectOption("Bitrate", 1);
                    menu?.setOk("Download");
                }, false),
                SlideUpMenuItem(container.context, R.drawable.ic_movie, "High Bitrate", "", 9999999, {
                    targetBitrate = 9999999;
                    menu?.selectOption("Bitrate", 9999999);
                    menu?.setOk("Download");
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
                menu.setOk("Download");
            }
            else {
                targetBitrate = 1;
                menu.selectOption("Bitrate", 1);
                menu.setOk("Download");
            }

            menu.onOK.subscribe {
                menu.hide();
                cb(if(targetPxSize > 0) targetPxSize else null, if(targetBitrate > 0) targetBitrate else null);
            };
            menu.show();
        }

        fun showVideoOptionsOverlay(video: IPlatformVideo, container: ViewGroup, onVideoHidden: (()->Unit)? = null): SlideUpMenuOverlay {
            val items = arrayListOf<View>();
            val lastUpdated = StatePlaylists.instance.getLastUpdatedPlaylist();

            if (lastUpdated != null) {
                items.add(
                    SlideUpMenuGroup(container.context, "Recently Used Playlist", "recentlyusedplaylist",
                        SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, lastUpdated.name, "${lastUpdated.videos.size} videos", "",
                            {
                                StatePlaylists.instance.addToPlaylist(lastUpdated.id, video);
                                StateDownloads.instance.checkForOutdatedPlaylists();
                            }))
                );
            }

            val allPlaylists = StatePlaylists.instance.getPlaylists();
            val queue = StatePlayer.instance.getQueue();
            val watchLater = StatePlaylists.instance.getWatchLater();
            items.add(SlideUpMenuGroup(container.context, "Actions", "actions",
                SlideUpMenuItem(container.context, R.drawable.ic_visibility_off, "Hide", "Hide from Home", "hide",
                    { StateMeta.instance.addHiddenVideo(video.url); onVideoHidden?.invoke() }),
                SlideUpMenuItem(container.context, R.drawable.ic_download, "Download", "Download the video", "download",
                    { showDownloadVideoOverlay(video, container); }, false)
            ))
            items.add(
                SlideUpMenuGroup(container.context, "Add To", "addto",
                    SlideUpMenuItem(container.context, R.drawable.ic_queue_add, "Add to Queue", "${queue.size} videos", "queue",
                        { StatePlayer.instance.addToQueue(video); }),
                    SlideUpMenuItem(container.context, R.drawable.ic_watchlist_add, "Add to " + StatePlayer.TYPE_WATCHLATER + "", "${watchLater.size} videos", "watch later",
                        { StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video)); })
            ));

            val playlistItems = arrayListOf<SlideUpMenuItem>();
            for (playlist in allPlaylists) {
                playlistItems.add(SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, "Add to " + playlist.name + "", "${playlist.videos.size} videos", "",
                    {
                        StatePlaylists.instance.addToPlaylist(playlist.id, video);
                        StateDownloads.instance.checkForOutdatedPlaylists();
                    }));
            }

            if(playlistItems.size > 0)
                items.add(SlideUpMenuGroup(container.context, "Playlists", "", playlistItems));

            return SlideUpMenuOverlay(container.context, container, "Video Options", null, true, items).apply { show() };
        }


        fun showAddToOverlay(video: IPlatformVideo, container: ViewGroup): SlideUpMenuOverlay {

            val items = arrayListOf<View>();

            val lastUpdated = StatePlaylists.instance.getLastUpdatedPlaylist();

            if (lastUpdated != null) {
                items.add(
                    SlideUpMenuGroup(container.context, "Recently Used Playlist", "recentlyusedplaylist",
                        SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, lastUpdated.name, "${lastUpdated.videos.size} videos", "",
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
                SlideUpMenuGroup(container.context, "Other", "other",
                    SlideUpMenuItem(container.context, R.drawable.ic_queue_add, "Queue", "${queue.size} videos", "queue",
                        { StatePlayer.instance.addToQueue(video); }),
                    SlideUpMenuItem(container.context, R.drawable.ic_watchlist_add, StatePlayer.TYPE_WATCHLATER, "${watchLater.size} videos", "watch later",
                        { StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video)); }),
                    SlideUpMenuItem(container.context, R.drawable.ic_download, "Download", "Download the video", "download",
                        { showDownloadVideoOverlay(video, container); }, false))
            );

            val playlistItems = arrayListOf<SlideUpMenuItem>();
            for (playlist in allPlaylists) {
                playlistItems.add(SlideUpMenuItem(container.context, R.drawable.ic_playlist_add, playlist.name, "${playlist.videos.size} videos", "",
                    {
                        StatePlaylists.instance.addToPlaylist(playlist.id, video);
                        StateDownloads.instance.checkForOutdatedPlaylists();
                    }));
            }

            if(playlistItems.size > 0)
                items.add(SlideUpMenuGroup(container.context, "Playlists", "", playlistItems));

            return SlideUpMenuOverlay(container.context, container, "Add to", null, true, items).apply { show() };
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
                arrayOf(SlideUpMenuItem(container.context, R.drawable.ic_pin, "Change Pins", "Decide which buttons should be pinned", "", {
                    showOrderOverlay(container, "Select your pins in order",  (visible + hidden).map { Pair(it.text.text.toString(), it.tagRef!!) }) {
                        val selected = it
                            .map { x -> visible.find { it.tagRef == x } ?: hidden.find { it.tagRef == x } }
                            .filter { it != null }
                            .map { it!! }
                            .toList();

                        onPinnedbuttons?.invoke(selected + (visible + hidden).filter { !selected.contains(it) });
                    }
                }, false))
            ).flatten().toTypedArray();

            return SlideUpMenuOverlay(container.context, container, "More Options", null, true, *views).apply { show() };
        }

        fun showOrderOverlay(container: ViewGroup, title: String, options: List<Pair<String, Any>>, onOrdered: (List<Any>)->Unit) {
            val selection: MutableList<Any> = mutableListOf();

            var overlay: SlideUpMenuOverlay? = null;

            overlay = SlideUpMenuOverlay(container.context, container, title, "Save", true,
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