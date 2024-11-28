package com.futo.platformplayer.api.media.models.comments

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.ratings.RatingType
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.Deferred
import java.time.OffsetDateTime

class LazyComment: IPlatformComment {
    private var _commentDeferred: Deferred<IPlatformComment>;
    private var _commentLoaded: IPlatformComment? = null;
    private var _commentException: Throwable? = null;

    override val contextUrl: String
        get() = _commentLoaded?.contextUrl ?: "";
    override val author: PlatformAuthorLink
        get() = _commentLoaded?.author ?: PlatformAuthorLink.UNKNOWN;
    override val message: String
        get() = _commentLoaded?.message ?: "";
    override val rating: IRating
        get() = _commentLoaded?.rating ?: RatingLikes(0);
    override val date: OffsetDateTime?
        get() = _commentLoaded?.date ?: OffsetDateTime.MIN;
    override val replyCount: Int?
        get() = _commentLoaded?.replyCount ?: 0;

    val isAvailable: Boolean get() = _commentLoaded != null;

    private var _uiHandler: ((LazyComment)->Unit)? = null;

    constructor(commentDeferred: Deferred<IPlatformComment>) {
        _commentDeferred = commentDeferred;
        _commentDeferred.invokeOnCompletion {
            if(it == null) {
                _commentLoaded = commentDeferred.getCompleted();
                Logger.i("LazyComment", "Resolved comment");
            }
            else {
                _commentException = it;
                Logger.e("LazyComment", "Resolving comment failed: ${it.message}", it);
            }

            _uiHandler?.invoke(this);
        }
    }

    fun getUnderlyingComment(): IPlatformComment? {
        return _commentLoaded;
    }

    fun setUIHandler(handler: (LazyComment)->Unit){
        _uiHandler = handler;
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment>? {
        return _commentLoaded?.getReplies(client);
    }

}