package com.futo.platformplayer.states

import android.content.Context
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.activities.SyncShowPairingCodeActivity
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.encryption.GEncryptionProvider
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.sToOffsetDateTimeUTC
import com.futo.platformplayer.smartMerge
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.stores.StringStringMapStorage
import com.futo.platformplayer.stores.StringTMapStorage
import com.futo.platformplayer.sync.SyncSessionData
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.internal.ISyncDatabaseProvider
import com.futo.platformplayer.sync.internal.Opcode
import com.futo.platformplayer.sync.internal.SyncKeyPair
import com.futo.platformplayer.sync.internal.SyncService
import com.futo.platformplayer.sync.internal.SyncServiceSettings
import com.futo.platformplayer.sync.internal.SyncSession
import com.futo.platformplayer.sync.models.SendToDevicePackage
import com.futo.platformplayer.sync.models.SyncPlaylistsPackage
import com.futo.platformplayer.sync.models.SyncSubscriptionGroupsPackage
import com.futo.platformplayer.sync.models.SyncSubscriptionsPackage
import com.futo.platformplayer.sync.models.SyncWatchLaterPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import kotlin.system.measureTimeMillis

class StateSync {
    private val _syncSessionData = FragmentedStorage.get<StringTMapStorage<SyncSessionData>>("syncSessionData")

    var syncService: SyncService? = null
        private set
    val deviceRemoved: Event1<String> = Event1()
    val deviceUpdatedOrAdded: Event2<String, SyncSession> = Event2()

    fun start(context: Context) {
        if (syncService != null) {
            Logger.i(TAG, "Already started.")
            return
        }

        syncService = SyncService(
            SERVICE_NAME,
            RELAY_SERVER,
            RELAY_PUBLIC_KEY,
            APP_ID,
            StoreBasedSyncDatabaseProvider(),
            SyncServiceSettings(
                mdnsBroadcast = Settings.instance.synchronization.broadcast,
                mdnsConnectDiscovered = Settings.instance.synchronization.connectDiscovered,
                bindListener = Settings.instance.synchronization.localConnections,
                connectLastKnown = Settings.instance.synchronization.connectLast,
                relayHandshakeAllowed = Settings.instance.synchronization.connectThroughRelay,
                relayPairAllowed = Settings.instance.synchronization.pairThroughRelay,
                relayEnabled = Settings.instance.synchronization.discoverThroughRelay,
                relayConnectDirect = Settings.instance.synchronization.connectLocalDirectThroughRelay,
                relayConnectRelayed = Settings.instance.synchronization.connectThroughRelay
            )
        ).apply {
            onAuthorized = { sess, isNewlyAuthorized, isNewSession ->
                if (isNewSession) {
                    deviceUpdatedOrAdded.emit(sess.remotePublicKey, sess)
                    StateApp.instance.scope.launch(Dispatchers.IO) { checkForSync(sess) }
                }
            }

            onUnauthorized = { sess ->
                StateApp.instance.scope.launch(Dispatchers.Main) {
                    UIDialogs.showConfirmationDialog(
                        context,
                        "Device Unauthorized: ${sess.displayName}",
                        action = {
                            Logger.i(TAG, "${sess.remotePublicKey} unauthorized received")
                            removeAuthorizedDevice(sess.remotePublicKey)
                            deviceRemoved.emit(sess.remotePublicKey)
                        },
                        cancelAction = {}
                    )
                }
            }

            onConnectedChanged = { sess, _ -> deviceUpdatedOrAdded.emit(sess.remotePublicKey, sess) }
            onClose = { sess -> deviceRemoved.emit(sess.remotePublicKey) }
            onData = { it, opcode, subOpcode, data ->
                val dataCopy = ByteArray(data.remaining())
                data.get(dataCopy)

                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        handleData(it, opcode, subOpcode, ByteBuffer.wrap(dataCopy))
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Exception occurred while handling data, closing session", e)
                        it.close()
                    }
                }
            }
            authorizePrompt = { remotePublicKey, callback ->
                val scope = StateApp.instance.scopeOrNull
                val activity = SyncShowPairingCodeActivity.activity

                if (scope != null && activity != null) {
                    scope.launch(Dispatchers.Main) {
                        UIDialogs.showConfirmationDialog(activity, "Allow connection from $remotePublicKey?",
                            action = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        callback(true)
                                        Logger.i(TAG, "Connection authorized for $remotePublicKey by confirmation")

                                        activity.finish()
                                    } catch (e: Throwable) {
                                        Logger.e(TAG, "Failed to send authorize", e)
                                    }
                                }
                            },
                            cancelAction = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        callback(false)
                                        Logger.i(TAG, "$remotePublicKey unauthorized received")
                                    } catch (e: Throwable) {
                                        Logger.w(TAG, "Failed to send unauthorize", e)
                                    }
                                }
                            }
                        )
                    }
                } else {
                    callback(false)
                    Logger.i(TAG, "Connection unauthorized for $remotePublicKey because not authorized and not on pairing activity to ask")
                }
            }
        }

        syncService?.start(context)
    }

    fun confirmStarted(context: Context, onStarted: () -> Unit, onNotStarted: () -> Unit) {
        if (syncService == null) {
            UIDialogs.showConfirmationDialog(context, "Sync has not been enabled yet, would you like to enable sync?", {
                Settings.instance.synchronization.enabled = true
                start(context)
                Settings.instance.save()
                onStarted.invoke()
            }, {
                onNotStarted.invoke()
            })
        } else {
            onStarted.invoke()
        }
    }

    fun hasAuthorizedDevice(): Boolean {
        return (syncService?.getAuthorizedDeviceCount() ?: 0) > 0
    }

    fun isAuthorized(publicKey: String): Boolean {
        return syncService?.isAuthorized(publicKey) ?: false
    }

    fun getSession(publicKey: String): SyncSession? {
        return syncService?.getSession(publicKey)
    }

    fun getAuthorizedSessions(): List<SyncSession> {
        return syncService?.getSessions()?.filter { it.isAuthorized }?.toList() ?: listOf()
    }

    fun getSyncSessionData(key: String): SyncSessionData {
        return _syncSessionData.get(key) ?: SyncSessionData(key);
    }
    fun getSyncSessionDataString(key: String): String {
        return Json.encodeToString(getSyncSessionData(key));
    }
    fun saveSyncSessionData(data: SyncSessionData){
        _syncSessionData.setAndSave(data.publicKey, data);
    }

    private fun handleSyncSubscriptionPackage(origin: SyncSession, pack: SyncSubscriptionsPackage) {
        val added = mutableListOf<Subscription>()
        for(sub in pack.subscriptions) {
            if(!StateSubscriptions.instance.isSubscribed(sub.channel)) {
                val removalTime = StateSubscriptions.instance.getSubscriptionRemovalTime(sub.channel.url);
                if(sub.creationTime > removalTime) {
                    val newSub = StateSubscriptions.instance.addSubscription(sub.channel, sub.creationTime);
                    added.add(newSub);
                }
            }
        }
        if(added.size > 3)
            UIDialogs.appToast("${added.size} Subscriptions from ${origin.remotePublicKey.substring(0, Math.min(8, origin.remotePublicKey.length))}");
        else if(added.size > 0)
            UIDialogs.appToast("Subscriptions from ${origin.remotePublicKey.substring(0, Math.min(8, origin.remotePublicKey.length))}:\n" +
                    added.map { it.channel.name }.joinToString("\n"));


        if(pack.subscriptionRemovals.isNotEmpty()) {
            for (subRemoved in pack.subscriptionRemovals) {
                val removed = StateSubscriptions.instance.applySubscriptionRemovals(pack.subscriptionRemovals);
                if(removed.size > 3) {
                    UIDialogs.appToast("Removed ${removed.size} Subscriptions from ${origin.remotePublicKey.substring(0, 8.coerceAtMost(origin.remotePublicKey.length))}");
                } else if(removed.isNotEmpty()) {
                    UIDialogs.appToast("Subscriptions removed from ${origin.remotePublicKey.substring(0, 8.coerceAtMost(origin.remotePublicKey.length))}:\n" + removed.map { it.channel.name }.joinToString("\n"));
                }
            }
        }
    }

    private fun handleData(session: SyncSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        val remotePublicKey = session.remotePublicKey
        when (subOpcode) {
            GJSyncOpcodes.sendToDevices -> {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    val context = StateApp.instance.contextOrNull;
                    if (context != null && context is MainActivity) {
                        val dataBody = ByteArray(data.remaining());
                        val remainder = data.remaining();
                        data.get(dataBody, 0, remainder);
                        val json = String(dataBody, Charsets.UTF_8);
                        val obj = Json.decodeFromString<SendToDevicePackage>(json);
                        UIDialogs.appToast("Received url from device [${session.remotePublicKey}]:\n{${obj.url}");
                        context.handleUrl(obj.url, obj.position);
                    }
                };
            }

            GJSyncOpcodes.syncStateExchange -> {
                val dataBody = ByteArray(data.remaining());
                data.get(dataBody);
                val json = String(dataBody, Charsets.UTF_8);
                val syncSessionData = Serializer.json.decodeFromString<SyncSessionData>(json);

                Logger.i(TAG, "Received SyncSessionData from $remotePublicKey");

                val subscriptionPackageString = StateSubscriptions.instance.getSyncSubscriptionsPackageString()
                Logger.i(TAG, "syncStateExchange syncSubscriptions b (size: ${subscriptionPackageString.length})")
                session.sendData(GJSyncOpcodes.syncSubscriptions, subscriptionPackageString);
                Logger.i(TAG, "syncStateExchange syncSubscriptions (size: ${subscriptionPackageString.length})")

                val subscriptionGroupPackageString = StateSubscriptionGroups.instance.getSyncSubscriptionGroupsPackageString()
                Logger.i(TAG, "syncStateExchange syncSubscriptionGroups b (size: ${subscriptionGroupPackageString.length})")
                session.sendData(GJSyncOpcodes.syncSubscriptionGroups, subscriptionGroupPackageString);
                Logger.i(TAG, "syncStateExchange syncSubscriptionGroups (size: ${subscriptionGroupPackageString.length})")

                val syncPlaylistPackageString = StatePlaylists.instance.getSyncPlaylistsPackageString()
                Logger.i(TAG, "syncStateExchange syncPlaylists b (size: ${syncPlaylistPackageString.length})")
                session.sendData(GJSyncOpcodes.syncPlaylists, syncPlaylistPackageString)
                Logger.i(TAG, "syncStateExchange syncPlaylists (size: ${syncPlaylistPackageString.length})")

                val watchLaterPackageString = Json.encodeToString(StatePlaylists.instance.getWatchLaterSyncPacket(false))
                Logger.i(TAG, "syncStateExchange syncWatchLater b (size: ${watchLaterPackageString.length})")
                session.sendData(GJSyncOpcodes.syncWatchLater, watchLaterPackageString);
                Logger.i(TAG, "syncStateExchange syncWatchLater (size: ${watchLaterPackageString.length})")

                val recentHistory = StateHistory.instance.getRecentHistory(syncSessionData.lastHistory);

                Logger.i(TAG, "syncStateExchange syncHistory b (size: ${recentHistory.size})")
                if(recentHistory.isNotEmpty())
                    session.sendJsonData(GJSyncOpcodes.syncHistory, recentHistory);

                Logger.i(TAG, "syncStateExchange syncHistory (size: ${recentHistory.size})")
            }

            GJSyncOpcodes.syncExport -> {
                val dataBody = ByteArray(data.remaining());
                val bytesStr = ByteArrayInputStream(data.array(), data.position(), data.remaining());
                bytesStr.use { bytesStrBytes ->
                    val exportStruct = StateBackup.ExportStructure.fromZipBytes(bytesStrBytes);
                    for (store in exportStruct.stores) {
                        if (store.key.equals("subscriptions", true)) {
                            val subStore =
                                StateSubscriptions.instance.getUnderlyingSubscriptionsStore();
                            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                                val pack = SyncSubscriptionsPackage(
                                    store.value.map {
                                        subStore.fromReconstruction(it, exportStruct.cache)
                                    },
                                    StateSubscriptions.instance.getSubscriptionRemovals()
                                );
                                handleSyncSubscriptionPackage(session, pack);
                            }
                        }
                    }
                }
            }

            GJSyncOpcodes.syncSubscriptions -> {
                val dataBody = ByteArray(data.remaining());
                data.get(dataBody);
                val json = String(dataBody, Charsets.UTF_8);
                val subPackage = Serializer.json.decodeFromString<SyncSubscriptionsPackage>(json);
                handleSyncSubscriptionPackage(session, subPackage);

                if(subPackage.subscriptions.size > 0) {
                    val newestSub = subPackage.subscriptions.maxOf { it.creationTime };

                    val sesData = getSyncSessionData(remotePublicKey);
                    if (newestSub > sesData.lastSubscription) {
                        sesData.lastSubscription = newestSub;
                        saveSyncSessionData(sesData);
                    }
                }
            }

            GJSyncOpcodes.syncSubscriptionGroups -> {
                val dataBody = ByteArray(data.remaining());
                data.get(dataBody);
                val json = String(dataBody, Charsets.UTF_8);
                val pack = Serializer.json.decodeFromString<SyncSubscriptionGroupsPackage>(json);

                var lastSubgroupChange = OffsetDateTime.MIN;
                for(group in pack.groups){
                    if(group.lastChange > lastSubgroupChange)
                        lastSubgroupChange = group.lastChange;

                    val existing = StateSubscriptionGroups.instance.getSubscriptionGroup(group.id);

                    if(existing == null)
                        StateSubscriptionGroups.instance.updateSubscriptionGroup(group, false, true);
                    else if(existing.lastChange < group.lastChange) {
                        existing.name = group.name;
                        existing.urls = group.urls;
                        existing.image = group.image;
                        existing.priority = group.priority;
                        existing.lastChange = group.lastChange;
                        StateSubscriptionGroups.instance.updateSubscriptionGroup(existing, false, true);
                    }
                }
                for(removal in pack.groupRemovals) {
                    val creation = StateSubscriptionGroups.instance.getSubscriptionGroup(removal.key);
                    val removalTime = removal.value.sToOffsetDateTimeUTC();
                    if(creation != null && creation.creationTime < removalTime)
                        StateSubscriptionGroups.instance.deleteSubscriptionGroup(removal.key, false);
                }
            }

            GJSyncOpcodes.syncPlaylists -> {
                val dataBody = ByteArray(data.remaining());
                data.get(dataBody);
                val json = String(dataBody, Charsets.UTF_8);
                val pack = Serializer.json.decodeFromString<SyncPlaylistsPackage>(json);

                for(playlist in pack.playlists) {
                    val existing = StatePlaylists.instance.getPlaylist(playlist.id);

                    if(existing == null)
                        StatePlaylists.instance.createOrUpdatePlaylist(playlist, false);
                    else if(existing.dateUpdate < playlist.dateUpdate) {
                        existing.dateUpdate = playlist.dateUpdate;
                        existing.name = playlist.name;
                        existing.videos = playlist.videos;
                        existing.dateCreation = playlist.dateCreation;
                        existing.datePlayed = playlist.datePlayed;
                        StatePlaylists.instance.createOrUpdatePlaylist(existing, false);
                    }
                }
                for(removal in pack.playlistRemovals) {
                    val creation = StatePlaylists.instance.getPlaylist(removal.key);
                    val removalTime = removal.value.sToOffsetDateTimeUTC();
                    if(creation != null && creation.dateCreation < removalTime)
                        StatePlaylists.instance.removePlaylist(creation, false);

                }
            }

            GJSyncOpcodes.syncWatchLater -> {
                val dataBody = ByteArray(data.remaining());
                data.get(dataBody);
                val json = String(dataBody, Charsets.UTF_8);
                val pack = Serializer.json.decodeFromString<SyncWatchLaterPackage>(json);

                Logger.i(TAG, "SyncWatchLater received ${pack.videos.size} (${pack.videoAdds?.size}, ${pack.videoRemovals?.size})");

                val allExisting = StatePlaylists.instance.getWatchLater();
                for(video in pack.videos) {
                    val existing = allExisting.firstOrNull { it.url == video.url };
                    val time = if(pack.videoAdds != null && pack.videoAdds.containsKey(video.url)) (pack.videoAdds[video.url] ?: 0).sToOffsetDateTimeUTC() else OffsetDateTime.MIN;
                    val removalTime = StatePlaylists.instance.getWatchLaterRemovalTime(video.url) ?: OffsetDateTime.MIN;
                    if(existing == null && time > removalTime) {
                        StatePlaylists.instance.addToWatchLater(video, false);
                        if(time > OffsetDateTime.MIN)
                            StatePlaylists.instance.setWatchLaterAddTime(video.url, time);
                    }
                }
                for(removal in pack.videoRemovals) {
                    val watchLater = allExisting.firstOrNull { it.url == removal.key } ?: continue;
                    val creation = StatePlaylists.instance.getWatchLaterRemovalTime(watchLater.url) ?: OffsetDateTime.MIN;
                    val removalTime = removal.value.sToOffsetDateTimeUTC()
                    if(creation < removalTime)
                        StatePlaylists.instance.removeFromWatchLater(watchLater, false, removalTime);
                }

                val packReorderTime = pack.reorderTime.sToOffsetDateTimeUTC()
                val localReorderTime = StatePlaylists.instance.getWatchLaterLastReorderTime();
                if(localReorderTime < packReorderTime && pack.ordering != null) {
                    StatePlaylists.instance.updateWatchLaterOrdering(smartMerge(pack.ordering!!, StatePlaylists.instance.getWatchLaterOrdering()), true);
                }
            }

            GJSyncOpcodes.syncHistory -> {
                val dataBody = ByteArray(data.remaining());
                data.get(dataBody);
                val json = String(dataBody, Charsets.UTF_8);
                val history = Serializer.json.decodeFromString<List<HistoryVideo>>(json);
                Logger.i(TAG, "SyncHistory received ${history.size} videos from ${remotePublicKey}");
                if (history.size == 1) {
                    Logger.i(TAG, "SyncHistory received update video '${history[0].video.name}' (url: ${history[0].video.url}) at timestamp ${history[0].position}");
                }

                var lastHistory = OffsetDateTime.MIN;
                for(video in history){
                    val hist = StateHistory.instance.getHistoryByVideo(video.video, true, video.date);
                    if(hist != null)
                        StateHistory.instance.updateHistoryPosition(video.video, hist, true, video.position, video.date)
                    if(lastHistory < video.date)
                        lastHistory = video.date;
                }

                if(lastHistory != OffsetDateTime.MIN && history.size > 1) {
                    val sesData = getSyncSessionData(remotePublicKey);
                    if (lastHistory > sesData.lastHistory) {
                        sesData.lastHistory = lastHistory;
                        saveSyncSessionData(sesData);
                    }
                }
            }
        }
    }

    inline fun <reified T> broadcastJsonData(subOpcode: UByte, data: T) {
        broadcast(Opcode.DATA.value, subOpcode, Json.encodeToString(data));
    }
    fun broadcastData(subOpcode: UByte, data: String) {
        broadcast(Opcode.DATA.value, subOpcode, ByteBuffer.wrap(data.toByteArray(Charsets.UTF_8)));
    }
    fun broadcast(opcode: UByte, subOpcode: UByte, data: String) {
        broadcast(opcode, subOpcode, ByteBuffer.wrap(data.toByteArray(Charsets.UTF_8)));
    }
    fun broadcast(opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        for(session in getAuthorizedSessions()) {
            try {
                session.send(opcode, subOpcode, data);
            }
            catch(ex: Exception) {
                Logger.w(TAG, "Failed to broadcast (opcode = ${opcode}, subOpcode = ${subOpcode}) to ${session.remotePublicKey}: ${ex.message}}", ex);
            }
        }
    }

    fun checkForSync(session: SyncSession) {
        val time = measureTimeMillis {
            //val export = StateBackup.export();
            //session.send(GJSyncOpcodes.syncExport, export.asZip());
            session.sendData(GJSyncOpcodes.syncStateExchange, getSyncSessionDataString(session.remotePublicKey));
        }
        Logger.i(TAG, "Generated and sent sync export in ${time}ms");
    }

    fun stop() {
        syncService?.stop()
        syncService = null
    }


    fun getAll(): List<String> {
        return syncService?.getAllAuthorizedDevices()?.toList() ?: listOf()
    }

    fun getCachedName(publicKey: String): String? {
        return syncService?.getCachedName(publicKey)
    }

    suspend fun delete(publicKey: String) {
        withContext(Dispatchers.IO) {
            try {
                val session = getSession(publicKey)
                session?.let {
                    try {
                        session.unauthorize()
                    } catch (ex: Throwable) {
                        Logger.w(TAG, "Failed to send unauthorize (delete)", ex)
                    }

                    session.close()
                }

                syncService?.removeSession(publicKey)
                syncService?.removeAuthorizedDevice(publicKey)

                withContext(Dispatchers.Main) {
                    deviceRemoved.emit(publicKey)
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to delete", e)
            }
        }
    }

    class StoreBasedSyncDatabaseProvider : ISyncDatabaseProvider {
        private val _authorizedDevices = FragmentedStorage.get<StringArrayStorage>("authorized_devices")
        private val _nameStorage = FragmentedStorage.get<StringStringMapStorage>("sync_remembered_name_storage")
        private val _syncKeyPair = FragmentedStorage.get<StringStorage>("sync_key_pair")
        private val _lastAddressStorage = FragmentedStorage.get<StringStringMapStorage>("sync_last_address_storage")

        override fun isAuthorized(publicKey: String): Boolean = synchronized(_authorizedDevices) { _authorizedDevices.values.contains(publicKey) }
        override fun addAuthorizedDevice(publicKey: String) = synchronized(_authorizedDevices) {
            _authorizedDevices.addDistinct(publicKey)
            _authorizedDevices.save()
        }
        override fun removeAuthorizedDevice(publicKey: String) = synchronized(_authorizedDevices) {
            _authorizedDevices.remove(publicKey)
            _authorizedDevices.save()
        }
        override fun getAllAuthorizedDevices(): Array<String> = synchronized(_authorizedDevices) { _authorizedDevices.values.toTypedArray() }
        override fun getAuthorizedDeviceCount(): Int = synchronized(_authorizedDevices) { _authorizedDevices.values.size }
        override fun getSyncKeyPair(): SyncKeyPair? = try {
            Json.decodeFromString<SyncKeyPair>(GEncryptionProvider.instance.decrypt(_syncKeyPair.value))
        } catch (e: Throwable) { null }
        override fun setSyncKeyPair(value: SyncKeyPair) { _syncKeyPair.setAndSave(GEncryptionProvider.instance.encrypt(Json.encodeToString(value))) }
        override fun getLastAddress(publicKey: String): String? = synchronized(_lastAddressStorage) { _lastAddressStorage.map[publicKey] }
        override fun setLastAddress(publicKey: String, address: String) = synchronized(_lastAddressStorage) {
            _lastAddressStorage.map[publicKey] = address
            _lastAddressStorage.save()
        }
        override fun getDeviceName(publicKey: String): String? = synchronized(_nameStorage) { _nameStorage.map[publicKey] }
        override fun setDeviceName(publicKey: String, name: String) = synchronized(_nameStorage) {
            _nameStorage.map[publicKey] = name
            _nameStorage.save()
        }
    }

    companion object {
        val version = 1
        val RELAY_SERVER = "relay.grayjay.app"
        val SERVICE_NAME = "_gsync._tcp"
        val RELAY_PUBLIC_KEY = "xGbHRzDOvE6plRbQaFgSen82eijF+gxS0yeUaeEErkw="
        val APP_ID = 0x534A5247u //GRayJaySync (GRJS)

        private const val TAG = "StateSync"
        const val PORT = 12315

        private var _instance: StateSync? = null
        val instance: StateSync
            get() {
                if(_instance == null)
                    _instance = StateSync()
                return _instance!!
            }
    }
}