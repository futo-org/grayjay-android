var IS_TESTING = false;

let Type = {
    Source: {
        Dash: "DASH",
        HLS: "HLS",
        STATIC: "Static"
    },
    Feed: {
        Videos: "VIDEOS",
        Streams: "STREAMS",
        Mixed: "MIXED",
        Live: "LIVE",
        Subscriptions: "SUBSCRIPTIONS"
    },
    Order: {
        Chronological: "CHRONOLOGICAL"
    },
    Date: {
        LastHour: "LAST_HOUR",
        Today: "TODAY",
        LastWeek: "LAST_WEEK",
        LastMonth: "LAST_MONTH",
        LastYear: "LAST_YEAR"
    },
    Duration: {
        Short: "SHORT",
        Medium: "MEDIUM",
        Long: "LONG"
    },
    Text: {
        RAW: 0,
        HTML: 1,
        MARKUP: 2
    },
    Chapter: {
        NORMAL: 0,

        SKIPPABLE: 5,
        SKIP: 6,
        SKIPONCE: 7
    }
};

let Language = {
    UNKNOWN: "Unknown",
    ARABIC: "ar",
    SPANISH: "es",
    FRENCH: "fr",
    HINDI: "hi",
    INDONESIAN: "id",
    KOREAN: "ko",
    PORTUGUESE: "pt",
    PORTBRAZIL: "pt",
    RUSSIAN: "ru",
    THAI: "th",
    TURKISH: "tr",
    VIETNAMESE: "vi",
    ENGLISH: "en"
}

class ScriptException extends Error {
    constructor(type, msg) {
        if(arguments.length == 1) {
            super(arguments[0]);
            this.plugin_type = "ScriptException";
            this.message = arguments[0];
        }
        else {
            super(msg);
            this.plugin_type = type ?? ""; //string
            this.msg = msg ?? ""; //string
        }
    }
}
class ScriptLoginRequiredException extends ScriptException {
    constructor(msg) {
        super("ScriptLoginRequiredException", msg);
    }
}
class LoginRequiredException extends ScriptException {
    constructor(msg) {
        super("ScriptLoginRequiredException", msg);
    }
}
class CaptchaRequiredException extends Error {
    constructor(url, body) {
        super(JSON.stringify({ 'plugin_type': 'CaptchaRequiredException', url, body }));
        this.plugin_type = "CaptchaRequiredException";
        this.url = url;
        this.body = body;
    }
}
class CriticalException extends ScriptException {
    constructor(msg) {
        super("CriticalException", msg);
    }
}
class UnavailableException extends ScriptException {
    constructor(msg) {
        super("UnavailableException", msg);
    }
}
class AgeException extends ScriptException {
    constructor(msg) {
        super("AgeException", msg);
    }
}
class TimeoutException extends ScriptException {
    constructor(msg) {
        super(msg);
        this.plugin_type = "ScriptTimeoutException";
    }
}
class ScriptImplementationException extends ScriptException {
    constructor(msg) {
        super(msg);
        this.plugin_type = "ScriptImplementationException";
    }
}

class Thumbnails {
    constructor(thumbnails) {
        this.sources = thumbnails ?? []; // Thumbnail[]
    }
}
class Thumbnail {
    constructor(url, quality) {
        this.url = url ?? ""; //string
        this.quality = quality ?? 0; //integer
    }
}

class PlatformID {
    constructor(platform, id, pluginId, claimType, claimFieldType) {
        this.platform = platform ?? ""; //string
        this.pluginId = pluginId; //string
        this.value = id; //string
        this.claimType = claimType ?? 0; //int
        this.claimFieldType = claimFieldType ?? -1; //int
    }
}

class ResultCapabilities {
    constructor(types, sorts, filters) {
        this.types = types ?? [];
        this.sorts = sorts ?? [];
        this.filters = filters ?? [];
    }
}
class FilterGroup {
    constructor(name, filters, isMultiSelect, id) {
        if(!name) throw new ScriptException("No name for filter group");
        if(!filters) throw new ScriptException("No filter provided");

        this.name = name
        this.filters = filters
        this.isMultiSelect = isMultiSelect;
        this.id = id;
    }
}
class FilterCapability {
    constructor(name, value, id) {
        if(!name) throw new ScriptException("No name for filter");
        if(!value) throw new ScriptException("No filter value");

        this.name = name;
        this.value = value;
        this.id = id;
    }
}


class PlatformAuthorLink {
    constructor(id, name, url, thumbnail, subscribers, membershipUrl) {
        this.id = id ?? PlatformID(); //PlatformID
        this.name = name ?? ""; //string
        this.url = url ?? ""; //string
        this.thumbnail = thumbnail; //string
        if(subscribers)
            this.subscribers = subscribers;
        if(membershipUrl)
            this.membershipUrl = membershipUrl ?? null; //string (for backcompat)
    }
}
class PlatformAuthorMembershipLink {
    constructor(id, name, url, thumbnail, subscribers, membershipUrl) {
        this.id = id ?? PlatformID(); //PlatformID
        this.name = name ?? ""; //string
        this.url = url ?? ""; //string
        this.thumbnail = thumbnail; //string
        if(subscribers)
            this.subscribers = subscribers;
        if(membershipUrl)
            this.membershipUrl = membershipUrl ?? null; //string
    }
}
class PlatformContent {
    constructor(obj, type) {
        this.contentType = type;
        obj = obj ?? {};
        this.id = obj.id ?? PlatformID();   //PlatformID
        this.name = obj.name ?? ""; //string
        this.thumbnails = obj.thumbnails ?? new Thumbnails([]); //Thumbnail[]
        this.author = obj.author; //PlatformAuthorLink
        this.datetime = obj.datetime ?? obj.uploadDate ?? 0; //OffsetDateTime (Long)
        this.url = obj.url ?? ""; //String
    }
}
class PlatformContentDetails {
    constructor(type) {
        this.contentType = type;
    }
}
class PlatformNestedMediaContent extends PlatformContent {
    constructor(obj) {
        super(obj, 11);
        obj = obj ?? {};
        this.contentUrl = obj.contentUrl ?? "";
        this.contentName = obj.contentName;
        this.contentDescription = obj.contentDescription;
        this.contentProvider = obj.contentProvider;
        this.contentThumbnails = obj.contentThumbnails ?? new Thumbnails();
    }
}
class PlatformLockedContent extends PlatformContent {
    constructor(obj) {
        super(obj, 70);
        obj = obj ?? {};
        this.contentName = obj.contentName;
        this.contentThumbnails = obj.contentThumbnails ?? new Thumbnails();
        this.unlockUrl = obj.unlockUrl ?? "";
        this.lockDescription = obj.lockDescription;
    }
}
class PlatformVideo extends PlatformContent {
    constructor(obj) {
        super(obj, 1);
        obj = obj ?? {};
        this.plugin_type = "PlatformVideo";
        this.shareUrl = obj.shareUrl;

        this.duration = obj.duration ?? -1; //Long
        this.viewCount = obj.viewCount ?? -1; //Long

        this.isLive = obj.isLive ?? false; //Boolean
    }
}
class PlatformVideoDetails extends PlatformVideo {
    constructor(obj) {
        super(obj);
        obj = obj ?? {};
        this.plugin_type = "PlatformVideoDetails";

        this.description = obj.description ?? "";//String
        this.video = obj.video ?? {}; //VideoSourceDescriptor
        this.dash = obj.dash ?? null; //DashSource, deprecated
        this.hls = obj.hls ?? null; //HLSSource, deprecated
        this.live = obj.live ?? null; //VideoSource

        this.rating = obj.rating ?? null; //IRating
        this.subtitles = obj.subtitles ?? [];
    }
}

class PlatformPost extends PlatformContent {
    constructor(obj) {
        super(obj, 2);
        obj = obj ?? {};
        this.plugin_type = "PlatformPost";
        this.thumbnails = obj.thumbnails ?? [];
        this.images = obj.images ?? [];
        this.description = obj.description ?? "";
    }
}
class PlatformPostDetails extends PlatformPost {
    constructor(obj) {
        super(obj);
        obj = obj ?? {};
        this.plugin_type = "PlatformPostDetails";
        this.rating = obj.rating ?? new RatingLikes(-1);
        this.textType = obj.textType ?? 0;
        this.content = obj.content ?? "";
    }
}

class PlatformArticleDetails extends PlatformContent {
    constructor(obj) {
        super(obj, 3);
        obj = obj ?? {};
        this.plugin_type = "PlatformArticleDetails";
        this.rating = obj.rating ?? new RatingLikes(-1);
        this.summary = obj.summary ?? "";
        this.segments = obj.segments ?? [];
        this.thumbnails = obj.thumbnails ?? new Thumbnails([]);
    }
}
class ArticleSegment {
    constructor(type) {
        this.type = type;
    }
}
class ArticleTextSegment extends ArticleSegment {
    constructor(content, textType) {
        super(1);
        this.textType = textType;
        this.content = content;
    }
}
class ArticleImagesSegment extends ArticleSegment {
    constructor(images) {
        super(2);
        this.images = images;
    }
}
class ArticleNestedSegment extends ArticleSegment {
    constructor(nested) {
        super(9);
        this.nested = nested;
    }
}


//Sources
class VideoSourceDescriptor {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "MuxVideoSourceDescriptor";
        this.isUnMuxed = false;

        if(obj.constructor === Array)
            this.videoSources = obj;
        else
            this.videoSources = obj.videoSources ?? [];
    }
}
class UnMuxVideoSourceDescriptor {
    constructor(videoSourcesOrObj, audioSources) {
        videoSourcesOrObj = videoSourcesOrObj ?? {};
        this.plugin_type = "UnMuxVideoSourceDescriptor";
        this.isUnMuxed = true;

        if(videoSourcesOrObj.constructor === Array) {
            this.videoSources = videoSourcesOrObj;
            this.audioSources = audioSources;
        }
        else {
            this.videoSources = videoSourcesOrObj.videoSources ?? [];
            this.audioSources = videoSourcesOrObj.audioSources ?? [];
        }
    }
}

class VideoUrlSource {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "VideoUrlSource";
        this.width = obj.width ?? 0;
        this.height = obj.height ?? 0;
        this.container = obj.container ?? "";
        this.codec = obj.codec ?? "";
        this.name = obj.name ?? "";
        this.bitrate = obj.bitrate ?? 0;
        this.duration = obj.duration ?? 0;
        this.url = obj.url;
        if(obj.requestModifier)
            this.requestModifier = obj.requestModifier;
    }
}
class VideoUrlRangeSource extends VideoUrlSource {
    constructor(obj) {
        super(obj);
        this.plugin_type = "VideoUrlRangeSource";

		this.itagId = obj.itagId ?? null;
		this.initStart = obj.initStart ?? null;
		this.initEnd = obj.initEnd ?? null;
		this.indexStart = obj.indexStart ?? null;
		this.indexEnd = obj.indexEnd ?? null;
    }
}
class AudioUrlSource {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "AudioUrlSource";
        this.name = obj.name ?? "";
        this.bitrate = obj.bitrate ?? 0;
        this.container = obj.container ?? "";
        this.codec = obj.codec ?? "";
        this.duration = obj.duration ?? 0;
        this.url = obj.url;
        this.language = obj.language ?? Language.UNKNOWN;
        if(obj.requestModifier)
            this.requestModifier = obj.requestModifier;
    }
}
class AudioUrlWidevineSource extends AudioUrlSource {
    constructor(obj) {
        super(obj);
        this.plugin_type = "AudioUrlWidevineSource";

        this.bearerToken = obj.bearerToken;
        this.licenseUri = obj.licenseUri;
    }
}
class AudioUrlRangeSource extends AudioUrlSource {
    constructor(obj) {
        super(obj);
        this.plugin_type = "AudioUrlRangeSource";

		this.itagId = obj.itagId ?? null;
		this.initStart = obj.initStart ?? null;
		this.initEnd = obj.initEnd ?? null;
		this.indexStart = obj.indexStart ?? null;
		this.indexEnd = obj.indexEnd ?? null;
		this.audioChannels = obj.audioChannels ?? 2;
    }
}
class HLSSource {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "HLSSource";
        this.name = obj.name ?? "HLS";
        this.duration = obj.duration ?? 0;
        this.url = obj.url;
        this.priority = obj.priority ?? false;
        if(obj.language)
            this.language = obj.language;
        if(obj.requestModifier)
            this.requestModifier = obj.requestModifier;
    }
}
class DashSource {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "DashSource";
        this.name = obj.name ?? "Dash";
        this.duration = obj.duration ?? 0;
        this.url = obj.url;
        if(obj.language)
            this.language = obj.language;
        if(obj.requestModifier)
            this.requestModifier = obj.requestModifier;
    }
}
class DashManifestRawSource {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "DashRawSource";
        this.name = obj.name ?? "";
        this.bitrate = obj.bitrate ?? 0;
        this.container = obj.container ?? "";
        this.codec = obj.codec ?? "";
        this.duration = obj.duration ?? 0;
        this.url = obj.url;
        this.language = obj.language ?? Language.UNKNOWN;
        if(obj.requestModifier)
            this.requestModifier = obj.requestModifier;
    }
}

class DashManifestRawAudioSource {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "DashRawAudioSource";
        this.name = obj.name ?? "";
        this.bitrate = obj.bitrate ?? 0;
        this.container = obj.container ?? "";
        this.codec = obj.codec ?? "";
        this.duration = obj.duration ?? 0;
        this.url = obj.url;
        this.language = obj.language ?? Language.UNKNOWN;
        this.manifest = obj.manifest ?? null;
        if(obj.requestModifier)
            this.requestModifier = obj.requestModifier;
    }
}


class RequestModifier {
    constructor(obj) {
        obj = obj ?? {};
        this.allowByteSkip = obj.allowByteSkip; //Kinda deprecated.. wip
    }
}

//Channel
class PlatformChannel {
    constructor(obj) {
        obj = obj ?? {};
        this.plugin_type = "PlatformChannel";
        this.id = obj.id ?? ""; //string
        this.name = obj.name ?? ""; //string
        this.thumbnail = obj.thumbnail; //string
        this.banner = obj.banner; //string
        this.subscribers = obj.subscribers ?? 0; //integer
        this.description = obj.description; //string
        this.url = obj.url ?? ""; //string
        this.urlAlternatives = obj.urlAlternatives ?? [];
        this.links = obj.links ?? {  } //Map<string,string>
    }
}

//Playlist
class PlatformPlaylist extends PlatformContent {
    constructor(obj) {
        super(obj, 4);
        this.plugin_type = "PlatformPlaylist";
        this.videoCount = obj.videoCount ?? -1;
        this.thumbnail = obj.thumbnail;
    }
}
class PlatformPlaylistDetails extends PlatformPlaylist  {
    constructor(obj) {
        super(obj);
        this.plugin_type = "PlatformPlaylistDetails";
        this.contents = obj.contents;
    }
}


//Ratings
class RatingLikes {
    constructor(likes) {
        this.type = 1;
        this.likes = likes;
    }
}
class RatingLikesDislikes {
    constructor(likes,dislikes) {
        this.type = 2;
        this.likes = likes;
        this.dislikes = dislikes;
    }
}
class RatingScaler {
    constructor(value) {
        this.type = 3;
        this.value = value;
    }
}

class PlatformComment {
    constructor(obj) {
        this.plugin_type = "Comment";
        this.contextUrl = obj.contextUrl ?? "";
        this.author = obj.author ?? new PlatformAuthorLink(null, "", "", null);
        this.message = obj.message ?? "";
        this.rating = obj.rating ?? new RatingLikes(0);
        this.date = obj.date ?? 0;
        this.replyCount = obj.replyCount ?? 0;
        this.context = obj.context ?? {};
    }
}

//Temporary backwards compat
class Comment extends PlatformComment {
    constructor(obj) {
        super(obj);
    }
}

class PlaybackTracker {
    constructor(interval) {
        this.nextRequest = interval ?? 10*1000;
    }
    setProgress(seconds) {
        throw new ScriptImplementationException("Missing required setProgress(seconds) on PlaybackTracker");
    }
}

class LiveEventPager {
    constructor(results, hasMore, context) {
        this.plugin_type = "LiveEventPager";
        this.results = results ?? [];
        this.hasMore = hasMore ?? false;
        this.context = context ?? {};
        this.nextRequest = 4000;
    }

    hasMorePagers() { return this.hasMore; }
    nextPage() { return new Pager([], false, this.context) }
}

class LiveEvent {
    constructor(type) {
        this.type = type;
    }
}
class LiveEventComment extends LiveEvent {
    constructor(name, message, thumbnail, colorName, badges) {
        super(1);
        this.name = name;
        this.message = message;
        this.thumbnail = thumbnail;
        this.colorName = colorName;
        this.badges = badges;
    }
}
class LiveEventEmojis extends LiveEvent {
    constructor(emojis) {
        super(4);
        this.emojis = emojis;
    }
}
class LiveEventDonation extends LiveEvent {
    constructor(amount, name, message, thumbnail, expire, colorDonation) {
        super(5);
        this.amount = amount;
        this.name = name;
        this.message = message ?? "";
        this.thumbnail = thumbnail;
        this.expire = expire;
        this.colorDonation = colorDonation;
    }
}
class LiveEventViewCount extends LiveEvent {
    constructor(viewCount) {
        super(10);
        this.viewCount = viewCount;
    }
}
class LiveEventRaid extends LiveEvent {
    constructor(targetUrl, targetName, targetThumbnail) {
        super(100);
        this.targetUrl = targetUrl;
        this.targetName = targetName;
        this.targetThumbnail = targetThumbnail;
    }
}

//Pagers
class ContentPager {
    constructor(results, hasMore, context) {
        this.plugin_type = "ContentPager";
        this.results = results ?? [];
        this.hasMore = hasMore ?? false;
        this.context = context ?? {};
    }

    hasMorePagers() { return this.hasMore; }
    nextPage() { return new ContentPager([], false, this.context) }
}
class VideoPager {
    constructor(results, hasMore, context) {
        this.plugin_type = "VideoPager";
        this.results = results ?? [];
        this.hasMore = hasMore ?? false;
        this.context = context ?? {};
    }

    hasMorePagers() { return this.hasMore; }
    nextPage() { return new VideoPager([], false, this.context) }
}
class ChannelPager {
    constructor(results, hasMore, context) {
        this.plugin_type = "ChannelPager";
        this.results = results ?? [];
        this.hasMore = hasMore ?? false;
        this.context = context ?? {};
    }

    hasMorePagers() { return this.hasMore; }
    nextPage() { return new Pager([], false, this.context) }
}
class PlaylistPager {
    constructor(results, hasMore, context) {
        this.plugin_type = "PlaylistPager";
        this.results = results ?? [];
        this.hasMore = hasMore ?? false;
        this.context = context ?? {};
    }

    hasMorePagers() { return this.hasMore; }
    nextPage() { return new Pager([], false, this.context) }
}
class CommentPager {
    constructor(results, hasMore, context) {
        this.plugin_type = "CommentPager";
        this.results = results ?? [];
        this.hasMore = hasMore ?? false;
        this.context = context ?? {};
    }

    hasMorePagers() { return this.hasMore; }
    nextPage() { return new Pager([], false, this.context) }
}

function throwException(type, message) {
    throw new Error("V8EXCEPTION:" + type + "-" + message);
}

let plugin = {
    config: {},
    settings: {}
};

//To override by plugin
const source = {
    getHome() { return new ContentPager([], false, {}); },

    enable(config){  },
    disable() {},

    searchSuggestions(query){ return []; },
    getSearchCapabilities(){ return { types: [], sorts: [] }; },
    search(query, type, order, filters){ return new ContentPager([], false, {}); }, //TODO
    //OPTIONAL getSearchChannelContentsCapabilities(){ return { types: [], sorts: [] }; },
    //OPTIONAL searchChannelContents(channelUrl, query, type, order, filters){ return new Pager([], false, {}); }, //TODO

    isChannelUrl(url){ return false; },
    getChannel(url){ return null; },
    getChannelCapabilities(){ return { types: [], sorts: [] }; },
    getChannelContents(url, type, order, filters) { return new ContentPager([], false, {}); },

    isContentDetailsUrl(url){ return false; },
    getContentDetails(url){  }, //TODO

    //OPTIONAL getComments(url){ return new Pager([], false, {}); }, //TODO
    //OPTIONAL getSubComments(comment){ return new Pager([], false, {}); }, //TODO

    //OPTIONAL getSubscriptionsUser(){ return []; },
    //OPTIONAL getPlaylistsUser(){ return []; }
};

function parseSettings(settings) {
    if(!settings)
        return {};
    let newSettings = {};
    for(let key in settings) {
        if(typeof settings[key] == "string")
            newSettings[key] = JSON.parse(settings[key]);
        else
            newSettings[key] = settings[key];
    }
    return newSettings;
}

function log(str) {
    if(str) {
        console.log(str);
        if(typeof str == "string")
            bridge.log(str);
        else
            bridge.log(JSON.stringify(str, null, 4));
    }
}

function encodePathSegment(segment) {
    return encodeURIComponent(segment).replace(/[!'()*]/g, function (c) {
        return '%' + c.charCodeAt(0).toString(16);
    });
}

class URLSearchParams {
    constructor(init) {
        this._entries = {};
        if (typeof init === 'string') {
            if (init !== '') {
                init = init.replace(/^\?/, '');
                const attributes = init.split('&');
                let attribute;
                for (let i = 0; i < attributes.length; i++) {
                    attribute = attributes[i].split('=');
                    this.append(decodeURIComponent(attribute[0]), (attribute.length > 1) ? decodeURIComponent(attribute[1]) : '');
                }
            }
        }
        else if (init instanceof URLSearchParams) {
            init.forEach((value, name) => {
                this.append(value, name);
            });
        }
    }
    append(name, value) {
        value = value.toString();
        if (name in this._entries) {
            this._entries[name].push(value);
        }
        else {
            this._entries[name] = [value];
        }
    }
    delete(name) {
        delete this._entries[name];
    }
    get(name) {
        return (name in this._entries) ? this._entries[name][0] : null;
    }
    getAll(name) {
        return (name in this._entries) ? this._entries[name].slice(0) : [];
    }
    has(name) {
        return (name in this._entries);
    }
    set(name, value) {
        this._entries[name] = [value.toString()];
    }
    forEach(callback) {
        let entries;
        for (let name in this._entries) {
            if (this._entries.hasOwnProperty(name)) {
                entries = this._entries[name];
                for (let i = 0; i < entries.length; i++) {
                    callback.call(this, entries[i], name, this);
                }
            }
        }
    }
    keys() {
        const items = [];
        this.forEach((value, name) => { items.push(name); });
        return createIterator(items);
    }
    values() {
        const items = [];
        this.forEach((value) => { items.push(value); });
        return createIterator(items);
    }
    entries() {
        const items = [];
        this.forEach((value, name) => { items.push([value, name]); });
        return createIterator(items);
    }
    toString() {
        let searchString = '';
        this.forEach((value, name) => {
            if (searchString.length > 0)
                searchString += '&';
            searchString += encodeURIComponent(name) + '=' + encodeURIComponent(value);
        });
        return searchString;
    }
}


var __REGEX_SPACE_CHARACTERS = /<%= spaceCharacters %>/g;
var __btoa_TABLE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
function btoa(input) {
	input = String(input);
	if (/[^\0-\xFF]/.test(input)) {
		// Note: no need to special-case astral symbols here, as surrogates are
		// matched, and the input is supposed to only contain ASCII anyway.
		error(
			'The string to be encoded contains characters outside of the ' +
			'Latin1 range.'
		);
	}
	var padding = input.length % 3;
	var output = '';
	var position = -1;
	var a;
	var b;
	var c;
	var buffer;
	// Make sure any padding is handled outside of the loop.
	var length = input.length - padding;

	while (++position < length) {
		// Read three bytes, i.e. 24 bits.
		a = input.charCodeAt(position) << 16;
		b = input.charCodeAt(++position) << 8;
		c = input.charCodeAt(++position);
		buffer = a + b + c;
		// Turn the 24 bits into four chunks of 6 bits each, and append the
		// matching character for each of them to the output.
		output += (
			__btoa_TABLE.charAt(buffer >> 18 & 0x3F) +
			__btoa_TABLE.charAt(buffer >> 12 & 0x3F) +
			__btoa_TABLE.charAt(buffer >> 6 & 0x3F) +
			__btoa_TABLE.charAt(buffer & 0x3F)
		);
	}

	if (padding == 2) {
		a = input.charCodeAt(position) << 8;
		b = input.charCodeAt(++position);
		buffer = a + b;
		output += (
			__btoa_TABLE.charAt(buffer >> 10) +
			__btoa_TABLE.charAt((buffer >> 4) & 0x3F) +
			__btoa_TABLE.charAt((buffer << 2) & 0x3F) +
			'='
		);
	} else if (padding == 1) {
		buffer = input.charCodeAt(position);
		output += (
			__btoa_TABLE.charAt(buffer >> 2) +
			__btoa_TABLE.charAt((buffer << 4) & 0x3F) +
			'=='
		);
	}

	return output;
};
function atob(input) {
	input = String(input)
		.replace(__REGEX_SPACE_CHARACTERS, '');
	var length = input.length;
	if (length % 4 == 0) {
		input = input.replace(/==?$/, '');
		length = input.length;
	}
	if (
		length % 4 == 1 ||
		// http://whatwg.org/C#alphanumeric-ascii-characters
		/[^+a-zA-Z0-9/]/.test(input)
	) {
		error(
			'Invalid character: the string to be decoded is not correctly encoded.'
		);
	}
	var bitCounter = 0;
	var bitStorage;
	var buffer;
	var output = '';
	var position = -1;
	while (++position < length) {
		buffer = __btoa_TABLE.indexOf(input.charAt(position));
		bitStorage = bitCounter % 4 ? bitStorage * 64 + buffer : buffer;
		// Unless this is the first of a group of 4 characters…
		if (bitCounter++ % 4) {
			// …convert the first 8 bits to a single ASCII character.
			output += String.fromCharCode(
				0xFF & bitStorage >> (-2 * bitCounter & 6)
			);
		}
	}
	return output;
};
