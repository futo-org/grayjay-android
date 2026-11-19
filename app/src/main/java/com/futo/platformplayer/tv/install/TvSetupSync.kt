package com.futo.platformplayer.tv.install

import android.content.Context
import com.futo.platformplayer.R
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.SyncSocketSession
import com.futo.platformplayer.sync.internal.SyncService
import com.futo.platformplayer.tv.sync.TvSyncIdentityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class TvSetupSync(private val context: Context, private val installer: TvInstaller) {
    suspend fun pair(device: TvInstallDevice, status: (Int) -> Unit): String = withContext(Dispatchers.IO) {
        val service = StateSync.instance.syncService ?: throw TvInstaller.Failure(R.string.tv_install_sync_failed)
        status(R.string.tv_install_sync_enable_tv)
        installer.connect(device.endpoint)
        installer.openSync(device)
        val info = withTimeoutOrNull(90_000) {
            var info = installer.pairingInfo(device)
            while (info == null) {
                delay(1000)
                info = installer.pairingInfo(device)
            }
            info
        } ?: throw TvInstaller.Failure(R.string.tv_install_sync_not_ready)
        if (service.getSession(info.publicKey)?.isAuthorized == true) {
            TvSyncIdentityStore.remember(context, info.publicKey)
            return@withContext info.publicKey
        }
        val port = installer.forwardSync(device)
        var socket: SyncSocketSession? = null
        try {
            status(R.string.tv_install_sync_approve)
            socket = connectForApproval(service, port, info.publicKey, info.pairingCode!!)
            if (!socket.isAuthorized)
                throw TvInstaller.Failure(R.string.tv_install_sync_failed)
            TvSyncIdentityStore.remember(context, info.publicKey)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (socket != null) socket.stop() else service.getSession(info.publicKey)?.close()
                service.getSession(info.publicKey)?.takeIf { !it.connected }?.close()
                runCatching { installer.removeForward(device, port) }
                // The ADB tunnel must never become the remembered network address.
                StateSync.StoreBasedSyncDatabaseProvider().setLastAddress(info.publicKey, device.host)
            }
        }
        status(R.string.tv_install_sync_reconnecting)
        try {
            service.connect((listOf(device.host) + info.addresses).distinct().toTypedArray(), info.port,
                info.publicKey, null, timeout_ms = 8000)
        } catch (cancelled: CancellationException) {
            service.getSession(info.publicKey)?.close()
            throw cancelled
        } catch (_: Exception) {
            // Pairing is saved even when the home network cannot be reached yet.
        }
        info.publicKey
    }

    fun devices(): List<Device> {
        val database = StateSync.StoreBasedSyncDatabaseProvider()
        val identities = TvSyncIdentityStore.all(context)
        return database.getAllAuthorizedDevices().filter { it in identities }.map { key ->
            val session = StateSync.instance.getSession(key)
            Device(key, session?.remoteDeviceName ?: database.getDeviceName(key) ?: key,
                session?.connected == true && session.isAuthorized)
        }
    }

    private suspend fun connectForApproval(service: SyncService, port: Int, key: String, code: String): SyncSocketSession = coroutineScope {
        val approval = CompletableDeferred<Boolean>()
        val connection = async {
            service.connect(arrayOf("127.0.0.1"), port, key, code, { complete, _ ->
                if (complete != null) approval.complete(complete)
            }, 90_000).also { approval.complete(it.isAuthorized) }
        }
        if (!approval.await()) {
            connection.cancelAndJoin()
            throw TvInstaller.Failure(R.string.tv_install_sync_failed)
        }
        connection.await()
    }

    data class Device(val key: String, val name: String, val connected: Boolean)
}
