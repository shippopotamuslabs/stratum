package com.stratum.domain.program

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.Program

data class MuscleVolumeSummary(
    val muscle: MuscleGroup,
    val exerciseCount: Int,
    val setCount: Double,
)

/**
 * Computes fractional hard-set **cycle** volume per muscle for a persisted [Program] using lift
 * catalog muscle tags (primary 1.0, secondary 0.5 credits per set). Sums every training slot in
 * the template once — not calendar-week normalized.
 */
