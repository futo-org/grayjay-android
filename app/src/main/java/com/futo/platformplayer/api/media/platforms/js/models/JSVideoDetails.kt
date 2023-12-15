package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueNull
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSVideoSourceDescriptor
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullable
import com.futo.platformplayer.states.StateDeveloper

class JSVideoDetails : JSVideo, IPlatformVideoDetails {
    private val _hasGetComments: Boolean;
    private val _hasGetPlaybackTracker: Boolean;

    //Details
    override val description : String;
    override val rating : IRating;

    override val video: IVideoSourceDescriptor;
    override val preview : IVideoSourceDescriptor? = null;

    override val dash: IDashManifestSource?;
    override val hls: IHLSManifestSource?;

    override val live: IVideoSource?;

    override val subtitles: List<ISubtitleSource>;


    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin.config, obj) {
        val contextName = "VideoDetails";
        val config = plugin.config;
        description = _content.getOrThrow(config, "description", contextName);
        video = JSVideoSourceDescriptor.fromV8(plugin, _content.getOrThrow(config, "video", contextName));
        dash =  JSSource.fromV8DashNullable(plugin, _content.getOrThrowNullable<V8ValueObject>(config, "dash", contextName));
        hls = JSSource.fromV8HLSNullable(plugin, _content.getOrThrowNullable<V8ValueObject>(config, "hls", contextName));
        live = JSSource.fromV8VideoNullable(plugin, _content.getOrThrowNullable<V8ValueObject>(config, "live", contextName));
        rating = IRating.fromV8OrDefault(config, _content.getOrDefault<V8ValueObject>(config, "rating", contextName, null), RatingLikes(0));

        if(!_content.has("subtitles"))
            subtitles = listOf();
        else {
            val subArrs = _content.getOrThrowNullable<V8ValueArray>(config, "subtitles", contextName);
            if(subArrs != null)
                subtitles = subArrs.keys.map { JSSubtitleSource.fromV8(config, subArrs.get(it)) };
            else
                subtitles = listOf();
        }

        _hasGetComments = _content.has("getComments");
        _hasGetPlaybackTracker = _content.has("getPlaybackTracker");
    }

    override fun getPlaybackTracker(): IPlaybackTracker? {
        if(!_hasGetPlaybackTracker || _content.isClosed)
            return null;
        if(_pluginConfig.id == StateDeveloper.DEV_ID)
            return StateDeveloper.instance.handleDevCall(_pluginConfig.id, "videoDetail.getComments()") {
                return@handleDevCall getPlaybackTrackerJS();
            }
        else
            return getPlaybackTrackerJS();
    }
    private fun getPlaybackTrackerJS(): IPlaybackTracker? {
        return V8Plugin.catchScriptErrors(_pluginConfig, "VideoDetails", "videoDetails.getPlaybackTracker()") {
            val tracker = _content.invoke<V8Value>("getPlaybackTracker", arrayOf<Any>())
                ?: return@catchScriptErrors null;
            if(tracker is V8ValueObject)
                return@catchScriptErrors JSPlaybackTracker(_pluginConfig, tracker);
            else
                return@catchScriptErrors null;
        };
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? {
        if(client !is JSClient || !_hasGetComments || _content.isClosed)
            return null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "videoDetail.getComments()") {
                return@handleDevCall getCommentsJS(client);
            }
        else
            return getCommentsJS(client);
    }

    private fun getCommentsJS(client: JSClient): IPager<IPlatformComment>? {
        val commentPager = _content.invoke<V8Value>("getComments", arrayOf<Any>());
        if (commentPager !is V8ValueObject) //TODO: Maybe handle this better?
            return null;

        return JSCommentPager(_pluginConfig, client, commentPager);
    }
}