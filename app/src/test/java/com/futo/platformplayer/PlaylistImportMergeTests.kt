package com.futo.platformplayer

import com.futo.platformplayer.models.PlaylistImportMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportMergeTests {
    private val id: (String) -> String = { it };

    @Test
    fun dedupe_keepsFirstAndCounts() {
        val (list, repeats) = PlaylistImportMerge.dedupe(listOf("a", "b", "b", "c", "a"), id);
        assertEquals(listOf("a", "b", "c"), list);
        assertEquals(2, repeats);
    }

    @Test
    fun merge_followsSource() {
        val p = PlaylistImportMerge.merge(listOf("a", "b", "x"), listOf("c", "a", "b", "b"), id);
        assertEquals(listOf("c", "a", "b"), p.result);
        assertEquals(1, p.added);
        assertEquals(1, p.removed);
        assertEquals(1, p.repeatsSkipped);
        assertTrue(p.hasChanges);
        assertEquals("+1 new, -1 removed, 1 repeats skipped", PlaylistImportMerge.describe(p));
    }

    @Test
    fun merge_noChanges() {
        val p = PlaylistImportMerge.merge(listOf("a", "b"), listOf("a", "b"), id);
        assertFalse(p.hasChanges);
    }

    data class P(val name: String, val source: String?);

    @Test
    fun findLinked_prefersSourceThenUnlinkedName() {
        val list = listOf(
            P("Favorites", null),
            P("Favorites", "https://www.youtube.com/playlist?list=PL1&si=abc"),
            P("Mix", null), P("Mix", null), P("Other", "https://youtube.com/playlist?list=PL9"));
        val bySource = PlaylistImportMerge.findLinked(list, "https://m.youtube.com/playlist?list=PL1", "Renamed", { it.source }, { it.name });
        assertEquals(list[1], bySource.bySource);
        val byName = PlaylistImportMerge.findLinked(list, "https://youtube.com/playlist?list=PL2", "Mix", { it.source }, { it.name });
        assertNull(byName.bySource);
        assertEquals(2, byName.byName.size);
        val linkedNameIgnored = PlaylistImportMerge.findLinked(list, "https://youtube.com/playlist?list=PL3", "Other", { it.source }, { it.name });
        assertEquals(0, linkedNameIgnored.byName.size);
    }

    @Test
    fun normalizeUrl() {
        assertEquals("youtube.com/playlist?list=PL1", PlaylistImportMerge.normalizeUrl("https://www.youtube.com/playlist?si=x&list=PL1"));
        assertEquals("example.com/p/1", PlaylistImportMerge.normalizeUrl("http://example.com/p/1/"));
    }
}
