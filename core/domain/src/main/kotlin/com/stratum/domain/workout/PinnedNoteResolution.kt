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
