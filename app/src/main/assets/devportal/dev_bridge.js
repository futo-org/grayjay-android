
//These calls are purposely synchronized to emulate behavior within V8
function syncGET(url, headers) {
    if(!headers) headers = {};
    const req = new XMLHttpRequest();
    req.open("GET", url, false);
    for (const [key, value] of Object.entries(headers))
        req.setRequestHeader(key, value);
    req.send(null);

    if(req.status >= 200 && req.status < 300)
        return req.response;
    else
        throw "Request [" + req.status + "]\n" + req.response;
}
function syncPOST(url, headers, body) {
    if(!headers) headers = {};
    const req = new XMLHttpRequest();
    req.open("POST", url, false);
    for (const [key, value] of Object.entries(headers))
        req.setRequestHeader(key, value);
    req.send(body);

    if(req.status >= 200 && req.status < 300)
        return req.response;
    else
        throw "Request [" + req.status + "]\n" + req.response;
}


class RemoteObject {
    constructor(remoteObj) {
        Object.assign(this, remoteObj);

        if(this.__methods) {
            const me = this;
            for(let i = 0; i < this.__methods.length; i++) {
                const methodName = this.__methods[i];

                this[methodName] = function() {
                    try{
                        return remoteCall(me.__id, methodName, Array.from(arguments));
                    }
                    catch(ex) {
                        if(ex.indexOf("[400]") > 0 && ex.indexOf("does not exist") > 0 && ex.indexOf(me.__id) > 0) {
                            deletePackage(me.__id);
                        }
                        else throw ex;
                    }
                };
            }
        }
        if(this.__props) {
            const me = this;
            for(let i = 0; i < this.__props.length; i++) {
                const propName = this.__props[i];

                Object.defineProperty(this, propName, {
                    get() {
                        try{
                            return remoteProp(me.__id, propName);
                        }
                        catch(ex) {
                            if(ex.indexOf("[400]") > 0 && ex.indexOf("does not exist") > 0 && ex.indexOf(me.__id) > 0) {
                                deletePackage(me.__id);
                            }
                            else throw ex;
                        }
                    }
                });
            }
        }
    }
}
const excludedCallsFromLogs = ["isLoggedIn"];
function remoteCall(objID, methodName, args) {
    for(let i = 0; i < args.length; i++) {
        let arg = args[i];
        if(typeof(arg) == "object") {
            switch(arg.constructor.name) {
                case "Uint8Array":
                    args[i] = [...arg]
                    break;
            }
        }
    }


    if(excludedCallsFromLogs.indexOf(methodName) < 0)
        console.log("Remote Call on [" + objID + "]." + methodName + "(...)", args);
    const result = pluginRemoteCall(objID, methodName, args);
    return wrapRemoteObject(result);
}
function remoteProp(objID, propName) {
    console.log("Remote Prop on [" + objID + "]." + propName);
    const result = pluginRemoteProp(objID, propName);
    return wrapRemoteObject(result);
}
function wrapRemoteObject(result) {
    if(Array.isArray(result)) {
        if(result.length == 0)
            return [];
        const firstItem = result[0];
        if(typeof firstItem === "object")
            return result.map(x=>new RemoteObject(x));
        else
            return result;
    }
    else if(typeof result === "object")
        return new RemoteObject(result);
    return result;
}

//These override implementations by packages if enabled
var packageOverrides = {
    domParser() {
        return {
            parseFromString(str) {
                return new DOMParser().parseFromString(str, "text/html");
            }
        }
    }
};
var packageOverridesEnabled = {};
for(override in packageOverrides)
    packageOverridesEnabled[override] = false;


var _loadedPackages = {

};
function clearPackages() {
    _loadedPackages = {};
}
function deletePackage(id) {
    for(let key in _loadedPackages) {
        if(_loadedPackages[key]?.__id == id)
            _loadedPackages[key] = undefined;
    }
}
function applyPackages(packages) {
    _loadedPackages = {};
    for(let i = 0; i < packages.length; i++) {
        const package = packages[i];
        delete window[package];
        Object.defineProperty(window, package, {
            configurable: true,
            get() {
                if(!_loadedPackages[package]) {
                    if(packageOverridesEnabled[package]) {
                        _loadedPackages[package] = packageOverrides[package]();
                        console.log("LOADED EMULATED PACKAGE [" + package + "]", _loadedPackages[package]);
                    }
                    else {
                        _loadedPackages[package] = new RemoteObject(pluginGetPackage(package));
                        console.log("LOADED REMOTE PACKAGE [" + package + "]", _loadedPackages[package]);
                        applyAdditionalOverrides(package, _loadedPackages[package]);
                    }
                }
                return _loadedPackages[package];
            }
        });
    }
}
function applyAdditionalOverrides(packageName, package) {
    switch(packageName) {
        case "http":
            console.log("Http override for socket");
            package.socket = (url, headers, auth) => {
                console.warn("This uses an emulated socket connection directly from browser. Remoting websocket is not yet supported.");
                if(auth)
                    throw "Socket override does not support auth yet (should work in-app)";

                const obj = {};

                obj.connect = function(listeners) {
                    obj.socket = new WebSocket(url);
                    obj.socket.addEventListener("open", (event) => {
                        obj.isOpen = true;
                        listeners.open && listeners.open();
                    });
                    obj.socket.addEventListener("message", (event) => listeners.message && listeners.message(event.data));
                    obj.socket.addEventListener("error", (event) => listeners.failure && listeners.failure());
                    obj.socket.addEventListener("closed", (event) => {
                        obj.isOpen = false;
                        listeners.closed && listeners.closed(event.code, event.reason);
                    });
                };
                obj.send = function(msg) {
                    if(obj.socket != null)
                        obj.socket.send(msg);
                }
                obj.close = function(code, reason) {
                    if(obj.socket != null)
                        obj.socket.close(code, reason);
                }
                return obj;
            };
        break;

    }
}

function reloadPackages() {
    const packages = Object.keys(_loadedPackages);
    applyPackages(packages);
}

function httpGETBypass(url, headers, ct) {
    return JSON.parse(syncPOST("/get?CT=" + ct, {}, JSON.stringify({
        url: url,
        headers: headers
    })));
}
function pluginUpdateTestPlugin(config) {
    return JSON.parse(syncPOST("/plugin/updateTestPlugin", {}, JSON.stringify(config)));
}
function pluginLoginTestPlugin() {
    return syncGET("/plugin/loginTestPlugin", {});
}
function pluginLogoutTestPlugin() {
    return syncGET("/plugin/logoutTestPlugin", {});
}
function pluginGetPackage(packageName) {
    return JSON.parse(syncGET("/plugin/packageGet?variable=" + packageName, {}));
}
function pluginRemoteProp(objID, propName) {
    return JSON.parse(syncGET("/plugin/remoteProp?id=" + objID + "&prop=" + propName, {}));
}
function pluginRemoteCall(objID, methodName, args) {
    return JSON.parse(syncPOST("/plugin/remoteCall?id=" + objID + "&method=" + methodName, {}, JSON.stringify(args)));
}

function pluginIsLoggedIn(cb, err) {
    fetch("/plugin/isLoggedIn", {
        timeout: 1000
    })
        .then(x => x.json())
        .then(x => cb(x))
        .catch(y => err && err(y));
}

function pluginGetWarnings(config) {
    return JSON.parse(syncPOST("/plugin/getWarnings", {}, JSON.stringify(config)));
}

function uploadDevPlugin(config) {
    return JSON.parse(syncPOST("/plugin/loadDevPlugin", {}, JSON.stringify(config)));
}
function getDevLogs(lastIndex, cb) {
    if(!lastIndex)
        lastIndex = 0;
    fetch("/plugin/getDevLogs?index=" + lastIndex, {
        timeout: 1000
    })
        .then(x=>x.json())
        .then(y=> cb && cb(y));
}
function sendFakeDevLog(devId, msg) {
    return syncGET("/plugin/fakeDevLog?devId=" + devId + "&msg=" + msg, {});
}

var __DEV_SETTINGS = {};
function setDevSettings(obj) {
    __DEV_SETTINGS = obj;
}

var liveChatIntervalId = null;
function testLiveChat(url, interval, verbose) {
    if(!interval)
        interval = 4000;
    if(liveChatIntervalId)
        clearInterval(liveChatIntervalId);

    let live = source.getLiveEvents(url);
    liveChatIntervalId = setInterval(()=>{
           if(!live.hasMorePagers()) {
              clearInterval(liveChatIntervalId);
              console.log("END OF CHAT");
           }
           live.nextPage();
           for(let event of live.results) {
              if(verbose) {
                  if(event.type == 1)
                     console.log("Live Chat: [" + event.name + "]:" + event.message, event);
                  else if(event.type == 5)
                     console.log("Live Chat: DONATION (" + event.amount + ") [" + event.name + "]: " + event.message, event);
                  else if(event.type == 6)
                     console.log("Live Chat: MEMBER (" + event.amount + ") [" + event.name + "]: " + event.message, event);
                  else console.log("Live Chat: Ev", event);
              }
              else {
                  if(event.type == 1)
                     console.log("Live Chat: [" + event.name + "]:" + event.message);
                  else if(event.type == 5)
                     console.log("Live Chat: DONATION (" + event.amount + ") [" + event.name + "]: " + event.message);
                  else if(event.type == 6)
                     console.log("Live Chat: MEMBER (" + event.amount + ") [" + event.name + "]: " + event.message);
                  else console.log("Live Chat: Ev", event);
              }
        }
    }, interval);
}

function testPlaybackTracker(url, seconds, iterations, pauseAfter) {
    let lastTime = (new Date()).getTime();
    const tracker = source.getPlaybackTracker(url);
    if(!tracker) {
        console.warn("No tracker available (null)");
        return;
    }

    if(tracker.onInit)
        tracker.onInit(seconds);

    let iteration = undefined;
    iteration = function(itt) {
        const diff = (new Date()).getTime() - lastTime;
        const secCurrent = seconds + (diff / 1000);

        tracker.onProgress(secCurrent, true);

        if(itt > 0)
            setTimeout(()=>{
                iteration(itt - 1);
            }, tracker.nextRequest);
        else
            setTimeout(()=> {
                const diff = (new Date()).getTime() - lastTime;
                const secCurrent = seconds + (diff / 1000);
                tracker.onProgress(secCurrent, false);
            }, 850);
    }
    setTimeout(()=> {
        iteration(iterations - 1);
    }, tracker.nextRequest);
}