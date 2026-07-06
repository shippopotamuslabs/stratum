package com.stratum.domain.importing

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Loads the user's per-lift fatigue decay parameters detected from imported training history
 * ([LiftTrainingInsights.fatigueDecaySlope] / [fatigueDecayIntercept]). Returns a [LiftId]-keyed
 * lookup so the workout and generation layers can pass personalized overrides into [FatigueModel]
 * call sites and the rep-range planner.
 *
 * Mirrors the one-shot `getAll().associateBy { it.liftId }` read in
 * `LoadProgramGenerationContextUseCase` — suspend-only (no Flow), consistent with the
 * session-start snapshot model.
 */
class ObserveLiftTrainingInsightsUseCase @Inject constructor(
    private val repository: LiftTrainingInsightsRepository,
) {
    suspend operator fun invoke(): Map<LiftId, FatigueDecay> =
        repository.getAll().associate { it.liftId to FatigueDecay(it.fatigueDecaySlope, it.fatigueDecayIntercept) }
}

/** Per-lift fatigue decay pair — nullable so callers fall back to population defaults when absent. */
data class FatigueDecay(
    val slope: Double?,
    val intercept: Double?,
)
