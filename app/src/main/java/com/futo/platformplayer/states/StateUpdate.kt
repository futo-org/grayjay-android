package com.futo.platformplayer.states

import android.content.Context
import android.os.Build
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.copyToOutputStream
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class StateUpdate {
    private var _backgroundUpdateFinished = false;
    private var _gettingOrDownloadingLastApk = false;
    private var _shouldBackgroundUpdate = false;
    private val _lockObject = Object();

    private fun getOrDownloadLastApkFile(filesDir: File): File? {
        try {
            Logger.i(TAG, "Started getting or downloading latest APK file.");

            if (!_shouldBackgroundUpdate) {
                Logger.i(TAG, "Update download cancelled 1.");
                return null;
            }

            Logger.i(TAG, "Started background update download.");
            val client = ManagedHttpClient();
            val latestVersion = downloadVersionCode(client);
            if (!_shouldBackgroundUpdate) {
                Logger.i(TAG, "Update download cancelled 2.");
                return null;
            }

            if (latestVersion != null) {
                val currentVersion = BuildConfig.VERSION_CODE;
                Logger.i(TAG, "Current version ${currentVersion} latest version ${latestVersion}.");

                if (latestVersion <= currentVersion) {
                    Logger.i(TAG, "Already up to date.");
                    _backgroundUpdateFinished = true;
                    return null;
                }

                val outputDirectory = File(filesDir, "autoupdate");
                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs();
                }

                if (!_shouldBackgroundUpdate) {
                    Logger.i(TAG, "Update download cancelled 3.");
                    return null;
                }

                val apkOutputFile = File(outputDirectory, "last_version.apk");
                val versionOutputFile = File(outputDirectory, "last_version.txt");

                var cachedVersionInvalid = false;
                if (!versionOutputFile.exists() || !apkOutputFile.exists()) {
                    Logger.i(TAG, "No downloaded version exists.");
                    cachedVersionInvalid = true;
                } else {
                    try {
                        val downloadedVersion = versionOutputFile.readText().toInt();
                        Logger.i(TAG, "Downloaded version is $downloadedVersion.");
                        if (downloadedVersion != latestVersion) {
                            Logger.i(TAG, "Downloaded version is not newest version.");
                            cachedVersionInvalid = true;
                        }
                    }
                    catch(ex: Throwable) {
                        Logger.w(TAG, "Deleted version file as it was inaccessible");
                        versionOutputFile.delete();
                        cachedVersionInvalid = true;
                    }
                }

                if (!_shouldBackgroundUpdate) {
                    Logger.i(TAG, "Update download cancelled 4.");
                    return null;
                }

                if (cachedVersionInvalid) {
                    Logger.i(TAG, "Downloading new APK to '${apkOutputFile.path}'...");
                    downloadApkToFile(client, apkOutputFile) { !_shouldBackgroundUpdate };
                    versionOutputFile.writeText(latestVersion.toString());

                    Logger.i(TAG, "Downloaded APK to '${apkOutputFile.path}'.");
                } else {
                    Logger.i(TAG, "Latest APK is already downloaded in '${apkOutputFile.path}'...");
                }

                if (!_shouldBackgroundUpdate) {
                    Logger.i(TAG, "Update download cancelled 5.");
                    return null;
                }

                return apkOutputFile;
            } else {
                Logger.w(TAG, "Failed to retrieve version from version URL.");
                return null;
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to download APK.", e);
            return null;
        } finally {
            _gettingOrDownloadingLastApk = false;
        }
    }

    fun setShouldBackgroundUpdate(shouldBackgroundUpdate: Boolean) {
        synchronized (_lockObject) {
            if (_backgroundUpdateFinished) {
                _shouldBackgroundUpdate = false;
                return;
            }

            _shouldBackgroundUpdate = shouldBackgroundUpdate;
            if (shouldBackgroundUpdate && !_gettingOrDownloadingLastApk) {
                Logger.i(TAG, "Auto Updating in Background");

                _gettingOrDownloadingLastApk = true;
                StateApp.withContext { context ->
                    StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                        try {
                            val file = getOrDownloadLastApkFile(context.filesDir);
                            if (file == null) {
                                Logger.i(TAG, "Failed to get or download update.");
                                return@launch;
                            }

                            withContext(Dispatchers.Main) {
                                try {
                                    context.let { c ->
                                        _backgroundUpdateFinished = true;
                                        UIDialogs.showInstallDownloadedUpdateDialog(c, file);
                                    };
                                    Logger.i(TAG, "Showing install dialog for '${file.path}'.");
                                } catch (e: Throwable) {
                                    context.let { c -> UIDialogs.toast(c, "Failed to show update dialog"); };
                                    Logger.w(TAG, "Error occurred in update dialog.", e);
                                }
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to get last downloaded APK file.", e)
                        }
                    }
                }
            }
        }
    }

    suspend fun checkForUpdates(context: Context, showUpToDateToast: Boolean, hideExceptionButtons: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val client = ManagedHttpClient();
            val latestVersion = downloadVersionCode(client);

            if (latestVersion != null) {
                val currentVersion = BuildConfig.VERSION_CODE;
                Logger.i(TAG, "Current version ${currentVersion} latest version ${latestVersion}.");

                if (latestVersion > currentVersion) {
                    withContext(Dispatchers.Main) {
                        try {
                            UIDialogs.showUpdateAvailableDialog(context, latestVersion, hideExceptionButtons);
                        } catch (e: Throwable) {
                            UIDialogs.toast(context, "Failed to show update dialog");
                            Logger.w(TAG, "Error occurred in update dialog.");
                        }
                    }
                } else {
                    if (showUpToDateToast) {
                        withContext(Dispatchers.Main) {
                            UIDialogs.toast(context, "Already on latest version");
                        }
                    }
                }
            } else {
                Logger.w(TAG, "Failed to retrieve version from version URL.");

                withContext(Dispatchers.Main) {
                    UIDialogs.toast(context, "Failed to retrieve version");
                }
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to check for updates.", e);
            android.util.Log.e(TAG, "Failed to check for updates.", e);
            withContext(Dispatchers.Main) {
                UIDialogs.toast(context, "Failed to check for updates\n" + e.message);
            }
        }
    }

    private fun downloadApkToFile(client: ManagedHttpClient, destinationFile: File, isCancelled: (() -> Boolean)? = null) {
        var apkStream: InputStream? = null;
        var outputStream: OutputStream? = null;

        try {
            val response = client.get(APK_URL);
            if (response.isOk && response.body != null) {
                apkStream = response.body.byteStream();
                outputStream = destinationFile.outputStream();
                apkStream.copyToOutputStream(outputStream, isCancelled);
                apkStream.close();
                outputStream.close();
            }
        } finally {
            apkStream?.close();
            outputStream?.close();
        }
    }

    fun downloadVersionCode(client: ManagedHttpClient): Int? {
        val response = client.get(VERSION_URL);
        if (!response.isOk || response.body == null) {
            return null;
        }

        return response.body.string().trim().toInt();
    }

    fun downloadChangelog(client: ManagedHttpClient, version: Int): String? {
        val response = client.get("${CHANGELOG_BASE_URL}/${version}");
        if (!response.isOk || response.body == null) {
            return null;
        }

        return response.body.string().trim();
    }

    companion object {
        private val TAG = "StateUpdate";

        private var _instance : StateUpdate? = null;
        val instance : StateUpdate
            get(){
            if(_instance == null)
                _instance = StateUpdate();
            return _instance!!;
        };

        val APP_SUPPORTED_ABIS = arrayOf("x86", "x86_64", "arm64-v8a", "armeabi-v7a");
        val DESIRED_ABI: String get() {
            for (i in 0 until Build.SUPPORTED_ABIS.size) {
                val abi = Build.SUPPORTED_ABIS[i];
                if (APP_SUPPORTED_ABIS.contains(abi)) {
                    return abi;
                }
            }

            throw Exception("App is not compatible. Supported ABIS: ${Build.SUPPORTED_ABIS.joinToString()}}.");
        };
        val VERSION_URL = if (BuildConfig.IS_UNSTABLE_BUILD) {
            "https://releases.grayjay.app/version-unstable.txt"
        } else {
            "https://releases.grayjay.app/version.txt"
        }
        val APK_URL = if (BuildConfig.IS_UNSTABLE_BUILD) {
            "https://releases.grayjay.app/app-$DESIRED_ABI-release-unstable.apk"
        } else {
            "https://releases.grayjay.app/app-$DESIRED_ABI-release.apk"
        }
        val CHANGELOG_BASE_URL = "https://releases.grayjay.app/changelogs";

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}