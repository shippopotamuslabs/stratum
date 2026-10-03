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
     * re-encode drops, so without [ExifOrientationReader.rotationFor] every
     * portrait capture would upload sideways and the vision model could not
     * read the label.
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
            codec.downscale(input, sampleSizeFor(width, height), ExifOrientationReader.rotationFor(input))
                ?: return null
        return encoded.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
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
}

/**
 * Clockwise rotation normalizing sensor-native pixels upright, as a closed
 * set instead of a raw Int: only 0/90/180/270 are representable, so the
 * [PhotoCodec] boundary documents its own contract and callers cannot pass a
 * meaningless degree value.
 */
enum class Rotation(val degrees: Int) {
    NONE(0),
    R90(90),
    R180(180),
    R270(270),
}

/** Bitmap boundary for [ScanPhotoProcessor]; faked in JVM tests. */
interface PhotoCodec {
    /** Decoded dimensions, or null when [bytes] is not an image. */
    fun probeSize(bytes: ByteArray): Pair<Int, Int>?

    /**
     * JPEG bytes of [bytes] decoded at [sampleSize] and rotated clockwise by
     * [rotation], or null on failure. The output carries no EXIF flag —
     * pixels are already upright.
     */
    fun downscale(bytes: ByteArray, sampleSize: Int, rotation: Rotation): ByteArray?
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

    override fun downscale(bytes: ByteArray, sampleSize: Int, rotation: Rotation): ByteArray? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val upright =
            if (rotation != Rotation.NONE) {
                val matrix = Matrix().apply { postRotate(rotation.degrees.toFloat()) }
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
