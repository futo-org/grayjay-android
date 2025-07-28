package com.futo.platformplayer.developer

import android.content.Context
import com.futo.platformplayer.activities.CaptchaActivity
import com.futo.platformplayer.activities.LoginActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.HttpGET
import com.futo.platformplayer.api.http.server.HttpPOST
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.api.media.platforms.js.internal.JSDocs
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.dev.V8RemoteObject
import com.futo.platformplayer.engine.dev.V8RemoteObject.Companion.gsonStandard
import com.futo.platformplayer.engine.dev.V8RemoteObject.Companion.serialize
import com.futo.platformplayer.engine.packages.PackageHttp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateAssets
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.states.StatePlatform
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.UUID
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class DeveloperEndpoints(private val context: Context) {
    private val TAG = "DeveloperEndpoints";
    private val _client = ManagedHttpClient();
    private var _testPlugin: V8Plugin? = null;
    private var _testPluginFull: JSClient? = null;
    private val testPluginOrThrow: V8Plugin get() = _testPlugin ?: throw IllegalStateException("Attempted to use test plugin without plugin");
    private val _testPluginVariables: HashMap<String, V8RemoteObject> = hashMapOf();

    private inline fun <reified T> createRemoteObjectArray(objs: Iterable<T>): List<V8RemoteObject?> {
        val remotes = mutableListOf<V8RemoteObject?>();
        for(obj in objs)
            remotes.add(createRemoteObject(obj));
        return remotes;
    }
    private inline fun <reified T> createRemoteObject(obj: T): V8RemoteObject? {
        if(obj == null)
            return null;

        val id = UUID.randomUUID().toString();
        val robj = V8RemoteObject(id, obj as Any);
        if(robj.requiresRegistration) {
            synchronized(_testPluginVariables) {
                _testPluginVariables.put(id, robj);
            }
        }
        return robj;
    }
    private inline fun <reified T> getRemoteObjectOrCreate(obj: T): V8RemoteObject? {
        if(obj == null)
            return null;

        var instance: V8RemoteObject? = getRemoteObjectByInstance(obj as Any);
        if(instance == null)
            instance = createRemoteObject(obj);
        return instance!!;
    }
    private fun getRemoteObject(id: String): V8RemoteObject {
        synchronized(_testPluginVariables) {
            if(!_testPluginVariables.containsKey(id))
                throw IllegalArgumentException("Remote object [${id}] does not exist");
            return _testPluginVariables[id]!!;
        }
    }
    private fun getRemoteObjectByInstance(obj: Any): V8RemoteObject? {

        synchronized(_testPluginVariables) {
            return _testPluginVariables.values.firstOrNull { it.obj == obj };
        }
    }


    //Files
    @HttpGET("/dev", "text/html")
    val devTestHtml = StateAssets.readAsset(context, "devportal/index.html");
    @HttpGET("/source.js", "application/javascript")
    val devSourceJS = StateAssets.readAsset(context, "scripts/source.js");
    @HttpGET("/dev_bridge.js", "application/javascript")
    val devBridgeJS = StateAssets.readAsset(context, "devportal/dev_bridge.js");
    @HttpGET("/source_docs.json", "application/json")
    val devSourceDocsJson = Json.encodeToString(JSClient.getJSDocs());
    @HttpGET("/source_docs.js", "application/javascript")
    val devSourceDocsJS = "const sourceDocs = $devSourceDocsJson";

    @HttpGET("/source_doc_urls.json", "application/json")
    fun devSourceDocUrlsJson(httpContext: HttpContext) {;
        val docs = JSClient.getMethodDocUrls();
        httpContext.respondCode(200, Json.encodeToString(docs), "application/json");
    }
    @HttpGET("/source_doc_urls.js", "application/javascript")
    fun devSourceDocUrlsJs(httpContext: HttpContext) {;
        val docs = JSClient.getMethodDocUrls();
        httpContext.respondCode(200, "const sourceDocUrls = " + Json.encodeToString(docs), "application/javascript");
    }

    //Dependencies
    @HttpGET("/dependencies/FutoMainLogo.svg", "image/svg+xml")
    val depFutoLogo = StateAssets.readAsset(context, "devportal/dependencies/FutoMainLogo.svg");
    @HttpGET("/favicon.svg", "image/svg+xml")
    val favicon = StateAssets.readAsset(context, "devportal/dependencies/favicon.svg");

    @HttpGET("/reference_plugin.d.ts", "text/plain")
    fun devSourceTSWithRefs(httpContext: HttpContext) {
        val builder = StringBuilder();

        builder.appendLine("//Reference Scriptfile");
        builder.appendLine("//Intended exclusively for auto-complete in your IDE, not for execution");

        builder.appendLine(StateAssets.readAsset(context, "devportal/plugin.d.ts"));

        httpContext.respondCode(200, builder.toString(), "text/plain");
    }

    @HttpGET("/reference_autocomplete.js", "application/javascript")
    fun devSourceJSWithRefs(httpContext: HttpContext) {
        val builder = StringBuilder();

        builder.appendLine("//Reference Scriptfile");
        builder.appendLine("//Intended exclusively for auto-complete in your IDE, not for execution");

        builder.appendLine(StateAssets.readAsset(context, "scripts/source.js"));

        for(pack in testPluginOrThrow.getPackages()) {
            builder.appendLine();
            builder.appendLine("//Package ${pack.name} (variable: ${pack.variableName})");
            val props = V8RemoteObject.getV8Properties(pack::class);
            val funcs = V8RemoteObject.getV8Functions(pack::class);

            if(!pack.variableName.isNullOrEmpty() && (props.isNotEmpty() || funcs.isNotEmpty())) {
                builder.appendLine("let ${pack.variableName} = {");

                val lastProp = props.lastOrNull();
                for(prop in props) {
                    builder.appendLine("   /**");
                    builder.appendLine("   * @return {${prop.returnType.jvmErasure.simpleName}}");
                    builder.appendLine("   **/");
                    builder.append("   ${prop.name}: null");
                    if(prop != lastProp || funcs.isNotEmpty())
                        builder.append(",\n");
                    else
                        builder.append(("\n"));
                    builder.appendLine();
                }

                val lastFunc = funcs.lastOrNull();
                for(func in funcs) {
                    builder.appendLine("   /**");
                    for(para in func.parameters.subList(1, func.parameters.size))
                        builder.appendLine("   * @param {${para.type.jvmErasure.simpleName}} ${para.name}");
                    builder.appendLine("   * @return {${func.returnType.jvmErasure.simpleName}}");
                    builder.appendLine("   **/");
                    builder.append("   ${func.name}: function(");

                    val lastPara = func.parameters.lastOrNull();
                    for(para in func.parameters.subList(1, func.parameters.size)) {
                        builder.append("${para.name}");
                        if(para != lastPara)
                            builder.append(", ");
                    }
                    builder.append(") {}");
                    if(func != lastFunc || funcs.isNotEmpty())
                        builder.append(",\n");
                    else
                        builder.append(("\n"));
                    builder.appendLine();
                }

                builder.appendLine("}");
            }
        }
        httpContext.respondCode(200, builder.toString(), "application/javascript");
    }



    @HttpPOST("/plugin/getWarnings")
    fun plugin_getWarnings(context: HttpContext) {
        val config = context.readContentJson<SourcePluginConfig>()
        context.respondJson(200, config.getWarnings())
    }

    //Testing
    @HttpPOST("/plugin/updateTestPlugin")
    fun pluginUpdateTestPlugin(context: HttpContext) {
        val config = context.readContentJson<SourcePluginConfig>()
        try {
            _testPluginVariables.clear();

            val client = JSHttpClient(null, null, null, config);
            val clientAuth = JSHttpClient(null, null, null, config);
            _testPlugin = V8Plugin(StateApp.instance.context, config, null, client, clientAuth);
            try {
                val script = _client.get(config.absoluteScriptUrl);
                _testPluginFull = JSClient(StateApp.instance.context, SourcePluginDescriptor(
                    config, null, null, null
                ), null, script.body?.string() ?: "");
                _testPluginFull!!.initialize();
            }
            catch (ex: Throwable) {
                Logger.e(TAG, "Loading full client failed", ex);
                _testPluginFull = null;
            }

            context.respondJson(200, testPluginOrThrow.getPackageVariables());
        }
        catch(ex: Throwable) {
            context.respondCode(500, (ex::class.simpleName + ":" + ex.message), "text/plain")
        }
    }
    @HttpPOST("/plugin/cleanTestPlugin")
    fun pluginCleanTestPlugin(context: HttpContext) {
        try {
            _testPluginVariables.clear();
            context.respondCode(200);
        }
        catch(ex: Throwable) {
            context.respondCode(500, (ex::class.simpleName + ":" + ex.message), "text/plain")
        }
    }
    @HttpPOST("/plugin/captchaTestPlugin")
    fun pluginCaptchaTestPlugin(context: HttpContext) {
        val config = _testPlugin?.config as SourcePluginConfig;
        val url = context.query.get("url")
        val html = context.readContentString();
        try {
            val captchaConfig = config.captcha;
            if(captchaConfig == null) {
                context.respondCode(403, "This plugin doesn't support captcha");
                return;
            }
            CaptchaActivity.showCaptcha(StateApp.instance.context, config, url, html) {
                _testPluginVariables.clear();
                _testPlugin = V8Plugin(StateApp.instance.context, config, null, JSHttpClient(null, null, it), JSHttpClient(null, null, it));

            };
            context.respondCode(200, "Captcha started");
        }
        catch(ex: Throwable) {
            context.respondCode(500, (ex::class.simpleName + ":" + ex.message), "text/plain")
        }
    }
    @HttpGET("/plugin/loginTestPlugin")
    fun pluginLoginTestPlugin(context: HttpContext) {
        val config = _testPlugin?.config as SourcePluginConfig;
        try {
            val authConfig = config.authentication;
            if(authConfig == null) {
                context.respondCode(403, "This plugin doesn't support auth");
                return;
            }
            LoginActivity.showLogin(StateApp.instance.context, config) {
                _testPluginVariables.clear();
                _testPlugin = V8Plugin(StateApp.instance.context, config, null, JSHttpClient(null, null, null, config), JSHttpClient(null, it, null, config));

            };
            context.respondCode(200, "Login started");
        }
        catch(ex: Throwable) {
            context.respondCode(500, (ex::class.simpleName + ":" + ex.message), "text/plain")
        }
    }
    @HttpGET("/plugin/logoutTestPlugin")
    fun pluginLogoutTestPlugin(context: HttpContext) {
        val config = _testPlugin?.config as SourcePluginConfig;
        try {
            _testPluginVariables.clear();
            _testPlugin = V8Plugin(StateApp.instance.context, config, null);
            context.respondCode(200, "Logged out");
        }
        catch(ex: Throwable) {
            context.respondCode(500, (ex::class.simpleName + ":" + ex.message), "text/plain")
        }
    }

    @HttpGET("/plugin/isLoggedIn")
    fun pluginIsLoggedIn(context: HttpContext) {
        try {
            val isLoggedIn = _testPlugin?.httpClientAuth is JSHttpClient && (_testPlugin?.httpClientAuth as JSHttpClient).isLoggedIn;
            context.respondCode(200, if(isLoggedIn) "true" else "false", "application/json");
        }
        catch(ex: Throwable) {
            context.respondCode(500, (ex::class.simpleName + ":" + ex.message), "text/plain")
        }
    }

    @HttpGET("/plugin/packageGet")
    fun pluginPackageGet(context: HttpContext) {
        val variableName = context.query.get("variable")
        try {
            if(variableName.isNullOrEmpty()) {
                context.respondCode(400, "Missing variable name");
                return;
            }
            val pack = testPluginOrThrow.getPackageByVariableName(variableName);
            context.respondCode(200, getRemoteObjectOrCreate(pack)?.serialize() ?: "null", "application/json");
        }
        catch(ex: Throwable) {
            //Logger.e("Developer Endpoints", "Failed to fetch packageGet:", ex);
            context.respondCode(500, ex::class.simpleName + ":" + ex.message ?: "", "text/plain")
        }
    }
    @HttpPOST("/plugin/remoteCall")
    fun pluginRemoteCall(context: HttpContext) {
        try {
            val objId = context.query.get("id")
            val method = context.query.get("method")

            if(objId.isNullOrEmpty()) {
                context.respondCode(400, "Missing object id");
                return;
            }
            if(method.isNullOrEmpty()) {
                context.respondCode(400, "Missing method");
                return;
            }
            if(method != "isLoggedIn")
                Logger.i(TAG, "Remote Call [${objId}].${method}(...)");

            val parameters = context.readContentString();

            val remoteObj = getRemoteObject(objId);
            val paras = JsonParser.parseString(parameters);
            if(!paras.isJsonArray)
                throw IllegalArgumentException("Expected json array as body");
            val callResult = remoteObj.call(method, paras as JsonArray);
            val json = wrapRemoteResult(callResult, false);
            context.respondCode(200, json, "application/json");
        }
        catch(invocation: InvocationTargetException) {
            val innerException = invocation.targetException;
            Logger.e("DeveloperEndpoints", innerException.message, innerException);
            context.respondCode(500, innerException::class.simpleName + ":" + innerException.message, "text/plain")
        }
        catch(ilEx: IllegalArgumentException) {
            if(ilEx.message?.contains("does not exist") ?: false) {
                context.respondCode(400, ilEx.message ?: "", "text/plain");
            }
            else {
                Logger.e("DeveloperEndpoints", ilEx.message, ilEx);
                context.respondCode(500, ilEx::class.simpleName + ":" + ilEx.message, "text/plain")
            }
        }
        catch(ex: Throwable) {
            Logger.e("DeveloperEndpoints", ex.message, ex);
            context.respondCode(500, ex::class.simpleName + ":" + ex.message, "text/plain")
        }
    }
    @HttpGET("/plugin/remoteProp")
    fun pluginRemoteProp(context: HttpContext) {
        val objId = context.query.get("id")
        val prop = context.query.get("prop")
        try {
            if(objId.isNullOrEmpty()) {
                context.respondCode(400, "Missing variable name");
                return;
            }
            if(prop.isNullOrEmpty()) {
                context.respondCode(400, "Missing prop name");
                return;
            }
            val remoteObj = getRemoteObject(objId);
            Logger.i(TAG, "Remote Prop [${objId}].${prop}(...)");

            //TODO: Determine if we should get existing or always create new
            val callResult = remoteObj.prop(prop);
            val json = wrapRemoteResult(callResult, true);
            context.respondCode(200, json, "application/json");
        }
        catch(ilEx: IllegalArgumentException) {
            if(ilEx.message?.contains("does not exist") ?: false) {
                context.respondCode(400, ilEx.message ?: "", "text/plain");
            }
            else {
                Logger.e("DeveloperEndpoints", ilEx.message, ilEx);
                context.respondCode(500, ilEx::class.simpleName + ":" + ilEx.message, "text/plain")
            }
        }
        catch(ex: Throwable) {
            Logger.e("DeveloperEndpoints", ex.message, ex);
            context.respondCode(500, ex::class.simpleName + ":" + ex.message, "text/plain")
        }
    }

    private fun wrapRemoteResult(callResult: Any?, useCached: Boolean = false): String {
        return if(callResult == null)
            "null";
        else if(callResult.javaClass.isPrimitive || callResult.javaClass == String::class.java)
            gsonStandard.toJson(callResult);
        else if(callResult is Iterable<*> && callResult.count() == 0)
            return "[]";
        else if(callResult is Iterable<*>) {
            val firstItemType = callResult.first()!!.javaClass;
            if(firstItemType.isPrimitive || firstItemType == String::class.java)
                return gsonStandard.toJson(callResult);
            else
                createRemoteObjectArray(callResult).serialize();
        }
        else if(useCached)
            getRemoteObjectOrCreate(callResult)?.serialize() ?: "null";
        else
            createRemoteObject(callResult)?.serialize() ?: "null";
    }



    //Integration
    @HttpPOST("/plugin/loadDevPlugin")
    fun pluginLoadDevPlugin(context: HttpContext) {
        val config = context.readContentJson<SourcePluginConfig>()
        try {
            val script = _client.get(config.absoluteScriptUrl);
            if(!script.isOk)
                throw IllegalStateException("URL ${config.scriptUrl} return code ${script.code}");
            if(script.body == null)
                throw IllegalStateException("URL ${config.scriptUrl} return no body");

            val id = StatePlatform.instance.injectDevPlugin(config, script.body.string());
            context.respondJson(200, id);
        }
        catch(ex: Exception) {
            Logger.e("DeveloperEndpoints", ex.message, ex);
            context.respondCode(500, ex::class.simpleName + ":" + ex.message, "text/plain")
        }
    }
    @HttpGET("/dev/setDevProxy")
    fun devSetDevProxy(context: HttpContext) {
        try {
            val url = context.query.getOrDefault("url", "");
            val port = context.query.getOrDefault("port", "");
            if(url.isNullOrEmpty() || port.isNullOrEmpty() || port.toIntOrNull() == null)
            {
                StateDeveloper.instance.devProxy = null;
                context.respondCode(400);
                return;
            }
            StateDeveloper.instance.devProxy = StateDeveloper.DevProxySettings(url, port.toInt());
            context.respondCode(200, "true", "application/json");
        }
        catch(ex: Exception) {
            Logger.e("DeveloperEndpoints", ex.message, ex);
            context.respondCode(500, ex::class.simpleName + ":" + ex.message, "text/plain")
        }
    }

    @HttpGET("/plugin/getDevLogs")
    fun pluginGetDevLogs(context: HttpContext) {
        try {
            val index = context.query.getOrDefault("index", "0").toInt();
            context.respondJson(200, StateDeveloper.instance.getLogs(index));
        }
        catch(ex: Exception) {
            context.respondCode(500, ex.message ?: "", "text/plain")
        }
    }
    @HttpGET("/plugin/getDevHttpExchanges")
    fun pluginGetDevExchanges(context: HttpContext) {
        try {
            context.respondJson(200, StateDeveloper.instance.getHttpExchangesAndClear());
        }
        catch(ex: Exception) {
            context.respondCode(500, ex.message ?: "", "text/plain")
        }
    }
    @HttpGET("/plugin/fakeDevLog")
    fun pluginFakeDevLog(context: HttpContext) {
        try {
            val type = context.query.getOrDefault("type", "INFO");
            val devId = context.query.getOrDefault("devId", "");
            val msg = context.query.getOrDefault("msg", "");
            when(type) {
                "INFO" -> StateDeveloper.instance.logDevInfo(devId, msg);
                "EXCEPTION" -> StateDeveloper.instance.logDevException(devId, msg);
            }
            context.respondCode(200);
        }
        catch(ex: Exception) {
            context.respondCode(500, ex.message ?: "", "text/plain")
        }
    }

    private val _fieldAttributesField = FieldAttributes::class.java.getDeclaredField("field");
    init {
        _fieldAttributesField.isAccessible = true;
    }
    private val _remoteTestGson = GsonBuilder()
        .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                return clazz?.simpleName == "JSClient" ||
                    clazz?.simpleName == "KSerializer[]" ||
                    clazz?.simpleName == "V8ValueObject";
            }

            override fun shouldSkipField(f: FieldAttributes?): Boolean {
                val isPublic = f?.hasModifier(Modifier.PUBLIC) ?: true;
                if(!isPublic) {
                    val underlyingField = _fieldAttributesField.get(f) as Field;
                    return !(underlyingField.declaringClass as Class).methods.any { it.name == "get" + underlyingField.name.replaceFirstChar { it.uppercaseChar() } && Modifier.isPublic(it.modifiers) };
                }
                else
                    return !isPublic;
            }
        }).create();
    @HttpPOST("/plugin/remoteTest")
    fun pluginRemoteTest(context: HttpContext) {
        val method = context.query.getOrDefault("method", "");
        try {

            val parameters = context.readContentString();
            val paras = JsonParser.parseString(parameters);
            if(!paras.isJsonArray)
                throw IllegalArgumentException("Expected json array as body");

            val plugin = _testPluginFull ?: throw IllegalStateException("Plugin not loaded");

            val function = plugin::class.memberFunctions.filter { it.findAnnotation<JSDocs>() != null }
                .find { it.name == method };
            if(function == null)
                throw java.lang.IllegalArgumentException("Plugin method [${function}] not found");
            val callResult = function.call(*(listOf(plugin) + paras.asJsonArray.take(function.parameters.size - 1).mapIndexed { index, jsonElement ->
                //For now, manual conversion.
                val parameter = function.parameters[index + 1];
                val value = _remoteTestGson.fromJson<Any>(jsonElement, parameter.type.javaType);
                return@mapIndexed value;
            }).toTypedArray());
            val json = if(callResult is IPager<*>)
                _remoteTestGson.toJson(callResult.getResults())
            else
                _remoteTestGson.toJson(callResult);
            //val json = wrapRemoteResult(callResult, false);

            context.respondCode(200, json);
        }
        catch(ex: InvocationTargetException) {
            Logger.e(TAG, "Remote test for [${method}] is failed", ex.targetException);
            context.respondCode(500, ex.targetException.message ?: "", "text/plain")
        }
        catch(ex: Exception) {
            Logger.e(TAG, "Remote test for [${method}] is failed", ex);
            context.respondCode(500, ex.message ?: "", "text/plain")
        }
    }

    //Internal calls
    @HttpPOST("/get")
    fun get(context: HttpContext) {
        try{
            val body = context.readContentJson<BridgeHttpRequest>();
            if(body.url == null)
                throw IllegalStateException("Missing url");

            val resp = _client.get(body.url!!, body.headers);

            context.respondCode(200,
                Json.encodeToString(PackageHttp.BridgeHttpStringResponse(resp.url, resp.code, resp.body?.string())),
                context.query.getOrDefault("CT", "text/plain"));
        }
        catch(ex: Exception) {
            context.respondCode(500, ex.message ?: "", "text/plain");
        }
    }

    @kotlinx.serialization.Serializable
    class BridgeHttpRequest() {
        var url: String? = null;
        var headers: MutableMap<String, String> = HashMap();
        var contentType: String = "";
        var body: String? = null;
    }
}