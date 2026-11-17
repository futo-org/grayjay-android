package com.futo.platformplayer.tv.sync

import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.internal.SyncSession
import com.futo.platformplayer.tv.TvMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TvPluginCredentials {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val _pending = ConcurrentHashMap<Pair<String, String>, CompletableDeferred<Message>>()
    private val _imports = Mutex()
    private val _json = Json { ignoreUnknownKeys = true }

    suspend fun choices(key: String): List<Plugin> = withContext(Dispatchers.IO) {
        val reply = request(key, Message("list", UUID.randomUUID().toString()))
        if (reply.type != "plugins") throw Failure(R.string.tv_credentials_unsupported)
        StatePlugins.instance.getPlugins().filter { !it.authEncrypted.isNullOrEmpty() }.map { descriptor ->
            val identity = identity(descriptor)
            Plugin(descriptor.config.id, descriptor.config.name, identity,
                reply.plugins.any { it.id == descriptor.config.id && it.identity == identity })
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun send(key: String, plugins: List<Plugin>): List<Result> = withContext(Dispatchers.IO) {
        require(plugins.isNotEmpty() && plugins.all { it.available })
        val credentials = plugins.map { plugin ->
            val descriptor = StatePlugins.instance.getPlugin(plugin.id)
                ?: throw Failure(R.string.tv_credentials_changed)
            if (identity(descriptor) != plugin.identity) throw Failure(R.string.tv_credentials_changed)
            val auth = descriptor.getAuth() ?: throw Failure(R.string.tv_credentials_changed)
            Credential(plugin.id, plugin.identity, SourceAuth.SerializedAuth(auth.cookieMap, auth.headers, auth.userAgent))
        }
        val reply = request(key, Message("send", UUID.randomUUID().toString(), credentials = credentials))
        if (reply.type != "result") throw Failure(R.string.tv_credentials_failed)
        plugins.map { plugin ->
            Result(plugin.id, reply.results.firstOrNull { it.id == plugin.id }?.status ?: "failed")
        }
    }

    suspend fun handle(session: SyncSession, data: ByteBuffer) {
        if (!session.isAuthorized || !session.connected || data.remaining() > MAX_BYTES) return
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        val message = try {
            _json.decodeFromString<Message>(bytes.decodeToString())
        } catch (_: Exception) {
            return
        } finally {
            bytes.fill(0)
        }
        if (message.type in listOf("plugins", "result", "unsupported")) {
            _pending[session.remotePublicKey to message.requestId]?.complete(message)
            return
        }
        val context = StateApp.instance.contextOrNull ?: return
        if (!TvMode.isEnabled(context)) {
            sendMessage(session, Message("unsupported", message.requestId))
            return
        }
        when (message.type) {
            "list" -> sendMessage(session, Message("plugins", message.requestId,
                plugins = StatePlugins.instance.getPlugins().map { Plugin(it.config.id, it.config.name, identity(it)) }))
            "send" -> _imports.withLock {
                if (!session.isAuthorized || !session.connected || message.credentials.isEmpty() ||
                    message.credentials.size > 100 || message.credentials.map { it.id }.distinct().size != message.credentials.size) return
                val results = message.credentials.map { credential ->
                    val descriptor = StatePlugins.instance.getPlugins().firstOrNull { it.config.id == credential.id }
                    if (descriptor == null || identity(descriptor) != credential.identity) {
                        Result(credential.id, "failed")
                    } else {
                        try {
                            val auth = credential.auth
                            StatePlugins.instance.setPluginAuth(credential.id, SourceAuth(auth.cookieMap, auth.headers, auth.userAgent))
                            val status = try {
                                StatePlatform.instance.reloadClient(context, credential.id)
                                "saved"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                "restart"
                            }
                            Result(credential.id, status)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Result(credential.id, "failed")
                        }
                    }
                }
                sendMessage(session, Message("result", message.requestId, results = results))
            }
        }
    }

    private fun identity(plugin: SourcePluginDescriptor): String {
        val script = StatePlugins.instance.getScript(plugin.config.id).orEmpty()
        val key = plugin.config.scriptPublicKey
        return if (!key.isNullOrEmpty() && plugin.config.validate(script)) "signed:$key"
        else "script:" + MessageDigest.getInstance("SHA-256").digest(script.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun request(key: String, message: Message): Message {
        val session = StateSync.instance.getSession(key)
            ?.takeIf { it.connected && it.isAuthorized } ?: throw Failure(R.string.tv_credentials_offline)
        val response = CompletableDeferred<Message>()
        val id = key to message.requestId
        _pending[id] = response
        try {
            sendMessage(session, message)
            return withTimeoutOrNull(if (message.type == "list") 15_000L else 90_000L) { response.await() }
                ?: throw Failure(if (message.type == "list") R.string.tv_credentials_unavailable else R.string.tv_credentials_unconfirmed)
        } finally {
            _pending.remove(id)
            response.cancel()
        }
    }

    private fun sendMessage(session: SyncSession, message: Message) {
        if (!session.isAuthorized || !session.connected) throw Failure(R.string.tv_credentials_offline)
        val json = _json.encodeToString(message)
        if (json.toByteArray().size > MAX_BYTES) throw Failure(R.string.tv_credentials_too_large)
        session.sendData(GJSyncOpcodes.syncPluginCredentials, json)
    }

    class Failure(val messageId: Int) : Exception()

    @Serializable
    data class Plugin(val id: String, val name: String, val identity: String, val available: Boolean = true)

    @Serializable
    data class Result(val id: String, val status: String)

    @Serializable
    private data class Credential(val id: String, val identity: String, val auth: SourceAuth.SerializedAuth)

    @Serializable
    private data class Message(
        val type: String,
        val requestId: String,
        val plugins: List<Plugin> = emptyList(),
        val credentials: List<Credential> = emptyList(),
        val results: List<Result> = emptyList()
    )
}
