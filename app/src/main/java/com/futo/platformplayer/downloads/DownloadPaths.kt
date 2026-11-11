package com.futo.platformplayer.downloads

import java.io.File

/**
 * Path rules for the download folder. Pure functions, no Android state.
 *
 * Downloaded items store file names relative to the current download folder. A path that
 * is already absolute (older downloads, or files left in a previous folder) is used as is.
 */
object DownloadPaths {
    /** Grayjay keeps its files in this subfolder of the folder the user picks. */
    const val SUBFOLDER_NAME = "Grayjay Downloads";
    /** Marks the subfolder as owned by one app install, so only that install cleans it. */
    const val OWNER_FILE_NAME = ".grayjay-owner";

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";

    /**
     * Converts a tree document id from the system folder picker to a file path.
     * "primary:Movies" -> "<primaryRoot>/Movies", "1A2B-3C4D:Movies" -> "/storage/1A2B-3C4D/Movies".
     * Returns null for ids that are not on a storage volume.
     */
    fun treeDocumentIdToPath(documentId: String, primaryRoot: String): String? {
        val separator = documentId.indexOf(':');
        if (separator <= 0)
            return null;
        val volume = documentId.substring(0, separator);
        val relative = documentId.substring(separator + 1).trim('/');
        if (relative.split('/').any { it == ".." })
            return null;

        val root = when {
            volume == "primary" -> primaryRoot.trimEnd('/');
            volume == "home" -> primaryRoot.trimEnd('/') + "/Documents";
            volume.contains('/') || volume == "raw" || volume == "msf" -> return null;
            else -> "/storage/$volume";
        };
        return if (relative.isEmpty()) root else "$root/$relative";
    }

    fun resolve(storedPath: String, downloadsDirectory: File): File {
        val file = File(storedPath);
        return if (file.isAbsolute) file else File(downloadsDirectory, storedPath);
    }

    /** Returns the file name when [path] is directly inside [downloadsDirectory], else the absolute path. */
    fun toStoredPath(path: String, downloadsDirectory: File): String {
        val file = File(path);
        if (!file.isAbsolute)
            return path;
        return if (file.parentFile?.absolutePath == downloadsDirectory.absolutePath) file.name else file.absolutePath;
    }

    /** Size estimate of a stream: [bitsPerSecond] x [durationSeconds] / 8. 0 if a value is not known. */
    fun estimateBytes(bitsPerSecond: Long, durationSeconds: Long): Long {
        if (bitsPerSecond <= 0 || durationSeconds <= 0)
            return 0;
        return bitsPerSecond * durationSeconds / 8;
    }

    /** Stored path for a folder change from [oldDirectory] to [newDirectory]; the file itself does not move. */
    fun rebase(storedPath: String, oldDirectory: File, newDirectory: File): String =
        toStoredPath(toAbsolutePath(storedPath, oldDirectory), newDirectory);

    /** Returns the absolute path of [storedPath], resolved against [downloadsDirectory]. */
    fun toAbsolutePath(storedPath: String, downloadsDirectory: File): String =
        resolve(storedPath, downloadsDirectory).absolutePath;
}
