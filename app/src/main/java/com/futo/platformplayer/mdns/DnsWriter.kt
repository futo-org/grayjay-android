package com.futo.platformplayer.mdns

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class DnsWriter {
    private val data = mutableListOf<Byte>()
    private val namePositions = mutableMapOf<String, Int>()

    fun toByteArray(): ByteArray = data.toByteArray()

    fun writePacket(
        header: DnsPacketHeader,
        questionCount: Int? = null, questionWriter: ((DnsWriter, Int) -> Unit)? = null,
        answerCount: Int? = null, answerWriter: ((DnsWriter, Int) -> Unit)? = null,
        authorityCount: Int? = null, authorityWriter: ((DnsWriter, Int) -> Unit)? = null,
        additionalsCount: Int? = null, additionalWriter: ((DnsWriter, Int) -> Unit)? = null
    ) {
        if (questionCount != null && questionWriter == null || questionCount == null && questionWriter != null)
            throw Exception("When question count is given, question writer should also be given.")
        if (answerCount != null && answerWriter == null || answerCount == null && answerWriter != null)
            throw Exception("When answer count is given, answer writer should also be given.")
        if (authorityCount != null && authorityWriter == null || authorityCount == null && authorityWriter != null)
            throw Exception("When authority count is given, authority writer should also be given.")
        if (additionalsCount != null && additionalWriter == null || additionalsCount == null && additionalWriter != null)
            throw Exception("When additionals count is given, additional writer should also be given.")

        writeHeader(header, questionCount ?: 0, answerCount ?: 0, authorityCount ?: 0, additionalsCount ?: 0)

        repeat(questionCount ?: 0) { questionWriter?.invoke(this, it) }
        repeat(answerCount ?: 0) { answerWriter?.invoke(this, it) }
        repeat(authorityCount ?: 0) { authorityWriter?.invoke(this, it) }
        repeat(additionalsCount ?: 0) { additionalWriter?.invoke(this, it) }
    }

    fun writeHeader(header: DnsPacketHeader, questionCount: Int, answerCount: Int, authorityCount: Int, additionalsCount: Int) {
        write(header.identifier)

        var flags: UShort = 0u
        flags = flags or ((header.queryResponse.toUInt() and 0xFFFFu) shl 15).toUShort()
        flags = flags or ((header.opcode.toUInt() and 0xFFFFu) shl 11).toUShort()
        flags = flags or ((if (header.authoritativeAnswer) 1u else 0u) shl 10).toUShort()
        flags = flags or ((if (header.truncated) 1u else 0u) shl 9).toUShort()
        flags = flags or ((if (header.recursionDesired) 1u else 0u) shl 8).toUShort()
        flags = flags or ((if (header.recursionAvailable) 1u else 0u) shl 7).toUShort()
        flags = flags or ((if (header.answerAuthenticated) 1u else 0u) shl 5).toUShort()
        flags = flags or ((if (header.nonAuthenticatedData) 1u else 0u) shl 4).toUShort()
        flags = flags or header.responseCode.value.toUShort()
        write(flags)

        write(questionCount.toUShort())
        write(answerCount.toUShort())
        write(authorityCount.toUShort())
        write(additionalsCount.toUShort())
    }

    fun writeDomainName(name: String) {
        synchronized(namePositions) {
            val labels = name.split('.')
            for (label in labels) {
                val nameAtOffset = name.substring(name.indexOf(label))
                if (namePositions.containsKey(nameAtOffset)) {
                    val position = namePositions[nameAtOffset]!!
                    val pointer = (0b11000000_00000000 or position).toUShort()
                    write(pointer)
                    return
                }
                if (label.isNotEmpty()) {
                    val labelBytes = label.toByteArray(StandardCharsets.UTF_8)
                    val nameStartPos = data.size
                    write(labelBytes.size.toByte())
                    write(labelBytes)
                    namePositions[nameAtOffset] = nameStartPos
                }
            }
            write(0.toByte())  // End of domain name
        }
    }

    fun write(value: DnsResourceRecord, dataWriter: (DnsWriter) -> Unit) {
        writeDomainName(value.name)
        write(value.type.toUShort())
        val cls = ((if (value.cacheFlush) 1u else 0u) shl 15).toUShort() or value.clazz.toUShort()
        write(cls)
        write(value.timeToLive)

        val lengthOffset = data.size
        write(0.toUShort())
        dataWriter(this)
        val rdLength = data.size - lengthOffset - 2
        val rdLengthBytes = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(rdLength.toShort()).array()
        data[lengthOffset] = rdLengthBytes[0]
        data[lengthOffset + 1] = rdLengthBytes[1]
    }

    fun write(value: DnsQuestion) {
        writeDomainName(value.name)
        write(value.type.toUShort())
        write(((if (value.queryUnicast) 1u else 0u shl 15).toUShort() or value.clazz.toUShort()))
    }

    fun write(value: Double) {
        val bytes = ByteBuffer.allocate(Double.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putDouble(value).array()
        write(bytes)
    }

    fun write(value: Short) {
        val bytes = ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putShort(value).array()
        write(bytes)
    }

    fun write(value: Int) {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
        write(bytes)
    }

    fun write(value: Long) {
        val bytes = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array()
        write(bytes)
    }

    fun write(value: Float) {
        val bytes = ByteBuffer.allocate(Float.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()
        write(bytes)
    }

    fun write(value: Byte) {
        data.add(value)
    }

    fun write(value: ByteArray) {
        data.addAll(value.asIterable())
    }

    fun write(value: ByteArray, offset: Int, length: Int) {
        data.addAll(value.slice(offset until offset + length))
    }

    fun write(value: UShort) {
        val bytes = ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array()
        write(bytes)
    }

    fun write(value: UInt) {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array()
        write(bytes)
    }

    fun write(value: ULong) {
        val bytes = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value.toLong()).array()
        write(bytes)
    }

    fun write(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        write(bytes.size.toByte())
        write(bytes)
    }

    fun write(value: PTRRecord) {
        writeDomainName(value.domainName)
    }

    fun write(value: ARecord) {
        val bytes = value.address.address
        if (bytes.size != 4) throw Exception("Unexpected amount of address bytes.")
        write(bytes)
    }

    fun write(value: AAAARecord) {
        val bytes = value.address.address
        if (bytes.size != 16) throw Exception("Unexpected amount of address bytes.")
        write(bytes)
    }

    fun write(value: TXTRecord) {
        value.texts.forEach {
            val bytes = it.toByteArray(StandardCharsets.UTF_8)
            write(bytes.size.toByte())
            write(bytes)
        }
    }

    fun write(value: SRVRecord) {
        write(value.priority)
        write(value.weight)
        write(value.port)
        writeDomainName(value.target)
    }

    fun write(value: NSECRecord) {
        writeDomainName(value.ownerName)
        value.typeBitMaps.forEach { (windowBlock, bitmap) ->
            write(windowBlock)
            write(bitmap.size.toByte())
            write(bitmap)
        }
    }

    fun write(value: OPTRecord) {
        value.options.forEach { option ->
            write(option.code)
            write(option.data.size.toUShort())
            write(option.data)
        }
    }
}