package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.toStorageLb

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
 * Outcome of scanning increment tiers from a photo (STR-243).
 *
 * [InvalidTiers] carries the domain validation result itself. [ScanFailed] is the
 * scan port's failure: the inference adapter already logged the originating
 * throwable once at its boundary, so this use case logs nothing for it (see the
 * class doc on [ScanIncrementTiersUseCase]).
 */
sealed interface ScanIncrementTiersResult {
    data class Success(val scanned: ScannedIncrementTiers) : ScanIncrementTiersResult

    /** The scan port failed (callable / network); logged once by the adapter. */
    data object ScanFailed : ScanIncrementTiersResult

    /** The scan yielded no usable tier rows (empty or all malformed). */
    data object Empty : ScanIncrementTiersResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        ScanIncrementTiersResult
}

/**
 * Converts a raw [IncrementScanPort] scan into storage-pound tiers for editor prefill.
 *
 * Malformed rows (non-finite / negative bounds, non-positive steps, inverted bands) are
 * dropped before validation. An empty or invalid tier list fails the whole scan (never
 * prefill corrupt bands); invalid micro stops are dropped while tiers are kept.
 *
 * Converted tiers are prepared for validation by [IncrementLattice.prepareScannedTiers],
 * which owns the final-band rule (STR-244); this use case only supplies the rows and
 * whether the read was complete (no scanned row dropped in conversion).
 */
class ScanIncrementTiersUseCase @Inject constructor(
    private val port: IncrementScanPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(photo: ScanPhoto): ScanIncrementTiersResult {
        val scanned =
            port.scanTiers(photo).getOrElse { error ->
                // Cancellation is not a scan failure (STR-239): it propagates.
                if (error is CancellationException) throw error
                return ScanIncrementTiersResult.ScanFailed
            }
        val rawRowCount = scanned.tiers.size
        val converted = scanned.tiers.mapNotNull { it.toTier(scanned.unit) }
        val tiers =
            IncrementLattice.prepareScannedTiers(converted, complete = converted.size == rawRowCount)
        if (tiers.isEmpty()) {
            loggingPort.warning(
                TAG,
                "incrementScanEmpty photoBytes=${photo.bytes.size} mimeType=${photo.mimeType} " +
                    "scannedRows=$rawRowCount droppedRows=${rawRowCount - tiers.size}",
            )
            return ScanIncrementTiersResult.Empty
        }
        val tierErrors = IncrementLattice.tierErrors(tiers)
        if (tierErrors.isNotEmpty()) {
            loggingPort.warning(
                TAG,
                "incrementScanInvalid photoBytes=${photo.bytes.size} mimeType=${photo.mimeType} " +
                    "tierCount=${tiers.size} errors=$tierErrors",
            )
            return ScanIncrementTiersResult.InvalidTiers(
                IncrementTiersValidationResult.Invalid(tierErrors),
            )
        }
        val micro =
            scanned.microStops
                .filter { it.isFinite() && it > 0.0 }
                .map { Weight(toStorageLb(it, scanned.unit), Weight.WeightUnit.Lb) }
        val keptMicro =
            if (IncrementLattice.microErrors(tiers, micro).isEmpty()) micro else emptyList()
        return ScanIncrementTiersResult.Success(ScannedIncrementTiers(tiers, keptMicro))
    }

    private fun ScannedTierRow.toTier(unit: Weight.WeightUnit): IncrementTier? {
        if (!lower.isFinite() || lower < 0.0) return null
        if (!step.isFinite() || step <= 0.0) return null
        val upperBound = upper
        if (upperBound != null && (!upperBound.isFinite() || upperBound <= lower)) return null
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
