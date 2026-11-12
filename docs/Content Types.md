# Content Types
This page will cover the various types of content that are supported, and how to present them to Grayjay.

While Grayjay is primarily used for video, it supports various types of video, audio, but also text, images, and articles. In the future more types of content support might be added!

Content can be presented as a feed object, or a detail object. Feed objects are objects you see inside feeds and overviews such as the Home and Subscription tabs. Generally detail objects have an accompanying overview object.

Feed items are often returned in pagers, the following are some plugin methods that expect a pager of feed items:
```
source.getHome()
source.getChannelContents(...)
```
Content details are generally retrieved using
```
source.getContentDetails(url)
```

Note that all detail objects can be considered feed objects, but not the other way around. When you return a detail object in places where feed object is expected, and the user tries to open said item in a detail view, the ```GetContentDetail``` call is skipped, and the item is immediately shown without loading details.





# Feed Types
Feed types represent content in a feed or overview page. Most feed types have both a thumbnail and preview visualization, where they are displayed slightly differently. The plugin is not aware of these differences though.

## PlatformContent
All feed objects inherit PlatformContent, and always have the following properties:
```kotlin
class PlatformContent
{
	id: PlatformID,
	name: String,
	thumbnails: ThumbNails,
	author: PlatformAuthorLink,
	datetime: Int, // (UnixTimeStamp)
	url: String
}
```




## PlatformVideo
A feed object representing a video or audio.
*Usage:*
```javascript
new PlatformVideo({
	id: new PlatformID("SomePlatformName", "SomeId", config.id),
	name: "Some Video Name",
	thumbnails: new Thumbnails([
			new Thumbnail("https://.../...", 720),
			new Thumbnail("https://.../...", 1080),
		]),
	author: new PlatformAuthorLink(
		new PlatformID("SomePlatformName", "SomeAuthorID", config.id), 
		"SomeAuthorName", 
		"https://platform.com/your/channel/url", 
		"../url/to/thumbnail.png"),
	uploadDate: 1696880568,
	duration: 120,
	viewCount: 1234567,
	url: "https://platform.com/your/detail/url",
	isLive: false
});
```

## PlatformPost
A feed object representing a community post with text, and optionally images.

*Usage:*
```javascript
new PlatformPost({
	id: new PlatformID(config.name, item?.id, config.id),
	name: item?.attributes?.title,
	author: getPlatformAuthorLink(item, context),
	datetime: (Date.parse(item?.attributes?.published_at) / 1000),
	url: item?.attributes?.url,
	description: "Description of Post",
	images: ["../url/to/image1.png", "../url/to/image2.png"],
	thumbnails: new Thumbnails([
			new Thumbnail("https://.../...", 720),
			new Thumbnail("https://.../...", 1080),
		])
});
```


## PlatformNestedMediaContent
A feed object representing a link to a different item (often handled by a different plugin). 

An example is a Patreon video, that links to an unlisted Youtube video. If no plugin exists to handle the content, it will be opened in an in-app browser.

A nested item consists of an detail url and optional metadata such as name, description, thumbnails, etc.
*Usage:*
```javascript
new PlatformNestedMediaContent({
	id: new PlatformID("SomePlatformName", "SomeId", config.id),
	name: "Name of content link",
	author: new PlatformAuthorLink(
		new PlatformID("SomePlatformName", "SomeAuthorID", config.id), 
		"SomeAuthorName", 
		"https://platform.com/your/channel/url", 
		"../url/to/thumbnail.png"),,
	datetime: 1696880568,
	url: item?.attributes?.url,
	contentUrl: "https://someplatform.com/detail/url",
	contentName: "OptionalName",
	contentDescription: "OptionalDescription",
	contentProvider: "OptionalPlatformName",
	contentThumbnails: new Thumbnails([
			new Thumbnail("https://.../...", 720),
			new Thumbnail("https://.../...", 1080),
		])
});
```



# Detail Types
Detail types represent content on a detail page.

## PlatformVideoDetails

A detail object representing a video or audio. It inherits PlatformVideo.


### Usage:
```javascript
new PlatformVideoDetails({
	id: new PlatformID("SomePlatformName", "SomeId", config.id),
	name: "Some Video Name",
	thumbnails: new Thumbnails([
			new Thumbnail("https://.../...", 720),
			new Thumbnail("https://.../...", 1080),
		]),
	author: new PlatformAuthorLink(
		new PlatformID("SomePlatformName", "SomeAuthorID", config.id), 
		"SomeAuthorName", 
		"https://platform.com/your/channel/url", 
		"../url/to/thumbnail.png"),
	uploadDate: 1696880568,
	duration: 120,
	viewCount: 1234567,
	url: "https://platform.com/your/detail/url",
	isLive: false,

	description: "Some description",
	video: new VideoSourceDescriptor([]), //See sources
	live: null,
	rating: new RatingLikes(123),
	subtitles: []
});
```
### Live Streams
If your video is live, the ```isLive``` property should be ```true```, and the ```live``` property should be set to a ```HLSSource```, ```DashSource```, or equivelant.

### UnMuxed  and Audio-Only
If your content is either audio-only (eg. music), or has seperate video/audio tracks, you want to use ```UnMuxedVideoDescriptor``` instead of ```VideoSourceDescriptor```:
```javascript
new UnMuxedVideoDescriptor(
	[videoSource1, videoSource2, ...], 
	[audioSource1, audioSource2, ...]
);
```

### Sources
Inside a VideoDescriptor you need to provide an array of sources. 
Below you can find several source types that Grayjay supports:

**Standard Url Video/Audio**
These are videos available directly on a single url.
```javascript
new VideoUrlSource({
	width: 1920,
	height: 1080,
	container: "video/mp4",
	codec: "avc1.4d401e",
	name: "1080p30 mp4",
	bitrate: 188103,
	duration: 250,
	url: "https://platform.com/some/video/url.mp4"
});
//For audio:
new AudioUrlSource({
	container: "audio/mp4",
	codec: "mp4a.40.2",
	name: "mp4a.40.2",
	bitrate: 131294,
	duration: 250,
	url: "https://platform.com/some/video/url.mp4a",
	language: "Unknown"
});
```
**Range Url Video/Audio**
These are more complex url sources that require very specific range headers to function.  They require correct initialization and index positions.
These are converted to Dash manifests.

```javascript
new VideoUrlRangeSource({
	width: 1920,
	height: 1080,
	container: "video/mp4",
	codec: "avc1.4d401e",
	name: "1080p30 mp4",
	bitrate: 188103,
	duration: 250,
	url: "https://platform.com/some/video/url.mp4",
	itagId: 1234, //Optional
	initStart: 0,
	initEnd: 219,
	indexStart: 220,
	indexEnd: 791
});
//For Audio
new AudioUrlRangeSource({
	container: "audio/mp4",
	codec: "mp4a.40.2",
	name: "mp4a.40.2",
	bitrate: 131294,
	duration: 250,
	url: "https://platform.com/some/video/url.mp4a",
	language: "Unknown"
	itagId: 1234, //Optional
	initStart: 0,
	initEnd: 219,
	indexStart: 220,
	indexEnd: 791,
	audioChannels: 2
});
```

**HLSSource**
These are sources that are described in a HLS Manifest. 
```javascript
new HLSSource({
	name: "SomeName", //Optional
	duration: 250, //Optional
	url: "https://platform.com/some/hls/manifest.m3u8",
	priority: false, //Optional
	language: "Unknown" //Optional
});
``` 
Generally, HLS sources deprioritized in Grayjay. However if your platform requires HLS sources to be prioritized, you set ```priority``` to ```true```.

**DashSource**
These are sources that are described in a Dash Manifest. 
```javascript
new DashSource({
	name: "SomeName", //Optional
	duration: 250, //Optional
	url: "https://platform.com/some/dash/manifest.mpd"
});
```

## PlatformPostDetails
A detail object representing a text with optionally accompanying images. The text can be either raw text or html (and possibly in future markup).

### Usage:
```javascript
new PlatformPostDetails{
	id: new PlatformID(config.name, item?.id, config.id),
	name: item?.attributes?.title,
	author: getPlatformAuthorLink(item, context),
	datetime: (Date.parse(item?.attributes?.published_at) / 1000),
	url: item?.attributes?.url,
	description: "Description of Post",
	images: ["../url/to/image1.png", "../url/to/image2.png"],
	thumbnails: new Thumbnails([
			new Thumbnail("https://.../thumbnail1.png", 720),
			new Thumbnail("https://.../thumbnail2.png", 1080),
		]),
	rating: new RatingLikes(123),
	textType: Type.Text.Html/Raw/Markup,
	content: "Your post content in either raw, html, or in future markup."
});
```

# Request Modifiers
Sources support request modifiers that allow to modify HTTP headers before sending requests. This is useful when a source requires specific headers for authentication, content type specification, or other requirements.

## Using requestModifier property

```
new HLSSource({
    //Your other properties...
    requestModifier: {
        headers: {
            "Referer": "https://www.example.com/",
            "Origin": "https://www.example.com"
        }
    }
})
```

## Custom source implementation

```
class YourAudioSource extends AudioUrlRangeSource {
    constructor(obj) {
        super(obj);
    }

    getRequestModifier() {
        return new YourRequestModifier();
    }
}

class YourRequestModifier extends RequestModifier {
    constructor() {
        super();
    }
    modifyRequest(url, headers) {
        //modify headers

        return {
            url: url,
            headers: headers
        }
    }
}

```


