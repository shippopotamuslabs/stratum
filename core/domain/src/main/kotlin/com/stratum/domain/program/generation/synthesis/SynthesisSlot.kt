package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.SiblingVariant
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.volume.TrainingDomain

enum class SynthesisSessionDomain {
    Upper,
    Lower,
    Full,
}

data class SynthesisSlot(
    val id: String,
    val pattern: String,
    val primaryMuscle: String,
    val role: SlotRole,
    val repProfile: RpRepProfile,
    val allowedDomains: Set<SynthesisSessionDomain>,
    val isCrossDomainTouch: Boolean,
    val siblingVariant: SiblingVariant?,
)

internal const val PLACEHOLDER_PROGRAM_NAME = "Generated Program"

private val PLACEHOLDER_SESSION_NAME = Regex("""^Session \d+$""")

internal fun placeholderWorkoutName(position: Int): String = "Session ${position + 1}"

/**
 * Renumbers `Session N` placeholders by calendar weekday so fallback names read in schedule order
 * even when CP-SAT permutes cycle position vs training day.
 */
internal fun relabelPlaceholderSessionNamesByWeekday(
    workouts: List<ProgramWorkoutDraft>,
): List<ProgramWorkoutDraft> {
    if (workouts.none { workout -> PLACEHOLDER_SESSION_NAME.matches(workout.name.orEmpty()) }) {
        return workouts
    }
    val renameByPosition =
        workouts
            .sortedBy { workout -> weekdaySortKey(workout.weekday, workout.position) }
            .mapIndexed { calendarIndex, workout ->
                (workout.position ?: calendarIndex) to "Session ${calendarIndex + 1}"
            }
            .toMap()
    return workouts.map { workout ->
        val position = workout.position ?: return@map workout
        renameByPosition[position]?.let { newName -> workout.copy(name = newName) } ?: workout
    }
}

private fun weekdaySortKey(weekday: String?, position: Int?): Int =
    weekday
        ?.let { runCatching { Weekday.valueOf(it).ordinal }.getOrNull() }
        ?: position
        ?: Int.MAX_VALUE


internal fun synthesisSessionDomainToTrainingDomain(domain: SynthesisSessionDomain): TrainingDomain? =
    when (domain) {
        SynthesisSessionDomain.Upper -> TrainingDomain.Upper
        SynthesisSessionDomain.Lower -> TrainingDomain.Lower
        SynthesisSessionDomain.Full -> null
    }
