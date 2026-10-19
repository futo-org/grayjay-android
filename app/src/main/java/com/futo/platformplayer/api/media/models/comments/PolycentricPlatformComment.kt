package com.futo.platformplayer.api.media.models.comments

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.structures.IPager
import polycentric.v2.EventKey
import java.time.OffsetDateTime

class PolycentricPlatformComment : IPlatformComment {
    override val contextUrl: String;
    override val author: PlatformAuthorLink;
    override val message: String;
    override val rating: IRating;
    override val date: OffsetDateTime;

    override val replyCount: Int?;

    // Harbor adapter fields
    val key: EventKey?;
    val root: EventKey?;
    val parent: EventKey?;
    val labels: List<String>;

    constructor(contextUrl: String, author: PlatformAuthorLink, msg: String, rating: IRating, date: OffsetDateTime, replyCount: Int? = null, key: EventKey? = null, root: EventKey? = null, parent: EventKey? = null, labels: List<String> = listOf()) {
        this.contextUrl = contextUrl;
        this.author = author;
        this.message = msg;
        this.rating = rating;
        this.date = date;
        this.replyCount = replyCount;
        this.key = key;
        this.root = root;
        this.parent = parent;
        this.labels = labels;
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment> {
        return NoCommentsPager();
    }

    fun cloneWithUpdatedReplyCount(replyCount: Int?): PolycentricPlatformComment {
        return PolycentricPlatformComment(contextUrl, author, message, rating, date, replyCount, key, root, parent, labels);
    }

    companion object {
        private const val TAG = "PolycentricPlatformComment"
        val MAX_COMMENT_SIZE = 2000
    }
}
