package com.futo.platformplayer.mdns

import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.*
import java.net.*
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MDNSListener {
    companion object {
        private val TAG = "MDNSListener"
        const val MulticastPort = 5353
        val MulticastAddressIPv4: InetAddress = InetAddress.getByName("224.0.0.251")
        val MulticastAddressIPv6: InetAddress = InetAddress.getByName("FF02::FB")
        val MdnsEndpointIPv6: InetSocketAddress = InetSocketAddress(MulticastAddressIPv6, MulticastPort)
        val MdnsEndpointIPv4: InetSocketAddress = InetSocketAddress(MulticastAddressIPv4, MulticastPort)
    }

    private val _lockObject = ReentrantLock()
    private var _receiver4: MulticastSocket? = null
    private var _receiver6: MulticastSocket? = null
    private val _senders = mutableListOf<MulticastSocket>()
    private val _nicMonitor = NICMonitor()
    private val _serviceRecordAggregator = ServiceRecordAggregator()
    private var _started = false
    private var _threadReceiver4: Thread? = null
    private var _threadReceiver6: Thread? = null
    private var _scope: CoroutineScope? = null

    var onPacket: ((DnsPacket) -> Unit)? = null
    var onServicesUpdated: ((List<DnsService>) -> Unit)? = null

    private val _recordLockObject = ReentrantLock()
    private val _recordsA = mutableListOf<Pair<DnsResourceRecord, ARecord>>()
    private val _recordsAAAA = mutableListOf<Pair<DnsResourceRecord, AAAARecord>>()
    private val _recordsPTR = mutableListOf<Pair<DnsResourceRecord, PTRRecord>>()
    private val _recordsTXT = mutableListOf<Pair<DnsResourceRecord, TXTRecord>>()
    private val _recordsSRV = mutableListOf<Pair<DnsResourceRecord, SRVRecord>>()
    private val _services = mutableListOf<BroadcastService>()

    init {
        _nicMonitor.added = { onNicsAdded(it) }
        _nicMonitor.removed = { onNicsRemoved(it) }
        _serviceRecordAggregator.onServicesUpdated = { onServicesUpdated?.invoke(it) }
    }

    fun start() {
        if (_started) {
            Logger.i(TAG, "Already started.")
            return
        }
        _started = true

        _scope = CoroutineScope(Dispatchers.IO);

        Logger.i(TAG, "Starting")
        _lockObject.withLock {
            val receiver4 = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), MulticastPort))
            }
            _receiver4 = receiver4

            val receiver6 = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("::"), MulticastPort))
            }
            _receiver6 = receiver6

            _nicMonitor.start()
            _serviceRecordAggregator.start()
            onNicsAdded(_nicMonitor.current)

            _threadReceiver4 = Thread {
                receiveLoop(receiver4)
            }.apply { start() }

            _threadReceiver6 = Thread {
                receiveLoop(receiver6)
            }.apply { start() }
        }
    }

    fun queryServices(names: Array<String>) {
        if (names.isEmpty()) throw IllegalArgumentException("At least one name must be specified.")

        val writer = DnsWriter()
        writer.writePacket(
            DnsPacketHeader(
                identifier = 0u,
                queryResponse = QueryResponse.Query.value.toInt(),
                opcode = DnsOpcode.StandardQuery.value.toInt(),
                truncated = false,
                nonAuthenticatedData = false,
                recursionDesired = false,
                answerAuthenticated = false,
                authoritativeAnswer = false,
                recursionAvailable = false,
                responseCode = DnsResponseCode.NoError
            ),
            questionCount = names.size,
            questionWriter = { w, i ->
                w.write(
                    DnsQuestion(
                        name = names[i],
                        type = QuestionType.PTR.value.toInt(),
                        clazz = QuestionClass.IN.value.toInt(),
                        queryUnicast = false
                    )
                )
            }
        )

        send(writer.toByteArray())
    }

    private fun send(data: ByteArray) {
        _lockObject.withLock {
            for (sender in _senders) {
                try {
                    val endPoint = if (sender.localAddress is Inet4Address) MdnsEndpointIPv4 else MdnsEndpointIPv6
                    sender.send(DatagramPacket(data, data.size, endPoint))
                } catch (e: Exception) {
                    Logger.i(TAG, "Failed to send on ${sender.localSocketAddress}: ${e.message}.")
                }
            }
        }
    }

    fun queryAllQuestions(names: Array<String>) {
        if (names.isEmpty()) throw IllegalArgumentException("At least one name must be specified.")

        val questions = names.flatMap { _serviceRecordAggregator.getAllQuestions(it) }
        questions.groupBy { it.name }.forEach { (_, questionsForHost) ->
            val writer = DnsWriter()
            writer.writePacket(
                DnsPacketHeader(
                    identifier = 0u,
                    queryResponse = QueryResponse.Query.value.toInt(),
                    opcode = DnsOpcode.StandardQuery.value.toInt(),
                    truncated = false,
                    nonAuthenticatedData = false,
                    recursionDesired = false,
                    answerAuthenticated = false,
                    authoritativeAnswer = false,
                    recursionAvailable = false,
                    responseCode = DnsResponseCode.NoError
                ),
                questionCount = questionsForHost.size,
                questionWriter = { w, i -> w.write(questionsForHost[i]) }
            )
            send(writer.toByteArray())
        }
    }

    private fun onNicsAdded(nics: List<NetworkInterface>) {
        _lockObject.withLock {
            if (!_started) return

            val addresses = nics.flatMap { nic ->
                nic.interfaceAddresses.map { it.address }
                    .filter { it is Inet4Address || (it is Inet6Address && it.isLinkLocalAddress) }
            }

            addresses.forEach { address ->
                Logger.i(TAG, "New address discovered $address")

                try {
                    when (address) {
                        is Inet4Address -> {
                            _receiver4?.let { receiver4 ->
                                //receiver4.setOption(StandardSocketOptions.IP_MULTICAST_IF, NetworkInterface.getByInetAddress(address))
                                receiver4.joinGroup(InetSocketAddress(MulticastAddressIPv4, MulticastPort), NetworkInterface.getByInetAddress(address))
                            }

                            val sender = MulticastSocket(null).apply {
                                reuseAddress = true
                                bind(InetSocketAddress(address, MulticastPort))
                                joinGroup(InetSocketAddress(MulticastAddressIPv4, MulticastPort), NetworkInterface.getByInetAddress(address))
                            }
                            _senders.add(sender)
                        }

                        is Inet6Address -> {
                            _receiver6?.let { receiver6 ->
                                //receiver6.setOption(StandardSocketOptions.IP_MULTICAST_IF, NetworkInterface.getByInetAddress(address))
                                receiver6.joinGroup(InetSocketAddress(MulticastAddressIPv6, MulticastPort), NetworkInterface.getByInetAddress(address))
                            }

                            val sender = MulticastSocket(null).apply {
                                reuseAddress = true
                                bind(InetSocketAddress(address, MulticastPort))
                                joinGroup(InetSocketAddress(MulticastAddressIPv6, MulticastPort), NetworkInterface.getByInetAddress(address))
                            }
                            _senders.add(sender)
                        }

                        else -> throw UnsupportedOperationException("Address type ${address.javaClass.name} is not supported.")
                    }
                } catch (e: Exception) {
                    Logger.i(TAG, "Exception occurred when processing added address $address: ${e.message}.")
                    // Close the socket if there was an error
                    (_senders.lastOrNull() as? MulticastSocket)?.close()
                }
            }
        }

        if (nics.isNotEmpty()) {
            try {
                updateBroadcastRecords()
                broadcastRecords()
            } catch (e: Exception) {
                Logger.i(TAG, "Exception occurred when broadcasting records: ${e.message}.")
            }
        }
    }

    private fun onNicsRemoved(nics: List<NetworkInterface>) {
        _lockObject.withLock {
            if (!_started) return
            //TODO: Cleanup?
        }

        if (nics.isNotEmpty()) {
            try {
                updateBroadcastRecords()
                broadcastRecords()
            } catch (e: Exception) {
                Logger.e(TAG, "Exception occurred when broadcasting records", e)
            }
        }
    }

    private fun receiveLoop(client: DatagramSocket) {
        Logger.i(TAG, "Started receive loop")

        val buffer = ByteArray(8972)
        val packet = DatagramPacket(buffer, buffer.size)
        while (_started) {
            try {
                client.receive(packet)
                handleResult(packet)
            } catch (e: Exception) {
                Logger.e(TAG, "An exception occurred while handling UDP result:", e)
            }
        }

        Logger.i(TAG, "Stopped receive loop")
    }

    fun broadcastService(
        deviceName: String,
        serviceName: String,
        port: UShort,
        ttl: UInt = 120u,
        weight: UShort = 0u,
        priority: UShort = 0u,
        texts: List<String>? = null
    ) {
        _recordLockObject.withLock {
            _services.add(
                BroadcastService(
                    deviceName = deviceName,
                    port = port,
                    priority = priority,
                    serviceName = serviceName,
                    texts = texts,
                    ttl = ttl,
                    weight = weight
                )
            )
        }

        updateBroadcastRecords()
        broadcastRecords()
    }

    private fun updateBroadcastRecords() {
        _recordLockObject.withLock {
            _recordsSRV.clear()
            _recordsPTR.clear()
            _recordsA.clear()
            _recordsAAAA.clear()
            _recordsTXT.clear()

            _services.forEach { service ->
                val id = UUID.randomUUID().toString()
                val deviceDomainName = "${service.deviceName}.${service.serviceName}"
                val addressName = "$id.local"

                _recordsSRV.add(
                    DnsResourceRecord(
                        clazz = ResourceRecordClass.IN.value.toInt(),
                        type = ResourceRecordType.SRV.value.toInt(),
                        timeToLive = service.ttl,
                        name = deviceDomainName,
                        cacheFlush = false
                    ) to SRVRecord(
                        target = addressName,
                        port = service.port,
                        priority = service.priority,
                        weight = service.weight
                    )
                )

                _recordsPTR.add(
                    DnsResourceRecord(
                        clazz = ResourceRecordClass.IN.value.toInt(),
                        type = ResourceRecordType.PTR.value.toInt(),
                        timeToLive = service.ttl,
                        name = service.serviceName,
                        cacheFlush = false
                    ) to PTRRecord(
                        domainName = deviceDomainName
                    )
                )

                val addresses = _nicMonitor.current.flatMap { nic ->
                    nic.interfaceAddresses.map { it.address }
                }

                addresses.forEach { address ->
                    when (address) {
                        is Inet4Address -> _recordsA.add(
                            DnsResourceRecord(
                                clazz = ResourceRecordClass.IN.value.toInt(),
                                type = ResourceRecordType.A.value.toInt(),
                                timeToLive = service.ttl,
                                name = addressName,
                                cacheFlush = false
                            ) to ARecord(
                                address = address
                            )
                        )

                        is Inet6Address -> _recordsAAAA.add(
                            DnsResourceRecord(
                                clazz = ResourceRecordClass.IN.value.toInt(),
                                type = ResourceRecordType.AAAA.value.toInt(),
                                timeToLive = service.ttl,
                                name = addressName,
                                cacheFlush = false
                            ) to AAAARecord(
                                address = address
                            )
                        )

                        else -> Logger.i(TAG, "Invalid address type: $address.")
                    }
                }

                if (service.texts != null) {
                    _recordsTXT.add(
                        DnsResourceRecord(
                            clazz = ResourceRecordClass.IN.value.toInt(),
                            type = ResourceRecordType.TXT.value.toInt(),
                            timeToLive = service.ttl,
                            name = deviceDomainName,
                            cacheFlush = false
                        ) to TXTRecord(
                            texts = service.texts
                        )
                    )
                }
            }
        }
    }

    private fun broadcastRecords(questions: List<DnsQuestion>? = null) {
        val writer = DnsWriter()
        _recordLockObject.withLock {
            val recordsA: List<Pair<DnsResourceRecord, ARecord>>
            val recordsAAAA: List<Pair<DnsResourceRecord, AAAARecord>>
            val recordsPTR: List<Pair<DnsResourceRecord, PTRRecord>>
            val recordsTXT: List<Pair<DnsResourceRecord, TXTRecord>>
            val recordsSRV: List<Pair<DnsResourceRecord, SRVRecord>>

            if (questions != null) {
                recordsA = _recordsA.filter { r -> questions.any { q -> q.name == r.first.name && q.clazz == r.first.clazz && q.type == r.first.type } }
                recordsAAAA = _recordsAAAA.filter { r -> questions.any { q -> q.name == r.first.name && q.clazz == r.first.clazz && q.type == r.first.type } }
                recordsPTR = _recordsPTR.filter { r -> questions.any { q -> q.name == r.first.name && q.clazz == r.first.clazz && q.type == r.first.type } }
                recordsSRV = _recordsSRV.filter { r -> questions.any { q -> q.name == r.first.name && q.clazz == r.first.clazz && q.type == r.first.type } }
                recordsTXT = _recordsTXT.filter { r -> questions.any { q -> q.name == r.first.name && q.clazz == r.first.clazz && q.type == r.first.type } }
            } else {
                recordsA = _recordsA
                recordsAAAA = _recordsAAAA
                recordsPTR = _recordsPTR
                recordsSRV = _recordsSRV
                recordsTXT = _recordsTXT
            }

            val answerCount = recordsA.size + recordsAAAA.size + recordsPTR.size + recordsSRV.size + recordsTXT.size
            if (answerCount < 1) return

            val txtOffset = recordsA.size + recordsAAAA.size + recordsPTR.size + recordsSRV.size
            val srvOffset = recordsA.size + recordsAAAA.size + recordsPTR.size
            val ptrOffset = recordsA.size + recordsAAAA.size
            val aaaaOffset = recordsA.size

            writer.writePacket(
                DnsPacketHeader(
                    identifier = 0u,
                    queryResponse = QueryResponse.Response.value.toInt(),
                    opcode = DnsOpcode.StandardQuery.value.toInt(),
                    truncated = false,
                    nonAuthenticatedData = false,
                    recursionDesired = false,
                    answerAuthenticated = false,
                    authoritativeAnswer = true,
                    recursionAvailable = false,
                    responseCode = DnsResponseCode.NoError
                ),
                answerCount = answerCount,
                answerWriter = { w, i ->
                    when {
                        i >= txtOffset -> {
                            val record = recordsTXT[i - txtOffset]
                            w.write(record.first) { it.write(record.second) }
                        }

                        i >= srvOffset -> {
                            val record = recordsSRV[i - srvOffset]
                            w.write(record.first) { it.write(record.second) }
                        }

                        i >= ptrOffset -> {
                            val record = recordsPTR[i - ptrOffset]
                            w.write(record.first) { it.write(record.second) }
                        }

                        i >= aaaaOffset -> {
                            val record = recordsAAAA[i - aaaaOffset]
                            w.write(record.first) { it.write(record.second) }
                        }

                        else -> {
                            val record = recordsA[i]
                            w.write(record.first) { it.write(record.second) }
                        }
                    }
                }
            )
        }

        send(writer.toByteArray())
    }

    private fun handleResult(result: DatagramPacket) {
        try {
            val packet = DnsPacket.parse(result.data)
            if (packet.questions.isNotEmpty()) {
                _scope?.launch(Dispatchers.IO) {
                    try {
                        broadcastRecords(packet.questions)
                    } catch (e: Throwable) {
                        Logger.i(TAG, "Broadcasting records failed", e)
                    }
                }

            }
            _serviceRecordAggregator.add(packet)
            onPacket?.invoke(packet)
        } catch (e: Exception) {
            Logger.v(TAG, "Failed to handle packet: ${Base64.getEncoder().encodeToString(result.data.slice(IntRange(0, result.length - 1)).toByteArray())}", e)
        }
    }

    fun stop() {
        _lockObject.withLock {
            _started = false

            _scope?.cancel()
            _scope = null

            _nicMonitor.stop()
            _serviceRecordAggregator.stop()

            _receiver4?.close()
            _receiver4 = null

            _receiver6?.close()
            _receiver6 = null

            _senders.forEach { it.close() }
            _senders.clear()
        }

        _threadReceiver4?.join()
        _threadReceiver4 = null

        _threadReceiver6?.join()
        _threadReceiver6 = null
    }
}
