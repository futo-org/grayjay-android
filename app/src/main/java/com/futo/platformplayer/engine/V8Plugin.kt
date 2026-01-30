package com.futo.platformplayer.engine

import android.content.Context
import com.caoccao.javet.exceptions.JavetCompilationException
import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.exceptions.JavetExecutionException
import com.caoccao.javet.interfaces.IJavetEntityError
import com.caoccao.javet.interfaces.IJavetEntityMap
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueBoolean
import com.caoccao.javet.values.primitive.V8ValueInteger
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.IV8ValuePromise
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValuePromise
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.engine.exceptions.NoInternetException
import com.futo.platformplayer.engine.exceptions.PluginEngineStoppedException
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptAgeException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCompilationException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptReloadRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptTimeoutException
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.engine.internal.V8Converter
import com.futo.platformplayer.engine.packages.PackageBridge
import com.futo.platformplayer.engine.packages.PackageBrowser
import com.futo.platformplayer.engine.packages.PackageDOMParser
import com.futo.platformplayer.engine.packages.PackageHttp
import com.futo.platformplayer.engine.packages.PackageHttpImp
import com.futo.platformplayer.engine.packages.PackageJSDOM
import com.futo.platformplayer.engine.packages.PackageUtilities
import com.futo.platformplayer.engine.packages.V8Package
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateAssets
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.toList
import com.futo.platformplayer.toV8ValueBlocking
import com.futo.platformplayer.toV8ValueAsync
import com.futo.platformplayer.warnIfMainThread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class V8Plugin {
    val config: IV8PluginConfig;
    private val _client: ManagedHttpClient;
    private val _clientAuth: ManagedHttpClient;
    private val _clientOthers: ConcurrentHashMap<String, JSHttpClient> = ConcurrentHashMap();

    private val _promises = ConcurrentHashMap<V8ValuePromise, ((V8ValuePromise)->Unit)?>();

    val httpClient: ManagedHttpClient get() = _client;
    val httpClientAuth: ManagedHttpClient get() = _clientAuth;
    val httpClientOthers: Map<String, JSHttpClient> get() = _clientOthers;

    var runtimeId: Int = 0;

    fun registerHttpClient(client: JSHttpClient) {
        synchronized(_clientOthers) {
            _clientOthers.put(client.clientId, client);
        }
    }

    private val _runtimeLock = Object();
    var _runtime : V8Runtime? = null;

    private val _deps : LinkedHashMap<String, String> = LinkedHashMap();
    private val _depsPackages : MutableList<V8Package> = mutableListOf();
    private var _script : String? = null;

    var isStopped = true;
    val onStopped = Event1<V8Plugin>();

    private val _busyLock = ReentrantLock()
    val isBusy get() = _busyLock.isLocked;

    var allowDevSubmit: Boolean = false
        private set(value) {
            field = value;
        }

    /**
     * Called before a busy counter is about to be removed.
     * Is primarily used to prevent additional calls to dead runtimes.
     *
     * Parameter is the busy count after this execution
     */
    val afterBusy = Event1<Int>();

    val onScriptException = Event1<ScriptException>();

    constructor(context: Context, config: IV8PluginConfig, script: String? = null, client: ManagedHttpClient = ManagedHttpClient(), clientAuth: ManagedHttpClient = ManagedHttpClient()) {
        this._client = client;
        this._clientAuth = clientAuth;
        this.config = config;
        this._script = script;
        withDependency(PackageBridge(this, config));

        for(pack in config.packages)
            withDependency(getPackage(pack)!!);
        for(pack in config.packagesOptional)
            getPackage(pack, true)?.let {
                withDependency(it);
            }
    }

    fun changeAllowDevSubmit(isAllowed: Boolean) {
        allowDevSubmit = isAllowed;
    }

    fun withDependency(context: Context, assetPath: String) : V8Plugin  {
        if(!_deps.containsKey(assetPath))
            _deps.put(assetPath, getAssetFile(context, assetPath));
        return this;
    }
    fun withDependency(name: String, script: String) : V8Plugin {
        if(!_deps.containsKey(name))
            _deps.put(name, script);
        return this;
    }
    fun withDependency(v8Package: V8Package) : V8Plugin {
        _depsPackages.add(v8Package);
        return this;
    }
    fun withScript(script: String) : V8Plugin {
        _script = script;
        return this;
    }

    fun getPackages(): List<V8Package> {
        return _depsPackages.toList();
    }
    fun getPackageVariables(): List<String> {
        return _depsPackages.filter { it.variableName != null }.map { it.variableName!! }.toList();
    }
    fun getPackageByVariableName(varName: String): V8Package? {
        return _depsPackages.firstOrNull { it.variableName == varName };
    }

    fun start() {
        val script = _script ?: throw IllegalStateException("Attempted to start V8 without script");
        synchronized(_runtimeLock) {
            if (_runtime != null)
                return;
            runtimeId = runtimeId + 1;
            //V8RuntimeOptions.V8_FLAGS.setUseStrict(true);
            val host = V8Host.getV8Instance();
            val options = host.jsRuntimeType.getRuntimeOptions();

            _runtime = host.createV8Runtime(options);
            if (!host.isIsolateCreated)
                throw IllegalStateException("Isolate not created");

            _runtimeMap.put(_runtime!!, this);

            //Setup bridge
            _runtime?.let {
                it.converter = V8Converter();

                for (pack in _depsPackages) {
                    if (pack.variableName != null)
                        it.createV8ValueObject().use { v8valueObject ->
                            it.globalObject.set(pack.variableName, v8valueObject);
                            v8valueObject.bind(pack);
                        };
                    catchScriptErrors("Package Dep[${pack.name}]") {
                        for (packScript in pack.getScripts())
                            it.getExecutor(packScript).executeVoid();
                    }
                }

                //Load deps
                for (dep in _deps)
                    catchScriptErrors("Dep[${dep.key}]") {
                        it.getExecutor(dep.value).executeVoid()
                    };


                if (config.allowEval)
                    it.allowEval(true);

                //Load plugin
                catchScriptErrors("Plugin[${config.name}]") {
                    it.getExecutor(script).executeVoid()
                };
                isStopped = false;
            }
        }
    }
    fun stop(){
        Logger.i(TAG, "Stopping plugin [${config.name}]");
        busy {
            Logger.i(TAG, "Plugin stopping");
            synchronized(_runtimeLock) {
                if(isStopped)
                    return@busy;
                isStopped = true;
                runtimeId = runtimeId + 1;

                //Cleanup http
                for(pack in _depsPackages) {
                    if(pack is PackageHttp) {
                        pack.cleanup();
                    }
                    else if(pack is PackageBrowser) {
                        pack.deinitialize();
                    }
                }

                _runtime?.let {
                    _runtimeMap.remove(it);
                    _runtime = null;
                    if(!it.isClosed && !it.isDead) {
                        try {
                            it.close();
                        }
                        catch(ex: JavetException) {
                            //In case race conditions are going on, already closed runtimes are fine.
                            if(ex.message?.contains("Runtime is already closed") != true)
                                throw ex;
                        }
                    }
                    Logger.i(TAG, "Stopped plugin [${config.name}]");
                };
            }
            Logger.i(TAG, "Plugin stopped");
            onStopped.emit(this);
        }
        cancelAllPromises();
    }

    fun isThreadAlreadyBusy(): Boolean {
        return _busyLock.isHeldByCurrentThread;
    }
    fun <T> busy(handle: ()->T): T {
        _busyLock.lock();
        //Logger.i(TAG, "Busy Enter [" + _busyLock.holdCount + "]" + Thread.currentThread().stackTrace.drop(3)?.firstOrNull()?.toString() + ", " + Thread.currentThread().stackTrace.drop(4)?.firstOrNull()?.toString() + ", " + Thread.currentThread().stackTrace.drop(5)?.firstOrNull()?.toString())
        try {
            return handle();
        }
        finally {
            //Logger.i(TAG, "Busy Leave [" + _busyLock.holdCount + "]" + Thread.currentThread().stackTrace.drop(3)?.firstOrNull()?.toString() + ", " + Thread.currentThread().stackTrace.drop(4)?.firstOrNull()?.toString()+ ", " + Thread.currentThread().stackTrace.drop(5)?.firstOrNull()?.toString())
            _busyLock.unlock();
        }
        /*
        _busyLock.withLock {
            //Logger.i(TAG, "Entered busy: " + Thread.currentThread().stackTrace.drop(3)?.firstOrNull()?.toString() + ", " + Thread.currentThread().stackTrace.drop(4)?.firstOrNull()?.toString());
            return handle();
        }*/
    }
    fun <T> unbusy(handle: ()->T): T {
        val wasLocked = isThreadAlreadyBusy();
        if(!wasLocked)
            return handle();
        val lockCount = _busyLock.holdCount;
        for(i in 1..lockCount)
            _busyLock.unlock();
        try {
            Logger.w(TAG, "Unlocking V8 thread for [${config.name}] for a blocking resolve of a promise")
            return handle();
        }
        finally {
            Logger.w(TAG, "Relocking V8 thread for [${config.name}] for a blocking resolve of a promise")

            for(i in 1..lockCount)
                _busyLock.lock();
        }
    }
    fun execute(js: String) : V8Value {
        return executeTyped<V8Value>(js);
    }

    suspend fun <T : V8Value> executeTypedAsync(js: String) : Deferred<T> {
        warnIfMainThread("V8Plugin.executeTypedAsync");
        if(isStopped)
            throw PluginEngineStoppedException(config, "Instance is stopped", js);

        return withContext(IO) {
            return@withContext busy {
                try {
                    val runtime = _runtime ?: throw IllegalStateException("JSPlugin not started yet");
                    val result = catchScriptErrors<V8Value>("Plugin[${config.name}]", js) {
                        runtime.getExecutor(js).execute()
                    };

                    if (result is V8ValuePromise) {
                        return@busy result.toV8ValueAsync<T>(this@V8Plugin);
                    } else
                        return@busy CompletableDeferred(result as T);
                }
                catch(ex: Throwable) {
                    val def = CompletableDeferred<T>();
                    def.completeExceptionally(ex);
                    return@busy def;
                }
            }
        }
    }
    fun <T : V8Value> executeTyped(js: String) : T {
        warnIfMainThread("V8Plugin.executeTyped");
        if(isStopped)
            throw PluginEngineStoppedException(config, "Instance is stopped", js);

        val result = busy {
            val runtime = _runtime ?: throw IllegalStateException("JSPlugin not started yet");
            return@busy catchScriptErrors<V8Value>("Plugin[${config.name}]", js) {
                runtime.getExecutor(js).execute()
            };
        };
        if(result is V8ValuePromise) {
            return result.toV8ValueBlocking(this@V8Plugin);
        }
        return result as T;
    }
    fun executeBoolean(js: String) : Boolean? = busy { catchScriptErrors("Plugin[${config.name}]") { executeTyped<V8ValueBoolean>(js).value } }
    fun executeString(js: String) : String? = busy { catchScriptErrors("Plugin[${config.name}]") { executeTyped<V8ValueString>(js).value } }
    fun executeInteger(js: String) : Int? = busy { catchScriptErrors("Plugin[${config.name}]") { executeTyped<V8ValueInteger>(js).value } }


    fun <T: V8Value> handlePromise(result: V8ValuePromise): CompletableDeferred<T> {
        val def = CompletableDeferred<T>();
        result.register(object: IV8ValuePromise.IListener {
            override fun onFulfilled(p0: V8Value?) {
                resolvePromise(result);
                def.complete(p0 as T);
            }
            override fun onRejected(p0: V8Value?) {
                resolvePromise(result);
                def.completeExceptionally(NotImplementedError("onRejected promise not implemented.."));
            }
            override fun onCatch(p0: V8Value?) {
                resolvePromise(result);
                def.completeExceptionally(NotImplementedError("onCatch promise not implemented.."));
            }
        });
        registerPromise(result) {
            if(def.isActive)
                def.cancel("Cancelled by system");
        }
        return def;
    }
    fun registerPromise(promise: V8ValuePromise, onCancelled: ((V8ValuePromise)->Unit)? = null) {
        Logger.v(TAG, "Promise registered for plugin [${config.name}]: ${promise.hashCode()}");
        if (onCancelled != null) {
            _promises.put(promise, onCancelled)
        };
    }
    fun resolvePromise(promise: V8ValuePromise, cancelled: Boolean = false) {
        Logger.v(TAG, "Promise resolved for plugin [${config.name}]: ${promise.hashCode()}");
        val found = synchronized(_promises) {
            val found = _promises.getOrDefault(promise, null);
            _promises.remove(promise);
            return@synchronized found;
        };
        if(found != null && cancelled)
            found(promise);
    }
    fun cancelAllPromises(){
        val promises = _promises.keys().toList();
        for(key in promises) {
            try {
                resolvePromise(key, true);
            }
            catch(ex: Throwable) {}
        }
    }


    private fun getPackage(packageName: String, allowNull: Boolean = false): V8Package? {
        //TODO: Auto get all package types?
        return when(packageName) {
            "DOMParser" -> PackageDOMParser(this)
            "Http" -> PackageHttp(this, config)
            "HttpImp" -> PackageHttpImp(this, config)
            "Utilities" -> PackageUtilities(this, config)
            "JSDOM" -> PackageJSDOM(this, config)
            "Browser" -> {
                val isOfficial = (config is SourcePluginConfig && config.isOfficialAuthor());

                if(BuildConfig.DEBUG)
                    PackageBrowser(this)
                else if(isOfficial)
                    PackageBrowser(this)
                else if(config is SourcePluginConfig && config.id == StateDeveloper.DEV_ID)
                    PackageBrowser(this)
                else
                    throw IllegalArgumentException("Browser is only allowed for debug and official plugins due to security");
            };
            else -> if(allowNull) null else throw ScriptCompilationException(config, "Unknown package [${packageName}] required for plugin ${config.name}");
        };
    }

    fun <T : Any> catchScriptErrors(context: String, code: String? = null, handle: ()->T): T {
        try {
            return catchScriptErrors(this.config, context, code, handle);
        }
        catch(ex: ScriptException) {
            onScriptException.emit(ex);
            throw ex;
        }
    }

    companion object {
        private val REGEX_EX_FALLBACK = Regex(".*throw.*?[\"](.*)[\"].*");
        private val REGEX_EX_FALLBACK2 = Regex(".*throw.*?['](.*)['].*");

        private val _runtimeMap = ConcurrentHashMap<V8Runtime, V8Plugin>();

        val TAG = "V8Plugin";

        fun getPluginFromRuntime(runtime: V8Runtime): V8Plugin? {
            return _runtimeMap.getOrDefault(runtime, null);
        }

        private fun ctxString(ctx: Any?, key: String): String? = when (ctx) {
            is Map<*, *> -> ctx[key]?.toString()
            is V8ValueObject -> if (ctx.has(key)) ctx.getString(key) else null
            else -> null
        }

        fun <T: Any?> catchScriptErrors(config: IV8PluginConfig, context: String, code: String? = null, handle: ()->T): T {
            var codeStripped = code;
            if(codeStripped != null) { //TODO: Improve code stripped
                if (codeStripped.contains("(") && codeStripped.contains(")"))
                {
                    val start = codeStripped.indexOf("(");
                    val end = codeStripped.lastIndexOf(")");
                    codeStripped = codeStripped.substring(0, start) + "(...)" + codeStripped.substring(end + 1);
                }
            }
            try {
                val result = handle();

                if(result is V8ValueObject) {
                    val type = result.getString("plugin_type");
                    if(type != null && type.endsWith("Exception"))
                        throwExceptionFromV8(
                            config,
                            result.getOrThrow(config, "plugin_type", "V8Plugin"),
                            context + ":" + result.getOrThrow(config, "message", "V8Plugin"),
                            null,
                            null,
                            codeStripped
                        );
                }


                return result;
            }
            catch(scriptEx: JavetCompilationException) {
                throw ScriptCompilationException(config, "Compilation: [${context}]: ${scriptEx.message}\n(${scriptEx.scriptingError.lineNumber})[${scriptEx.scriptingError.startColumn}-${scriptEx.scriptingError.endColumn}]: ${scriptEx.scriptingError.sourceLine}", null, codeStripped);
            }
            catch(executeEx: JavetExecutionException) {
                if(executeEx.scriptingError?.context is IJavetEntityError) {
                    val obj = executeEx.scriptingError?.context as IJavetEntityError
                    if(obj.context.containsKey("plugin_type") == true) {
                        val pluginType = obj.context["plugin_type"].toString();

                        //Captcha
                        if (pluginType == "CaptchaRequiredException") {
                            throw ScriptCaptchaRequiredException(config,
                                obj.context["url"]?.toString(),
                                obj.context["body"]?.toString(),
                                executeEx, executeEx.scriptingError?.stack, codeStripped);
                        }

                        //Reload Required
                        if (pluginType == "ReloadRequiredException") {
                            throw ScriptReloadRequiredException(config,
                                obj.context["msg"]?.toString(),
                                obj.context["reloadData"]?.toString(),
                                executeEx, executeEx.scriptingError?.stack, codeStripped);
                        }

                        //Others
                        throwExceptionFromV8(
                            config,
                            pluginType,
                            (extractJSExceptionMessage(executeEx) ?: ""),
                            executeEx,
                            executeEx.scriptingError?.stack,
                            codeStripped
                        );
                    }

                }
                throw ScriptExecutionException(config, extractJSExceptionMessage(executeEx) ?: "", null, executeEx.scriptingError?.stack, codeStripped);
            }
            catch(ex: Exception) {
                throw ex;
            }
        }

        private fun throwExceptionFromV8(config: IV8PluginConfig, pluginType: String, msg: String, innerEx: Exception? = null, stack: String? = null, code: String? = null) {
            throw getExceptionFromPlugin(config, pluginType, msg, innerEx, stack, code);
        }
        fun getExceptionFromPlugin(config: IV8PluginConfig, obj: V8ValueObject, innerEx: Exception? = null, stack: String? = null, code: String? = null, prefix: String? = null): PluginException {
            val pluginType = obj.getOrDefault(config, "plugin_type", "Exception Handling", "")?.let { if(!it.isNullOrBlank()) it + "" else "" } ?: "";
            var msg = obj.getOrDefault<String?>(config, "msg", "Exception Handling", null)
                ?: obj.getOrDefault(config, "message", "Exception Handling", "");
            if(!prefix.isNullOrBlank())
                msg = prefix + msg;
            return getExceptionFromPlugin(config, pluginType, msg ?: "Unknown exception", innerEx, stack, code);
        }
        fun getExceptionFromPlugin(config: IV8PluginConfig, pluginType: String, msg: String, innerEx: Exception? = null, stack: String? = null, code: String? = null): PluginException {
            when(pluginType) {
                "ScriptException" -> return ScriptException(config, msg, innerEx, stack, code);
                "CriticalException" -> return ScriptCriticalException(config, msg, innerEx, stack, code);
                "AgeException" -> return ScriptAgeException(config, msg, innerEx, stack, code);
                "UnavailableException" -> return ScriptUnavailableException(config, msg, innerEx, stack, code);
                "ScriptLoginRequiredException" -> return ScriptLoginRequiredException(config, msg, innerEx, stack, code);
                "ScriptExecutionException" -> return ScriptExecutionException(config, msg, innerEx, stack, code);
                "ScriptCompilationException" -> return ScriptCompilationException(config, msg, innerEx, code);
                "ScriptImplementationException" -> return ScriptImplementationException(config, msg, innerEx, null, code);
                "ScriptTimeoutException" -> return ScriptTimeoutException(config, msg, innerEx);
                "NoInternetException" -> return NoInternetException(config, msg, innerEx, stack, code);
                else -> return ScriptExecutionException(config, msg, innerEx, stack, code);
            }
        }

        private fun extractJSExceptionMessage(ex: JavetExecutionException) : String? {
            val lineInfo = " (${ex.scriptingError.lineNumber})[${ex.scriptingError.startColumn}-${ex.scriptingError.endColumn}]";

            if(ex.message == null || ex.message == "<null>") {
                if(!ex.scriptingError?.message.isNullOrEmpty())
                    return ex.scriptingError?.message!! + lineInfo;
                else if(!ex.scriptingError?.sourceLine?.isNullOrEmpty()!!) {
                    val source = ex.scriptingError.sourceLine;
                    val matchReg1 = REGEX_EX_FALLBACK.matchEntire(source);
                    val matchReg2 = REGEX_EX_FALLBACK2.matchEntire(source);
                    if(matchReg1 != null)
                        return matchReg1.groupValues[1] + lineInfo;
                    if(matchReg2 != null)
                        return matchReg2.groupValues[1] + lineInfo;
                }
            }
            else if(!ex.scriptingError?.detailedMessage.isNullOrEmpty())
                return ex.scriptingError.detailedMessage + lineInfo;
            else if(!ex.scriptingError?.message.isNullOrEmpty())
                return ex.scriptingError.message + lineInfo;
            return ex.message + lineInfo;
        }

        private fun getAssetFile(context: Context, path: String) : String {
            return StateAssets.readAsset(context, path) ?: throw java.lang.IllegalStateException("script ${path} not found");
        }
    }
}