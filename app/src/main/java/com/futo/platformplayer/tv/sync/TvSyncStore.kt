package com.futo.platformplayer.tv.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.futo.platformplayer.Settings
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.LinkType
import java.util.concurrent.atomic.AtomicBoolean

internal data class TvSyncDevice(val key: String, val name: String, val connected: Boolean, val link: LinkType)

internal data class TvSyncSnapshot(
    val enabled: Boolean,
    val devices: List<TvSyncDevice>,
    val pairingUrl: String? = null,
    val local: Boolean = false,
    val relay: Boolean = false,
    val listenerFailed: Boolean = false
)

internal class TvSyncRequest(val key: String, val name: String?, private val reply: (Boolean) -> Unit) {
    private val answered = AtomicBoolean(false)

    fun respond(allow: Boolean) {
        if (answered.compareAndSet(false, true)) reply(allow)
    }
}

internal interface TvSyncStore {
    suspend fun snapshot(): TvSyncSnapshot

    fun enable(context: Context)

    suspend fun remove(key: String)

    fun observe(changed: () -> Unit, pairing: (TvSyncRequest) -> Unit): () -> Unit

    companion object {
        fun platform(): TvSyncStore =
            object : TvSyncStore {
                private val database by lazy { StateSync.StoreBasedSyncDatabaseProvider() }

                private val state
                    get() = StateSync.instance

                override suspend fun snapshot(): TvSyncSnapshot {
                    val service = state.syncService
                    val devices = database.getAllAuthorizedDevices()
                        .map { key ->
                            val session = state.getSession(key)
                            TvSyncDevice(
                                key,
                                session?.remoteDeviceName ?: database.getDeviceName(key) ?: key,
                                session?.connected == true && session.isAuthorized,
                                session?.linkType ?: LinkType.None)
                        }
                        .sortedWith(
                            compareByDescending<TvSyncDevice> { it.connected }
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    val url = service?.publicKey?.let { key ->
                        service.pairingCode?.let { code -> TvSyncPairingCode.url(key, code) }
                    }
                    return TvSyncSnapshot(
                        service != null,
                        devices,
                        url,
                        service?.serverSocketStarted == true,
                        service?.relayConnected == true && Settings.instance.synchronization.pairThroughRelay,
                        service?.serverSocketFailedToStart == true)
                }

                override fun enable(context: Context) {
                    if (state.syncService != null) return
                    val settings = Settings.instance.synchronization
                    val previous = settings.enabled
                    try {
                        settings.enabled = true
                        state.start(context)
                        Settings.instance.save()
                    } catch (error: Throwable) {
                        state.stop()
                        settings.enabled = previous
                        Settings.instance.save()
                        throw error
                    }
                }

                override suspend fun remove(key: String) {
                    check(state.syncService != null)
                    state.delete(key)
                    check(key !in state.getAll()) { "Device is still paired" }
                }

                override fun observe(changed: () -> Unit, pairing: (TvSyncRequest) -> Unit): () -> Unit {
                    val owner = Any()
                    val active = AtomicBoolean(true)
                    val main = Handler(Looper.getMainLooper())
                    val pending = mutableSetOf<TvSyncRequest>()
                    fun update() {
                        main.post { if (active.get()) changed() }
                    }
                    state.deviceUpdatedOrAdded.subscribe(owner) { _, _ -> update() }
                    state.deviceRemoved.subscribe(owner) { update() }
                    val service = state.syncService
                    val previous = service?.authorizePrompt
                    val prompt: (String, (Boolean) -> Unit) -> Unit = { key, reply ->
                        lateinit var request: TvSyncRequest
                        request = TvSyncRequest(key, state.getSession(key)?.remoteDeviceName) { allow ->
                            synchronized(pending) { pending.remove(request) }
                            reply(allow)
                        }
                        synchronized(pending) { pending.add(request) }
                        main.post { if (active.get()) pairing(request) else request.respond(false) }
                    }
                    service?.authorizePrompt = prompt
                    return {
                        active.set(false)
                        state.deviceUpdatedOrAdded.remove(owner)
                        state.deviceRemoved.remove(owner)
                        if (service?.authorizePrompt === prompt) service.authorizePrompt = previous
                        synchronized(pending) { pending.toList() }.forEach { it.respond(false) }
                    }
                }
            }
    }
}
