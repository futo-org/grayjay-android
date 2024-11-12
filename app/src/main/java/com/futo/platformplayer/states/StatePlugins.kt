package com.futo.platformplayer.states

import android.content.Context
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.LoginActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourceCaptchaData
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.fragment.mainactivity.main.SourceDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.SourceDetailFragment.Companion
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.PluginIconStorage
import com.futo.platformplayer.stores.PluginScriptsDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/***
 * Used to maintain plugin settings and configs
 */
class StatePlugins {
    private val TAG = "StatePlugins";

    private val FORCE_REINSTALL_EMBEDDED = false;
    private var _isFirstEmbedUpdate = true;

    private val _pluginScripts = FragmentedStorage.getDirectory<PluginScriptsDirectory>();
    private var _plugins = FragmentedStorage.storeJson<SourcePluginDescriptor>("plugins")
        .load();
    private val iconsDir = FragmentedStorage.getDirectory<PluginIconStorage>();


    private val _syncObject = Object()
    private var _embeddedSources: Map<String, String>? = null
    private var _embeddedSourcesDefault: List<String>? = null
    private var _sourcesUnderConstruction: Map<String, ImageVariable>? = null

    private var _updatesAvailableMap: HashSet<String> = hashSetOf();

    private val _isUpdating: HashSet<String> = hashSetOf();

    fun getPluginIconOrNull(id: String): ImageVariable? {
        if(iconsDir.hasIcon(id))
            return iconsDir.getIconBinary(id);
        return null;
    }

    fun reloadPluginFile(){
        _plugins = FragmentedStorage.storeJson<SourcePluginDescriptor>("plugins")
            .load();
    }

    fun isUpdating(id: String): Boolean{
        synchronized(_isUpdating){
            return _isUpdating.contains(id);
        }
    }
    fun setIsUpdating(id: String, value: Boolean){
        synchronized(_isUpdating){
            if(value && !_isUpdating.contains(id)) {
                Logger.i(TAG, "PLUGIN [${id}] UPDATING");
                _isUpdating.add(id);
            }
            if(!value && _isUpdating.contains(id)) {
                Logger.i(TAG, "PLUGIN [${id}] NOT UPDATING");
                _isUpdating.remove(id);
            }
        }
    }
    suspend fun whileUpdating(id: String, handle: suspend ()->Unit){
        try {
            setIsUpdating(id, true);
            handle();
        }
        finally {
            setIsUpdating(id, false);
        }
    }
    fun clearUpdating(){
        synchronized(_isUpdating) {
            _isUpdating.clear();
        }
    }


    suspend fun checkForUpdates(): List<Pair<SourcePluginConfig, SourcePluginConfig>> = withContext(Dispatchers.IO) {
        var configs = mutableListOf<Pair<SourcePluginConfig, SourcePluginConfig>>()
        val updatesAvailableFor = hashSetOf<String>()
        for (availableClient in StatePlatform.instance.getAvailableClients().filter { it is JSClient && it.descriptor.appSettings.checkForUpdates }) {
            if (availableClient !is JSClient) {
                continue
            }

            val newConfig = checkForUpdates(availableClient.config);
            if (newConfig != null) {
                configs.add(Pair(availableClient.config, newConfig));
                updatesAvailableFor.add(availableClient.config.id)
            }
        }

        _updatesAvailableMap = updatesAvailableFor
        return@withContext configs;
    }
    private suspend fun checkForUpdates(c: SourcePluginConfig): SourcePluginConfig? = withContext(Dispatchers.IO) {
        val sourceUrl = c.sourceUrl ?: return@withContext null;

        Logger.i(TAG, "Check for source updates '${c.name}'.");
        try {
            val client = ManagedHttpClient();
            val response = client.get(sourceUrl);
            Logger.i(TAG, "Downloading source config '$sourceUrl'.");

            if (!response.isOk || response.body == null) {
                return@withContext null;
            }

            val configJson = response.body.string();
            Logger.i(TAG, "Downloaded source config ($sourceUrl):\n${configJson}");

            val config = SourcePluginConfig.fromJson(configJson);
            if (config.version <= c.version) {
                return@withContext null;
            }

            Logger.i(TAG, "Update is available (config.version=${config.version}, source.config.version=${c.version}).");
            return@withContext config;
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to check for updates.", e);
            return@withContext null;
        }
    }
    fun hasUpdateAvailable(c: SourcePluginConfig): Boolean {
        val updatesAvailableMap = _updatesAvailableMap
        synchronized(updatesAvailableMap) {
            return updatesAvailableMap.contains(c.id)
        }
    }
    fun clearUpdateAvailable(c: SourcePluginConfig) {
        val updatesAvailableMap = _updatesAvailableMap
        synchronized(updatesAvailableMap) {
            updatesAvailableMap.remove(c.id)
        }
    }





    fun loginPlugin(context: Context, id: String, afterLogin: ()->Unit): Boolean {
        val descriptor = getPlugin(id) ?: return false;
        val config = descriptor.config;

        if(config.authentication == null)
            return false;

        LoginActivity.showLogin(context, config) {
            try {
                StatePlugins.instance.setPluginAuth(config.id, it);
            } catch (e: Throwable) {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    UIDialogs.showGeneralErrorDialog(context, "Failed to set plugin authentication (loginPlugin)", e)
                }
                Logger.e(SourceDetailFragment.TAG, "Failed to set plugin authentication (loginPlugin)", e)
                return@showLogin
            }

            StateApp.instance.scope.launch(Dispatchers.IO) {
                StatePlatform.instance.reloadClient(context, id);
                afterLogin.invoke();
            }
        };
        return true;
    }


    private fun getResourceIdFromString(resourceName: String, c: Class<*> = R.drawable::class.java): Int? {
        return try {
            val idField = c.getDeclaredField(resourceName)
            idField.getInt(idField)
        } catch (exception: Exception) {
            null
        }
    }

    private fun ensureSourcesConfigLoaded(context: Context) {
        if (_embeddedSources != null && _embeddedSourcesDefault != null && _sourcesUnderConstruction != null) {
            return
        }

        val inputStream = context.resources.openRawResource(R.raw.plugin_config)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val config = Json.decodeFromString<PluginConfig>(jsonString)

        _embeddedSources = config.SOURCES_EMBEDDED
        _embeddedSourcesDefault = config.SOURCES_EMBEDDED_DEFAULT
        _sourcesUnderConstruction = config.SOURCES_UNDER_CONSTRUCTION.mapNotNull {
            val imageVariable = getResourceIdFromString(it.value)?.let { ImageVariable.fromResource(it) } ?: return@mapNotNull null
            Pair(it.key, imageVariable)
        }.toMap()

        Logger.i(TAG, "ensureSourcesConfigLoaded _embeddedSources:\n${_embeddedSources!!.map { "   { ${it.key}: ${it.value} }" }.joinToString("\n")}")
        Logger.i(TAG, "ensureSourcesConfigLoaded _embeddedSourcesDefault:\n${_embeddedSourcesDefault!!.map { "   ${it}" }.joinToString("\n")}")
        Logger.i(TAG, "ensureSourcesConfigLoaded _sourcesUnderConstruction:\n${_sourcesUnderConstruction!!.map { "   { ${it.key}: ${it.value} }" }.joinToString("\n")}")
    }

    fun getEmbeddedSources(context: Context): Map<String, String> {
        synchronized(_syncObject) {
            ensureSourcesConfigLoaded(context)
            return _embeddedSources!!
        }
    }
    fun getEmbeddedSourcesDefault(context: Context): List<String> {
        synchronized(_syncObject) {
            ensureSourcesConfigLoaded(context)
            return _embeddedSourcesDefault!!
        }
    }
    fun getSourcesUnderConstruction(context: Context): Map<String, ImageVariable> {
        synchronized(_syncObject) {
            ensureSourcesConfigLoaded(context)
            return _sourcesUnderConstruction!!
        }
    }


    suspend fun reinstallEmbeddedPlugins(context: Context) {
        for(embedded in getEmbeddedSources(context))
            instance.deletePlugin(embedded.key);
        StatePlatform.instance.updateAvailableClients(context);
    }
    fun updateEmbeddedPlugins(context: Context, subset: List<String>? = null, force: Boolean = false) {
        for(embedded in getEmbeddedSources(context).filter { subset == null || subset.contains(it.key) }) {
            val embeddedConfig = getEmbeddedPluginConfig(context, embedded.value);
            if(embeddedConfig != null) {
                val existing = getPlugin(embedded.key);
                if(existing == null || (existing.config.version < embeddedConfig.version || (force || FORCE_REINSTALL_EMBEDDED))) {
                    if (existing != null)
                        Logger.i(TAG, "Outdated Embedded plugin [${existing.config.id}] ${existing.config.name} (${existing.config.version} < ${embeddedConfig.version}), reinstalling");
                    else
                        Logger.i(TAG, "Embedded plugin nog installed [${embeddedConfig.id}] ${embeddedConfig.name} (${embeddedConfig.version}), installing");
                    installEmbeddedPlugin(context, embedded.value)
                }
                else if(existing != null && _isFirstEmbedUpdate) {
                    Logger.i(TAG, "Embedded plugin [${existing.config.id}] ${existing.config.name}, up to date (${existing.config.version} >= ${embeddedConfig.version})");
                }
            }
        }
        _isFirstEmbedUpdate = false;
    }
    fun installMissingEmbeddedPlugins(context: Context) {
        val plugins = getPlugins();
        for(embedded in getEmbeddedSources(context)) {
            if(!plugins.any { it.config.id == embedded.key }) {
                Logger.i(TAG, "Installing missing embedded plugin [${embedded.key}] ${embedded.value}, deleting and reinstalling");
                val success = instance.installEmbeddedPlugin(context, embedded.value, embedded.key);
                if(!success)
                    Logger.i(TAG, "Failed to install embedded plugin [${embedded.key}]: ${embedded.value}");
            }
        }
    }
    fun getEmbeddedPluginConfig(context: Context, assetConfigPath: String): SourcePluginConfig? {
        val configJson = StateAssets.readAsset(context, assetConfigPath) ?: return null;
        return SourcePluginConfig.fromJson(configJson, "");
    }
    fun getEmbeddedPluginConfigFromID(context: Context, pluginId: String): SourcePluginConfig? {
        val embedded = getEmbeddedSources(context);
        if(!embedded.containsKey(pluginId))
            return null;
        return getEmbeddedPluginConfig(context, embedded[pluginId]!!);
    }

    fun installEmbeddedPlugin(context: Context, assetConfigPath: String, id: String? = null): Boolean {
        try {
            val configJson = StateAssets.readAsset(context, assetConfigPath) ?:
                throw IllegalStateException("Plugin config asset [${assetConfigPath}] not found");
            val config = SourcePluginConfig.fromJson(configJson, "");
            if(id != null && config.id != id)
                throw IllegalStateException("Attempted to install embedded plugin with different id [${config.id}]");

            val script = StateAssets.readAssetRelative(context, assetConfigPath, config.scriptUrl);
            if(script.isNullOrEmpty())
                throw IllegalStateException("Plugin script asset [${config.scriptUrl}] could not be found in assets");

            val icon = if(!config.iconUrl.isNullOrEmpty())
                StateAssets.readAssetBinRelative(context, assetConfigPath, config.iconUrl);
            else null;

            createPlugin(config, script, icon, true);
            return true;
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Exception installing embedded plugin", ex);
            return false;
        }
    }
    fun installPlugins(context: Context, scope: CoroutineScope, sourceUrls: List<String>, handler: ((Boolean) -> Unit)? = null) {
        if(sourceUrls.isEmpty()) {
            handler?.invoke(true);
            return;
        }
        installPlugin(context, scope, sourceUrls[0]) {
           installPlugins(context, scope, sourceUrls.drop(1), handler);
        }
    }
    fun installPlugin(context: Context, scope: CoroutineScope, sourceUrl: String, handler: ((Boolean) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val client = ManagedHttpClient();
            val config: SourcePluginConfig;
            try {
                val configResp = client.get(sourceUrl);
                if(!configResp.isOk)
                    throw IllegalStateException("Failed request with ${configResp.code}");
                val configJson = configResp.body?.string();
                if(configJson.isNullOrEmpty())
                    throw IllegalStateException("No response");
                config = SourcePluginConfig.fromJson(configJson, sourceUrl);
            }
            catch(ex: SerializationException) {
                Logger.e(TAG, "Failed decode config", ex);
                withContext(Dispatchers.Main) {
                    UIDialogs.showDialog(context, R.drawable.ic_error,
                        "Invalid Config Format", null, null,
                        0, UIDialogs.Action("Ok", {
                            finish();
                            handler?.invoke(false);
                        }, UIDialogs.ActionStyle.PRIMARY));
                };
                return@launch;
            }
            catch(ex: Exception) {
                Logger.e(TAG, "Failed fetch config", ex);
                withContext(Dispatchers.Main) {
                    UIDialogs.showGeneralErrorDialog(context, "Failed to install plugin\n(${sourceUrl})", ex, "Ok") {
                        handler?.invoke(false);
                    };
                };
                return@launch;
            }

            val script: String?
            try {
                val scriptResp = client.get(config.absoluteScriptUrl);
                if (!scriptResp.isOk)
                    throw IllegalStateException("script not available [${scriptResp.code}]");
                script = scriptResp.body?.string();
                if (script.isNullOrEmpty())
                    throw IllegalStateException("script empty");
            } catch (ex: Exception) {
                Logger.e(TAG, "Failed fetch script", ex);
                withContext(Dispatchers.Main) {
                    UIDialogs.showGeneralErrorDialog(context, "Failed to fetch script", ex);
                };
                return@launch;
            }

            withContext(Dispatchers.Main) {
                installPlugin(context, scope, config, script, handler);
            }
        }
    }
    fun installPlugin(context: Context, scope: CoroutineScope, config: SourcePluginConfig, script: String, handler: ((Boolean)->Unit)? = null) {
        val client = ManagedHttpClient();
        val warnings = config.getWarnings();

        if (script.isEmpty())
            throw IllegalStateException("script empty");

        fun doInstall(reinstall: Boolean) {
            UIDialogs.showDialogProgress(context) {
                it.setText("Downloading script...");
                it.setProgress(0f);

                scope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) {
                            it.setText("Validating script...");
                            it.setProgress(0.25);
                        }

                        val tempDescriptor = SourcePluginDescriptor(config);
                        val plugin = JSClient(context, tempDescriptor, null, script);
                        plugin.validate();

                        withContext(Dispatchers.Main) {
                            it.setText("Downloading Icon...");
                            it.setProgress(0.5);
                        }

                        val icon = config.absoluteIconUrl?.let { absIconUrl ->
                            withContext(Dispatchers.Main) {
                                it.setText("Saving plugin...");
                                it.setProgress(0.75);
                            }
                            val iconResp = client.get(absIconUrl);
                            if(iconResp.isOk)
                                return@let iconResp.body?.byteStream()?.use { it.readBytes() };
                            return@let null;
                        }
                        val installEx = StatePlugins.instance.createPlugin(config, script, icon, reinstall);
                        if(installEx != null)
                            throw installEx;
                        StatePlatform.instance.updateAvailableClients(context);

                        withContext(Dispatchers.Main) {
                            it.setText("Plugin created!");
                            it.setProgress(1.0);
                            it.dismiss();

                            UIDialogs.toast(context, "Plugin ${config.name} installed");
                            handler?.invoke(true);
                        }
                    } catch (ex: Exception) {
                        Logger.e(TAG, ex.message ?: "null", ex);
                        withContext(Dispatchers.Main) {
                            it.dismiss();
                            UIDialogs.showDialogOk(
                                context,
                                R.drawable.ic_error,
                                "Failed to install due to:\n${ex.message}"
                            ) {
                                handler?.invoke(false);
                            }
                        }
                    }
                };
            };
        }
        fun verifyCanInstall() {
            val installed = StatePlatform.instance.getClientOrNull(config.id);
            if(installed != null)
                UIDialogs.showDialog(context, R.drawable.ic_security_pred,
                    "A plugin with this id already exists named:\n" +
                            "${installed.name}\n[${config.id}]\n\n" +
                            "Would you like to reinstall it?", null, null,
                    1,
                    UIDialogs.Action("Reinstall", { doInstall(true) }, UIDialogs.ActionStyle.DANGEROUS_TEXT),
                    UIDialogs.Action("Cancel", { handler?.invoke(false); }, UIDialogs.ActionStyle.DANGEROUS)
                );
            else
                doInstall(false);
        }

        if(!warnings.isEmpty()) {
            UIDialogs.showDialog(context, R.drawable.ic_security_pred,
                "You are trying to install a plugin (${config.name}) with security vunerabilities.\n" +
                        "Are you sure you want to install it", null,
                    warnings.map { "${it.first}:\n${it.second}\n" }.joinToString("\n"),
                1,
                UIDialogs.Action("Install Anyway", { verifyCanInstall() }, UIDialogs.ActionStyle.DANGEROUS_TEXT),
                UIDialogs.Action("Cancel", { }, UIDialogs.ActionStyle.DANGEROUS));
        }
        else verifyCanInstall();
    }

    fun installPluginBackground(context: Context, scope: CoroutineScope, config: SourcePluginConfig, script: String, onProgress: (text: String, progress: Double)->Unit, onConcluded: (ex: Throwable?)->Unit) {
        scope.launch(Dispatchers.IO) {
            whileUpdating(config.id) {
                withContext(Dispatchers.Main) {
                    onProgress.invoke("Waiting for plugins to finish", 0.1);
                }
                delay(500);

                val client = ManagedHttpClient();
                try {
                    withContext(Dispatchers.Main) {
                        onProgress.invoke("Validating script", 0.25);
                    }

                    val tempDescriptor = SourcePluginDescriptor(config);
                    val plugin = JSClient(context, tempDescriptor, null, script);
                    plugin.validate();

                    withContext(Dispatchers.Main) {
                        onProgress.invoke("Downloading Icon", 0.5);
                    }

                    val icon = config.absoluteIconUrl?.let { absIconUrl ->
                        withContext(Dispatchers.Main) {
                            onProgress.invoke("Saving plugin", 0.75);
                        }
                        val iconResp = client.get(absIconUrl);
                        if (iconResp.isOk)
                            return@let iconResp.body?.byteStream()?.use { it.readBytes() };
                        return@let null;
                    }
                    val installEx = StatePlugins.instance.createPlugin(config, script, icon, true);
                    if (installEx != null)
                        throw installEx;
                    StatePlatform.instance.updateAvailableClients(context);

                    withContext(Dispatchers.Main) {
                        onProgress.invoke("Finished", 1.0)
                        onConcluded.invoke(null);
                    }
                } catch (ex: Exception) {
                    Logger.e(TAG, ex.message ?: "null", ex);
                    withContext(Dispatchers.Main) {
                        onConcluded.invoke(ex);
                    }
                }
            }
        }
    }

    fun getPlugin(id: String): SourcePluginDescriptor? {
        if(id == StateDeveloper.DEV_ID)
            throw IllegalStateException("Attempted to retrieve a persistent developer plugin, this is not allowed");

        synchronized(_plugins) {
            return _plugins.findItem { it.config.id == id };
        }
    }
    fun getPlugins(): List<SourcePluginDescriptor> {
        return _plugins.getItems();
    }
    fun hasPlugin(id: String): Boolean = _plugins.findItem { it.config.id == id } != null;

    fun deletePlugin(id: String) {
        synchronized(_pluginScripts) {
            synchronized(_plugins) {
                _pluginScripts.deleteFile(id);
                val plugins = _plugins.findItems { it.config.id == id };
                for(plugin in plugins)
                    _plugins.delete(plugin);
            }
        }
    }
    fun createPlugin(config: SourcePluginConfig, script: String, icon: ByteArray? = null, reinstall: Boolean = false, flags: List<String> = listOf()) : Throwable? {
        try {
            if(config.id == StateDeveloper.DEV_ID)
                throw IllegalStateException("Attempted to make developer plugin persistent, this is not allowed");

            if(!config.scriptSignature.isNullOrBlank()) {
                val isValid = config.validate(script);
                if(!isValid)
                    throw SecurityException("Script signature is invalid. Possible tampering");
            }

            val existing = getPlugin(config.id)
            val existingAuth = existing?.getAuth();
            val existingCaptcha = existing?.getCaptchaData();
            if (existing != null) {
                if(!reinstall)
                    throw IllegalStateException("Plugin with id ${config.id} already exists");
                else deletePlugin(config.id);
            }
            _pluginScripts.setScript(config.id, script);

            if(_pluginScripts.getScript(config.id).isNullOrEmpty())
                throw IllegalStateException("Plugin script corrupted?");

            if(icon != null)
                iconsDir.saveIconBinary(config.id, icon);

            val descriptor = SourcePluginDescriptor(config, existingAuth?.toEncrypted(), existingCaptcha?.toEncrypted(), flags);
            descriptor.settings = existing?.settings ?: descriptor.settings;
            descriptor.appSettings = existing?.appSettings ?: descriptor.appSettings;
            _plugins.save(descriptor);
            return null;
        }
        catch(ex: Throwable) {
            deletePlugin(config.id);
            return ex;
        }
    }

    fun getScript(pluginId: String) : String? {
        return _pluginScripts.getScript(pluginId);
    }

    fun setPluginSettings(id: String, map: Map<String, String?>) {
        val newSettings = HashMap(map);
        if(id == StateDeveloper.DEV_ID)
        {
            val decConfig = StatePlatform.instance.getDevClient()?.config ?: return;
            for(setting in decConfig.settings) {
                if(!newSettings.containsKey(setting.variableOrName) || newSettings[setting.variableOrName] == null)
                    newSettings[setting.variableOrName] = setting.default;
            }
            StateDeveloper.instance.setDevClientSettings(newSettings);
            return;
        }
        val plugin = getPlugin(id);

        if(plugin != null) {
            for(setting in plugin.config.settings) {
                if(!newSettings.containsKey(setting.variableOrName) || newSettings[setting.variableOrName] == null)
                    newSettings[setting.variableOrName] = setting.default;
            }

            plugin.settings = newSettings;
            _plugins.save(plugin, false, true);
        }
    }
    fun savePlugin(id: String) {
        val plugin = getPlugin(id);

        if(plugin != null) {
            _plugins.save(plugin, false, true);
        }
    }

    fun setPluginCaptcha(id: String, captcha: SourceCaptchaData?) {
        if(id == StateDeveloper.DEV_ID) {
            StatePlatform.instance.getDevClient()?.let {
                it.setCaptcha(captcha);
            };
            return;
        }

        val descriptor = getPlugin(id) ?: throw IllegalArgumentException("Plugin [${id}] does not exist");
        descriptor.updateCaptcha(captcha);
        _plugins.save(descriptor);
    }
    fun setPluginAuth(id: String, auth: SourceAuth?) {
        if(id == StateDeveloper.DEV_ID) {
            StatePlatform.instance.getDevClient()?.let {
                it.setAuth(auth);
            };
            return;
        }

        val descriptor = getPlugin(id) ?: throw IllegalArgumentException("Plugin [${id}] does not exist");
        descriptor.updateAuth(auth);
        _plugins.save(descriptor);
    }

    @Serializable
    private data class PluginConfig(
        val SOURCES_EMBEDDED: Map<String, String>,
        val SOURCES_EMBEDDED_DEFAULT: List<String>,
        val SOURCES_UNDER_CONSTRUCTION: Map<String, String>
    )

    companion object {
        private var _instance : StatePlugins? = null;
        val instance : StatePlugins
            get(){
            if(_instance == null)
                _instance = StatePlugins();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}