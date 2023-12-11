package com.futo.platformplayer.api.media.models.subtitles

import android.net.Uri

interface ISubtitleSource {
    val name: String;
    val url: String?;
    val format: String?;
    val hasFetch: Boolean;

    fun getSubtitles(): String?;

    suspend fun getSubtitlesURI(): Uri?;
}