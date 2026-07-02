package com.stratum.domain.lift

/**
 * The loading category of a lift — explicit biomechanical and loading properties that drive
 * recovery classification, replacing the movement-pattern-overloading hack (where leg
 * press / split squats were labeled `KneeExtension` to be eligible for quad isolation slots).
 *
 * The three categories map directly to [com.stratum.domain.program.generation.reference.LiftStressClass]:
 * - [AxialCompound] → `HeavyAxial` (barbell squat, deadlift — spine-loaded free-weight compounds)
 * - [NonAxialCompound] → `Compound` (leg press, hack squat, machine chest press, cable row —
 *   multi-joint compounds without axial loading)
 * - [Isolation] → `Isolation` (leg extension, leg curl, bicep curl — single-joint movements)
 *
 * This is a first-class catalog property (set in `seed-lifts.json`), not a heuristic derived from
 * the lift name or movement pattern. Recovery classification is fully data-driven.
 */
enum class KinematicProfile {
    AxialCompound,
    NonAxialCompound,
    Isolation,
}
