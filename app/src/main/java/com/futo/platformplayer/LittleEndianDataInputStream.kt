package com.futo.platformplayer

import com.google.common.base.Preconditions
import com.google.common.io.ByteStreams
import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import java.io.DataInput
import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class LittleEndianDataInputStream
/**
 * Creates a `LittleEndianDataInputStream` that wraps the given stream.
 *
 * @param in the stream to delegate to
 */
    (`in`: InputStream?) : FilterInputStream(Preconditions.checkNotNull(`in`)), DataInput {
    /** This method will throw an [UnsupportedOperationException].  */
    override fun readLine(): String {
        throw UnsupportedOperationException("readLine is not supported")
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray) {
        ByteStreams.readFully(this, b)
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray, off: Int, len: Int) {
        ByteStreams.readFully(this, b, off, len)
    }

    @Throws(IOException::class)
    override fun skipBytes(n: Int): Int {
        return `in`.skip(n.toLong()).toInt()
    }

    @Throws(IOException::class)
    override fun readUnsignedByte(): Int {
        val b1 = `in`.read()
        if (0 > b1) {
            throw EOFException()
        }

        return b1
    }

    /**
     * Reads an unsigned `short` as specified by [DataInputStream.readUnsignedShort],
     * except using little-endian byte order.
     *
     * @return the next two bytes of the input stream, interpreted as an unsigned 16-bit integer in
     * little-endian byte order
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readUnsignedShort(): Int {
        val b1 = readAndCheckByte()
        val b2 = readAndCheckByte()

        return Ints.fromBytes(0.toByte(), 0.toByte(), b2, b1)
    }

    /**
     * Reads an integer as specified by [DataInputStream.readInt], except using little-endian
     * byte order.
     *
     * @return the next four bytes of the input stream, interpreted as an `int` in little-endian
     * byte order
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readInt(): Int {
        val b1 = readAndCheckByte()
        val b2 = readAndCheckByte()
        val b3 = readAndCheckByte()
        val b4 = readAndCheckByte()

        return Ints.fromBytes(b4, b3, b2, b1)
    }

    /**
     * Reads a `long` as specified by [DataInputStream.readLong], except using
     * little-endian byte order.
     *
     * @return the next eight bytes of the input stream, interpreted as a `long` in
     * little-endian byte order
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readLong(): Long {
        val b1 = readAndCheckByte()
        val b2 = readAndCheckByte()
        val b3 = readAndCheckByte()
        val b4 = readAndCheckByte()
        val b5 = readAndCheckByte()
        val b6 = readAndCheckByte()
        val b7 = readAndCheckByte()
        val b8 = readAndCheckByte()

        return Longs.fromBytes(b8, b7, b6, b5, b4, b3, b2, b1)
    }

    /**
     * Reads a `float` as specified by [DataInputStream.readFloat], except using
     * little-endian byte order.
     *
     * @return the next four bytes of the input stream, interpreted as a `float` in
     * little-endian byte order
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readFloat(): Float {
        return java.lang.Float.intBitsToFloat(readInt())
    }

    /**
     * Reads a `double` as specified by [DataInputStream.readDouble], except using
     * little-endian byte order.
     *
     * @return the next eight bytes of the input stream, interpreted as a `double` in
     * little-endian byte order
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readDouble(): Double {
        return java.lang.Double.longBitsToDouble(readLong())
    }

    @Throws(IOException::class)
    override fun readUTF(): String {
        return DataInputStream(`in`).readUTF()
    }

    /**
     * Reads a `short` as specified by [DataInputStream.readShort], except using
     * little-endian byte order.
     *
     * @return the next two bytes of the input stream, interpreted as a `short` in little-endian
     * byte order.
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun readShort(): Short {
        return readUnsignedShort().toShort()
    }

    /**
     * Reads a char as specified by [DataInputStream.readChar], except using little-endian
     * byte order.
     *
     * @return the next two bytes of the input stream, interpreted as a `char` in little-endian
     * byte order
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readChar(): Char {
        return readUnsignedShort().toChar()
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        return readUnsignedByte().toByte()
    }

    @Throws(IOException::class)
    override fun readBoolean(): Boolean {
        return readUnsignedByte() != 0
    }

    /**
     * Reads a byte from the input stream checking that the end of file (EOF) has not been
     * encountered.
     *
     * @return byte read from input
     * @throws IOException if an error is encountered while reading
     * @throws EOFException if the end of file (EOF) is encountered.
     */
    @Throws(IOException::class, EOFException::class)
    private fun readAndCheckByte(): Byte {
        val b1 = `in`.read()

        if (-1 == b1) {
            throw EOFException()
        }

        return b1.toByte()
    }
}