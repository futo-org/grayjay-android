package com.futo.platformplayer.api.media.platforms.js

import android.content.Context
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDeveloper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DevJSClient : JSClient {
    override val id: String
        get() = StateDeveloper.DEV_ID;

    private val _devScript: String;
    private var _auth: SourceAuth? = null;
    private var _captcha: SourceCaptchaData? = null;

    val devID: String;

    constructor(context: Context, config: SourcePluginConfig, script: String, auth: SourceAuth? = null, captcha: SourceCaptchaData? = null, devID: String? = null, settings: HashMap<String, String?>? = null): super(context, SourcePluginDescriptor(config, auth?.toEncrypted(), captcha?.toEncrypted(), listOf("DEV"), settings), null, script) {
        _devScript = script;
        _auth = auth;
        _captcha = captcha;
        this.devID = devID ?: UUID.randomUUID().toString().substring(0, 5);

        onCaptchaException.subscribe { client, c ->
            StateApp.instance.handleCaptchaException(client, c);
        }
    }
    //TODO: Misisng auth/captcha pass on purpose?
    constructor(context: Context, descriptor: SourcePluginDescriptor, script: String, auth: SourceAuth? = null, captcha: SourceCaptchaData? = null, savedState: String? = null, devID: String? = null): super(context, descriptor, savedState, script) {
        _devScript = script;
        _auth = auth;
        _captcha = captcha;
        this.devID = devID ?: UUID.randomUUID().toString().substring(0, 5);

        onCaptchaException.subscribe { client, c ->
            StateApp.instance.handleCaptchaException(client, c);
        }
    }

    fun setCaptcha(captcha: SourceCaptchaData? = null) {
        _captcha = captcha;
    }
    fun setAuth(auth: SourceAuth? = null) {
        _auth = auth;
    }
    fun recreate(context: Context): DevJSClient {
        return DevJSClient(context, config, _devScript, _auth, _captcha, devID, descriptor.settings);
    }

    override fun getCopy(privateCopy: Boolean, noSaveState: Boolean): JSClient {
        val client = DevJSClient(_context, descriptor, _script, if(!privateCopy) _auth else null, _captcha, if (noSaveState) null else saveState(), devID);
        client.setReloadData(getReloadData(true));
        if (noSaveState)
            client.initialize()
        return client
    }

    override fun initialize() {
        return StateDeveloper.instance.handleDevCall(devID, "enable"){
            super.initialize();
        };
    }
    override fun disable() {
        return StateDeveloper.instance.handleDevCall(devID, "disable"){
            super.disable()
        };
    }

    //Home
    override fun getHome(): IPager<IPlatformContent> {
        return StateDeveloper.instance.handleDevCall(devID, "getHome"){
            DevPlatformVideoPager(devID, "homePager", super.getHome());
        };
    }

    //Search
    override fun searchSuggestions(query: String): Array<String> {
        return StateDeveloper.instance.handleDevCall(devID, "searchSuggestions"){
            super.searchSuggestions(query);
        };
    }
    override fun search(
        query: String,
        type: String?,
        order: String?,
        filters: Map<String, List<String>>?
    ): IPager<IPlatformContent> {
        return StateDeveloper.instance.handleDevCall(devID, "search"){
            DevPlatformVideoPager(devID, "searchPager", super.search(query, type, order, filters));
        };
    }

    //Channel
    override fun isChannelUrl(url: String): Boolean {
        return StateDeveloper.instance.handleDevCall(devID, "isChannelUrl"){
            super.isChannelUrl(url);
        };
    }
    override fun getChannel(channelUrl: String): IPlatformChannel {
        return StateDeveloper.instance.handleDevCall(devID, "getChannel"){
            super.getChannel(channelUrl);
        };
    }
    override fun getChannelContents(
        channelUrl: String,
        type: String?,
        order: String?,
        filters: Map<String, List<String>>?
    ): IPager<IPlatformContent> {
        return StateDeveloper.instance.handleDevCall(devID, "getChannelVideos"){
            DevPlatformVideoPager(devID, "channelPager", super.getChannelContents(channelUrl, type, order, filters));
        };
    }

    //Video
    override fun isContentDetailsUrl(url: String): Boolean {
        return StateDeveloper.instance.handleDevCall(devID, "isVideoDetailsUrl(${Json.encodeToString(url)})"){
            super.isContentDetailsUrl(url);
        };
    }
    override fun getContentDetails(url: String): IPlatformContentDetails {
        return StateDeveloper.instance.handleDevCall(devID, "getVideoDetails"){
            super.getContentDetails(url);
        };
    }

    //Comments
    override fun getComments(url: String): IPager<IPlatformComment> {
        return StateDeveloper.instance.handleDevCall(devID, "getComments"){
            DevPlatformCommentPager(devID, "commentPager", super.getComments(url));
        };
    }
    override fun getSubComments(comment: IPlatformComment): IPager<IPlatformComment> {
        return StateDeveloper.instance.handleDevCall(devID, "getSubComments"){
            DevPlatformCommentPager(devID, "subCommentPager", super.getSubComments(comment));
        };
    }

    override fun searchPlaylists(
        query: String,
        type: String?,
        order: String?,
        filters: Map<String, List<String>>?
    ): IPager<IPlatformContent> {
        return StateDeveloper.instance.handleDevCall(devID, "searchPlaylists"){
            DevPlatformVideoPager(devID, "searchPlaylists", super.searchPlaylists(query, type, order, filters));
        };
    }

    class DevPlatformVideoPager(
        private val _devId: String,
        private val _contextName: String,
        private val _pager: IPager<IPlatformContent>
    ) : IPager<IPlatformContent> {

        private var _morePagesWasFalse = false;

        override fun hasMorePages(): Boolean {
            if(_morePagesWasFalse)
                return false;
            return StateDeveloper.instance.handleDevCall(_devId, "${_contextName}.hasMorePages", true) {
                val result = _pager.hasMorePages();
                if(!result)
                    _morePagesWasFalse = true;
                return@handleDevCall result;
            }
        }

        override fun nextPage() {
            return StateDeveloper.instance.handleDevCall(_devId, "${_contextName}.nextPage") {
                _pager.nextPage();
            }
        }

        override fun getResults(): List<IPlatformContent> {
            return StateDeveloper.instance.handleDevCall(_devId, "${_contextName}.getResults", true) {
                _pager.getResults();
            }
        }
    }
    class DevPlatformCommentPager(
        private val _devId: String,
        private val _contextName: String,
        private val _pager: IPager<IPlatformComment>) : IPager<IPlatformComment> {

        override fun hasMorePages(): Boolean {
            return StateDeveloper.instance.handleDevCall(_devId, "${_contextName}.hasMorePages") {
                _pager.hasMorePages();
            }
        }

        override fun nextPage() {
            return StateDeveloper.instance.handleDevCall(_devId, "${_contextName}.nextPage") {
                _pager.nextPage();
            }
        }

        override fun getResults(): List<IPlatformComment> {
            return StateDeveloper.instance.handleDevCall(_devId, "${_contextName}.getResults") {
                _pager.getResults();
            }
        }
    }
}