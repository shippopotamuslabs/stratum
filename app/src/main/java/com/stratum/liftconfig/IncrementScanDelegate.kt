package com.stratum.liftconfig

import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.Weight
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scan status for the increments AI prefill. PR-5 wires this into ViewModel state
 * only — no composable reads it yet, so the scan button stays hidden and shipped
 * behavior is unchanged.
 */
sealed interface IncrementScanState {
    data object Idle : IncrementScanState
    data object Scanning : IncrementScanState
    data class Failed(val message: String) : IncrementScanState
    data class Scanned(
        val tierDrafts: List<IncrementTierDraft>,
        val microStopDrafts: List<String>,
    ) : IncrementScanState
}

/**
 * Shared scan runner for the lift and equipment increments editors.
 *
 * Runs [ScanIncrementTiersUseCase] (storage-pound result) and maps it to editor
 * drafts in the calling VM's [displayUnit] via [IncrementTierDraftOps]. The
 * delegate stays unit-agnostic: the unit arrives per [scan] call so each VM keeps
 * owning its display unit. Failures yield [IncrementScanState.Failed] and never
 * touch existing drafts — the VMs only apply [IncrementScanState.Scanned].
 */
class IncrementScanDelegate @Inject constructor(
    private val scanIncrementTiers: ScanIncrementTiersUseCase,
) {
    private val _scanState = MutableStateFlow<IncrementScanState>(IncrementScanState.Idle)
    val scanState: StateFlow<IncrementScanState> = _scanState.asStateFlow()

    suspend fun scan(photo: ScanPhoto, displayUnit: Weight.WeightUnit) {
        _scanState.value = IncrementScanState.Scanning
        val scanned = scanIncrementTiers(photo).getOrNull()
        val tierDrafts =
            scanned?.tiers?.map { IncrementTierDraftOps.toDraft(it, displayUnit) }.orEmpty()
        _scanState.value =
            if (scanned == null || tierDrafts.isEmpty()) {
                IncrementScanState.Failed(SCAN_FAILURE_MESSAGE)
            } else {
                IncrementScanState.Scanned(
                    tierDrafts = tierDrafts,
                    microStopDrafts =
                        IncrementTierDraftOps.toMicroDrafts(scanned.microStops, displayUnit),
                )
            }
    }

    fun reset() {
        _scanState.value = IncrementScanState.Idle
    }

    companion object {
        /**
         * Scan failure copy. Plain string: the state layer carries messages without
         * a Context; proper `strings.xml` entries land with the PR-6 entry UI.
         */
        const val SCAN_FAILURE_MESSAGE =
            "Could not read increments from the photo. Enter them manually."
    }
}
