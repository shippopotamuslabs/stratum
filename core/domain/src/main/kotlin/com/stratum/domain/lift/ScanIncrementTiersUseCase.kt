package com.stratum.domain.lift

import javax.inject.Inject

/**
 * Validated increment tiers (plus optional adder stops) in storage pounds, ready for
 * editor prefill. Micro is best-effort: an invalid micro list is dropped while valid
 * tiers are kept.
 */
data class ScannedIncrementTiers(
    val tiers: List<IncrementTier>,
    val microStops: List<Weight>,
)

/**
 * Converts a raw [IncrementScanPort] scan into storage-pound tiers for editor prefill.
 *
 * Malformed rows (non-finite / negative bounds, non-positive steps, inverted bands) are
 * dropped before validation. An empty or invalid tier list fails the whole result (never
 * prefill corrupt bands); invalid micro stops are dropped while tiers are kept.
 */
class ScanIncrementTiersUseCase @Inject constructor(
    private val port: IncrementScanPort,
    private val validateTiers: ValidateIncrementTiersUseCase,
    private val validateMicro: ValidateMicroIncrementsUseCase,
) {
    suspend operator fun invoke(photo: ScanPhoto): Result<ScannedIncrementTiers> {
        val scanned = port.scanTiers(photo).getOrElse { return Result.failure(it) }
        val tiers = scanned.tiers.mapNotNull { it.toTier(scanned.unit) }
        if (tiers.isEmpty()) return Result.failure(IncrementScanEmptyException())
        when (val validation = validateTiers(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return Result.failure(IncrementTiersInvalidException(validation.errors))
            IncrementTiersValidationResult.Valid -> Unit
        }
        val micro =
            scanned.microStops
                .filter { it.isFinite() && it > 0.0 }
                .map { Weight(toStorageLb(it, scanned.unit), Weight.WeightUnit.Lb) }
        val keptMicro =
            when (validateMicro(tiers, micro)) {
                MicroIncrementsValidationResult.Valid -> micro
                is MicroIncrementsValidationResult.Invalid -> emptyList()
            }
        return Result.success(ScannedIncrementTiers(tiers, keptMicro))
    }

    private fun ScannedTierRow.toTier(unit: Weight.WeightUnit): IncrementTier? {
        if (!lower.isFinite() || lower < 0.0) return null
        if (!step.isFinite() || step <= 0.0) return null
        if (upper != null && (!upper.isFinite() || upper <= lower)) return null
        return IncrementTier(
            lowerBound = Weight(toStorageLb(lower, unit), Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(toStorageLb(it, unit), Weight.WeightUnit.Lb) },
            increment = Weight(toStorageLb(step, unit), Weight.WeightUnit.Lb),
        )
    }

    private fun toStorageLb(amount: Double, unit: Weight.WeightUnit): Double =
        WeightConversion.convertAmount(amount, unit, Weight.WeightUnit.Lb)
}

/** Thrown when a scan yields no usable tier rows (empty or all malformed). */
class IncrementScanEmptyException :
    Exception("Increment scan returned no usable tiers.")
