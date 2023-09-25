
declare class ScriptException extends Error {
    constructor(type: string, msg: string);
}
declare class TimeoutException extends ScriptException {
    constructor(msg: string);
}
declare class UnavailableException extends ScriptException {
    constructor(msg: string);
}
declare class ScriptImplementationException extends ScriptException {
    constructor(msg: string);
}

declare class Thumbnails {
    constructor(thumbnails: Thumbnail[])
}
declare class Thumbnail {
    constructor(url, quality) {
        this.url = url ?? ""; //string
        this.quality = quality ?? 0; //integer
    }
}

declare class PlatformID {
    constructor(platform: string, id: string, pluginId: string, claimType: int = 0, claimFieldType: integer = -1);
}

declare class ResultCapabilities {
    constructor(types: string[], sorts: string[], filters: FilterGroup[])
}
declare class FilterGroup {
    constructor(name: string, filters: string[], isMultiSelect: boolean, id: string);
}
declare class FilterCapability {
    constructor(name: string, value: string, id: string);
}


declare class PlatformAuthorLink {
    constructor(id: PlatformID, name: string, url: string, thumbnail: string, subscribers: integer?);
}

declare interface PlatformContentDef {
    id: PlatformID,
    name: string,
    author: PlatformAuthorLink,
    datetime: integer,
    url: string
}
declare interface PlatformNestedMediaContentDef extends PlatformContentDef {
    contentUrl: string,
    contentName: string?,
    contentDescription: string?,
    contentProvider: string?,
    contentThumbnails: Thumbnails
}
declare class PlatformNestedMediaContent {
    constructor(obj: PlatformNestedMediaContentDef);
}

declare interface PlatformVideoDef extends PlatformContentDef {
    thumbnails: Thumbnails,
    author: PlatformAuthorLink,

    duration: int,
    viewCount: long,
    isLive: boolean
}
declare interface PlatformContent {}

declare class PlatformVideo implements PlatformContent {
    constructor(obj: PlatformVideoDef);
}


declare interface PlatformVideoDetailsDef extends PlatformVideoDef {
    description: string,
    video: VideoSourceDescriptor,
    live: SubtitleSource[],
    rating: IRating
}
declare class PlatformVideoDetails extends PlatformVideo {
    constructor(obj: PlatformVideoDetailsDef);
}

declare class PlatformPostDef extends PlatformContentDef {
    thumbnails: string[],
    images: string[],
    description: string
}
declare class PlatformPost extends PlatformContent {
    constructor(obj: PlatformPostDef)
}

declare class PlatformPostDetailsDef extends PlatformPostDef {
    rating: IRating,
    textType: int,
    content: String
}
declare class PlatformPostDetails extends PlatformPost {
    constructor(obj: PlatformPostDetailsDef);
}


//Sources
declare interface IVideoSourceDescriptor {}

declare interface MuxVideoSourceDescriptorDef {
    isUnMuxed: boolean,
    videoSources: VideoSource[]
}
declare class MuxVideoSourceDescriptor implements IVideoSourceDescriptor {
    constructor(obj: VideoSourceDescriptorDef);
}

declare interface UnMuxVideoSourceDescriptorDef {
    isUnMuxed: boolean,
    videoSources: VideoSource[]
}
class UnMuxVideoSourceDescriptor implements IVideoSourceDescriptor {
    constructor(videoSourcesOrObj: VideoSource[], audioSources: AudioSource[]);
    constructor(videoSourcesOrObj: UnMuxVideoSourceDescriptorDef);
}

declare interface IVideoSource {

}
declare interface IAudioSource {

}
interface VideoUrlSourceDef implements IVideoSource {
    width: integer,
    height: integer,
    container: string,
    codec: string,
    name: string,
    bitrate: integer,
    duration: integer,
    url: string
}
class VideoUrlSource {
    constructor(obj: VideoUrlSourceDef);

    getRequestModifier(): RequestModifier?;
}
interface VideoUrlRangeSourceDef extends VideoUrlSource {
    itagId: integer,
    initStart: integer,
    initEnd: integer,
    indexStart: integer,
    indexEnd: integer,
}
class VideoUrlRangeSource extends VideoUrlSource {
    constructor(obj: YTVideoSourceDef);
}
interface AudioUrlSourceDef {
    name: string,
    bitrate: integer,
    container: string,
    codecs: string,
    duration: integer,
    url: string,
    language: string
}
class AudioUrlSource implements IAudioSource {
    constructor(obj: AudioUrlSourceDef);

    getRequestModifier(): RequestModifier?;
}
interface IRequest {
    url: string,
    headers: Map<string, string>
}
interface IRequestModifierDef {
    allowByteSkip: boolean
}
class RequestModifier {
    constructor(obj: IRequestModifierDef) { }

    modifyRequest(url: string, headers: Map<string, string>): IRequest;
}
interface AudioUrlRangeSourceDef extends AudioUrlSource {
    itagId: integer,
    initStart: integer,
    initEnd: integer,
    indexStart: integer,
    indexEnd: integer,
    audioChannels: integer
}
class AudioUrlRangeSource extends AudioUrlSource {
    constructor(obj: AudioUrlRangeSourceDef);
}
interface HLSSourceDef {
    name: string,
    duration: integer,
    url: string
}
class HLSSource implements IVideoSource {
    constructor(obj: HLSSourceDef);
}
interface DashSourceDef {
    name: string,
    duration: integer,
    url: string
}
class DashSource implements IVideoSource {
    constructor(obj: DashSourceDef)
}

//Channel
interface PlatformChannelDef {
    id: PlatformID,
    name: string,
    thumbnail: string,
    banner: string,
    subscribers: integer,
    description: string,
    url: string,
    links: Map<string>?
}
class PlatformChannel {
    constructor(obj: PlatformChannelDef);
}

//Ratings
interface IRating {
    type: integer
}
declare class RatingLikes implements IRating {
    constructor(likes: integer);
}
declare class RatingLikesDislikes implements IRating {
    constructor(likes: integer, dislikes: integer);
}
declare class RatingScaler implements IRating {
    constructor(value: double);
}

declare interface CommentDef {
    contextUrl: string,
    author: PlatformAuthorLink,
    message: string,
    rating: IRating,
    date: long,
    replyCount: int,
    context: any
}
declare class PlatformComment {
    constructor(obj: CommentDef);
}



declare class LiveEventPager {
    nextRequest = 4000;

    constructor(results: LiveEvent[], hasMore: boolean, context: any);

    hasMorePagers(): boolean
    nextPage(): LiveEventPager; //Could be self
}

class LiveEvent {
    type: String
}
declare class LiveEventComment extends LiveEvent {
    constructor(name: string, message: string, thumbnail: string?, colorName: string?, badges: string[]);
}
declare class LiveEventEmojis extends LiveEvent {
    constructor(name: Map<string>);
}
declare class LiveEventDonation extends LiveEvent  {
    constructor(amount: integer, name: string, message: string, thumbnail: string?, expire: Int, colorDonation: string?);
}
declare class LiveEventViewCount extends LiveEvent {
    constructor(viewCount: integer);
}
declare class LiveEventRaid extends LiveEvent {
    constructor(targetUrl: string, targetName: string, targetThumbnail: string);
}



//Pagers
declare class ContentPager {
    constructor(results: PlatformContent[], hasMore: boolean);

    hasMorePagers(): boolean
    nextPage(): VideoPager; //Could be self
}
declare class VideoPager {
    constructor(results: PlatformVideo[], hasMore: boolean);

    hasMorePagers(): boolean
    nextPage(): VideoPager; //Could be self
}
declare class ChannelPager {
    constructor(results: PlatformChannel[], hasMore: boolean);

    hasMorePagers(): boolean;
    nextPage(): ChannelPager; //Could be self
}
declare class CommentPager {
    constructor(results: PlatformComment[], hasMore: boolean);

    hasMorePagers(): boolean
    nextPage(): CommentPager; //Could be self
}

interface Map<T> {
    [Key: string]: T;
}

//To override by plugin

interface Source {
    getHome(): VideoPager;

    enable(config: SourceConfig, settings: Any, savedState: string?);
    disable();

    saveState(): string;

    searchSuggestions(query: string): string[];
    search(query: string, type: string, order: string, filters): ContentPager;
    getSearchCapabilities(): ResultCapabilities

    //Optional
    searchChannelContents(channelUrl: string, query: string, type: string, order: string, filters): ContentPager;
    //Optional
    getSearchChannelContentsCapabilities(): ResultCapabilities;

    //Optional
    getChannelUrlByClaim(claimType: int, values: Map<Int, String>)

    isChannelUrl(url: string): boolean;
    getChannel(url: string): PlatformChannel;

    getChannelContents(url: string, type: string, order: string, filters): ContentPager;
    getChannelCapabilities(): ResultCapabilities;

    isContentDetailsUrl(url: string): boolean;
    getContentDetails(url: string): PlatformVideoDetails;

    getLiveEvents(url: string): LiveEventPager;

    //Optional
    getComments(url: string): CommentPager;
    //Optional
    getSubComments(comment: PlatformComment): CommentPager;

    //Optional
    getUserSubscriptions(): string[];
    //Optional
    getUserPlaylists(): string[];

    //Optional
    isPlaylistUrl(url: string): boolean;
    //Optional
    getPlaylist(url): string[];
}

const source: Source;
