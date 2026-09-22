package com.futo.platformplayer.polycentric

import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import org.futo.polycentric.core.Collections
import polycentric.v2.AttributedTo
import polycentric.v2.AttributedToReaction
import polycentric.v2.Content
import polycentric.v2.Delete
import polycentric.v2.Event
import polycentric.v2.EventKey
import polycentric.v2.GetAttributedToReactionCountsRequest
import polycentric.v2.GetAttributedToReactionCountsResponse
import polycentric.v2.Link
import polycentric.v2.Reaction

/**
 * Adapter functions for ratings (known in Harbor/Polycentric as reactions). In Harbor, reactions
 * each are represented in an emoji, but for Grayjay, we only display whether the reaction is
 * positive or negative, ignoring the emoji assigned.
 */

/**
 * Set rating on a video URL. Truthy `positive` sets a like, falsy sets a dislike, and null clears
 * the reaction.
 */
suspend fun PolycentricAdapter.setVideoRating(videoUrl: String, positive: Boolean?) {
    requireActiveIdentity()
    val url = normalizeUrl(videoUrl)
    for ((key, _) in myVideoReactions(url)) {
        publish(Content(delete = Delete(event_key = key)), Collections.INTERACTIONS)
    }
    if (positive != null) {
        publish(
            Content(attributed_to_reaction = AttributedToReaction(attributed_to = AttributedTo(link = Link(url = url)), positive = positive)),
            Collections.INTERACTIONS,
        )
    }
}

/**
 * Set rating on a comment. Truthy `positive` sets a like, falsy sets a dislike, and null clears the
 * reaction.
 */
suspend fun PolycentricAdapter.setCommentRating(comment: PolycentricPlatformComment, positive: Boolean?) {
    requireActiveIdentity()
    for ((key, _) in myCommentReactions(comment)) {
        publish(Content(delete = Delete(event_key = key)), Collections.INTERACTIONS)
    }
    if (positive != null) {
        publish(
            Content(reaction = Reaction(event_key = comment.key, positive = positive)),
            Collections.INTERACTIONS,
        )
    }
}

/**
 * Return the current rating on a video URL. Truthy returns mean it is liked, falsy mean disliked,
 * and null means it does not have a rating.
 */
fun PolycentricAdapter.myVideoRating(videoUrl: String): Boolean? = myVideoReactions(normalizeUrl(videoUrl)).firstOrNull()?.second

/**
 * Return the current rating on a comment. Truthy returns mean it is liked, falsy, mean disliked,
 * and null means it does not have a rating.
 */
fun PolycentricAdapter.myCommentRating(comment: PolycentricPlatformComment): Boolean? = myCommentReactions(comment).firstOrNull()?.second

/**
 * The server's aggregate ratings/reactions for a video URL. Returns zeros if no server is
 * configured or the query fails.
 */
suspend fun PolycentricAdapter.getLiveVideoRating(videoUrl: String): RatingLikeDislikes {
    val server = client.servers.firstOrNull() ?: return RatingLikeDislikes(0, 0)
    val request = GetAttributedToReactionCountsRequest(
        attributed_to = AttributedTo(link = Link(url = normalizeUrl(videoUrl))),
    )
    val bytes = client.core.getAttributedToReactionCounts(
        server,
        GetAttributedToReactionCountsRequest.ADAPTER.encode(request),
    )
    val resp = GetAttributedToReactionCountsResponse.ADAPTER.decode(bytes)
    return RatingLikeDislikes(resp.upvote_count, resp.downvote_count)
}

/** Whether the comment was authored by the active identity (deletable). */
fun PolycentricAdapter.isMyComment(comment: PolycentricPlatformComment): Boolean {
    val identity = client.activeIdentityKey ?: return false
    return comment.key?.identity == identity
}

/**
 * Returns a list of all non-deleted reactions targeting a video URL.
 */
private fun PolycentricAdapter.myVideoReactions(url: String): List<Pair<EventKey, Boolean>> =
    myInteractions().mapNotNull { (key, content) ->
        content.attributed_to_reaction?.takeIf { it.attributed_to?.link?.url == url }
            ?.let { key to it.positive }
    }.toList()

/**
 * Returns a list of all non-deleted reactions targeting a Harbor/Polycentric event.
 */
private fun PolycentricAdapter.myCommentReactions(comment: PolycentricPlatformComment): List<Pair<EventKey, Boolean>> =
    myInteractions().mapNotNull { (key, content) ->
        content.reaction?.takeIf { it.event_key == comment.key }
            ?.let { key to it.positive }
    }.toList()
