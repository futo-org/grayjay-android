package com.futo.platformplayer

import com.futo.platformplayer.downloads.DownloadPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DownloadPathsTests {
    private val primary = "/storage/emulated/0";

    @Test
    fun treeDocumentIdToPath_mapsVolumes() {
        assertEquals("/storage/emulated/0/Movies/GJ", DownloadPaths.treeDocumentIdToPath("primary:Movies/GJ", primary));
        assertEquals("/storage/emulated/0", DownloadPaths.treeDocumentIdToPath("primary:", primary));
        assertEquals("/storage/1A2B-3C4D/Videos", DownloadPaths.treeDocumentIdToPath("1A2B-3C4D:Videos/", primary));
        assertEquals("/storage/emulated/0/Documents/x", DownloadPaths.treeDocumentIdToPath("home:x", primary));
    }

    @Test
    fun treeDocumentIdToPath_rejectsBadIds() {
        assertNull(DownloadPaths.treeDocumentIdToPath("raw:/storage/emulated/0/Download", primary));
        assertNull(DownloadPaths.treeDocumentIdToPath("msf:12", primary));
        assertNull(DownloadPaths.treeDocumentIdToPath("noseparator", primary));
        assertNull(DownloadPaths.treeDocumentIdToPath("primary:a/../../etc", primary));
    }

    @Test
    fun storedPath_isRelativeOnlyDirectlyInFolder() {
        val dir = File("/storage/1A2B-3C4D/Videos/Grayjay Downloads");
        assertEquals("abc [1920x1080].mp4", DownloadPaths.toStoredPath("${dir.absolutePath}/abc [1920x1080].mp4", dir));
        assertEquals("/data/user/0/app/files/downloads/abc.mp4", DownloadPaths.toStoredPath("/data/user/0/app/files/downloads/abc.mp4", dir));
        assertEquals("${dir.absolutePath}/sub/abc.mp4", DownloadPaths.toStoredPath("${dir.absolutePath}/sub/abc.mp4", dir));
        assertEquals("rel.mp4", DownloadPaths.toStoredPath("rel.mp4", dir));
    }

    @Test
    fun resolve_relativeFollowsFolder() {
        assertEquals(File("/a/b/x.mp4"), DownloadPaths.resolve("x.mp4", File("/a/b")));
        assertEquals(File("/c/x.mp4"), DownloadPaths.resolve("x.mp4", File("/c")));
        assertEquals(File("/abs/x.mp4"), DownloadPaths.resolve("/abs/x.mp4", File("/c")));
    }

    @Test
    fun rebase_pinsOldAndRelativizesNew() {
        val oldDir = File("/data/files/downloads");
        val newDir = File("/storage/X/Grayjay Downloads");
        assertEquals("/data/files/downloads/a.mp4", DownloadPaths.rebase("a.mp4", oldDir, newDir));
        assertEquals("b.mp4", DownloadPaths.rebase("/storage/X/Grayjay Downloads/b.mp4", oldDir, newDir));
        assertEquals("c.mp4", DownloadPaths.rebase("c.mp4", newDir, newDir));
    }

    @Test
    fun estimateBytes() {
        assertEquals(0L, DownloadPaths.estimateBytes(0, 100));
        assertEquals(0L, DownloadPaths.estimateBytes(8000, 0));
        assertEquals(100_000L, DownloadPaths.estimateBytes(8000, 100));
    }

    @Test
    fun readme_hasPathAndNotice() {
        val text = DownloadPaths.readmeText("/storage/X/Grayjay Downloads");
        org.junit.Assert.assertTrue(text.contains("/storage/X/Grayjay Downloads"));
        org.junit.Assert.assertTrue(text.contains(DownloadPaths.UNINSTALL_NOTICE));
    }
}
