package com.futo.platformplayer.api.media.models.comments

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.structures.IPager
import java.time.OffsetDateTime

open class PlatformComment : IPlatformComment {
    override val contextUrl: String;
    override val author: PlatformAuthorLink;
    override val message: String;
    override val rating: IRating;
    override val date: OffsetDateTime;

    override val replyCount: Int?;

    constructor(contextUrl: String, author: PlatformAuthorLink, msg: String, rating: IRating, date: OffsetDateTime, replyCount: Int? = null) {
        this.contextUrl = contextUrl;
        this.author = author;
        this.message = msg;
        this.rating = rating;
        this.date = date;
        this.replyCount = replyCount;
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment> {
        return NoCommentsPager();
    }
}