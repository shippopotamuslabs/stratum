package com.stratum.domain.lift

import com.stratum.domain.program.generation.reference.LiftStressClass

/**
 * Loading / recovery cost band for a lift (catalog-authored; not biomechanics wording).
 * Maps to LiftStressClass: High→HeavyAxial, Medium→UpperCompound, Low→Isolation
 * (stress class names unchanged).
 */
enum class FatigueProfile {
    /** Spine-loaded free-weight compounds (e.g. barbell squat, deadlift). Was AxialCompound. */
    High,

    /** Other multi-joint compounds without high axial free-weight cost. Was NonAxialCompound. */
    Medium,

    /** Lighter accessories — not single-joint only. Was Isolation (kinematic). */
    Low,
}
