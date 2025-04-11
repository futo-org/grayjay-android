package com.futo.platformplayer

import com.futo.platformplayer.noise.protocol.Noise
import com.futo.platformplayer.sync.internal.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class SyncServerTests {

    //private val relayHost = "relay.grayjay.app"
    //private val relayKey = "xGbHRzDOvE6plRbQaFgSen82eijF+gxS0yeUaeEErkw="
    private val relayKey = "XlUaSpIlRaCg0TGzZ7JYmPupgUHDqTZXUUBco2K7ejw="
    private val relayHost = "192.168.1.175"
    private val relayPort = 9000

    /** Creates a client connected to the live relay server. */
    private suspend fun createClient(
        onHandshakeComplete: ((SyncSocketSession) -> Unit)? = null,
        onData: ((SyncSocketSession, UByte, UByte, ByteBuffer) -> Unit)? = null,
        onNewChannel: ((SyncSocketSession, ChannelRelayed) -> Unit)? = null,
        isHandshakeAllowed: ((SyncSocketSession, String, String?) -> Boolean)? = null
    ): SyncSocketSession = withContext(Dispatchers.IO) {
        val p = Noise.createDH("25519")
        p.generateKeyPair()
        val socket = Socket(relayHost, relayPort)
        val inputStream = LittleEndianDataInputStream(socket.getInputStream())
        val outputStream = LittleEndianDataOutputStream(socket.getOutputStream())
        val tcs = CompletableDeferred<Boolean>()
        val socketSession = SyncSocketSession(
            relayHost,
            p,
            inputStream,
            outputStream,
            onClose = { socket.close() },
            onHandshakeComplete = { s ->
                onHandshakeComplete?.invoke(s)
                tcs.complete(true)
            },
            onData = onData ?: { _, _, _, _ -> },
            onNewChannel = onNewChannel ?: { _, _ -> },
            isHandshakeAllowed = isHandshakeAllowed ?: { _, _, _ -> true }
        )
        socketSession.authorizable = AlwaysAuthorized()
        socketSession.startAsInitiator(relayKey)
        withTimeout(5000.milliseconds) { tcs.await() }
        return@withContext socketSession
    }

    @Test
    fun multipleClientsHandshake_Success() = runBlocking {
        val client1 = createClient()
        val client2 = createClient()
        assertNotNull(client1.remotePublicKey, "Client 1 handshake failed")
        assertNotNull(client2.remotePublicKey, "Client 2 handshake failed")
        client1.stop()
        client2.stop()
    }

    @Test
    fun publishAndRequestConnectionInfo_Authorized_Success() = runBlocking {
        val clientA = createClient()
        val clientB = createClient()
        val clientC = createClient()
        clientA.publishConnectionInformation(arrayOf(clientB.localPublicKey), 12345, true, true, true, true)
        delay(100.milliseconds)
        val infoB = clientB.requestConnectionInfo(clientA.localPublicKey)
        val infoC = clientC.requestConnectionInfo(clientA.localPublicKey)
        assertNotNull("Client B should receive connection info", infoB)
        assertEquals(12345.toUShort(), infoB!!.port)
        assertNull("Client C should not receive connection info (unauthorized)", infoC)
        clientA.stop()
        clientB.stop()
        clientC.stop()
    }

    @Test
    fun relayedTransport_Bidirectional_Success() = runBlocking {
        val tcsA = CompletableDeferred<ChannelRelayed>()
        val tcsB = CompletableDeferred<ChannelRelayed>()
        val clientA = createClient(onNewChannel = { _, c -> tcsA.complete(c) })
        val clientB = createClient(onNewChannel = { _, c -> tcsB.complete(c) })
        val channelTask = async { clientA.startRelayedChannel(clientB.localPublicKey) }
        val channelA = withTimeout(5000.milliseconds) { tcsA.await() }
        channelA.authorizable = AlwaysAuthorized()
        val channelB = withTimeout(5000.milliseconds) { tcsB.await() }
        channelB.authorizable = AlwaysAuthorized()
        channelTask.await()

        val tcsDataB = CompletableDeferred<ByteArray>()
        channelB.setDataHandler { _, _, o, so, d ->
            val b = ByteArray(d.remaining())
            d.get(b)
            if (o == Opcode.DATA.value && so == 0u.toUByte()) tcsDataB.complete(b)
        }
        channelA.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(byteArrayOf(1, 2, 3)))

        val tcsDataA = CompletableDeferred<ByteArray>()
        channelA.setDataHandler { _, _, o, so, d ->
            val b = ByteArray(d.remaining())
            d.get(b)
            if (o == Opcode.DATA.value && so == 0u.toUByte()) tcsDataA.complete(b)
        }
        channelB.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(byteArrayOf(4, 5, 6)))

        val receivedB = withTimeout(5000.milliseconds) { tcsDataB.await() }
        val receivedA = withTimeout(5000.milliseconds) { tcsDataA.await() }
        assertArrayEquals(byteArrayOf(1, 2, 3), receivedB)
        assertArrayEquals(byteArrayOf(4, 5, 6), receivedA)
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun relayedTransport_MaximumMessageSize_Success() = runBlocking {
        val MAX_DATA_PER_PACKET = SyncSocketSession.MAXIMUM_PACKET_SIZE - SyncSocketSession.HEADER_SIZE - 8 - 16 - 16
        val maxSizeData = ByteArray(MAX_DATA_PER_PACKET).apply { Random.nextBytes(this) }
        val tcsA = CompletableDeferred<ChannelRelayed>()
        val tcsB = CompletableDeferred<ChannelRelayed>()
        val clientA = createClient(onNewChannel = { _, c -> tcsA.complete(c) })
        val clientB = createClient(onNewChannel = { _, c -> tcsB.complete(c) })
        val channelTask = async { clientA.startRelayedChannel(clientB.localPublicKey) }
        val channelA = withTimeout(5000.milliseconds) { tcsA.await() }
        channelA.authorizable = AlwaysAuthorized()
        val channelB = withTimeout(5000.milliseconds) { tcsB.await() }
        channelB.authorizable = AlwaysAuthorized()
        channelTask.await()

        val tcsDataB = CompletableDeferred<ByteArray>()
        channelB.setDataHandler { _, _, o, so, d ->
            val b = ByteArray(d.remaining())
            d.get(b)
            if (o == Opcode.DATA.value && so == 0u.toUByte()) tcsDataB.complete(b)
        }
        channelA.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(maxSizeData))
        val receivedData = withTimeout(5000.milliseconds) { tcsDataB.await() }
        assertArrayEquals(maxSizeData, receivedData)
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun publishAndGetRecord_Success() = runBlocking {
        val clientA = createClient()
        val clientB = createClient()
        val clientC = createClient()
        val data = byteArrayOf(1, 2, 3)
        val success = clientA.publishRecords(listOf(clientB.localPublicKey), "testKey", data)
        val recordB = clientB.getRecord(clientA.localPublicKey, "testKey")
        val recordC = clientC.getRecord(clientA.localPublicKey, "testKey")
        assertTrue(success)
        assertNotNull(recordB)
        assertArrayEquals(data, recordB!!.first)
        assertNull("Unauthorized client should not access record", recordC)
        clientA.stop()
        clientB.stop()
        clientC.stop()
    }

    @Test
    fun getNonExistentRecord_ReturnsNull() = runBlocking {
        val clientA = createClient()
        val clientB = createClient()
        val record = clientB.getRecord(clientA.localPublicKey, "nonExistentKey")
        assertNull("Getting non-existent record should return null", record)
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun updateRecord_TimestampUpdated() = runBlocking {
        val clientA = createClient()
        val clientB = createClient()
        val key = "updateKey"
        val data1 = byteArrayOf(1)
        val data2 = byteArrayOf(2)
        clientA.publishRecords(listOf(clientB.localPublicKey), key, data1)
        val record1 = clientB.getRecord(clientA.localPublicKey, key)
        delay(1000.milliseconds)
        clientA.publishRecords(listOf(clientB.localPublicKey), key, data2)
        val record2 = clientB.getRecord(clientA.localPublicKey, key)
        assertNotNull(record1)
        assertNotNull(record2)
        assertTrue(record2!!.second > record1!!.second)
        assertArrayEquals(data2, record2.first)
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun deleteRecord_Success() = runBlocking {
        val clientA = createClient()
        val clientB = createClient()
        val data = byteArrayOf(1, 2, 3)
        clientA.publishRecords(listOf(clientB.localPublicKey), "toDelete", data)
        val success = clientB.deleteRecords(clientA.localPublicKey, clientB.localPublicKey, listOf("toDelete"))
        val record = clientB.getRecord(clientA.localPublicKey, "toDelete")
        assertTrue(success)
        assertNull(record)
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun listRecordKeys_Success() = runBlocking {
        val clientA = createClient()
        val clientB = createClient()
        val keys = arrayOf("key1", "key2", "key3")
        keys.forEach { key ->
            clientA.publishRecords(listOf(clientB.localPublicKey), key, byteArrayOf(1))
        }
        val listedKeys = clientB.listRecordKeys(clientA.localPublicKey, clientB.localPublicKey)
        assertArrayEquals(keys, listedKeys.map { it.first }.toTypedArray())
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun singleLargeMessageViaRelayedChannel_Success() = runBlocking {
        val largeData = ByteArray(100000).apply { Random.nextBytes(this) }
        val tcsA = CompletableDeferred<ChannelRelayed>()
        val tcsB = CompletableDeferred<ChannelRelayed>()
        val clientA = createClient(onNewChannel = { _, c -> tcsA.complete(c) })
        val clientB = createClient(onNewChannel = { _, c -> tcsB.complete(c) })
        val channelTask = async { clientA.startRelayedChannel(clientB.localPublicKey) }
        val channelA = withTimeout(5000.milliseconds) { tcsA.await() }
        channelA.authorizable = AlwaysAuthorized()
        val channelB = withTimeout(5000.milliseconds) { tcsB.await() }
        channelB.authorizable = AlwaysAuthorized()
        channelTask.await()

        val tcsDataB = CompletableDeferred<ByteArray>()
        channelB.setDataHandler { _, _, o, so, d ->
            val b = ByteArray(d.remaining())
            d.get(b)
            if (o == Opcode.DATA.value && so == 0u.toUByte()) tcsDataB.complete(b)
        }
        channelA.send(Opcode.DATA.value, 0u, ByteBuffer.wrap(largeData))
        val receivedData = withTimeout(10000.milliseconds) { tcsDataB.await() }
        assertArrayEquals(largeData, receivedData)
        clientA.stop()
        clientB.stop()
    }

    @Test
    fun publishAndGetLargeRecord_Success() = runBlocking {
        val largeData = ByteArray(1000000).apply { Random.nextBytes(this) }
        val clientA = createClient()
        val clientB = createClient()
        val success = clientA.publishRecords(listOf(clientB.localPublicKey), "largeRecord", largeData)
        val record = clientB.getRecord(clientA.localPublicKey, "largeRecord")
        assertTrue(success)
        assertNotNull(record)
        assertArrayEquals(largeData, record!!.first)
        clientA.stop()
        clientB.stop()
    }
}

class AlwaysAuthorized : IAuthorizable {
    override val isAuthorized: Boolean get() = true
}