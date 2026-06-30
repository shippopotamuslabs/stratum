package com.stratum.domain.program.generation.prescription

/**
 * Decomposed CP-SAT objective for comparing split candidates and telemetry.
 * Lower [total] is better. Tiers are separated by orders of magnitude in the solver.
 */
data class SynthesisObjectiveBreakdown(
    val total: Long,
    val recoveryPenalty: Long,
    val specializedMevShortfall: Long,
    val coSpecializationImbalance: Long,
    val specializeAimDeviation: Long,
    val maintenanceAimDeviation: Long,
    val minimumVolumeAimDeviation: Long,
    val roleMinimumShortfall: Long,
    val pushPullImbalance: Long,
    val sessionFillShortfall: Long,
) {
    companion object {
        val ZERO =
            SynthesisObjectiveBreakdown(
                total = 0L,
                recoveryPenalty = 0L,
                specializedMevShortfall = 0L,
                coSpecializationImbalance = 0L,
                specializeAimDeviation = 0L,
                maintenanceAimDeviation = 0L,
                minimumVolumeAimDeviation = 0L,
                roleMinimumShortfall = 0L,
                pushPullImbalance = 0L,
                sessionFillShortfall = 0L,
            )
    }
}
