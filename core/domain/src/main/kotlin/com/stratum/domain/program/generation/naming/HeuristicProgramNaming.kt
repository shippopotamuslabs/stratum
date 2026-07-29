package com.stratum.domain.program.generation.naming

import com.stratum.domain.lift.CORE_MUSCLE_GROUPS
import com.stratum.domain.lift.LOWER_BODY_MUSCLE_GROUPS
import com.stratum.domain.lift.LiftCategoryRegion
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.categoryRegion
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val REGION_THRESHOLD = 0.70

private val PROGRAM_NAME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Body-region bucket used only for generated workout **titles** (Upper / Lower / Full Body).
 *
 * Polish-only — not a split type, not [com.stratum.domain.program.generation.structure.SessionRegionFilter],
 * not volume [com.stratum.domain.program.generation.volume.TrainingDomain], and not recovery input.
 */
internal enum class WorkoutTitleRegion {
    Upper,
    Lower,
    FullBody,
}

/**
 * Assigns a local date/time program name and heuristic workout titles.
 *
 * **Program name:** `yyyy-MM-dd HH:mm:ss` in the clock's zone (device local by default).
 *
 * **Workout titles:** share of lifts (by **lift count**, not set volume):
 * - [Upper A], [Lower B], [Full Body A], … when a region holds **more than** 70% of lifts
 * - Otherwise [Full Body X]
 *
 * Letter suffixes are independent per prefix and assigned in workout [position] order
 * (`A`…`Z`, then `AA`, `AB`, … — never throws).
 *
 * **Lift classification (title contribution only):**
 * 1. Any primary ∈ [LOWER_BODY_MUSCLE_GROUPS] → Lower (if a lift lists both lower and upper
 *    primaries, Lower wins — same primary-first rule as category region)
 * 2. Else any primary outside [CORE_MUSCLE_GROUPS] → Upper
 * 3. Else any movement pattern with [LiftCategoryRegion.Lower] → Lower
 *    (covers LowerBack-primary hinges / deadlifts)
 * 4. Else any upper pattern → Upper
 * 5. Else Neither (core-only, empty, or missing metadata) — counts in the denominator only
 *
 * Missing [liftsById] entries and null lift ids are Neither.
 */
fun applyHeuristicProgramNames(
    program: ProgramDraft,
    liftsById: Map<String, LiftContext>,
    clock: Clock = Clock.systemDefaultZone(),
): ProgramDraft {
    val programName = formatProgramName(clock)
    val prefixCounts = mutableMapOf<String, Int>()
    val ordered =
        program.workouts
            .mapIndexed { listIndex, workout -> listIndex to workout }
            .sortedWith(
                compareBy<Pair<Int, ProgramWorkoutDraft>> { (_, workout) ->
                    workout.position ?: Int.MAX_VALUE
                }.thenBy { (listIndex, _) -> listIndex },
            )

    val nameByListIndex = mutableMapOf<Int, String>()
    for ((listIndex, workout) in ordered) {
        val region = classifyWorkoutRegion(workout, liftsById)
        val prefix = region.displayPrefix
        val letterIndex = prefixCounts.getOrDefault(prefix, 0)
        prefixCounts[prefix] = letterIndex + 1
        nameByListIndex[listIndex] = "$prefix ${letterForIndex(letterIndex)}"
    }

    val renamedWorkouts =
        program.workouts.mapIndexed { index, workout ->
            workout.copy(name = nameByListIndex.getValue(index))
        }
    return program.copy(name = programName, workouts = renamedWorkouts)
}

internal fun formatProgramName(clock: Clock): String =
    LocalDateTime.now(clock).format(PROGRAM_NAME_FORMATTER)

internal fun classifyWorkoutRegion(
    workout: ProgramWorkoutDraft,
    liftsById: Map<String, LiftContext>,
): WorkoutTitleRegion {
    val lifts = workout.workoutLifts.orEmpty()
    if (lifts.isEmpty()) return WorkoutTitleRegion.FullBody

    var upper = 0
    var lower = 0
    for (lift in lifts) {
        when (classifyLiftRegion(lift.liftId, liftsById)) {
            LiftTitleContribution.Upper -> upper++
            LiftTitleContribution.Lower -> lower++
            LiftTitleContribution.Neither -> Unit
        }
    }

    val total = lifts.size.toDouble()
    val upperShare = upper / total
    val lowerShare = lower / total
    return when {
        upperShare > REGION_THRESHOLD -> WorkoutTitleRegion.Upper
        lowerShare > REGION_THRESHOLD -> WorkoutTitleRegion.Lower
        else -> WorkoutTitleRegion.FullBody
    }
}

/** Per-lift contribution to workout title region (polish only). */
internal enum class LiftTitleContribution {
    Upper,
    Lower,
    Neither,
}

private fun classifyLiftRegion(
    liftId: String?,
    liftsById: Map<String, LiftContext>,
): LiftTitleContribution {
    if (liftId.isNullOrBlank()) return LiftTitleContribution.Neither
    val meta = liftsById[liftId] ?: return LiftTitleContribution.Neither
    return classifyLiftTitleContribution(
        primaryMuscles = meta.primaryMuscles,
        movementPatterns = meta.movementPatterns,
        movementPattern = meta.movementPattern,
    )
}

/**
 * Primary-muscle first, then pattern fallback for core-only / empty primaries
 * (e.g. LowerBack-primary [MovementPattern.HipHinge] deadlift → Lower).
 */
internal fun classifyLiftTitleContribution(
    primaryMuscles: Set<MuscleGroup>,
    movementPatterns: Set<MovementPattern> = emptySet(),
    movementPattern: MovementPattern? = null,
): LiftTitleContribution {
    if (primaryMuscles.any { it in LOWER_BODY_MUSCLE_GROUPS }) {
        return LiftTitleContribution.Lower
    }
    if (primaryMuscles.any { it !in CORE_MUSCLE_GROUPS }) {
        return LiftTitleContribution.Upper
    }

    val patterns =
        buildSet {
            addAll(movementPatterns)
            movementPattern?.let { add(it) }
        }
    if (patterns.any { it.categoryRegion == LiftCategoryRegion.Lower }) {
        return LiftTitleContribution.Lower
    }
    if (patterns.any { it.categoryRegion == LiftCategoryRegion.Upper }) {
        return LiftTitleContribution.Upper
    }
    return LiftTitleContribution.Neither
}

private val WorkoutTitleRegion.displayPrefix: String
    get() =
        when (this) {
            WorkoutTitleRegion.Upper -> "Upper"
            WorkoutTitleRegion.Lower -> "Lower"
            WorkoutTitleRegion.FullBody -> "Full Body"
        }

private const val LETTER_RADIX = 26

/**
 * 0-based index → `A`…`Z`, then `AA`, `AB`, … (base-26, Excel-style).
 * Negative indices clamp to `A` — polish must not abort generation.
 */
internal fun letterForIndex(index: Int): String {
    // Base-26 with A=1 style for multi-char: 0→A … 25→Z, 26→AA
    var n = index.coerceAtLeast(0)
    val chars = ArrayDeque<Char>()
    do {
        chars.addFirst('A' + (n % LETTER_RADIX))
        n = n / LETTER_RADIX - 1
    } while (n >= 0)
    return chars.joinToString("")
}
