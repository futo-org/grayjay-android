import com.futo.platformplayer.LittleEndianDataInputStream
import com.futo.platformplayer.LittleEndianDataOutputStream
import com.futo.platformplayer.logging.ILogConsumer
import com.futo.platformplayer.logging.LogLevel
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.noise.protocol.CipherState
import com.futo.platformplayer.noise.protocol.CipherStatePair
import com.futo.platformplayer.noise.protocol.HandshakeState
import com.futo.platformplayer.noise.protocol.Noise
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.IAuthorizable
import com.futo.platformplayer.sync.internal.SyncSocketSession
import com.futo.platformplayer.sync.internal.SyncStream
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class NoiseProtocolTest {
    constructor() {
        Logger.setLogConsumers(listOf(
            object : ILogConsumer {
                override fun willConsume(level: LogLevel, tag: String): Boolean {
                    return true
                }

                override fun consume(level: LogLevel, tag: String, text: String?, e: Throwable?) {
                    when (level) {
                        LogLevel.VERBOSE -> println("${level};INTERNAL;$tag: ${text}, ${e}")
                        LogLevel.INFORMATION -> println("${level};INTERNAL;$tag: ${text}, ${e}")
                        LogLevel.WARNING -> println("${level};INTERNAL;$tag: ${text}, ${e}")
                        LogLevel.ERROR -> println("${level};INTERNAL;$tag: ${text}, ${e}")
                        else -> throw Exception("Unknown log level")
                    }
                }

            }
        ))
    }

    class TestMessage {
        val payload: ByteArray
        val cipherText: ByteArray

        @OptIn(ExperimentalStdlibApi::class)
        constructor(p: String, c: String) {
            payload = p.hexToByteArray()
            cipherText = c.hexToByteArray()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testNoiseIK25519HandshakeAndMessage() {
        val dh = "25519"
        val pattern = "IK"
        val cipher = "ChaChaPoly"
        val hash = "BLAKE2b"
        var protocolName = "Noise"
        protocolName += "_${pattern}_${dh}_${cipher}_${hash}"

        val messages = arrayListOf(
            TestMessage("4c756477696720766f6e204d69736573", "ca35def5ae56cec33dc2036731ab14896bc4c75dbb07a61f879f8e3afa4c7944ba83a447b38c83e327ad936929812f624884847b7831e95e197b2f797088efdd232fe541af156ec6d0657602902a8c3ee64e470f4b6fcd9298ce0b56fe20f86e60d9d933ec6e103ffb09e6001d6abb64"),
            TestMessage("4d757272617920526f746862617264", "95ebc60d2b1fa672c1f46a8aa265ef51bfe38e7ccb39ec5be34069f1448088439f069b267a06b3de3ecb1043bcb09807c6cd101f3826192a65f11ef3fe4317"),
            TestMessage("462e20412e20486179656b", "cd54383060e7a28434cca27fb1cc524cfbabeb18181589df219d07"),
            TestMessage("4361726c204d656e676572", "a856d3bf0246bfc476c655009cd1ed677b8dcc5b349ae8ef2a05f2"),
            TestMessage("4a65616e2d426170746973746520536179", "49063084b2c51f098337cb8a13739ac848f907e67cfb2cc8a8b60586467aa02fc7"),
            TestMessage("457567656e2042f6686d20766f6e2042617765726b", "8b9709d23b47e4639df7678d7a21741eba4ef1e9c60383001c7435549c20f9d56f30e935d3")
         )

        val initiator = HandshakeState(protocolName, HandshakeState.INITIATOR)
        val responder = HandshakeState(protocolName, HandshakeState.RESPONDER)
        assertEquals(HandshakeState.INITIATOR, initiator.role)
        assertEquals(HandshakeState.RESPONDER, responder.role)
        assertEquals(protocolName, initiator.protocolName)
        assertEquals(protocolName, responder.protocolName)

        // Set all keys and special values that we need.
        val init_prologue = "50726f6c6f677565313233".hexToByteArray()
        initiator.setPrologue(init_prologue, 0, init_prologue.size)
        initiator.localKeyPair.setPrivateKey("e61ef9919cde45dd5f82166404bd08e38bceb5dfdfded0a34c8df7ed542214d1".hexToByteArray(), 0)
        initiator.remotePublicKey.setPublicKey("31e0303fd6418d2f8c0e78b91f22e8caed0fbe48656dcf4767e4834f701b8f62".hexToByteArray(), 0)
        initiator.getFixedEphemeralKey().setPrivateKey("893e28b9dc6ca8d611ab664754b8ceb7bac5117349a4439a6b0569da977c464a".hexToByteArray(), 0)

        val prologue = "50726f6c6f677565313233".hexToByteArray()
        responder.setPrologue(prologue, 0, prologue.size)

        responder.localKeyPair.setPrivateKey("4a3acbfdb163dec651dfa3194dece676d437029c62a408b4c5ea9114246e4893".hexToByteArray(), 0)
        responder.getFixedEphemeralKey().setPrivateKey("bbdb4cdbd309f1a1f2e1456967fe288cadd6f712d65dc7b7793d5e63da6b375b".hexToByteArray(), 0)

        // Start both sides of the handshake.
        assertEquals(HandshakeState.NO_ACTION, initiator.action)
        assertEquals(HandshakeState.NO_ACTION, responder.action)
        initiator.start()
        responder.start()
        assertEquals(HandshakeState.WRITE_MESSAGE, initiator.action)
        assertEquals(HandshakeState.READ_MESSAGE, responder.action)

        // Work through the messages one by one until both sides "split".
        var role: Int = HandshakeState.INITIATOR
        var index = 0
        var send: HandshakeState?
        var recv: HandshakeState?
        val isOneWay = false
        val message = ByteArray(8192)
        val plaintext = ByteArray(8192)
        while (index < messages.size) {
            if (initiator.action == HandshakeState.SPLIT && responder.action == HandshakeState.SPLIT) {
                break
            }
            if (role == HandshakeState.INITIATOR) {
                // Send on the initiator, receive on the responder.
                send = initiator
                recv = responder
                if (!isOneWay)role = HandshakeState.RESPONDER
            } else {
                // Send on the responder, receive on the initiator.
                send = responder
                recv = initiator
                role = HandshakeState.INITIATOR
            }
            assertEquals(HandshakeState.WRITE_MESSAGE, send.action)
            assertEquals(HandshakeState.READ_MESSAGE, recv.action)
            val msg: TestMessage = messages[index]
            val len: Int = send.writeMessage(message, 0, msg.payload, 0, msg.payload.size)
            assertEquals(msg.cipherText.size, len)
            assertSubArrayEquals("$index: ciphertext", msg.cipherText, message)
            val plen: Int = recv.readMessage(message, 0, len, plaintext, 0)
            assertEquals(msg.payload.size, plen)
            assertSubArrayEquals("$index: payload", msg.payload, plaintext)
            ++index
        }

        assertEquals(HandshakeState.INITIATOR, initiator.role)
        assertEquals(HandshakeState.RESPONDER, responder.role)

        // Handshake finished.  Check the handshake hash values.
        val handshakeHash = "00e51d2aac81a9b8ebe441d6af3e1c8efc0f030cc608332edcb42588ff6a0ce26415ddc106e95277a5e6d54132f1e5245976b89caf96d262f1fe5a7f0c55c078".hexToByteArray()
        assertArrayEquals(handshakeHash, initiator.getHandshakeHash())
        assertArrayEquals(handshakeHash, responder.getHandshakeHash())
        assertEquals(HandshakeState.SPLIT, initiator.action)
        assertEquals(HandshakeState.SPLIT, responder.action)


        // Split the two sides to get the transport ciphers.
        val initPair: CipherStatePair = initiator.split()
        val respPair: CipherStatePair = responder.split()
        assertEquals(HandshakeState.COMPLETE, initiator.action)
        assertEquals(HandshakeState.COMPLETE, responder.action)

        // Now handle the data transport.
        var csend: CipherState?
        var crecv: CipherState?
        while (index < messages.size) {
            val msg: TestMessage = messages[index]
            if (role == HandshakeState.INITIATOR) {
                // Send on the initiator, receive on the responder.
                csend = initPair.sender
                crecv = respPair.receiver
                if (!isOneWay)role = HandshakeState.RESPONDER
            } else {
                // Send on the responder, receive on the initiator.
                csend = respPair.sender
                crecv = initPair.receiver
                role = HandshakeState.INITIATOR
            }
            val len: Int = csend.encryptWithAd(null, msg.payload, 0, message, 0, msg.payload.size)
            assertEquals(msg.cipherText.size, len)
            assertSubArrayEquals("$index: ciphertext", msg.cipherText, message)
            val plen: Int = crecv.decryptWithAd(null, message, 0, plaintext, 0, len)
            assertEquals(msg.payload.size, plen)
            assertSubArrayEquals("$index: payload", msg.payload, plaintext)
            ++index
        }

        // Clean up.
        initiator.destroy()
        responder.destroy()
        initPair.destroy()
        respPair.destroy()
    }

    @Test
    fun testNoiseIK25519HandshakeAndMessageRandom() {
        val dh = "25519"
        val pattern = "IK"
        val cipher = "ChaChaPoly"
        val hash = "BLAKE2b"
        var protocolName = "Noise"
        protocolName += "_${pattern}_${dh}_${cipher}_${hash}"

        val initiator = HandshakeState(protocolName, HandshakeState.INITIATOR)
        val responder = HandshakeState(protocolName, HandshakeState.RESPONDER)
        assertEquals(HandshakeState.INITIATOR, initiator.role)
        assertEquals(HandshakeState.RESPONDER, responder.role)
        assertEquals(protocolName, initiator.protocolName)
        assertEquals(protocolName, responder.protocolName)

        // Set all keys and special values that we need.
        responder.localKeyPair.generateKeyPair()
        val responderPublicKey = ByteArray(responder.localKeyPair.publicKeyLength)
        responder.localKeyPair.getPublicKey(responderPublicKey, 0)

        initiator.localKeyPair.generateKeyPair()
        initiator.remotePublicKey.setPublicKey(responderPublicKey, 0)

        // Start both sides of the handshake.
        assertEquals(HandshakeState.NO_ACTION, initiator.action)
        assertEquals(HandshakeState.NO_ACTION, responder.action)
        initiator.start()
        responder.start()
        assertEquals(HandshakeState.WRITE_MESSAGE, initiator.action)
        assertEquals(HandshakeState.READ_MESSAGE, responder.action)

        // Work through the messages one by one until both sides "split".
        var role: Int = HandshakeState.INITIATOR
        var send: HandshakeState?
        var recv: HandshakeState?
        val message = ByteArray(8192)
        val plaintext = ByteArray(8192)
        for (i in 0..10) {
            if (i == 9)
                throw Exception("Handshake not finished in time")

            if (initiator.action == HandshakeState.SPLIT && responder.action == HandshakeState.SPLIT) {
                break
            }

            if (role == HandshakeState.INITIATOR) {
                // Send on the initiator, receive on the responder.
                send = initiator
                recv = responder
                role = HandshakeState.RESPONDER
            } else {
                // Send on the responder, receive on the initiator.
                send = responder
                recv = initiator
                role = HandshakeState.INITIATOR
            }
            assertEquals(HandshakeState.WRITE_MESSAGE, send.action)
            assertEquals(HandshakeState.READ_MESSAGE, recv.action)
            val len: Int = send.writeMessage(message, 0, null, 0, 0)
            recv.readMessage(message, 0, len, plaintext, 0)
        }

        assertEquals(HandshakeState.INITIATOR, initiator.role)
        assertEquals(HandshakeState.RESPONDER, responder.role)

        // Handshake finished.  Check the handshake hash values.
        assertEquals(HandshakeState.SPLIT, initiator.action)
        assertEquals(HandshakeState.SPLIT, responder.action)

        // Split the two sides to get the transport ciphers.
        val initPair: CipherStatePair = initiator.split()
        val respPair: CipherStatePair = responder.split()
        assertEquals(HandshakeState.COMPLETE, initiator.action)
        assertEquals(HandshakeState.COMPLETE, responder.action)

        // Now handle the data transport.
        var csend: CipherState?
        var crecv: CipherState?
        for (i in 0..1) {
            if (role == HandshakeState.INITIATOR) {
                // Send on the initiator, receive on the responder.
                csend = initPair.sender
                crecv = respPair.receiver
                role = HandshakeState.RESPONDER
            } else {
                // Send on the responder, receive on the initiator.
                csend = respPair.sender
                crecv = initPair.receiver
                role = HandshakeState.INITIATOR
            }
            val expected = "Message counter $i"
            val payload = expected.toByteArray()
            val len: Int = csend.encryptWithAd(null, payload, 0, message, 0, payload.size)
            val plen: Int = crecv.decryptWithAd(null, message, 0, plaintext, 0, len)
            assertEquals(expected, plaintext.slice(IntRange(0, plen - 1)).toByteArray().decodeToString())
        }

        // Clean up.
        initiator.destroy()
        responder.destroy()
        initPair.destroy()
        respPair.destroy()
    }

    @Test
    fun testNoiseIK25519HandshakeAndMessageRandomSenderReceiver() {
        val dh = "25519"
        val pattern = "IK"
        val cipher = "ChaChaPoly"
        val hash = "BLAKE2b"
        var protocolName = "Noise"
        protocolName += "_${pattern}_${dh}_${cipher}_${hash}"

        val initiator = HandshakeState(protocolName, HandshakeState.INITIATOR)
        assertEquals(HandshakeState.INITIATOR, initiator.role)
        assertEquals(protocolName, initiator.protocolName)

        val responder = HandshakeState(protocolName, HandshakeState.RESPONDER)
        assertEquals(HandshakeState.RESPONDER, responder.role)
        assertEquals(protocolName, responder.protocolName)

        responder.localKeyPair.generateKeyPair()
        val responderPublicKey = ByteArray(responder.localKeyPair.publicKeyLength)
        responder.localKeyPair.getPublicKey(responderPublicKey, 0)

        initiator.localKeyPair.generateKeyPair()
        initiator.remotePublicKey.setPublicKey(responderPublicKey, 0)

        val initiatorToResponderOut = PipedOutputStream()
        val responderToInitiatorOut = PipedOutputStream()

        val initiatorToResponderIn = PipedInputStream(initiatorToResponderOut)
        val responderToInitiatorIn = PipedInputStream(responderToInitiatorOut)

        // Start both sides of the handshake.
        val responderThread = Thread {
            val writer = DataOutputStream(responderToInitiatorOut)
            val reader = DataInputStream(initiatorToResponderIn)

            writer.writeInt(23145)
            val testValue = reader.readInt()
            assertEquals(3141, testValue)

            assertEquals(HandshakeState.NO_ACTION, responder.action)
            responder.start()
            assertEquals(HandshakeState.READ_MESSAGE, responder.action)

            System.out.println("Responder start")

            val message = ByteArray(8192)
            val plaintext = ByteArray(8192)

            var len: Int
            while (true) {
                if (responder.action == HandshakeState.SPLIT) {
                    break
                }

                val messageSize = reader.readInt()
                reader.read(message, 0, messageSize)
                System.out.println("Responder read (messageSize = ${messageSize}): ${Base64.getEncoder().encodeToString(message.slice(IntRange(0, messageSize - 1)).toByteArray())}")
                responder.readMessage(message, 0, messageSize, plaintext, 0)

                if (responder.action == HandshakeState.SPLIT) {
                    break
                }

                len = responder.writeMessage(message, 0, null, 0, 0)
                writer.writeInt(len)
                writer.write(message, 0, len)
                System.out.println("Responder wrote (len = ${len}): ${Base64.getEncoder().encodeToString(message.slice(IntRange(0, len - 1)).toByteArray())}")
            }

            System.out.println("Responder handshake complete")

            assertEquals(HandshakeState.RESPONDER, responder.role)
            assertEquals(HandshakeState.SPLIT, responder.action)
            val respPair: CipherStatePair = responder.split()
            assertEquals(HandshakeState.COMPLETE, responder.action)

            assertEquals(true, responder.hasRemotePublicKey())
            val responderRemotePublicKey = ByteArray(responder.remotePublicKey.publicKeyLength)
            responder.remotePublicKey.getPublicKey(responderRemotePublicKey, 0)
            val initiatorLocalPublicKey = ByteArray(initiator.localKeyPair.publicKeyLength)
            initiator.localKeyPair.getPublicKey(initiatorLocalPublicKey, 0)
            assertArrayEquals(initiatorLocalPublicKey, responderRemotePublicKey)

            System.out.println("Initiator local public key: ${Base64.getEncoder().encodeToString(initiatorLocalPublicKey)}")
            System.out.println("Responder remote public key: ${Base64.getEncoder().encodeToString(responderRemotePublicKey)}")

            //Handshake complete, now exchange a message
            val expected = "Hello from responder"
            val payload = expected.toByteArray()
            len = respPair.sender.encryptWithAd(null, payload, 0, message, 0, payload.size)
            writer.writeInt(len)
            writer.write(message, 0, len)

            val messageSize = reader.readInt()
            reader.read(message, 0, messageSize)
            val plen: Int = respPair.receiver.decryptWithAd(null, message, 0, plaintext, 0, messageSize)

            val ptext = plaintext.slice(IntRange(0, plen - 1)).toByteArray().decodeToString()
            System.out.println("Responder read: ${ptext}")
            assertEquals("Hello from initiator", ptext)

            respPair.destroy()
        }.apply { start() }

        val initiatorThread = Thread {
            val writer = DataOutputStream(initiatorToResponderOut)
            val reader = DataInputStream(responderToInitiatorIn)

            writer.writeInt(3141)
            val testValue = reader.readInt()
            assertEquals(23145, testValue)

            assertEquals(HandshakeState.NO_ACTION, initiator.action)
            initiator.start()
            assertEquals(HandshakeState.WRITE_MESSAGE, initiator.action)

            val message = ByteArray(8192)
            val plaintext = ByteArray(8192)

            System.out.println("Initiator start")

            var len: Int = initiator.writeMessage(message, 0, null, 0, 0)
            writer.writeInt(len)
            writer.write(message, 0, len)

            System.out.println("Initiator wrote: ${Base64.getEncoder().encodeToString(message.slice(IntRange(0, len - 1)).toByteArray())}")

            while (true) {
                if (initiator.action == HandshakeState.SPLIT) {
                    break
                }

                val messageSize = reader.readInt()
                reader.read(message, 0, messageSize)
                System.out.println("Initiator read (messageSize = ${messageSize}): ${Base64.getEncoder().encodeToString(message.slice(IntRange(0, messageSize - 1)).toByteArray())}")
                initiator.readMessage(message, 0, messageSize, plaintext, 0)

                if (initiator.action == HandshakeState.SPLIT) {
                    break
                }

                len = initiator.writeMessage(message, 0, null, 0, 0)
                writer.writeInt(len)
                writer.write(message, 0, len)

                System.out.println("Initiator wrote (len = ${len}): ${Base64.getEncoder().encodeToString(message.slice(IntRange(0, len - 1)).toByteArray())}")
            }

            System.out.println("Initiator handshake complete")

            assertEquals(HandshakeState.INITIATOR, initiator.role)
            assertEquals(HandshakeState.SPLIT, initiator.action)
            val initPair: CipherStatePair = initiator.split()
            assertEquals(HandshakeState.COMPLETE, initiator.action)

            assertEquals(true, initiator.hasRemotePublicKey())
            val initiatorRemotePublicKey = ByteArray(initiator.remotePublicKey.publicKeyLength)
            initiator.remotePublicKey.getPublicKey(initiatorRemotePublicKey, 0)
            val responderLocalPublicKey = ByteArray(responder.localKeyPair.publicKeyLength)
            responder.localKeyPair.getPublicKey(responderLocalPublicKey, 0)
            assertArrayEquals(responderLocalPublicKey, initiatorRemotePublicKey)

            System.out.println("Responder local public key: ${Base64.getEncoder().encodeToString(responderLocalPublicKey)}")
            System.out.println("Initiator remote public key: ${Base64.getEncoder().encodeToString(initiatorRemotePublicKey)}")

            val expected = "Hello from initiator"
            val payload = expected.toByteArray()
            len = initPair.sender.encryptWithAd(null, payload, 0, message, 0, payload.size)
            writer.writeInt(len)
            writer.write(message, 0, len)

            val messageSize = reader.readInt()
            reader.read(message, 0, messageSize)
            val plen: Int = initPair.receiver.decryptWithAd(null, message, 0, plaintext, 0, messageSize)

            val ptext = plaintext.slice(IntRange(0, plen - 1)).toByteArray().decodeToString()
            System.out.println("Initiator read: ${ptext}")
            assertEquals("Hello from responder", ptext)

            initPair.destroy()
        }.apply { start() }

        responderThread.join()
        initiatorThread.join()

        initiator.destroy()
        responder.destroy()
    }

    private class Authorized : IAuthorizable {
        override val isAuthorized: Boolean = true
    }

    @Test
    fun testSyncSessionHandshakeAndCommunication() {
        // Create piped streams to simulate a network connection
        val initiatorToResponderOut = PipedOutputStream()
        val responderToInitiatorOut = PipedOutputStream()
        val initiatorToResponderIn = PipedInputStream(initiatorToResponderOut)
        val responderToInitiatorIn = PipedInputStream(responderToInitiatorOut)

        val initiatorInput = LittleEndianDataInputStream(responderToInitiatorIn)
        val initiatorOutput = LittleEndianDataOutputStream(initiatorToResponderOut)
        val responderInput = LittleEndianDataInputStream(initiatorToResponderIn)
        val responderOutput = LittleEndianDataOutputStream(responderToInitiatorOut)

        // Latches to track when handshake and communication are complete
        val handshakeLatch = CountDownLatch(2)

        val initiatorKeyPair = Noise.createDH(StateSync.dh)
        initiatorKeyPair.generateKeyPair()
        val responderKeyPair = Noise.createDH(StateSync.dh)
        responderKeyPair.generateKeyPair()

        val randomBytesExactlyOnePacket = generateRandomByteArray(SyncSocketSession.MAXIMUM_PACKET_SIZE - SyncSocketSession.HEADER_SIZE)
        val randomBytes = generateRandomByteArray(2 * (SyncSocketSession.MAXIMUM_PACKET_SIZE - SyncSocketSession.HEADER_SIZE))
        val randomBytesBig = generateRandomByteArray(SyncStream.MAXIMUM_SIZE)

        // Create and start the initiator session
        val initiatorSession = SyncSocketSession("", initiatorKeyPair,
            initiatorInput,
            initiatorOutput,
            onClose = { session ->
                println("Initiator session closed")
            },
            onHandshakeComplete = { session ->
                println("Initiator handshake complete")
                handshakeLatch.countDown()  // Handshake complete for initiator
            },
            onData = { session, opcode, subOpcode, data ->
                println("Initiator received: Opcode: $opcode, SubOpcode: $subOpcode, Data Length: ${data.remaining()}")

                when (data.remaining()) {
                    randomBytesExactlyOnePacket.remaining() -> {
                        assertByteBufferEquals(randomBytesExactlyOnePacket, data)
                        println("randomBytesExactlyOnePacket valid")
                    }
                    randomBytes.remaining() -> {
                        assertByteBufferEquals(randomBytes, data)
                        println("randomBytes valid")
                    }
                    randomBytesBig.remaining() -> {
                        assertByteBufferEquals(randomBytesBig, data)
                        println("randomBytesBig valid")
                    }
                    else -> println("Unknown data size received")
                }
            }
        )

        // Create and start the responder session
        val responderSession = SyncSocketSession("", responderKeyPair,
            responderInput,
            responderOutput,
            onClose = { session ->
                println("Responder session closed")
            },
            onHandshakeComplete = { session ->
                println("Responder handshake complete")
                handshakeLatch.countDown()  // Handshake complete for responder
            },
            onData = { session, opcode, subOpcode, data ->
                println("Responder received: Opcode $opcode, SubOpcode $subOpcode, Data Length: ${data.remaining()}")

                when (data.remaining()) {
                    randomBytesExactlyOnePacket.remaining() -> {
                        assertByteBufferEquals(randomBytesExactlyOnePacket, data)
                        println("randomBytesExactlyOnePacket valid")
                    }
                    randomBytes.remaining() -> {
                        assertByteBufferEquals(randomBytes, data)
                        println("randomBytes valid")
                    }
                    randomBytesBig.remaining() -> {
                        assertByteBufferEquals(randomBytesBig, data)
                        println("randomBytesBig valid")
                    }
                    else -> println("Unknown data size received")
                }
            }
        )

        initiatorSession.startAsInitiator(responderSession.localPublicKey)
        responderSession.startAsResponder()

        initiatorSession.authorizable = Authorized()
        responderSession.authorizable = Authorized()

        handshakeLatch.await(10, TimeUnit.SECONDS)

        // Simulate initiator sending a PING and responder replying with PONG
        initiatorSession.send(SyncSocketSession.Opcode.PING.value)
        responderSession.send(SyncSocketSession.Opcode.PONG.value)

        // Test data transfer
        responderSession.send(SyncSocketSession.Opcode.DATA.value, 0u, randomBytesExactlyOnePacket)
        initiatorSession.send(SyncSocketSession.Opcode.DATA.value, 1u, randomBytes)

        // Send large data to test stream handling
        val start = System.currentTimeMillis()
        responderSession.send(SyncSocketSession.Opcode.DATA.value, 0u, randomBytesBig)
        println("Sent 10MB in ${System.currentTimeMillis() - start}ms")

        // Wait for a brief period to simulate delay and allow communication
        sleep(1000)

        // Stop both sessions after the test
        initiatorSession.stop()
        responderSession.stop()
    }

    private fun generateRandomByteArray(size: Int): ByteBuffer {
        val random = Random()
        return ByteBuffer.wrap(ByteArray(size).apply { random.nextBytes(this) })
    }

    private fun assertSubArrayEquals(msg: String, expected: ByteArray, actual: ByteArray) {
        for (index in expected.indices) assertEquals("$msg[$index]", expected[index], actual[index])
    }

    private fun assertByteBufferEquals(expected: ByteBuffer, actual: ByteBuffer) {
        if (expected.remaining() != actual.remaining())
            throw Exception("ByteBuffers have a different length")

        for (i in 0 until expected.remaining()) {
            if (expected.array()[expected.position() + i] != actual.array()[actual.position() + i])
                throw Exception("Byte mismatch at index ${i}")
        }
    }
}