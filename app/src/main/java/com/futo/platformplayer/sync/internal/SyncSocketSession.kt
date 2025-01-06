package com.futo.platformplayer.sync.internal

import com.futo.platformplayer.LittleEndianDataInputStream
import com.futo.platformplayer.LittleEndianDataOutputStream
import com.futo.platformplayer.ensureNotMainThread
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.noise.protocol.CipherStatePair
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.HandshakeState
import com.futo.platformplayer.states.StateSync
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class SyncSocketSession {
    enum class Opcode(val value: UByte) {
        PING(0u),
        PONG(1u),
        NOTIFY_AUTHORIZED(2u),
        NOTIFY_UNAUTHORIZED(3u),
        STREAM_START(4u),
        STREAM_DATA(5u),
        STREAM_END(6u),
        DATA(7u)
    }

    private val _inputStream: LittleEndianDataInputStream
    private val _outputStream: LittleEndianDataOutputStream
    private val _sendLockObject = Object()
    private val _buffer = ByteArray(MAXIMUM_PACKET_SIZE_ENCRYPTED)
    private val _bufferDecrypted = ByteArray(MAXIMUM_PACKET_SIZE)
    private val _sendBuffer = ByteArray(MAXIMUM_PACKET_SIZE)
    private val _sendBufferEncrypted = ByteArray(MAXIMUM_PACKET_SIZE_ENCRYPTED)
    private val _syncStreams = hashMapOf<Int, SyncStream>()
    private val _streamIdGenerator = 0
    private val _streamIdGeneratorLock = Object()
    private val _onClose: (session: SyncSocketSession) -> Unit
    private val _onHandshakeComplete: (session: SyncSocketSession) -> Unit
    private var _thread: Thread? = null
    private var _cipherStatePair: CipherStatePair? = null
    private var _remotePublicKey: String? = null
    val remotePublicKey: String? get() = _remotePublicKey
    private var _started: Boolean = false
    private val _localKeyPair: DHState
    private var _localPublicKey: String
    val localPublicKey: String get() = _localPublicKey
    private val _onData: (session: SyncSocketSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) -> Unit
    var authorizable: IAuthorizable? = null

    val remoteAddress: String

    constructor(remoteAddress: String, localKeyPair: DHState, inputStream: LittleEndianDataInputStream, outputStream: LittleEndianDataOutputStream, onClose: (session: SyncSocketSession) -> Unit, onHandshakeComplete: (session: SyncSocketSession) -> Unit, onData: (session: SyncSocketSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) -> Unit) {
        _inputStream = inputStream
        _outputStream = outputStream
        _onClose = onClose
        _onHandshakeComplete = onHandshakeComplete
        _localKeyPair = localKeyPair
        _onData = onData
        this.remoteAddress = remoteAddress

        val localPublicKey = ByteArray(localKeyPair.publicKeyLength)
        localKeyPair.getPublicKey(localPublicKey, 0)
        _localPublicKey = java.util.Base64.getEncoder().encodeToString(localPublicKey)
    }

    fun startAsInitiator(remotePublicKey: String) {
        _started = true
        _thread = Thread {
            try {
                handshakeAsInitiator(remotePublicKey)
                _onHandshakeComplete.invoke(this)
                receiveLoop()
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to run as initiator", e)
            } finally {
                stop()
            }
        }.apply { start() }
    }

    fun startAsResponder() {
        _started = true
        _thread = Thread {
            try {
                handshakeAsResponder()
                _onHandshakeComplete.invoke(this)
                receiveLoop()
            } catch(e: Throwable) {
                Logger.e(TAG, "Failed to run as responder", e)
            } finally {
                stop()
            }
        }.apply { start() }
    }

    private fun receiveLoop() {
        while (_started) {
            try {
                val messageSize = _inputStream.readInt()
                if (messageSize > MAXIMUM_PACKET_SIZE_ENCRYPTED) {
                    throw Exception("Message size (${messageSize}) cannot exceed MAXIMUM_PACKET_SIZE ($MAXIMUM_PACKET_SIZE_ENCRYPTED)")
                }

                //Logger.i(TAG, "Receiving message (size = ${messageSize})")

                var bytesRead = 0
                while (bytesRead < messageSize) {
                    val read = _inputStream.read(_buffer, bytesRead, messageSize - bytesRead)
                    if (read == -1)
                        throw Exception("Stream closed")
                    bytesRead += read
                }

                val plen: Int = _cipherStatePair!!.receiver.decryptWithAd(null, _buffer, 0, _bufferDecrypted, 0, messageSize)
                //Logger.i(TAG, "Decrypted message (size = ${plen})")

                handleData(_bufferDecrypted, plen)
            } catch (e: Throwable) {
                Logger.e(TAG, "Exception while receiving data", e)
                break
            }
        }
    }

    fun stop() {
        _started = false
        _onClose(this)
        _inputStream.close()
        _outputStream.close()
        _thread = null
        Logger.i(TAG, "Session closed")
    }

    private fun handshakeAsInitiator(remotePublicKey: String) {
        performVersionCheck()

        val initiator = HandshakeState(StateSync.protocolName, HandshakeState.INITIATOR)
        initiator.localKeyPair.copyFrom(_localKeyPair)

        initiator.remotePublicKey.setPublicKey(java.util.Base64.getDecoder().decode(remotePublicKey), 0)
        _cipherStatePair = handshake(initiator)

        _remotePublicKey = initiator.remotePublicKey.let {
            val pkey = ByteArray(it.publicKeyLength)
            it.getPublicKey(pkey, 0)
            return@let java.util.Base64.getEncoder().encodeToString(pkey)
        }
    }

    private fun handshakeAsResponder() {
        performVersionCheck()

        val responder = HandshakeState(StateSync.protocolName, HandshakeState.RESPONDER)
        responder.localKeyPair.copyFrom(_localKeyPair)
        _cipherStatePair = handshake(responder)

        _remotePublicKey = responder.remotePublicKey.let {
            val pkey = ByteArray(it.publicKeyLength)
            it.getPublicKey(pkey, 0)
            return@let java.util.Base64.getEncoder().encodeToString(pkey)
        }
    }

    private fun performVersionCheck() {
        val CURRENT_VERSION = 2
        _outputStream.writeInt(CURRENT_VERSION)
        val version = _inputStream.readInt()
        Logger.i(TAG, "performVersionCheck (version = $version)")
        if (version != CURRENT_VERSION)
            throw Exception("Invalid version")
    }

    private fun handshake(handshakeState: HandshakeState): CipherStatePair {
        handshakeState.start()

        val message = ByteArray(8192)
        val plaintext = ByteArray(8192)

        while (_started) {
            when (handshakeState.action) {
                HandshakeState.READ_MESSAGE -> {
                    val messageSize = _inputStream.readInt()
                    Logger.i(TAG, "Handshake read message (size = ${messageSize})")

                    var bytesRead = 0
                    while (bytesRead < messageSize) {
                        val read = _inputStream.read(message, bytesRead, messageSize - bytesRead)
                        if (read == -1)
                            throw Exception("Stream closed")
                        bytesRead += read
                    }

                    handshakeState.readMessage(message, 0, messageSize, plaintext, 0)
                }
                HandshakeState.WRITE_MESSAGE -> {
                    val messageSize = handshakeState.writeMessage(message, 0, null, 0, 0)
                    Logger.i(TAG, "Handshake wrote message (size = ${messageSize})")
                    _outputStream.writeInt(messageSize)
                    _outputStream.write(message, 0, messageSize)
                }
                HandshakeState.SPLIT -> {
                    //Logger.i(TAG, "Handshake split")
                    return handshakeState.split()
                }
                else -> throw Exception("Unexpected state (handshakeState.action = ${handshakeState.action})")
            }
        }

        throw Exception("Handshake finished without completing")
    }

    fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        ensureNotMainThread()

        if (data.remaining() + HEADER_SIZE > MAXIMUM_PACKET_SIZE) {
            val segmentSize = MAXIMUM_PACKET_SIZE - HEADER_SIZE
            val segmentData = ByteArray(segmentSize)
            var sendOffset = 0
            val id = synchronized(_streamIdGeneratorLock) {
                _streamIdGenerator + 1
            }

            while (sendOffset < data.remaining()) {
                val bytesRemaining = data.remaining() - sendOffset
                var bytesToSend: Int
                var segmentPacketSize: Int
                val segmentOpcode: UByte

                if (sendOffset == 0) {
                    segmentOpcode = Opcode.STREAM_START.value
                    bytesToSend = segmentSize - 4 - 4 - 1 - 1
                    segmentPacketSize = bytesToSend + 4 + 4 + 1 + 1
                } else {
                    bytesToSend = minOf(segmentSize - 4 - 4, bytesRemaining)
                    segmentOpcode = if (bytesToSend >= bytesRemaining) Opcode.STREAM_END.value else Opcode.STREAM_DATA.value
                    segmentPacketSize = bytesToSend + 4 + 4
                }

                ByteBuffer.wrap(segmentData).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(id)
                    putInt(if (segmentOpcode == Opcode.STREAM_START.value) data.remaining() else sendOffset)
                    if (segmentOpcode == Opcode.STREAM_START.value) {
                        put(opcode.toByte())
                        put(subOpcode.toByte())
                    }
                    put(data.array(), data.position() + sendOffset, bytesToSend)
                }

                send(segmentOpcode, 0u, ByteBuffer.wrap(segmentData, 0, segmentPacketSize))
                sendOffset += bytesToSend
            }
        } else {
            synchronized(_sendLockObject) {
                ByteBuffer.wrap(_sendBuffer).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(data.remaining() + 2)
                    put(opcode.toByte())
                    put(subOpcode.toByte())
                    put(data.array(), data.position(), data.remaining())
                }

                //Logger.i(TAG, "Encrypting message (size = ${data.size + HEADER_SIZE})")
                val len = _cipherStatePair!!.sender.encryptWithAd(null, _sendBuffer, 0, _sendBufferEncrypted, 0, data.remaining() + HEADER_SIZE)
                //Logger.i(TAG, "Sending encrypted message (size = ${len})")
                _outputStream.writeInt(len)
                _outputStream.write(_sendBufferEncrypted, 0, len)
            }
        }
    }

    fun send(opcode: UByte, subOpcode: UByte = 0u) {
        ensureNotMainThread()

        synchronized(_sendLockObject) {
            ByteBuffer.wrap(_sendBuffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(2)
            _sendBuffer.asUByteArray()[4] = opcode
            _sendBuffer.asUByteArray()[5] = subOpcode

            //Logger.i(TAG, "Encrypting message (opcode = ${opcode}, subOpcode = ${subOpcode}, size = ${HEADER_SIZE})")

            val len = _cipherStatePair!!.sender.encryptWithAd(null, _sendBuffer, 0, _sendBufferEncrypted, 0, HEADER_SIZE)
            //Logger.i(TAG, "Sending encrypted message (size = ${len})")

            _outputStream.writeInt(len)
            _outputStream.write(_sendBufferEncrypted, 0, len)
        }
    }

    private fun handleData(data: ByteArray, length: Int) {
        if (length < HEADER_SIZE)
            throw Exception("Packet must be at least 6 bytes (header size)")

        val size = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (size != length - 4)
            throw Exception("Incomplete packet received")

        val opcode = data.asUByteArray()[4]
        val subOpcode = data.asUByteArray()[5]
        val packetData = ByteBuffer.wrap(data, HEADER_SIZE, size - 2)
        handlePacket(opcode, subOpcode, packetData.order(ByteOrder.LITTLE_ENDIAN))
    }

    private fun handlePacket(opcode: UByte, subOpcode: UByte, data: ByteBuffer) {
        Logger.i(TAG, "Handle packet (opcode = ${opcode}, subOpcode = ${subOpcode})")

        when (opcode) {
            Opcode.PING.value -> {
                send(Opcode.PONG.value)
                //Logger.i(TAG, "Received ping, sent pong")
                return
            }
            Opcode.PONG.value -> {
                //Logger.i(TAG, "Received pong")
                return
            }
            Opcode.NOTIFY_AUTHORIZED.value,
            Opcode.NOTIFY_UNAUTHORIZED.value -> {
                _onData.invoke(this, opcode, subOpcode, data)
                return
            }
        }

        if (authorizable?.isAuthorized != true) {
            return
        }

        when (opcode) {
            Opcode.STREAM_START.value -> {
                val id = data.int
                val expectedSize = data.int
                val op = data.get().toUByte()
                val subOp = data.get().toUByte()

                val syncStream = SyncStream(expectedSize, op, subOp)
                if (data.remaining() > 0) {
                    syncStream.add(data.array(), data.position(), data.remaining())
                }

                synchronized(_syncStreams) {
                    _syncStreams[id] = syncStream
                }
            }
            Opcode.STREAM_DATA.value -> {
                val id = data.int
                val expectedOffset = data.int

                val syncStream = synchronized(_syncStreams) {
                    _syncStreams[id] ?: throw Exception("Received data for sync stream that does not exist")
                }

                if (expectedOffset != syncStream.bytesReceived) {
                    throw Exception("Expected offset does not match the amount of received bytes")
                }

                if (data.remaining() > 0) {
                    syncStream.add(data.array(), data.position(), data.remaining())
                }
            }
            Opcode.STREAM_END.value -> {
                val id = data.int
                val expectedOffset = data.int

                val syncStream = synchronized(_syncStreams) {
                    _syncStreams.remove(id) ?: throw Exception("Received data for sync stream that does not exist")
                }

                if (expectedOffset != syncStream.bytesReceived) {
                    throw Exception("Expected offset does not match the amount of received bytes")
                }

                if (data.remaining() > 0) {
                    syncStream.add(data.array(), data.position(), data.remaining())
                }

                if (!syncStream.isComplete) {
                    throw Exception("After sync stream end, the stream must be complete")
                }

                handlePacket(syncStream.opcode, syncStream.subOpcode, syncStream.getBytes().let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) })
            }
            Opcode.DATA.value -> {
                _onData.invoke(this, opcode, subOpcode, data)
            }
            else -> {
                Logger.w(TAG, "Unknown opcode received (opcode = ${opcode}, subOpcode = ${subOpcode})")
            }
        }
    }

    companion object {
        private const val TAG = "SyncSocketSession"
        const val MAXIMUM_PACKET_SIZE = 65535 - 16
        const val MAXIMUM_PACKET_SIZE_ENCRYPTED = MAXIMUM_PACKET_SIZE + 16
        const val HEADER_SIZE = 6
    }
}