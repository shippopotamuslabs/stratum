@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.validation

import com.stratum.domain.program.generation.context.MAJOR_MUSCLES
import com.stratum.domain.program.generation.context.MINUTES_PER_HARD_SET
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.isPullPattern
import com.stratum.domain.program.generation.context.isPushPattern
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.FrequencyTier
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.frequencyTierForWeeklySets
import com.stratum.domain.program.generation.reference.sessionDurationBudgetMinutes
import com.stratum.domain.program.generation.split.calendarAdjacentTrainingDayPairs
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget
import com.stratum.domain.program.generation.volume.resolveMuscleWeeklyTarget
import kotlin.math.max

enum class ValidationSeverity {
    Error,
    Warning,
}

data class ValidationIssue(
    val code: String,
    val severity: ValidationSeverity,
    val message: String,
    val details: Map<String, Any?>? = null,
)

data class ValidationSummary(
    val weeklyMuscleVolume: Map<String, Double>,
    val workoutHardSets: List<Int>,
    val estimatedWorkoutMinutes: List<Double>,
    val muscleWeeklyFrequency: Map<String, Int>,
    val weeklyPushSets: Double,
    val weeklyPullSets: Double,
)

data class ValidationReport(
    val pass: Boolean,
    val issues: List<ValidationIssue>,
    val summary: ValidationSummary,
)

/** Mon=0 … Sun=6 weekday names aligned with [trainingWeekdayIndices]. */
private val WEEKDAY_NAMES =
    listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** Relative weekly-volume slack below min / above max (≈ practical programming noise). */
const val VOLUME_RELATIVE_TOLERANCE = 0.2

/** Minimum hard-set slack regardless of target size. */
const val VOLUME_ABSOLUTE_TOLERANCE = 3.0

private const val PUSH_PULL_TOLERANCE_RATIO = 0.35

/** Share of weekly volume in one workout before flagging imbalance (warning only). */
private const val WORKOUT_VOLUME_SHARE_THRESHOLD = 0.85

fun validateProgramMetrics(
    program: Any?,
    context: ProgramGenerationContext,
): ValidationReport {
    val draft = normalizeProgramDraft(program)
    val weeklyMuscleVolume = computeWeeklyMuscleVolume(draft, context)
    val workoutHardSets = draft.workouts.map(::countWorkoutHardSets)
    val estimatedWorkoutMinutes = workoutHardSets.map { sets -> sets * MINUTES_PER_HARD_SET }
    val muscleWeeklyFrequency = computeMuscleWeeklyFrequency(draft, context)
    val (pushSets, pullSets) = computeWeeklyPushPullVolume(draft, context)

    val issues = buildList {
        addAll(validateWorkoutDuration(draft, context, estimatedWorkoutMinutes))
        addAll(validateWeeklyMuscleVolume(weeklyMuscleVolume, context))
        addAll(validateWorkoutMuscleVolume(draft, context, weeklyMuscleVolume))
        addAll(validateSessionMuscleVolumeCap(draft, context))
        addAll(validateMuscleFrequency(muscleWeeklyFrequency, weeklyMuscleVolume, context))
        addAll(validateNoDuplicateLifts(draft))
        addAll(validateExerciseOrdering(draft, context))
        addAll(validatePushPullBalance(pushSets, pullSets))
        addAll(validateCalendarAdjacentMuscleExposure(draft, context))
    }

    val hasErrors = issues.any { it.severity == ValidationSeverity.Error }
    return ValidationReport(
        pass = !hasErrors,
        issues = issues,
        summary = ValidationSummary(
            weeklyMuscleVolume = weeklyMuscleVolume,
            workoutHardSets = workoutHardSets,
            estimatedWorkoutMinutes = estimatedWorkoutMinutes,
            muscleWeeklyFrequency = muscleWeeklyFrequency,
            weeklyPushSets = pushSets,
            weeklyPullSets = pullSets,
        ),
    )
}

fun validateWorkoutDuration(
    program: ProgramDraft,
    context: ProgramGenerationContext,
    estimatedMinutes: List<Double>? = null,
): List<ValidationIssue> {
    val sessionLength = context.sessionLengthMinutes
    if (sessionLength == null || sessionLength <= 0) return emptyList()
    val minutes = estimatedMinutes
        ?: program.workouts.map { workout -> countWorkoutHardSets(workout) * MINUTES_PER_HARD_SET }
    val budget = sessionDurationBudgetMinutes(sessionLength).toDouble()
    val issues = mutableListOf<ValidationIssue>()
    program.workouts.forEachIndexed { index, workout ->
        val estimate = minutes.getOrElse(index) { 0.0 }
        if (estimate > budget) {
            issues += ValidationIssue(
                code = "WORKOUT_DURATION_EXCEEDED",
                severity = ValidationSeverity.Error,
                message = "Workout \"${workout.name ?: index}\" estimated ${"%.1f".format(estimate)} min " +
                    "exceeds budget $sessionLength min (cap ${"%.1f".format(budget)} with tolerance).",
                details = mapOf(
                    "workoutIndex" to index,
                    "estimatedMinutes" to estimate,
                    "budgetMinutes" to sessionLength,
                ),
            )
        }
    }
    return issues
}

/**
 * Fuzzy weekly-volume band: allows ~20% (min 3 sets) below [min] and above [max]. Hypertrophy
 * programming is not exact — validation should catch only material misses, not minor drift.
 */
fun weeklyVolumeToleranceBand(target: WeeklyVolumeTarget): Pair<Double, Double> {
    val relative = max(
        VOLUME_ABSOLUTE_TOLERANCE,
        max(
            target.aim * VOLUME_RELATIVE_TOLERANCE,
            target.max * (VOLUME_RELATIVE_TOLERANCE * 0.75),
        ),
    )
    val minFloor = max(0.0, target.min - relative)
    val maxCeiling = target.max + relative
    return minFloor to maxCeiling
}

fun validateWeeklyMuscleVolume(
    weeklyMuscleVolume: Map<String, Double>,
    context: ProgramGenerationContext,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    val musclesToCheck = (weeklyMuscleVolume.keys + MAJOR_MUSCLES).toSet()

    for (muscle in musclesToCheck) {
        val level = context.specialization[muscle] ?: "Maintenance"
        if (level == "Omit") {
            val volume = weeklyMuscleVolume[muscle] ?: 0.0
            if (volume > 0.01) {
                issues += ValidationIssue(
                    code = "OMITTED_MUSCLE_HAS_VOLUME",
                    severity = ValidationSeverity.Error,
                    message = "Muscle $muscle is Omitted but received ${"%.1f".format(volume)} weekly hard-set credits.",
                )
            }
            continue
        }

        val volume = weeklyMuscleVolume[muscle] ?: 0.0
        if (volume <= 0.01) continue

        val target = resolveMuscleWeeklyTarget(muscle, level, context)
        val (minFloor, maxCeiling) = weeklyVolumeToleranceBand(target)
        if (volume < minFloor) {
            issues += ValidationIssue(
                code = "WEEKLY_VOLUME_BELOW_TARGET",
                severity = ValidationSeverity.Error,
                message = "$muscle weekly volume ${"%.1f".format(volume)} is materially below target minimum " +
                    "${"%.1f".format(target.min)} ($level, aim ${"%.1f".format(target.aim)}; " +
                    "fuzzy floor ${"%.1f".format(minFloor)}).",
                details = mapOf("muscle" to muscle, "volume" to volume, "target" to target, "minFloor" to minFloor),
            )
        }
        if (volume > maxCeiling) {
            issues += ValidationIssue(
                code = "WEEKLY_VOLUME_ABOVE_MRV",
                severity = ValidationSeverity.Error,
                message = "$muscle weekly volume ${"%.1f".format(volume)} materially exceeds MRV ceiling " +
                    "${"%.1f".format(target.max)} (fuzzy cap ${"%.1f".format(maxCeiling)}).",
                details = mapOf("muscle" to muscle, "volume" to volume, "target" to target, "maxCeiling" to maxCeiling),
            )
        }
    }
    return issues
}

fun validateWorkoutMuscleVolume(
    program: ProgramDraft,
    context: ProgramGenerationContext,
    weeklyMuscleVolume: Map<String, Double>,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    program.workouts.forEachIndexed { workoutIndex, workout ->
        val workoutVolume = computeWorkoutMuscleVolume(workout, context)
        for ((muscle, weeklyTotal) in weeklyMuscleVolume) {
            if (weeklyTotal <= 0) continue
            val inWorkout = workoutVolume[muscle] ?: 0.0
            if (inWorkout <= 0) continue
            val share = inWorkout / weeklyTotal
            if (share > WORKOUT_VOLUME_SHARE_THRESHOLD) {
                issues += ValidationIssue(
                    code = "WORKOUT_VOLUME_IMBALANCED",
                    severity = ValidationSeverity.Warning,
                    message = "$muscle: ${(share * 100).toInt()}% of weekly volume is in workout " +
                        "\"${workout.name ?: workoutIndex}\" — distribute across sessions.",
                    details = mapOf(
                        "muscle" to muscle,
                        "workoutIndex" to workoutIndex,
                        "share" to share,
                        "inWorkout" to inWorkout,
                        "weeklyTotal" to weeklyTotal,
                    ),
                )
            }
        }
    }
    return issues
}

fun validateSessionMuscleVolumeCap(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    program.workouts.forEachIndexed { workoutIndex, workout ->
        val workoutVolume = computeWorkoutMuscleVolume(workout, context)
        for ((muscle, credits) in workoutVolume) {
            if (credits > SESSION_MUSCLE_SET_CAP + 0.01) {
                issues += ValidationIssue(
                    code = "SESSION_MUSCLE_VOLUME_HIGH",
                    severity = ValidationSeverity.Error,
                    message = "$muscle: ${"%.1f".format(credits)} fractional sets in workout " +
                        "\"${workout.name ?: workoutIndex}\" exceeds $SESSION_MUSCLE_SET_CAP session cap.",
                    details = mapOf(
                        "muscle" to muscle,
                        "workoutIndex" to workoutIndex,
                        "credits" to credits,
                        "cap" to SESSION_MUSCLE_SET_CAP,
                    ),
                )
            }
        }
    }
    return issues
}

fun validateMuscleFrequency(
    muscleWeeklyFrequency: Map<String, Int>,
    weeklyMuscleVolume: Map<String, Double>,
    context: ProgramGenerationContext,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    for (muscle in MAJOR_MUSCLES) {
        val level = context.specialization[muscle] ?: "Maintenance"
        if (level == "Omit") continue
        val frequency = muscleWeeklyFrequency[muscle] ?: 0
        val weeklyAim = weeklyMuscleVolume[muscle] ?: 0.0
        if (weeklyAim <= 0) continue
        val tier = frequencyTierForWeeklySets(weeklyAim)
        val minRequired = when (tier) {
            FrequencyTier.One -> 1
            FrequencyTier.Two -> 2
            FrequencyTier.Three -> 3
        }
        if (frequency < minRequired) {
            issues += ValidationIssue(
                code = "MUSCLE_FREQUENCY_LOW",
                severity = if (level == "Specialize") ValidationSeverity.Error else ValidationSeverity.Warning,
                message = "$muscle is trained ${frequency}× per cycle; prefer ≥${minRequired}× for " +
                    "${weeklyAim.toInt()} weekly sets (TABLE 7.6).",
                details = mapOf(
                    "muscle" to muscle,
                    "frequency" to frequency,
                    "minRequired" to minRequired,
                    "weeklyAim" to weeklyAim,
                ),
            )
        }
    }
    return issues
}

fun validateActiveSlotSelections(
    sessionPlans: List<com.stratum.domain.program.generation.prescription.SessionSlotPlan>,
    selectionsBySession: List<List<com.stratum.domain.program.generation.selection.SelectedSlotLift>>,
    context: ProgramGenerationContext,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    sessionPlans.forEachIndexed { sessionIndex, plan ->
        val selectedSlotIds = selectionsBySession.getOrElse(sessionIndex) { emptyList() }
            .map { selection -> selection.slot.slotId }
            .toSet()
        for (slot in plan.filledSlots) {
            if (slot.setCount <= 0) continue
            if (slot.slotId in selectedSlotIds) continue
            val candidateCount =
                com.stratum.domain.program.generation.selection.candidatesForSlot(
                    slot.toWorkoutSlotTemplate(),
                    context,
                ).size
            issues +=
                ValidationIssue(
                    code = "UNSELECTED_ACTIVE_SLOT",
                    severity = ValidationSeverity.Error,
                    message =
                        "Workout \"${plan.name}\" slot \"${slot.slotId}\" has ${slot.setCount} sets " +
                            "but no lift was selected ($candidateCount catalog candidates).",
                    details =
                        mapOf(
                            "sessionIndex" to sessionIndex,
                            "slotId" to slot.slotId,
                            "pattern" to slot.pattern,
                            "candidateCount" to candidateCount,
                        ),
                )
        }
    }
    return issues
}

fun validateNoDuplicateLifts(program: ProgramDraft): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    program.workouts.forEachIndexed { workoutIndex, workout ->
        val seen = mutableSetOf<String>()
        val dupes = mutableSetOf<String>()
        for (lift in workout.workoutLifts.orEmpty()) {
            val liftId = lift.liftId.orEmpty()
            if (liftId.isEmpty()) continue
            if (liftId in seen) dupes += liftId else seen += liftId
        }
        if (dupes.isNotEmpty()) {
            issues += ValidationIssue(
                code = "DUPLICATE_LIFT_IN_WORKOUT",
                severity = ValidationSeverity.Error,
                message = "Workout \"${workout.name ?: workoutIndex}\" repeats liftId(s) " +
                    "${dupes.joinToString(", ")} within the same workout.",
                details = mapOf("workoutIndex" to workoutIndex, "liftIds" to dupes.toList()),
            )
        }
    }
    return issues
}

fun validateExerciseOrdering(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    program.workouts.forEachIndexed { workoutIndex, workout ->
        val lifts = workout.workoutLifts.orEmpty().sortedBy { it.position ?: 0 }
        var consecutiveSamePrimary = 0
        var lastPrimaryKey = ""
        for (lift in lifts) {
            val liftId = lift.liftId.orEmpty()
            val meta = context.liftsById[liftId]
            val primaryKey = meta?.primaryMuscles?.joinToString("|") ?: liftId
            if (primaryKey.isEmpty()) continue
            if (primaryKey == lastPrimaryKey) {
                consecutiveSamePrimary += 1
            } else {
                consecutiveSamePrimary = 1
                lastPrimaryKey = primaryKey
            }
            if (consecutiveSamePrimary >= 3) {
                issues += ValidationIssue(
                    code = "EXERCISE_ORDER_STACKED",
                    severity = ValidationSeverity.Warning,
                    message = "Workout \"${workout.name ?: workoutIndex}\" stacks $consecutiveSamePrimary " +
                        "consecutive exercises for the same primary muscle ($primaryKey). " +
                        "Consider interleaving antagonists.",
                    details = mapOf("workoutIndex" to workoutIndex, "liftId" to liftId, "primaryKey" to primaryKey),
                )
                break
            }
        }
    }
    return issues
}

/**
 * Advisory check: same muscle meaningfully exposed on calendar-adjacent training days using lift
 * metadata ([computeWorkoutMuscleVolume]). Emitted as warnings only — users can edit the draft
 * after generation rather than failing the pipeline.
 */
fun validateCalendarAdjacentMuscleExposure(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): List<ValidationIssue> {
    val exposuresByWorkoutName =
        program.workouts.associate { workout ->
            workout.name.orEmpty() to computeWorkoutMuscleVolume(workout, context)
        }
    val workoutByDayIndex =
        program.workouts
            .mapNotNull { workout ->
                val weekday = workout.weekday ?: return@mapNotNull null
                val dayIndex = trainingWeekdayIndices(listOf(weekday)).singleOrNull() ?: return@mapNotNull null
                dayIndex to workout
            }.toMap()
    val adjacentPairs = calendarAdjacentTrainingDayPairs(trainingWeekdayIndices(context.weekdays))
    val issues = mutableListOf<ValidationIssue>()
    for ((earlierDayIndex, laterDayIndex) in adjacentPairs) {
        val earlierWorkout = workoutByDayIndex[earlierDayIndex] ?: continue
        val laterWorkout = workoutByDayIndex[laterDayIndex] ?: continue
        val earlierExposure = exposuresByWorkoutName[earlierWorkout.name.orEmpty()].orEmpty()
        val laterExposure = exposuresByWorkoutName[laterWorkout.name.orEmpty()].orEmpty()
        for (muscle in earlierExposure.keys + laterExposure.keys) {
            val earlierDose = earlierExposure[muscle] ?: 0.0
            val laterDose = laterExposure[muscle] ?: 0.0
            if (
                earlierDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE ||
                laterDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
            ) {
                continue
            }
            val earlierDay = WEEKDAY_NAMES.getOrElse(earlierDayIndex) { earlierDayIndex.toString() }
            val laterDay = WEEKDAY_NAMES.getOrElse(laterDayIndex) { laterDayIndex.toString() }
            val level = context.specialization[muscle] ?: "Maintenance"
            issues +=
                ValidationIssue(
                    code = "CALENDAR_ADJACENT_MUSCLE_EXPOSURE",
                    severity = ValidationSeverity.Warning,
                    message =
                        "$muscle ($level) is meaningfully exposed on calendar-adjacent days " +
                            "$earlierDay (${"%.1f".format(earlierDose)} credits) and " +
                            "$laterDay (${"%.1f".format(laterDose)} credits).",
                    details =
                        mapOf(
                            "muscle" to muscle,
                            "level" to level,
                            "earlierDay" to earlierDay,
                            "laterDay" to laterDay,
                            "earlierDose" to earlierDose,
                            "laterDose" to laterDose,
                            "earlierWorkout" to earlierWorkout.name,
                            "laterWorkout" to laterWorkout.name,
                        ),
                )
        }
    }
    return issues
}

fun validatePushPullBalance(
    pushSets: Double,
    pullSets: Double,
): List<ValidationIssue> {
    val total = pushSets + pullSets
    if (total < 6) return emptyList()
    val imbalance = kotlin.math.abs(pushSets - pullSets) / total
    if (imbalance > PUSH_PULL_TOLERANCE_RATIO) {
        return listOf(
            ValidationIssue(
                code = "PUSH_PULL_IMBALANCE",
                severity = ValidationSeverity.Warning,
                message = "Push/pull weekly set credits ${"%.1f".format(pushSets)} push vs " +
                    "${"%.1f".format(pullSets)} pull (${(imbalance * 100).toInt()}% imbalance; " +
                    "keep within ${(PUSH_PULL_TOLERANCE_RATIO * 100).toInt()}%).",
                details = mapOf("pushSets" to pushSets, "pullSets" to pullSets, "imbalance" to imbalance),
            ),
        )
    }
    return emptyList()
}

fun formatValidationFeedback(report: ValidationReport): String {
    val errors = report.issues.filter { it.severity == ValidationSeverity.Error }
    val warnings = report.issues.filter { it.severity == ValidationSeverity.Warning }
    return buildString {
        appendLine("Program metric validation failed. Fix these issues and regenerate:")
        errors.forEach { issue -> appendLine("- [ERROR ${issue.code}] ${issue.message}") }
        warnings.forEach { issue -> appendLine("- [WARN ${issue.code}] ${issue.message}") }
        appendLine()
        appendLine("Summary:")
        appendLine("- Weekly muscle volume (hard-set credits): ${report.summary.weeklyMuscleVolume}")
        appendLine("- Workout hard sets: ${report.summary.workoutHardSets}")
        appendLine("- Estimated workout minutes: ${report.summary.estimatedWorkoutMinutes}")
        appendLine("- Muscle weekly frequency: ${report.summary.muscleWeeklyFrequency}")
        appendLine(
            "- Push/pull credits: ${"%.1f".format(report.summary.weeklyPushSets)} / " +
                "${"%.1f".format(report.summary.weeklyPullSets)}",
        )
    }
}

fun buildMetricRetryFeedback(
    report: ValidationReport,
    context: ProgramGenerationContext,
    rejectedLiftIds: List<String>,
): String {
    return buildString {
        append(formatValidationFeedback(report))
        appendLine()
        appendLine("Regenerate a COMPLETE program with exactly ${context.trainingDaysPerWeek} workouts.")
        appendLine("Hit at least MEV weekly hard-set credits per muscle; train each major muscle ≥2×/week.")
        appendLine("Use only liftId values from provenLifts/availableLifts in the onboarding payload.")
        if (rejectedLiftIds.isNotEmpty()) {
            appendLine(
                "These liftId values were removed because they are not enabled: ${rejectedLiftIds.joinToString(", ")}.",
            )
        }
        appendLine("Call validate_program_metrics on your draft and fix every ERROR before returning JSON.")
    }
}

private fun normalizeProgramDraft(program: Any?): ProgramDraft {
    if (program !is ProgramDraft) {
        return ProgramDraft(workouts = emptyList())
    }
    return program.copy(
        name = program.name ?: "Program",
        workouts = program.workouts.map { workout ->
            workout.copy(
                name = workout.name ?: "Workout",
                weekday = workout.weekday,
                position = workout.position ?: 0,
                workoutLifts = workout.workoutLifts?.toMutableList(),
            )
        }.toList(),
    )
}

private fun countWorkoutHardSets(workout: ProgramWorkoutDraft): Int {
    var total = 0
    for (lift in workout.workoutLifts.orEmpty()) {
        total += lift.workoutLiftSets?.size ?: 0
    }
    return total
}

private fun computeWeeklyMuscleVolume(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): Map<String, Double> {
    val totals = mutableMapOf<String, Double>()
    for (workout in program.workouts) {
        val workoutVolume = computeWorkoutMuscleVolume(workout, context)
        for ((muscle, credits) in workoutVolume) {
            totals[muscle] = (totals[muscle] ?: 0.0) + credits
        }
    }
    return totals
}

/** Fractional hard-set credits per muscle in one workout (primary 1.0, secondary 0.5). */
fun computeWorkoutMuscleVolume(
    workout: ProgramWorkoutDraft,
    context: ProgramGenerationContext,
): Map<String, Double> {
    val totals = mutableMapOf<String, Double>()
    for (lift in workout.workoutLifts.orEmpty()) {
        val liftId = lift.liftId.orEmpty()
        val setCount = lift.workoutLiftSets?.size ?: 0
        if (setCount == 0) continue
        val meta = context.liftsById[liftId] ?: continue
        for (muscle in meta.primaryMuscles) {
            totals[muscle] = (totals[muscle] ?: 0.0) + setCount * PRIMARY_SET_CREDIT
        }
        for (muscle in meta.secondaryMuscles) {
            if (muscle in meta.primaryMuscles) continue
            totals[muscle] = (totals[muscle] ?: 0.0) + setCount * SECONDARY_SET_CREDIT
        }
    }
    return totals
}

private fun computeMuscleWeeklyFrequency(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): Map<String, Int> {
    val frequency = mutableMapOf<String, Int>()
    for (workout in program.workouts) {
        val muscles = computeWorkoutMuscleVolume(workout, context).keys
        for (muscle in muscles) {
            frequency[muscle] = (frequency[muscle] ?: 0) + 1
        }
    }
    return frequency
}

private fun computeWeeklyPushPullVolume(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): Pair<Double, Double> {
    var pushSets = 0.0
    var pullSets = 0.0
    for (workout in program.workouts) {
        for (lift in workout.workoutLifts.orEmpty()) {
            val liftId = lift.liftId.orEmpty()
            val setCount = lift.workoutLiftSets?.size ?: 0
            if (setCount == 0) continue
            val pattern = context.movementPatternByLiftId[liftId].orEmpty()
            if (isPushPattern(pattern)) pushSets += setCount
            if (isPullPattern(pattern)) pullSets += setCount
        }
    }
    return pushSets to pullSets
}
