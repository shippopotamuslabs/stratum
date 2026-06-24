package com.stratum.domain.lift

/**
 * How a [Lift] moves — the categorical slot a lift occupies (squat pattern, horizontal push,
 * etc.). The 18 values port LiftLab's `MovementPattern` one-for-one for OS-6 generation
 * parity, renamed to PascalCase to match the Stratum enum style (see `MuscleGroup`). Values
 * are grouped logically: lower body, then upper-body compound, then isolation.
 *
 * A lift's pattern is orthogonal to which muscles it works ([Lift.primaryMuscles]) — many
 * lifts in the same pattern share muscle contributions, but the pattern drives exercise
 * selection and split assignment in program generation (OS-6).
 */
enum class MovementPattern {
    LegPush,
    HipHinge,
    QuadIso,
    HamstringIso,
    Calves,

    HorizontalPush,
    VerticalPull,
    HorizontalPull,
    ChestIso,
    TricepIso,
    BicepIso,
    DeltIso,

    GluteIso,
    ForearmIso,
    TrapIso,
    AbIso,
    InclinePush,
    VerticalPush,
    ;

    /**
     * Human-readable label for UI grouping. Display strings live in the domain here (not the
     * feature layer) because the grouping is shared between OS-4 selection and OS-6 generation.
     */
    val displayName: String
        get() = when (this) {
            LegPush -> "Leg Push"
            HipHinge -> "Hip Hinge"
            QuadIso -> "Quad Isolation"
            HamstringIso -> "Hamstring Isolation"
            Calves -> "Calves"
            HorizontalPush -> "Horizontal Push"
            VerticalPull -> "Vertical Pull"
            HorizontalPull -> "Horizontal Pull"
            ChestIso -> "Chest Isolation"
            TricepIso -> "Tricep Isolation"
            BicepIso -> "Bicep Isolation"
            DeltIso -> "Deltoid Isolation"
            GluteIso -> "Glute Isolation"
            ForearmIso -> "Forearm Isolation"
            TrapIso -> "Trap Isolation"
            AbIso -> "Abs"
            InclinePush -> "Incline Push"
            VerticalPush -> "Vertical Push"
        }
}
