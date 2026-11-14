package com.futo.platformplayer.tv.sources

import android.content.Context
import com.futo.platformplayer.R
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StateSubscriptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class TvSourceInfo(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val icon: ImageVariable? = null,
    val config: SourcePluginConfig? = null,
    val installed: Boolean = true,
    val home: Boolean = true,
    val search: Boolean = true,
    val loggedIn: Boolean = false,
    val values: Map<String, String?> = emptyMap(),
    val bundled: Boolean = false
)

internal data class TvSourceUpdate(
    val previousVersion: Int,
    val config: SourcePluginConfig,
    val script: String,
    val warnings: List<String>
)

internal interface TvSourcesStore {
    suspend fun list(): List<TvSourceInfo>

    suspend fun enable(id: String, enabled: Boolean)

    suspend fun install(id: String)

    suspend fun remove(id: String)

    suspend fun affectedSubscriptions(id: String): Int

    suspend fun visibility(id: String, home: Boolean, search: Boolean)

    suspend fun move(id: String, delta: Int)

    suspend fun settings(id: String, values: Map<String, String?>)

    suspend fun checkUpdate(id: String): TvSourceUpdate?

    suspend fun update(update: TvSourceUpdate)

    companion object {
        fun platform(context: Context): TvSourcesStore =
            object : TvSourcesStore {
                private val app = context.applicationContext
                private val platform
                    get() = StatePlatform.instance

                private val plugins
                    get() = StatePlugins.instance

                override suspend fun list(): List<TvSourceInfo> {
                    val embedded = plugins.getEmbeddedSources(app)
                    val enabled = platform.getSortedEnabledClient()
                    val available = platform.getAvailableClients()
                    val installed = (enabled + available.filter { it.id !in enabled.map { c -> c.id } }.sortedBy { it.name }).map {
                        client ->
                        val js = client as? JSClient
                        TvSourceInfo(
                            client.id,
                            client.name,
                            enabled.any { it.id == client.id },
                            client.icon,
                            js?.config,
                            home = js?.enableInHome ?: true,
                            search = js?.enableInSearch ?: true,
                            loggedIn = js?.isLoggedIn ?: false,
                            values = js?.descriptor?.getSettingsWithDefaults().orEmpty(),
                            bundled = client.id in embedded)
                    }
                    val bundled = embedded
                        .filterKeys { id -> installed.none { it.id == id } }
                        .mapNotNull { (id, path) ->
                            plugins.getEmbeddedPluginConfig(app, path)?.let { config ->
                                val icon = config.iconUrl?.let {
                                    ImageVariable.fromUrl(
                                        if (it.startsWith("https://")) it
                                        else
                                            "file:///android_asset/${path.substringBeforeLast('/')}/${it.removePrefix("./")}")
                                }
                                TvSourceInfo(id, config.name, false, icon, config, installed = false, bundled = true)
                            }
                        }
                    return installed + bundled.sortedBy { it.name }
                }

                override suspend fun enable(id: String, enabled: Boolean) {
                    val ids = platform.getSortedEnabledClient().map { it.id }.toMutableList()
                    if (enabled) {
                        if (id !in ids) ids.add(id)
                    } else {
                        check(ids.size > 1 || id !in ids)
                        ids.remove(id)
                    }
                    platform.selectClients(*ids.toTypedArray())
                    check(platform.isClientEnabled(id) == enabled) { "Source failed to initialize" }
                }

                override suspend fun install(id: String) {
                    check(!plugins.hasPlugin(id))
                    val path = checkNotNull(plugins.getEmbeddedSources(app)[id])
                    check(plugins.installEmbeddedPlugin(app, path, id))
                    platform.updateAvailableClients(app)
                    enable(id, true)
                }

                override suspend fun remove(id: String) {
                    check(id !in plugins.getEmbeddedSources(app)) { "Bundled sources can be disabled, not uninstalled" }
                    plugins.deletePlugin(id)
                    platform.updateAvailableClients(app)
                }

                override suspend fun affectedSubscriptions(id: String): Int {
                    val client = platform.getClient(id)
                    val others = platform.getEnabledClients().filter { it.id != id }
                    return StateSubscriptions.instance.getSubscriptions().count { subscription ->
                        client.isChannelUrl(subscription.channel.url) &&
                            others.none { it.isChannelUrl(subscription.channel.url) }
                    }
                }

                override suspend fun visibility(id: String, home: Boolean, search: Boolean) {
                    val descriptor = checkNotNull(plugins.getPlugin(id))
                    descriptor.appSettings.tabEnabled.enableHome = home
                    descriptor.appSettings.tabEnabled.enableSearch = search
                    plugins.savePlugin(id)
                }

                override suspend fun move(id: String, delta: Int) {
                    val ordered = platform.getSortedEnabledClient().toMutableList()
                    val index = ordered.indexOfFirst { it.id == id }
                    check(index >= 0)
                    val target = (index + delta).coerceIn(0, ordered.lastIndex)
                    val client = ordered.removeAt(index)
                    ordered.add(target, client)
                    platform.setPlatformOrder(ordered.map { it.name })
                }

                override suspend fun settings(id: String, values: Map<String, String?>) {
                    plugins.setPluginSettings(id, values)
                    platform.reloadClient(app, id)
                }

                override suspend fun checkUpdate(id: String): TvSourceUpdate? {
                    val current = checkNotNull(plugins.getPlugin(id)).config
                    val url = checkNotNull(current.sourceUrl)
                    val config = SourcePluginConfig.fromJson(download(url), url)
                    check(config.id == id) { "Update belongs to a different source" }
                    if (config.version <= current.version) return null
                    val script = download(config.absoluteScriptUrl)
                    if (!config.scriptSignature.isNullOrBlank())
                        check(config.validate(script)) { "Invalid source signature" }
                    val warnings = config.getWarnings(script).map { "${it.first}\n${it.second}" }.toMutableList()
                    if (!current.isLowRiskUpdate(plugins.getScript(id).orEmpty(), config, script)) {
                        warnings.add(app.getString(R.string.tv_source_permissions_changed))
                        warnings.add(config.allowUrls.joinToString("\n"))
                    }
                    return TvSourceUpdate(current.version, config, script, warnings.filter { it.isNotBlank() })
                }

                override suspend fun update(update: TvSourceUpdate) {
                    check(plugins.getPlugin(update.config.id)?.config?.version == update.previousVersion) {
                        "Source changed; check again"
                    }
                    suspendCancellableCoroutine<Unit> { continuation ->
                        plugins.installPluginBackground(
                            app, StateApp.instance.scope, update.config, update.script, { _, _ -> }) { error ->
                            if (continuation.isActive) {
                                if (error == null) continuation.resume(Unit)
                                else continuation.resumeWithException(error)
                            }
                        }
                    }
                }

                private fun download(url: String): String {
                    val response = ManagedHttpClient().apply { setTimeout(15000) }.get(url)
                    try {
                        check(response.isOk) { "Source request failed" }
                        return checkNotNull(response.body).string()
                    } finally {
                        response.close()
                    }
                }
            }
    }
}
