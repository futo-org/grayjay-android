package com.futo.platformplayer.tv.install

import java.io.File
import java.util.zip.ZipFile

class TvApkFiles(val files: List<File>) {
    fun supports(abis: List<String>): Boolean {
        val architectures = mutableSetOf<String>()
        for (file in files) {
            ZipFile(file).use { zip ->
                for (entry in zip.entries()) {
                    val parts = entry.name.split('/')
                    if (parts.size == 3 && parts[0] == "lib" && parts[2].endsWith(".so"))
                        architectures.add(parts[1])
                }
            }
        }
        return architectures.isEmpty() || abis.any { it in architectures }
    }

    fun installArguments(endpoint: AdbEndpoint): List<String> {
        require(files.isNotEmpty() && files.all { it.isFile }) { "Installed APK files are missing" }
        return listOf("-s", endpoint.serial, if (files.size > 1) "install-multiple" else "install", "-r", "--no-incremental") +
            files.map { it.absolutePath }
    }
}
