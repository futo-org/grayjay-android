package com.futo.platformplayer

import android.util.Log
import com.google.common.base.CharMatcher
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer


private const val IPV4_PART_COUNT = 4;
private const val IPV6_PART_COUNT = 8;
private const val IPV4_DELIMITER = '.';
private const val IPV6_DELIMITER = ':';
private val IPV4_DELIMITER_MATCHER = CharMatcher.`is`(IPV4_DELIMITER);
private val IPV6_DELIMITER_MATCHER = CharMatcher.`is`(IPV6_DELIMITER);
private val LOOPBACK4: Inet4Address? = "127.0.0.1".toInetAddress() as Inet4Address?;
private val ANY4: Inet4Address? = "0.0.0.0".toInetAddress() as Inet4Address?;

fun String.toInetAddress(): InetAddress? {
    val addr = ipStringToBytes(this) ?: return null;
    return addr.toInetAddress();
}

private fun ipStringToBytes(ipStringParam: String): ByteArray? {
    var ipString: String? = ipStringParam;
    var hasColon = false;
    var hasDot = false;
    var percentIndex = -1;

    for (i in 0 until ipString!!.length) {
        val c = ipString[i];
        if (c == '.') {
            hasDot = true;
        } else if (c == ':') {
            if (hasDot) {
                return null;
            }

            hasColon = true;
        } else if (c == '%') {
            percentIndex = i;
            break;
        } else if (c.digitToIntOrNull(16) ?: -1 == -1) {
            return null;
        }
    }

    // Now decide which address family to parse.
    if (hasColon) {
        if (hasDot) {
            ipString = convertDottedQuadToHex(ipString)
            if (ipString == null) {
                return null;
            }
        }
        if (percentIndex != -1) {
            ipString = ipString.substring(0, percentIndex);
        }
        return textToNumericFormatV6(ipString);
    } else if (hasDot) {
        return if (percentIndex != -1) {
            null // Scope IDs are not supported for IPV4
        } else textToNumericFormatV4(ipString);
    }
    return null
}

private fun textToNumericFormatV4(ipString: String): ByteArray? {
    if (IPV4_DELIMITER_MATCHER.countIn(ipString) + 1 != IPV4_PART_COUNT) {
        return null; // Wrong number of parts
    }
    val bytes = ByteArray(IPV4_PART_COUNT);
    var start = 0;
    // Iterate through the parts of the ip string.
    // Invariant: start is always the beginning of an octet.
    for (i in 0 until IPV4_PART_COUNT) {
        var end = ipString.indexOf(IPV4_DELIMITER, start);
        if (end == -1) {
            end = ipString.length;
        }
        try {
            bytes[i] = parseOctet(ipString, start, end);
        } catch (ex: java.lang.NumberFormatException) {
            return null;
        }
        start = end + 1;
    }
    return bytes;
}

private fun textToNumericFormatV6(ipString: String): ByteArray? {
    // An address can have [2..8] colons.
    val delimiterCount: Int = IPV6_DELIMITER_MATCHER.countIn(ipString);
    if (delimiterCount < 2 || delimiterCount > IPV6_PART_COUNT) {
        return null;
    }
    var partsSkipped: Int = IPV6_PART_COUNT - (delimiterCount + 1); // estimate; may be modified later
    var hasSkip = false;
    // Scan for the appearance of ::, to mark a skip-format IPV6 string and adjust the partsSkipped
    // estimate.
    for (i in 0 until ipString.length - 1) {
        if (ipString[i] == IPV6_DELIMITER && ipString[i + 1] == IPV6_DELIMITER) {
            if (hasSkip) {
                return null; // Can't have more than one ::
            }
            hasSkip = true;
            partsSkipped++; // :: means we skipped an extra part in between the two delimiters.
            if (i == 0) {
                partsSkipped++; // Begins with ::, so we skipped the part preceding the first :
            }
            if (i == ipString.length - 2) {
                partsSkipped++; // Ends with ::, so we skipped the part after the last :
            }
        }
    }
    if (ipString[0] == IPV6_DELIMITER && ipString[1] != IPV6_DELIMITER) {
        return null; // ^: requires ^::
    }
    if (ipString[ipString.length - 1] == IPV6_DELIMITER && ipString[ipString.length - 2] != IPV6_DELIMITER) {
        return null; // :$ requires ::$
    }
    if (hasSkip && partsSkipped <= 0) {
        return null // :: must expand to at least one '0'
    }
    if (!hasSkip && delimiterCount + 1 != IPV6_PART_COUNT) {
        return null // Incorrect number of parts
    }
    val rawBytes: ByteBuffer = ByteBuffer.allocate(2 * IPV6_PART_COUNT)
    try {
        // Iterate through the parts of the ip string.
        // Invariant: start is always the beginning of a hextet, or the second ':' of the skip
        // sequence "::"
        var start = 0
        if (ipString[0] == IPV6_DELIMITER) {
            start = 1
        }
        while (start < ipString.length) {
            var end: Int = ipString.indexOf(IPV6_DELIMITER, start)
            if (end == -1) {
                end = ipString.length
            }
            if (ipString[start] == IPV6_DELIMITER) {
                // expand zeroes
                for (i in 0 until partsSkipped) {
                    rawBytes.putShort(0.toShort())
                }
            } else {
                rawBytes.putShort(parseHextet(ipString, start, end))
            }
            start = end + 1
        }
    } catch (ex: NumberFormatException) {
        return null
    }
    return rawBytes.array()
}

private fun parseHextet(ipString: String, start: Int, end: Int): Short {
    // Note: we already verified that this string contains only hex digits.
    val length = end - start
    if (length <= 0 || length > 4) {
        throw java.lang.NumberFormatException()
    }
    var hextet = 0
    for (i in start until end) {
        hextet = hextet shl 4
        hextet = hextet or ipString[i].digitToIntOrNull(16)!!
    }
    return hextet.toShort()
}

private fun parseOctet(ipString: String, start: Int, end: Int): Byte {
    // Note: we already verified that this string contains only hex digits, but the string may still
    // contain non-decimal characters.
    val length = end - start
    if (length <= 0 || length > 3) {
        throw java.lang.NumberFormatException()
    }
    // Disallow leading zeroes, because no clear standard exists on
    // whether these should be interpreted as decimal or octal.
    if (length > 1 && ipString[start] == '0') {
        throw java.lang.NumberFormatException()
    }
    var octet = 0
    for (i in start until end) {
        octet *= 10
        val digit = ipString[i].digitToIntOrNull() ?: -1
        if (digit < 0) {
            throw java.lang.NumberFormatException()
        }
        octet += digit
    }
    if (octet > 255) {
        throw java.lang.NumberFormatException()
    }
    return octet.toByte()
}

fun convertDottedQuadToHex(ipString: String): String? {
    val lastColon = ipString.lastIndexOf(':');
    val initialPart = ipString.substring(0, lastColon + 1);
    val dottedQuad = ipString.substring(lastColon + 1);
    val quad: ByteArray = textToNumericFormatV4(dottedQuad) ?: return null;
    val penultimate = Integer.toHexString(quad[0].toInt() and 0xff shl 8 or (quad[1].toInt() and 0xff));
    val ultimate = Integer.toHexString(quad[2].toInt() and 0xff shl 8 or (quad[3].toInt() and 0xff));
    return "$initialPart$penultimate:$ultimate";
}

private fun ByteArray.toInetAddress(): InetAddress {
    return InetAddress.getByAddress(this);
}

fun getConnectedSocket(addresses: List<InetAddress>, port: Int): Socket? {
    val timeout = 2000

    if (addresses.isEmpty()) {
        return null;
    }

    if (addresses.size == 1) {
        val socket = Socket()

        try {
            return socket.apply { this.connect(InetSocketAddress(addresses[0], port), timeout) }
        } catch (e: Throwable) {
            Log.i("getConnectedSocket", "Failed to connect to: ${addresses[0]}", e)
            socket.close()
        }

        return null;
    }

    val sockets: ArrayList<Socket> = arrayListOf();
    for (i in addresses.indices) {
        sockets.add(Socket());
    }

    val syncObject = Object();
    var connectedSocket: Socket? = null;
    val threads: ArrayList<Thread> = arrayListOf();
    for (i in 0 until sockets.size) {
        val address = addresses[i];
        val socket = sockets[i];
        val thread = Thread {
            try {
                synchronized(syncObject) {
                    if (connectedSocket != null) {
                        return@Thread;
                    }
                }

                socket.connect(InetSocketAddress(address, port), timeout);

                synchronized(syncObject) {
                    if (connectedSocket == null) {
                        connectedSocket = socket;

                        for (j in 0 until sockets.size) {
                            if (i != j) {
                                sockets[j].close();
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.i("getConnectedSocket", "Failed to connect to: $address", e)
            }
        };

        thread.start();
        threads.add(thread);
    }

    for (thread in threads) {
        thread.join();
    }

    return connectedSocket;
}

fun InputStream.readHttpHeaderBytes() : ByteArray {
    val headerBytes = ByteArrayOutputStream()
    var crlfCount = 0

    while (crlfCount < 4) {
        val b = read()
        if (b == -1) {
            throw IOException("Unexpected end of stream while reading headers")
        }

        if (b == 0x0D || b == 0x0A) { // CR or LF
            crlfCount++
        } else {
            crlfCount = 0
        }

        headerBytes.write(b)
    }

    return headerBytes.toByteArray()
}

fun InputStream.readLine() : String? {
    val line = ByteArrayOutputStream()
    var crlfCount = 0

    while (crlfCount < 2) {
        val b = read()
        if (b == -1) {
            return null
        }

        if (b == 0x0D || b == 0x0A) { // CR or LF
            crlfCount++
        } else {
            crlfCount = 0
            line.write(b)
        }
    }

    return String(line.toByteArray(), Charsets.UTF_8)
}