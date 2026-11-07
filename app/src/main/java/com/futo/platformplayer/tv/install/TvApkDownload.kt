package com.futo.platformplayer.tv.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.states.StateUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class TvApkDownload(private val context: Context) {
    suspend fun withApk(abi: String, onProgress: (Progress) -> Unit, install: suspend (TvApkFiles) -> Unit) {
        withApk(StateUpdate.getApkUrl(BuildConfig.VERSION_CODE, abi), abi, onProgress, install)
    }

    private suspend fun withApk(url: String, abi: String, onProgress: (Progress) -> Unit, install: suspend (TvApkFiles) -> Unit) = coroutineScope {
        require(abi in StateUpdate.APP_SUPPORTED_ABIS)
        val file = File(context.cacheDir, "tv-install-${UUID.randomUUID()}.apk")
        val call = AtomicReference<Call>()
        val client = ManagedHttpClient().apply {
            setTimeout(15_000)
            setReadTimeout(30_000)
            setCallTimeout(15 * 60_000)
        }
        val request = ManagedHttpClient.Request(url, "GET", null)
        val scope = this
        request.onCallCreated.subscribe { created ->
            call.set(created)
            if (!scope.isActive) created.cancel()
        }
        val download = async(Dispatchers.IO) {
            client.execute(request).use { response ->
                if (response.code == 404)
                    throw TvInstaller.Failure(R.string.tv_install_download_unavailable)
                if (!response.isOk)
                    throw TvInstaller.Failure(R.string.tv_install_download_failed)
                val body = response.body ?: throw TvInstaller.Failure(R.string.tv_install_download_failed)
                val total = body.contentLength()
                if (total > 0 && total > context.cacheDir.usableSpace)
                    throw TvInstaller.Failure(R.string.tv_install_download_storage)
                var received = 0L
                var lastUpdate = 0L
                onProgress(Progress(0, total))
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= 500) {
                                onProgress(Progress(received, total))
                                lastUpdate = now
                            }
                        }
                    }
                }
                if (received == 0L || (total >= 0 && received != total))
                    throw TvInstaller.Failure(R.string.tv_install_download_failed)
                onProgress(Progress(received, total))
            }
            validate(file, abi)
        }
        try {
            download.await()
            ensureActive()
            install(TvApkFiles(listOf(file)))
        } finally {
            call.get()?.cancel()
            withContext(NonCancellable + Dispatchers.IO) {
                download.join()
                file.delete()
                client.close()
            }
        }
    }

    private fun validate(file: File, abi: String) {
        val manager = context.packageManager
        val apk = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw TvInstaller.Failure(R.string.tv_install_download_invalid)
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = apk.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        if (apk.packageName != context.packageName || apk.longVersionCode != BuildConfig.VERSION_CODE.toLong() ||
            signers.isEmpty() || signers != installed.signingInfo?.apkContentsSigners?.toSet() ||
            !TvApkFiles(listOf(file)).supports(listOf(abi)))
            throw TvInstaller.Failure(R.string.tv_install_download_invalid)
    }

    data class Progress(val received: Long, val total: Long)
}
