package com.futo.platformplayer.polycentric

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.futo.polycentric.core.Collections
import polycentric.v2.Content
import polycentric.v2.Event
import polycentric.v2.EventBundle
import polycentric.v2.EventHint
import polycentric.v2.EventKey
import polycentric.v2.Labels
import polycentric.v2.Post
import polycentric.v2.ProfileUpdate
import polycentric.v2.SerializedContent
import polycentric.v2.SignedEvent

class PolycentricCommentLabelsTests {
    @Before
    fun setUp() = ModerationLabels.overrideValuesForTest(listOf("hate", "self-harm", "sexually-suggestive", "sexually-explicit", "violence"))

    @After
    fun tearDown() = ModerationLabels.overrideValuesForTest(null)

    private var nextSequence = 1L

    private fun key(identity: String, collection: Int = Collections.FEED) = EventKey(collection = collection, identity = identity, sequence = nextSequence++)

    private fun bundle(key: EventKey, content: Content) = EventBundle(
        signed_event = SignedEvent(event_bytes = Event(key = key, created_at = 0L).encodeByteString()),
        serialized_content = SerializedContent(content_bytes = content.encodeByteString()),
    )

    private fun labelHint(labeler: String, target: EventKey, vararg values: String) =
        EventHint(event_bundle = bundle(key(labeler, collection = 7), Content(labels = Labels(event_key = target, label_values = values.toList()))))

    private fun author(identity: String) = PlatformAuthorLink(PlatformID("polycentric", identity), identity, identity)

    @Test
    fun `labels from two labelers on one target are kept and duplicates dropped`() {
        val post = key("alice")
        val labels = decodeLabels(
            listOf(
                labelHint("mod1", post, "hate", "violence"),
                labelHint("mod1", post, "hate"),
                labelHint("mod2", post, "hate"),
            ),
            mapOf("mod1" to ProfileUpdate(name = "Harbor Moderation")),
        )

        assertEquals(
            listOf(
                CommentLabel("hate", "mod1", "Harbor Moderation"),
                CommentLabel("violence", "mod1", "Harbor Moderation"),
                CommentLabel("hate", "mod2", null),
            ),
            labels.values.single(),
        )
    }

    @Test
    fun `labels targeting a key outside the feed do not attach to any comment`() {
        val post = key("alice")
        val other = key("bob")
        val labelMap = decodeLabels(listOf(labelHint("mod", other, "hate")))
        val comment = toComment("url", bundle(post, Content(post = Post(text = "hi"))), labelMap, ::author)!!
        assertEquals(emptyList<CommentLabel>(), comment.labels)
    }

    @Test
    fun `self-labels are merged and non-moderation self-labels ignored`() {
        val post = key("alice")
        val labelMap = decodeLabels(listOf(labelHint("mod", post, "violence")))
        val content = Content(post = Post(text = "hi", labels = listOf("harbor", "sexually-explicit", "violence")))
        val comment = toComment("url", bundle(post, content), labelMap, ::author)!!

        assertEquals(
            listOf(
                CommentLabel("violence", "mod"),
                CommentLabel("sexually-explicit", "alice"),
                CommentLabel("violence", "alice"),
            ),
            comment.labels,
        )
    }

    @Test
    fun `non-moderation hint labels are kept on the comment`() {
        val post = key("alice")
        val labelMap = decodeLabels(listOf(labelHint("mod", post, "harbor", "hate")))
        val comment = toComment("url", bundle(post, Content(post = Post(text = "hi"))), labelMap, ::author)!!
        assertEquals(listOf(CommentLabel("harbor", "mod"), CommentLabel("hate", "mod")), comment.labels)
    }
}
