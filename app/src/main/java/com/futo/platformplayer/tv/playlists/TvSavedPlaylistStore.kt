package com.futo.platformplayer.tv.playlists

import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlaylists

internal interface TvSavedPlaylistStore {
    val playlistId: String?
        get() = null

    fun name(): String? = null

    fun videos(): List<SerializedPlatformVideo>

    fun observe(owner: Any, changed: () -> Unit) {}

    fun release(owner: Any) {}

    fun remove(url: String, position: Int = -1)

    fun move(url: String, destination: Int, position: Int = -1)

    fun canPlay(url: String): Boolean

    companion object {
        fun playlist(id: String) =
            object : TvSavedPlaylistStore {
                override val playlistId = id

                override fun name() = playlist().name

                override fun videos() = playlist().videos.toList()

                override fun remove(url: String, position: Int) = change {
                    val index = position.takeIf { getOrNull(it)?.url == url } ?: indexOfFirst { it.url == url }
                    if (index >= 0) removeAt(index)
                }

                override fun move(url: String, destination: Int, position: Int) = change {
                    val from = position.takeIf { getOrNull(it)?.url == url } ?: indexOfFirst { it.url == url }
                    if (from >= 0) add(destination.coerceIn(0, lastIndex), removeAt(from))
                }

                override fun canPlay(url: String) = playable(url)

                private fun playlist() =
                    checkNotNull(StatePlaylists.instance.getPlaylist(id)) { "Playlist no longer exists" }

                private fun change(action: MutableList<SerializedPlatformVideo>.() -> Unit) {
                    val state = StatePlaylists.instance
                    synchronized(state.playlistStore) {
                        val playlist = playlist()
                        playlist.videos.action()
                        state.createOrUpdatePlaylist(playlist, true)
                    }
                }
            }

        fun platform() =
            object : TvSavedPlaylistStore {
                override fun videos() = StatePlaylists.instance.getWatchLater()

                override fun observe(owner: Any, changed: () -> Unit) =
                    StatePlaylists.instance.onWatchLaterChanged.subscribe(owner, changed)

                override fun release(owner: Any) = StatePlaylists.instance.onWatchLaterChanged.remove(owner)

                override fun remove(url: String, position: Int) =
                    StatePlaylists.instance.removeFromWatchLater(url, true)

                override fun move(url: String, destination: Int, position: Int) {
                    val videos = videos().toMutableList()
                    val from = videos.indexOfFirst { it.url == url }
                    if (from < 0) return
                    videos.add(destination.coerceIn(0, videos.lastIndex), videos.removeAt(from))
                    StatePlaylists.instance.updateWatchLater(videos, true)
                }

                override fun canPlay(url: String) = playable(url)
            }

        private fun playable(url: String) =
            url.startsWith("content://") ||
                url.startsWith("file://") ||
                StatePlatform.instance.getContentClientOrNull(url) != null
    }
}
