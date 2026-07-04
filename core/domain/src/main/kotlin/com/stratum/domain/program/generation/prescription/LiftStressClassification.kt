@file:Suppress("MagicNumber", "MaxLineLength")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate

/**
 * Classifies movement stress for recovery scheduling (literature-aligned three-tier model).
 *
 * The lift-based overload reads the [LiftContext.kinematicProfile] — an explicit catalog property
 * set in `seed-lifts.json` — instead of inferring the class from movement-pattern heuristics and
 * lift-name regex. This means a leg press (`NonAxialCompound`) recovers as a compound, not as an
 * isolation like a leg extension (`Isolation`), even though both are Quad-primary lower-body lifts.
 *
 * The slot-based overload (`classifyLiftStressClass(slot, lift)`) still uses the slot's `repProfile`
 * and `movementPattern` because slots are templates, not specific lifts — the slot determines the
 * *expected* recovery class, and the lift that fills it should match.
 */
fun classifyLiftStressClass(
    repProfile: RpRepProfile,
    movementPattern: MovementPattern?,
    liftName: String,
): LiftStressClass {
    val lowerFreeWeightByPattern = movementPattern != null &&
        movementPattern in LOWER_FREE_WEIGHT_PATTERNS &&
        isLowerFreeWeightLiftName(liftName)
    if (repProfile == RpRepProfile.LowerFreeWeightCompound || lowerFreeWeightByPattern) {
        return LiftStressClass.HeavyAxial
    }
    if (repProfile == RpRepProfile.Isolation) return LiftStressClass.Isolation
    if (movementPattern in MAJOR_COMPOUND_PATTERNS) return LiftStressClass.UpperCompound
    return LiftStressClass.Isolation
}

fun classifyLiftStressClass(slot: WorkoutSlotTemplate, lift: LiftContext): LiftStressClass =
    classifyLiftStressClass(
        repProfile = classifyLiftRepProfile(slot, lift),
        movementPattern = lift.movementPattern,
        liftName = lift.name,
    )

/**
 * Metadata-truth classification when no slot template is available (repairs, validation).
 * Uses the lift's explicit [LiftContext.kinematicProfile] — no pattern/name heuristics.
 */
fun classifyLiftStressClass(lift: LiftContext): LiftStressClass =
    kinematicProfileToStressClass(lift.kinematicProfile)

/** Maps a [KinematicProfile] (from [LiftContext.kinematicProfile]) to [LiftStressClass]. */
fun kinematicProfileToStressClass(profile: KinematicProfile): LiftStressClass =
    when (profile) {
        KinematicProfile.AxialCompound -> LiftStressClass.HeavyAxial
        KinematicProfile.NonAxialCompound -> LiftStressClass.UpperCompound
        KinematicProfile.Isolation -> LiftStressClass.Isolation
    }

internal fun inferRepProfileForStressClassification(lift: LiftContext): RpRepProfile {
    val pattern = lift.movementPattern ?: return RpRepProfile.Isolation
    if (pattern in ISOLATION_MOVEMENT_PATTERNS) return RpRepProfile.Isolation
    if (pattern in LOWER_FREE_WEIGHT_PATTERNS && isLowerFreeWeightLiftName(lift.name)) {
        return RpRepProfile.LowerFreeWeightCompound
    }
    if (pattern in MAJOR_COMPOUND_PATTERNS) return RpRepProfile.LowerMachineOrUpperPress
    return RpRepProfile.Isolation
}

private val ISOLATION_MOVEMENT_PATTERNS =
    setOf(
        MovementPattern.ElbowFlexion,
        MovementPattern.ElbowExtension,
        MovementPattern.KneeFlexion,
        MovementPattern.KneeExtension,
        MovementPattern.PlantarFlexion,
        MovementPattern.ShoulderAbduction,
        MovementPattern.ShoulderHorizontalAbduction,
    )

private val LOWER_FREE_WEIGHT_PATTERNS = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

internal fun isLowerFreeWeightLiftName(name: String): Boolean {
    val normalized = name.lowercase()
    if (Regex("""\b(machine|smith)\b""").containsMatchIn(normalized)) return false
    return Regex("""\b(barbell|squat|deadlift|rdl|romanian)\b""").containsMatchIn(normalized) ||
        Regex("""\bsquat\b""").containsMatchIn(normalized)
}

/**
 * Dominant stress class among contributors crediting ≥ 1.0 fractional sets to a muscle in one session.
 * Sub-1.0 contributors (e.g. 0.5 secondary tags) do not escalate the class.
 */
fun dominantStressClass(
    contributors: List<Pair<LiftStressClass, Double>>,
): LiftStressClass {
    val ranked = contributors
        .filter { (_, credit) -> credit >= 1.0 }
        .map { (stressClass, _) -> stressClass }
    return ranked.maxByOrNull { it.ordinal } ?: LiftStressClass.Isolation
}

/**
 * Stress class used when pricing recovery for a muscle credit from a lift.
 * Compound secondaries (e.g. Glute from squats, Bicep from rows) inherit the lift's movement
 * class for volume accounting only — recovery debt is priced at [LiftStressClass.Isolation].
 * The primary mover (Quad on squats, Back on rows) keeps the compound class.
 */
fun recoveryStressClassForContributor(
    liftStressClass: LiftStressClass,
    isPrimaryContributor: Boolean,
): LiftStressClass =
    if (!isPrimaryContributor && liftStressClass != LiftStressClass.Isolation) {
        LiftStressClass.Isolation
    } else {
        liftStressClass
    }
