package com.futo.platformplayer.engine

import android.content.Context
import com.caoccao.javet.exceptions.JavetCompilationException
import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.exceptions.JavetExecutionException
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.interop.options.V8Flags
import com.caoccao.javet.interop.options.V8RuntimeOptions
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueBoolean
import com.caoccao.javet.values.primitive.V8ValueInteger
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.engine.exceptions.NoInternetException
import com.futo.platformplayer.engine.exceptions.PluginEngineStoppedException
import com.futo.platformplayer.engine.exceptions.ScriptAgeException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCompilationException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptTimeoutException
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.engine.internal.V8Converter
import com.futo.platformplayer.engine.packages.PackageBridge
import com.futo.platformplayer.engine.packages.PackageDOMParser
import com.futo.platformplayer.engine.packages.PackageHttp
import com.futo.platformplayer.engine.packages.PackageJSDOM
import com.futo.platformplayer.engine.packages.PackageUtilities
import com.futo.platformplayer.engine.packages.V8Package
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateAssets
import com.futo.platformplayer.warnIfMainThread
import java.util.concurrent.ConcurrentHashMap

class V8Plugin {
    val config: IV8PluginConfig;
    private val _client: ManagedHttpClient;
    private val _clientAuth: ManagedHttpClient;
    private val _clientOthers: ConcurrentHashMap<String, JSHttpClient> = ConcurrentHashMap();

    val httpClient: ManagedHttpClient get() = _client;
    val httpClientAuth: ManagedHttpClient get() = _clientAuth;
    val httpClientOthers: Map<String, JSHttpClient> get() = _clientOthers;

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

    //TODO: Implement a more universal isBusy system for plugins + JSClient + pooling? TBD if propagation would be beneficial
    private val _busyCounterLock = Object();
    private var _busyCounter = 0;
    val isBusy get() = synchronized(_busyCounterLock) { _busyCounter > 0 };

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
            //V8RuntimeOptions.V8_FLAGS.setUseStrict(true);
            val host = V8Host.getV8Instance();
            val options = host.jsRuntimeType.getRuntimeOptions();

            _runtime = host.createV8Runtime(options);
            if (!host.isIsolateCreated)
                throw IllegalStateException("Isolate not created");

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
        isStopped = true;
        whenNotBusy {
            synchronized(_runtimeLock) {
                isStopped = true;
                _runtime?.let {
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
            onStopped.emit(this);
        }
    }

    fun execute(js: String) : V8Value {
        return executeTyped<V8Value>(js);
    }
    fun <T : V8Value> executeTyped(js: String) : T {
        warnIfMainThread("V8Plugin.executeTyped");
        if(isStopped)
            throw PluginEngineStoppedException(config, "Instance is stopped", js);

        synchronized(_busyCounterLock) {
            _busyCounter++;
        }

        val runtime = _runtime ?: throw IllegalStateException("JSPlugin not started yet");
        try {
            return catchScriptErrors("Plugin[${config.name}]", js) {
                runtime.getExecutor(js).execute()
            };
        }
        finally {
            synchronized(_busyCounterLock) {
                //Free busy *after* afterBusy calls are done to prevent calls on dead runtimes
                try {
                    afterBusy.emit(_busyCounter - 1);
                }
                catch(ex: Throwable) {
                    Logger.e(TAG, "Unhandled V8Plugin.afterBusy", ex);
                }
                _busyCounter--;
            }
        }
    }
    fun executeBoolean(js: String) : Boolean? = catchScriptErrors("Plugin[${config.name}]") { executeTyped<V8ValueBoolean>(js).value };
    fun executeString(js: String) : String? = catchScriptErrors("Plugin[${config.name}]") { executeTyped<V8ValueString>(js).value };
    fun executeInteger(js: String) : Int? = catchScriptErrors("Plugin[${config.name}]") { executeTyped<V8ValueInteger>(js).value };

    fun whenNotBusy(handler: (V8Plugin)->Unit) {
        synchronized(_busyCounterLock) {
            if(_busyCounter == 0)
                handler(this);
            else {
                val tag = Object();
                afterBusy.subscribe(tag) {
                    if(it == 0) {
                        Logger.w(TAG, "V8Plugin afterBusy handled");
                        afterBusy.remove(tag);
                        handler(this);
                    }
                }
            }
        }
    }

    private fun getPackage(packageName: String, allowNull: Boolean = false): V8Package? {
        //TODO: Auto get all package types?
        return when(packageName) {
            "DOMParser" -> PackageDOMParser(this)
            "Http" -> PackageHttp(this, config)
            "Utilities" -> PackageUtilities(this, config)
            "JSDOM" -> PackageJSDOM(this, config)
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

        val TAG = "V8Plugin";

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
                if(executeEx.scriptingError?.context?.containsKey("plugin_type") == true) {
                    val pluginType = executeEx.scriptingError.context["plugin_type"].toString();

                    //Captcha
                    if (pluginType == "CaptchaRequiredException") {
                        throw ScriptCaptchaRequiredException(config,
                            executeEx.scriptingError.context["url"]?.toString(),
                            executeEx.scriptingError.context["body"]?.toString(),
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
                throw ScriptExecutionException(config, extractJSExceptionMessage(executeEx) ?: "", null, executeEx.scriptingError?.stack, codeStripped);
            }
            catch(ex: Exception) {
                throw ex;
            }
        }

        private fun throwExceptionFromV8(config: IV8PluginConfig, pluginType: String, msg: String, innerEx: Exception? = null, stack: String? = null, code: String? = null) {
            when(pluginType) {
                "ScriptException" -> throw ScriptException(config, msg, innerEx, stack, code);
                "CriticalException" -> throw ScriptCriticalException(config, msg, innerEx, stack, code);
                "AgeException" -> throw ScriptAgeException(config, msg, innerEx, stack, code);
                "UnavailableException" -> throw ScriptUnavailableException(config, msg, innerEx, stack, code);
                "ScriptLoginRequiredException" -> throw ScriptLoginRequiredException(config, msg, innerEx, stack, code);
                "ScriptExecutionException" -> throw ScriptExecutionException(config, msg, innerEx, stack, code);
                "ScriptCompilationException" -> throw ScriptCompilationException(config, msg, innerEx, code);
                "ScriptImplementationException" -> throw ScriptImplementationException(config, msg, innerEx, null, code);
                "ScriptTimeoutException" -> throw ScriptTimeoutException(config, msg, innerEx);
                "NoInternetException" -> throw NoInternetException(config, msg, innerEx, stack, code);
                else -> throw ScriptExecutionException(config, msg, innerEx, stack, code);
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


    /**
     * Methods available for scripts (bridge object)
     */
}