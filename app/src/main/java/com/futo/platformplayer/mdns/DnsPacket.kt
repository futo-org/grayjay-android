package com.futo.platformplayer.mdns

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class QueryResponse(val value: Byte) {
    Query(0),
    Response(1)
}

enum class DnsOpcode(val value: Byte) {
    StandardQuery(0),
    InverseQuery(1),
    ServerStatusRequest(2)
}

enum class DnsResponseCode(val value: Byte) {
    NoError(0),
    FormatError(1),
    ServerFailure(2),
    NameError(3),
    NotImplemented(4),
    Refused(5)
}

data class DnsPacketHeader(
    val identifier: UShort,
    val queryResponse: Int,
    val opcode: Int,
    val authoritativeAnswer: Boolean,
    val truncated: Boolean,
    val recursionDesired: Boolean,
    val recursionAvailable: Boolean,
    val answerAuthenticated: Boolean,
    val nonAuthenticatedData: Boolean,
    val responseCode: DnsResponseCode
)

data class DnsPacket(
    val header: DnsPacketHeader,
    val questions: List<DnsQuestion>,
    val answers: List<DnsResourceRecord>,
    val authorities: List<DnsResourceRecord>,
    val additionals: List<DnsResourceRecord>
) {
    companion object {
        fun parse(data: ByteArray): DnsPacket {
            val span = data.asUByteArray()
            val flags = (span[2].toInt() shl 8 or span[3].toInt()).toUShort()
            val questionCount = (span[4].toInt() shl 8 or span[5].toInt()).toUShort()
            val answerCount = (span[6].toInt() shl 8 or span[7].toInt()).toUShort()
            val authorityCount = (span[8].toInt() shl 8 or span[9].toInt()).toUShort()
            val additionalCount = (span[10].toInt() shl 8 or span[11].toInt()).toUShort()

            var position = 12

            val questions = List(questionCount.toInt()) {
                DnsQuestion.parse(data, position).also { position = it.second }
            }.map { it.first }

            val answers = List(answerCount.toInt()) {
                DnsResourceRecord.parse(data, position).also { position = it.second }
            }.map { it.first }

            val authorities = List(authorityCount.toInt()) {
                DnsResourceRecord.parse(data, position).also { position = it.second }
            }.map { it.first }

            val additionals = List(additionalCount.toInt()) {
                DnsResourceRecord.parse(data, position).also { position = it.second }
            }.map { it.first }

            return DnsPacket(
                header = DnsPacketHeader(
                    identifier = (span[0].toInt() shl 8 or span[1].toInt()).toUShort(),
                    queryResponse = ((flags.toUInt() shr 15) and 0b1u).toInt(),
                    opcode = ((flags.toUInt() shr 11) and 0b1111u).toInt(),
                    authoritativeAnswer = (flags.toInt() shr 10) and 0b1 != 0,
                    truncated = (flags.toInt() shr 9) and 0b1 != 0,
                    recursionDesired = (flags.toInt() shr 8) and 0b1 != 0,
                    recursionAvailable = (flags.toInt() shr 7) and 0b1 != 0,
                    answerAuthenticated = (flags.toInt() shr 5) and 0b1 != 0,
                    nonAuthenticatedData = (flags.toInt() shr 4) and 0b1 != 0,
                    responseCode = DnsResponseCode.entries[flags.toInt() and 0b1111]
                ),
                questions = questions,
                answers = answers,
                authorities = authorities,
                additionals = additionals
            )
        }
    }
}