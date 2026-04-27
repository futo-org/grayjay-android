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
import com.futo.platformplayer.getSourcePlugin
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.states.StateDeveloper

class JSPostDetails : JSPost, IPlatformPost, IPlatformPostDetails {
    private val _hasGetComments: Boolean;
    private val _hasGetContentRecommendations: Boolean;

    override val rating: IRating;

    override val textType: TextType;
    override val content: String;



    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        var parsedRating: IRating? = null;
        var parsedTextType: TextType? = null;
        var parsedContent: String? = null;
        var parsedHasGetComments = false;
        var parsedHasGetContentRecommendations = false;

        val parse = {
            val contextName = "PlatformPostDetails";

            parsedRating = obj.getOrDefault<V8ValueObject>(config, "rating", contextName, null)?.let { IRating.fromV8(config, it, contextName) } ?: RatingLikes(0);
            parsedTextType = TextType.fromInt((obj.getOrDefault<Int>(config, "textType", contextName, null) ?: 0));
            parsedContent = obj.getOrDefault(config, "content", contextName, "") ?: "";

            parsedHasGetComments = _content.has("getComments");
            parsedHasGetContentRecommendations = _content.has("getContentRecommendations");
        };
        obj.getSourcePlugin()?.busy {
            parse();
        } ?: parse()

        rating = parsedRating ?: RatingLikes(0);
        textType = parsedTextType ?: TextType.RAW;
        content = parsedContent ?: "";
        _hasGetComments = parsedHasGetComments;
        _hasGetContentRecommendations = parsedHasGetContentRecommendations;
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? {
        val jsClient = client as? JSClient;
        if(jsClient == null)
            return null;
        val canGetComments = jsClient.busy {
            _hasGetComments && !_content.isClosed
        }
        if(!canGetComments)
            return null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "videoDetail.getComments()") {
                return@handleDevCall getCommentsJS(client);
            }
        return getCommentsJS(jsClient);
    }
    override fun getPlaybackTracker(): IPlaybackTracker? = null;

    override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? {
        val jsClient = client as? JSClient;
        if(jsClient == null)
            return null;
        val canGetContentRecommendations = jsClient.busy {
            _hasGetContentRecommendations && !_content.isClosed
        }
        if(!canGetContentRecommendations)
            return  null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "postDetail.getContentRecommendations()") {
                return@handleDevCall getContentRecommendationsJS(client);
            }
        return getContentRecommendationsJS(jsClient);
    }
    private fun getContentRecommendationsJS(client: JSClient): JSContentPager {
        return client.busy {
            val contentPager = _content.invokeV8<V8ValueObject>("getContentRecommendations", arrayOf<Any>());
            return@busy JSContentPager(_pluginConfig, client, contentPager);
        }
    }

    private fun getCommentsJS(client: JSClient): JSCommentPager {
        return client.busy {
            val commentPager = _content.invokeV8<V8ValueObject>("getComments", arrayOf<Any>());
            return@busy JSCommentPager(_pluginConfig, client, commentPager);
        }
    }

}
