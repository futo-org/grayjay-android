package com.futo.platformplayer.views

import android.content.Context
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.PlaylistImportMerge
import com.futo.platformplayer.states.StatePlaylists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Asks the user how to import a playlist that matches a local one, with a preview of the changes.
 * Call from a coroutine; the dialogs show on the main thread.
 */
object PlaylistImportPrompt {
    /** Imports [imported]. Returns the saved playlist, or null if the user cancelled. */
    suspend fun import(context: Context, imported: Playlist): Playlist? {
        val plan = StatePlaylists.instance.planImport(imported);
        val target = when {
            plan.target != null -> plan.target;
            plan.nameMatches.isEmpty() -> return withContext(Dispatchers.IO) { StatePlaylists.instance.applyImport(imported, null) };
            else -> when (askNameMatch(context, imported, plan.nameMatches.size)) {
                NameChoice.UPDATE -> plan.nameMatches.first();
                NameChoice.NEW -> return withContext(Dispatchers.IO) { StatePlaylists.instance.applyImport(imported, null) };
                NameChoice.CANCEL -> return null;
            }
        };

        val preview = plan.previewFor(target);
        if (!preview.hasChanges || confirmPreview(context, target.name, preview))
            return withContext(Dispatchers.IO) { StatePlaylists.instance.applyImport(imported, target) };
        return null;
    }

    private enum class NameChoice { UPDATE, NEW, CANCEL }

    private suspend fun askNameMatch(context: Context, imported: Playlist, count: Int): NameChoice = ask(context) { done ->
        UIDialogs.showDialog(context, R.drawable.ic_playlist_add, "$count local playlists are named \"${imported.name}\"",
            "Update the newest one with this import, or save the import as a new playlist?",
            null, -1,
            UIDialogs.Action("Cancel", { done(NameChoice.CANCEL) }),
            UIDialogs.Action("New playlist", { done(NameChoice.NEW) }),
            UIDialogs.Action("Update newest", { done(NameChoice.UPDATE) }, UIDialogs.ActionStyle.PRIMARY)
        ).setOnCancelListener { done(NameChoice.CANCEL) };
    }

    private suspend fun confirmPreview(context: Context, name: String, preview: PlaylistImportMerge.Preview<*>): Boolean = ask(context) { done ->
        UIDialogs.showDialog(context, R.drawable.ic_playlist_add, "Update \"$name\"",
            "$name: ${PlaylistImportMerge.describe(preview)}\n\nThe playlist will follow the source: new videos are added, removed videos are removed, and the source order is used.",
            null, -1,
            UIDialogs.Action("Cancel", { done(false) }),
            UIDialogs.Action("Apply", { done(true) }, UIDialogs.ActionStyle.PRIMARY)
        ).setOnCancelListener { done(false) };
    }

    private suspend fun <T> ask(context: Context, show: (done: (T) -> Unit) -> Unit): T = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var finished = false;
            show { value ->
                if (!finished) {
                    finished = true;
                    cont.resume(value);
                }
            };
        }
    };
}
