package com.futo.platformplayer.polycentric

import androidx.annotation.VisibleForTesting
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import kotlinx.coroutines.runBlocking
import org.futo.polycentric.core.Collections
import org.futo.polycentric.core.getAttributionFeed
import org.futo.polycentric.core.getIdentityFeed
import org.futo.polycentric.core.getPostThread
import polycentric.v2.Content
import polycentric.v2.Delete
import polycentric.v2.Event
import polycentric.v2.EventBundle
import polycentric.v2.EventHint
import polycentric.v2.EventKey
import polycentric.v2.Post
import polycentric.v2.PostReply
import polycentric.v2.ProfileUpdate
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

/**
 * Returns a pager over all replies to a comment. The thread is fetched in a
 * single request, so the pager only has a single page.
 */
suspend fun PolycentricAdapter.getReplies(comment: PolycentricPlatformComment, omitLabels: List<String> = emptyList()): IPager<IPlatformComment> {
    val key = comment.key ?: return staticCommentPager(emptyList())
    val resp = client.getPostThread(key, omitLabels = omitLabels) ?: return staticCommentPager(emptyList())
    val replies = toComments(resp.thread, resp.event_hints) { comment.contextUrl }
        .filter { it.key != comment.key }
    return staticCommentPager(replies)
}

/** A simple single-page pager over an in-memory list of comments. */
private fun staticCommentPager(comments: List<IPlatformComment>): IPager<IPlatformComment> =
    object : IAsyncPager<IPlatformComment>, IPager<IPlatformComment> {
        override fun hasMorePages(): Boolean = false
        override fun nextPage() = runBlocking { nextPageAsync() }
        override suspend fun nextPageAsync() = Unit
        override fun getResults(): List<IPlatformComment> = comments
    }

/**
 * Fetch the comment at [key] together with a page of replies. Returns null when the thread does not
 * include the comment itself.
 */
suspend fun PolycentricAdapter.getThread(key: EventKey, contextUrl: String, omitLabels: List<String> = emptyList()): Pair<PolycentricPlatformComment, IPager<IPlatformComment>>? {
    val resp = client.getPostThread(key, omitLabels = omitLabels) ?: return null
    val comments = toComments(resp.thread, resp.event_hints) { contextUrl }
    val comment = comments.firstOrNull { it.key == key } ?: return null
    return comment to staticCommentPager(comments.filter { it.key != key })
}

/** Return a list of the active identity's comments, in reverse-chronological order. */
suspend fun PolycentricAdapter.getMyComments(): List<IPlatformComment> {
    val identity = client.activeIdentityKey ?: return emptyList()
    val resp = client.getIdentityFeed(identity) ?: return emptyList()
    return toComments(resp.event_bundles, resp.event_hints) { contextUrlOf(it) ?: "" }
}

/**
 * Publishes a new comment as a `Post` attributed to the video on the Harbor network. Then returns
 * a [PolycentricPlatformComment] object representing the new comment. The video is also included
 * as the post's link preview, filled out from [video].
 */
suspend fun PolycentricAdapter.postComment(videoUrl: String, video: AttributedContent?, text: String): PolycentricPlatformComment {
    requireActiveIdentity()
    val url = normalizeUrl(videoUrl)
    requirePublishableUrl(url)
    val content = Content(
        post = Post(
            text = text,
            links = listOf(linkPreview(url, video)),
            attributed_to = listOf(attributionLink(url, video?.title)),
        ),
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
    val resp = client.getAttributionFeed(attributionLink(url, null), forwardToken = cursor, omitLabels = omitLabels)
        ?: return emptyList<IPlatformComment>() to null
    val comments = toComments(resp.event_bundles, resp.event_hints) { url }
    val next = resp.page_info?.takeIf { it.has_next_page }?.end_cursor
    return comments to next
}

/**
 * Turn a batch of event bundles into comments. Author profiles and moderation labels are read from
 * the response's [hints]. Authors without a profile hint show as "Unknown".
 */
private fun PolycentricAdapter.toComments(bundles: List<EventBundle>, hints: List<EventHint>, contextUrl: (EventBundle) -> String): List<PolycentricPlatformComment> {
    val profiles = decodeProfiles(hints)
    val labelMap = decodeLabels(hints, profiles)
    return bundles.mapNotNull { toComment(contextUrl(it), it, labelMap) { identity -> authorLink(identity, profiles[identity]) } }
}

/**
 * Turn a generic event bundle retrieved from polycentric-core into a [PolycentricPlatformComment],
 * if it is a `Post` or `PostReply` event. The comment's author is produced by [author] from the
 * author's identity. Its labels are those in [labelMap] plus any moderation labels directly
 * attached to the post by the author.
 */
@VisibleForTesting
internal fun toComment(contextUrl: String, bundle: EventBundle, labelMap: Map<String, List<CommentLabel>> = emptyMap(), author: (String) -> PlatformAuthorLink): PolycentricPlatformComment? {
    val signed = bundle.signed_event ?: return null
    val event = Event.ADAPTER.decode(signed.event_bytes)
    val key = event.key ?: return null
    val contentBytes = bundle.serialized_content?.content_bytes ?: return null
    val post = Content.ADAPTER.decode(contentBytes).post ?: return null
    val meta = bundle.meta
    // Labels fetched from the post (attached by the author), rather than event hints
    val selfLabels = post.labels
        .filter { ModerationLabels.isModerationLabel(it) }
        .map { CommentLabel(it, key.identity) }
    return PolycentricPlatformComment(
        contextUrl = contextUrl,
        author = author(key.identity),
        msg = post.text.take(PolycentricPlatformComment.MAX_COMMENT_SIZE),
        rating = RatingLikeDislikes((meta?.upvote_count ?: 0).toLong(), (meta?.downvote_count ?: 0).toLong()),
        date = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.created_at), ZoneOffset.UTC),
        key = key,
        root = post.reply?.root,
        parent = post.reply?.parent,
        replyCount = meta?.reply_count ?: 0,
        labels = (labelMap[labelKeyOf(key)].orEmpty() + selfLabels).distinctBy { it.value to it.labeledBy },
    )
}

/**
 * Produce a new [PolycentricPlatformComment] with the given metadata and zero ratings and replies,
 * for when the client authors a new comment.
 */
private fun PolycentricAdapter.localComment(contextUrl: String, text: String, key: EventKey, root: EventKey?, parent: EventKey?) =
    PolycentricPlatformComment(
        contextUrl = contextUrl,
        author = fetchLocalAuthor(client.activeIdentityKey ?: key.identity),
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
 * event key and is signed by its labeler, whose name is looked up in [profiles]. We return a
 * mapping from keys to labels, deduplicated on (value, labeler).
 */
@VisibleForTesting
internal fun decodeLabels(hints: List<EventHint>, profiles: Map<String, ProfileUpdate> = emptyMap()): Map<String, List<CommentLabel>> {
    val map = HashMap<String, LinkedHashSet<CommentLabel>>()
    for (hint in hints) {
        val bundle = hint.event_bundle ?: continue
        val contentBytes = bundle.serialized_content?.content_bytes ?: continue
        val signed = bundle.signed_event ?: continue
        val labeler = runCatching { Event.ADAPTER.decode(signed.event_bytes).key?.identity }.getOrNull() ?: continue
        val labels = runCatching { Content.ADAPTER.decode(contentBytes).labels }.getOrNull() ?: continue
        val target = labels.event_key ?: continue
        val name = profiles[labeler]?.name
        map.getOrPut(labelKeyOf(target)) { LinkedHashSet() }
            .addAll(labels.label_values.map { CommentLabel(it, labeler, name) })
    }
    return map.mapValues { it.value.toList() }
}

/**
 * Decode the latest `ProfileUpdate` for each identity whose profile events are carried in a feed's
 * `event_hints` field. Feed responses include the latest profile event of every author they return.
 */
private fun PolycentricAdapter.decodeProfiles(hints: List<EventHint>): Map<String, ProfileUpdate> {
    val bundlesByIdentity = HashMap<String, MutableList<EventBundle>>()
    for (hint in hints) {
        val bundle = hint.event_bundle ?: continue
        val signed = bundle.signed_event ?: continue
        val key = Event.ADAPTER.decode(signed.event_bytes).key ?: continue
        if (key.collection != Collections.PROFILE) continue
        bundlesByIdentity.getOrPut(key.identity) { mutableListOf() }.add(bundle)
    }
    return bundlesByIdentity.mapNotNull { (identity, bundles) ->
        latestProfileUpdate(bundles)?.let { identity to it }
    }.toMap()
}

/** Stable string key for an [EventKey], used to match labels to comments. */
private fun labelKeyOf(k: EventKey): String =
    "${k.collection}|${k.identity}|${k.signed_by?.key_type}|${k.signed_by?.key?.hex()}|${k.sequence}"

// PlatformID Polycentric claim constant, used below
private const val POLYCENTRIC_CLAIM_TYPE = 25

/**
 * Fetches the latest profile info for [identity] from the local store, then creates a
 * [PlatformAuthorLink] for it. If no corresponding info is available, shows "Unknown" for the
 * profile.
 */
private fun PolycentricAdapter.fetchLocalAuthor(identity: String): PlatformAuthorLink {
    val bundles = client.listValidEvents(identity, Collections.PROFILE)
    return authorLink(identity, latestProfileUpdate(bundles))
}

/**
 * Create a Grayjay [PlatformAuthorLink] for a Harbor identity from its explicitly passed latest
 * profile update.
 */
private fun PolycentricAdapter.authorLink(identity: String, update: ProfileUpdate?): PlatformAuthorLink =
    PlatformAuthorLink(
        id = PlatformID("polycentric", authorUrl(identity), null, POLYCENTRIC_CLAIM_TYPE),
        name = update?.name ?: "Unknown",
        url = authorUrl(identity),
        thumbnail = bestBlobUrl(update?.avatar),
    )

/** Return the URL for the profile view of a Harbor identity. */
private fun authorUrl(identity: String) = "${PolycentricAdapter.WEB_BASE_URL}/$identity"
