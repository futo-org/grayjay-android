package com.futo.platformplayer.mdns

import android.util.Log

object Extensions {
    fun ByteArray.toByteDump(): String {
        val result = StringBuilder()
        for (i in indices) {
            result.append(String.format("%02X ", this[i]))

            if ((i + 1) % 16 == 0 || i == size - 1) {
                val padding = 3 * (16 - (i % 16 + 1))
                if (i == size - 1 && (i + 1) % 16 != 0) result.append(" ".repeat(padding))

                result.append("; ")
                val start = i - (i % 16)
                val end = minOf(i, size - 1)
                for (j in start..end) {
                    val ch = if (this[j] in 32..127) this[j].toChar() else '.'
                    result.append(ch)
                }
                if (i != size - 1) result.appendLine()
            }
        }
        return result.toString()
    }

    fun UByteArray.readDomainName(startPosition: Int): Pair<String, Int> {
        var position = startPosition
        return readDomainName(position, 0)
    }

    private fun UByteArray.readDomainName(position: Int, depth: Int = 0): Pair<String, Int> {
        if (depth > 16) throw Exception("Exceeded maximum recursion depth in DNS packet. Possible circular reference.")

        val domainParts = mutableListOf<String>()
        var newPosition = position

        while (true) {
            if (newPosition < 0)
                println()

            val length = this[newPosition].toUByte()
            if ((length and 0b11000000u).toUInt() == 0b11000000u) {
                val offset = (((length and 0b00111111u).toUInt()) shl 8) or this[newPosition + 1].toUInt()
                val (part, _) = this.readDomainName(offset.toInt(), depth + 1)
                domainParts.add(part)
                newPosition += 2
                break
            } else if (length.toUInt() == 0u) {
                newPosition++
                break
            } else {
                newPosition++
                val part = String(this.asByteArray(), newPosition, length.toInt(), Charsets.UTF_8)
                domainParts.add(part)
                newPosition += length.toInt()
            }
        }

        return domainParts.joinToString(".") to newPosition
    }
}