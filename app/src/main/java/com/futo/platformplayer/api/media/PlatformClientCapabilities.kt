package com.futo.platformplayer.api.media

data class PlatformClientCapabilities(
    val hasChannelSearch: Boolean = false,
    val hasGetComments: Boolean = false,
    val hasGetUserSubscriptions: Boolean = false,
    val hasSearchPlaylists: Boolean = false,
    val hasGetPlaylist: Boolean = false,
    val hasGetUserPlaylists: Boolean = false,
    val hasSearchChannelContents: Boolean = false,
    val hasSaveState: Boolean = false,
    val hasGetPlaybackTracker: Boolean = false,
    val hasGetChannelUrlByClaim: Boolean = false,
    val hasGetChannelTemplateByClaimMap: Boolean = false,
    val hasGetSearchCapabilities: Boolean = false,
    val hasGetSearchChannelContentsCapabilities: Boolean = false,
    val hasGetChannelCapabilities: Boolean = false,
    val hasGetLiveEvents: Boolean = false,
    val hasGetLiveChatWindow: Boolean = false,
    val hasGetContentChapters: Boolean = false
) {

}