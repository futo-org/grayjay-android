# Pagers
Within Grayjay there are several situations where Pagers are used to communicate multiple pages of data back to the app. Some examples are home feed, channel contents, comments, live events, etc.

All these pagers have exact same layout and usage, with only some very specific cases where additional functionality is exposed.

Some example of base pagers that exist:

**ContentPager** for feed objects
**ChannelPager** for channels
**PlaylistPager** for playlists
**CommentPager** for comments

An example of a pager implementation is as follows:
```javascript
class MyPlatformContentPager extends ContentPager {
	constructor(someInfo) {
		super([], true); //Alternatively, pass first page results in []
		this.someInfo = someInfo;
	}

	nextPage() {
		const myNewResults = //Fetch your next page
		this.results = myNewResults;
		this.hasMore = true; //Or false if last page
	}
}
```
You can also choose to return an entirely new pager object in nextPage, but this is **NOT RECOMMENDED** as it generates a new object for every page. But can be convenient in some recursive situations.
```
nextPage() {
   return new MyPlatformContentPager(...);
}
```
In this case the new pager will replace the parent.

If you ever just want to return an empty pager without any results, you can choose to directly use the base pagers as follows:
```
return new ContentPager([], false);
```
Which effectively says *"First page is empty, and no next page"*.
