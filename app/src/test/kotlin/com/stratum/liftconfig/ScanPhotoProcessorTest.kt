package com.stratum.liftconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ScanPhotoProcessorTest {

    private fun codec(
        size: Pair<Int, Int>? = 2000 to 1000,
        encoded: ByteArray? = ByteArray(100),
        seenSample: MutableList<Int> = mutableListOf(),
    ): PhotoCodec =
        object : PhotoCodec {
            override fun probeSize(bytes: ByteArray): Pair<Int, Int>? = size

            override fun downscale(bytes: ByteArray, sampleSize: Int): ByteArray? {
                seenSample += sampleSize
                return encoded
            }
        }

    @Test
    @DisplayName("Given a large photo when processed then the sample size caps the long edge")
    fun downscalesLargePhoto() {
        val seen = mutableListOf<Int>()

        val photo = ScanPhotoProcessor.process(ByteArray(10), codec(seenSample = seen))

        assertTrue(photo != null)
        assertEquals(listOf(2), seen)
        assertEquals("image/jpeg", photo?.mimeType)
    }

    @Test
    @DisplayName("Given a small photo when processed then no downscale is applied")
    fun keepsSmallPhoto() {
        val seen = mutableListOf<Int>()

        ScanPhotoProcessor.process(ByteArray(10), codec(size = 800 to 600, seenSample = seen))

        assertEquals(listOf(1), seen)
    }

    @Test
    @DisplayName("Given empty input when processed then null is returned")
    fun rejectsEmptyInput() {
        assertNull(ScanPhotoProcessor.process(ByteArray(0), codec()))
    }

    @Test
    @DisplayName("Given oversize input when processed then null is returned without decoding")
    fun rejectsOversizeInput() {
        var decoded = false
        val guard =
            object : PhotoCodec {
                override fun probeSize(bytes: ByteArray): Pair<Int, Int>? {
                    decoded = true
                    return 100 to 100
                }

                override fun downscale(bytes: ByteArray, sampleSize: Int): ByteArray? =
                    ByteArray(10)
            }

        assertNull(
            ScanPhotoProcessor.process(ByteArray(ScanPhotoProcessor.MAX_INPUT_BYTES + 1), guard),
        )
        assertEquals(false, decoded)
    }

    @Test
    @DisplayName("Given undecodable bytes when processed then null is returned")
    fun rejectsUndecodableBytes() {
        assertNull(ScanPhotoProcessor.process(ByteArray(10), codec(size = null)))
    }

    @Test
    @DisplayName("Given encoded output over the cap when processed then null is returned")
    fun rejectsOverCapOutput() {
        val big = ByteArray(ScanPhotoProcessor.MAX_BYTES + 1)

        assertNull(ScanPhotoProcessor.process(ByteArray(10), codec(encoded = big)))
    }

    @Test
    @DisplayName("Given failed encode when processed then null is returned")
    fun rejectsFailedEncode() {
        assertNull(ScanPhotoProcessor.process(ByteArray(10), codec(encoded = null)))
    }

    @Test
    @DisplayName("Given dimensions when sizing then powers of two bound the long edge")
    fun sampleSizeMath() {
        assertEquals(1, ScanPhotoProcessor.sampleSizeFor(1280, 800))
        assertEquals(2, ScanPhotoProcessor.sampleSizeFor(1281, 800))
        assertEquals(2, ScanPhotoProcessor.sampleSizeFor(2000, 1000))
        assertEquals(4, ScanPhotoProcessor.sampleSizeFor(5000, 100))
    }
}
