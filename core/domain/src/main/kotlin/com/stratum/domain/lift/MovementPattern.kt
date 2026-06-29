package com.stratum.domain.lift

/**
 * How a [Lift] moves — the categorical slot a lift occupies (squat pattern, horizontal push,
 * etc.). Values are grouped logically: lower body, then upper-body compound, then isolation.
 * The deltoid head isos are split into [LateralDeltIso], [RearDeltIso], and [AnteriorDeltIso]
 * so program generation can target each head independently (a balanced upper day covers a
 * lateral raise AND a rear-delt movement, which a single `DeltIso` could not express).
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
    LateralDeltIso,
    RearDeltIso,
    AnteriorDeltIso,

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
            LateralDeltIso -> "Lateral Delt Isolation"
            RearDeltIso -> "Rear Delt Isolation"
            AnteriorDeltIso -> "Front Delt Isolation"
            GluteIso -> "Glute Isolation"
            ForearmIso -> "Forearm Isolation"
            TrapIso -> "Trap Isolation"
            AbIso -> "Abs"
            InclinePush -> "Incline Push"
            VerticalPush -> "Vertical Push"
        }
}
