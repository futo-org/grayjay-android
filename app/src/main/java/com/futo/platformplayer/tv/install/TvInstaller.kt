package com.futo.platformplayer.tv.install

import android.content.Context
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.states.StateUpdate
import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import com.futo.platformplayer.tv.sync.TvSyncSetupReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class TvInstaller(context: Context) : Closeable {
    private val _application = context.applicationContext
    private val _executable = File(context.applicationInfo.nativeLibraryDir, "libgrayjay_adb.so")
    private val _process = AdbProcess(_executable, File(context.noBackupFilesDir, "tv-adb"))

    val available: Boolean
        get() = _executable.isFile && _executable.canExecute()

    val apks: TvApkFiles
        get() {
            if (BuildConfig.IS_PLAYSTORE_BUILD)
                throw Failure(R.string.tv_install_playstore_download_required)
            return TvApkFiles(listOf(File(_application.applicationInfo.sourceDir)) +
                _application.applicationInfo.splitSourceDirs.orEmpty().map { File(it) })
        }

    suspend fun pair(endpoint: AdbEndpoint, code: String) {
        require(code.matches(Regex("[0-9]{6}")))
        start()
        val result = _process.run(listOf("pair", endpoint.serial), timeoutSeconds = 60, input = code)
        if (!result.succeeded || !result.output.contains("Successfully paired to"))
            throw Failure(R.string.tv_install_pair_failed)
    }

    suspend fun connect(endpoint: AdbEndpoint): TvInstallDevice {
        start()
        val connection = _process.run(listOf("connect", endpoint.serial))
        if (connection.output.contains("unauthorized", ignoreCase = true) || connection.output.contains("authenticate", ignoreCase = true))
            throw Failure(R.string.tv_install_authorize)
        if (!connection.succeeded || !connection.output.contains("connected to"))
            throw Failure(R.string.tv_install_connect_failed)
        val features = shell(endpoint, "pm", "list", "features")
        if (!features.contains("android.software.leanback") && !features.contains("android.hardware.type.television"))
            throw Failure(R.string.tv_install_not_tv)
        val sdk = shell(endpoint, "getprop", "ro.build.version.sdk").trim().toIntOrNull() ?: 0
        if (sdk < 28)
            throw Failure(R.string.tv_install_android_version)
        val name = shell(endpoint, "getprop", "ro.product.model").trim().take(120).ifEmpty { endpoint.host }
        return TvInstallDevice(name, endpoint.host, endpoint.port)
    }

    suspend fun connected(): Set<String> {
        start()
        val result = _process.run(listOf("devices"))
        if (!result.succeeded)
            throw Failure(R.string.tv_install_connect_failed)
        return result.output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            parts.firstOrNull()?.takeIf { parts.getOrNull(1) == "device" }
        }.toSet()
    }

    suspend fun disconnect(endpoint: AdbEndpoint) {
        start()
        _process.run(listOf("disconnect", endpoint.serial))
    }

    suspend fun packageFor(device: TvInstallDevice): Package = withContext(Dispatchers.IO) {
        val abis = shell(device.endpoint, "getprop", "ro.product.cpu.abilist").trim().split(',')
        val abi = abis.firstOrNull { it in StateUpdate.APP_SUPPORTED_ABIS }
            ?: throw Failure(R.string.tv_install_abi_unsupported)
        Package(abi, BuildConfig.IS_PLAYSTORE_BUILD || !apks.supports(abis))
    }

    suspend fun install(device: TvInstallDevice, files: TvApkFiles = apks) = withContext(Dispatchers.IO) {
        if (BuildConfig.IS_PLAYSTORE_BUILD && (files.files.size != 1 ||
            _application.packageManager.getPackageArchiveInfo(files.files.single().absolutePath, 0)?.packageName != targetPackageName))
            throw Failure(R.string.tv_install_download_invalid)
        start()
        val result = _process.run(files.installArguments(device.endpoint), timeoutSeconds = 300)
        if (!result.succeeded || result.output.lineSequence().none { it.trim() == "Success" }) {
            val message = when {
                result.output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> R.string.tv_install_signature_mismatch
                result.output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> R.string.tv_install_downgrade
                result.output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> R.string.tv_install_storage
                result.output.contains("INSTALL_FAILED_NO_MATCHING_ABIS") -> R.string.tv_install_abi_mismatch
                else -> R.string.tv_install_failed
            }
            throw Failure(message)
        }
    }

    suspend fun launch(device: TvInstallDevice) {
        start()
        val result = shell(device.endpoint, "am", "start", "-n", "${targetPackageName}/com.futo.platformplayer.tv.TvActivity")
        if (result.contains("Error", ignoreCase = true) || result.contains("Exception"))
            throw Failure(R.string.tv_install_launch_failed)
    }

    suspend fun isInstalled(device: TvInstallDevice): Boolean {
        return shell(device.endpoint, "pm", "list", "packages", targetPackageName)
            .lineSequence().any { it.trim() == "package:${targetPackageName}" }
    }

    suspend fun openSync(device: TvInstallDevice) {
        val result = shell(device.endpoint, "am", "start", "-n", "${targetPackageName}/com.futo.platformplayer.tv.TvActivity",
            "-a", TvSyncSetupReceiver.ACTION_OPEN)
        if (result.contains("Error", ignoreCase = true) || result.contains("Exception"))
            throw Failure(R.string.tv_install_launch_failed)
    }

    suspend fun pairingInfo(device: TvInstallDevice): SyncDeviceInfo? {
        val result = shell(device.endpoint, "am", "broadcast", "--receiver-foreground", "-p", targetPackageName,
            "-a", TvSyncSetupReceiver.ACTION_PAIRING)
        return TvSetupPairing.fromBroadcast(result)
    }

    suspend fun forwardSync(device: TvInstallDevice): Int {
        val result = _process.run(listOf("-s", device.endpoint.serial, "forward", "tcp:0", "tcp:${StateSync.PORT}"))
        val port = result.output.trim().toIntOrNull()
        if (!result.succeeded || port == null || port !in 1..65535)
            throw Failure(R.string.tv_install_sync_failed)
        return port
    }

    suspend fun removeForward(device: TvInstallDevice, port: Int) {
        require(port in 1..65535)
        _process.run(listOf("-s", device.endpoint.serial, "forward", "--remove", "tcp:$port"))
    }

    suspend fun openDebuggingSettings(device: TvInstallDevice) {
        start()
        val result = shell(device.endpoint, "am", "start", "-a", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
        if (result.contains("Error", ignoreCase = true) || result.contains("Exception"))
            throw Failure(R.string.tv_install_settings_failed)
    }

    override fun close() {
        _process.close()
    }

    private suspend fun start() {
        if (!available)
            throw Failure(R.string.tv_install_unavailable)
        try {
            _process.start()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw Failure(R.string.tv_install_start_failed)
        }
    }

    private suspend fun shell(endpoint: AdbEndpoint, vararg arguments: String): String {
        val result = _process.run(listOf("-s", endpoint.serial, "shell", "-T") + arguments)
        if (!result.succeeded) {
            if (result.output.contains("unauthorized", ignoreCase = true))
                throw Failure(R.string.tv_install_authorize)
            throw Failure(R.string.tv_install_connect_failed)
        }
        return result.output
    }

    companion object {
        val targetPackageName = if (BuildConfig.IS_PLAYSTORE_BUILD) "com.futo.platformplayer" else BuildConfig.APPLICATION_ID
    }

    class Failure(val messageId: Int) : Exception()

    data class Package(val abi: String, val downloadRequired: Boolean)
}
