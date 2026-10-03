package com.stratum.liftconfig

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.stratum.domain.lift.ScanPhoto
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Decodes a stack-label photo into a size-capped JPEG [ScanPhoto] for the increments
 * AI scan. The Android bitmap work lives behind [PhotoCodec] so the budgeting logic
 * (sample-size math, byte caps, failure mapping) is pure JVM and unit-tested.
 */
object ScanPhotoProcessor {
    /** Longest edge after downscale; keeps base64 payloads far under the server cap. */
    const val MAX_DIMENSION_PX = 1280

    /** JPEG quality for the scan upload (legibility over size). */
    const val JPEG_QUALITY = 80

    /** Hard cap on the encoded upload; mirrors the server `MAX_IMAGE_BYTES`. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** Raw inputs over this are rejected before decode (OOM guard for huge picks). */
    const val MAX_INPUT_BYTES = 12 * 1024 * 1024

    /**
     * Returns a JPEG [ScanPhoto], or null when the input is empty, oversize,
     * undecodable, or still over [MAX_BYTES] after compression.
     *
     * The upload is normalized upright: phone captures store sensor-native pixels
     * plus an EXIF orientation flag, which the bitmap decode ignores and the JPEG
     * re-encode drops, so without [rotationDegreesFor] every portrait capture
     * would upload sideways and the vision model could not read the label.
     */
    fun process(input: ByteArray, codec: PhotoCodec = AndroidPhotoCodec): ScanPhoto? {
        if (input.isEmpty() || input.size > MAX_INPUT_BYTES) return null
        val encoded = decodeCapped(input, codec) ?: return null
        return ScanPhoto(encoded, "image/jpeg")
    }

    private fun decodeCapped(input: ByteArray, codec: PhotoCodec): ByteArray? {
        val (width, height) = codec.probeSize(input) ?: return null
        if (width <= 0 || height <= 0) return null
        val encoded =
            codec.downscale(input, sampleSizeFor(width, height), rotationDegreesFor(input))
                ?: return null
        return encoded.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
    }

    /**
     * Clockwise rotation (0, 90, 180, or 270) needed to normalize [bytes] upright
     * from its JPEG EXIF orientation flag. Pure JVM so it is unit-tested.
     *
     * Any non-JPEG, missing, mirrored, or malformed structure yields 0 (no
     * rotation) — worst case is today's behavior, never a wrong-way rotation.
     */
    fun rotationDegreesFor(bytes: ByteArray): Int =
        runCatching { exifRotationDegrees(bytes) }.getOrDefault(0)

    private fun exifRotationDegrees(bytes: ByteArray): Int =
        when (jpegExifOrientation(bytes)) {
            EXIF_ORIENTATION_ROTATE_90 -> ROTATE_90_DEGREES
            EXIF_ORIENTATION_ROTATE_180 -> ROTATE_180_DEGREES
            EXIF_ORIENTATION_ROTATE_270 -> ROTATE_270_DEGREES
            else -> 0
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

    /**
     * Largest power-of-two sample factor keeping the longest edge within
     * [MAX_DIMENSION_PX]. Powers of two because [BitmapFactory.Options.inSampleSize]
     * rounds down to one on older platforms.
     */
    fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_DIMENSION_PX) {
            sample *= 2
        }
        return sample
    }

    private const val ROTATE_90_DEGREES = 90
    private const val ROTATE_180_DEGREES = 180
    private const val ROTATE_270_DEGREES = 270

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

/** Bitmap boundary for [ScanPhotoProcessor]; faked in JVM tests. */
interface PhotoCodec {
    /** Decoded dimensions, or null when [bytes] is not an image. */
    fun probeSize(bytes: ByteArray): Pair<Int, Int>?

    /**
     * JPEG bytes of [bytes] decoded at [sampleSize] and rotated clockwise by
     * [rotationDegrees] (one of 0, 90, 180, 270), or null on failure. The output
     * carries no EXIF flag — pixels are already upright.
     */
    fun downscale(bytes: ByteArray, sampleSize: Int, rotationDegrees: Int): ByteArray?
}

/** [PhotoCodec] over the platform bitmap APIs. */
object AndroidPhotoCodec : PhotoCodec {
    override fun probeSize(bytes: ByteArray): Pair<Int, Int>? {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
            }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return bounds.outWidth to bounds.outHeight
    }

    override fun downscale(bytes: ByteArray, sampleSize: Int, rotationDegrees: Int): ByteArray? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val upright =
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated =
                    Bitmap.createBitmap(
                        decoded,
                        0,
                        0,
                        decoded.width,
                        decoded.height,
                        matrix,
                        true,
                    )
                decoded.recycle()
                rotated
            } else {
                decoded
            }
        return try {
            val out = ByteArrayOutputStream()
            val ok = upright.compress(Bitmap.CompressFormat.JPEG, ScanPhotoProcessor.JPEG_QUALITY, out)
            if (!ok) null else out.toByteArray()
        } finally {
            upright.recycle()
        }
    }
}
