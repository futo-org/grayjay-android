package com.futo.platformplayer

import com.futo.platformplayer.noise.protocol.DHState
import com.futo.platformplayer.noise.protocol.Noise
import com.futo.platformplayer.sync.internal.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration.Companion.seconds

data class PipeStreams(
    val initiatorInput: LittleEndianDataInputStream,
    val initiatorOutput: LittleEndianDataOutputStream,
    val responderInput: LittleEndianDataInputStream,
    val responderOutput: LittleEndianDataOutputStream
)

typealias OnHandshakeComplete = (SyncSocketSession) -> Unit
typealias IsHandshakeAllowed = (LinkType, SyncSocketSession, String, String?, UInt) -> Boolean
typealias OnClose = (SyncSocketSession) -> Unit
typealias OnData = (SyncSocketSession, UByte, UByte, ByteBuffer) -> Unit

class SyncSocketTests {
    private fun createPipeStreams(): PipeStreams {
        val initiatorOutput = PipedOutputStream()
        val responderOutput = PipedOutputStream()
        val responderInput = PipedInputStream(initiatorOutput)
        val initiatorInput = PipedInputStream(responderOutput)
        return PipeStreams(
            LittleEndianDataInputStream(initiatorInput), LittleEndianDataOutputStream(initiatorOutput),
            LittleEndianDataInputStream(responderInput), LittleEndianDataOutputStream(responderOutput)
        )
    }
    
    fun generateKeyPair(): DHState {
        val p = Noise.createDH("25519")
        p.generateKeyPair()
        return p
    }

    private fun createSessions(
        initiatorInput: LittleEndianDataInputStream,
        initiatorOutput: LittleEndianDataOutputStream,
        responderInput: LittleEndianDataInputStream,
        responderOutput: LittleEndianDataOutputStream,
        initiatorKeyPair: DHState,
        responderKeyPair: DHState,
        onInitiatorHandshakeComplete: OnHandshakeComplete,
        onResponderHandshakeComplete: OnHandshakeComplete,
        onInitiatorClose: OnClose? = null,
        onResponderClose: OnClose? = null,
        onClose: OnClose? = null,
        isHandshakeAllowed: IsHandshakeAllowed? = null,
        onDataA: OnData? = null,
        onDataB: OnData? = null
    ): Pair<SyncSocketSession, SyncSocketSession> {
        val initiatorSession = SyncSocketSession(
            "", initiatorKeyPair, initiatorInput, initiatorOutput,
            onClose = {
                onClose?.invoke(it)
                onInitiatorClose?.invoke(it)
            },
            onHandshakeComplete = onInitiatorHandshakeComplete,
            onData = onDataA,
            isHandshakeAllowed = isHandshakeAllowed
        )

        val responderSession = SyncSocketSession(
            "", responderKeyPair, responderInput, responderOutput,
            onClose = {
                onClose?.invoke(it)
                onResponderClose?.invoke(it)
            },
            onHandshakeComplete = onResponderHandshakeComplete,
            onData = onDataB,
            isHandshakeAllowed = isHandshakeAllowed
        )

        return Pair(initiatorSession, responderSession)
    }

    @Test
    fun handshake_WithValidPairingCode_Succeeds(): Unit = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()
        val validPairingCode = "secret"

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            isHandshakeAllowed = { _, _, _, pairingCode, _ -> pairingCode == validPairingCode }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey, pairingCode = validPairingCode)
        responderSession.startAsResponder()

        withTimeout(5.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }
    }

    @Test
    fun handshake_WithInvalidPairingCode_Fails() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()
        val validPairingCode = "secret"
        val invalidPairingCode = "wrong"

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val initiatorClosed = CompletableDeferred<Boolean>()
        val responderClosed = CompletableDeferred<Boolean>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onInitiatorClose = {
                initiatorClosed.complete(true)
            },
            onResponderClose = {
                responderClosed.complete(true)
            },
            isHandshakeAllowed = { _, _, _, pairingCode, _ -> pairingCode == validPairingCode }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey, pairingCode = invalidPairingCode)
        responderSession.startAsResponder()

        withTimeout(100.seconds) {
            initiatorClosed.await()
            responderClosed.await()
        }

        assertFalse(handshakeInitiatorCompleted.isCompleted)
        assertFalse(handshakeResponderCompleted.isCompleted)
    }

    @Test
    fun handshake_WithoutPairingCodeWhenRequired_Fails() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()
        val validPairingCode = "secret"

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val initiatorClosed = CompletableDeferred<Boolean>()
        val responderClosed = CompletableDeferred<Boolean>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onInitiatorClose = {
                initiatorClosed.complete(true)
            },
            onResponderClose = {
                responderClosed.complete(true)
            },
            isHandshakeAllowed = { _, _, _, pairingCode, _ -> pairingCode == validPairingCode }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey) // No pairing code
        responderSession.startAsResponder()

        withTimeout(5.seconds) {
            initiatorClosed.await()
            responderClosed.await()
        }

        assertFalse(handshakeInitiatorCompleted.isCompleted)
        assertFalse(handshakeResponderCompleted.isCompleted)
    }

    @Test
    fun handshake_WithPairingCodeWhenNotRequired_Succeeds(): Unit = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()
        val pairingCode = "unnecessary"

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            isHandshakeAllowed = { _, _, _, _, _ -> true } // Always allow
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey, pairingCode = pairingCode)
        responderSession.startAsResponder()

        withTimeout(10.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }
    }

    @Test
    fun sendAndReceive_SmallDataPacket_Succeeds() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val tcsDataReceived = CompletableDeferred<ByteArray>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onDataB = { _, opcode, subOpcode, data ->
                if (opcode == Opcode.DATA.value && subOpcode == 0u.toUByte()) {
                    val b = ByteArray(data.remaining())
                    data.get(b)
                    tcsDataReceived.complete(b)
                }
            }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey)
        responderSession.startAsResponder()

        withTimeout(10.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }

        // Ensure both sessions are authorized
        initiatorSession.authorizable = Authorized()
        responderSession.authorizable = Authorized()

        val smallData = byteArrayOf(1, 2, 3)
        initiatorSession.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(smallData))

        val receivedData = withTimeout(10.seconds) { tcsDataReceived.await() }
        assertArrayEquals(smallData, receivedData)
    }

    @Test
    fun sendAndReceive_ExactlyMaximumPacketSize_Succeeds() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val tcsDataReceived = CompletableDeferred<ByteArray>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onDataB = { _, opcode, subOpcode, data ->
                if (opcode == Opcode.DATA.value && subOpcode == 0u.toUByte()) {
                    val b = ByteArray(data.remaining())
                    data.get(b)
                    tcsDataReceived.complete(b)
                }
            }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey)
        responderSession.startAsResponder()

        withTimeout(10.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }

        // Ensure both sessions are authorized
        initiatorSession.authorizable = Authorized()
        responderSession.authorizable = Authorized()

        val maxData = ByteArray(SyncSocketSession.MAXIMUM_PACKET_SIZE - SyncSocketSession.HEADER_SIZE).apply { Random.nextBytes(this) }
        initiatorSession.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(maxData))

        val receivedData = withTimeout(10.seconds) { tcsDataReceived.await() }
        assertArrayEquals(maxData, receivedData)
    }

    @Test
    fun stream_LargeData_Succeeds() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val tcsDataReceived = CompletableDeferred<ByteArray>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onDataB = { _, opcode, subOpcode, data ->
                if (opcode == Opcode.DATA.value && subOpcode == 0u.toUByte()) {
                    val b = ByteArray(data.remaining())
                    data.get(b)
                    tcsDataReceived.complete(b)
                }
            }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey)
        responderSession.startAsResponder()

        withTimeout(10.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }

        // Ensure both sessions are authorized
        initiatorSession.authorizable = Authorized()
        responderSession.authorizable = Authorized()

        val largeData = ByteArray(2 * (SyncSocketSession.MAXIMUM_PACKET_SIZE - SyncSocketSession.HEADER_SIZE)).apply { Random.nextBytes(this) }
        initiatorSession.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(largeData))

        val receivedData = withTimeout(10.seconds) { tcsDataReceived.await() }
        assertArrayEquals(largeData, receivedData)
    }

    @Test
    fun authorizedSession_CanSendData() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val tcsDataReceived = CompletableDeferred<ByteArray>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onDataB = { _, opcode, subOpcode, data ->
                if (opcode == Opcode.DATA.value && subOpcode == 0u.toUByte()) {
                    val b = ByteArray(data.remaining())
                    data.get(b)
                    tcsDataReceived.complete(b)
                }
            }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey)
        responderSession.startAsResponder()

        withTimeout(10.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }

        // Authorize both sessions
        initiatorSession.authorizable = Authorized()
        responderSession.authorizable = Authorized()

        val data = byteArrayOf(1, 2, 3)
        initiatorSession.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(data))

        val receivedData = withTimeout(10.seconds) { tcsDataReceived.await() }
        assertArrayEquals(data, receivedData)
    }

    @Test
    fun unauthorizedSession_CannotSendData() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val tcsDataReceived = CompletableDeferred<ByteArray>()

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onDataB = { _, _, _, _ -> }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey)
        responderSession.startAsResponder()

        withTimeout(10.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }

        // Authorize initiator but not responder
        initiatorSession.authorizable = Authorized()
        responderSession.authorizable = Unauthorized()

        val data = byteArrayOf(1, 2, 3)
        initiatorSession.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(data))

        delay(1.seconds)
        assertFalse(tcsDataReceived.isCompleted)
    }

    @Test
    fun directHandshake_WithValidAppId_Succeeds() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()
        val allowedAppId = 1234u

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()

        val responderIsHandshakeAllowed = { linkType: LinkType, _: SyncSocketSession, _: String, _: String?, appId: UInt ->
            linkType == LinkType.Direct && appId == allowedAppId
        }

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            isHandshakeAllowed = responderIsHandshakeAllowed
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey, appId = allowedAppId)
        responderSession.startAsResponder()

        withTimeout(5.seconds) {
            handshakeInitiatorCompleted.await()
            handshakeResponderCompleted.await()
        }

        assertNotNull(initiatorSession.remotePublicKey)
        assertNotNull(responderSession.remotePublicKey)
    }

    @Test
    fun directHandshake_WithInvalidAppId_Fails() = runBlocking {
        val (initiatorInput, initiatorOutput, responderInput, responderOutput) = createPipeStreams()
        val initiatorKeyPair = generateKeyPair()
        val responderKeyPair = generateKeyPair()
        val allowedAppId = 1234u
        val invalidAppId = 5678u

        val handshakeInitiatorCompleted = CompletableDeferred<Boolean>()
        val handshakeResponderCompleted = CompletableDeferred<Boolean>()
        val initiatorClosed = CompletableDeferred<Boolean>()
        val responderClosed = CompletableDeferred<Boolean>()

        val responderIsHandshakeAllowed = { linkType: LinkType, _: SyncSocketSession, _: String, _: String?, appId: UInt ->
            linkType == LinkType.Direct && appId == allowedAppId
        }

        val (initiatorSession, responderSession) = createSessions(
            initiatorInput, initiatorOutput, responderInput, responderOutput,
            initiatorKeyPair, responderKeyPair,
            { handshakeInitiatorCompleted.complete(true) },
            { handshakeResponderCompleted.complete(true) },
            onInitiatorClose = {
                initiatorClosed.complete(true)
            },
            onResponderClose = {
                responderClosed.complete(true)
            },
            isHandshakeAllowed = responderIsHandshakeAllowed
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey, appId = invalidAppId)
        responderSession.startAsResponder()

        withTimeout(5.seconds) {
            initiatorClosed.await()
            responderClosed.await()
        }

        assertFalse(handshakeInitiatorCompleted.isCompleted)
        assertFalse(handshakeResponderCompleted.isCompleted)
    }
}

class Authorized : IAuthorizable {
    override val isAuthorized: Boolean = true
}

class Unauthorized : IAuthorizable {
    override val isAuthorized: Boolean = false
}