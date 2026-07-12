@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.generation.context.MINUTES_PER_HARD_SET
import kotlin.math.floor

/**
 * Soft aim + hard max session duration for program generation.
 *
 * UI bands store the **soft aim** midpoint as [UserTrainingProfile.sessionLengthMinutes]
 * (30 / 50 / 75). Domain resolves the band range so the solver soft-fills toward aim and
 * hard-caps at max; post-pass only trims when estimated time exceeds max + [DURATION_TRIM_GRACE_MINUTES].
 *
 * | Soft aim (stored) | Band minutes |
 * |-------------------|--------------|
 * | 30                | 20–40        |
 * | 50                | 40–60        |
 * | 75                | 60–90        |
 * | other (custom)    | aim = max = stored |
 */
data class SessionLengthBudget(
    val aimMinutes: Int,
    val maxMinutes: Int,
) {
    val aimHardSets: Int
        get() = floor(aimMinutes / MINUTES_PER_HARD_SET).toInt()

    val maxHardSets: Int
        get() = floor(maxMinutes / MINUTES_PER_HARD_SET).toInt()

    /** Only trim sessions longer than max + grace. */
    val trimHardSets: Int
        get() = floor((maxMinutes + DURATION_TRIM_GRACE_MINUTES) / MINUTES_PER_HARD_SET).toInt()
}

/** Minutes over the band max before post-pass duration trim fires. */
const val DURATION_TRIM_GRACE_MINUTES = 5

/** UI / profile key for the short band (soft aim 30). */
const val SESSION_BAND_KEY_SHORT = "20-40"

/** UI / profile key for the medium band (soft aim 50). */
const val SESSION_BAND_KEY_MEDIUM = "40-60"

/** UI / profile key for the long band (soft aim 75). */
const val SESSION_BAND_KEY_LONG = "60-90"

const val SESSION_BAND_AIM_SHORT = 30
const val SESSION_BAND_AIM_MEDIUM = 50
const val SESSION_BAND_AIM_LONG = 75

const val SESSION_BAND_MAX_SHORT = 40
const val SESSION_BAND_MAX_MEDIUM = 60
const val SESSION_BAND_MAX_LONG = 90

/**
 * Resolve budget from persisted soft-aim minutes.
 * Known band midpoints (30 / 50 / 75) expand to their range; any other positive value is custom
 * (aim = max). Prefer not to use 30/50/75 as custom values — UI bands own those midpoints.
 */
fun sessionLengthBudget(sessionLengthMinutes: Int?): SessionLengthBudget? {
    if (sessionLengthMinutes == null || sessionLengthMinutes <= 0) return null
    return when (sessionLengthMinutes) {
        SESSION_BAND_AIM_SHORT -> SessionLengthBudget(SESSION_BAND_AIM_SHORT, SESSION_BAND_MAX_SHORT)
        SESSION_BAND_AIM_MEDIUM -> SessionLengthBudget(SESSION_BAND_AIM_MEDIUM, SESSION_BAND_MAX_MEDIUM)
        SESSION_BAND_AIM_LONG -> SessionLengthBudget(SESSION_BAND_AIM_LONG, SESSION_BAND_MAX_LONG)
        else -> SessionLengthBudget(sessionLengthMinutes, sessionLengthMinutes)
    }
}

/** Resolve budget from UI band key + optional custom minutes. */
fun sessionLengthBudgetForBandKey(
    bandKey: String?,
    customMinutes: Int?,
): SessionLengthBudget? {
    if (bandKey.isNullOrBlank()) return null
    return when (bandKey) {
        SESSION_BAND_KEY_SHORT ->
            SessionLengthBudget(SESSION_BAND_AIM_SHORT, SESSION_BAND_MAX_SHORT)
        SESSION_BAND_KEY_MEDIUM ->
            SessionLengthBudget(SESSION_BAND_AIM_MEDIUM, SESSION_BAND_MAX_MEDIUM)
        SESSION_BAND_KEY_LONG ->
            SessionLengthBudget(SESSION_BAND_AIM_LONG, SESSION_BAND_MAX_LONG)
        "custom" -> {
            val minutes = customMinutes ?: return null
            if (minutes <= 0) return null
            SessionLengthBudget(minutes, minutes)
        }
        else -> {
            // Legacy numeric keys ("45", "60", "75") or raw minutes string
            val minutes = bandKey.toIntOrNull() ?: return null
            sessionLengthBudget(minutes)
        }
    }
}

/** Hard set ceiling for CP-SAT (band max). Null if session length unset. */
fun sessionHardSetCapForLength(sessionLengthMinutes: Int?): Int? =
    sessionLengthBudget(sessionLengthMinutes)?.maxHardSets

/** Soft fill target set count (band aim). Null if session length unset. */
fun sessionAimHardSetsForLength(sessionLengthMinutes: Int?): Int? =
    sessionLengthBudget(sessionLengthMinutes)?.aimHardSets

/** Post-pass trim threshold (max + grace). Null if session length unset. */
fun sessionTrimHardSetsForLength(sessionLengthMinutes: Int?): Int? =
    sessionLengthBudget(sessionLengthMinutes)?.trimHardSets

/** Duration budget in minutes for validation (band max, or custom value). */
fun sessionDurationBudgetMinutes(sessionLengthMinutes: Int): Int =
    sessionLengthBudget(sessionLengthMinutes)?.maxMinutes ?: sessionLengthMinutes
