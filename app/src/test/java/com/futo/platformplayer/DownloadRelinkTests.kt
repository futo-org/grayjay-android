package com.futo.platformplayer

import com.futo.platformplayer.downloads.DownloadPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadRelinkTests {
    @get:Rule val tmp = TemporaryFolder();

    @Test
    fun findByName_prefersGrayjaySubfolder() {
        val root = tmp.newFolder("card");
        val sub = File(root, DownloadPaths.SUBFOLDER_NAME).apply { mkdirs() };
        val inSub = File(sub, "a.mp4").apply { writeText("x") };
        File(root, "a.mp4").writeText("y");
        File(root, "b.mp4").writeText("z");
        assertEquals(inSub, DownloadPaths.findByName("a.mp4", root));
        assertEquals(File(root, "b.mp4"), DownloadPaths.findByName("b.mp4", root));
        assertNull(DownloadPaths.findByName("c.mp4", root));
    }
}
