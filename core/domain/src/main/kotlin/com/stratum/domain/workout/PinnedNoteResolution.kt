package com.stratum.domain.workout

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.identity.WorkoutLiftId

/** Overlay pin wins; otherwise the template/projection value. */
fun resolvedWorkoutPinnedNote(
    overlayNotes: Map<WorkoutLiftId, String>,
    workoutLiftId: WorkoutLiftId,
    templateNote: String?,
): String? = overlayNotes[workoutLiftId] ?: templateNote

/**
 * Catalog row is authoritative when present, including a cleared (null) pin.
 * Missing catalog falls back to the current projection.
 */
fun resolvedLiftPinnedNote(
    catalog: Lift?,
    projectedNote: String?,
): String? = if (catalog != null) catalog.pinnedNote else projectedNote

/** List form of [resolvedWorkoutPinnedNote]: overlay list wins; otherwise the template list. */
fun resolvedWorkoutPinnedNotes(
    overlayNotes: Map<WorkoutLiftId, List<String>>,
    workoutLiftId: WorkoutLiftId,
    templateNotes: List<String>,
): List<String> = overlayNotes[workoutLiftId] ?: templateNotes

/**
 * List form that also honors pre-list overlay entries: overlay list wins, then the
 * legacy single overlay pin (older persisted sessions), then the template list.
 */
fun resolvedWorkoutPinnedNotes(
    overlay: WorkoutOverlay,
    workoutLiftId: WorkoutLiftId,
    templateNotes: List<String>,
): List<String> =
    overlay.workoutLiftPinnedNoteLists[workoutLiftId]
        ?: overlay.workoutLiftPinnedNotes[workoutLiftId]?.let(::listOf)
        ?: templateNotes

/**
 * Display form: first overlay list entry wins; otherwise the legacy single-note
 * resolution (overlay pin, then template). Keeps single-string readers working
 * while slots carry ordered lists.
 */
fun resolvedWorkoutPinnedNoteFirst(
    overlay: WorkoutOverlay,
    workoutLiftId: WorkoutLiftId,
    templateNote: String?,
): String? =
    overlay.workoutLiftPinnedNoteLists[workoutLiftId]?.firstOrNull()
        ?: resolvedWorkoutPinnedNote(overlay.workoutLiftPinnedNotes, workoutLiftId, templateNote)

/**
 * List form of [resolvedLiftPinnedNote]: catalog list is authoritative when the
 * catalog row is present (including an explicitly cleared empty list).
 * Missing catalog falls back to the current projection.
 */
fun resolvedLiftPinnedNotes(
    catalogNotes: List<String>?,
    projectedNotes: List<String>,
): List<String> = catalogNotes ?: projectedNotes
