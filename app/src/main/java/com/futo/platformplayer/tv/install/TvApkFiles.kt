package com.futo.platformplayer.tv.install

import java.io.File
import java.util.zip.ZipFile

class TvApkFiles(val files: List<File>) {
    fun supports(abis: List<String>): Boolean {
        val libraries = mutableMapOf<String, MutableSet<String>>()
        for (file in files) {
            ZipFile(file).use { zip ->
                for (entry in zip.entries()) {
                    val parts = entry.name.split('/')
                    if (parts.size == 3 && parts[0] == "lib" && parts[2].endsWith(".so"))
                        libraries.getOrPut(parts[2]) { mutableSetOf() }.add(parts[1])
                }
            }
        }
        return libraries.isEmpty() || abis.any { abi -> libraries.values.all { abi in it } }
    }

    fun installArguments(endpoint: AdbEndpoint): List<String> {
        require(files.isNotEmpty() && files.all { it.isFile }) { "Installed APK files are missing" }
        return listOf("-s", endpoint.serial, if (files.size > 1) "install-multiple" else "install", "-r", "--no-incremental") +
            files.map { it.absolutePath }
    }
}
