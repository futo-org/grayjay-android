package com.futo.platformplayer.polycentric

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.futo.polycentric.core.Collections
import org.futo.polycentric.core.getAttributionFeed
import org.futo.polycentric.core.getEvent
import org.futo.polycentric.core.getIdentityFeed
import org.futo.polycentric.core.getPostThread
import org.futo.polycentric.core.getProfile
import polycentric.v2.AttributedTo
import polycentric.v2.Content
import polycentric.v2.Delete
import polycentric.v2.Event
import polycentric.v2.EventBundle
import polycentric.v2.EventHint
import polycentric.v2.EventKey
import polycentric.v2.Link
import polycentric.v2.Post
import polycentric.v2.PostReply
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset


/**
 * Adapter functions for comment reads and writes. A video comment is a `Post` attributed to the
 * video's (normalized) URL, and replies are `PostReply` events.
 */

/**
 * Returns a pager object for the Harbor/Polycentric comments attributed to a video URL, in
 * reverse-chronological order.
 */
suspend fun PolycentricAdapter.getCommentPager(videoUrl: String, omitLabels: List<String> = emptyList()): IPager<IPlatformComment> {
    val url = normalizeUrl(videoUrl)
    val first = fetchPage(url, cursor = null, omitLabels = omitLabels)
    return object : IAsyncPager<IPlatformComment>, IPager<IPlatformComment> {
        private var _results = first.first
        private var _cursor = first.second
        override fun hasMorePages(): Boolean = _cursor != null
        override fun nextPage() = runBlocking { nextPageAsync() }
        override suspend fun nextPageAsync() {
            val cursor = _cursor ?: return
            val next = fetchPage(url, cursor, omitLabels)
            _results = next.first
            _cursor = next.second
        }
        override fun getResults(): List<IPlatformComment> = _results
    }
}

/** Returns a list of all replies to a comment. */
suspend fun PolycentricAdapter.getReplies(comment: PolycentricPlatformComment, omitLabels: List<String> = emptyList()): List<IPlatformComment> {
    val key = comment.key ?: return emptyList()
    val resp = client.getPostThread(key, omitLabels = omitLabels) ?: return emptyList()
    val labelMap = decodeLabels(resp.event_hints)
    return coroutineScope {
        resp.thread.map { async { toComment(comment.contextUrl, it, labelMap) } }.awaitAll()
    }
        .filterNotNull()
        .filter { it.key != comment.key }
}

/** Fetch a single comment by its event key. */
suspend fun PolycentricAdapter.getComment(key: EventKey): PolycentricPlatformComment? {
    val bundle = client.getEvent(key.identity, key.collection, key.sequence) ?: return null
    return toComment(contextUrlOf(bundle) ?: "", bundle)
}

/** Return a list of the active identity's comments, in reverse-chronological order. */
suspend fun PolycentricAdapter.getMyComments(): List<IPlatformComment> {
    val identity = client.activeIdentityKey ?: return emptyList()
    val resp = client.getIdentityFeed(identity) ?: return emptyList()
    val labelMap = decodeLabels(resp.event_hints)
    return coroutineScope {
        resp.event_bundles.map { async { toComment(contextUrlOf(it) ?: "", it, labelMap) } }.awaitAll().filterNotNull()
    }
}

/**
 * Publishes a new comment as a `Post` attributed to the video on the Harbor network. Then returns
 * a [PolycentricPlatformComment] object representing the new comment.
 */
suspend fun PolycentricAdapter.postComment(videoUrl: String, text: String): PolycentricPlatformComment {
    requireActiveIdentity()
    val url = normalizeUrl(videoUrl)
    val content = Content(
        post = Post(text = text, attributed_to = listOf(AttributedTo(link = Link(url = url)))),
    )
    val key = publish(content, Collections.FEED)
    return localComment(url, text, key, root = null, parent = null)
}

/**
 * Publishes a new reply as a `PostReply` targeting a parent on the Harbor network. Then returns a
 * [PolycentricPlatformComment] object representing the new reply.
 */
suspend fun PolycentricAdapter.postReply(parent: PolycentricPlatformComment, text: String): PolycentricPlatformComment {
    requireActiveIdentity()
    val root = parent.root ?: parent.key // top-level parent is its own root
    val content = Content(
        post = Post(text = text, reply = PostReply(root = root, parent = parent.key)),
    )
    val key = publish(content, Collections.FEED)
    return localComment(parent.contextUrl, text, key, root = root, parent = parent.key)
}

/** Retract one of my comments by publishing a Delete for its event. */
suspend fun PolycentricAdapter.deleteComment(comment: PolycentricPlatformComment) {
    requireActiveIdentity()
    if (!isMyComment(comment)) {
        throw IllegalArgumentException("Cannot delete a comment that is not authored by the active identity.")
    }
    publish(Content(delete = Delete(event_key = comment.key)), Collections.FEED)
}

/** Retrieves a page of comments using polycentric-core's attribution feed. */
private suspend fun PolycentricAdapter.fetchPage(url: String, cursor: String?, omitLabels: List<String> = emptyList()): Pair<List<IPlatformComment>, String?> {
    val resp = client.getAttributionFeed(AttributedTo(link = Link(url = url)), forwardToken = cursor, omitLabels = omitLabels)
        ?: return emptyList<IPlatformComment>() to null
    val labelMap = decodeLabels(resp.event_hints)
    val comments = coroutineScope {
        resp.event_bundles.map { async { toComment(url, it, labelMap) } }.awaitAll().filterNotNull()
    }
    val next = resp.page_info?.takeIf { it.has_next_page }?.end_cursor
    return comments to next
}

/**
 * Turn a generic event bundle retrieved from polycentric-core into a [PolycentricPlatformComment],
 * if it is a `Post` or `PostReply` event.
 */
private suspend fun PolycentricAdapter.toComment(contextUrl: String, bundle: EventBundle, labelMap: Map<String, List<String>> = emptyMap()): PolycentricPlatformComment? {
    val signed = bundle.signed_event ?: return null
    val event = Event.ADAPTER.decode(signed.event_bytes)
    val key = event.key ?: return null
    val contentBytes = bundle.serialized_content?.content_bytes ?: return null
    val post = Content.ADAPTER.decode(contentBytes).post ?: return null
    val meta = bundle.meta
    return PolycentricPlatformComment(
        contextUrl = contextUrl,
        author = resolveAuthor(key.identity),
        msg = post.text.take(PolycentricPlatformComment.MAX_COMMENT_SIZE),
        rating = RatingLikeDislikes((meta?.upvote_count ?: 0).toLong(), (meta?.downvote_count ?: 0).toLong()),
        date = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.created_at), ZoneOffset.UTC),
        key = key,
        root = post.reply?.root,
        parent = post.reply?.parent,
        replyCount = meta?.reply_count ?: 0,
        labels = labelMap[labelKeyOf(key)].orEmpty(),
    )
}

/**
 * Produce a new [PolycentricPlatformComment] with the given metadata and zero ratings and replies,
 * for when the client authors a new comment.
 */
private suspend fun PolycentricAdapter.localComment(contextUrl: String, text: String, key: EventKey, root: EventKey?, parent: EventKey?) =
    PolycentricPlatformComment(
        contextUrl = contextUrl,
        author = resolveAuthor(client.activeIdentityKey ?: key.identity),
        msg = text,
        rating = RatingLikeDislikes(0, 0),
        date = OffsetDateTime.now(),
        key = key,
        root = root,
        parent = parent,
        replyCount = 0,
    )

/** Get the URL a post is attributed to, if any. */
private fun PolycentricAdapter.contextUrlOf(bundle: EventBundle): String? {
    val bytes = bundle.serialized_content?.content_bytes ?: return null
    val post = Content.ADAPTER.decode(bytes).post ?: return null
    return post.attributed_to.firstNotNullOfOrNull { it.link?.url }
}

/**
 * Decode the moderation labels carried in a feed's `event_hints` field. Each label event targets an
 * event key. We return a mapping from keys to label values.
 */
private fun PolycentricAdapter.decodeLabels(hints: List<EventHint>): Map<String, List<String>> {
    val map = HashMap<String, MutableList<String>>()
    for (hint in hints) {
        val contentBytes = hint.event_bundle?.serialized_content?.content_bytes ?: continue
        val labels = runCatching { Content.ADAPTER.decode(contentBytes).labels }.getOrNull() ?: continue
        val target = labels.event_key ?: continue
        map.getOrPut(labelKeyOf(target)) { mutableListOf() }.addAll(labels.label_values)
    }
    return map
}

/** Stable string key for an [EventKey], used to match labels to comments. */
private fun labelKeyOf(k: EventKey): String =
    "${k.collection}|${k.identity}|${k.signed_by?.key_type}|${k.signed_by?.key?.hex()}|${k.sequence}"

// PlatformID Polycentric claim constant, used below
private const val POLYCENTRIC_CLAIM_TYPE = 25

/** Create a Grayjay [PlatformAuthorLink] for the given Harbor identity. */
private suspend fun PolycentricAdapter.resolveAuthor(identity: String): PlatformAuthorLink {
    val profile = client.getProfile(identity)
    val update = profile?.let { latestProfileUpdate(it.event_bundles) }
    return PlatformAuthorLink(
        id = PlatformID("polycentric", authorUrl(identity), null, POLYCENTRIC_CLAIM_TYPE),
        name = update?.name ?: "Unknown",
        url = authorUrl(identity),
        thumbnail = bestBlobUrl(update?.avatar),
    )
}

/** Return the URL for the profile view of a Harbor identity. */
private fun authorUrl(identity: String) = "https://harbor.social/$identity"
