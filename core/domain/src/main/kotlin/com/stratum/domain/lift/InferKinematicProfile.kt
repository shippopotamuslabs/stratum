package com.stratum.domain.lift

/**
 * Deterministic kinematic classification for custom / inferred lifts when the catalog has no
 * explicit profile (import LLM does not return kinematic; [CustomLiftDefinition] must not
 * default everything to [KinematicProfile.Isolation]).
 *
 * Seed lifts keep their authored profile; this is for import stubs and custom definitions.
 */
fun inferKinematicProfile(
    patterns: Set<MovementPattern>,
    liftName: String? = null,
): KinematicProfile {
    if (patterns.isEmpty()) return KinematicProfile.Isolation
    val primary =
        patterns.firstOrNull { it !in SECONDARY_ONLY_HINTS } ?: patterns.first()
    return inferKinematicProfile(primary, liftName)
}

fun inferKinematicProfile(
    pattern: MovementPattern,
    liftName: String? = null,
): KinematicProfile {
    if (pattern in ISOLATION_PATTERNS) return KinematicProfile.Isolation
    if (pattern in AXIAL_CANDIDATE_PATTERNS && isFreeWeightAxialName(liftName)) {
        return KinematicProfile.AxialCompound
    }
    return KinematicProfile.NonAxialCompound
}

private val ISOLATION_PATTERNS: Set<MovementPattern> =
    setOf(
        MovementPattern.ElbowFlexion,
        MovementPattern.ElbowExtension,
        MovementPattern.KneeFlexion,
        MovementPattern.KneeExtension,
        MovementPattern.PlantarFlexion,
        MovementPattern.ShoulderAbduction,
        MovementPattern.ShoulderHorizontalAbduction,
        MovementPattern.ShoulderFlexion,
        MovementPattern.ShoulderAdduction,
        MovementPattern.WristFlexionExtension,
        MovementPattern.ScapularElevation,
        MovementPattern.SpinalFlexion,
        MovementPattern.HipAbduction,
        MovementPattern.HipAdduction,
    )

private val AXIAL_CANDIDATE_PATTERNS: Set<MovementPattern> =
    setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

/** Patterns that often appear only as secondary co-patterns (e.g. ElbowFlexion on a row). */
private val SECONDARY_ONLY_HINTS: Set<MovementPattern> =
    setOf(
        MovementPattern.ElbowFlexion,
        MovementPattern.ElbowExtension,
        MovementPattern.HipExtension,
    )

/**
 * Free-weight axial loading: barbell/dumbbell squat or hinge without machine/smith framing.
 * Matches the recovery helper used for lower free-weight stress classification.
 */
private fun isFreeWeightAxialName(name: String?): Boolean {
    if (name.isNullOrBlank()) return false
    val normalized = name.lowercase()
    if (Regex("""\b(machine|smith|cable|leg press|hack)\b""").containsMatchIn(normalized)) {
        return false
    }
    return Regex("""\b(barbell|dumbbell|deadlift|rdl|romanian|good morning)\b""")
        .containsMatchIn(normalized) ||
        Regex("""\bsquat\b""").containsMatchIn(normalized)
}
