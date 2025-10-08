package com.futo.platformplayer.sync.internal

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.futo.platformplayer.Settings
import com.futo.platformplayer.ensureNotMainThread
import com.futo.platformplayer.generateReadablePassword
import com.futo.platformplayer.getConnectedSocket
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.Noise
import com.futo.platformplayer.states.StateSync
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.base64UrlToByteArray
import com.futo.polycentric.core.toBase64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Base64
import java.util.Locale
import kotlin.math.min

public data class SyncServiceSettings(
    val listenerPort: Int = 12315,
    val mdnsBroadcast: Boolean = true,
    val mdnsConnectDiscovered: Boolean = true,
    val bindListener: Boolean = true,
    val connectLastKnown: Boolean = true,
    val relayHandshakeAllowed: Boolean = true,
    val relayPairAllowed: Boolean = true,
    val relayEnabled: Boolean = true,
    val relayConnectDirect: Boolean = true,
    val relayConnectRelayed: Boolean = true
)

interface ISyncDatabaseProvider {
    fun isAuthorized(publicKey: String): Boolean
    fun addAuthorizedDevice(publicKey: String)
    fun removeAuthorizedDevice(publicKey: String)
    fun getAllAuthorizedDevices(): Array<String>?
    fun getAuthorizedDeviceCount(): Int
    fun getSyncKeyPair(): SyncKeyPair?
    fun setSyncKeyPair(value: SyncKeyPair)
    fun getLastAddress(publicKey: String): String?
    fun setLastAddress(publicKey: String, address: String)
    fun getDeviceName(publicKey: String): String?
    fun setDeviceName(publicKey: String, name: String)
}

class SyncService(
    private val serviceName: String,
    private val relayServer: String,
    private val relayPublicKey: String,
    private val appId: UInt,
    private val database: ISyncDatabaseProvider,
    private val settings: SyncServiceSettings = SyncServiceSettings()
) {
    private var _serverSocket: ServerSocketChannel? = null
    private val _sessions: MutableMap<String, SyncSession> = mutableMapOf()
    private val _lastConnectTimesMdns: MutableMap<String, Long> = mutableMapOf()
    private val _lastConnectTimesIp: MutableMap<String, Long> = mutableMapOf()
    var serverSocketFailedToStart = false
    var serverSocketStarted = false
    var relayConnected = false
    //TODO: Should sync mdns and casting mdns be merged?
    //TODO: Decrease interval that devices are updated
    //TODO: Send less data

    private val _pairingCode: String? = generateReadablePassword(8)
    val pairingCode: String? get() = _pairingCode
    private var _relaySession: SyncSocketSession? = null
    private val _remotePendingStatusUpdateRelayed = mutableMapOf<String, (complete: Boolean?, message: String) -> Unit>()
    private val _remotePendingStatusUpdateDirect = mutableMapOf<String, (complete: Boolean?, message: String) -> Unit>()
    private var _nsdManager: NsdManager? = null
    @Volatile private var _scope: CoroutineScope? = null
    private val _mdnsCache = mutableMapOf<String, SyncDeviceInfo>()
    private var _discoveryListener: NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started for $regType")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "service lost: $service")
            val urlSafePkey = service.attributes["pk"]?.decodeToString() ?: return
            val pkey = urlSafePkey.base64UrlToByteArray().toBase64()
            synchronized(_mdnsCache) {
                _mdnsCache.remove(pkey)
            }
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
            val pkey = urlSafePkey.base64UrlToByteArray().toBase64()
            val syncDeviceInfo = SyncDeviceInfo(pkey, adrs.map { it.hostAddress }.toTypedArray(), port, null)

            synchronized(_mdnsCache) {
                _mdnsCache[pkey] = syncDeviceInfo
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
                        val urlSafePkey = service.attributes["pk"]?.decodeToString() ?: return
                        val pkey = urlSafePkey.base64UrlToByteArray().toBase64()
                        synchronized(_mdnsCache) {
                            _mdnsCache.remove(pkey)
                        }
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
    }

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

    var onAuthorized: ((SyncSession, Boolean, Boolean) -> Unit)? = null
    var onUnauthorized: ((SyncSession) -> Unit)? = null
    var onConnectedChanged: ((SyncSession, Boolean) -> Unit)? = null
    var onClose: ((SyncSession) -> Unit)? = null
    var onData: ((SyncSession, UByte, UByte, ByteBuffer) -> Unit)? = null
    var authorizePrompt: ((String, (Boolean) -> Unit) -> Unit)? = null

    fun start(context: Context) {
        if (_scope != null) {
            Log.i(TAG, "Already started.")
            return
        }

        Log.i(TAG, "Start SyncService.")
        _scope = CoroutineScope(Dispatchers.IO)

        try {
            val syncKeyPair = database.getSyncKeyPair() ?: throw Exception("SyncKeyPair not found")
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
            database.setSyncKeyPair(syncKeyPair)

            Logger.e(TAG, "Failed to load existing key pair", e)
            keyPair = p
        }

        publicKey = keyPair?.let {
            val pkey = ByteArray(it.publicKeyLength)
            it.getPublicKey(pkey, 0)
            return@let pkey.toBase64()
        }

        _nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        if (settings.mdnsConnectDiscovered) {
            startMdnsRetryLoop()
        }

        if (settings.mdnsBroadcast) {
            val pk = publicKey
            val nsdManager = _nsdManager

            if (pk != null && nsdManager != null) {
                val sn = serviceName
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = getDeviceName()
                    serviceType = sn
                    port = settings.listenerPort
                    setAttribute("pk", pk.replace('+', '-').replace('/', '_').replace("=", ""))
                }

                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, _registrationListener)
            }
        }

        Logger.i(TAG, "Sync key pair initialized (public key = $publicKey)")

        serverSocketStarted = false
        if (settings.bindListener) {
            startListener()
        }

        relayConnected = false
        if (settings.relayEnabled) {
            startRelayLoop()
        }

        if (settings.connectLastKnown) {
            startConnectLastLoop()
        }
    }

    private fun startListener() {
        serverSocketFailedToStart = false
        serverSocketStarted = false
        _scope?.launch(Dispatchers.IO) {
            try {
                val serverSocket = ServerSocketChannel.open()
                serverSocket.socket().bind(InetSocketAddress("0.0.0.0", settings.listenerPort))
                _serverSocket = serverSocket

                Log.i(TAG, "Running on port ${settings.listenerPort} (TCP)")
                serverSocketStarted = true

                while (isActive) {
                    val socket = serverSocket.accept()
                    //TODO: Switch to SocketChannel?
                    val session = createSocketSession(socket.socket(), true)
                    session.startAsResponder()
                }
            } catch (e: ClosedChannelException) {
                // normal shutdown
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to bind server socket to port ${settings.listenerPort}", e)
                serverSocketFailedToStart = true
            } finally {
                serverSocketStarted = false
            }
        }
    }

    private fun startMdnsRetryLoop() {
        _nsdManager?.apply {
            discoverServices(serviceName, NsdManager.PROTOCOL_DNS_SD, _discoveryListener)
        }

        _scope?.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val pairs = synchronized (_mdnsCache) { _mdnsCache.toList() }
                    for ((pkey, info) in pairs) {
                        if (!database.isAuthorized(pkey) || getLinkType(pkey) == LinkType.Direct) continue

                        val last = synchronized(_lastConnectTimesMdns) {
                            _lastConnectTimesMdns[pkey] ?: 0L
                        }
                        if (now - last > 30_000L) {
                            synchronized(_lastConnectTimesMdns) {
                                _lastConnectTimesMdns[pkey] = now
                            }
                            try {
                                Log.i(TAG, "MDNS-retry: connecting to $pkey")
                                connect(info)
                                if (!isActive) break
                            } catch (ex: Throwable) {
                                Log.w(TAG, "MDNS retry failed for $pkey", ex)
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    Log.e(TAG, "Error in MDNS retry loop", ex)
                }
                delay(5000)
            }
        }
    }

    private fun startConnectLastLoop() {
        _scope?.launch(Dispatchers.IO) {
            Log.i(TAG, "Running auto reconnector")

            while (isActive) {
                val authorizedDevices = database.getAllAuthorizedDevices()?.toList() ?: listOf()
                val addressesToConnect = authorizedDevices.mapNotNull {
                    val connectedDirectly = getLinkType(it) == LinkType.Direct
                    if (connectedDirectly) {
                        return@mapNotNull null
                    }

                    val lastKnownAddress = database.getLastAddress(it) ?: return@mapNotNull null
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

                            Log.i(TAG, "Attempting to connect to authorized device by last known IP '${connectPair.first}' with pkey=${connectPair.first}")
                            connect(arrayOf(connectPair.second), settings.listenerPort, connectPair.first, null)
                        }
                    } catch (e: Throwable) {
                        Log.i(TAG, "Failed to connect to " + connectPair.first, e)
                    }
                }
                delay(5000)
            }
        }
    }

    private fun startRelayLoop() {
        relayConnected = false
        _scope?.launch(Dispatchers.IO) {
            try {
                var backoffs: Array<Long> = arrayOf(1000, 5000, 10000, 20000)
                var backoffIndex = 0;

                while (isActive) {
                    try {
                        Log.i(TAG, "Starting relay session...")
                        relayConnected = false

                        var socketClosed = false;
                        val socket = Socket(relayServer, 9000)
                        _relaySession = SyncSocketSession(
                            (socket.remoteSocketAddress as InetSocketAddress).address.hostAddress!!,
                            keyPair!!,
                            socket,
                            isHandshakeAllowed = { linkType, syncSocketSession, publicKey, pairingCode, appId ->
                                isHandshakeAllowed(
                                    linkType,
                                    syncSocketSession,
                                    publicKey,
                                    pairingCode,
                                    appId
                                )
                            },
                            onNewChannel = { _, c ->
                                val remotePublicKey = c.remotePublicKey
                                if (remotePublicKey == null) {
                                    Log.e(
                                        TAG,
                                        "Remote public key should never be null in onNewChannel."
                                    )
                                    return@SyncSocketSession
                                }

                                Log.i(
                                    TAG,
                                    "New channel established from relay (pk: '$remotePublicKey')."
                                )

                                var session: SyncSession?
                                synchronized(_sessions) {
                                    session = _sessions[remotePublicKey]
                                    if (session == null) {
                                        val remoteDeviceName =
                                            database.getDeviceName(remotePublicKey)
                                        session =
                                            createNewSyncSession(remotePublicKey, remoteDeviceName)
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
                                backoffIndex = 0

                                Thread {
                                    try {
                                        while (isActive && !socketClosed) {
                                            val unconnectedAuthorizedDevices =
                                                database.getAllAuthorizedDevices()
                                                    ?.filter {
                                                        if (Settings.instance.synchronization.connectLocalDirectThroughRelay) {
                                                            getLinkType(it) != LinkType.Direct
                                                        } else {
                                                            !isConnected(it)
                                                        }
                                                    }?.toTypedArray() ?: arrayOf()
                                            relaySession.publishConnectionInformation(
                                                unconnectedAuthorizedDevices,
                                                settings.listenerPort,
                                                settings.relayConnectDirect,
                                                false,
                                                false,
                                                settings.relayConnectRelayed
                                            )

                                            Logger.v(
                                                TAG,
                                                "Requesting ${unconnectedAuthorizedDevices.size} devices connection information"
                                            )
                                            val connectionInfos = runBlocking {
                                                relaySession.requestBulkConnectionInfo(
                                                    unconnectedAuthorizedDevices
                                                )
                                            }
                                            Logger.v(
                                                TAG,
                                                "Received ${connectionInfos.size} devices connection information"
                                            )

                                            for ((targetKey, connectionInfo) in connectionInfos) {
                                                val potentialLocalAddresses =
                                                    connectionInfo.ipv4Addresses
                                                        .filter { it != connectionInfo.remoteIp }
                                                if (getLinkType(targetKey) != LinkType.Direct && connectionInfo.allowLocalDirect && Settings.instance.synchronization.connectLocalDirectThroughRelay) {
                                                    launch(Dispatchers.IO) {
                                                        try {
                                                            Log.v(TAG, "Attempting to connect directly, locally to '$targetKey'.")
                                                            connect(potentialLocalAddresses.map { it }.toTypedArray(), settings.listenerPort, targetKey, null)
                                                        } catch (e: Throwable) {
                                                            Log.e(TAG, "Failed to start direct connection using connection info with $targetKey.", e)
                                                        }
                                                    }
                                                }

                                                if (connectionInfo.allowRemoteDirect) {
                                                    // TODO: Implement direct remote connection if needed
                                                }

                                                if (connectionInfo.allowRemoteHolePunched) {
                                                    // TODO: Implement hole punching if needed
                                                }

                                                if (!isConnected(targetKey) && connectionInfo.allowRemoteRelayed && Settings.instance.synchronization.connectThroughRelay) {
                                                    try {
                                                        Logger.v(
                                                            TAG,
                                                            "Attempting relayed connection with '$targetKey'."
                                                        )
                                                        runBlocking {
                                                            relaySession.startRelayedChannel(
                                                                targetKey,
                                                                appId,
                                                                null
                                                            )
                                                        }
                                                    } catch (e: Throwable) {
                                                        Logger.e(
                                                            TAG,
                                                            "Failed to start relayed channel with $targetKey.",
                                                            e
                                                        )
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

                        relayConnected = true
                        _relaySession!!.runAsInitiator(relayPublicKey, appId, null)

                        Log.i(TAG, "Started relay session.")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Relay session failed.", e)
                    } finally {
                        relayConnected = false
                        _relaySession?.stop()
                        _relaySession = null
                        Thread.sleep(backoffs[min(backoffs.size - 1, backoffIndex++)])
                    }
                }
            } catch (ex: Throwable) {
                Log.i(TAG, "Unhandled exception in relay loop.", ex)
            }
        }
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
                        val remoteDeviceName = database.getDeviceName(remotePublicKey)
                        database.setLastAddress(remotePublicKey, s.remoteAddress)
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
            val isAuthorized = database.isAuthorized(remotePublicKey)
            if (!isAuthorized) {
                val ap = this.authorizePrompt
                if (ap == null) {
                    try {
                        Logger.i(TAG, "$remotePublicKey unauthorized because AuthorizePrompt is null")
                        syncSession.unauthorize()
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to send authorize result.", e)
                    }
                    return;
                }

                ap.invoke(remotePublicKey) {
                    try {
                        _scope?.launch(Dispatchers.IO) {
                            if (it) {
                                Logger.i(TAG, "$remotePublicKey manually authorized")
                                syncSession.authorize()
                            } else {
                                Logger.i(TAG, "$remotePublicKey manually unauthorized")
                                syncSession.unauthorize()
                                syncSession.close()
                            }
                        }
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to send authorize result.")
                    }
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

    private fun isHandshakeAllowed(linkType: LinkType, syncSocketSession: SyncSocketSession, publicKey: String, pairingCode: String?, appId: UInt): Boolean {
        Log.v(TAG, "Check if handshake allowed from '$publicKey' (app id: $appId).")
        if (publicKey == StateSync.RELAY_PUBLIC_KEY)
            return true

        if (database.isAuthorized(publicKey)) {
            if (linkType == LinkType.Relayed && !settings.relayHandshakeAllowed)
                return false
            return true
        }

        Log.v(TAG, "Check if handshake allowed with pairing code '$pairingCode' with active pairing code '$_pairingCode' (app id: $appId).")
        if (_pairingCode == null || pairingCode.isNullOrEmpty())
            return false

        if (linkType == LinkType.Relayed && !settings.relayPairAllowed)
            return false

        return _pairingCode == pairingCode
    }

    private fun sendRemotePendingStatusUpdate(remotePublicKey: String, complete: Boolean, message: String) {
        synchronized(_remotePendingStatusUpdateDirect) {
            _remotePendingStatusUpdateDirect.remove(remotePublicKey)?.invoke(complete, message)
        }
        synchronized(_remotePendingStatusUpdateRelayed) {
            _remotePendingStatusUpdateRelayed.remove(remotePublicKey)?.invoke(complete, message)
        }
    }

    private fun createNewSyncSession(rpk: String, remoteDeviceName: String?): SyncSession {
        val remotePublicKey = rpk.base64ToByteArray().toBase64()
        return SyncSession(
            remotePublicKey,
            onAuthorized = { it, isNewlyAuthorized, isNewSession ->
                sendRemotePendingStatusUpdate(remotePublicKey, true, "Authorized")

                if (isNewSession) {
                    it.remoteDeviceName?.let { remoteDeviceName ->
                        database.setDeviceName(remotePublicKey, remoteDeviceName)
                    }

                    database.addAuthorizedDevice(remotePublicKey)
                }

                onAuthorized?.invoke(it, isNewlyAuthorized, isNewSession)
            },
            onUnauthorized = {
                sendRemotePendingStatusUpdate(remotePublicKey, false, "Unauthorized")
                onUnauthorized?.invoke(it)
            },
            onConnectedChanged = { it, connected ->
                Logger.i(TAG, "$remotePublicKey connected: $connected")
                onConnectedChanged?.invoke(it, connected)
            },
            onClose = {
                Logger.i(TAG, "$remotePublicKey closed")

                removeSession(it.remotePublicKey)
                sendRemotePendingStatusUpdate(remotePublicKey, false, "Connection closed")

                onClose?.invoke(it)
            },
            dataHandler = { it, opcode, subOpcode, data ->
                onData?.invoke(it, opcode, subOpcode, data)
            },
            remoteDeviceName
        )
    }

    fun getLinkType(publicKey: String): LinkType = synchronized(_sessions) { _sessions[publicKey]?.linkType ?: LinkType.None }
    fun isConnected(publicKey: String): Boolean = synchronized(_sessions) { _sessions[publicKey]?.connected ?: false }
    fun isAuthorized(publicKey: String): Boolean = database.isAuthorized(publicKey)
    fun getSession(publicKey: String): SyncSession? = synchronized(_sessions) { _sessions[publicKey] }
    fun getSessions(): List<SyncSession> = synchronized(_sessions) { _sessions.values.toList() }
    fun removeSession(publicKey: String) = synchronized(_sessions) { _sessions.remove(publicKey) }
    fun getCachedName(publicKey: String): String? = database.getDeviceName(publicKey)
    fun getAuthorizedDeviceCount(): Int = database.getAuthorizedDeviceCount()
    fun getAllAuthorizedDevices(): Array<String>? = database.getAllAuthorizedDevices()
    fun removeAuthorizedDevice(publicKey: String) = database.removeAuthorizedDevice(publicKey)


    suspend fun connect(deviceInfo: SyncDeviceInfo, alsoTryRelayed: Boolean = false, timeout_ms: Int = 10_000, onStatusUpdate: ((complete: Boolean?, message: String) -> Unit)? = null) {
        val rs = _relaySession
        val startTime = System.currentTimeMillis()
        if (alsoTryRelayed && rs != null && settings.relayPairAllowed) {
            onStatusUpdate?.invoke(null, "Connecting via relay...")

            if (onStatusUpdate != null) {
                synchronized(_remotePendingStatusUpdateRelayed) {
                    _remotePendingStatusUpdateRelayed[deviceInfo.publicKey.base64ToByteArray().toBase64()] = onStatusUpdate
                }
            }
            //TODO: Do not try relayed channel here only for pairing mode?
            rs.startRelayedChannel(deviceInfo.publicKey.base64ToByteArray().toBase64(), appId, deviceInfo.pairingCode)
        }

        try {
            connect(deviceInfo.addresses, deviceInfo.port, deviceInfo.publicKey, deviceInfo.pairingCode, onStatusUpdate, timeout_ms)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to connect directly", e)

            val waitTime_ms = timeout_ms - (System.currentTimeMillis() - startTime)
            if (waitTime_ms > 0)
                delay(waitTime_ms)

            onStatusUpdate?.invoke(false, "Failed to connect.")
            synchronized(_remotePendingStatusUpdateRelayed) {
                _remotePendingStatusUpdateRelayed.remove(deviceInfo.publicKey.base64ToByteArray().toBase64())
            }
        }
    }

    suspend fun connect(addresses: Array<String>, port: Int, publicKey: String, pairingCode: String?, onStatusUpdate: ((complete: Boolean?, message: String) -> Unit)? = null, timeout_ms: Int = 10_000): SyncSocketSession {
        val startTime_ms = System.currentTimeMillis()
        Log.i(TAG, "Connecting directly (timeout_ms = ${timeout_ms})...")
        onStatusUpdate?.invoke(null, "Connecting directly...")
        val socket = getConnectedSocket(addresses.map { InetAddress.getByName(it) }, port, timeout_ms) ?: throw Exception("Failed to connect")
        onStatusUpdate?.invoke(null, "Handshaking...")

        val session = createSocketSession(socket, false)
        if (onStatusUpdate != null) {
            synchronized(_remotePendingStatusUpdateDirect) {
                _remotePendingStatusUpdateDirect[publicKey.base64ToByteArray().toBase64()] = onStatusUpdate
            }
        }

        session.startAsInitiator(publicKey, appId, pairingCode)

        while ((System.currentTimeMillis() - startTime_ms) < timeout_ms && !session.isAuthorized) {
            delay(100)
        }

        if (!session.isAuthorized) {
            Log.i(TAG, "Session is not authorized after timeout, cancelling connection.")
            session.stop()
            onStatusUpdate?.invoke(false, "Session not authorized.")
            synchronized(_remotePendingStatusUpdateDirect) {
                _remotePendingStatusUpdateDirect.remove(publicKey.base64ToByteArray().toBase64())
            }
        }

        return session
    }

    fun stop() {
        _scope?.cancel()
        _scope = null
        _relaySession?.stop()
        _relaySession = null
        _serverSocket?.close()
        _serverSocket = null

        synchronized(_sessions) {
            _sessions.values.toList()
        }.forEach { it.close() }

        synchronized(_sessions) {
            _sessions.clear()
        }
        _remotePendingStatusUpdateDirect.clear()
        _remotePendingStatusUpdateRelayed.clear()
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

    companion object {
        val dh = "25519"
        val pattern = "IK"
        val cipher = "ChaChaPoly"
        val hash = "BLAKE2b"
        var protocolName = "Noise_${pattern}_${dh}_${cipher}_${hash}"

        private const val TAG = "SyncService"
    }
}