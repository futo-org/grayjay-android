package com.futo.platformplayer.downloads

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.SettingsFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lets the user pick a folder (phone or SD card) for downloads.
 * Uses "All files access" so the download, player, cast and cleanup code can keep using java.io.File.
 */
object DownloadFolderPicker {
    private const val TAG = "DownloadFolderPicker";
    private const val CODE_ALL_FILES_ACCESS = 97;
    private const val CODE_PICK_FOLDER = 96;

    fun describeCurrentFolder(): String {
        val state = StateDownloads.instance;
        if (state.getPickedFolder() == null)
            return "Now: app storage (private to Grayjay)";
        val directory = state.getDownloadsDirectory();
        return "Now: ${directory.absolutePath}" + (if (state.isDownloadsDirectoryAvailable()) "" else " (not available)") +
            "\n" + DownloadPaths.UNINSTALL_NOTICE;
    }

    /** Asks for "All files access" if necessary, then opens the system folder picker. */
    fun pick(activity: MainActivity) {
        pickFolderPath(activity) { path -> confirmFolder(activity, path) };
    }

    /** Gets a writable folder path from the user. Calls [onPicked] only with a usable folder. */
    fun pickFolderPath(activity: MainActivity, onPicked: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            UIDialogs.showDialogOk(activity, R.drawable.ic_warning_yellow, "A download folder needs Android 11 or later.");
            return;
        }
        if (Environment.isExternalStorageManager())
            openPicker(activity, onPicked);
        else
            requestAllFilesAccess(activity) { openPicker(activity, onPicked) };
    }

    fun useAppStorage(activity: MainActivity) {
        if (StateDownloads.instance.getPickedFolder() == null) {
            UIDialogs.toast(activity, "Downloads already go to app storage");
            return;
        }
        UIDialogs.showConfirmationDialog(activity, "Put new downloads in app storage again? Downloads that you have now stay where they are.", {
            StateDownloads.instance.changeDownloadFolder(null);
            SettingsFragment.currentView?.reloadSettings();
            UIDialogs.toast(activity, "New downloads go to app storage");
        });
    }

    private fun requestAllFilesAccess(activity: MainActivity, onGranted: () -> Unit) {
        UIDialogs.showDialog(activity, R.drawable.ic_security, "\"All files access\" is necessary",
            "Grayjay needs \"All files access\" to save downloads in a folder that you pick. On the next screen, turn on \"Allow access to manage all files\", then go back.",
            null, 0,
            UIDialogs.Action("Cancel", {}),
            UIDialogs.Action("Open settings", {
                val onResult: (androidx.activity.result.ActivityResult) -> Unit = {
                    if (Environment.isExternalStorageManager())
                        onGranted();
                    else
                        UIDialogs.toast(activity, "\"All files access\" is off. The download folder did not change.");
                };
                try {
                    activity.launchForResult(Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.packageName)), CODE_ALL_FILES_ACCESS, onResult);
                } catch (ex: ActivityNotFoundException) {
                    activity.launchForResult(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), CODE_ALL_FILES_ACCESS, onResult);
                }
            }, UIDialogs.ActionStyle.PRIMARY));
    }

    private fun openPicker(activity: MainActivity, onPicked: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        activity.launchForResult(intent, CODE_PICK_FOLDER) { result ->
            if (result.resultCode != Activity.RESULT_OK)
                return@launchForResult;
            val uri = result.data?.data ?: return@launchForResult;
            val path = treeUriToPath(uri);
            if (path == null) {
                UIDialogs.showDialogOk(activity, R.drawable.ic_warning_yellow, "Grayjay cannot use this folder. Pick a folder on the phone storage or the SD card.");
                return@launchForResult;
            }
            val folder = File(path);
            if (!folder.isDirectory || !folder.canWrite()) {
                UIDialogs.showDialogOk(activity, R.drawable.ic_warning_yellow, "Grayjay cannot write to this folder:\n$path");
                return@launchForResult;
            }
            onPicked(folder.absolutePath);
        };
    }

    private fun confirmFolder(activity: MainActivity, path: String) {
        val target = File(path, DownloadPaths.SUBFOLDER_NAME);
        UIDialogs.showDialog(activity, R.drawable.ic_folder, "Download folder",
            "New downloads go to:\n${target.absolutePath}\n\n${DownloadPaths.UNINSTALL_NOTICE}",
            null, 0,
            UIDialogs.Action("Cancel", {}),
            UIDialogs.Action("Use this folder", {
                StateDownloads.instance.changeDownloadFolder(path);
                SettingsFragment.currentView?.reloadSettings();
                Logger.i(TAG, "Download folder set to [$path]");
                onFolderChanged(activity);
            }, UIDialogs.ActionStyle.PRIMARY));
    }

    /** Called after the download folder changed. */
    private fun onFolderChanged(activity: MainActivity) {
        val outside = StateDownloads.instance.getItemsOutsideDownloadsDirectory().size;
        if (outside == 0) {
            UIDialogs.toast(activity, "Download folder changed");
            return;
        }
        UIDialogs.showDialog(activity, R.drawable.ic_folder, "Move existing downloads?",
            "$outside downloaded videos are in the old folder. They still play from there. Move them to the new folder now? You can also do this later in Settings > Downloads.",
            null, 0,
            UIDialogs.Action("Later", {}),
            UIDialogs.Action("Move", { moveExisting(activity) }, UIDialogs.ActionStyle.PRIMARY));
    }

    fun describeMoveState(): String {
        val outside = StateDownloads.instance.getItemsOutsideDownloadsDirectory().size;
        return if (outside == 0) "All downloads are in the download folder" else "$outside downloaded videos are outside the download folder";
    }

    /** Moves all downloads into the current download folder, with a progress dialog. */
    fun moveExisting(activity: MainActivity) {
        val count = StateDownloads.instance.getItemsOutsideDownloadsDirectory().size;
        if (count == 0) {
            UIDialogs.toast(activity, "All downloads are in the download folder");
            return;
        }
        UIDialogs.showDialogProgress(activity) { dialog ->
            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { dialog.setText("Moving downloads (0/$count)"); };
                var lastUpdate = 0L;
                val result = StateDownloads.instance.moveDownloadsToCurrentFolder({ index, total, fraction ->
                    val now = System.currentTimeMillis();
                    if (now - lastUpdate > 200) {
                        lastUpdate = now;
                        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                            dialog.setText("Moving downloads (${index + 1}/$total)");
                            dialog.setProgress(fraction);
                        };
                    }
                });
                withContext(Dispatchers.Main) {
                    dialog.dismiss();
                    SettingsFragment.currentView?.reloadSettings();
                    if (result.error == null)
                        UIDialogs.showDialogOk(activity, R.drawable.ic_folder, "Moved ${result.movedItems} downloaded videos to:\n${StateDownloads.instance.getDownloadsDirectory().absolutePath}");
                    else
                        UIDialogs.showDialogOk(activity, R.drawable.ic_warning_yellow, "Moved ${result.movedItems} of ${result.totalItems} downloaded videos, then stopped:\n${result.error.message}\n\nNo file was lost. Use \"Move existing downloads\" again to continue.");
                };
            };
        };
    }

    /** Converts a tree URI from the system folder picker to a file path, or null. */
    fun treeUriToPath(uri: Uri): String? {
        if (uri.authority != DownloadPaths.EXTERNAL_STORAGE_AUTHORITY)
            return null;
        val documentId = try { DocumentsContract.getTreeDocumentId(uri) } catch (ex: Throwable) { return null };
        return DownloadPaths.treeDocumentIdToPath(documentId, Environment.getExternalStorageDirectory().absolutePath);
    }
}
