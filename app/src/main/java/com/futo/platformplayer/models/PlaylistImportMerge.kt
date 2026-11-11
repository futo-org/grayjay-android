package com.futo.platformplayer.models

/**
 * Rules for a playlist import that can update a local playlist instead of making a copy.
 * Pure functions, no Android state.
 */
object PlaylistImportMerge {
    /** Removes repeat items (same key), keeps the first one. Returns the list and the number removed. */
    fun <T> dedupe(items: List<T>, key: (T) -> String): Pair<List<T>, Int> {
        val seen = HashSet<String>();
        val result = items.filter { seen.add(key(it)) };
        return Pair(result, items.size - result.size);
    }

    class Preview<T>(val result: List<T>, val added: Int, val removed: Int, val repeatsSkipped: Int) {
        val hasChanges: Boolean get() = added > 0 || removed > 0;
    }

    /**
     * The local list follows the imported (source) list: new items are added, items no longer
     * in the source are removed, and the source order is used. Repeats are skipped.
     */
    fun <T> merge(existing: List<T>, imported: List<T>, key: (T) -> String): Preview<T> {
        val (deduped, repeats) = dedupe(imported, key);
        val existingKeys = existing.map(key).toHashSet();
        val importedKeys = deduped.map(key).toHashSet();
        return Preview(
            result = deduped,
            added = importedKeys.count { it !in existingKeys },
            removed = existingKeys.count { it !in importedKeys },
            repeatsSkipped = repeats
        );
    }

    /** Text for the preview dialog, for example "+8 new, -0 removed, 2 repeats skipped". */
    fun describe(preview: Preview<*>): String =
        "+${preview.added} new, -${preview.removed} removed, ${preview.repeatsSkipped} repeats skipped";

    class LinkCandidates<P>(val bySource: P?, val byName: List<P>);

    /**
     * Local playlists that an import of [sourceUrl] / [name] can update.
     * First a match on the source URL; else playlists with the same name and no source URL yet.
     */
    fun <P> findLinked(playlists: List<P>, sourceUrl: String?, name: String, sourceOf: (P) -> String?, nameOf: (P) -> String): LinkCandidates<P> {
        val normalized = sourceUrl?.let { normalizeUrl(it) };
        val bySource = if (normalized == null) null else playlists.firstOrNull { p -> sourceOf(p)?.let { normalizeUrl(it) } == normalized };
        if (bySource != null)
            return LinkCandidates(bySource, listOf());
        return LinkCandidates(null, playlists.filter { sourceOf(it) == null && nameOf(it).trim() == name.trim() });
    }

    /** Same playlist URL in other forms: scheme, "www."/"m.", trailing "/", extra query values (YouTube "list" kept). */
    fun normalizeUrl(url: String): String {
        var u = url.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").removePrefix("m.");
        val query = u.substringAfter('?', "");
        u = u.substringBefore('?').trimEnd('/');
        val list = query.split('&').firstOrNull { it.startsWith("list=") };
        return if (list != null) "$u?$list" else u;
    }
}
