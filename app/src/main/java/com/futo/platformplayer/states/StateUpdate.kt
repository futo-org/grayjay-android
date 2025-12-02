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

        fun getApkFile(context: Context, version: Int): File {
            val dir = File(context.filesDir, "updates");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return File(dir, "app-${DESIRED_ABI}-${version}.apk");
        }

        fun getPartialApkFile(context: Context, version: Int): File {
            val dir = File(context.filesDir, "updates");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return File(dir, "app-${DESIRED_ABI}-${version}.apk.part");
        }

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}