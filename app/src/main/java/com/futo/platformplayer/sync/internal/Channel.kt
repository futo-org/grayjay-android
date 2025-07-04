package com.futo.platformplayer.sync.internal

import com.futo.platformplayer.ensureNotMainThread
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.noise.protocol.CipherStatePair
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.HandshakeState
import com.futo.platformplayer.states.StateSync
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.toBase64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.GZIPOutputStream

interface IChannel : AutoCloseable {
    val remotePublicKey: String?
    val remoteVersion: Int?
    var authorizable: IAuthorizable?
    var syncSession: SyncSession?
    fun setDataHandler(onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)?)
    fun send(opcode: UByte, subOpcode: UByte = 0u, data: ByteBuffer? = null, contentEncoding: ContentEncoding? = null)
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

    override fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer?, contentEncoding: ContentEncoding?) {
        ensureNotMainThread()
        if (data != null) {
            session.send(opcode, subOpcode, data, contentEncoding)
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
        HandshakeState(SyncService.protocolName, HandshakeState.INITIATOR).apply {
            localKeyPair.copyFrom(this@ChannelRelayed.localKeyPair)
            remotePublicKey.setPublicKey(publicKey.base64ToByteArray(), 0)
        }
    } else {
        HandshakeState(SyncService.protocolName, HandshakeState.RESPONDER).apply {
            localKeyPair.copyFrom(this@ChannelRelayed.localKeyPair)
        }
    }
    private var transport: CipherStatePair? = null
    override var authorizable: IAuthorizable? = null
    val isAuthorized: Boolean get() = authorizable?.isAuthorized ?: false
    var connectionId: Long = 0L
    override var remotePublicKey: String? = publicKey.base64ToByteArray().toBase64()
        private set
    override var remoteVersion: Int? = null
        private set
    override var syncSession: SyncSession? = null
    override val linkType: LinkType get() = LinkType.Relayed

    private var onData: ((SyncSocketSession, IChannel, UByte, UByte, ByteBuffer) -> Unit)? = null
    private var onClose: ((IChannel) -> Unit)? = null
    private var disposed = false
    private var _lastPongTime: Long = 0
    private val _pingInterval: Long = 5000 // 5 seconds in milliseconds
    private val _disconnectTimeout: Long = 30000 // 30 seconds in milliseconds

    init {
        handshakeState?.start()
    }

    private fun startPingLoop() {
        if (remoteVersion!! < 5) {
            return
        }

        _lastPongTime = System.currentTimeMillis()

        Thread {
            try {
                while (!disposed) {
                    Thread.sleep(_pingInterval)
                    if (System.currentTimeMillis() - _lastPongTime > _disconnectTimeout) {
                        Logger.e("ChannelRelayed", "Channel timed out waiting for PONG; closing.")
                        close()
                        break
                    }
                    send(Opcode.PING.value, 0u)
                }
            } catch (e: Exception) {
                Logger.e("ChannelRelayed", "Ping loop failed", e)
                close()
            }
        }.start()
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
        if (opcode == Opcode.PONG.value) {
            _lastPongTime = System.currentTimeMillis()
            return
        }
        onData?.invoke(session, this, opcode, subOpcode, data)
    }

    private fun completeHandshake(remoteVersion: Int, transport: CipherStatePair) {
        throwIfDisposed()

        this.remoteVersion = remoteVersion
        val remoteKeyBytes = ByteArray(handshakeState!!.remotePublicKey.publicKeyLength)
        handshakeState!!.remotePublicKey.getPublicKey(remoteKeyBytes, 0)
        this.remotePublicKey = remoteKeyBytes.toBase64()
        handshakeState?.destroy()
        handshakeState = null
        this.transport = transport
        Logger.i("ChannelRelayed", "Completed handshake for connectionId $connectionId")
        startPingLoop()
    }

    private fun sendPacket(packet: ByteArray) {
        throwIfDisposed()
        ensureNotMainThread()

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
        ensureNotMainThread()

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

    override fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer?, ce: ContentEncoding?) {
        throwIfDisposed()
        ensureNotMainThread()

        var contentEncoding: ContentEncoding? = ce
        var processedData = data
        if (data != null && contentEncoding == ContentEncoding.Gzip) {
            val isGzipSupported = opcode == Opcode.DATA.value
            if (isGzipSupported) {
                val compressedStream = ByteArrayOutputStream()
                GZIPOutputStream(compressedStream).use { gzipStream ->
                    gzipStream.write(data.array(), data.position(), data.remaining())
                    gzipStream.finish()
                }
                processedData = ByteBuffer.wrap(compressedStream.toByteArray())
            } else {
                Logger.w(TAG, "Gzip requested but not supported on this (opcode = ${opcode}, subOpcode = ${subOpcode}), falling back.")
                contentEncoding = ContentEncoding.Raw
            }
        }

        val ENCRYPTION_OVERHEAD = 16
        val CONNECTION_ID_SIZE = 8
        val HEADER_SIZE = 7
        val MAX_DATA_PER_PACKET = SyncSocketSession.MAXIMUM_PACKET_SIZE - HEADER_SIZE - CONNECTION_ID_SIZE - ENCRYPTION_OVERHEAD - 16

        Logger.v(TAG, "Send (opcode: ${opcode}, subOpcode: ${subOpcode}, processedData.size: ${processedData?.remaining()})")

        if (processedData != null && processedData.remaining() > MAX_DATA_PER_PACKET) {
            val streamId = session.generateStreamId()
            var sendOffset = 0

            while (sendOffset < processedData.remaining()) {
                val bytesRemaining = processedData.remaining() - sendOffset
                val bytesToSend = minOf(MAX_DATA_PER_PACKET - 8 - HEADER_SIZE + 4, bytesRemaining)

                val streamData: ByteArray
                val streamOpcode: StreamOpcode
                if (sendOffset == 0) {
                    streamOpcode = StreamOpcode.START
                    streamData = ByteArray(4 + HEADER_SIZE + bytesToSend)
                    ByteBuffer.wrap(streamData).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(streamId)
                        putInt(processedData.remaining())
                        put(opcode.toByte())
                        put(subOpcode.toByte())
                        put(contentEncoding?.value?.toByte() ?: 0.toByte())
                        put(processedData.array(), processedData.position() + sendOffset, bytesToSend)
                    }
                } else {
                    streamData = ByteArray(4 + 4 + bytesToSend)
                    ByteBuffer.wrap(streamData).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(streamId)
                        putInt(sendOffset)
                        put(processedData.array(), processedData.position() + sendOffset, bytesToSend)
                    }
                    streamOpcode = if (bytesToSend < bytesRemaining) StreamOpcode.DATA else StreamOpcode.END
                }

                val fullPacket = ByteArray(HEADER_SIZE + streamData.size)
                ByteBuffer.wrap(fullPacket).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(streamData.size + HEADER_SIZE - 4)
                    put(Opcode.STREAM.value.toByte())
                    put(streamOpcode.value.toByte())
                    put(ContentEncoding.Raw.value.toByte())
                    put(streamData)
                }

                sendPacket(fullPacket)
                sendOffset += bytesToSend
            }
        } else {
            val packet = ByteArray(HEADER_SIZE + (processedData?.remaining() ?: 0))
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt((processedData?.remaining() ?: 0) + HEADER_SIZE - 4)
                put(opcode.toByte())
                put(subOpcode.toByte())
                put(contentEncoding?.value?.toByte() ?: ContentEncoding.Raw.value.toByte())
                if (processedData != null && processedData.remaining() > 0) put(processedData.array(), processedData.position(), processedData.remaining())
            }
            sendPacket(packet)
        }
    }

    fun sendRequestTransport(requestId: Int, publicKey: String, appId: UInt, pairingCode: String? = null) {
        throwIfDisposed()
        ensureNotMainThread()

        synchronized(sendLock) {
            val channelMessage = ByteArray(1024)
            val channelBytesWritten = handshakeState!!.writeMessage(channelMessage, 0, null, 0, 0)

            val publicKeyBytes = publicKey.base64ToByteArray()
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

            val packetSize = 4 + 4 + 32 + 4 + pairingMessageLength + 4 + channelBytesWritten
            val packet = ByteArray(packetSize)
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(requestId)
                putInt(appId.toInt())
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
        ensureNotMainThread()

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

    companion object {
        private val TAG = "Channel"
    }
}