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
        seenDegrees: MutableList<Int> = mutableListOf(),
    ): PhotoCodec =
        object : PhotoCodec {
            override fun probeSize(bytes: ByteArray): Pair<Int, Int>? = size

            override fun downscale(bytes: ByteArray, sampleSize: Int, rotationDegrees: Int): ByteArray? {
                seenSample += sampleSize
                seenDegrees += rotationDegrees
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

                override fun downscale(bytes: ByteArray, sampleSize: Int, rotationDegrees: Int): ByteArray? =
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

    @Test
    @DisplayName("Given a portrait EXIF capture when processed then the codec normalizes it upright")
    fun portraitCaptureIsNormalizedUpright() {
        val seenDegrees = mutableListOf<Int>()
        val input = soi() + PIL_PORTRAIT_APP1 + eoi()

        val photo =
            ScanPhotoProcessor.process(
                input,
                codec(seenSample = mutableListOf(), seenDegrees = seenDegrees),
            )

        assertTrue(photo != null)
        assertEquals(listOf(90), seenDegrees)
    }

    @Test
    @DisplayName("Given EXIF orientations when read then clockwise degrees are returned")
    fun exifOrientationsMapToDegrees() {
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 1)))
        assertEquals(180, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 3)))
        assertEquals(90, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 6)))
        assertEquals(270, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 8)))
        assertEquals(90, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 6, littleEndian = false)))
    }

    @Test
    @DisplayName("Given mirrored or unknown orientations when read then no rotation is applied")
    fun mirroredOrientationsYieldZero() {
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 2)))
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 5)))
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 9)))
    }

    @Test
    @DisplayName("Given a non-EXIF APP1 segment first when read then scanning continues to the EXIF segment")
    fun xmpBeforeExifIsSkipped() {
        val input = exifJpeg(orientation = 6, leadingSegments = listOf(app1Segment(XMP_PAYLOAD)))

        assertEquals(90, ScanPhotoProcessor.rotationDegreesFor(input))
    }

    @Test
    @DisplayName("Given no EXIF data when read then no rotation is applied")
    fun missingExifYieldsZero() {
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(soi() + eoi()))
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(PNG_MAGIC))
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(ByteArray(0)))
        assertEquals(0, ScanPhotoProcessor.rotationDegreesFor(exifJpeg(orientation = 6).copyOf(10)))
    }

    @Test
    @DisplayName("Given an EXIF-less photo when processed then the codec receives zero degrees")
    fun exifLessPhotoKeepsZeroDegrees() {
        val seenDegrees = mutableListOf<Int>()

        ScanPhotoProcessor.process(soi() + eoi(), codec(seenDegrees = seenDegrees))

        assertEquals(listOf(0), seenDegrees)
    }

    private fun soi(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

    private fun eoi(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun app1Segment(payload: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        out += 0xFF.toByte()
        out += 0xE1.toByte()
        val length = payload.size + 2
        out += ((length shr 8) and 0xFF).toByte()
        out += (length and 0xFF).toByte()
        out.addAll(payload.toList())
        return out.toByteArray()
    }

    private fun exifJpeg(
        orientation: Int,
        littleEndian: Boolean = true,
        leadingSegments: List<ByteArray> = emptyList(),
    ): ByteArray {
        val tiff = mutableListOf<Byte>()
        fun u16(value: Int) {
            if (littleEndian) {
                tiff += (value and 0xFF).toByte()
                tiff += ((value shr 8) and 0xFF).toByte()
            } else {
                tiff += ((value shr 8) and 0xFF).toByte()
                tiff += (value and 0xFF).toByte()
            }
        }
        fun u32(value: Int) {
            if (littleEndian) {
                u16(value and 0xFFFF)
                u16((value shr 16) and 0xFFFF)
            } else {
                u16((value shr 16) and 0xFFFF)
                u16(value and 0xFFFF)
            }
        }
        tiff.addAll(EXIF_HEADER.toList())
        tiff.addAll(if (littleEndian) BYTE_ORDER_LE.toList() else BYTE_ORDER_BE.toList())
        u16(TIFF_MAGIC)
        u32(IFD_OFFSET)
        u16(1)
        u16(EXIF_ORIENTATION_TAG)
        u16(TIFF_TYPE_SHORT)
        u32(1)
        u16(orientation)
        u16(0)
        u32(0)
        val out = mutableListOf<Byte>()
        out.addAll(soi().toList())
        for (segment in leadingSegments) out.addAll(segment.toList())
        out.addAll(app1Segment(tiff.toByteArray()).toList())
        out.addAll(eoi().toList())
        return out.toByteArray()
    }

    private companion object {
        /**
         * APP1 EXIF segment of a real portrait-capture JPEG (600x200 label photo
         * saved with EXIF orientation 6 by a standard encoder): big-endian TIFF,
         * single IFD entry, orientation 6. Guards the byte-level layout phones emit
         * rather than only the test builder below.
         */
        private val PIL_PORTRAIT_APP1 =
            byteArrayOf(
                -1, -31, 0, 34, 69, 120, 105, 102, 0, 0, 77, 77, 0, 42, 0, 0, 0, 8, 0, 1,
                1, 18, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0,
            )
        private val PNG_MAGIC =
            byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            )
        private val XMP_PAYLOAD = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
        private val EXIF_HEADER = "Exif\u0000\u0000".toByteArray()
        private val BYTE_ORDER_LE = "II".toByteArray()
        private val BYTE_ORDER_BE = "MM".toByteArray()
        private const val TIFF_MAGIC = 42
        private const val IFD_OFFSET = 8
        private const val EXIF_ORIENTATION_TAG = 0x0112
        private const val TIFF_TYPE_SHORT = 3
    }
}
