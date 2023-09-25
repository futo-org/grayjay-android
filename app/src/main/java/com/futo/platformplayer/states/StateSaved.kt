package com.futo.platformplayer.states

import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@kotlinx.serialization.Serializable
data class VideoToOpen(val url: String, val timeSeconds: Long);

class StateSaved {
    var videoToOpen: VideoToOpen? = null;

    private val _videoToOpen = FragmentedStorage.get<StringStorage>("videoToOpen")

    fun load() {
        val videoToOpenString = _videoToOpen.value;
        if (videoToOpenString.isNotEmpty()) {
            try {
                val v = Serializer.json.decodeFromString<VideoToOpen>(videoToOpenString);
                videoToOpen = v;
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to load video to open", e)
            }
        }

        Logger.i(TAG, "loaded videoToOpen=$videoToOpen");
    }

    fun setVideoToOpenNonBlocking(v: VideoToOpen? = null) {
        Logger.i(TAG, "set videoToOpen=$v");

        videoToOpen = v;
        _videoToOpen.setAndSave(if (v != null) Serializer.json.encodeToString(v) else "");
    }


    fun setVideoToOpenBlocking(v: VideoToOpen? = null) {
        Logger.i(TAG, "set videoToOpen=$v");

        videoToOpen = v;
        _videoToOpen.setAndSaveBlocking(if (v != null) Serializer.json.encodeToString(v) else "");
    }

    companion object {
        const val TAG = "StateSaved"

        val instance: StateSaved = StateSaved()
    }
}