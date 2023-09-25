package com.futo.platformplayer.states

import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringHashSetStorage

class StateMeta {
    val hiddenVideos = FragmentedStorage.get<StringHashSetStorage>("hiddenVideos");

    fun isVideoHidden(videoUrl: String) : Boolean {
        return hiddenVideos.contains(videoUrl);
    }
    fun addHiddenVideo(videoUrl: String) {
        hiddenVideos.addDistinct(videoUrl);
    }
    fun removeHiddenVideo(videoUrl: String) {
        hiddenVideos.remove(videoUrl);
    }

    companion object {
        private var _instance : StateMeta? = null;
        val instance : StateMeta
            get(){
            if(_instance == null)
                _instance = StateMeta();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}