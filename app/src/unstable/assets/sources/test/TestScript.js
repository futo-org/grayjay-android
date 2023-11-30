var config = {};

//Source Methods
source.enable = function(conf){
	config = conf ?? {};
	//log(config);
}
source.getHome = function() {
    return new ContentPager([
        source.getContentDetails("whatever")
    ]);
};

//Video
source.isContentDetailsUrl = function(url) {
	return REGEX_DETAILS_URL.test(url)
};
source.getContentDetails = function(url) {
	return new PlatformVideoDetails({
		id: new PlatformID("Test", "Something", config.id),
		name: "Test Video",
		thumbnails: new Thumbnails([]),
		author: new PlatformAuthorLink(new PlatformID("Test", "TestID", config.id),
			"TestAuthor",
			"None",
			""),
		datetime: parseInt(new Date().getTime() / 1000),
		duration: 0,
		viewCount: 0,
		url: "",
		isLive: false,
		description: "",
		rating: new RatingLikes(0),
		video: new VideoSourceDescriptor([
			new HLSSource({
				name: "HLS",
				url: "",
				duration: 0,
				priority: true
			})
		])
	});
};

log("LOADED");