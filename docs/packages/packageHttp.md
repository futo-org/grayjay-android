# Package: Http
Package http is the main way for a plugin to make web requests, and is likely a package you will always need.
It offers several ways to make web requests as well as websocket connections.

Before you can use http you need to register it in your plugin config. See [Packages](_blank).

## Basic Info
Underneath the http package by default exist two web clients. An authenticated client and a unauthenticated client.
The authenticated client has will apply headers and cookies if the user is logged in with your plugin. 
See [Plugin Authentication](_blank).
These two clients are always available even when the user is not logged in, meaning it behaves similar to the unauthenticated client and can safely use it either way.

>:warning: **Requests are synchronous**  
>If you need to make multiple requests concurrently you **SHOULD** use the batch() method so that these requests happen concurrently. See [http.batch()](#batching).


## Common Usage

### Basic Requests
If you just want to make basic web requests you can use the following methods.

Methods:
```kotlin
http.GET(url: String, headers: Map<String, String>, useAuthClient: Boolean): BridgeHttpResponse;
http.POST(url: String, body: String, headers: Map<String, String>, useAuthClient: Boolean): BridgeHttpResponse;
http.request(method: String, url: String, headers: Map<String, String>, useAuthClient: Boolean): BridgeHttpResponse;
http.requestWithBody(method: String, url: String, body: String, headers: Map<String, String>, useAuthClient: Boolean): BridgeHttpResponse;
```
All usages of these methods are identical except for its parameters.
Example:
```javascript
const resp = http.GET("yourUrl", {your: "headers"}, false);
if(!resp.isOk) {
	//Handle your exception
	throw new ScriptException("Something went wrong while doing x with y");
}
//eg. for a json response
const resultData = JSON.parse(resp.body);
//Use your json result
```

### Web Socket
...

### Custom Clients
You might run into scenarios where you have to maintain multiple web clients with different cookies and configurations.
In this scenario you can declare a custom client that you can configure and which keeps its own cookies etc.
Method:
```kotlin
//newClient will copy the current state of either the unauthenticated or authenticated client.
//This includes auth cookies/headers.
http.newClient(useAuthClient: Boolean);
```
Custom clients support all methods that the root http package supports, except that the useAuthClient parameter is no longer available as it is implicit on your created client.
Example:
```
const myCustomClient = http.newClient(true);
const resp = myCustomClient.GET("yourUrl", {});
```
A custom client has the following customizing methods:
```kotlin
//Adds default headers that are added on every request
myClient.setDefaultHeaders(defaultHeaders: Map<String, String>);
//If cookies should be applied to requests
myClient.setDoApplyCookies(apply: Boolean);
//If cookies should be updated from request responses
myClient.setDoUpdateCookies(update: Boolean);
//If new cookies should be added if found in responses
myClient.setDoAllowNewCookies(allow: Boolean);
```

### Batching
All requests made by the HTTP package are synchronous, this is important to know and handle correctly. In the cases where you need to fire multiple requests without needing to know the response first you can use the .batch() method.
```javascript
http.batch(): BatchBuilder;
```
You can use batch to setup multiple http calls that will all be fired at the same time. The batch call supports all methods you can use on http as well. In addition to client-specific versions.
Example:
```javascript
const someCustomClient = http.newClient(false);

const responses = http.batch()
	.GET("someUrl", {})
	.POST("someUrl", "", {})
	.request("HEAD", {})
	.clientGET(someCustomClient, "someUrl", {})
	.execute();
//.execute() will run your requests. Responses are returned in their respective order.
const respGET = responses[0];
const respPOST = responses[1];
const respHEAD = responses[2];
const respClientGET = responses[3];
```
