package com.futo.platformplayer.states

import android.os.Build
import android.util.Log
import com.futo.platformplayer.LittleEndianDataInputStream
import com.futo.platformplayer.LittleEndianDataOutputStream
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.SyncShowPairingCodeActivity
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.encryption.GEncryptionProvider
import com.futo.platformplayer.getConnectedSocket
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.mdns.DnsService
import com.futo.platformplayer.mdns.ServiceDiscoverer
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.Noise
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStringMapStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.stores.StringTMapStorage
import com.futo.platformplayer.sync.SyncSessionData
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import com.futo.platformplayer.sync.internal.SyncKeyPair
import com.futo.platformplayer.sync.internal.SyncSession
import com.futo.platformplayer.sync.internal.SyncSocketSession
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.Locale
import kotlin.system.measureTimeMillis

class StateSync {
    private val _authorizedDevices = FragmentedStorage.get<StringArrayStorage>("authorized_devices")
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
    val _serviceDiscoverer = ServiceDiscoverer(arrayOf("_gsync._tcp.local")) { handleServiceUpdated(it) }

    var keyPair: DHState? = null
    var publicKey: String? = null
    val deviceRemoved: Event1<String> = Event1()
    val deviceUpdatedOrAdded: Event2<String, SyncSession> = Event2()

    fun hasAuthorizedDevice(): Boolean {
        synchronized(_sessions) {
            return _sessions.any{ it.value.connected && it.value.isAuthorized };
        }
    }

    fun start() {
        if (_started) {
            Logger.i(TAG, "Already started.")
            return
        }
        _started = true

        if (Settings.instance.synchronization.broadcast || Settings.instance.synchronization.connectDiscovered) {
            _serviceDiscoverer.start()
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
            publicKey?.let { _serviceDiscoverer.broadcastService(getDeviceName(), "_gsync._tcp.local", PORT.toUShort(), texts = arrayListOf("pk=${it.replace('+', '-').replace('/', '_').replace("=", "")}")) }
        }

        Logger.i(TAG, "Sync key pair initialized (public key = ${publicKey})")

        _thread = Thread {
            try {
                val serverSocket = ServerSocket(PORT)
                _serverSocket = serverSocket

                Log.i(TAG, "Running on port ${PORT} (TCP)")

                while (_started) {
                    val socket = serverSocket.accept()
                    val session = createSocketSession(socket, true) { session, socketSession ->

                    }

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
                            val syncDeviceInfo = SyncDeviceInfo(connectPair.first, arrayOf(connectPair.second), PORT)

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
                                connect(syncDeviceInfo)
                            }
                        } catch (e: Throwable) {
                            Logger.i(TAG, "Failed to connect to " + connectPair.first, e)
                        }
                    }
                    Thread.sleep(5000)
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
        return synchronized(_sessions) {
            return _sessions.values.toList()
        };
    }
    fun getAuthorizedSessions(): List<SyncSession> {
        return synchronized(_sessions) {
            return _sessions.values.filter { it.isAuthorized }.toList()
        };
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

    private fun handleServiceUpdated(services: List<DnsService>) {
        if (!Settings.instance.synchronization.connectDiscovered) {
            return
        }

        for (s in services) {
            //TODO: Addresses IPv4 only?
            val addresses = s.addresses.mapNotNull { it.hostAddress }.toTypedArray()
            val port = s.port.toInt()
            if (s.name.endsWith("._gsync._tcp.local")) {
                val name = s.name.substring(0, s.name.length - "._gsync._tcp.local".length)
                val urlSafePkey = s.texts.firstOrNull { it.startsWith("pk=") }?.substring("pk=".length) ?: continue
                val pkey = Base64.getEncoder().encodeToString(Base64.getDecoder().decode(urlSafePkey.replace('-', '+').replace('_', '/')))

                val syncDeviceInfo = SyncDeviceInfo(pkey, addresses, port)
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
        }
    }

    private fun unauthorize(remotePublicKey: String) {
        Logger.i(TAG, "${remotePublicKey} unauthorized received")
        _authorizedDevices.remove(remotePublicKey)
        _authorizedDevices.save()
        deviceRemoved.emit(remotePublicKey)
    }

    private fun createSocketSession(socket: Socket, isResponder: Boolean, onAuthorized: (session: SyncSession, socketSession: SyncSocketSession) -> Unit): SyncSocketSession {
        var session: SyncSession? = null
        return SyncSocketSession((socket.remoteSocketAddress as InetSocketAddress).address.hostAddress!!, keyPair!!, LittleEndianDataInputStream(socket.getInputStream()), LittleEndianDataOutputStream(socket.getOutputStream()),
            onClose = { s ->
                session?.removeSocketSession(s)
            },
            onHandshakeComplete = { s ->
                val remotePublicKey = s.remotePublicKey
                if (remotePublicKey == null) {
                    s.stop()
                    return@SyncSocketSession
                }

                Logger.i(TAG, "Handshake complete with (LocalPublicKey = ${s.localPublicKey}, RemotePublicKey = ${s.remotePublicKey})")

                synchronized(_sessions) {
                    session = _sessions[s.remotePublicKey]
                    if (session == null) {
                        session = SyncSession(remotePublicKey, onAuthorized = { it, isNewlyAuthorized, isNewSession ->
                            if (!isNewSession) {
                                return@SyncSession
                            }

                            Logger.i(TAG, "${s.remotePublicKey} authorized")
                            synchronized(_lastAddressStorage) {
                                _lastAddressStorage.setAndSave(remotePublicKey, s.remoteAddress)
                            }

                            onAuthorized(it, s)
                            _authorizedDevices.addDistinct(remotePublicKey)
                            _authorizedDevices.save()
                            deviceUpdatedOrAdded.emit(it.remotePublicKey, session!!)

                            checkForSync(it);
                        }, onUnauthorized = {
                            unauthorize(remotePublicKey)

                            synchronized(_sessions) {
                                session?.close()
                                _sessions.remove(remotePublicKey)
                            }
                        }, onConnectedChanged = { it, connected ->
                            Logger.i(TAG, "${s.remotePublicKey} connected: " + connected)
                            deviceUpdatedOrAdded.emit(it.remotePublicKey, session!!)
                        }, onClose = {
                            Logger.i(TAG, "${s.remotePublicKey} closed")

                            synchronized(_sessions)
                            {
                                _sessions.remove(it.remotePublicKey)
                            }

                            deviceRemoved.emit(it.remotePublicKey)

                        })
                        _sessions[remotePublicKey] = session!!
                    }
                    session!!.addSocketSession(s)
                }

                if (isResponder) {
                    val isAuthorized = synchronized(_authorizedDevices) {
                        _authorizedDevices.values.contains(remotePublicKey)
                    }

                    if (!isAuthorized) {
                        val scope = StateApp.instance.scopeOrNull
                        val activity = SyncShowPairingCodeActivity.activity

                        if (scope != null && activity != null) {
                            scope.launch(Dispatchers.Main) {
                                UIDialogs.showConfirmationDialog(activity, "Allow connection from ${remotePublicKey}?", action = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            session!!.authorize(s)
                                            Logger.i(TAG, "Connection authorized for $remotePublicKey by confirmation")
                                        } catch (e: Throwable) {
                                            Logger.e(TAG, "Failed to send authorize", e)
                                        }
                                    }
                                }, cancelAction = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            unauthorize(remotePublicKey)
                                        } catch (e: Throwable) {
                                            Logger.w(TAG, "Failed to send unauthorize", e)
                                        }

                                        synchronized(_sessions) {
                                            session?.close()
                                            _sessions.remove(remotePublicKey)
                                        }
                                    }
                                })
                            }
                        } else {
                            val publicKey = session!!.remotePublicKey
                            session!!.unauthorize(s)
                            session!!.close()

                            synchronized(_sessions) {
                                _sessions.remove(publicKey)
                            }

                            Logger.i(TAG, "Connection unauthorized for ${remotePublicKey} because not authorized and not on pairing activity to ask")
                        }
                    } else {
                        //Responder does not need to check because already approved
                        session!!.authorize(s)
                        Logger.i(TAG, "Connection authorized for ${remotePublicKey} because already authorized")
                    }
                } else {
                    //Initiator does not need to check because the manual action of scanning the QR counts as approval
                    session!!.authorize(s)
                    Logger.i(TAG, "Connection authorized for ${remotePublicKey} because initiator")
                }
            },
            onData = { s, opcode, subOpcode, data ->
                session?.handlePacket(s, opcode, subOpcode, data)
            })
    }

    inline fun <reified T> broadcastJsonData(subOpcode: UByte, data: T) {
        broadcast(SyncSocketSession.Opcode.DATA.value, subOpcode, Json.encodeToString(data));
    }
    fun broadcastData(subOpcode: UByte, data: String) {
        broadcast(SyncSocketSession.Opcode.DATA.value, subOpcode, data.toByteArray(Charsets.UTF_8));
    }
    fun broadcast(opcode: UByte, subOpcode: UByte, data: String) {
        broadcast(opcode, subOpcode, data.toByteArray(Charsets.UTF_8));
    }
    fun broadcast(opcode: UByte, subOpcode: UByte, data: ByteArray) {
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
        _serviceDiscoverer.stop()

        _serverSocket?.close()
        _serverSocket = null

        //_thread?.join()
        _thread = null
        _connectThread = null
    }

    fun connect(deviceInfo: SyncDeviceInfo, onStatusUpdate: ((session: SyncSocketSession?, complete: Boolean, message: String) -> Unit)? = null): SyncSocketSession {
        onStatusUpdate?.invoke(null, false, "Connecting...")
        val socket = getConnectedSocket(deviceInfo.addresses.map { InetAddress.getByName(it) }, deviceInfo.port) ?: throw Exception("Failed to connect")
        onStatusUpdate?.invoke(null, false, "Handshaking...")

        val session = createSocketSession(socket, false) { _, ss ->
            onStatusUpdate?.invoke(ss, true, "Handshake complete")
        }

        session.startAsInitiator(deviceInfo.publicKey)
        return session
    }

    fun getAll(): List<String> {
        synchronized(_authorizedDevices) {
            return _authorizedDevices.values.toList()
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