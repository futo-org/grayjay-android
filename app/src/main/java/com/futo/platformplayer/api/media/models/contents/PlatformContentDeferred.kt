package com.futo.platformplayer.api.media.models.contents

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import java.time.OffsetDateTime

class PlatformContentDeferred(pluginId: String?): IPlatformContent {
    override val contentType: ContentType = ContentType.DEFERRED;
    override val id: PlatformID = PlatformID("", null, pluginId);
    override val name: String = "";
    override val url: String = "";
    override val shareUrl: String = "";
    override val datetime: OffsetDateTime? = null;
    override val author: PlatformAuthorLink = PlatformAuthorLink(PlatformID("", pluginId), "", "", null, null);
}