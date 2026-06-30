@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.split

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.ProgramGenerationContext

private val WEEKDAY_ORDER = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
)

internal fun sortedWeekdayIndices(weekdays: List<String>): List<Int> =
    weekdays
        .map { WEEKDAY_ORDER.indexOf(it) }
        .filter { it >= 0 }
        .sorted()

internal fun sortedWeekdayIndicesFromWeekdays(weekdays: List<Weekday>): List<Int> =
    sortedWeekdayIndices(weekdays.map { it.name })

/**
 * Calendar-aware recovery score for mapping [sessionTypes] to [weekdayIndices] in order.
 * Uses per-muscle literature gaps when [context] is available (includes touch slots).
 */
internal fun calendarRecoveryScore(
    sessionTypes: List<SessionTypeId>,
    weekdayIndices: List<Int>,
    context: ProgramGenerationContext? = null,
): Int {
    if (sessionTypes.isEmpty() || weekdayIndices.isEmpty()) return 100
    val exposures = exposuresFromSessionTypes(sessionTypes, weekdayIndices, context)
    return calendarRecoveryScoreFromExposures(exposures)
}

internal fun inferSessionTypeFromWorkoutName(name: String): SessionTypeId? {
    val normalized = name.trim().lowercase()
    return when {
        normalized.startsWith("lower") -> SessionTypeId.Lower
        normalized.startsWith("upper push") -> SessionTypeId.UpperPush
        normalized.startsWith("upper pull") -> SessionTypeId.UpperPull
        normalized.startsWith("upper") -> SessionTypeId.Upper
        normalized.startsWith("full push") -> SessionTypeId.FullPush
        normalized.startsWith("full pull") -> SessionTypeId.FullPull
        normalized.startsWith("full body") -> SessionTypeId.FullBody
        normalized.startsWith("compound pair") -> SessionTypeId.CompoundPair
        normalized.startsWith("aux pair") -> SessionTypeId.AuxPair
        normalized.startsWith("single anchor") -> SessionTypeId.SingleAnchor
        normalized.startsWith("chest & tris") -> SessionTypeId.ChestTris
        normalized.startsWith("back & bis") -> SessionTypeId.BackBis
        normalized.startsWith("chest") -> SessionTypeId.Chest
        normalized.startsWith("back") -> SessionTypeId.Back
        normalized.startsWith("shoulders") -> SessionTypeId.Shoulders
        normalized.startsWith("arms") -> SessionTypeId.Arms
        normalized.startsWith("quads") -> SessionTypeId.QuadsCalves
        normalized.startsWith("glutes") -> SessionTypeId.GlutesHams
        else -> null
    }
}
