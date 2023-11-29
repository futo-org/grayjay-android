package com.futo.platformplayer.api.media.models.comments

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.polycentric.core.Pointer
import userpackage.Protocol.Reference
import java.time.OffsetDateTime

class PolycentricPlatformComment : IPlatformComment {
    override val contextUrl: String;
    override val author: PlatformAuthorLink;
    override val message: String;
    override val rating: IRating;
    override val date: OffsetDateTime;

    override val replyCount: Int?;

    val eventPointer: Pointer;
    val reference: Reference;

    constructor(contextUrl: String, author: PlatformAuthorLink, msg: String, rating: IRating, date: OffsetDateTime, eventPointer: Pointer, replyCount: Int? = null) {
        this.contextUrl = contextUrl;
        this.author = author;
        this.message = msg;
        this.rating = rating;
        this.date = date;
        this.replyCount = replyCount;
        this.eventPointer = eventPointer;
        this.reference = eventPointer.toReference();
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment> {
        return NoCommentsPager();
    }

    fun cloneWithUpdatedReplyCount(replyCount: Int?): PolycentricPlatformComment {
        return PolycentricPlatformComment(contextUrl, author, message, rating, date, eventPointer, replyCount);
    }

    companion object {
        val MAX_COMMENT_SIZE = 2000
    }
}