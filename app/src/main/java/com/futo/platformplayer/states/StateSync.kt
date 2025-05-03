package com.futo.platformplayer.states

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.futo.platformplayer.LittleEndianDataInputStream
import com.futo.platformplayer.LittleEndianDataOutputStream
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.activities.SyncShowPairingCodeActivity
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.casting.StateCasting.Companion
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.encryption.GEncryptionProvider
import com.futo.platformplayer.generateReadablePassword
import com.futo.platformplayer.getConnectedSocket
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.Noise
import com.futo.platformplayer.smartMerge
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStringMapStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.stores.StringTMapStorage
import com.futo.platformplayer.sync.SyncSessionData
import com.futo.platformplayer.sync.internal.ChannelSocket
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.internal.IAuthorizable
import com.futo.platformplayer.sync.internal.IChannel
import com.futo.platformplayer.sync.internal.LinkType
import com.futo.platformplayer.sync.internal.Opcode
import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import com.futo.platformplayer.sync.internal.SyncKeyPair
import com.futo.platformplayer.sync.internal.SyncSession
import com.futo.platformplayer.sync.internal.SyncSocketSession
import com.futo.platformplayer.sync.models.SendToDevicePackage
import com.futo.platformplayer.sync.models.SyncPlaylistsPackage
import com.futo.platformplayer.sync.models.SyncSubscriptionGroupsPackage
import com.futo.platformplayer.sync.models.SyncSubscriptionsPackage
import com.futo.platformplayer.sync.models.SyncWatchLaterPackage
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import kotlin.system.measureTimeMillis

class StateSync {
    private val _authorizedDevices = FragmentedStorage.get<StringArrayStorage>("authorized_devices")
    private val _nameStorage = FragmentedStorage.get<StringStringMapStorage>("sync_remembered_name_storage")
    private val _syncKeyPair = FragmentedStorage.get<StringStorage>("sync_key_pair")
    private val _lastAddressStorage = FragmentedStorage.get<StringStringMapStorage>("sync_last_address_storage")
    private val _syncSessionData = FragmentedStorage.get<StringTMapStorage<SyncSessionData>>("syncSessionData")

    private var _serverSocket: ServerSocket? = null
    private var _thread: Thread? = null
    private var _connectThread: Thread? = null
    private var _started = false
    private val _sessions: MutableMap<String, SyncSession> = mutableMapOf()
    private val _lastConnectTimesMdns: MutableMap<String, Long> = mutableMapOf()
    private val _lastConnectTimesIp: MutableMap<String, Long> = mutableMapOf()
    //TODO: Should sync mdns and casting mdns be merged?
    //TODO: Decrease interval that devices are updated
    //TODO: Send less data

    private val _pairingCode: String? = generateReadablePassword(8)
    val pairingCode: String? get() = _pairingCode
    private var _relaySession: SyncSocketSession? = null
    private var _threadRelay: Thread? = null
    private val _remotePendingStatusUpdate = mutableMapOf<String, (complete: Boolean?, message: String) -> Unit>()
    private var _nsdManager: NsdManager? = null
    private val _registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceRegistered: ${serviceInfo.serviceName}")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.v(TAG, "onRegistrationFailed: ${serviceInfo.serviceName} (error code: $errorCode)")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceUnregistered: ${serviceInfo.serviceName}")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.v(TAG, "onUnregistrationFailed: ${serviceInfo.serviceName} (error code: $errorCode)")
        }
    }

    var keyPair: DHState? = null
    var publicKey: String? = null
    val deviceRemoved: Event1<String> = Event1()
    val deviceUpdatedOrAdded: Event2<String, SyncSession> = Event2()

    //TODO: Should authorize acknowledge be implemented?

    fun hasAuthorizedDevice(): Boolean {
        synchronized(_sessions) {
            return _sessions.any{ it.value.connected && it.value.isAuthorized };
        }
    }

    fun start(context: Context) {
        if (_started) {
            Logger.i(TAG, "Already started.")
            return
        }
        _started = true
        _nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        if (Settings.instance.synchronization.connectDiscovered) {
            _nsdManager?.apply {
                discoverServices("_gsync._tcp", NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {
                        Log.d(TAG, "Service discovery started for $regType")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.i(TAG, "Discovery stopped: $serviceType")
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        Log.e(TAG, "service lost: $service")
                        // TODO: Handle service lost, e.g., remove device
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Discovery failed for $serviceType: Error code:$errorCode")
                        try {
                            _nsdManager?.stopServiceDiscovery(this)
                        } catch (e: Throwable) {
                            Logger.w(TAG, "Failed to stop service discovery", e)
                        }
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Stop discovery failed for $serviceType: Error code:$errorCode")
                        try {
                            _nsdManager?.stopServiceDiscovery(this)
                        } catch (e: Throwable) {
                            Logger.w(TAG, "Failed to stop service discovery", e)
                        }
                    }

                    fun addOrUpdate(name: String, adrs: Array<InetAddress>, port: Int, attributes: Map<String, ByteArray>) {
                        if (!Settings.instance.synchronization.connectDiscovered) {
                            return
                        }

                        val urlSafePkey = attributes.get("pk")?.decodeToString() ?: return
                        val pkey = Base64.getEncoder().encodeToString(Base64.getDecoder().decode(urlSafePkey.replace('-', '+').replace('_', '/')))
                        val syncDeviceInfo = SyncDeviceInfo(pkey, adrs.map { it.hostAddress }.toTypedArray(), port, null)
                        val authorized = isAuthorized(pkey)

                        if (authorized && !isConnected(pkey)) {
                            val now = System.currentTimeMillis()
                            val lastConnectTime = synchronized(_lastConnectTimesMdns) {
                                _lastConnectTimesMdns[pkey] ?: 0
                            }

                            //Connect once every 30 seconds, max
                            if (now - lastConnectTime > 30000) {
                                synchronized(_lastConnectTimesMdns) {
                                    _lastConnectTimesMdns[pkey] = now
                                }

                                Logger.i(TAG, "Found device authorized device '${name}' with pkey=$pkey, attempting to connect")

                                try {
                                    connect(syncDeviceInfo)
                                } catch (e: Throwable) {
                                    Logger.i(TAG, "Failed to connect to $pkey", e)
                                }
                            }
                        }
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        Log.v(TAG, "Service discovery success for ${service.serviceType}: $service")
                        addOrUpdate(service.serviceName, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            service.hostAddresses.toTypedArray()
                        } else {
                            if(service.host != null)
                                arrayOf(service.host);
                            else
                                arrayOf();
                        }, service.port, service.attributes)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            _nsdManager?.registerServiceInfoCallback(service, { it.run() }, object : NsdManager.ServiceInfoCallback {
                                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                    Log.v(TAG, "onServiceUpdated: $serviceInfo")
                                    addOrUpdate(serviceInfo.serviceName, serviceInfo.hostAddresses.toTypedArray(), serviceInfo.port, serviceInfo.attributes)
                                }

                                override fun onServiceLost() {
                                    Log.v(TAG, "onServiceLost: $service")
                                    // TODO: Handle service lost
                                }

                                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                    Log.v(TAG, "onServiceInfoCallbackRegistrationFailed: $errorCode")
                                }

                                override fun onServiceInfoCallbackUnregistered() {
                                    Log.v(TAG, "onServiceInfoCallbackUnregistered")
                                }
                            })
                        } else {
                            _nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                    Log.v(TAG, "Resolve failed: $errorCode")
                                }

                                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                    Log.v(TAG, "Resolve Succeeded: $serviceInfo")
                                    addOrUpdate(serviceInfo.serviceName, arrayOf(serviceInfo.host), serviceInfo.port, serviceInfo.attributes)
                                }
                            })
                        }
                    }
                })
            }
        }

        try {
            val syncKeyPair = Json.decodeFromString<SyncKeyPair>(GEncryptionProvider.instance.decrypt(_syncKeyPair.value))
            val p = Noise.createDH(dh)
            p.setPublicKey(syncKeyPair.publicKey.base64ToByteArray(), 0)
            p.setPrivateKey(syncKeyPair.privateKey.base64ToByteArray(), 0)
            keyPair = p
        } catch (e: Throwable) {
            //Sync key pair non-existing, invalid or lost
            val p = Noise.createDH(dh)
            p.generateKeyPair()

            val publicKey = ByteArray(p.publicKeyLength)
            p.getPublicKey(publicKey, 0)
            val privateKey = ByteArray(p.privateKeyLength)
            p.getPrivateKey(privateKey, 0)

            val syncKeyPair = SyncKeyPair(1, publicKey.toBase64(), privateKey.toBase64())
            _syncKeyPair.setAndSave(GEncryptionProvider.instance.encrypt(Json.encodeToString(syncKeyPair)))

            Logger.e(TAG, "Failed to load existing key pair", e)
            keyPair = p
        }

        publicKey = keyPair?.let {
            val pkey = ByteArray(it.publicKeyLength)
            it.getPublicKey(pkey, 0)
            return@let pkey.toBase64()
        }

        if (Settings.instance.synchronization.broadcast) {
            val pk = publicKey
            val nsdManager = _nsdManager

            if (pk != null && nsdManager != null) {
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = getDeviceName()
                    serviceType = "_gsync._tcp"
                    port = PORT
                    setAttribute("pk", pk.replace('+', '-').replace('/', '_').replace("=", ""))
                }

                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, _registrationListener)
            }
        }

        Logger.i(TAG, "Sync key pair initialized (public key = ${publicKey})")

        _thread = Thread {
            try {
                val serverSocket = ServerSocket(PORT)
                _serverSocket = serverSocket

                Log.i(TAG, "Running on port ${PORT} (TCP)")

                while (_started) {
                    val socket = serverSocket.accept()
                    val session = createSocketSession(socket, true)
                    session.startAsResponder()
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to bind server socket to port ${PORT}", e)
                UIDialogs.toast("Failed to start sync, port in use")
            }
        }.apply { start() }

        if (Settings.instance.synchronization.connectLast) {
            _connectThread = Thread {
                Log.i(TAG, "Running auto reconnector")

                while (_started) {
                    val authorizedDevices = synchronized(_authorizedDevices) {
                        return@synchronized _authorizedDevices.values
                    }

                    val lastKnownMap = synchronized(_lastAddressStorage) {
                        return@synchronized _lastAddressStorage.map.toMap()
                    }

                    val addressesToConnect = authorizedDevices.mapNotNull {
                        val connected = isConnected(it)
                        if (connected) {
                            return@mapNotNull null
                        }

                        val lastKnownAddress = lastKnownMap[it] ?: return@mapNotNull null
                        return@mapNotNull Pair(it, lastKnownAddress)
                    }

                    for (connectPair in addressesToConnect) {
                        try {
                            val now = System.currentTimeMillis()
                            val lastConnectTime = synchronized(_lastConnectTimesIp) {
                                _lastConnectTimesIp[connectPair.first] ?: 0
                            }

                            //Connect once every 30 seconds, max
                            if (now - lastConnectTime > 30000) {
                                synchronized(_lastConnectTimesIp) {
                                    _lastConnectTimesIp[connectPair.first] = now
                                }

                                Logger.i(TAG, "Attempting to connect to authorized device by last known IP '${connectPair.first}' with pkey=${connectPair.first}")
                                connect(arrayOf(connectPair.second), PORT, connectPair.first, null)
                            }
                        } catch (e: Throwable) {
                            Logger.i(TAG, "Failed to connect to " + connectPair.first, e)
                        }
                    }
                    Thread.sleep(5000)
                }
            }.apply { start() }
        }

        if (Settings.instance.synchronization.discoverThroughRelay) {
            _threadRelay = Thread {
                while (_started) {
                    try {
                        Log.i(TAG, "Starting relay session...")

                        var socketClosed = false;
                        val socket = Socket(RELAY_SERVER, 9000)
                        _relaySession = SyncSocketSession(
                            (socket.remoteSocketAddress as InetSocketAddress).address.hostAddress!!,
                            keyPair!!,
                            socket,
                            isHandshakeAllowed = { linkType, syncSocketSession, publicKey, pairingCode, appId -> isHandshakeAllowed(linkType, syncSocketSession, publicKey, pairingCode, appId) },
                            onNewChannel = { _, c ->
                                val remotePublicKey = c.remotePublicKey
                                if (remotePublicKey == null) {
                                    Log.e(TAG, "Remote public key should never be null in onNewChannel.")
                                    return@SyncSocketSession
                                }

                                Log.i(TAG, "New channel established from relay (pk: '$remotePublicKey').")

                                var session: SyncSession?
                                synchronized(_sessions) {
                                    session = _sessions[remotePublicKey]
                                    if (session == null) {
                                        val remoteDeviceName = synchronized(_nameStorage) {
                                            _nameStorage.get(remotePublicKey)
                                        }
                                        session = createNewSyncSession(remotePublicKey, remoteDeviceName)
                                        _sessions[remotePublicKey] = session!!
                                    }
                                    session!!.addChannel(c)
                                }

                                c.setDataHandler { _, channel, opcode, subOpcode, data ->
                                    session?.handlePacket(opcode, subOpcode, data)
                                }
                                c.setCloseHandler { channel ->
                                    session?.removeChannel(channel)
                                }
                            },
                            onChannelEstablished = { _, channel, isResponder ->
                                handleAuthorization(channel, isResponder)
                            },
                            onClose = { socketClosed = true },
                            onHandshakeComplete = { relaySession ->
                                Thread {
                                    try {
                                        while (_started && !socketClosed) {
                                            val unconnectedAuthorizedDevices = synchronized(_authorizedDevices) {
                                                _authorizedDevices.values.filter { !isConnected(it) }.toTypedArray()
                                            }

                                            relaySession.publishConnectionInformation(unconnectedAuthorizedDevices, PORT, Settings.instance.synchronization.discoverThroughRelay, false, false, Settings.instance.synchronization.discoverThroughRelay && Settings.instance.synchronization.connectThroughRelay)

                                            Logger.v(TAG, "Requesting ${unconnectedAuthorizedDevices.size} devices connection information")
                                            val connectionInfos = runBlocking { relaySession.requestBulkConnectionInfo(unconnectedAuthorizedDevices) }
                                            Logger.v(TAG, "Received ${connectionInfos.size} devices connection information")

                                            for ((targetKey, connectionInfo) in connectionInfos) {
                                                val potentialLocalAddresses = connectionInfo.ipv4Addresses.union(connectionInfo.ipv6Addresses)
                                                    .filter { it != connectionInfo.remoteIp }
                                                if (connectionInfo.allowLocalDirect && Settings.instance.synchronization.connectLocalDirectThroughRelay) {
                                                    Thread {
                                                        try {
                                                            Log.v(TAG, "Attempting to connect directly, locally to '$targetKey'.")
                                                            connect(potentialLocalAddresses.map { it }.toTypedArray(), PORT, targetKey, null)
                                                        } catch (e: Throwable) {
                                                            Log.e(TAG, "Failed to start direct connection using connection info with $targetKey.", e)
                                                        }
                                                    }.start()
                                                }

                                                if (connectionInfo.allowRemoteDirect) {
                                                    // TODO: Implement direct remote connection if needed
                                                }

                                                if (connectionInfo.allowRemoteHolePunched) {
                                                    // TODO: Implement hole punching if needed
                                                }

                                                if (connectionInfo.allowRemoteRelayed && Settings.instance.synchronization.connectThroughRelay) {
                                                    try {
                                                        Logger.v(TAG, "Attempting relayed connection with '$targetKey'.")
                                                        runBlocking { relaySession.startRelayedChannel(targetKey, APP_ID, null) }
                                                    } catch (e: Throwable) {
                                                        Logger.e(TAG, "Failed to start relayed channel with $targetKey.", e)
                                                    }
                                                }
                                            }

                                            Thread.sleep(15000)
                                        }
                                    } catch (e: Throwable) {
                                        Logger.e(TAG, "Unhandled exception in relay session.", e)
                                        relaySession.stop()
                                    }
                                }.start()
                            }
                        )

                        _relaySession!!.authorizable = object : IAuthorizable {
                            override val isAuthorized: Boolean get() = true
                        }

                        _relaySession!!.runAsInitiator(RELAY_PUBLIC_KEY, APP_ID, null)

                        Log.i(TAG, "Started relay session.")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Relay session failed.", e)
                    } finally {
                        _relaySession?.stop()
                        _relaySession = null
                        Thread.sleep(5000)
                    }
                }
            }.apply { start() }
        }


    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val model = Build.MODEL

        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            "$manufacturer $model".replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    fun isConnected(publicKey: String): Boolean {
        return synchronized(_sessions) {
            _sessions[publicKey]?.connected ?: false
        }
    }

    fun isAuthorized(publicKey: String): Boolean {
        return synchronized(_authorizedDevices) {
            _authorizedDevices.values.contains(publicKey)
        }
    }

    fun getSession(publicKey: String): SyncSession? {
        return synchronized(_sessions) {
            _sessions[publicKey]
        }
    }
    fun getSessions(): List<SyncSession> {
        synchronized(_sessions) {
            return _sessions.values.toList()
        }
    }
    fun getAuthorizedSessions(): List<SyncSession> {
        synchronized(_sessions) {
            return _sessions.values.filter { it.isAuthorized }.toList()
        }
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

    private fun unauthorize(remotePublicKey: String) {
        Logger.i(TAG, "${remotePublicKey} unauthorized received")
        _authorizedDevices.remove(remotePublicKey)
        _authorizedDevices.save()
        deviceRemoved.emit(remotePublicKey)
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
                    val removalTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(removal.value, 0), ZoneOffset.UTC);
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
                    else if(existing.dateUpdate.toLocalDateTime() < playlist.dateUpdate.toLocalDateTime()) {
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
                    val removalTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(removal.value, 0), ZoneOffset.UTC);
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
                    val time = if(pack.videoAdds != null && pack.videoAdds.containsKey(video.url)) OffsetDateTime.ofInstant(Instant.ofEpochSecond(pack.videoAdds[video.url] ?: 0), ZoneOffset.UTC) else OffsetDateTime.MIN;

                    if(existing == null) {
                        StatePlaylists.instance.addToWatchLater(video, false);
                        if(time > OffsetDateTime.MIN)
                            StatePlaylists.instance.setWatchLaterAddTime(video.url, time);
                    }
                }
                for(removal in pack.videoRemovals) {
                    val watchLater = allExisting.firstOrNull { it.url == removal.key } ?: continue;
                    val creation = StatePlaylists.instance.getWatchLaterRemovalTime(watchLater.url) ?: OffsetDateTime.MIN;
                    val removalTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(removal.value), ZoneOffset.UTC);
                    if(creation < removalTime)
                        StatePlaylists.instance.removeFromWatchLater(watchLater, false, removalTime);
                }

                val packReorderTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(pack.reorderTime), ZoneOffset.UTC);
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

    private fun onAuthorized(remotePublicKey: String) {
        synchronized(_remotePendingStatusUpdate) {
            _remotePendingStatusUpdate.remove(remotePublicKey)?.invoke(true, "Authorized")
        }
    }

    private fun onUnuthorized(remotePublicKey: String) {
        synchronized(_remotePendingStatusUpdate) {
            _remotePendingStatusUpdate.remove(remotePublicKey)?.invoke(false, "Unauthorized")
        }
    }

    private fun createNewSyncSession(remotePublicKey: String, remoteDeviceName: String?): SyncSession {
        return SyncSession(
            remotePublicKey,
            onAuthorized = { it, isNewlyAuthorized, isNewSession ->
                if (!isNewSession) {
                    return@SyncSession
                }

                it.remoteDeviceName?.let { remoteDeviceName ->
                    synchronized(_nameStorage) {
                        _nameStorage.setAndSave(remotePublicKey, remoteDeviceName)
                    }
                }

                Logger.i(TAG, "$remotePublicKey authorized (name: ${it.displayName})")
                onAuthorized(remotePublicKey)
                _authorizedDevices.addDistinct(remotePublicKey)
                _authorizedDevices.save()
                deviceUpdatedOrAdded.emit(it.remotePublicKey, it)

                checkForSync(it);
            },
            onUnauthorized = {
                unauthorize(remotePublicKey)

                Logger.i(TAG, "$remotePublicKey unauthorized (name: ${it.displayName})")
                onUnuthorized(remotePublicKey)

                synchronized(_sessions) {
                    it.close()
                    _sessions.remove(remotePublicKey)
                }
            },
            onConnectedChanged = { it, connected ->
                Logger.i(TAG, "$remotePublicKey connected: $connected")
                deviceUpdatedOrAdded.emit(it.remotePublicKey, it)
            },
            onClose = {
                Logger.i(TAG, "$remotePublicKey closed")

                synchronized(_sessions)
                {
                    _sessions.remove(it.remotePublicKey)
                }

                deviceRemoved.emit(it.remotePublicKey)

                synchronized(_remotePendingStatusUpdate) {
                    _remotePendingStatusUpdate.remove(remotePublicKey)?.invoke(false, "Connection closed")
                }
            },
            dataHandler = { it, opcode, subOpcode, data ->
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
            },
            remoteDeviceName
        )
    }

    private fun isHandshakeAllowed(linkType: LinkType, syncSocketSession: SyncSocketSession, publicKey: String, pairingCode: String?, appId: UInt): Boolean {
        Log.v(TAG, "Check if handshake allowed from '$publicKey' (app id: $appId).")
        if (publicKey == RELAY_PUBLIC_KEY)
            return true

        synchronized(_authorizedDevices) {
            if (_authorizedDevices.values.contains(publicKey)) {
                if (linkType == LinkType.Relayed && !Settings.instance.synchronization.connectThroughRelay)
                    return false
                return true
            }
        }

        Log.v(TAG, "Check if handshake allowed with pairing code '$pairingCode' with active pairing code '$_pairingCode' (app id: $appId).")
        if (_pairingCode == null || pairingCode.isNullOrEmpty())
            return false

        if (linkType == LinkType.Relayed && !Settings.instance.synchronization.pairThroughRelay)
            return false

        return _pairingCode == pairingCode
    }

    private fun createSocketSession(socket: Socket, isResponder: Boolean): SyncSocketSession {
        var session: SyncSession? = null
        var channelSocket: ChannelSocket? = null
        return SyncSocketSession(
            (socket.remoteSocketAddress as InetSocketAddress).address.hostAddress!!,
            keyPair!!,
            socket,
            onClose = { s ->
                if (channelSocket != null)
                    session?.removeChannel(channelSocket!!)
            },
            isHandshakeAllowed = { linkType, syncSocketSession, publicKey, pairingCode, appId -> isHandshakeAllowed(linkType, syncSocketSession, publicKey, pairingCode, appId) },
            onHandshakeComplete = { s ->
                val remotePublicKey = s.remotePublicKey
                if (remotePublicKey == null) {
                    s.stop()
                    return@SyncSocketSession
                }

                Logger.i(TAG, "Handshake complete with (LocalPublicKey = ${s.localPublicKey}, RemotePublicKey = ${s.remotePublicKey})")

                channelSocket = ChannelSocket(s)

                synchronized(_sessions) {
                    session = _sessions[s.remotePublicKey]
                    if (session == null) {
                        val remoteDeviceName = synchronized(_nameStorage) {
                            _nameStorage.get(remotePublicKey)
                        }

                        synchronized(_lastAddressStorage) {
                            _lastAddressStorage.setAndSave(remotePublicKey, s.remoteAddress)
                        }

                        session = createNewSyncSession(remotePublicKey, remoteDeviceName)
                        _sessions[remotePublicKey] = session!!
                    }
                    session!!.addChannel(channelSocket!!)
                }

                handleAuthorization(channelSocket!!, isResponder)
            },
            onData = { s, opcode, subOpcode, data ->
                session?.handlePacket(opcode, subOpcode, data)
            }
        )
    }

    private fun handleAuthorization(channel: IChannel, isResponder: Boolean) {
        val syncSession = channel.syncSession!!
        val remotePublicKey = channel.remotePublicKey!!

        if (isResponder) {
            val isAuthorized = synchronized(_authorizedDevices) {
                _authorizedDevices.values.contains(remotePublicKey)
            }

            if (!isAuthorized) {
                val scope = StateApp.instance.scopeOrNull
                val activity = SyncShowPairingCodeActivity.activity

                if (scope != null && activity != null) {
                    scope.launch(Dispatchers.Main) {
                        UIDialogs.showConfirmationDialog(activity, "Allow connection from ${remotePublicKey}?",
                            action = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        syncSession.authorize()
                                        Logger.i(TAG, "Connection authorized for $remotePublicKey by confirmation")
                                    } catch (e: Throwable) {
                                        Logger.e(TAG, "Failed to send authorize", e)
                                    }
                                }
                            },
                            cancelAction = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        unauthorize(remotePublicKey)
                                    } catch (e: Throwable) {
                                        Logger.w(TAG, "Failed to send unauthorize", e)
                                    }

                                    syncSession.close()
                                    synchronized(_sessions) {
                                        _sessions.remove(remotePublicKey)
                                    }
                                }
                            }
                        )
                    }
                } else {
                    val publicKey = syncSession.remotePublicKey
                    syncSession.unauthorize()
                    syncSession.close()

                    synchronized(_sessions) {
                        _sessions.remove(publicKey)
                    }

                    Logger.i(TAG, "Connection unauthorized for $remotePublicKey because not authorized and not on pairing activity to ask")
                }
            } else {
                //Responder does not need to check because already approved
                syncSession.authorize()
                Logger.i(TAG, "Connection authorized for $remotePublicKey because already authorized")
            }
        } else {
            //Initiator does not need to check because the manual action of scanning the QR counts as approval
            syncSession.authorize()
            Logger.i(TAG, "Connection authorized for $remotePublicKey because initiator")
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
        _started = false
        _nsdManager?.unregisterService(_registrationListener)

        _serverSocket?.close()
        _serverSocket = null

        _thread?.interrupt()
        _thread = null
        _connectThread?.interrupt()
        _connectThread = null
        _threadRelay?.interrupt()
        _threadRelay = null

        _relaySession?.stop()
        _relaySession = null
    }

    fun connect(deviceInfo: SyncDeviceInfo, onStatusUpdate: ((complete: Boolean?, message: String) -> Unit)? = null) {
        try {
            connect(deviceInfo.addresses, deviceInfo.port, deviceInfo.publicKey, deviceInfo.pairingCode, onStatusUpdate)
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to connect directly", e)
            val relaySession = _relaySession
            if (relaySession != null && Settings.instance.synchronization.pairThroughRelay) {
                onStatusUpdate?.invoke(null, "Connecting via relay...")

                runBlocking {
                    if (onStatusUpdate != null) {
                        synchronized(_remotePendingStatusUpdate) {
                            _remotePendingStatusUpdate[deviceInfo.publicKey] = onStatusUpdate
                        }
                    }
                    relaySession.startRelayedChannel(deviceInfo.publicKey, APP_ID, deviceInfo.pairingCode)
                }
            } else {
                throw e
            }
        }
    }

    fun connect(addresses: Array<String>, port: Int, publicKey: String, pairingCode: String?, onStatusUpdate: ((complete: Boolean?, message: String) -> Unit)? = null): SyncSocketSession {
        onStatusUpdate?.invoke(null, "Connecting directly...")
        val socket = getConnectedSocket(addresses.map { InetAddress.getByName(it) }, port) ?: throw Exception("Failed to connect")
        onStatusUpdate?.invoke(null, "Handshaking...")

        val session = createSocketSession(socket, false)
        if (onStatusUpdate != null) {
            synchronized(_remotePendingStatusUpdate) {
                _remotePendingStatusUpdate[publicKey] = onStatusUpdate
            }
        }

        session.startAsInitiator(publicKey, APP_ID, pairingCode)
        return session
    }

    fun getAll(): List<String> {
        synchronized(_authorizedDevices) {
            return _authorizedDevices.values.toList()
        }
    }

    fun getCachedName(publicKey: String): String? {
        return synchronized(_nameStorage) {
            _nameStorage.get(publicKey)
        }
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

                synchronized(_sessions) {
                    _sessions.remove(publicKey)
                }

                synchronized(_authorizedDevices) {
                    _authorizedDevices.remove(publicKey)
                }
                _authorizedDevices.save()

                withContext(Dispatchers.Main) {
                    deviceRemoved.emit(publicKey)
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to delete", e)
            }
        }

    }

    companion object {
        val dh = "25519"
        val pattern = "IK"
        val cipher = "ChaChaPoly"
        val hash = "BLAKE2b"
        var protocolName = "Noise_${pattern}_${dh}_${cipher}_${hash}"
        val version = 1
        val RELAY_SERVER = "relay.grayjay.app"
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