package com.futo.platformplayer

import com.futo.platformplayer.downloads.DownloadMover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadMoverTests {
    @get:Rule val tmp = TemporaryFolder();

    private fun file(dir: File, name: String, size: Int, seed: Int = 1): File {
        dir.mkdirs();
        return File(dir, name).apply { writeBytes(ByteArray(size) { ((it * 31 + seed) % 251).toByte() }) };
    }

    @Test
    fun copy_thenDeleteSource() {
        val src = file(tmp.newFolder("old"), "a.mp4", 300_000);
        val mover = DownloadMover(File(tmp.root, "new/Grayjay Downloads"));
        var bytes = 0L;
        val target = mover.copyIntoTarget(src, { bytes += it });
        assertEquals(300_000L, bytes);
        assertTrue(src.exists());
        assertTrue(DownloadMover.sameContent(src, target));
        assertFalse(File(target.parentFile, "a.mp4" + DownloadMover.TEMP_SUFFIX).exists());
        assertTrue(mover.deleteSource(src, target));
        assertFalse(src.exists());
        assertTrue(target.exists());
    }

    @Test
    fun resume_skipsFinishedCopy() {
        val src = file(tmp.newFolder("old"), "a.mp4", 200_000);
        val targetDir = tmp.newFolder("new");
        val earlier = src.copyTo(File(targetDir, "a.mp4"));
        val stamp = earlier.lastModified() - 10_000;
        earlier.setLastModified(stamp);
        val target = DownloadMover(targetDir).copyIntoTarget(src);
        assertEquals(earlier, target);
        assertEquals(stamp, target.lastModified());
    }

    @Test
    fun foreignFileWithSameName_isKept() {
        val src = file(tmp.newFolder("old"), "a.mp4", 100_000, seed = 1);
        val targetDir = tmp.newFolder("new");
        val foreign = file(targetDir, "a.mp4", 100_000, seed = 7);
        val target = DownloadMover(targetDir).copyIntoTarget(src);
        assertEquals("a (1).mp4", target.name);
        assertTrue(foreign.exists());
        assertFalse(DownloadMover.sameContent(src, foreign));
    }

    @Test
    fun stop_keepsSourceAndRemovesTemp() {
        val src = file(tmp.newFolder("old"), "a.mp4", 3_000_000);
        val targetDir = tmp.newFolder("new");
        var calls = 0;
        try {
            DownloadMover(targetDir).copyIntoTarget(src, {}, { calls++ > 1 });
            fail("expected stop");
        } catch (ex: InterruptedException) { }
        assertTrue(src.exists());
        assertEquals(0, targetDir.listFiles()!!.size);
    }

    @Test
    fun deleteSource_refusesWhenTargetDiffers() {
        val src = file(tmp.newFolder("old"), "a.mp4", 1000, seed = 1);
        val other = file(tmp.newFolder("new"), "a.mp4", 1000, seed = 2);
        assertFalse(DownloadMover(other.parentFile!!).deleteSource(src, other));
        assertTrue(src.exists());
        assertFalse(DownloadMover(src.parentFile!!).deleteSource(src, src));
    }

    @Test
    fun volumeRoot_andBytesNeeded() {
        assertEquals("/storage/emulated/0", DownloadMover.volumeRoot("/storage/emulated/0/Movies/x.mp4"));
        assertEquals("/storage/1A2B-3C4D", DownloadMover.volumeRoot("/storage/1A2B-3C4D/Grayjay Downloads/x.mp4"));
        assertEquals("/data", DownloadMover.volumeRoot("/data/user/0/app/files/downloads/x.mp4"));
        val src = file(tmp.newFolder("old"), "a.mp4", 1234);
        assertEquals(0L, DownloadMover.bytesNeeded(listOf(src), File(tmp.root, "x")));
    }
}
