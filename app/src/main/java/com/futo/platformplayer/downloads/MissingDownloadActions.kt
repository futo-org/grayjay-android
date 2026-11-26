package com.futo.platformplayer.downloads

import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.states.StateDownloads
import java.io.File

/** Actions for a downloaded item whose files are not found. The item is never removed without the user. */
object MissingDownloadActions {
    fun show(activity: MainActivity, item: VideoLocal) {
        val location = StateDownloads.instance.getMissingLocation(item);
        UIDialogs.showDialog(activity, R.drawable.ic_warning_yellow, "Video file is missing",
            "Grayjay cannot find the files of \"${item.name}\" at:\n$location\n\nIf they are on an SD card, put the card in. Then use one of these actions.",
            null, -1,
            UIDialogs.Action("Remove", {
                UIDialogs.showConfirmationDialog(activity, "Remove \"${item.name}\" from downloads?", {
                    StateDownloads.instance.deleteCachedVideo(item.id);
                });
            }, UIDialogs.ActionStyle.DANGEROUS_TEXT),
            UIDialogs.Action("Find folder", { findFolder(activity) }),
            UIDialogs.Action("Download again", {
                StateDownloads.instance.downloadAgain(item);
            }, UIDialogs.ActionStyle.PRIMARY));
    }

    /** Lets the user pick the folder that has the files now (for example, after a card or folder change). */
    fun findFolder(activity: MainActivity) {
        DownloadFolderPicker.pickFolderPath(activity) { path ->
            val fixed = StateDownloads.instance.relinkMissing(File(path));
            val stillMissing = StateDownloads.instance.getDownloadedVideos().count { StateDownloads.instance.isMissing(it) };
            UIDialogs.showDialogOk(activity, R.drawable.ic_folder,
                "Found the files of $fixed downloaded videos." + (if (stillMissing > 0) "\n$stillMissing are still missing." else ""));
        };
    }
}
