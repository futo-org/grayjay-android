package com.futo.platformplayer

import com.google.common.base.Preconditions
import com.google.common.primitives.Longs
import java.io.*

class LittleEndianDataOutputStream
    /**
     * Creates a `LittleEndianDataOutputStream` that wraps the given stream.
     *
     * @param out the stream to delegate to
     */
    (out: OutputStream?) : FilterOutputStream(DataOutputStream(Preconditions.checkNotNull(out))),
    DataOutput {
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        // Override slow FilterOutputStream impl
        out.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun writeBoolean(v: Boolean) {
        (out as DataOutputStream).writeBoolean(v)
    }

    @Throws(IOException::class)
    override fun writeByte(v: Int) {
        (out as DataOutputStream).writeByte(v)
    }

    @Deprecated(
        """The semantics of {@code writeBytes(String s)} are considered dangerous. Please use
        {@link #writeUTF(String s)}, {@link #writeChars(String s)} or another write method instead."""
    )
    @Throws(
        IOException::class
    )
    override fun writeBytes(s: String) {
        (out as DataOutputStream).writeBytes(s)
    }

    /**
     * Writes a char as specified by [DataOutputStream.writeChar], except using
     * little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeChar(v: Int) {
        writeShort(v)
    }

    /**
     * Writes a `String` as specified by [DataOutputStream.writeChars], except
     * each character is written using little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeChars(s: String) {
        for (i in 0 until s.length) {
            writeChar(s[i].code)
        }
    }

    /**
     * Writes a `double` as specified by [DataOutputStream.writeDouble], except
     * using little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeDouble(v: Double) {
        writeLong(java.lang.Double.doubleToLongBits(v))
    }

    /**
     * Writes a `float` as specified by [DataOutputStream.writeFloat], except using
     * little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeFloat(v: Float) {
        writeInt(java.lang.Float.floatToIntBits(v))
    }

    /**
     * Writes an `int` as specified by [DataOutputStream.writeInt], except using
     * little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeInt(v: Int) {
        val bytes = byteArrayOf(
            (0xFF and v).toByte(),
            (0xFF and (v shr 8)).toByte(),
            (0xFF and (v shr 16)).toByte(),
            (0xFF and (v shr 24)).toByte()
        )
        out.write(bytes)
    }

    /**
     * Writes a `long` as specified by [DataOutputStream.writeLong], except using
     * little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeLong(v: Long) {
        val bytes = Longs.toByteArray(java.lang.Long.reverseBytes(v))
        write(bytes, 0, bytes.size)
    }

    /**
     * Writes a `short` as specified by [DataOutputStream.writeShort], except using
     * little-endian byte order.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun writeShort(v: Int) {
        val bytes = byteArrayOf(
            (0xFF and v).toByte(),
            (0xFF and (v shr 8)).toByte()
        )
        out.write(bytes)
    }

    @Throws(IOException::class)
    override fun writeUTF(str: String) {
        (out as DataOutputStream).writeUTF(str)
    }

    // Overriding close() because FilterOutputStream's close() method pre-JDK8 has bad behavior:
    // it silently ignores any exception thrown by flush(). Instead, just close the delegate stream.
    // It should flush itself if necessary.
    @Throws(IOException::class)
    override fun close() {
        out.close()
    }
}