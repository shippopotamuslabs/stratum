package com.stratum.liftconfig

import com.stratum.domain.featureflag.ObserveIncrementsAiScanUseCase
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.Weight
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Scan status for the increments AI prefill. PR-5 wires this into ViewModel state
 * only — no composable reads it yet, so the scan button stays hidden and shipped
 * behavior is unchanged. User-visible text mapping happens at the composable
 * boundary (PR-6 owns strings.xml), so [Failed] carries no copy.
 */
sealed interface IncrementScanState {
    data object Idle : IncrementScanState
    data object Scanning : IncrementScanState
    data object Failed : IncrementScanState
    data class Scanned(
        val tierDrafts: List<IncrementTierDraft>,
        val microStopDrafts: List<String>,
    ) : IncrementScanState
}

/**
 * Shared scan runner for the lift and equipment increments editors.
 *
 * Owns the whole scan session: the launch-flag gate, the request state, the
 * reset lifecycle, and the generation guard. Each editor only supplies an
 * apply-scanned-drafts lambda plus its display unit via [requestScan].
 *
 * Runs [ScanIncrementTiersUseCase] (storage-pound result) and maps it to editor
 * drafts in the calling VM's [displayUnit] via [IncrementTierDraftOps]. The
 * delegate stays unit-agnostic: the unit arrives per call so each VM keeps
 * owning its display unit. Failures yield null and never touch existing
 * drafts — the VMs only apply returned [ScannedDrafts].
 */
class IncrementScanDelegate @Inject constructor(
    private val scanIncrementTiers: ScanIncrementTiersUseCase,
    private val observeIncrementsAiScan: ObserveIncrementsAiScanUseCase,
) {
    /** Drafts from a successful scan, mapped to the caller's display unit. */
    data class ScannedDrafts(
        val tierDrafts: List<IncrementTierDraft>,
        val microStopDrafts: List<String>,
    )

    /** Combined scan presentation: the flag gate plus the request state in one typed object. */
    data class ScanUi(
        val available: Boolean,
        val state: IncrementScanState,
    )

    private val _scanState = MutableStateFlow<IncrementScanState>(IncrementScanState.Idle)
    val scanState: StateFlow<IncrementScanState> = _scanState.asStateFlow()

    /**
     * Single typed flow the editors combine into UiState, so neither VM owns a
     * flag flow, a scan-state flow, or the gating between them.
     */
    fun observeScanUi(): Flow<ScanUi> =
        combine(observeIncrementsAiScan(), scanState, ::ScanUi)

    // Generation-guarded session: every request and every invalidation bumps
    // [generation]; a completion applies only when its generation and owner id
    // are still current, so an in-flight scan can never prefill a new
    // lift/equipment after a reset. Main-confined: touched only from ViewModel
    // scopes and tests.
    private var generation = 0L
    private var currentOwnerId: String? = null
    private var activeJob: Job? = null

    /**
     * Runs the photo scan and returns the mapped drafts directly (null on
     * failure or while the launch flag is off), while still publishing
     * [_scanState] for observation. Callers apply the returned value and never
     * re-read the flow. A completion that went stale mid-flight (after
     * [onOwnerChanged]/[onDisplayUnitChanged]) keeps the reset Idle instead of
     * overwriting it, and returns null.
     */
    suspend fun scan(photo: ScanPhoto, displayUnit: Weight.WeightUnit): ScannedDrafts? =
        runScan(photo, displayUnit, generation)

    /**
     * Generation-guarded scan entry for the editors: cancels/replaces the
     * previous request, runs the photo, and applies the returned drafts via
     * [onScanned] only when the generation and [ownerId] are still current.
     * No-op while the launch flag is off.
     */
    fun requestScan(
        scope: CoroutineScope,
        photo: ScanPhoto,
        displayUnit: Weight.WeightUnit,
        ownerId: String?,
        onScanned: (ScannedDrafts) -> Unit,
    ) {
        activeJob?.cancel()
        val requestGeneration = ++generation
        activeJob = scope.launch {
            val drafts = runScan(photo, displayUnit, requestGeneration) ?: return@launch
            if (requestGeneration != generation || ownerId != currentOwnerId) return@launch
            onScanned(drafts)
        }
    }

    /**
     * Owner change (`initialize`): records the owner, drops any in-flight scan,
     * and returns to idle.
     */
    fun onOwnerChanged(ownerId: String?) {
        currentOwnerId = ownerId
        cancelAndIdle()
    }

    /**
     * Display-unit flip: drops any in-flight scan (mapped to the old unit) and
     * returns to idle. The owner is unchanged.
     */
    fun onDisplayUnitChanged() {
        cancelAndIdle()
    }

    private fun cancelAndIdle() {
        activeJob?.cancel()
        activeJob = null
        generation++
        _scanState.value = IncrementScanState.Idle
    }

    private suspend fun runScan(
        photo: ScanPhoto,
        displayUnit: Weight.WeightUnit,
        requestGeneration: Long,
    ): ScannedDrafts? {
        if (!observeIncrementsAiScan().first()) return null
        _scanState.value = IncrementScanState.Scanning
        val scanned = scanIncrementTiers(photo).getOrNull()
        val tierDrafts =
            scanned?.tiers?.map { IncrementTierDraftOps.toDraft(it, displayUnit) }.orEmpty()
        val drafts =
            if (scanned == null || tierDrafts.isEmpty()) {
                null
            } else {
                ScannedDrafts(
                    tierDrafts = tierDrafts,
                    microStopDrafts =
                        IncrementTierDraftOps.toMicroDrafts(scanned.microStops, displayUnit),
                )
            }
        if (requestGeneration != generation) return null
        _scanState.value =
            if (drafts == null) {
                IncrementScanState.Failed
            } else {
                IncrementScanState.Scanned(drafts.tierDrafts, drafts.microStopDrafts)
            }
        return drafts
    }
}
