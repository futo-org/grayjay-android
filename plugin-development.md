# Grayjay App Plugin Development Documentation

## Table of Contents

- [Introduction](#introduction)
- [Quick Start](#quick-start)
- [Configuration file](#configuration-file)
- [Packages](#packages)
- [Authentication](#authentication)
- [Content Types](#content-types)
- [Example plugin](#example-plugin)
- [Pagination](#pagination)
- [Script signing](#script-signing)
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
- `cd` into the project folder and serve with `npx serve` (if you have [Node.js](https://nodejs.org/en/)) or any other HTTP Server you desire.
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
	
    // See the "Script Signing" section for details
	"scriptSignature": "<omitted>",
	"scriptPublicKey": "<omitted>",

    // See the "Packages" section for details, currently allowed values are: ["Http", "DOMParser", "Utilities"]
	"packages": ["Http"],
	
	"allowEval": false,

    // The `allowUrls` field is allowed to be `everywhere`, this means that the plugin is allowed to access all URLs. However, this will popup a warning for the user that this is the case. Therefore, it is recommended to narrow the scope of the accessible URLs only to the URLs that you actually need. Other requests will be blocked. During development it can be convenient to use `everywhere`. Possible values are `odysee.com`, `api.odysee.com`, etc.
	"allowUrls": [
		"everywhere"
	]
}
```

## Packages

The `packages` field allows you to specify which packages you want to use, current available packages are:
- `Http`: for performing HTTP requests (see [docs](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/docs/packages/packageHttp.md))
- `DOMParser`: for parsing a DOM (no docs yet, see [source code](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/app/src/main/java/com/futo/platformplayer/engine/packages/PackageDOMParser.kt))
- `Utilities`: for various utility functions like generating UUIDs or converting to Base64 (no docs yet, see [source code](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/app/src/main/java/com/futo/platformplayer/engine/packages/PackageUtilities.kt))

## Authentication

Authentication is sometimes required by plugins to access user data and premium content, for example on YouTube or Patreon.

See [Authentication.md](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/docs/Authentication.md)

## Content Types

Docs for data structures like PlatformVideo your plugin uses to communicate with the GrayJay app.

See [Content Types.md](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/docs/Content%20Types.md)

## Example plugin

See the example plugin to better understand the plugin API e.g. `getHome` and `search`.

See [Example Plugin.md](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/docs/Example%20Plugin.md)

## Pagination

Plugins use "Pagers" to send paginated data to the GrayJay app.

See [Pagers.md](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/docs/Pagers.md)

## Script signing

When you deploy your plugin, you'll need to add code signing for security.

See [Script Signing.md](https://gitlab.futo.org/videostreaming/grayjay/-/blob/master/docs/Script%20Signing.md)

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
