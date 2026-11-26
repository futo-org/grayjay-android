package com.futo.platformplayer.downloads

import java.io.File
import java.io.IOException

/**
 * File steps to move downloads into [targetDirectory]. No Android state, so it is unit tested.
 *
 * Safe order per file: copy to a temp name, check the size, rename to the final name.
 * The caller updates the item and only then deletes the source (see [deleteSource]).
 * A stop at any point leaves the source file in place, and a new run continues.
 */
class DownloadMover(val targetDirectory: File) {

    /** True if [source] is already directly in the target folder. */
    fun isInTarget(source: File): Boolean = source.parentFile?.absolutePath == targetDirectory.absolutePath;

    /**
     * Copies [source] into the target folder and returns the final file.
     * If a file with the same name and size is there (an earlier stopped run), the copy is skipped.
     * If a different file has that name, a free name is used.
     */
    fun copyIntoTarget(source: File, onBytes: (Long) -> Unit = {}, isCancelled: () -> Boolean = { false }): File {
        if (!source.isFile)
            throw IOException("Source file not found: ${source.absolutePath}");
        if (isInTarget(source))
            return source;
        if (!targetDirectory.isDirectory && !targetDirectory.mkdirs())
            throw IOException("Cannot create folder: ${targetDirectory.absolutePath}");

        val target = chooseTarget(source);
        if (target.isFile && sameContent(source, target)) {
            onBytes(source.length());
            return target;
        }

        val temp = File(targetDirectory, target.name + TEMP_SUFFIX);
        try {
            source.inputStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE);
                    while (true) {
                        if (isCancelled())
                            throw InterruptedException("Move stopped");
                        val read = input.read(buffer);
                        if (read < 0)
                            break;
                        output.write(buffer, 0, read);
                        onBytes(read.toLong());
                    }
                    output.fd.sync();
                }
            }
            if (temp.length() != source.length())
                throw IOException("Size check failed for ${target.name}: ${temp.length()} of ${source.length()} bytes");
            if (!temp.renameTo(target))
                throw IOException("Cannot rename ${temp.name} to ${target.name}");
        } catch (ex: Throwable) {
            temp.delete();
            throw ex;
        }
        return target;
    }

    /** Final file for [source]: same name, or "name (n).ext" if a different file has that name. */
    fun chooseTarget(source: File): File {
        val same = File(targetDirectory, source.name);
        if (!same.exists() || sameContent(source, same))
            return same;
        val base = source.nameWithoutExtension;
        val ext = source.extension.let { if (it.isEmpty()) "" else ".$it" };
        var n = 1;
        while (true) {
            val candidate = File(targetDirectory, "$base ($n)$ext");
            if (!candidate.exists() || sameContent(source, candidate))
                return candidate;
            n++;
        }
    }

    /** Deletes [source] after the item points to [target]. Never deletes the target itself. */
    fun deleteSource(source: File, target: File): Boolean {
        if (source.absolutePath == target.absolutePath)
            return false;
        if (!target.isFile || !sameContent(source, target))
            return false;
        return source.delete();
    }

    companion object {
        const val TEMP_SUFFIX = ".gjmove";
        private const val BUFFER_SIZE = 1024 * 1024;
        private const val SAMPLE_SIZE = 64 * 1024;

        /** Same size, and the same first and last 64 KB. Enough to tell an earlier copy from another file. */
        fun sameContent(a: File, b: File): Boolean {
            if (!a.isFile || !b.isFile || a.length() != b.length())
                return false;
            val length = a.length();
            val offsets = listOf(0L, (length - SAMPLE_SIZE).coerceAtLeast(0L)).distinct();
            java.io.RandomAccessFile(a, "r").use { ra ->
                java.io.RandomAccessFile(b, "r").use { rb ->
                    for (offset in offsets) {
                        val size = minOf(SAMPLE_SIZE.toLong(), length - offset).toInt();
                        val ba = ByteArray(size); val bb = ByteArray(size);
                        ra.seek(offset); ra.readFully(ba);
                        rb.seek(offset); rb.readFully(bb);
                        if (!ba.contentEquals(bb))
                            return false;
                    }
                }
            }
            return true;
        }

        /** Mount root of a path ("/storage/emulated/0", "/storage/ABCD-1234", "/data"). Same root = no copy space needed. */
        fun volumeRoot(path: String): String {
            val parts = path.trimStart('/').split('/');
            return when {
                parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" -> "/storage/emulated/${parts[2]}";
                parts.size >= 2 && parts[0] == "storage" -> "/storage/${parts[1]}";
                parts.size >= 2 && parts[0] == "mnt" -> "/mnt/${parts[1]}";
                parts.isNotEmpty() -> "/${parts[0]}";
                else -> "/";
            };
        }

        /** Bytes the target needs: the size of files that are on another volume than [targetDirectory]. */
        fun bytesNeeded(sources: List<File>, targetDirectory: File): Long {
            val targetRoot = volumeRoot(targetDirectory.absolutePath);
            return sources.filter { volumeRoot(it.absolutePath) != targetRoot }.sumOf { it.length() };
        }
    }
}
