package com.stratum.domain.lift

/**
 * Photo input for increment-tier inference. Raw bytes stay in domain as a stdlib
 * [ByteArray] (no Android types cross the boundary); the inference adapter encodes
 * them into a data URI for the vision call. Equality is content-based.
 */
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

/** Cloud vision port for inferring increment tiers from a stack-label photo. */
interface IncrementScanPort {
    suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers>
}
