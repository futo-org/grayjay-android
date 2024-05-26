package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrDefaultList
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullable

class JSChannel : IPlatformChannel {
    private val _pluginConfig: SourcePluginConfig;
    private val _channel : V8ValueObject;

    override val id: PlatformID;
    override val name: String;
    override val thumbnail: String?;
    override val banner: String?;
    override val subscribers: Long;
    override val description: String?;
    override val url: String;
    override val links: Map<String, String>;
    override val urlAlternatives: List<String>;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) {
        _pluginConfig = config;
        _channel = obj;
        val contextName = "PlatformChannel";
        id = PlatformID.fromV8(_pluginConfig, _channel.getOrThrow(config, "id", contextName));
        name = _channel.getOrThrow(config, "name", contextName);
        thumbnail = _channel.getOrThrowNullable(config, "thumbnail", contextName);
        banner = _channel.getOrThrowNullable(config, "banner", contextName);
        subscribers = _channel.getOrThrow<Int>(config, "subscribers", contextName).toLong();
        description = _channel.getOrThrowNullable(config, "description", contextName);
        url = _channel.getOrThrow(config, "url", contextName);
        urlAlternatives = _channel.getOrDefaultList(config, "urlAlternatives", contextName, listOf()) ?: listOf();
        links = HashMap(_channel.getOrDefault<Map<String, String>>(config, "links", contextName, mapOf()) ?: mapOf());
    }

    override fun getContents(client: IPlatformClient): IPager<IPlatformContent> {
        return client.getChannelContents(url);
    }
}