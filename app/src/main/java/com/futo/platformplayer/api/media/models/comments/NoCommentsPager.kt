package com.futo.platformplayer.api.media.models.comments

import com.futo.platformplayer.api.media.structures.IPager

class NoCommentsPager : IPager<IPlatformComment> {

    override fun hasMorePages(): Boolean = false;
    override fun nextPage() { }
    override fun getResults(): List<IPlatformComment> {
        return listOf();
    }
}