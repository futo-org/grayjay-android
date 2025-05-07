package com.futo.platformplayer.sync.internal

import android.os.Build
import com.futo.platformplayer.LittleEndianDataInputStream
import com.futo.platformplayer.LittleEndianDataOutputStream
import com.futo.platformplayer.copyToOutputStream
import com.futo.platformplayer.ensureNotMainThread
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.noise.protocol.CipherStatePair
import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.HandshakeState
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.ChannelRelayed.Companion
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.toBase64
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.min
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime

class SyncSocketSession {
    private val _socket: Socket
    private val _inputStream: InputStream
    private val _outputStream: OutputStream
    private val _sendLockObject = Object()
    private val _buffer = ByteArray(MAXIMUM_PACKET_SIZE_ENCRYPTED)
    private val _bufferDecrypted = ByteArray(MAXIMUM_PACKET_SIZE)
    private val _sendBuffer = ByteArray(MAXIMUM_PACKET_SIZE)
    private val _sendBufferEncrypted = ByteArray(4 + MAXIMUM_PACKET_SIZE_ENCRYPTED)
    private val _syncStreams = hashMapOf<Int, SyncStream>()
    private var _streamIdGenerator = 0
    private val _streamIdGeneratorLock = Object()
    private var _requestIdGenerator = 0
    private val _requestIdGeneratorLock = Object()
    private val _onClose: ((session: SyncSocketSession) -> Unit)?
    private val _onHandshakeComplete: ((session: SyncSocketSession) -> Unit)?
    private val _onNewChannel: ((session: SyncSocketSession, channel: ChannelRelayed) -> Unit)?
    private val _onChannelEstablished: ((session: SyncSocketSession, channel: ChannelRelayed, isResponder: Boolean) -> Unit)?
    private val _isHandshakeAllowed: ((linkType: LinkType, session: SyncSocketSession, remotePublicKey: String, pairingCode: String?, appId: UInt) -> Boolean)?
    private var _cipherStatePair: CipherStatePair? = null
    private var _remotePublicKey: String? = null
    val remotePublicKey: String? get() = _remotePublicKey
    private var _started: Boolean = false
    private val _localKeyPair: DHState
    private var _thread: Thread? = null
    private var _localPublicKey: String
    val localPublicKey: String get() = _localPublicKey
    private val _onData: ((session: SyncSocketSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) -> Unit)?
    val isAuthorized: Boolean
        get() = authorizable?.isAuthorized ?: false
    var authorizable: IAuthorizable? = null
    var remoteVersion: Int = -1
        private set

    val remoteAddress: String

    private val _channels = ConcurrentHashMap<Long, ChannelRelayed>()
    private val _pendingChannels = ConcurrentHashMap<Int, Pair<ChannelRelayed, CompletableDeferred<ChannelRelayed>>>()
    private val _pendingConnectionInfoRequests = ConcurrentHashMap<Int, CompletableDeferred<ConnectionInfo?>>()
    private val _pendingPublishRequests = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    private val _pendingDeleteRequests = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    private val _pendingListKeysRequests = ConcurrentHashMap<Int, CompletableDeferred<List<Pair<String, Long>>>>()
    private val _pendingGetRecordRequests = ConcurrentHashMap<Int, CompletableDeferred<Pair<ByteArray, Long>?>>()
    private val _pendingBulkGetRecordRequests = ConcurrentHashMap<Int, CompletableDeferred<Map<String, Pair<ByteArray, Long>>>>()
    private val _pendingBulkConnectionInfoRequests = ConcurrentHashMap<Int, CompletableDeferred<Map<String, ConnectionInfo>>>()

    data class ConnectionInfo(
        val port: UShort,
        val name: String,
        val remoteIp: String,
        val ipv4Addresses: List<String>,
        val ipv6Addresses: List<String>,
        val allowLocalDirect: Boolean,
        val allowRemoteDirect: Boolean,
        val allowRemoteHolePunched: Boolean,
        val allowRemoteRelayed: Boolean
    )

    constructor(
        remoteAddress: String,
        localKeyPair: DHState,
        socket: Socket,
        onClose: ((session: SyncSocketSession) -> Unit)? = null,
        onHandshakeComplete: ((session: SyncSocketSession) -> Unit)? = null,
        onData: ((session: SyncSocketSession, opcode: UByte, subOpcode: UByte, data: ByteBuffer) -> Unit)? = null,
        onNewChannel: ((session: SyncSocketSession, channel: ChannelRelayed) -> Unit)? = null,
        onChannelEstablished: ((session: SyncSocketSession, channel: ChannelRelayed, isResponder: Boolean) -> Unit)? = null,
        isHandshakeAllowed: ((linkType: LinkType, session: SyncSocketSession, remotePublicKey: String, pairingCode: String?, appId: UInt) -> Boolean)? = null
    ) {
        _socket = socket
        _socket.receiveBufferSize = MAXIMUM_PACKET_SIZE_ENCRYPTED
        _socket.sendBufferSize = MAXIMUM_PACKET_SIZE_ENCRYPTED
        _socket.tcpNoDelay = true
        _inputStream = _socket.getInputStream()
        _outputStream = _socket.getOutputStream()
        _onClose = onClose
        _onHandshakeComplete = onHandshakeComplete
        _localKeyPair = localKeyPair
        _onData = onData
        _onNewChannel = onNewChannel
        _onChannelEstablished = onChannelEstablished
        _isHandshakeAllowed = isHandshakeAllowed
        this.remoteAddress = remoteAddress

        val localPublicKey = ByteArray(localKeyPair.publicKeyLength)
        localKeyPair.getPublicKey(localPublicKey, 0)
        _localPublicKey = Base64.getEncoder().encodeToString(localPublicKey)
    }

    fun startAsInitiator(remotePublicKey: String, appId: UInt = 0u, pairingCode: String? = null) {
        _started = true
        _thread = Thread {
            try {
                handshakeAsInitiator(remotePublicKey, appId, pairingCode)
                _onHandshakeComplete?.invoke(this)
                receiveLoop()
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to run as initiator", e)
            } finally {
                stop()
            }
        }.apply { start() }
    }

    fun runAsInitiator(remotePublicKey: String, appId: UInt = 0u, pairingCode: String? = null) {
        _started = true
        try {
            handshakeAsInitiator(remotePublicKey, appId, pairingCode)
            _onHandshakeComplete?.invoke(this)
            receiveLoop()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to run as initiator", e)
        } finally {
            stop()
        }
    }

    fun startAsResponder() {
        _started = true
        _thread = Thread {
            try {
                if (handshakeAsResponder()) {
                    _onHandshakeComplete?.invoke(this)
                    receiveLoop()
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to run as responder", e)
            } finally {
                stop()
            }
        }.apply { start() }
    }

    private fun readExact(buffer: ByteArray, offset: Int, size: Int) {
        var totalBytesReceived: Int = 0
        while (totalBytesReceived < size) {
            val bytesReceived = _inputStream.read(buffer, offset + totalBytesReceived, size - totalBytesReceived)
            if (bytesReceived <= 0)
                throw Exception("Socket disconnected")
            totalBytesReceived += bytesReceived
        }
    }

    private fun receiveLoop() {
        while (_started) {
            try {
                //Logger.v(TAG, "Waiting for message size...")

                readExact(_buffer, 0, 4)
                val messageSize = ByteBuffer.wrap(_buffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int

                //Logger.v(TAG, "Read message size ${messageSize}.")

                if (messageSize > MAXIMUM_PACKET_SIZE_ENCRYPTED) {
                    throw Exception("Message size (${messageSize}) cannot exceed MAXIMUM_PACKET_SIZE ($MAXIMUM_PACKET_SIZE_ENCRYPTED)")
                }

                //Logger.i(TAG, "Receiving message (size = ${messageSize})")

                readExact(_buffer, 0, messageSize)
                //Logger.v(TAG, "Read ${messageSize}.")

                //Logger.v(TAG, "Decrypting ${messageSize} bytes.")
                val plen: Int = _cipherStatePair!!.receiver.decryptWithAd(null, _buffer, 0, _bufferDecrypted, 0, messageSize)
                //Logger.i(TAG, "Decrypted message (size = ${plen})")

                //Logger.v(TAG, "Decrypted ${messageSize} bytes.")
                handleData(_bufferDecrypted, plen, null)
                //Logger.v(TAG, "Handled data ${messageSize} bytes.")
            } catch (e: Throwable) {
                Logger.e(TAG, "Exception while receiving data, closing socket session", e)
                stop()
                break
            }
        }
    }

    fun stop() {
        _started = false
        _pendingConnectionInfoRequests.forEach { it.value.cancel() }
        _pendingConnectionInfoRequests.clear()
        _pendingPublishRequests.forEach { it.value.cancel() }
        _pendingPublishRequests.clear()
        _pendingDeleteRequests.forEach { it.value.cancel() }
        _pendingDeleteRequests.clear()
        _pendingListKeysRequests.forEach { it.value.cancel() }
        _pendingListKeysRequests.clear()
        _pendingGetRecordRequests.forEach { it.value.cancel() }
        _pendingGetRecordRequests.clear()
        _pendingBulkGetRecordRequests.forEach { it.value.cancel() }
        _pendingBulkGetRecordRequests.clear()
        _pendingBulkConnectionInfoRequests.forEach { it.value.cancel() }
        _pendingBulkConnectionInfoRequests.clear()
        _pendingChannels.forEach { it.value.first.close(); it.value.second.cancel() }
        _pendingChannels.clear()
        synchronized(_syncStreams) {
            _syncStreams.clear()
        }
        _channels.values.forEach { it.close() }
        _channels.clear()
        _onClose?.invoke(this)
        _socket.close()
        _thread = null
        _cipherStatePair?.sender?.destroy()
        _cipherStatePair?.receiver?.destroy()
        Logger.i(TAG, "Session closed")
    }

    private fun handshakeAsInitiator(remotePublicKey: String, appId: UInt, pairingCode: String?) {
        performVersionCheck()

        val initiator = HandshakeState(StateSync.protocolName, HandshakeState.INITIATOR)
        initiator.localKeyPair.copyFrom(_localKeyPair)
        initiator.remotePublicKey.setPublicKey(Base64.getDecoder().decode(remotePublicKey), 0)
        initiator.start()

        val pairingMessage: ByteArray
        val pairingMessageLength: Int
        if (pairingCode != null) {
            val pairingHandshake = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.INITIATOR)
            pairingHandshake.remotePublicKey.setPublicKey(Base64.getDecoder().decode(remotePublicKey), 0)
            pairingHandshake.start()
            val pairingCodeBytes = pairingCode.toByteArray(Charsets.UTF_8)
            val pairingBuffer = ByteArray(512)
            pairingMessageLength = pairingHandshake.writeMessage(pairingBuffer, 0, pairingCodeBytes, 0, pairingCodeBytes.size)
            pairingMessage = pairingBuffer.copyOf(pairingMessageLength)
        } else {
            pairingMessage = ByteArray(0)
            pairingMessageLength = 0
        }

        val mainBuffer = ByteArray(512)
        val mainLength = initiator.writeMessage(mainBuffer, 0, null, 0, 0)

        val messageSize = 4 + 4 + pairingMessageLength + mainLength
        val messageData = ByteBuffer.allocate(4 + messageSize).order(ByteOrder.LITTLE_ENDIAN)
        messageData.putInt(messageSize)
        messageData.putInt(appId.toInt())
        messageData.putInt(pairingMessageLength)
        if (pairingMessageLength > 0) messageData.put(pairingMessage)
        messageData.put(mainBuffer, 0, mainLength)
        val messageDataArray = messageData.array()
        _outputStream.write(messageDataArray, 0, 4 + messageSize)

        readExact(_buffer, 0, 4)
        val responseSize = ByteBuffer.wrap(_buffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (responseSize > MAXIMUM_PACKET_SIZE_ENCRYPTED) {
            throw Exception("Message size (${messageSize}) cannot exceed MAXIMUM_PACKET_SIZE ($MAXIMUM_PACKET_SIZE_ENCRYPTED)")
        }

        val responseMessage = ByteArray(responseSize)
        readExact(responseMessage, 0, responseSize)

        val plaintext = ByteArray(512) // Buffer for any payload (none expected here)
        initiator.readMessage(responseMessage, 0, responseSize, plaintext, 0)

        _cipherStatePair = initiator.split()
        val remoteKeyBytes = ByteArray(initiator.remotePublicKey.publicKeyLength)
        initiator.remotePublicKey.getPublicKey(remoteKeyBytes, 0)
        _remotePublicKey = Base64.getEncoder().encodeToString(remoteKeyBytes).base64ToByteArray().toBase64()
    }

    private fun handshakeAsResponder(): Boolean {
        performVersionCheck()

        val responder = HandshakeState(StateSync.protocolName, HandshakeState.RESPONDER)
        responder.localKeyPair.copyFrom(_localKeyPair)
        responder.start()

        readExact(_buffer, 0, 4)
        val messageSize = ByteBuffer.wrap(_buffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (messageSize > MAXIMUM_PACKET_SIZE_ENCRYPTED) {
            throw Exception("Message size (${messageSize}) cannot exceed MAXIMUM_PACKET_SIZE ($MAXIMUM_PACKET_SIZE_ENCRYPTED)")
        }

        val message = ByteArray(messageSize)
        readExact(message, 0, messageSize)

        val messageBuffer = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN)
        val appId = messageBuffer.int.toUInt()
        val pairingMessageLength = messageBuffer.int
        val pairingMessage = if (pairingMessageLength > 0) ByteArray(pairingMessageLength).also { messageBuffer.get(it) } else byteArrayOf()
        val mainLength = messageSize - 4 - 4 - pairingMessageLength
        val mainMessage = ByteArray(mainLength).also { messageBuffer.get(it) }

        var pairingCode: String? = null
        if (pairingMessageLength > 0) {
            val pairingHandshake = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.RESPONDER)
            pairingHandshake.localKeyPair.copyFrom(_localKeyPair)
            pairingHandshake.start()
            val pairingPlaintext = ByteArray(512)
            val plaintextLength = pairingHandshake.readMessage(pairingMessage, 0, pairingMessageLength, pairingPlaintext, 0)
            pairingCode = String(pairingPlaintext, 0, plaintextLength, Charsets.UTF_8)
        }

        val plaintext = ByteArray(512)
        responder.readMessage(mainMessage, 0, mainLength, plaintext, 0)
        val remoteKeyBytes = ByteArray(responder.remotePublicKey.publicKeyLength)
        responder.remotePublicKey.getPublicKey(remoteKeyBytes, 0)
        val remotePublicKey = Base64.getEncoder().encodeToString(remoteKeyBytes)

        val isAllowedToConnect = remotePublicKey != _localPublicKey && (_isHandshakeAllowed?.invoke(LinkType.Direct, this, remotePublicKey, pairingCode, appId) ?: true)
        if (!isAllowedToConnect) {
            stop()
            return false
        }

        val responseBuffer = ByteArray(4 + 512)
        val responseLength = responder.writeMessage(responseBuffer, 4, null, 0, 0)
        ByteBuffer.wrap(responseBuffer).order(ByteOrder.LITTLE_ENDIAN).putInt(responseLength)
        _outputStream.write(responseBuffer, 0, 4 + responseLength)

        _cipherStatePair = responder.split()
        _remotePublicKey = remotePublicKey.base64ToByteArray().toBase64()
        return true
    }

    private fun performVersionCheck() {
        val CURRENT_VERSION = 4
        val MINIMUM_VERSION = 4

        val versionBytes = ByteArray(4)
        ByteBuffer.wrap(versionBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(CURRENT_VERSION)
        _outputStream.write(versionBytes, 0, 4)

        readExact(versionBytes, 0, 4)
        remoteVersion = ByteBuffer.wrap(versionBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        Logger.i(TAG, "performVersionCheck (version = $remoteVersion)")
        if (remoteVersion < MINIMUM_VERSION)
            throw Exception("Invalid version")
    }

    fun generateStreamId(): Int = synchronized(_streamIdGeneratorLock) { _streamIdGenerator++ }
    private fun generateRequestId(): Int = synchronized(_requestIdGeneratorLock) { _requestIdGenerator++ }

    fun send(opcode: UByte, subOpcode: UByte, data: ByteBuffer, ce: ContentEncoding? = null) {
        ensureNotMainThread()

        Logger.v(TAG, "send (opcode: ${opcode}, subOpcode: ${subOpcode}, data.remaining(): ${data.remaining()})")

        var contentEncoding: ContentEncoding? = ce
        var processedData = data
        if (contentEncoding == ContentEncoding.Gzip) {
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

        if (processedData.remaining() + HEADER_SIZE > MAXIMUM_PACKET_SIZE) {
            val segmentSize = MAXIMUM_PACKET_SIZE - HEADER_SIZE
            val segmentData = ByteArray(segmentSize)
            var sendOffset = 0
            val id = generateStreamId()

            while (sendOffset < processedData.remaining()) {
                val bytesRemaining = processedData.remaining() - sendOffset
                var bytesToSend: Int
                var segmentPacketSize: Int
                val streamOp: StreamOpcode

                if (sendOffset == 0) {
                    streamOp = StreamOpcode.START
                    bytesToSend = segmentSize - 4 - HEADER_SIZE
                    segmentPacketSize = bytesToSend + 4 + HEADER_SIZE
                } else {
                    bytesToSend = minOf(segmentSize - 4 - 4, bytesRemaining)
                    streamOp = if (bytesToSend >= bytesRemaining) StreamOpcode.END else StreamOpcode.DATA
                    segmentPacketSize = bytesToSend + 4 + 4
                }

                ByteBuffer.wrap(segmentData).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(id)
                    putInt(if (streamOp == StreamOpcode.START) processedData.remaining() else sendOffset)
                    if (streamOp == StreamOpcode.START) {
                        put(opcode.toByte())
                        put(subOpcode.toByte())
                        put(contentEncoding?.value?.toByte() ?: ContentEncoding.Raw.value.toByte())
                    }
                    put(processedData.array(), processedData.position() + sendOffset, bytesToSend)
                }

                send(Opcode.STREAM.value, streamOp.value, ByteBuffer.wrap(segmentData, 0, segmentPacketSize))
                sendOffset += bytesToSend
            }
        } else {
            synchronized(_sendLockObject) {
                ByteBuffer.wrap(_sendBuffer).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(processedData.remaining() + HEADER_SIZE - 4)
                    put(opcode.toByte())
                    put(subOpcode.toByte())
                    put(contentEncoding?.value?.toByte() ?: ContentEncoding.Raw.value.toByte())
                    put(processedData.array(), processedData.position(), processedData.remaining())
                }

                val len = _cipherStatePair!!.sender.encryptWithAd(null, _sendBuffer, 0, _sendBufferEncrypted, 4, processedData.remaining() + HEADER_SIZE)
                val sendDuration = measureTimeMillis {
                    ByteBuffer.wrap(_sendBufferEncrypted, 0, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(len)
                    _outputStream.write(_sendBufferEncrypted, 0, 4 + len)
                }
                Logger.v(TAG, "_outputStream.write (opcode: ${opcode}, subOpcode: ${subOpcode}, processedData.remaining(): ${processedData.remaining()}, sendDuration: ${sendDuration})")
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun send(opcode: UByte, subOpcode: UByte = 0u) {
        ensureNotMainThread()

        synchronized(_sendLockObject) {
            ByteBuffer.wrap(_sendBuffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(HEADER_SIZE - 4)
            _sendBuffer.asUByteArray()[4] = opcode
            _sendBuffer.asUByteArray()[5] = subOpcode
            _sendBuffer.asUByteArray()[6] = ContentEncoding.Raw.value

            //Logger.i(TAG, "Encrypting message (opcode = ${opcode}, subOpcode = ${subOpcode}, size = ${HEADER_SIZE})")

            val len = _cipherStatePair!!.sender.encryptWithAd(null, _sendBuffer, 0, _sendBufferEncrypted, 4, HEADER_SIZE)
            //Logger.i(TAG, "Sending encrypted message (size = ${len})")

            ByteBuffer.wrap(_sendBufferEncrypted, 0, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(len)
            _outputStream.write(_sendBufferEncrypted, 0, 4 + len)
        }
    }

    private fun handleData(data: ByteArray, length: Int, sourceChannel: ChannelRelayed?) {
        return handleData(ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN), sourceChannel)
    }

    private fun handleData(data: ByteBuffer, sourceChannel: ChannelRelayed?) {
        val length = data.remaining()
        if (length < HEADER_SIZE)
            throw Exception("Packet must be at least ${HEADER_SIZE} bytes (header size)")

        val size = data.int
        if (size != length - 4)
            throw Exception("Incomplete packet received")

        val opcode = data.get().toUByte()
        val subOpcode = data.get().toUByte()
        val contentEncoding = data.get().toUByte()

        //Logger.v(TAG, "handleData (opcode: ${opcode}, subOpcode: ${subOpcode}, data.size: ${data.remaining()}, sourceChannel.connectionId: ${sourceChannel?.connectionId})")
        handlePacket(opcode, subOpcode, data, contentEncoding, sourceChannel)
    }

    private fun handleRequest(subOpcode: UByte, data: ByteBuffer, sourceChannel: ChannelRelayed?) {
        when (subOpcode) {
            RequestOpcode.TRANSPORT_RELAYED.value -> {
                Logger.i(TAG, "Received request for a relayed transport")
                if (data.remaining() < 52) {
                    Logger.e(TAG, "HandleRequestTransport: Packet too short")
                    return
                }
                val remoteVersion = data.int
                val connectionId = data.long
                val requestId = data.int
                val appId = data.int.toUInt()
                val publicKeyBytes = ByteArray(32).also { data.get(it) }
                val pairingMessageLength = data.int
                if (pairingMessageLength > 128) throw IllegalArgumentException("Pairing message length ($pairingMessageLength) exceeds maximum (128) (app id: $appId)")
                val pairingMessage = if (pairingMessageLength > 0) ByteArray(pairingMessageLength).also { data.get(it) } else ByteArray(0)
                val channelMessageLength = data.int
                if (data.remaining() != channelMessageLength) {
                    Logger.e(TAG, "Invalid packet size. Expected ${52 + pairingMessageLength + 4 + channelMessageLength}, got ${data.capacity()} (app id: $appId)")
                    return
                }
                val channelHandshakeMessage = ByteArray(channelMessageLength).also { data.get(it) }
                val publicKey = Base64.getEncoder().encodeToString(publicKeyBytes)
                val pairingCode = if (pairingMessageLength > 0) {
                    val pairingProtocol = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.RESPONDER).apply {
                        localKeyPair.copyFrom(_localKeyPair)
                        start()
                    }
                    val plaintext = ByteArray(1024)
                    val length = pairingProtocol.readMessage(pairingMessage, 0, pairingMessageLength, plaintext, 0)
                    String(plaintext, 0, length, Charsets.UTF_8)
                } else null
                val isAllowed = publicKey != _localPublicKey && (_isHandshakeAllowed?.invoke(LinkType.Relayed, this, publicKey, pairingCode, appId) ?: true)
                if (!isAllowed) {
                    val rp = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    rp.putInt(2) // Status code for not allowed
                    rp.putLong(connectionId)
                    rp.putInt(requestId)
                    rp.rewind()
                    send(Opcode.RESPONSE.value, ResponseOpcode.TRANSPORT.value, rp)
                    return
                }
                val channel = ChannelRelayed(this, _localKeyPair, publicKey, false)
                channel.connectionId = connectionId
                _onNewChannel?.invoke(this, channel)
                _channels[connectionId] = channel
                channel.sendResponseTransport(remoteVersion, requestId, channelHandshakeMessage)
                _onChannelEstablished?.invoke(this, channel, true)
            }
            else -> Logger.w(TAG, "Unhandled request opcode: $subOpcode")
        }
    }

    private fun handleResponse(subOpcode: UByte, data: ByteBuffer, sourceChannel: ChannelRelayed?) {
        if (data.remaining() < 8) {
            Logger.e(TAG, "Response packet too short")
            return
        }
        val requestId = data.int
        val statusCode = data.int
        when (subOpcode) {
            ResponseOpcode.CONNECTION_INFO.value -> {
                _pendingConnectionInfoRequests.remove(requestId)?.let { tcs ->
                    if (statusCode == 0) {
                        try {
                            val connectionInfo = parseConnectionInfo(data)
                            tcs.complete(connectionInfo)
                        } catch (e: Exception) {
                            tcs.completeExceptionally(e)
                        }
                    } else {
                        tcs.complete(null)
                    }
                } ?: Logger.e(TAG, "No pending request for requestId $requestId")
            }
            ResponseOpcode.TRANSPORT_RELAYED.value -> {
                if (statusCode == 0) {
                    if (data.remaining() < 16) {
                        Logger.e(TAG, "RESPONSE_TRANSPORT packet too short")
                        return
                    }
                    val remoteVersion = data.int
                    val connectionId = data.long
                    val messageLength = data.int
                    if (data.remaining() != messageLength) {
                        Logger.e(TAG, "Invalid RESPONSE_TRANSPORT packet size. Expected ${16 + messageLength}, got ${data.remaining() + 16}")
                        return
                    }
                    val handshakeMessage = ByteArray(messageLength).also { data.get(it) }
                    _pendingChannels.remove(requestId)?.let { (channel, tcs) ->
                        channel.handleTransportRelayed(remoteVersion, connectionId, handshakeMessage)
                        _channels[connectionId] = channel
                        tcs.complete(channel)
                        _onChannelEstablished?.invoke(this, channel, false)
                    } ?: Logger.e(TAG, "No pending channel for requestId $requestId")
                } else {
                    _pendingChannels.remove(requestId)?.let { (channel, tcs) ->
                        channel.close()
                        tcs.completeExceptionally(Exception("Relayed transport request $requestId failed with code $statusCode"))
                    }
                }
            }
            ResponseOpcode.PUBLISH_RECORD.value, ResponseOpcode.BULK_PUBLISH_RECORD.value -> {
                _pendingPublishRequests.remove(requestId)?.complete(statusCode == 0)
                    ?: Logger.e(TAG, "No pending publish request for requestId $requestId")
            }
            ResponseOpcode.DELETE_RECORD.value, ResponseOpcode.BULK_DELETE_RECORD.value -> {
                _pendingDeleteRequests.remove(requestId)?.complete(statusCode == 0)
                    ?: Logger.e(TAG, "No pending delete request for requestId $requestId")
            }
            ResponseOpcode.LIST_RECORD_KEYS.value -> {
                _pendingListKeysRequests.remove(requestId)?.let { tcs ->
                    if (statusCode == 0) {
                        try {
                            val keyCount = data.int
                            val keys = mutableListOf<Pair<String, Long>>()
                            repeat(keyCount) {
                                val keyLength = data.get().toInt()
                                val key = ByteArray(keyLength).also { data.get(it) }.toString(Charsets.UTF_8)
                                val timestamp = data.long
                                keys.add(key to timestamp)
                            }
                            tcs.complete(keys)
                        } catch (e: Exception) {
                            tcs.completeExceptionally(e)
                        }
                    } else {
                        tcs.completeExceptionally(Exception("Error listing keys: status code $statusCode"))
                    }
                } ?: Logger.e(TAG, "No pending list keys request for requestId $requestId")
            }
            ResponseOpcode.GET_RECORD.value -> {
                _pendingGetRecordRequests.remove(requestId)?.let { tcs ->
                    if (statusCode == 0) {
                        try {
                            val blobLength = data.int
                            val encryptedBlob = ByteArray(blobLength).also { data.get(it) }
                            val timestamp = data.long
                            val protocol = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.RESPONDER).apply {
                                localKeyPair.copyFrom(_localKeyPair)
                                start()
                            }
                            val handshakeMessage = encryptedBlob.copyOf(48)
                            val plaintext = ByteArray(0)
                            protocol.readMessage(handshakeMessage, 0, 48, plaintext, 0)
                            val transportPair = protocol.split()
                            var blobOffset = 48
                            val chunks = mutableListOf<ByteArray>()
                            while (blobOffset + 4 <= encryptedBlob.size) {
                                val chunkLength = ByteBuffer.wrap(encryptedBlob, blobOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                blobOffset += 4
                                val encryptedChunk = encryptedBlob.copyOfRange(blobOffset, blobOffset + chunkLength)
                                val decryptedChunk = ByteArray(chunkLength - 16)
                                transportPair.receiver.decryptWithAd(null, encryptedChunk, 0, decryptedChunk, 0, encryptedChunk.size)
                                chunks.add(decryptedChunk)
                                blobOffset += chunkLength
                            }
                            val dataResult = chunks.reduce { acc, bytes -> acc + bytes }
                            tcs.complete(dataResult to timestamp)
                        } catch (e: Exception) {
                            tcs.completeExceptionally(e)
                        }
                    } else if (statusCode == 2) {
                        tcs.complete(null)
                    } else {
                        tcs.completeExceptionally(Exception("Error getting record: statusCode $statusCode"))
                    }
                }
            }
            ResponseOpcode.BULK_GET_RECORD.value -> {
                _pendingBulkGetRecordRequests.remove(requestId)?.let { tcs ->
                    if (statusCode == 0) {
                        try {
                            val recordCount = data.get().toInt()
                            val records = mutableMapOf<String, Pair<ByteArray, Long>>()
                            repeat(recordCount) {
                                val publisherBytes = ByteArray(32).also { data.get(it) }
                                val publisher = Base64.getEncoder().encodeToString(publisherBytes)
                                val blobLength = data.int
                                val encryptedBlob = ByteArray(blobLength).also { data.get(it) }
                                val timestamp = data.long
                                val protocol = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.RESPONDER).apply {
                                    localKeyPair.copyFrom(_localKeyPair)
                                    start()
                                }
                                val handshakeMessage = encryptedBlob.copyOf(48)
                                val plaintext = ByteArray(0)
                                protocol.readMessage(handshakeMessage, 0, 48, plaintext, 0)
                                val transportPair = protocol.split()
                                var blobOffset = 48
                                val chunks = mutableListOf<ByteArray>()
                                while (blobOffset + 4 <= encryptedBlob.size) {
                                    val chunkLength = ByteBuffer.wrap(encryptedBlob, blobOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                    blobOffset += 4
                                    val encryptedChunk = encryptedBlob.copyOfRange(blobOffset, blobOffset + chunkLength)
                                    val decryptedChunk = ByteArray(chunkLength - 16)
                                    transportPair.receiver.decryptWithAd(null, encryptedChunk, 0, decryptedChunk, 0, encryptedChunk.size)
                                    chunks.add(decryptedChunk)
                                    blobOffset += chunkLength
                                }
                                val dataResult = chunks.reduce { acc, bytes -> acc + bytes }
                                records[publisher] = dataResult to timestamp
                            }
                            tcs.complete(records)
                        } catch (e: Exception) {
                            tcs.completeExceptionally(e)
                        }
                    } else {
                        tcs.completeExceptionally(Exception("Error getting bulk records: statusCode $statusCode"))
                    }
                }
            }
            ResponseOpcode.BULK_CONNECTION_INFO.value -> {
                _pendingBulkConnectionInfoRequests.remove(requestId)?.let { tcs ->
                    try {
                        val numResponses = data.get().toInt()
                        val result = mutableMapOf<String, ConnectionInfo>()
                        repeat(numResponses) {
                            val publicKey = Base64.getEncoder().encodeToString(ByteArray(32).also { data.get(it) })
                            val status = data.get().toInt()
                            if (status == 0) {
                                val infoSize = data.int
                                val infoData = ByteArray(infoSize).also { data.get(it) }
                                result[publicKey] = parseConnectionInfo(ByteBuffer.wrap(infoData).order(ByteOrder.LITTLE_ENDIAN))
                            }
                        }
                        tcs.complete(result)
                    } catch (e: Exception) {
                        tcs.completeExceptionally(e)
                    }
                } ?: Logger.e(TAG, "No pending bulk request for requestId $requestId")
            }
        }
    }

    private fun parseConnectionInfo(data: ByteBuffer): ConnectionInfo {
        val ipSize = data.get().toInt()
        val remoteIpBytes = ByteArray(ipSize).also { data.get(it) }
        val remoteIp = remoteIpBytes.joinToString(".") { it.toUByte().toString() }
        val handshakeMessage = ByteArray(48).also { data.get(it) }
        val ciphertext = ByteArray(data.remaining()).also { data.get(it) }
        val protocol = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.RESPONDER).apply {
            localKeyPair.copyFrom(_localKeyPair)
            start()
        }
        val plaintext = ByteArray(0)
        protocol.readMessage(handshakeMessage, 0, 48, plaintext, 0)
        val transportPair = protocol.split()
        val decryptedData = ByteArray(ciphertext.size - 16)
        transportPair.receiver.decryptWithAd(null, ciphertext, 0, decryptedData, 0, ciphertext.size)
        val info = ByteBuffer.wrap(decryptedData).order(ByteOrder.LITTLE_ENDIAN)
        val port = info.short.toUShort()
        val nameLength = info.get().toInt()
        val name = ByteArray(nameLength).also { info.get(it) }.toString(Charsets.UTF_8)
        val ipv4Count = info.get().toInt()
        val ipv4Addresses = List(ipv4Count) { ByteArray(4).also { info.get(it) }.joinToString(".") { it.toUByte().toString() } }
        val ipv6Count = info.get().toInt()
        val ipv6Addresses = List(ipv6Count) { ByteArray(16).also { info.get(it) }.joinToString(":") { it.toUByte().toString(16).padStart(2, '0') } }
        val allowLocalDirect = info.get() != 0.toByte()
        val allowRemoteDirect = info.get() != 0.toByte()
        val allowRemoteHolePunched = info.get() != 0.toByte()
        val allowRemoteRelayed = info.get() != 0.toByte()
        return ConnectionInfo(port, name, remoteIp, ipv4Addresses, ipv6Addresses, allowLocalDirect, allowRemoteDirect, allowRemoteHolePunched, allowRemoteRelayed)
    }

    private fun handleNotify(subOpcode: UByte, data: ByteBuffer, sourceChannel: ChannelRelayed?) {
        when (subOpcode) {
            NotifyOpcode.AUTHORIZED.value, NotifyOpcode.UNAUTHORIZED.value -> {
                if (sourceChannel != null)
                    sourceChannel.invokeDataHandler(Opcode.NOTIFY.value, subOpcode, data)
                else
                    _onData?.invoke(this, Opcode.NOTIFY.value, subOpcode, data)
            }
            NotifyOpcode.CONNECTION_INFO.value -> { /* Handle connection info if needed */ }
        }
    }

    fun sendRelayError(connectionId: Long, errorCode: SyncErrorCode) {
        val packet = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        packet.putLong(connectionId)
        packet.putInt(errorCode.value)
        packet.rewind()
        send(Opcode.RELAY.value, RelayOpcode.RELAY_ERROR.value, packet)
    }

    private fun handleRelay(subOpcode: UByte, data: ByteBuffer, sourceChannel: ChannelRelayed?) {
        when (subOpcode) {
            RelayOpcode.RELAYED_DATA.value -> {
                if (data.remaining() < 8) {
                    Logger.e(TAG, "RELAYED_DATA packet too short")
                    return
                }
                val connectionId = data.long
                val channel = _channels[connectionId] ?: run {
                    Logger.e(TAG, "No channel found for connectionId $connectionId")
                    return
                }
                val decryptedPayload = channel.decrypt(data)
                try {
                    handleData(decryptedPayload, channel)
                } catch (e: Exception) {
                    Logger.e(TAG, "Exception while handling relayed data", e)
                    channel.sendError(SyncErrorCode.ConnectionClosed)
                    channel.close()
                    _channels.remove(connectionId)
                }
            }
            RelayOpcode.RELAYED_ERROR.value -> {
                if (data.remaining() < 8) {
                    Logger.e(TAG, "RELAYED_ERROR packet too short")
                    return
                }
                val connectionId = data.long
                val channel = _channels[connectionId] ?: run {
                    Logger.e(TAG, "No channel found for connectionId $connectionId")
                    sendRelayError(connectionId, SyncErrorCode.NotFound)
                    return
                }
                val decryptedPayload = channel.decrypt(data)
                val errorCode = SyncErrorCode.entries.find { it.value == decryptedPayload.int } ?: SyncErrorCode.ConnectionClosed
                Logger.e(TAG, "Received relayed error (errorCode = $errorCode) on connectionId $connectionId, closing")
                channel.close()
                _channels.remove(connectionId)
            }
            RelayOpcode.RELAY_ERROR.value -> {
                if (data.remaining() < 12) {
                    Logger.e(TAG, "RELAY_ERROR packet too short")
                    return
                }
                val connectionId = data.long
                val errorCode = SyncErrorCode.entries.find { it.value == data.int } ?: SyncErrorCode.ConnectionClosed
                val channel = _channels[connectionId] ?: run {
                    Logger.e(TAG, "Received error code $errorCode for non-existent channel with connectionId $connectionId")
                    return
                }
                Logger.i(TAG, "Received relay error (errorCode = $errorCode) on connectionId $connectionId, closing")
                channel.close()
                _channels.remove(connectionId)
                _pendingChannels.entries.find { it.value.first == channel }?.let {
                    _pendingChannels.remove(it.key)?.second?.cancel()
                }
            }
        }
    }

    private fun handlePacket(opcode: UByte, subOpcode: UByte, d: ByteBuffer, contentEncoding: UByte, sourceChannel: ChannelRelayed?) {
        Logger.i(TAG, "Handle packet (opcode = ${opcode}, subOpcode = ${subOpcode})")

        var data = d
        if (contentEncoding == ContentEncoding.Gzip.value) {
            val isGzipSupported = opcode == Opcode.DATA.value
            if (!isGzipSupported)
                throw Exception("Failed to handle packet, gzip is not supported for this opcode (opcode = ${opcode}, subOpcode = ${subOpcode}, data.length = ${data.remaining()}).")

            val compressedStream = ByteArrayInputStream(data.array(), data.position(), data.remaining())
            val outputStream = ByteArrayOutputStream()
            GZIPInputStream(compressedStream).use { gzipStream ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                while (gzipStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            data = ByteBuffer.wrap(outputStream.toByteArray())
        }

        when (opcode) {
            Opcode.PING.value -> {
                if (sourceChannel != null)
                    sourceChannel.send(Opcode.PONG.value)
                else
                    send(Opcode.PONG.value)
                //Logger.i(TAG, "Received ping, sent pong")
                return
            }
            Opcode.PONG.value -> {
                Logger.v(TAG, "Received pong")
                return
            }
            Opcode.REQUEST.value -> {
                handleRequest(subOpcode, data, sourceChannel)
                return
            }
            Opcode.RESPONSE.value -> {
                handleResponse(subOpcode, data, sourceChannel)
                return
            }
            Opcode.NOTIFY.value -> {
                handleNotify(subOpcode, data, sourceChannel)
                return
            }
            Opcode.RELAY.value -> {
                handleRelay(subOpcode, data, sourceChannel)
                return
            }
            else -> if (isAuthorized) when (opcode) {
                Opcode.STREAM.value -> when (subOpcode)
                {
                    StreamOpcode.START.value -> {
                        val id = data.int
                        val expectedSize = data.int
                        val op = data.get().toUByte()
                        val subOp = data.get().toUByte()
                        val ce = data.get().toUByte()

                        val syncStream = SyncStream(expectedSize, op, subOp, ce)
                        if (data.remaining() > 0) {
                            syncStream.add(data.array(), data.position(), data.remaining())
                        }

                        synchronized(_syncStreams) {
                            _syncStreams[id] = syncStream
                        }
                    }
                    StreamOpcode.DATA.value -> {
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
                    StreamOpcode.END.value -> {
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

                        handlePacket(syncStream.opcode, syncStream.subOpcode, syncStream.getBytes().let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }, syncStream.contentEncoding, sourceChannel)
                    }
                }
                Opcode.DATA.value -> {
                    if (sourceChannel != null)
                        sourceChannel.invokeDataHandler(opcode, subOpcode, data)
                    else
                        _onData?.invoke(this, opcode, subOpcode, data)
                }
                else -> {
                    Logger.w(TAG, "Unknown opcode received (opcode = ${opcode}, subOpcode = ${subOpcode})")
                }
            }
        }
    }

    suspend fun requestConnectionInfo(publicKey: String): ConnectionInfo? {
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<ConnectionInfo?>()
        _pendingConnectionInfoRequests[requestId] = deferred
        try {
            val publicKeyBytes = Base64.getDecoder().decode(publicKey)
            if (publicKeyBytes.size != 32) throw IllegalArgumentException("Public key must be 32 bytes")
            val packet = ByteBuffer.allocate(4 + 32).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(publicKeyBytes)
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.CONNECTION_INFO.value, packet)
        } catch (e: Exception) {
            _pendingConnectionInfoRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    suspend fun requestBulkConnectionInfo(publicKeys: Array<String>): Map<String, ConnectionInfo> {
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<Map<String, ConnectionInfo>>()
        _pendingBulkConnectionInfoRequests[requestId] = deferred
        try {
            val packet = ByteBuffer.allocate(4 + 1 + publicKeys.size * 32).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(publicKeys.size.toByte())
            for (pk in publicKeys) {
                val pkBytes = Base64.getDecoder().decode(pk)
                if (pkBytes.size != 32) throw IllegalArgumentException("Invalid public key length for $pk")
                packet.put(pkBytes)
            }
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.BULK_CONNECTION_INFO.value, packet)
        } catch (e: Exception) {
            _pendingBulkConnectionInfoRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    suspend fun startRelayedChannel(publicKey: String, appId: UInt = 0u, pairingCode: String? = null): ChannelRelayed? {
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<ChannelRelayed>()
        val channel = ChannelRelayed(this, _localKeyPair, publicKey.base64ToByteArray().toBase64(), true)
        _onNewChannel?.invoke(this, channel)
        _pendingChannels[requestId] = channel to deferred
        try {
            channel.sendRequestTransport(requestId, publicKey, appId, pairingCode)
        } catch (e: Exception) {
            _pendingChannels.remove(requestId)?.let { it.first.close(); it.second.completeExceptionally(e) }
            throw e
        }
        return deferred.await()
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(
            Locale.getDefault()) else it.toString() }
        val model = Build.MODEL

        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            "$manufacturer $model".replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun getLimitedUtf8Bytes(str: String, maxByteLength: Int): ByteArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxByteLength) return bytes

        var truncateAt = maxByteLength
        while (truncateAt > 0 && (bytes[truncateAt].toInt() and 0xC0) == 0x80) {
            truncateAt--
        }
        return bytes.copyOf(truncateAt)
    }

    fun publishConnectionInformation(
        authorizedKeys: Array<String>,
        port: Int,
        allowLocalDirect: Boolean,
        allowRemoteDirect: Boolean,
        allowRemoteHolePunched: Boolean,
        allowRemoteRelayed: Boolean
    ) {
        if (authorizedKeys.size > 255) throw IllegalArgumentException("Number of authorized keys exceeds 255")

        val ipv4Addresses = mutableListOf<String>()
        val ipv6Addresses = mutableListOf<String>()
        for (nic in NetworkInterface.getNetworkInterfaces()) {
            if (nic.isUp) {
                for (addr in nic.inetAddresses) {
                    if (!addr.isLoopbackAddress) {
                        when (addr) {
                            is Inet4Address -> ipv4Addresses.add(addr.hostAddress)
                            is Inet6Address -> ipv6Addresses.add(addr.hostAddress)
                        }
                    }
                }
            }
        }

        val deviceName = getDeviceName()
        val nameBytes = getLimitedUtf8Bytes(deviceName, 255)

        val blobSize = 2 + 1 + nameBytes.size + 1 + ipv4Addresses.size * 4 + 1 + ipv6Addresses.size * 16 + 1 + 1 + 1 + 1
        val data = ByteBuffer.allocate(blobSize).order(ByteOrder.LITTLE_ENDIAN)
        data.putShort(port.toShort())
        data.put(nameBytes.size.toByte())
        data.put(nameBytes)
        data.put(ipv4Addresses.size.toByte())
        for (addr in ipv4Addresses) {
            val addrBytes = InetAddress.getByName(addr).address
            data.put(addrBytes)
        }
        data.put(ipv6Addresses.size.toByte())
        for (addr in ipv6Addresses) {
            val addrBytes = InetAddress.getByName(addr).address
            data.put(addrBytes)
        }
        data.put(if (allowLocalDirect) 1 else 0)
        data.put(if (allowRemoteDirect) 1 else 0)
        data.put(if (allowRemoteHolePunched) 1 else 0)
        data.put(if (allowRemoteRelayed) 1 else 0)

        val handshakeSize = 48 // Noise handshake size for N pattern

        data.rewind()
        val ciphertextSize = data.remaining() + 16 // Encrypted data size
        val totalSize = 1 + authorizedKeys.size * (32 + handshakeSize + 4 + ciphertextSize)
        val publishBytes = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        publishBytes.put(authorizedKeys.size.toByte())

        for (key in authorizedKeys) {
            val publicKeyBytes = Base64.getDecoder().decode(key)
            if (publicKeyBytes.size != 32) throw IllegalArgumentException("Public key must be 32 bytes")

            val protocol = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.INITIATOR)
            protocol.remotePublicKey.setPublicKey(publicKeyBytes, 0)
            protocol.start()

            val handshakeMessage = ByteArray(handshakeSize)
            val handshakeBytesWritten = protocol.writeMessage(handshakeMessage, 0, null, 0, 0)
            if (handshakeBytesWritten != handshakeSize) throw IllegalStateException("Handshake message size mismatch")

            val transportPair = protocol.split()

            publishBytes.put(publicKeyBytes)
            publishBytes.put(handshakeMessage)

            val ciphertext = ByteArray(ciphertextSize)
            val ciphertextBytesWritten = transportPair.sender.encryptWithAd(null, data.array(), data.position(), ciphertext, 0, data.remaining())
            if (ciphertextBytesWritten != ciphertextSize) throw IllegalStateException("Ciphertext size mismatch")

            publishBytes.putInt(ciphertextBytesWritten)
            publishBytes.put(ciphertext, 0, ciphertextBytesWritten)
        }

        publishBytes.rewind()
        send(Opcode.NOTIFY.value, NotifyOpcode.CONNECTION_INFO.value, publishBytes)
    }

    suspend fun publishRecords(consumerPublicKeys: List<String>, key: String, data: ByteArray, contentEncoding: ContentEncoding? = null): Boolean {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        if (key.isEmpty() || keyBytes.size > 32) throw IllegalArgumentException("Key must be 1-32 bytes")
        if (consumerPublicKeys.isEmpty()) throw IllegalArgumentException("At least one consumer required")
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<Boolean>()
        _pendingPublishRequests[requestId] = deferred
        try {
            val MAX_PLAINTEXT_SIZE = 65535
            val HANDSHAKE_SIZE = 48
            val LENGTH_SIZE = 4
            val TAG_SIZE = 16
            val chunkCount = (data.size + MAX_PLAINTEXT_SIZE - 1) / MAX_PLAINTEXT_SIZE

            var blobSize = HANDSHAKE_SIZE
            var dataOffset = 0
            for (i in 0 until chunkCount) {
                val chunkSize = minOf(MAX_PLAINTEXT_SIZE, data.size - dataOffset)
                blobSize += LENGTH_SIZE + (chunkSize + TAG_SIZE)
                dataOffset += chunkSize
            }

            val totalPacketSize = 4 + 1 + keyBytes.size + 1 + consumerPublicKeys.size * (32 + 4 + blobSize)
            val packet = ByteBuffer.allocate(totalPacketSize).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(keyBytes.size.toByte())
            packet.put(keyBytes)
            packet.put(consumerPublicKeys.size.toByte())

            for (consumer in consumerPublicKeys) {
                val consumerBytes = Base64.getDecoder().decode(consumer)
                if (consumerBytes.size != 32) throw IllegalArgumentException("Consumer public key must be 32 bytes")
                packet.put(consumerBytes)
                val protocol = HandshakeState(SyncSocketSession.nProtocolName, HandshakeState.INITIATOR).apply {
                    remotePublicKey.setPublicKey(consumerBytes, 0)
                    start()
                }
                val handshakeMessage = ByteArray(HANDSHAKE_SIZE)
                protocol.writeMessage(handshakeMessage, 0, null, 0, 0)
                val transportPair = protocol.split()
                packet.putInt(blobSize)
                packet.put(handshakeMessage)

                dataOffset = 0
                for (i in 0 until chunkCount) {
                    val chunkSize = minOf(MAX_PLAINTEXT_SIZE, data.size - dataOffset)
                    val plaintext = data.copyOfRange(dataOffset, dataOffset + chunkSize)
                    val ciphertext = ByteArray(chunkSize + TAG_SIZE)
                    val written = transportPair.sender.encryptWithAd(null, plaintext, 0, ciphertext, 0, plaintext.size)
                    packet.putInt(written)
                    packet.put(ciphertext, 0, written)
                    dataOffset += chunkSize
                }
            }
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.BULK_PUBLISH_RECORD.value, packet, ce = contentEncoding)
        } catch (e: Exception) {
            _pendingPublishRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    suspend fun getRecord(publisherPublicKey: String, key: String): Pair<ByteArray, Long>? {
        if (key.isEmpty() || key.length > 32) throw IllegalArgumentException("Key must be 1-32 bytes")
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<Pair<ByteArray, Long>?>()
        _pendingGetRecordRequests[requestId] = deferred
        try {
            val publisherBytes = Base64.getDecoder().decode(publisherPublicKey)
            if (publisherBytes.size != 32) throw IllegalArgumentException("Publisher public key must be 32 bytes")
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val packet = ByteBuffer.allocate(4 + 32 + 1 + keyBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(publisherBytes)
            packet.put(keyBytes.size.toByte())
            packet.put(keyBytes)
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.GET_RECORD.value, packet)
        } catch (e: Exception) {
            _pendingGetRecordRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    suspend fun getRecords(publisherPublicKeys: List<String>, key: String): Map<String, Pair<ByteArray, Long>> {
        if (key.isEmpty() || key.length > 32) throw IllegalArgumentException("Key must be 1-32 bytes")
        if (publisherPublicKeys.isEmpty()) return emptyMap()
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<Map<String, Pair<ByteArray, Long>>>()
        _pendingBulkGetRecordRequests[requestId] = deferred
        try {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val packet = ByteBuffer.allocate(4 + 1 + keyBytes.size + 1 + publisherPublicKeys.size * 32).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(keyBytes.size.toByte())
            packet.put(keyBytes)
            packet.put(publisherPublicKeys.size.toByte())
            for (publisher in publisherPublicKeys) {
                val bytes = Base64.getDecoder().decode(publisher)
                if (bytes.size != 32) throw IllegalArgumentException("Publisher public key must be 32 bytes")
                packet.put(bytes)
            }
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.BULK_GET_RECORD.value, packet)
        } catch (e: Exception) {
            _pendingBulkGetRecordRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    suspend fun deleteRecords(publisherPublicKey: String, consumerPublicKey: String, keys: List<String>): Boolean {
        if (keys.any { it.toByteArray(Charsets.UTF_8).size > 32 }) throw IllegalArgumentException("Keys must be at most 32 bytes")
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<Boolean>()
        _pendingDeleteRequests[requestId] = deferred
        try {
            val publisherBytes = Base64.getDecoder().decode(publisherPublicKey)
            if (publisherBytes.size != 32) throw IllegalArgumentException("Publisher public key must be 32 bytes")
            val consumerBytes = Base64.getDecoder().decode(consumerPublicKey)
            if (consumerBytes.size != 32) throw IllegalArgumentException("Consumer public key must be 32 bytes")
            val packetSize = 4 + 32 + 32 + 1 + keys.sumOf { 1 + it.toByteArray(Charsets.UTF_8).size }
            val packet = ByteBuffer.allocate(packetSize).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(publisherBytes)
            packet.put(consumerBytes)
            packet.put(keys.size.toByte())
            for (key in keys) {
                val keyBytes = key.toByteArray(Charsets.UTF_8)
                packet.put(keyBytes.size.toByte())
                packet.put(keyBytes)
            }
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.BULK_DELETE_RECORD.value, packet)
        } catch (e: Exception) {
            _pendingDeleteRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    suspend fun listRecordKeys(publisherPublicKey: String, consumerPublicKey: String): List<Pair<String, Long>> {
        val requestId = generateRequestId()
        val deferred = CompletableDeferred<List<Pair<String, Long>>>()
        _pendingListKeysRequests[requestId] = deferred
        try {
            val publisherBytes = Base64.getDecoder().decode(publisherPublicKey)
            if (publisherBytes.size != 32) throw IllegalArgumentException("Publisher public key must be 32 bytes")
            val consumerBytes = Base64.getDecoder().decode(consumerPublicKey)
            if (consumerBytes.size != 32) throw IllegalArgumentException("Consumer public key must be 32 bytes")
            val packet = ByteBuffer.allocate(4 + 32 + 32).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(requestId)
            packet.put(publisherBytes)
            packet.put(consumerBytes)
            packet.rewind()
            send(Opcode.REQUEST.value, RequestOpcode.LIST_RECORD_KEYS.value, packet)
        } catch (e: Exception) {
            _pendingListKeysRequests.remove(requestId)?.completeExceptionally(e)
            throw e
        }
        return deferred.await()
    }

    companion object {
        val dh = "25519"
        val pattern = "N"
        val cipher = "ChaChaPoly"
        val hash = "BLAKE2b"
        var nProtocolName = "Noise_${pattern}_${dh}_${cipher}_${hash}"

        private const val TAG = "SyncSocketSession"
        const val MAXIMUM_PACKET_SIZE = 65535 - 16
        const val MAXIMUM_PACKET_SIZE_ENCRYPTED = MAXIMUM_PACKET_SIZE + 16
        const val HEADER_SIZE = 7
    }
}