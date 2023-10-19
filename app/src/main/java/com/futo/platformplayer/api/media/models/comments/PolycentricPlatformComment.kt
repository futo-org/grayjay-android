package com.futo.platformplayer.api.media.models.comments

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.states.StatePolycentric
import com.futo.polycentric.core.Pointer
import com.futo.polycentric.core.SignedEvent
import userpackage.Protocol.Reference
import java.time.OffsetDateTime

class PolycentricPlatformComment : IPlatformComment {
    override val contextUrl: String;
    override val author: PlatformAuthorLink;
    override val message: String;
    override val rating: IRating;
    override val date: OffsetDateTime;

    override val replyCount: Int?;

    val reference: Reference;

    constructor(contextUrl: String, author: PlatformAuthorLink, msg: String, rating: IRating, date: OffsetDateTime, reference: Reference, replyCount: Int? = null) {
        this.contextUrl = contextUrl;
        this.author = author;
        this.message = msg;
        this.rating = rating;
        this.date = date;
        this.replyCount = replyCount;
        this.reference = reference;
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment> {
        return NoCommentsPager();
    }

    fun cloneWithUpdatedReplyCount(replyCount: Int?): PolycentricPlatformComment {
        return PolycentricPlatformComment(contextUrl, author, message, rating, date, reference, replyCount);
    }

    companion object {
        val MAX_COMMENT_SIZE = 2000
    }
}