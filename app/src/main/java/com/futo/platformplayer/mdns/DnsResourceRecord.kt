package com.futo.platformplayer.mdns

import com.futo.platformplayer.mdns.Extensions.readDomainName

enum class ResourceRecordType(val value: UShort) {
    None(0u),
    A(1u),
    NS(2u),
    MD(3u),
    MF(4u),
    CNAME(5u),
    SOA(6u),
    MB(7u),
    MG(8u),
    MR(9u),
    NULL(10u),
    WKS(11u),
    PTR(12u),
    HINFO(13u),
    MINFO(14u),
    MX(15u),
    TXT(16u),
    RP(17u),
    AFSDB(18u),
    SIG(24u),
    KEY(25u),
    AAAA(28u),
    LOC(29u),
    SRV(33u),
    NAPTR(35u),
    KX(36u),
    CERT(37u),
    DNAME(39u),
    APL(42u),
    DS(43u),
    SSHFP(44u),
    IPSECKEY(45u),
    RRSIG(46u),
    NSEC(47u),
    DNSKEY(48u),
    DHCID(49u),
    NSEC3(50u),
    NSEC3PARAM(51u),
    TSLA(52u),
    SMIMEA(53u),
    HIP(55u),
    CDS(59u),
    CDNSKEY(60u),
    OPENPGPKEY(61u),
    CSYNC(62u),
    ZONEMD(63u),
    SVCB(64u),
    HTTPS(65u),
    EUI48(108u),
    EUI64(109u),
    TKEY(249u),
    TSIG(250u),
    URI(256u),
    CAA(257u),
    TA(32768u),
    DLV(32769u),
    AXFR(252u),
    IXFR(251u),
    OPT(41u)
}

enum class ResourceRecordClass(val value: UShort) {
    IN(1u),
    CS(2u),
    CH(3u),
    HS(4u)
}

data class DnsResourceRecord(
    override val name: String,
    override val type: Int,
    override val clazz: Int,
    val timeToLive: UInt,
    val cacheFlush: Boolean,
    val dataPosition: Int = -1,
    val dataLength: Int = -1,
    private val data: ByteArray? = null
) : DnsResourceRecordBase(name, type, clazz) {

    companion object {
        fun parse(data: ByteArray, startPosition: Int): Pair<DnsResourceRecord, Int> {
            val span = data.asUByteArray()
            var position = startPosition
            val name = span.readDomainName(position).also { position = it.second }
            val type = (span[position].toInt() shl 8 or span[position + 1].toInt()).toUShort()
            position += 2
            val clazz = (span[position].toInt() shl 8 or span[position + 1].toInt()).toUShort()
            position += 2
            val ttl = (span[position].toInt() shl 24 or (span[position + 1].toInt() shl 16) or
                    (span[position + 2].toInt() shl 8) or span[position + 3].toInt()).toUInt()
            position += 4
            val rdlength = (span[position].toInt() shl 8 or span[position + 1].toInt()).toUShort()
            val rdposition = position + 2
            position += 2 + rdlength.toInt()

            return DnsResourceRecord(
                name = name.first,
                type = type.toInt(),
                clazz = clazz.toInt() and 0b1111111_11111111,
                timeToLive = ttl,
                cacheFlush = ((clazz.toInt() shr 15) and 0b1) != 0,
                dataPosition = rdposition,
                dataLength = rdlength.toInt(),
                data = data
            ) to position
        }
    }

    fun getDataReader(): DnsReader {
        return DnsReader(data!!, dataPosition, dataLength)
    }
}
