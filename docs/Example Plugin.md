# Example plugin

Note that this is just a starting point, plugins can also implement optional features such as login, importing playlists/subscriptions, etc. For full examples please see in-house developed plugins (click [here](https://gitlab.futo.org/videostreaming/plugins)).

```js
source.enable = function (conf) {
    /**
     * @param conf: SourceV8PluginConfig (the SomeConfig.js)
     */
}

source.getHome = function(continuationToken) {
    /**
     * @param continuationToken: any?
     * @returns: VideoPager
     */
    const videos = []; // The results (PlatformVideo)
    const hasMore = false; // Are there more pages?
    const context = { continuationToken: continuationToken }; // Relevant data for the next page
    return new SomeHomeVideoPager(videos, hasMore, context);
}

source.searchSuggestions = function(query) {
    /**
     * @param query: string
     * @returns: string[]
     */

    const suggestions = []; //The suggestions for a specific search query
    return suggestions;
}

source.getSearchCapabilities = function() {
    //This is an example of how to return search capabilities like available sorts, filters and which feed types are available (see source.js for more details) 
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological, "^release_time"],
		filters: [
			{
				id: "date",
				name: "Date",
				isMultiSelect: false,
				filters: [
					{ id: Type.Date.Today, name: "Last 24 hours", value: "today" },
					{ id: Type.Date.LastWeek, name: "Last week", value: "thisweek" },
					{ id: Type.Date.LastMonth, name: "Last month", value: "thismonth" },
					{ id: Type.Date.LastYear, name: "Last year", value: "thisyear" }
				]
			},
		]
	};
}

source.search = function (query, type, order, filters, continuationToken) {
    /**
     * @param query: string
     * @param type: string
     * @param order: string
     * @param filters: Map<string, Array<string>>
     * @param continuationToken: any?
     * @returns: VideoPager
     */
    const videos = []; // The results (PlatformVideo)
    const hasMore = false; // Are there more pages?
    const context = { query: query, type: type, order: order, filters: filters, continuationToken: continuationToken }; // Relevant data for the next page
    return new SomeSearchVideoPager(videos, hasMore, context);
}

source.getSearchChannelContentsCapabilities = function () {
    //This is an example of how to return search capabilities on a channel like available sorts, filters and which feed types are available (see source.js for more details)
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological],
		filters: []
	};
}

source.searchChannelContents = function (url, query, type, order, filters, continuationToken) {
    /**
     * @param url: string
     * @param query: string
     * @param type: string
     * @param order: string
     * @param filters: Map<string, Array<string>>
     * @param continuationToken: any?
     * @returns: VideoPager
     */

    const videos = []; // The results (PlatformVideo)
    const hasMore = false; // Are there more pages?
    const context = { channelUrl: channelUrl, query: query, type: type, order: order, filters: filters, continuationToken: continuationToken }; // Relevant data for the next page
    return new SomeSearchChannelVideoPager(videos, hasMore, context);
}

source.searchChannels = function (query, continuationToken) {
    /**
     * @param query: string
     * @param continuationToken: any?
     * @returns: ChannelPager
     */

    const channels = []; // The results (PlatformChannel)
    const hasMore = false; // Are there more pages?
    const context = { query: query, continuationToken: continuationToken }; // Relevant data for the next page
    return new SomeChannelPager(channels, hasMore, context);
}

source.isChannelUrl = function(url) {
    /**
     * @param url: string
     * @returns: boolean
     */

	return REGEX_CHANNEL_URL.test(url);
}

source.getChannel = function(url) {
	return new PlatformChannel({
		//... see source.js for more details
	});
}

source.getChannelContents = function(url, type, order, filters, continuationToken) {
    /**
     * @param url: string
     * @param type: string
     * @param order: string
     * @param filters: Map<string, Array<string>>
     * @param continuationToken: any?
     * @returns: VideoPager
     */

    const videos = []; // The results (PlatformVideo)
    const hasMore = false; // Are there more pages?
    const context = { url: url, query: query, type: type, order: order, filters: filters, continuationToken: continuationToken }; // Relevant data for the next page
    return new SomeChannelVideoPager(videos, hasMore, context);
}

source.isContentDetailsUrl = function(url) {
    /**
     * @param url: string
     * @returns: boolean
     */

	return REGEX_DETAILS_URL.test(url);
}

source.getContentDetails = function(url) {
    /**
     * @param url: string
     * @returns: PlatformVideoDetails
     */

	return new PlatformVideoDetails({
		//... see source.js for more details
	});
}

source.getComments = function (url, continuationToken) {
    /**
     * @param url: string
     * @param continuationToken: any?
     * @returns: CommentPager
     */

    const comments = []; // The results (Comment)
    const hasMore = false; // Are there more pages?
    const context = { url: url, continuationToken: continuationToken }; // Relevant data for the next page
    return new SomeCommentPager(comments, hasMore, context);

}
source.getSubComments = function (comment) {
    /**
     * @param comment: Comment
     * @returns: SomeCommentPager
     */

	if (typeof comment === 'string') {
		comment = JSON.parse(comment);
	}

	return getCommentsPager(comment.context.claimId, comment.context.claimId, 1, false, comment.context.commentId);
}

class SomeCommentPager extends CommentPager {
    constructor(results, hasMore, context) {
        super(results, hasMore, context);
    }

    nextPage() {
        return source.getComments(this.context.url, this.context.continuationToken);
    }
}

class SomeHomeVideoPager extends VideoPager {
	constructor(results, hasMore, context) {
		super(results, hasMore, context);
	}
	
	nextPage() {
		return source.getHome(this.context.continuationToken);
	}
}

class SomeSearchVideoPager extends VideoPager {
	constructor(results, hasMore, context) {
		super(results, hasMore, context);
	}
	
	nextPage() {
		return source.search(this.context.query, this.context.type, this.context.order, this.context.filters, this.context.continuationToken);
	}
}

class SomeSearchChannelVideoPager extends VideoPager {
	constructor(results, hasMore, context) {
		super(results, hasMore, context);
	}
	
	nextPage() {
		return source.searchChannelContents(this.context.channelUrl, this.context.query, this.context.type, this.context.order, this.context.filters, this.context.continuationToken);
	}
}

class SomeChannelPager extends ChannelPager {
	constructor(results, hasMore, context) {
		super(results, hasMore, context);
	}
	
	nextPage() {
		return source.searchChannelContents(this.context.query, this.context.continuationToken);
	}
}

class SomeChannelVideoPager extends VideoPager {
	constructor(results, hasMore, context) {
		super(results, hasMore, context);
	}
	
	nextPage() {
		return source.getChannelContents(this.context.url, this.context.type, this.context.order, this.context.filters, this.context.continuationToken);
	}
}
```