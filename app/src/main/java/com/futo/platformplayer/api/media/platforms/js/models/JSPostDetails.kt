package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.IPlatformPostDetails
import com.futo.platformplayer.api.media.models.post.TextType
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.states.StateDeveloper

class JSPostDetails : JSPost, IPlatformPost, IPlatformPostDetails {
    private val _hasGetComments: Boolean;
    private val _hasGetContentRecommendations: Boolean;

    override val rating: IRating;

    override val textType: TextType;
    override val content: String;



    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformPostDetails";

        rating = obj.getOrDefault<V8ValueObject>(config, "rating", contextName, null)?.let { IRating.fromV8(config, it, contextName) } ?: RatingLikes(0);
        textType = TextType.fromInt((obj.getOrDefault<Int>(config, "textType", contextName, null) ?: 0));
        content = obj.getOrDefault(config, "content", contextName, "") ?: "";

        _hasGetComments = _content.has("getComments");
        _hasGetContentRecommendations = _content.has("getContentRecommendations");
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? {
        if(!_hasGetComments || _content.isClosed)
            return null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "videoDetail.getComments()") {
                return@handleDevCall getCommentsJS(client);
            }
        else if(client is JSClient)
            return getCommentsJS(client);

        return null;
    }
    override fun getPlaybackTracker(): IPlaybackTracker? = null;

    override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? {
        if(!_hasGetContentRecommendations || _content.isClosed)
            return  null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "postDetail.getContentRecommendations()") {
                return@handleDevCall getContentRecommendationsJS(client);
            }
        else if(client is JSClient)
            return getContentRecommendationsJS(client);

        return null;
    }
    private fun getContentRecommendationsJS(client: JSClient): JSContentPager {
        val contentPager = _content.invoke<V8ValueObject>("getContentRecommendations", arrayOf<Any>());
        return JSContentPager(_pluginConfig, client, contentPager);
    }

    private fun getCommentsJS(client: JSClient): JSCommentPager {
        val commentPager = _content.invoke<V8ValueObject>("getComments", arrayOf<Any>());
        return JSCommentPager(_pluginConfig, client, commentPager);
    }

}