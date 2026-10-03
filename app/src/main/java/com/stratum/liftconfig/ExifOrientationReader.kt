package com.stratum.liftconfig

/**
 * Pure-JVM JPEG/EXIF orientation reader.
 *
 * Binary metadata parsing (JPEG segment walk, TIFF/IFD parsing, endianness,
 * bounds checks) is a separate concern from the image-budgeting orchestration
 * in [ScanPhotoProcessor], so it lives here behind one narrow API. Pure JVM
 * (no Android framework) so it stays unit-testable without Robolectric and
 * adds no dependency.
 *
 * Phone captures store sensor-native pixels plus an EXIF orientation flag,
 * which the bitmap decode ignores and the JPEG re-encode drops — callers
 * rotate pixels upright via [rotationFor] before encode.
 */
object ExifOrientationReader {
    /**
     * Clockwise [Rotation] needed to normalize [bytes] upright from its JPEG
     * EXIF orientation flag.
     *
     * Any non-JPEG, missing, mirrored, or malformed structure yields
     * [Rotation.NONE] — worst case is no rotation (the old behavior), never a
     * wrong-way rotation.
     */
    fun rotationFor(bytes: ByteArray): Rotation =
        runCatching { exifRotation(bytes) }.getOrDefault(Rotation.NONE)

    private fun exifRotation(bytes: ByteArray): Rotation =
        when (jpegExifOrientation(bytes)) {
            EXIF_ORIENTATION_ROTATE_90 -> Rotation.R90
            EXIF_ORIENTATION_ROTATE_180 -> Rotation.R180
            EXIF_ORIENTATION_ROTATE_270 -> Rotation.R270
            else -> Rotation.NONE
        }

    /**
     * Reads the EXIF orientation flag (tag `0x0112`) from JPEG APP1 metadata, or
     * null when absent/unreadable. Only the first IFD is consulted — phone
     * captures store orientation there.
     */
    private fun jpegExifOrientation(bytes: ByteArray): Int? {
        if (bytes.size < MIN_JPEG_SIZE || bytes[0] != JPEG_MARKER || bytes[1] != JPEG_SOI) {
            return null
        }
        var offset = SOI_LENGTH
        var orientation: Int? = null
        while (orientation == null && offset + SEGMENT_HEADER_LENGTH <= bytes.size) {
            if (bytes[offset] != JPEG_MARKER) return null
            val marker = bytes[offset + 1]
            if (marker == JPEG_EOI || marker == JPEG_SOS) return null
            val length = u16BigEndian(bytes, offset + LENGTH_OFFSET)
            if (length < LENGTH_SIZE || offset + MARKER_LENGTH + length > bytes.size) return null
            if (marker == JPEG_APP1) {
                orientation =
                    tiffOrientation(
                        bytes,
                        start = offset + SEGMENT_HEADER_LENGTH,
                        size = length - LENGTH_SIZE,
                    )
            }
            offset += MARKER_LENGTH + length
        }
        return orientation
    }

    private fun tiffOrientation(bytes: ByteArray, start: Int, size: Int): Int? {
        if (size < EXIF_HEADER_LENGTH + TIFF_HEADER_LENGTH || start + size > bytes.size) return null
        if (!isExifHeader(bytes, start)) return null
        val tiff = start + EXIF_HEADER_LENGTH
        val littleEndian = tiffByteOrder(bytes, tiff) ?: return null
        if (u16(bytes, tiff + TIFF_MAGIC_OFFSET, littleEndian) != TIFF_MAGIC) return null
        return orientationFromIfd(bytes, tiff, littleEndian, start, size)
    }

    private fun tiffByteOrder(bytes: ByteArray, tiff: Int): Boolean? =
        when {
            bytes[tiff] == BYTE_I && bytes[tiff + 1] == BYTE_I -> true
            bytes[tiff] == BYTE_M && bytes[tiff + 1] == BYTE_M -> false
            else -> null
        }

    private fun orientationFromIfd(
        bytes: ByteArray,
        tiff: Int,
        littleEndian: Boolean,
        start: Int,
        size: Int,
    ): Int? {
        val ifd = tiff + u32(bytes, tiff + IFD_OFFSET_OFFSET, littleEndian)
        if (ifd < tiff || ifd + IFD_COUNT_LENGTH > start + size) return null
        val entries = u16(bytes, ifd, littleEndian)
        for (index in 0 until entries) {
            val entry = ifd + IFD_COUNT_LENGTH + index * IFD_ENTRY_LENGTH
            if (entry + IFD_ENTRY_LENGTH > start + size) return null
            if (isOrientationEntry(bytes, entry, littleEndian)) {
                return u16(bytes, entry + IFD_VALUE_OFFSET, littleEndian)
            }
        }
        return null
    }

    private fun isOrientationEntry(bytes: ByteArray, entry: Int, littleEndian: Boolean): Boolean {
        val tag = u16(bytes, entry, littleEndian)
        val type = u16(bytes, entry + IFD_TYPE_OFFSET, littleEndian)
        val count = u32(bytes, entry + IFD_COUNT_OFFSET, littleEndian)
        return tag == EXIF_ORIENTATION_TAG && type == TIFF_TYPE_SHORT && count >= 1
    }

    private fun isExifHeader(bytes: ByteArray, start: Int): Boolean =
        bytes[start] == BYTE_E &&
            bytes[start + 1] == BYTE_LOWER_X &&
            bytes[start + 2] == BYTE_LOWER_I &&
            bytes[start + 3] == BYTE_LOWER_F &&
            bytes[start + EXIF_ZERO_OFFSET] == 0.toByte() &&
            bytes[start + EXIF_ZERO_OFFSET + 1] == 0.toByte()

    private fun u16(bytes: ByteArray, at: Int, littleEndian: Boolean): Int =
        if (littleEndian) {
            (bytes[at].toInt() and BYTE_MASK) or ((bytes[at + 1].toInt() and BYTE_MASK) shl SHIFT_BYTE)
        } else {
            ((bytes[at].toInt() and BYTE_MASK) shl SHIFT_BYTE) or (bytes[at + 1].toInt() and BYTE_MASK)
        }

    private fun u16BigEndian(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and BYTE_MASK) shl SHIFT_BYTE) or (bytes[at + 1].toInt() and BYTE_MASK)

    private fun u32(bytes: ByteArray, at: Int, littleEndian: Boolean): Int {
        val low: Int
        val high: Int
        if (littleEndian) {
            low = u16(bytes, at, littleEndian = true)
            high = u16(bytes, at + WORD_LENGTH, littleEndian = true)
        } else {
            high = u16(bytes, at, littleEndian = false)
            low = u16(bytes, at + WORD_LENGTH, littleEndian = false)
        }
        return (high shl SHIFT_WORD) or low
    }

    private val JPEG_MARKER: Byte = 0xFF.toByte()
    private val JPEG_SOI: Byte = 0xD8.toByte()
    private val JPEG_EOI: Byte = 0xD9.toByte()
    private val JPEG_SOS: Byte = 0xDA.toByte()
    private val JPEG_APP1: Byte = 0xE1.toByte()
    private const val MIN_JPEG_SIZE = 4
    private const val SOI_LENGTH = 2
    private const val MARKER_LENGTH = 2
    private const val SEGMENT_HEADER_LENGTH = 4
    private const val LENGTH_OFFSET = 2
    private const val LENGTH_SIZE = 2

    private const val EXIF_HEADER_LENGTH = 6
    private const val EXIF_ZERO_OFFSET = 4
    private val BYTE_E: Byte = 'E'.code.toByte()
    private val BYTE_LOWER_X: Byte = 'x'.code.toByte()
    private val BYTE_LOWER_I: Byte = 'i'.code.toByte()
    private val BYTE_LOWER_F: Byte = 'f'.code.toByte()
    private val BYTE_I: Byte = 'I'.code.toByte()
    private val BYTE_M: Byte = 'M'.code.toByte()
    private const val BYTE_MASK = 0xFF
    private const val SHIFT_BYTE = 8
    private const val SHIFT_WORD = 16
    private const val WORD_LENGTH = 2
    private const val TIFF_HEADER_LENGTH = 8
    private const val TIFF_MAGIC_OFFSET = 2
    private const val TIFF_MAGIC = 42
    private const val IFD_OFFSET_OFFSET = 4
    private const val IFD_COUNT_LENGTH = 2
    private const val IFD_ENTRY_LENGTH = 12
    private const val IFD_TYPE_OFFSET = 2
    private const val IFD_COUNT_OFFSET = 4
    private const val IFD_VALUE_OFFSET = 8
    private const val TIFF_TYPE_SHORT = 3
    private const val EXIF_ORIENTATION_TAG = 0x0112
    private const val EXIF_ORIENTATION_ROTATE_90 = 6
    private const val EXIF_ORIENTATION_ROTATE_180 = 3
    private const val EXIF_ORIENTATION_ROTATE_270 = 8
}
