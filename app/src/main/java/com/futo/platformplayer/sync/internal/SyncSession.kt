package com.futo.platformplayer.sync.internal

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.sync.models.SyncSubscriptionsPackage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.UUID

interface IAuthorizable {
    val isAuthorized: Boolean
}

class SyncSession : IAuthorizable {
    private val _channels: MutableList<IChannel> = mutableListOf()
    private var _authorized: Boolean = false
    private var _remoteAuthorized: Boolean = false
    private val _onAuthorized: (session: SyncSession, isNewlyAuthorized: Boolean, isNewSession: Boolean) -> Unit
    private val _onUnauthorized: (session: SyncSession) -> Unit
    private val _onClose: (session: SyncSession) -> Unit
    private val _onConnectedChanged: (session: SyncSession, connected: Boolean) -> Unit
    private val _dataHandler: (session: SyncSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) -> Unit
    val remotePublicKey: String
    override val isAuthorized get() = _authorized && _remoteAuthorized
    private var _wasAuthorized = false
    private val _id = UUID.randomUUID()
    private var _remoteId: UUID? = null
    private var _lastAuthorizedRemoteId: UUID? = null
    var remoteDeviceName: String? = null
        private set
    val displayName: String get() = remoteDeviceName ?: remotePublicKey

    val linkType: LinkType get()
    {
        var hasRelayed = false
        var hasDirect = false
        synchronized(_channels)
        {
            for (channel in _channels)
            {
                if (channel is ChannelRelayed)
                    hasRelayed = true
                if (channel is ChannelSocket)
                    hasDirect = true
                if (hasRelayed && hasDirect)
                    return LinkType.Direct
            }
        }

        if (hasRelayed)
            return LinkType.Relayed
        if (hasDirect)
            return LinkType.Direct
        return LinkType.None
    }

    var connected: Boolean = false
        private set(v) {
            if (field != v) {
                field = v
                this._onConnectedChanged(this, v)
            }
        }

    constructor(
        remotePublicKey: String,
        onAuthorized: (session: SyncSession, isNewlyAuthorized: Boolean, isNewSession: Boolean) -> Unit,
        onUnauthorized: (session: SyncSession) -> Unit,
        onConnectedChanged: (session: SyncSession, connected: Boolean) -> Unit,
        onClose: (session: SyncSession) -> Unit,
        dataHandler: (session: SyncSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) -> Unit,
        remoteDeviceName: String? = null
    ) {
        this.remotePublicKey = remotePublicKey
        this.remoteDeviceName = remoteDeviceName
        _onAuthorized = onAuthorized
        _onUnauthorized = onUnauthorized
        _onConnectedChanged = onConnectedChanged
        _onClose = onClose
        _dataHandler = dataHandler
    }

    fun addChannel(channel: IChannel) {
        if (channel.remotePublicKey != remotePublicKey) {
            throw Exception("Public key of session must match public key of channel")
        }

        synchronized(_channels) {
            _channels.add(channel)
            connected = _channels.isNotEmpty()
        }

        channel.authorizable = this
        channel.syncSession = this
    }

    fun authorize() {
        Logger.i(TAG, "Sent AUTHORIZED with session id $_id")
        val idString = _id.toString()
        val idBytes = idString.toByteArray(Charsets.UTF_8)
        val name = "${android.os.Build.MANUFACTURER}-${android.os.Build.MODEL}"
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val buffer = ByteArray(1 + idBytes.size + 1 + nameBytes.size)
        buffer[0] = idBytes.size.toByte()
        System.arraycopy(idBytes, 0, buffer, 1, idBytes.size)
        buffer[1 + idBytes.size] = nameBytes.size.toByte()
        System.arraycopy(nameBytes, 0, buffer, 2 + idBytes.size, nameBytes.size)
        send(Opcode.NOTIFY.value, NotifyOpcode.AUTHORIZED.value, ByteBuffer.wrap(buffer))
        _authorized = true
        checkAuthorized()
    }

    fun unauthorize() {
        send(Opcode.NOTIFY.value, NotifyOpcode.UNAUTHORIZED.value)
    }

    private fun checkAuthorized() {
        if (isAuthorized) {
            val isNewlyAuthorized = !_wasAuthorized
            val isNewSession = _lastAuthorizedRemoteId != _remoteId
            Logger.i(TAG, "onAuthorized (isNewlyAuthorized = $isNewlyAuthorized, isNewSession = $isNewSession)")
            _onAuthorized(this, isNewlyAuthorized, isNewSession)
            _wasAuthorized = true
            _lastAuthorizedRemoteId = _remoteId
        }
    }

    fun removeChannel(channel: IChannel) {
        synchronized(_channels) {
            _channels.remove(channel)
            connected = _channels.isNotEmpty()
        }
    }

    fun close() {
        synchronized(_channels) {
            _channels.forEach { it.close() }
            _channels.clear()
        }
        _onClose(this)
    }

    fun handlePacket(opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        try {
            Logger.i(TAG, "Handle packet (opcode: $opcode, subOpcode: $subOpcode, data.length: ${data.remaining()})")

            when (opcode) {
                Opcode.NOTIFY.value -> when (subOpcode) {
                    NotifyOpcode.AUTHORIZED.value -> {
                        val idByteCount = data.get().toInt()
                        if (idByteCount > 64)
                            throw Exception("Id should always be smaller than 64 bytes")
                        val idBytes = ByteArray(idByteCount)
                        data.get(idBytes)

                        val nameByteCount = data.get().toInt()
                        if (nameByteCount > 64)
                            throw Exception("Name should always be smaller than 64 bytes")
                        val nameBytes = ByteArray(nameByteCount)
                        data.get(nameBytes)

                        _remoteId = UUID.fromString(idBytes.toString(Charsets.UTF_8))
                        remoteDeviceName = nameBytes.toString(Charsets.UTF_8)
                        _remoteAuthorized = true
                        Logger.i(TAG, "Received AUTHORIZED with session id $_remoteId (device name: '${remoteDeviceName ?: "not set"}')")
                        checkAuthorized()
                        return
                    }
                    NotifyOpcode.UNAUTHORIZED.value -> {
                        _remoteAuthorized = false
                        _remoteId = null
                        remoteDeviceName = null
                        _lastAuthorizedRemoteId = null
                        _onUnauthorized(this)
                        return
                    }
                }
            }

            if (!isAuthorized) {
                return
            }

            if (opcode != Opcode.DATA.value) {
                Logger.w(TAG, "Unknown opcode received: (opcode = $opcode, subOpcode = $subOpcode)")
                return
            }

            Logger.i(TAG, "Received (opcode = $opcode, subOpcode = $subOpcode) (${data.remaining()} bytes)")
            _dataHandler.invoke(this, opcode, subOpcode, data)
        } catch (ex: Exception) {
            Logger.w(TAG, "Failed to handle sync package $opcode: ${ex.message}", ex)
        }
        catch(ex: Exception) {
            Logger.w(TAG, "Failed to handle sync package ${opcode}: ${ex.message}", ex);
        }
    }

    inline fun <reified T> sendJsonData(subOpcode: UByte, data: T) {
        send(Opcode.DATA.value, subOpcode, Json.encodeToString(data))
    }

    fun sendData(subOpcode: UByte, data: String) {
        send(Opcode.DATA.value, subOpcode, ByteBuffer.wrap(data.toByteArray(Charsets.UTF_8)))
    }

    fun send(opcode: UByte, subOpcode: UByte, data: String) {
        send(opcode, subOpcode, ByteBuffer.wrap(data.toByteArray(Charsets.UTF_8)))
    }

    fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer? = null) {
        //TODO: Prioritize local connections
        val channels = synchronized(_channels) { _channels.toList() }
        if (channels.isEmpty()) {
            //TODO: Should this throw?
            Logger.v(TAG, "Packet was not sent (opcode = $opcode, subOpcode = $subOpcode) due to no connected sockets")
            return
        }

        var sent = false
        for (channel in channels) {
            try {
                channel.send(opcode, subOpcode, data)
                sent = true
                break
            } catch (e: Throwable) {
                Logger.w(TAG, "Packet failed to send (opcode = $opcode, subOpcode = $subOpcode)", e)
            }
        }

        if (!sent) {
            throw Exception("Packet was not sent (opcode = $opcode, subOpcode = $subOpcode) due to send errors and no remaining candidates")
        }
    }

    companion object {
        private const val TAG = "SyncSession"
    }
}