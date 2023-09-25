# Grayjay App Plugin Development Documentation

## Table of Contents

- [Introduction](#introduction)
- [Grayjay App Overview](#grayjay-app-overview)
- [Plugin Development Overview](#plugin-development-overview)
- [Setting up the Development Environment](#setting-up-the-development-environment)
- [Using the Developer Interface](#using-the-developer-interface)
- [Plugin Deployment](#plugin-deployment)
- [Common Issues and Troubleshooting](#common-issues-and-troubleshooting)
- [Additional Resources](#additional-resources)
- [Support and Contact](#support-and-contact)


## Introduction

Welcome to the Grayjay App plugin development documentation. This guide will provide an overview of Grayjay's plugin system and guide you through the steps necessary to create, test, debug, and deploy plugins.

## Grayjay App Overview

Grayjay is a unique media application that aims to revolutionize the relationship between content creators and their audiences. By shifting the focus from platforms to creators, Grayjay democratizes the content delivery process, empowering creators to retain full ownership of their content and directly monetize their work.

For users, Grayjay offers a more privacy-focused and personalized content viewing experience. Rather than being manipulated by opaque algorithms, users can decide what they want to watch, thus enhancing their engagement and enjoyment of the content. 

Our ultimate goal is to create the best media app, merging content and features that users love with a strong emphasis on user and creator empowerment and privacy.

By developing Grayjay, we strive to make a stride toward a more open, interconnected, and equitable media ecosystem. This ecosystem fosters a thriving community of creators who are supported by their audiences, all facilitated through a platform that respects and prioritizes privacy and ownership.

## Plugin Development Overview

Plugins are additional components that you can create to extend the functionality of the Grayjay app.

## Setting up the Developer Environment

Before you start developing plugins, it is necessary to set up a suitable developer environment. Here's how to do it:

1. Create a plugin, the minimal starting point is the following.

`SomeConfig.js`
```json
{
	"name": "Some name",
	"description": "A description for your plugin",
	"author": "Your author name",
	"authorUrl": "https://yoursite.com",
	
	"sourceUrl": "https://yoursite.com/SomeConfig.json",
	"repositoryUrl": "https://github.com/someuser/someproject",
	"scriptUrl": "./SomeScript.js",
	"version": 1,
	
	"iconUrl": "./someimage.png",
	"id": "309b2e83-7ede-4af8-8ee9-822bc4647a24",
	
	"scriptSignature": "<ommitted>",
	"scriptPublicKey": "<ommitted>",
	"packages": ["Http"],
	
	"allowEval": false,
	"allowUrls": [
		"everywhere"
	]
}
```

The `sourceUrl` field should contain the URL where your plugin will be publically accessible in the future. This allows the app to scan this location to see if there are any updates available.

The `id` field should be a uniquely generated UUID like from [https://www.uuidgenerator.net/](https://www.uuidgenerator.net/). This will be used to distinguish your plugin from others.

The `allowUrls` field is allowed to be `everywhere`, this means that the plugin is allowed to access all URLs. However, this will popup a warning for the user that this is the case. Therefore, it is recommended to narrow the scope of the accessible URLs only to the URLs that you actually need. Other requests will be blocked. During development it can be convenient to use `everywhere`. Possible values are `odysee.com`, `api.odysee.com`, etc.

The `scriptSignature` and `scriptPublicKey` should be set whenever you deploy your script (NOT REQUIRED DURING DEVELOPMENT). The purpose of these fields is to verify that a plugin update was made by the same individual that developed the original plugin. This prevents somebody from hijacking your plugin without having access to your public private keypair. When this value is not present, you can still use this plugin, however the user will be informed that these values are missing and that this is a security risk. Here is an example script showing you how to generate these values.

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

The `packages` field allows you to specify which packages you want to use, current available packages are:
- `Http`: for performing HTTP requests (see [docs](TODO))
- `DOMParser`: for parsing a DOM (see [docs](TODO))
- `Utilities`: for various utility functions like generating random UUIDs or converting to Base64 (see [docs](TODO))

Note that this is just a starting point, plugins can also implement optional features such as login, importing playlists/subscriptions, etc. For full examples please see in-house developed plugins (click [here](TODO)).

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

2. Configure a web server to host the plugin. This can be something as simple as a NGINX server where you just place the files in the wwwroot or a simple dotnet/npm program that hosts the file for you. The important part is that the webserver and the phone are on the same network and the phone can access the files hosted by the development machine. An example of what this would look like is [here](https://plugins.grayjay.app/Odysee/OdyseeConfig.json). Alternatively, you could simply point to a Github/Gitlab raw file if you do not want to host it yourself. Note that the URL is not required to be publically accessible during development and HTTPS is NOT required.
3. Enable developer mode on the mobile application by going to settings, clicking on the version code multiple times. Once enabled, click on developer settings and then in the developer settings enable the webserver.
4. You are now able to access the developer interface on the phone via `http://<phone-ip>:11337/dev`.

## Using the Developer Interface

Once in the web portal you will see several tabs and a form allowing you to load a plugin.

1. Lets load your plugin. Take the URL that your plugin config is available at (like http://192.168.1.196:5000/Some/SomeConfig.json) and enter it in the `Plugin Config Json Url` field. Once entered, click load plugin.
*The package override domParser will override the domParser with the browser implementation. This is useful when you quickly want to iterate on plugins that parse the DOM, but it is less accurate to what the plugin will behave like once in-app.*
2. Once the plugin is loaded, you can click on the `Testing` tab and call individual methods. This allows you to quickly iterate, test methods and make sure they are returning the proper values. To reload once you make changes on the plugin, click the top-right refresh button.
3. After you are sure everything is working properly, click the `Integration` tab in order to perform integration testing on your plugin. You can click the `Inject Plugin` button in order to inject the plugin into the app. On the sources page in your app you should see your source and you are able to test it and make sure everything works. If you make changes and want to reload the plugin, click the `Inject Plugin` button again.

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

## Additional Resources

Here are some additional resources that might help you with your plugin development:

Please 

## Support and Contact

If you have any issues or need further assistance, feel free to reach out to us at:

https://chat.futo.org/login/