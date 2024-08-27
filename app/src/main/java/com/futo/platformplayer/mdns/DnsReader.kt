package com.futo.platformplayer.mdns

import com.futo.platformplayer.mdns.Extensions.readDomainName
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.pow
import java.net.InetAddress

data class PTRRecord(val domainName: String)

data class ARecord(val address: InetAddress)

data class AAAARecord(val address: InetAddress)

data class MXRecord(val preference: UShort, val exchange: String)

data class CNAMERecord(val cname: String)

data class TXTRecord(val texts: List<String>)

data class SOARecord(
    val primaryNameServer: String,
    val responsibleAuthorityMailbox: String,
    val serialNumber: Int,
    val refreshInterval: Int,
    val retryInterval: Int,
    val expiryLimit: Int,
    val minimumTTL: Int
)

data class SRVRecord(val priority: UShort, val weight: UShort, val port: UShort, val target: String)

data class NSRecord(val nameServer: String)

data class CAARecord(val flags: Byte, val tag: String, val value: String)

data class HINFORecord(val cpu: String, val os: String)

data class RPRecord(val mailbox: String, val txtDomainName: String)


data class AFSDBRecord(val subtype: UShort, val hostname: String)
data class LOCRecord(
    val version: Byte,
    val size: Double,
    val horizontalPrecision: Double,
    val verticalPrecision: Double,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
) {
    companion object {
        fun decodeSizeOrPrecision(coded: Byte): Double {
            val baseValue = (coded.toInt() shr 4) and 0x0F
            val exponent = coded.toInt() and 0x0F
            return baseValue * 10.0.pow(exponent.toDouble())
        }

        fun decodeLatitudeOrLongitude(coded: Int): Double {
            val arcSeconds = coded / 1E3
            return arcSeconds / 3600.0
        }

        fun decodeAltitude(coded: Int): Double {
            return (coded / 100.0) - 100000.0
        }
    }
}

data class NAPTRRecord(
    val order: UShort,
    val preference: UShort,
    val flags: String,
    val services: String,
    val regexp: String,
    val replacement: String
)

data class RRSIGRecord(
    val typeCovered: UShort,
    val algorithm: Byte,
    val labels: Byte,
    val originalTTL: UInt,
    val signatureExpiration: UInt,
    val signatureInception: UInt,
    val keyTag: UShort,
    val signersName: String,
    val signature: ByteArray
)

data class KXRecord(val preference: UShort, val exchanger: String)

data class CERTRecord(val type: UShort, val keyTag: UShort, val algorithm: Byte, val certificate: ByteArray)



data class DNAMERecord(val target: String)

data class DSRecord(val keyTag: UShort, val algorithm: Byte, val digestType: Byte, val digest: ByteArray)

data class SSHFPRecord(val algorithm: Byte, val fingerprintType: Byte, val fingerprint: ByteArray)

data class TLSARecord(val usage: Byte, val selector: Byte, val matchingType: Byte, val certificateAssociationData: ByteArray)

data class SMIMEARecord(val usage: Byte, val selector: Byte, val matchingType: Byte, val certificateAssociationData: ByteArray)

data class URIRecord(val priority: UShort, val weight: UShort, val target: String)

data class NSECRecord(val ownerName: String, val typeBitMaps: List<Pair<Byte, ByteArray>>)
data class NSEC3Record(
    val hashAlgorithm: Byte,
    val flags: Byte,
    val iterations: UShort,
    val salt: ByteArray,
    val nextHashedOwnerName: ByteArray,
    val typeBitMaps: List<UShort>
)

data class NSEC3PARAMRecord(val hashAlgorithm: Byte, val flags: Byte, val iterations: UShort, val salt: ByteArray)
data class SPFRecord(val texts: List<String>)
data class TKEYRecord(
    val algorithm: String,
    val inception: UInt,
    val expiration: UInt,
    val mode: UShort,
    val error: UShort,
    val keyData: ByteArray,
    val otherData: ByteArray
)

data class TSIGRecord(
    val algorithmName: String,
    val timeSigned: UInt,
    val fudge: UShort,
    val mac: ByteArray,
    val originalID: UShort,
    val error: UShort,
    val otherData: ByteArray
)

data class OPTRecordOption(val code: UShort, val data: ByteArray)
data class OPTRecord(val options: List<OPTRecordOption>)

class DnsReader(private val data: ByteArray, private var position: Int = 0, private val length: Int = data.size) {

    private val endPosition: Int = position + length

    fun readDomainName(): String {
        return data.asUByteArray().readDomainName(position).also { position = it.second }.first
    }

    fun readDouble(): Double {
        checkRemainingBytes(Double.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Double.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).double
        position += Double.SIZE_BYTES
        return result
    }

    fun readInt16(): Short {
        checkRemainingBytes(Short.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).short
        position += Short.SIZE_BYTES
        return result
    }

    fun readInt32(): Int {
        checkRemainingBytes(Int.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).int
        position += Int.SIZE_BYTES
        return result
    }

    fun readInt64(): Long {
        checkRemainingBytes(Long.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).long
        position += Long.SIZE_BYTES
        return result
    }

    fun readSingle(): Float {
        checkRemainingBytes(Float.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Float.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).float
        position += Float.SIZE_BYTES
        return result
    }

    fun readByte(): Byte {
        checkRemainingBytes(Byte.SIZE_BYTES)
        return data[position++]
    }

    fun readBytes(length: Int): ByteArray {
        checkRemainingBytes(length)
        return ByteArray(length).also { data.copyInto(it, startIndex = position, endIndex = position + length) }
            .also { position += length }
    }

    fun readUInt16(): UShort {
        checkRemainingBytes(Short.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).short.toUShort()
        position += Short.SIZE_BYTES
        return result
    }

    fun readUInt32(): UInt {
        checkRemainingBytes(Int.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).int.toUInt()
        position += Int.SIZE_BYTES
        return result
    }

    fun readUInt64(): ULong {
        checkRemainingBytes(Long.SIZE_BYTES)
        val result = ByteBuffer.wrap(data, position, Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).long.toULong()
        position += Long.SIZE_BYTES
        return result
    }

    fun readString(): String {
        val length = data[position++].toInt()
        checkRemainingBytes(length)
        return String(data, position, length, StandardCharsets.UTF_8).also { position += length }
    }

    private fun checkRemainingBytes(requiredBytes: Int) {
        if (position + requiredBytes > endPosition) throw IndexOutOfBoundsException()
    }

    fun readRPRecord(): RPRecord {
        return RPRecord(readDomainName(), readDomainName())
    }

    fun readKXRecord(): KXRecord {
        val preference = readUInt16()
        val exchanger = readDomainName()
        return KXRecord(preference, exchanger)
    }

    fun readCERTRecord(): CERTRecord {
        val type = readUInt16()
        val keyTag = readUInt16()
        val algorithm = readByte()
        val certificateLength = readUInt16().toInt() - 5
        val certificate = readBytes(certificateLength)
        return CERTRecord(type, keyTag, algorithm, certificate)
    }

    fun readPTRRecord(): PTRRecord {
        return PTRRecord(readDomainName())
    }

    fun readARecord(): ARecord {
        val address = readBytes(4)
        return ARecord(InetAddress.getByAddress(address))
    }

    fun readAAAARecord(): AAAARecord {
        val address = readBytes(16)
        return AAAARecord(InetAddress.getByAddress(address))
    }

    fun readMXRecord(): MXRecord {
        val preference = readUInt16()
        val exchange = readDomainName()
        return MXRecord(preference, exchange)
    }

    fun readCNAMERecord(): CNAMERecord {
        return CNAMERecord(readDomainName())
    }

    fun readTXTRecord(): TXTRecord {
        val texts = mutableListOf<String>()
        while (position < endPosition) {
            val textLength = data[position++].toInt()
            checkRemainingBytes(textLength)
            val text = String(data, position, textLength, StandardCharsets.UTF_8)
            texts.add(text)
            position += textLength
        }
        return TXTRecord(texts)
    }

    fun readSOARecord(): SOARecord {
        val primaryNameServer = readDomainName()
        val responsibleAuthorityMailbox = readDomainName()
        val serialNumber = readInt32()
        val refreshInterval = readInt32()
        val retryInterval = readInt32()
        val expiryLimit = readInt32()
        val minimumTTL = readInt32()
        return SOARecord(primaryNameServer, responsibleAuthorityMailbox, serialNumber, refreshInterval, retryInterval, expiryLimit, minimumTTL)
    }

    fun readSRVRecord(): SRVRecord {
        val priority = readUInt16()
        val weight = readUInt16()
        val port = readUInt16()
        val target = readDomainName()
        return SRVRecord(priority, weight, port, target)
    }

    fun readNSRecord(): NSRecord {
        return NSRecord(readDomainName())
    }

    fun readCAARecord(): CAARecord {
        val length = readUInt16().toInt()
        val flags = readByte()
        val tagLength = readByte().toInt()
        val tag = String(data, position, tagLength, StandardCharsets.US_ASCII).also { position += tagLength }
        val valueLength = length - 1 - 1 - tagLength
        val value = String(data, position, valueLength, StandardCharsets.US_ASCII).also { position += valueLength }
        return CAARecord(flags, tag, value)
    }

    fun readHINFORecord(): HINFORecord {
        val cpuLength = readByte().toInt()
        val cpu = String(data, position, cpuLength, StandardCharsets.US_ASCII).also { position += cpuLength }
        val osLength = readByte().toInt()
        val os = String(data, position, osLength, StandardCharsets.US_ASCII).also { position += osLength }
        return HINFORecord(cpu, os)
    }

    fun readAFSDBRecord(): AFSDBRecord {
        return AFSDBRecord(readUInt16(), readDomainName())
    }

    fun readLOCRecord(): LOCRecord {
        val version = readByte()
        val size = LOCRecord.decodeSizeOrPrecision(readByte())
        val horizontalPrecision = LOCRecord.decodeSizeOrPrecision(readByte())
        val verticalPrecision = LOCRecord.decodeSizeOrPrecision(readByte())
        val latitudeCoded = readInt32()
        val longitudeCoded = readInt32()
        val altitudeCoded = readInt32()
        val latitude = LOCRecord.decodeLatitudeOrLongitude(latitudeCoded)
        val longitude = LOCRecord.decodeLatitudeOrLongitude(longitudeCoded)
        val altitude = LOCRecord.decodeAltitude(altitudeCoded)
        return LOCRecord(version, size, horizontalPrecision, verticalPrecision, latitude, longitude, altitude)
    }

    fun readNAPTRRecord(): NAPTRRecord {
        val order = readUInt16()
        val preference = readUInt16()
        val flags = readString()
        val services = readString()
        val regexp = readString()
        val replacement = readDomainName()
        return NAPTRRecord(order, preference, flags, services, regexp, replacement)
    }

    fun readDNAMERecord(): DNAMERecord {
        return DNAMERecord(readDomainName())
    }

    fun readDSRecord(): DSRecord {
        val keyTag = readUInt16()
        val algorithm = readByte()
        val digestType = readByte()
        val digestLength = readUInt16().toInt() - 4
        val digest = readBytes(digestLength)
        return DSRecord(keyTag, algorithm, digestType, digest)
    }

    fun readSSHFPRecord(): SSHFPRecord {
        val algorithm = readByte()
        val fingerprintType = readByte()
        val fingerprintLength = readUInt16().toInt() - 2
        val fingerprint = readBytes(fingerprintLength)
        return SSHFPRecord(algorithm, fingerprintType, fingerprint)
    }

    fun readTLSARecord(): TLSARecord {
        val usage = readByte()
        val selector = readByte()
        val matchingType = readByte()
        val dataLength = readUInt16().toInt() - 3
        val certificateAssociationData = readBytes(dataLength)
        return TLSARecord(usage, selector, matchingType, certificateAssociationData)
    }

    fun readSMIMEARecord(): SMIMEARecord {
        val usage = readByte()
        val selector = readByte()
        val matchingType = readByte()
        val dataLength = readUInt16().toInt() - 3
        val certificateAssociationData = readBytes(dataLength)
        return SMIMEARecord(usage, selector, matchingType, certificateAssociationData)
    }

    fun readURIRecord(): URIRecord {
        val priority = readUInt16()
        val weight = readUInt16()
        val length = readUInt16().toInt()
        val target = String(data, position, length, StandardCharsets.US_ASCII).also { position += length }
        return URIRecord(priority, weight, target)
    }

    fun readRRSIGRecord(): RRSIGRecord {
        val typeCovered = readUInt16()
        val algorithm = readByte()
        val labels = readByte()
        val originalTTL = readUInt32()
        val signatureExpiration = readUInt32()
        val signatureInception = readUInt32()
        val keyTag = readUInt16()
        val signersName = readDomainName()
        val signatureLength = readUInt16().toInt()
        val signature = readBytes(signatureLength)
        return RRSIGRecord(
            typeCovered,
            algorithm,
            labels,
            originalTTL,
            signatureExpiration,
            signatureInception,
            keyTag,
            signersName,
            signature
        )
    }

    fun readNSECRecord(): NSECRecord {
        val ownerName = readDomainName()
        val typeBitMaps = mutableListOf<Pair<Byte, ByteArray>>()
        while (position < endPosition) {
            val windowBlock = readByte()
            val bitmapLength = readByte().toInt()
            val bitmap = readBytes(bitmapLength)
            typeBitMaps.add(windowBlock to bitmap)
        }
        return NSECRecord(ownerName, typeBitMaps)
    }

    fun readNSEC3Record(): NSEC3Record {
        val hashAlgorithm = readByte()
        val flags = readByte()
        val iterations = readUInt16()
        val saltLength = readByte().toInt()
        val salt = readBytes(saltLength)
        val hashLength = readByte().toInt()
        val nextHashedOwnerName = readBytes(hashLength)
        val bitMapLength = readUInt16().toInt()
        val typeBitMaps = mutableListOf<UShort>()
        val endPos = position + bitMapLength
        while (position < endPos) {
            typeBitMaps.add(readUInt16())
        }
        return NSEC3Record(hashAlgorithm, flags, iterations, salt, nextHashedOwnerName, typeBitMaps)
    }

    fun readNSEC3PARAMRecord(): NSEC3PARAMRecord {
        val hashAlgorithm = readByte()
        val flags = readByte()
        val iterations = readUInt16()
        val saltLength = readByte().toInt()
        val salt = readBytes(saltLength)
        return NSEC3PARAMRecord(hashAlgorithm, flags, iterations, salt)
    }


    fun readSPFRecord(): SPFRecord {
        val length = readUInt16().toInt()
        val texts = mutableListOf<String>()
        val endPos = position + length
        while (position < endPos) {
            val textLength = readByte().toInt()
            val text = String(data, position, textLength, StandardCharsets.US_ASCII).also { position += textLength }
            texts.add(text)
        }
        return SPFRecord(texts)
    }

    fun readTKEYRecord(): TKEYRecord {
        val algorithm = readDomainName()
        val inception = readUInt32()
        val expiration = readUInt32()
        val mode = readUInt16()
        val error = readUInt16()
        val keySize = readUInt16().toInt()
        val keyData = readBytes(keySize)
        val otherSize = readUInt16().toInt()
        val otherData = readBytes(otherSize)
        return TKEYRecord(algorithm, inception, expiration, mode, error, keyData, otherData)
    }

    fun readTSIGRecord(): TSIGRecord {
        val algorithmName = readDomainName()
        val timeSigned = readUInt32()
        val fudge = readUInt16()
        val macSize = readUInt16().toInt()
        val mac = readBytes(macSize)
        val originalID = readUInt16()
        val error = readUInt16()
        val otherSize = readUInt16().toInt()
        val otherData = readBytes(otherSize)
        return TSIGRecord(algorithmName, timeSigned, fudge, mac, originalID, error, otherData)
    }



    fun readOPTRecord(): OPTRecord {
        val options = mutableListOf<OPTRecordOption>()
        while (position < endPosition) {
            val optionCode = readUInt16()
            val optionLength = readUInt16().toInt()
            val optionData = readBytes(optionLength)
            options.add(OPTRecordOption(optionCode, optionData))
        }
        return OPTRecord(options)
    }
}
