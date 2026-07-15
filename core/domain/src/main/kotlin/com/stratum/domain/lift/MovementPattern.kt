package com.stratum.domain.lift

/**
 * How a [Lift] moves — the categorical slot a lift occupies (squat pattern, horizontal push,
 * etc.). Values are grouped logically: lower body, then upper-body compound, then isolation.
 * The deltoid head isos are split into [ShoulderAbduction], [ShoulderHorizontalAbduction], and [ShoulderFlexion]
 * so program generation can target each head independently (a balanced upper day covers a
 * lateral raise AND a rear-delt movement, which a single `DeltIso` could not express).
 *
 * A lift's pattern is orthogonal to which muscles it works ([Lift.primaryMuscles]) — many
 * lifts in the same pattern share muscle contributions, but the pattern drives exercise
 * selection and split assignment in program generation (OS-6).
 */
enum class MovementPattern {
    // Primary Target: Quad / Glute
    SquatPattern,
    // Primary Target: Hamstring / Glute / Lower Back
    HipHinge,
    // Primary Target: Quad
    KneeExtension,
    // Primary Target: Hamstring
    KneeFlexion,
    // Primary Target: Calf
    PlantarFlexion,

    // Primary Target: Chest
    HorizontalPress,
    // Primary Target: Back
    VerticalPull,
    // Primary Target: Back
    HorizontalPull,
    // Primary Target: Chest
    ShoulderAdduction,
    // Primary Target: Tricep
    ElbowExtension,
    // Primary Target: Bicep
    ElbowFlexion,
    // Primary Target: Side Delt
    ShoulderAbduction,
    // Primary Target: Rear Delt
    ShoulderHorizontalAbduction,
    // Primary Target: Front Delt
    ShoulderFlexion,

    // Primary Target: Glute
    HipExtension,
    // Primary Target: Forearm
    WristFlexionExtension,
    // Primary Target: Trap
    ScapularElevation,
    // Primary Target: Ab
    SpinalFlexion,
    // Primary Target: Chest (Upper) / Front Delt
    InclinePress,
    // Primary Target: Front Delt
    VerticalPress,
    // Primary Target: Glute Medius/Minimus (Abductors)
    HipAbduction,
    // Primary Target: Adductors
    HipAdduction,
    ;

    /**
     * Human-readable label for UI grouping. Display strings live in the domain here (not the
     * feature layer) because the grouping is shared between OS-4 selection and OS-6 generation.
     */
    val displayName: String
        get() = when (this) {
            SquatPattern -> "Squat"
            HipHinge -> "Hinge"
            KneeExtension -> "Leg extension"
            KneeFlexion -> "Leg curl"
            PlantarFlexion -> "Calf raise"
            HorizontalPress -> "Horizontal push"
            VerticalPull -> "Vertical pull"
            HorizontalPull -> "Horizontal pull"
            ShoulderAdduction -> "Chest fly"
            ElbowExtension -> "Triceps extension"
            ElbowFlexion -> "Biceps curl"
            ShoulderAbduction -> "Lateral raise"
            ShoulderHorizontalAbduction -> "Rear delt"
            ShoulderFlexion -> "Front raise"
            HipExtension -> "Hip thrust"
            WristFlexionExtension -> "Forearm / wrist"
            ScapularElevation -> "Shrug"
            SpinalFlexion -> "Core / abs"
            InclinePress -> "Incline push"
            VerticalPress -> "Vertical push"
            HipAbduction -> "Hip abduction"
            HipAdduction -> "Hip adduction"
        }

    val secondaryPatterns: Set<MovementPattern>
        get() = when (this) {
            HorizontalPress -> setOf(ElbowExtension)
            InclinePress -> setOf(ElbowExtension)
            VerticalPress -> setOf(ElbowExtension)
            VerticalPull -> setOf(ElbowFlexion)
            HorizontalPull -> setOf(ElbowFlexion)
            SquatPattern -> setOf(HipExtension)
            HipHinge -> setOf(HipExtension)
            else -> emptySet()
        }
}
