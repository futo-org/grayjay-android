package com.futo.platformplayer.sync.internal

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.noise.protocol.CipherStatePair
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.HandshakeState
import com.futo.platformplayer.states.StateSync
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

interface IChannel : AutoCloseable {
    val remotePublicKey: String?
    val remoteVersion: Int?
    var authorizable: IAuthorizable?
    var syncSession: SyncSession?
    fun setDataHandler(onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)?)
    fun send(opcode: UByte, subOpcode: UByte = 0u, data: ByteBuffer? = null)
    fun setCloseHandler(onClose: ((IChannel) -> Unit)?)
    val linkType: LinkType
}

class ChannelSocket(private val session: SyncSocketSession) : IChannel {
    override val remotePublicKey: String? get() = session.remotePublicKey
    override val remoteVersion: Int? get() = session.remoteVersion
    private var onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)? = null
    private var onClose: ((IChannel) -> Unit)? = null
    override val linkType: LinkType get() = LinkType.Direct

    override var authorizable: IAuthorizable?
        get() = session.authorizable
        set(value) { session.authorizable = value }
    override var syncSession: SyncSession? = null

    override fun setDataHandler(onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)?) {
        this.onData = onData
    }

    override fun setCloseHandler(onClose: ((IChannel) -> Unit)?) {
        this.onClose = onClose
    }

    override fun close() {
        session.stop()
        onClose?.invoke(this)
    }

    fun invokeDataHandler(opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        onData?.invoke(session, this, opcode, subOpcode, data)
    }

    override fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer?) {
        if (data != null) {
            session.send(opcode, subOpcode, data)
        } else {
            session.send(opcode, subOpcode)
        }
    }
}

class ChannelRelayed(
    private val session: SyncSocketSession,
    private val localKeyPair: DHState,
    private val publicKey: String,
    private val initiator: Boolean
) : IChannel {
    private val sendLock = Object()
    private val decryptLock = Object()
    private var handshakeState: HandshakeState? = if (initiator) {
        HandshakeState(StateSync.protocolName, HandshakeState.INITIATOR).apply {
            localKeyPair.copyFrom(this@ChannelRelayed.localKeyPair)
            remotePublicKey.setPublicKey(Base64.getDecoder().decode(publicKey), 0)
        }
    } else {
        HandshakeState(StateSync.protocolName, HandshakeState.RESPONDER).apply {
            localKeyPair.copyFrom(this@ChannelRelayed.localKeyPair)
        }
    }
    private var transport: CipherStatePair? = null
    override var authorizable: IAuthorizable? = null
    val isAuthorized: Boolean get() = authorizable?.isAuthorized ?: false
    var connectionId: Long = 0L
    override var remotePublicKey: String? = publicKey
        private set
    override var remoteVersion: Int? = null
        private set
    override var syncSession: SyncSession? = null
    override val linkType: LinkType get() = LinkType.Relayed

    private var onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)? = null
    private var onClose: ((IChannel) -> Unit)? = null
    private var disposed = false

    init {
        handshakeState?.start()
    }

    override fun setDataHandler(onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)?) {
        this.onData = onData
    }

    override fun setCloseHandler(onClose: ((IChannel) -> Unit)?) {
        this.onClose = onClose
    }

    override fun close() {
        disposed = true

        if (connectionId != 0L) {
            Thread {
                try {
                    session.sendRelayError(connectionId, SyncErrorCode.ConnectionClosed)
                } catch (e: Exception) {
                    Logger.e("ChannelRelayed", "Exception while sending relay error", e)
                }
            }.start()
        }

        transport?.sender?.destroy()
        transport?.receiver?.destroy()
        transport = null
        handshakeState?.destroy()
        handshakeState = null

        onClose?.invoke(this)
    }

    private fun throwIfDisposed() {
        if (disposed) throw IllegalStateException("ChannelRelayed is disposed")
    }

    fun invokeDataHandler(opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        onData?.invoke(session, this, opcode, subOpcode, data)
    }

    private fun completeHandshake(remoteVersion: Int, transport: CipherStatePair) {
        throwIfDisposed()

        this.remoteVersion = remoteVersion
        val remoteKeyBytes = ByteArray(handshakeState!!.remotePublicKey.publicKeyLength)
        handshakeState!!.remotePublicKey.getPublicKey(remoteKeyBytes, 0)
        this.remotePublicKey = Base64.getEncoder().encodeToString(remoteKeyBytes)
        handshakeState?.destroy()
        handshakeState = null
        this.transport = transport
        Logger.i("ChannelRelayed", "Completed handshake for connectionId $connectionId")
    }

    private fun sendPacket(packet: ByteArray) {
        throwIfDisposed()

        synchronized(sendLock) {
            val encryptedPayload = ByteArray(packet.size + 16)
            val encryptedLength = transport!!.sender.encryptWithAd(null, packet, 0, encryptedPayload, 0, packet.size)

            val relayedPacket = ByteArray(8 + encryptedLength)
            ByteBuffer.wrap(relayedPacket).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(connectionId)
                put(encryptedPayload, 0, encryptedLength)
            }

            session.send(Opcode.RELAY.value, RelayOpcode.DATA.value, ByteBuffer.wrap(relayedPacket).order(ByteOrder.LITTLE_ENDIAN))
        }
    }

    fun sendError(errorCode: SyncErrorCode) {
        throwIfDisposed()

        synchronized(sendLock) {
            val packet = ByteArray(4)
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putInt(errorCode.value)

            val encryptedPayload = ByteArray(4 + 16)
            val encryptedLength = transport!!.sender.encryptWithAd(null, packet, 0, encryptedPayload, 0, packet.size)

            val relayedPacket = ByteArray(8 + encryptedLength)
            ByteBuffer.wrap(relayedPacket).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(connectionId)
                put(encryptedPayload, 0, encryptedLength)
            }

            session.send(Opcode.RELAY.value, RelayOpcode.ERROR.value, ByteBuffer.wrap(relayedPacket))
        }
    }

    override fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer?) {
        throwIfDisposed()

        val actualCount = data?.remaining() ?: 0
        val ENCRYPTION_OVERHEAD = 16
        val CONNECTION_ID_SIZE = 8
        val HEADER_SIZE = 6
        val MAX_DATA_PER_PACKET = SyncSocketSession.MAXIMUM_PACKET_SIZE - HEADER_SIZE - CONNECTION_ID_SIZE - ENCRYPTION_OVERHEAD - 16

        if (actualCount > MAX_DATA_PER_PACKET && data != null) {
            val streamId = session.generateStreamId()
            val totalSize = actualCount
            var sendOffset = 0

            while (sendOffset < totalSize) {
                val bytesRemaining = totalSize - sendOffset
                val bytesToSend = minOf(MAX_DATA_PER_PACKET - 8 - 2, bytesRemaining)

                val streamData: ByteArray
                val streamOpcode: StreamOpcode
                if (sendOffset == 0) {
                    streamOpcode = StreamOpcode.START
                    streamData = ByteArray(4 + 4 + 1 + 1 + bytesToSend)
                    ByteBuffer.wrap(streamData).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(streamId)
                        putInt(totalSize)
                        put(opcode.toByte())
                        put(subOpcode.toByte())
                        put(data.array(), data.position() + sendOffset, bytesToSend)
                    }
                } else {
                    streamData = ByteArray(4 + 4 + bytesToSend)
                    ByteBuffer.wrap(streamData).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(streamId)
                        putInt(sendOffset)
                        put(data.array(), data.position() + sendOffset, bytesToSend)
                    }
                    streamOpcode = if (bytesToSend < bytesRemaining) StreamOpcode.DATA else StreamOpcode.END
                }

                val fullPacket = ByteArray(HEADER_SIZE + streamData.size)
                ByteBuffer.wrap(fullPacket).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(streamData.size + 2)
                    put(Opcode.STREAM.value.toByte())
                    put(streamOpcode.value.toByte())
                    put(streamData)
                }

                sendPacket(fullPacket)
                sendOffset += bytesToSend
            }
        } else {
            val packet = ByteArray(HEADER_SIZE + actualCount)
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(actualCount + 2)
                put(opcode.toByte())
                put(subOpcode.toByte())
                if (actualCount > 0 && data != null) put(data.array(), data.position(), actualCount)
            }
            sendPacket(packet)
        }
    }

    fun sendRequestTransport(requestId: Int, publicKey: String, pairingCode: String? = null) {
        throwIfDisposed()

        synchronized(sendLock) {
            val channelMessage = ByteArray(1024)
            val channelBytesWritten = handshakeState!!.writeMessage(channelMessage, 0, null, 0, 0)

            val publicKeyBytes = Base64.getDecoder().decode(publicKey)
            if (publicKeyBytes.size != 32) throw IllegalArgumentException("Public key must be 32 bytes")

            val (pairingMessageLength, pairingMessage) = if (pairingCode != null) {
                val pairingHandshake = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.INITIATOR).apply {
                    remotePublicKey.setPublicKey(publicKeyBytes, 0)
                    start()
                }
                val pairingCodeBytes = pairingCode.toByteArray(Charsets.UTF_8)
                if (pairingCodeBytes.size > 32) throw IllegalArgumentException("Pairing code must not exceed 32 bytes")
                val pairingMessageBuffer = ByteArray(1024)
                val bytesWritten = pairingHandshake.writeMessage(pairingMessageBuffer, 0, pairingCodeBytes, 0, pairingCodeBytes.size)
                bytesWritten to pairingMessageBuffer.copyOf(bytesWritten)
            } else {
                0 to ByteArray(0)
            }

            val packetSize = 4 + 32 + 4 + pairingMessageLength + 4 + channelBytesWritten
            val packet = ByteArray(packetSize)
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(requestId)
                put(publicKeyBytes)
                putInt(pairingMessageLength)
                if (pairingMessageLength > 0) put(pairingMessage)
                putInt(channelBytesWritten)
                put(channelMessage, 0, channelBytesWritten)
            }

            session.send(Opcode.REQUEST.value, RequestOpcode.TRANSPORT.value, ByteBuffer.wrap(packet))
        }
    }

    fun sendResponseTransport(remoteVersion: Int, requestId: Int, handshakeMessage: ByteArray) {
        throwIfDisposed()

        synchronized(sendLock) {
            val message = ByteArray(1024)
            val plaintext = ByteArray(1024)
            handshakeState!!.readMessage(handshakeMessage, 0, handshakeMessage.size, plaintext, 0)
            val bytesWritten = handshakeState!!.writeMessage(message, 0, null, 0, 0)
            val transport = handshakeState!!.split()

            val responsePacket = ByteArray(20 + bytesWritten)
            ByteBuffer.wrap(responsePacket).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0) // Status code
                putLong(connectionId)
                putInt(requestId)
                putInt(bytesWritten)
                put(message, 0, bytesWritten)
            }

            completeHandshake(remoteVersion, transport)
            session.send(Opcode.RESPONSE.value, ResponseOpcode.TRANSPORT.value, ByteBuffer.wrap(responsePacket))
        }
    }

    fun decrypt(encryptedPayload: ByteBuffer): ByteBuffer {
        throwIfDisposed()

        synchronized(decryptLock) {
            val encryptedBytes = ByteArray(encryptedPayload.remaining()).also { encryptedPayload.get(it) }
            val decryptedPayload = ByteArray(encryptedBytes.size - 16)
            val plen = transport!!.receiver.decryptWithAd(null, encryptedBytes, 0, decryptedPayload, 0, encryptedBytes.size)
            if (plen != decryptedPayload.size) throw IllegalStateException("Expected decrypted payload length to be $plen")
            return ByteBuffer.wrap(decryptedPayload).order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    fun handleTransportRelayed(remoteVersion: Int, connectionId: Long, handshakeMessage: ByteArray) {
        throwIfDisposed()

        synchronized(decryptLock) {
            this.connectionId = connectionId
            val plaintext = ByteArray(1024)
            val plen = handshakeState!!.readMessage(handshakeMessage, 0, handshakeMessage.size, plaintext, 0)
            val transport = handshakeState!!.split()
            completeHandshake(remoteVersion, transport)
        }
    }
}