package com.stratum.domain.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject

/**
 * Validated increment tiers (plus optional adder stops) in storage pounds, ready for
 * editor prefill. Micro is best-effort: an invalid micro list is dropped while valid
 * tiers are kept.
 *
 * Empty/invalid outcomes are expected AI-read failures (not crashes), so they are
 * logged at breadcrumb level ([LoggingPort.warning]) with enough context to diagnose
 * the miss without the original photo — never as non-fatals. Callable-level failures
 * stay logged by the inference adapter; this use case only propagates them.
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
 *
 * The final scanned band is normalized to open-ended before validation — but only when
 * no scanned row was dropped in conversion: labels print the stack's closed top and the
 * lattice models the top band as open (STR-244), while a dropped trailing row means the
 * last kept band is not the top and must stay closed so the scan fails honestly.
 */
class ScanIncrementTiersUseCase @Inject constructor(
    private val port: IncrementScanPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(photo: ScanPhoto): Result<ScannedIncrementTiers> {
        val scanned = port.scanTiers(photo).getOrElse { return Result.failure(it) }
        val rawRowCount = scanned.tiers.size
        val converted = scanned.tiers.mapNotNull { it.toTier(scanned.unit) }
        // Opening the final tier is only sound when nothing was dropped: if trailing
        // rows were lost in conversion, the last kept tier is not the stack top, and
        // opening it would prefill an infinite band for a truncated scan instead of
        // failing validation.
        val tiers = if (converted.size == rawRowCount) converted.withOpenFinalTier() else converted
        if (tiers.isEmpty()) {
            loggingPort.warning(
                TAG,
                "incrementScanEmpty photoBytes=${photo.bytes.size} mimeType=${photo.mimeType} " +
                    "scannedRows=$rawRowCount droppedRows=${rawRowCount - tiers.size}",
            )
            return Result.failure(IncrementScanEmptyException())
        }
        val tierErrors = IncrementLattice.tierErrors(tiers)
        if (tierErrors.isNotEmpty()) {
            loggingPort.warning(
                TAG,
                "incrementScanInvalid photoBytes=${photo.bytes.size} mimeType=${photo.mimeType} " +
                    "tierCount=${tiers.size} errors=$tierErrors",
            )
            return Result.failure(IncrementTiersInvalidException(tierErrors))
        }
        val micro =
            scanned.microStops
                .filter { it.isFinite() && it > 0.0 }
                .map { Weight(toStorageLb(it, scanned.unit), Weight.WeightUnit.Lb) }
        val keptMicro =
            if (IncrementLattice.microErrors(tiers, micro).isEmpty()) micro else emptyList()
        return Result.success(ScannedIncrementTiers(tiers, keptMicro))
    }

    /**
     * Drops the final tier's ceiling. Scanned labels print a closed top band (the stack's
     * maximum weight); the lattice represents the top band as open-ended, so a faithful
     * read of a finite stack would otherwise fail [IncrementLattice.tierErrors] with
     * FinalTierMustBeOpenEnded on every scan.
     */
    private fun List<IncrementTier>.withOpenFinalTier(): List<IncrementTier> {
        val finalTier = lastOrNull() ?: return this
        if (finalTier.upperBound == null) return this
        return dropLast(1) + finalTier.copy(upperBound = null)
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

    private companion object {
        // Matches FirebaseIncrementScanAdapter so empty/invalid breadcrumbs sit next
        // to callable-failure logs for the same scan surface.
        const val TAG = "StratumIncrementScan"
    }
}

/** Thrown when a scan yields no usable tier rows (empty or all malformed). */
class IncrementScanEmptyException :
    Exception("Increment scan returned no usable tiers.")
