package com.stratum.core.ui.share

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ShareCacheTest {

    @Nested
    @DisplayName("sanitizeShareFileName")
    inner class Sanitize {

        @Test
        @DisplayName("Replaces path separators and traversal with underscores")
        fun stripsPathBits() {
            assertEquals("a_b_c", sanitizeShareFileName("a/b\\c"))
            // Dots are allowed (file extensions); slashes become underscores.
            assertEquals("foo_.._bar", sanitizeShareFileName("../foo/../bar"))
            assertEquals("f0_s1", sanitizeShareFileName("f0::s1"))
        }

        @Test
        @DisplayName("Falls back when blank after sanitize")
        fun blankFallback() {
            assertEquals("share", sanitizeShareFileName("///"))
            assertEquals("custom", sanitizeShareFileName("   ", fallback = "custom"))
        }

        @Test
        @DisplayName("Keeps safe alphanumerics and common punctuation")
        fun keepsSafe() {
            assertEquals("workout_share_uuid.png", sanitizeShareFileName("workout_share_uuid.png"))
        }
    }

    @Nested
    @DisplayName("pruneShareCacheDir")
    inner class Prune {

        @TempDir
        lateinit var tempDir: File

        @Test
        @DisplayName("Always drops files past keepLast regardless of age")
        fun dropsBeyondKeepLastAlways() {
            val now = 2_000_000_000_000L
            val files =
                (0 until 5).map { index ->
                    File(tempDir, "f$index.png").also { file ->
                        file.writeText("x")
                        // All young — previous policy would keep all of them.
                        file.setLastModified(now - index * 1_000L)
                    }
                }

            pruneShareCacheDir(
                exportDir = tempDir,
                nowMillis = now,
                maxAgeMs = SHARE_CACHE_MAX_AGE_MS,
                keepLast = 3,
            )

            assertTrue(files[0].exists())
            assertTrue(files[1].exists())
            assertTrue(files[2].exists())
            assertFalse(files[3].exists())
            assertFalse(files[4].exists())
        }

        @Test
        @DisplayName("Drops aged files even inside keepLast window")
        fun dropsAgedInsideKeepLast() {
            val now = 2_000_000_000_000L
            val maxAgeMs = 60_000L
            val young =
                File(tempDir, "young.png").also {
                    it.writeText("x")
                    it.setLastModified(now - 1_000L) // within maxAge
                }
            val old =
                File(tempDir, "old.png").also {
                    it.writeText("x")
                    it.setLastModified(now - 10L * 24 * 60 * 60 * 1000L)
                }

            pruneShareCacheDir(
                exportDir = tempDir,
                nowMillis = now,
                maxAgeMs = maxAgeMs,
                keepLast = 5,
            )

            assertTrue(young.exists())
            assertFalse(old.exists())
        }

        @Test
        @DisplayName("Empty directory is a no-op")
        fun emptyDir() {
            pruneShareCacheDir(tempDir)
            assertEquals(0, tempDir.listFiles()?.size ?: 0)
        }
    }
}
