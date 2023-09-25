package com.futo.platformplayer

import android.util.Log
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnail
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.futo.platformplayer.serializers.FlexibleBooleanSerializer
import com.futo.platformplayer.stores.FragmentedStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Test
import java.time.OffsetDateTime

class RequireMigrationTests {
    /* THESE TESTS SIMPLY EXIST TO WARN THE DEVELOPER THAT THEIR CHANGE WILL CAUSE A MIGRATION FOR ALL USERS */
    private val serializedString = "{\"id\":{\"platform\":\"Youtube\",\"value\":\"video123\"},\"name\":\"Sample Video\",\"thumbnails\":{\"sources\":[{\"url\":\"thumbnail1.jpg\",\"quality\":1},{\"url\":\"thumbnail2.jpg\",\"quality\":2}]},\"author\":{\"id\":{\"platform\":\"Youtube\",\"value\":\"video123\"},\"name\":\"John Doe\",\"url\":\"https://example.com/author/johndoe\",\"thumbnail\":\"thumbnail.jpg\",\"subscribers\":1000},\"datetime\":1689168120,\"url\":\"https://example.com/videos/video123\",\"shareUrl\":\"https://example.com/share/video123\",\"duration\":180000,\"viewCount\":1000}";

    @Test
    fun `test if deserialize works`() {
        Serializer.json.decodeFromString<SerializedPlatformVideo>(serializedString);
        Log.i("RequireMigrationTests", createVideo().toJson())
    }

    private fun createVideo(): SerializedPlatformVideo {
        val platformId = PlatformID("Youtube", "video123")
        val name = "Sample Video"
        val thumbnails = createThumbnails()
        val author = createAuthorLink()
        val datetime = OffsetDateTime.now()
        val url = "https://example.com/videos/video123"
        val shareUrl = "https://example.com/share/video123"
        val duration = 180000L
        val viewCount = 1000L

        return SerializedPlatformVideo(
            platformId,
            name,
            thumbnails,
            author,
            datetime,
            url,
            shareUrl,
            duration,
            viewCount
        )
    }

    private fun createThumbnails(): Thumbnails {
        val thumbnail1 = Thumbnail("thumbnail1.jpg", 1)
        val thumbnail2 = Thumbnail("thumbnail2.jpg", 2)
        val thumbnailsArray = arrayOf(thumbnail1, thumbnail2)

        return Thumbnails(thumbnailsArray)
    }

    private fun createAuthorLink(): PlatformAuthorLink {
        val id = PlatformID("Youtube", "video123")
        val name = "John Doe"
        val url = "https://example.com/author/johndoe"
        val thumbnail = "thumbnail.jpg"
        val subscribers = 1000L

        return PlatformAuthorLink(id, name, url, thumbnail, subscribers)
    }


    @Test
    fun `Test flexible boolean deserializer`() {
        val str = "{ \"int1\": 1, \"int2\": 0, \"boolean1\": true, \"boolean2\": false, \"string1\": \"1\", \"string2\": \"0\", \"string3\": \"true\", \"string4\": \"false\" }";

        val test = Json.decodeFromString<FlexibleBooleanTestClass>(str);
        Assert.assertEquals(true, test.int1);
        Assert.assertEquals(false, test.int2);
        Assert.assertEquals(true, test.boolean1);
        Assert.assertEquals(false, test.boolean2);
        Assert.assertEquals(true, test.string1);
        Assert.assertEquals(false, test.string2);
        Assert.assertEquals(true, test.string3);
        Assert.assertEquals(false, test.string4);
    }

    @Serializable
    private class FlexibleBooleanTestClass {

        @Serializable(with = FlexibleBooleanSerializer::class)
        var int1: Boolean = false;

        @Serializable(with = FlexibleBooleanSerializer::class)
        var int2: Boolean = false;

        @Serializable(with = FlexibleBooleanSerializer::class)
        var boolean1: Boolean = false;
        @Serializable(with = FlexibleBooleanSerializer::class)
        var boolean2: Boolean = false;

        @Serializable(with = FlexibleBooleanSerializer::class)
        var string1: Boolean = false;
        @Serializable(with = FlexibleBooleanSerializer::class)
        var string2: Boolean = false;
        @Serializable(with = FlexibleBooleanSerializer::class)
        var string3: Boolean = false;
        @Serializable(with = FlexibleBooleanSerializer::class)
        var string4: Boolean = false;
    }
}