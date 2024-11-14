# PlatformPlayer

The FUTO media app endeavours creating infrastructure for creators to have their content hosted by someone else but at the same time having creators retain full ownership of their content. We want creators to feel like they are publishing to the world, and we want multiple indexers competing with each other to do a good job connecting consumers to creators and their content.

One part of the solution is to create an application that allows users to search through all available media websites and giving creators the tools for direct monetization of their content by allowing users to directly donate to the content creator.

FUTO is an organization dedicated to developing, both through in-house engineering and investment,
technologies that frustrate centralization and industry consolidation.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/video.png" height="700" /></b></td>
    <td><b style="font-size:30px"><img src="images/video-details.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Video</td>
    <td>Video (details)</td>
 </tr>
</table>

## What does the app do?

The FUTO media app is a player that exposes multiple video websites as sources in the app. These sources can be easily configured and third-party sources can also manually be added. This is done through the sources UI.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/source.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Sources</td>
 </tr>
</table>

Additional sources can also be installed. These sources are JavaScript sources, created and maintained by the community.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/source-install.png" height="700" /></b></td>
    <td><b style="font-size:30px"><img src="images/source-settings.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Install a new source</td>
    <td>Configure a source</td>
 </tr>
</table>

Once the sources are configured, the combined results will be shown throughout the app. The core features of the app will be highlighted below.

### Searching

When a user enters a search term into the search bar,  the query is posted to the underlying platforms and a list of results that are ranked by relevance is returned. The search functionality of the app allows users to search multiple sources at once, allowing users to discover a wider range of content that is relevant to their interests.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/search-list.png" height="700" /></b></td>
    <td><b style="font-size:30px"><img src="images/search-preview.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Search (list)</td>
    <td>Search (preview)</td>
 </tr>
</table>

### Channels

Channels allow users to view the creators content, read more about them or support them by donating, purchasing from their store or buying a membership. The FUTO media app only links to other stores and the app does not play an intermediate role in the actual purchase process. This way, creators can directly monetize their own content in the way they like.

Creators are able to configure their profile using NeoPass.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/channel.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Channel</td>
 </tr>
</table>

### Feed

Subscriptions are a way for users to keep up with the latest videos and content from their favorite creators. The creators you are subscribed to are shown in the creators tab. In the future we will add both creator search and suggested creators.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/creators.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Creators</td>
 </tr>
</table>

When you subscribe to a creator, you'll be able to find new videos uploaded by them in the subscriptions tab.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/subscriptions-list.png" height="700" /></b></td>
    <td><b style="font-size:30px"><img src="images/subscriptions-preview.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Subscriptions (list)</td>
    <td>Subscriptions (preview)</td>
 </tr>
</table>

Additionally there is also the "Home" feed which is based purely on recommendations by the underlying platforms. Also here we hope to offer user-picked recommendation engines in the future.

## Settings

The app offers a lot of settings customizing how the app looks and feels. An example of this is the  background behaviour, do you wish to have it use picture in picture, background play or shut off entirely. Another example configuration option is choosing between list views or video previews.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/settings.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Settings</td>
 </tr>
</table>

### Playlists

Playlists allow you to make a collection of videos that you can create and customize to your liking. When you add videos to a playlist, they're grouped together in a single location, making it easy for you to find and watch all of the videos in the playlist in sequence.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/playlists.png" height="700" /></b></td>
    <td><b style="font-size:30px"><img src="images/playlist.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Playlists</td>
    <td>Playlist</td>
 </tr>
</table>

Playlists can also be downloaded in their entirety.

### Downloads

Both individual videos and playlists can be downloaded for local, offline playback. You can watch downloaded videos any time, even if you do not have an active internet connection.

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/downloads.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Downloads</td>
 </tr>
</table>

### Casting

The app can also cast to a big screen using any of the supported protocols (FastCast, ChromeCast, AirPlay). Not all casting protocols support all features. As a rule of thumb feature-wise FastCast > ChromeCast > AirPlay.

For more information about casting please click [here](./docs/casting.md).

<table border="0">
 <tr>
    <td><b style="font-size:30px"><img src="images/casting.png" height="700" /></b></td>
 </tr>
 <tr>
    <td>Casting</td>
 </tr>
</table>

### Commenting and rating

The app can also cast to comment and rate. For more information about this please click [here](./docs/polycentric.md).

### Creator Linking

The app can also cast to link channels together. For more information about this please click [here](./docs/linking.md).

### Migration and recommendations

Sources have the ability to login, allowing you to use features that require credentials like importing your playlists, importing your subscriptions or have personalized recommendations. Some platforms may require a membership to work at all.

In the future we hope to offer users the choice of their desired recommendation engine and have multiple competing recommendation engines for different audiences.

## Building

1. Download a copy of the repository.
2. Open the project in Android Studio: Once the repository is cloned, you can open it in Android Studio by selecting "Open an Existing Project" from the welcome screen and navigating to the directory where you cloned the repository.
3. Open the terminal in Android Studio by clicking on the terminal icon on bottom left and run the following command:

```sh
git submodule update --init --recursive
```

3. Build the project: With the project open in Android Studio, you can build it by selecting "Build > Make Project" from the main menu. This will compile the code and generate an APK file that you can install on your device or emulator.
4. Run the project: To run the project, select "Run > Run 'app'" from the main menu. This will launch the app on your device or emulator, allowing you to test it and make any necessary changes.

## Contributing

Please see [CONTRIBUTION.md](./CONTRIBUTION.md).

## CI/CD

Tests will always run and are required to pass before a merge request is allowed to be merged. The build/deploy CI/CD steps will only be triggered by a tag on the master branch.

### Making a new build

Create a tag on the master branch, incrementing the last version number by 1 (for example `25` to `26`).

Click on the CI/CD tab, you should now see the tests and build are in progress. If the build succeeds the last step will become available. The last step is a manual action which can be triggered by clicking the run button on the action. This action will deploy the build to all users using the app through auto-update.

## Documentation

The documentation can be found [here](https://gitlab.futo.org/videostreaming/documents/-/wikis/API-Overview).
