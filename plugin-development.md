# Grayjay App Plugin Development Documentation

## Table of Contents

- [Introduction](#introduction)
- [Quick Start](#quick-start)
- [Configuration file](#configuration-file)
- [Example plugin](#example-plugin)
- [Plugin Deployment](#plugin-deployment)
- [Common Issues and Troubleshooting](#common-issues-and-troubleshooting)
- [Support and Contact](#support-and-contact)


## Introduction

Welcome to the Grayjay App plugin development documentation. Plugins are additional components that you can create to extend the functionality of the Grayjay app, for example a YouTube or Odysee plugin. This guide will provide an overview of Grayjay's plugin system and guide you through the steps necessary to create, test, debug, and deploy plugins.

## Quick Start

### Download GrayJay:

- Download the GrayJay app for Android [here](https://grayjay.app/).

### Enable GrayJay Developer Mode:

- Enable developer mode in the GrayJay app (not Android settings app) by tapping the “More” tab, tapping “Settings”, scrolling all the way to the bottom, and tapping the “Version Code” multiple times.

### Run the GrayJay DevServer:

- At the bottom of the Settings page in the GrayJay app, Click the purple “Developer Settings” button. Then click the “Start Server” button to start the DevServer.

  <img src="https://gitlab.futo.org/videostreaming/grayjay/uploads/07fc4919b0a8446c4cdf5335565c0611/image.png" width="200">

### Open the GrayJay DevServer on your computer:

- Open the Android settings app and search for “IP address”. The IP address should look like `192.168.X.X`.
- Open `http://<phone-ip>:11337/dev` in your web browser.
    
  <img src="https://gitlab.futo.org/videostreaming/grayjay/uploads/72885c3bc51b8efe9462ee68d47e3b51/image.png" width="600">

### Create and host your plugin:

- Clone the [Odysee plugin](https://gitlab.futo.org/videostreaming/plugins/odysee) as an example
- `cd` into the project folder and serve with `npx serve` (if you have [Node.js](https://nodejs.org/en/))
- `npx serve` should give you a Network url (not the localhost one) that looks like `http://192.168.X.X:3000`. Your config file URL will be something like `http://192.168.X.X:3000/OdyseeConfig.json`.
    
  <img src="https://gitlab.futo.org/videostreaming/grayjay/uploads/cc266da0a0b85c5770abca22c0b03b3b/image.png" width="600">

### Test your plugin:

- When the DevServer is open in your browser, enter the config file URL and click “Load Plugin”. This will NOT inject the plugin into the app, for that you need to click "Inject Plugin" on the Integration tab.
    
  <img src="https://gitlab.futo.org/videostreaming/grayjay/uploads/386a562f30a60cfcbb8a8a1345a788e5/image.png" width="600">
    
- On the Testing tab, you can individually test the methods in your plugin. To reload once you make changes on the plugin, click the top-right refresh button. *Note: While testing, the custom domParser package is overwritten with the browser's implementation, so it may behave differently than once it is loaded into the app.*
    
  <img src="https://gitlab.futo.org/videostreaming/grayjay/uploads/08830eb8cc56cc55ba445dd49db86235/image.png" width="600">
    
- On the Integration tab you can test your plugin end-to-end in the GrayJay app and monitor device logs. You can click "Inject Plugin" in order to inject the plugin into the app. Your plugin should show up on the Sources tab in the GrayJay app. If you make changes and want to reload the plugin, click "Inject Plugin" again.
    
  <img src="https://gitlab.futo.org/videostreaming/grayjay/uploads/74813fbf37dcfc63055595061e41c48b/image.png" width="600">

## Configuration file

Create a configuration file for your plugin.

`SomeConfig.json`
```js
{
	"name": "Some name",
	"description": "A description for your plugin",
	"author": "Your author name",
	"authorUrl": "https://yoursite.com",
	
    // The `sourceUrl` field should contain the URL where your plugin will be publically accessible in the future. This allows the app to scan this location to see if there are any updates available.
	"sourceUrl": "https://yoursite.com/SomeConfig.json",
	"repositoryUrl": "https://github.com/someuser/someproject",
	"scriptUrl": "./SomeScript.js",
	"version": 1,
	
	"iconUrl": "./someimage.png",

    // The `id` field should be a uniquely generated UUID like from [https://www.uuidgenerator.net/](https://www.uuidgenerator.net/). This will be used to distinguish your plugin from others.
	"id": "309b2e83-7ede-4af8-8ee9-822bc4647a24",
	
    // The `scriptSignature` and `scriptPublicKey` should be set whenever you deploy your script (NOT REQUIRED DURING DEVELOPMENT). The purpose of these fields is to verify that a plugin update was made by the same individual that developed the original plugin. This prevents somebody from hijacking your plugin without having access to your public private keypair. When this value is not present, you can still use this plugin, however the user will be informed that these values are missing and that this is a security risk. Here is an example script showing you how to generate these values.
	"scriptSignature": "<omitted>",
	"scriptPublicKey": "<omitted>",

    // The `packages` field allows you to specify which packages you want to use, current available packages are:
    // - `Http`: for performing HTTP requests
    // - `DOMParser`: for parsing a DOM
    // - `Utilities`: for various utility functions like generating UUIDs or converting to Base64
    // See documentation for more: https://gitlab.futo.org/videostreaming/grayjay/-/tree/master/docs/packages
	"packages": ["Http"],
	
	"allowEval": false,

    // The `allowUrls` field is allowed to be `everywhere`, this means that the plugin is allowed to access all URLs. However, this will popup a warning for the user that this is the case. Therefore, it is recommended to narrow the scope of the accessible URLs only to the URLs that you actually need. Other requests will be blocked. During development it can be convenient to use `everywhere`. Possible values are `odysee.com`, `api.odysee.com`, etc.
	"allowUrls": [
		"everywhere"
	]
}
```

You can use this script to generate the `scriptSignature` and `scriptPublicKey` fields above:

`sign-script.sh`
```sh
#!/bin/sh
#Example usage:
#cat script.js | sign-script.sh
#sh sign-script.sh script.js

#Set your key paths here
PRIVATE_KEY_PATH=~/.ssh/id_rsa
PUBLIC_KEY_PATH=~/.ssh/id_rsa.pub

PUBLIC_KEY_PKCS8=$(ssh-keygen -f "$PUBLIC_KEY_PATH" -e -m pkcs8 | tail -n +2 | head -n -1 | tr -d '\n')
echo "This is your public key: '$PUBLIC_KEY_PKCS8'"

if [ $# -eq 0 ]; then
  # No parameter provided, read from stdin
  DATA=$(cat)
else
  # Parameter provided, read from file
  DATA=$(cat "$1")
fi

SIGNATURE=$(echo -n "$DATA" | openssl dgst -sha512 -sign ~/.ssh/id_rsa | base64 -w 0)
echo "This is your signature: '$SIGNATURE'"
```

## Example plugin

Note that this is just a starting point, plugins can also implement optional features such as login, importing playlists/subscriptions, etc. For full examples please see in-house developed plugins (click [here](https://gitlab.futo.org/videostreaming/plugins)).

`SomeScript.js`
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

## Plugin Deployment

Here's how to deploy your plugin and distribute it to end-users:

1. Put the plugin config, script and icon on a publically accessible URL, this can be a self-hosted server or something like Github pages. The URL should match with the `sourceUrl` specified in the config.
2. Make sure to sign the script as mentioned earlier.
3. Make sure to increment the version.
4. Make a QR code for this plugin and distribute it to whoever wants to install it. In the Grayjay app they are able to click add source, scan the QR code and use your plugin.

## Common Issues and Troubleshooting

Here are some common issues that you might encounter and how to troubleshoot them:

### My plugin doesn't load when I enter the URL.

Double-check your URL to ensure it is correct. Make sure your server is running and accessible over the network. Check if there are any server-side issues or errors in the server logs.

### The functions in my plugin aren't returning the expected values.

Recheck your function implementation for any logical errors. Ensure that your functions are correctly parsing and manipulating the data. Use the 'Testing' tab to check the return values of your functions.

### The changes in my plugin are not being reflected.

Ensure you have clicked the top-right refresh button after making changes to your plugin. The system will not automatically pick up the changes.

### The plugin isn't behaving as expected when integrated in the app.

Ensure that your methods return the correct type of values. Test all the functionalities in different scenarios and handle edge cases properly.

### My plugin is not accessible publicly after deploying it.

Make sure your public server is correctly set up and your files are in the correct directory. If you're using a service like Github pages, make sure your repository is public and Github Pages is enabled.


### Users are unable to install my plugin using the QR code.

Ensure the QR code correctly points to the plugin config URL. The URL must be publicly accessible. Test the QR code yourself before distributing it.

### The plugin fails to load after signing the script and incrementing the version.

Make sure the signature is correctly generated and added. Also, ensure the version number in the config matches the new version number.

## Support and Contact

If you have any issues or need further assistance, feel free to reach out to us at:

https://chat.futo.org/login/