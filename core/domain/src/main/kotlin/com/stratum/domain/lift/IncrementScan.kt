package com.stratum.domain.lift


class ScanPhoto(
    val bytes: ByteArray,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ScanPhoto &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
}

/** One scanned stack band in the photo's unit (`upper == null` means open-ended). */
data class ScannedTierRow(
    val lower: Double,
    val upper: Double?,
    val step: Double,
)

/**
 * Structured increment-scan result in the photo's [unit]. Amounts are raw photo-label
 * magnitudes — the use case converts them to storage pounds before validation.
 */

data class ScannedTiers(
    val unit: Weight.WeightUnit,
    val tiers: List<ScannedTierRow>,
    val microStops: List<Double> = emptyList(),
)
