package com.futo.platformplayer.mdns

import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.Date

data class DnsService(
    var name: String,
    var target: String,
    var port: UShort,
    val addresses: MutableList<InetAddress> = mutableListOf(),
    val pointers: MutableList<String> = mutableListOf(),
    val texts: MutableList<String> = mutableListOf()
)

data class CachedDnsAddressRecord(
    val expirationTime: Date,
    val address: InetAddress
)

data class CachedDnsTxtRecord(
    val expirationTime: Date,
    val texts: List<String>
)

data class CachedDnsPtrRecord(
    val expirationTime: Date,
    val target: String
)

data class CachedDnsSrvRecord(
    val expirationTime: Date,
    val service: SRVRecord
)

class ServiceRecordAggregator {
    private val _lockObject = Any()
    private val _cachedAddressRecords = mutableMapOf<String, MutableList<CachedDnsAddressRecord>>()
    private val _cachedTxtRecords = mutableMapOf<String, CachedDnsTxtRecord>()
    private val _cachedPtrRecords = mutableMapOf<String, MutableList<CachedDnsPtrRecord>>()
    private val _cachedSrvRecords = mutableMapOf<String, CachedDnsSrvRecord>()
    private val _currentServices = mutableListOf<DnsService>()
    private var _cts: Job? = null

    var onServicesUpdated: ((List<DnsService>) -> Unit)? = null

    fun start() {
        synchronized(_lockObject) {
            if (_cts != null) throw Exception("Already started.")

            _cts = CoroutineScope(Dispatchers.Default).launch {
                try {
                    while (isActive) {
                        val now = Date()
                        synchronized(_currentServices) {
                            _cachedAddressRecords.forEach { it.value.removeAll { record -> now.after(record.expirationTime) } }
                            _cachedTxtRecords.entries.removeIf { now.after(it.value.expirationTime) }
                            _cachedSrvRecords.entries.removeIf { now.after(it.value.expirationTime) }
                            _cachedPtrRecords.forEach { it.value.removeAll { record -> now.after(record.expirationTime) } }

                            val newServices = getCurrentServices()
                            _currentServices.clear()
                            _currentServices.addAll(newServices)
                        }

                        onServicesUpdated?.invoke(_currentServices.toList())
                        delay(5000)
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Unexpected failure in MDNS loop", e)
                }
            }
        }
    }

    fun stop() {
        synchronized(_lockObject) {
            _cts?.cancel()
            _cts = null
        }
    }

    fun add(packet: DnsPacket) {
        val currentServices: List<DnsService>
        val dnsResourceRecords = packet.answers + packet.additionals + packet.authorities
        val txtRecords = dnsResourceRecords.filter { it.type == ResourceRecordType.TXT.value.toInt() }.map { it to it.getDataReader().readTXTRecord() }
        val aRecords = dnsResourceRecords.filter { it.type == ResourceRecordType.A.value.toInt() }.map { it to it.getDataReader().readARecord() }
        val aaaaRecords = dnsResourceRecords.filter { it.type == ResourceRecordType.AAAA.value.toInt() }.map { it to it.getDataReader().readAAAARecord() }
        val srvRecords = dnsResourceRecords.filter { it.type == ResourceRecordType.SRV.value.toInt() }.map { it to it.getDataReader().readSRVRecord() }
        val ptrRecords = dnsResourceRecords.filter { it.type == ResourceRecordType.PTR.value.toInt() }.map { it to it.getDataReader().readPTRRecord() }

        /*val builder = StringBuilder()
        builder.appendLine("Received records:")
        srvRecords.forEach { builder.appendLine("SRV ${it.first.name} ${it.first.type} ${it.first.clazz} TTL ${it.first.timeToLive}: (Port: ${it.second.port}, Target: ${it.second.target}, Priority: ${it.second.priority}, Weight: ${it.second.weight})") }
        ptrRecords.forEach { builder.appendLine("PTR ${it.first.name} ${it.first.type} ${it.first.clazz} TTL ${it.first.timeToLive}: ${it.second.domainName}") }
        txtRecords.forEach { builder.appendLine("TXT ${it.first.name} ${it.first.type} ${it.first.clazz} TTL ${it.first.timeToLive}: ${it.second.texts.joinToString(", ")}") }
        aRecords.forEach { builder.appendLine("A ${it.first.name} ${it.first.type} ${it.first.clazz} TTL ${it.first.timeToLive}: ${it.second.address}") }
        aaaaRecords.forEach { builder.appendLine("AAAA ${it.first.name} ${it.first.type} ${it.first.clazz} TTL ${it.first.timeToLive}: ${it.second.address}") }
        Logger.i(TAG, "$builder")*/

        synchronized(this._currentServices) {
            ptrRecords.forEach { record ->
                val cachedPtrRecord = _cachedPtrRecords.getOrPut(record.first.name) { mutableListOf() }
                val newPtrRecord = CachedDnsPtrRecord(Date(System.currentTimeMillis() + record.first.timeToLive.toLong() * 1000L), record.second.domainName)
                cachedPtrRecord.replaceOrAdd(newPtrRecord) { it.target == record.second.domainName }
            }

            aRecords.forEach { aRecord ->
                val cachedARecord = _cachedAddressRecords.getOrPut(aRecord.first.name) { mutableListOf() }
                val newARecord = CachedDnsAddressRecord(Date(System.currentTimeMillis() + aRecord.first.timeToLive.toLong() * 1000L), aRecord.second.address)
                cachedARecord.replaceOrAdd(newARecord) { it.address == newARecord.address }
            }

            aaaaRecords.forEach { aaaaRecord ->
                val cachedAaaaRecord = _cachedAddressRecords.getOrPut(aaaaRecord.first.name) { mutableListOf() }
                val newAaaaRecord = CachedDnsAddressRecord(Date(System.currentTimeMillis() + aaaaRecord.first.timeToLive.toLong() * 1000L), aaaaRecord.second.address)
                cachedAaaaRecord.replaceOrAdd(newAaaaRecord) { it.address == newAaaaRecord.address }
            }

            txtRecords.forEach { txtRecord ->
                _cachedTxtRecords[txtRecord.first.name] = CachedDnsTxtRecord(Date(System.currentTimeMillis() + txtRecord.first.timeToLive.toLong() * 1000L), txtRecord.second.texts)
            }

            srvRecords.forEach { srvRecord ->
                _cachedSrvRecords[srvRecord.first.name] = CachedDnsSrvRecord(Date(System.currentTimeMillis() + srvRecord.first.timeToLive.toLong() * 1000L), srvRecord.second)
            }

            currentServices = getCurrentServices()
            this._currentServices.clear()
            this._currentServices.addAll(currentServices)
        }

        onServicesUpdated?.invoke(currentServices)
    }

    fun getAllQuestions(serviceName: String): List<DnsQuestion> {
        val questions = mutableListOf<DnsQuestion>()
        synchronized(_currentServices) {
            val servicePtrRecords = _cachedPtrRecords[serviceName] ?: return emptyList()

            val ptrWithoutSrvRecord = servicePtrRecords.filterNot { _cachedSrvRecords.containsKey(it.target) }.map { it.target }
            questions.addAll(ptrWithoutSrvRecord.flatMap { s ->
                listOf(
                    DnsQuestion(
                        name = s,
                        type = QuestionType.SRV.value.toInt(),
                        clazz = QuestionClass.IN.value.toInt(),
                        queryUnicast = false
                    )
                )
            })

            val incompleteCurrentServices = _currentServices.filter { it.addresses.isEmpty() && it.name.endsWith(serviceName) }
            questions.addAll(incompleteCurrentServices.flatMap { s ->
                listOf(
                    DnsQuestion(
                        name = s.name,
                        type = QuestionType.TXT.value.toInt(),
                        clazz = QuestionClass.IN.value.toInt(),
                        queryUnicast = false
                    ),
                    DnsQuestion(
                        name = s.target,
                        type = QuestionType.A.value.toInt(),
                        clazz = QuestionClass.IN.value.toInt(),
                        queryUnicast = false
                    ),
                    DnsQuestion(
                        name = s.target,
                        type = QuestionType.AAAA.value.toInt(),
                        clazz = QuestionClass.IN.value.toInt(),
                        queryUnicast = false
                    )
                )
            })
        }
        return questions
    }

    private fun getCurrentServices(): MutableList<DnsService> {
        val currentServices = _cachedSrvRecords.map { (key, value) ->
            DnsService(
                name = key,
                target = value.service.target,
                port = value.service.port
            )
        }.toMutableList()

        currentServices.forEach { service ->
            _cachedAddressRecords[service.target]?.let {
                service.addresses.addAll(it.map { record -> record.address })
            }
        }

        currentServices.forEach { service ->
            service.pointers.addAll(_cachedPtrRecords.filter { it.value.any { ptr -> ptr.target == service.name } }.map { it.key })
        }

        currentServices.forEach { service ->
            _cachedTxtRecords[service.name]?.let {
                service.texts.addAll(it.texts)
            }
        }

        return currentServices
    }

    private inline fun <T> MutableList<T>.replaceOrAdd(newElement: T, predicate: (T) -> Boolean) {
        val index = indexOfFirst(predicate)
        if (index >= 0) {
            this[index] = newElement
        } else {
            add(newElement)
        }
    }

    private companion object {
        private const val TAG = "ServiceRecordAggregator"
    }
}
