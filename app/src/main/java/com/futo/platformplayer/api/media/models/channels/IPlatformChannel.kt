package com.futo.platformplayer.api.media.models.channels

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IPager

interface IPlatformChannel {
    val id : PlatformID;
    val name : String;
    val thumbnail : String?;
    val banner : String?;
    val subscribers : Long;
    val description: String?;
    val url: String;
    val links: Map<String, String>;
    val urlAlternatives: List<String>;

    fun getContents(client: IPlatformClient): IPager<IPlatformContent>;
}