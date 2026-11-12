package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.JSChannelContent
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

interface IJSContent: IPlatformContent  {

    companion object {
        fun fromV8(plugin: JSClient, obj: V8ValueObject): IPlatformContent {
            obj.ensureIsBusy();
            val config = plugin.config;
            val type: Int = obj.getOrThrow(config, "contentType", "ContentItem");
            val pluginType: String? = obj.getOrDefault(config, "plugin_type", "ContentItem", null);

            //TODO: Temporary workaround for intercepting details in lists
            if(pluginType != null && pluginType.endsWith("Details"))
                return IJSContentDetails.fromV8(plugin, obj);

            return when(ContentType.fromInt(type)) {
                ContentType.MEDIA -> JSVideo(config, obj);
                ContentType.POST -> JSPost(config, obj);
                ContentType.NESTED_VIDEO -> JSNestedMediaContent(config, obj);
                ContentType.PLAYLIST -> JSPlaylist(config, obj);
                ContentType.LOCKED -> JSLockedContent(config, obj);
                ContentType.CHANNEL -> JSChannelContent(config, obj);
                ContentType.ARTICLE -> JSArticle(config, obj);
                ContentType.WEB -> JSWeb(config, obj);
                else -> throw NotImplementedError("Unknown content type ${type}");
            }
        }
    }
}