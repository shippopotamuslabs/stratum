package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

enum class TrainingSessionStatus {
    IN_PROGRESS,
    COMPLETED,
}

enum class TrainingSessionKind {
    PROGRAM_WORKOUT,
    REST_DAY,
    AD_HOC,
}

enum class TrainingSessionOrigin {
    IMPORT,
    LIVE,
}

/**
 * One training session (program workout, rest skip, ad-hoc, or import).
 *
 * History is permanent: [programId] / [workoutId] are soft references cleared when the
 * program template is deleted (not Room FKs — template rewrite must not unlink live sessions).
 * Display names are snapshotted at **session create** only so history UI does not need the live
 * program hierarchy or a probe of [LoggedSet] rows.
 */
data class TrainingSession(
    val id: TrainingSessionId,
    /** Live program id when known; null after the program template is deleted. */
    val programId: ProgramId?,
    /** Live workout id when known; null for rest/ad-hoc or after soft-unlink on program delete. */
    val workoutId: WorkoutId?,
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    val cycleNumberSnapshot: Int,
    val weekdaySnapshot: Weekday?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val sourceApp: String?,
    /** Program display name at session create (survives program delete). */
    val programNameSnapshot: String? = null,
    /** Workout display name at session create (survives template delete); null for rest/ad-hoc. */
    val workoutNameSnapshot: String? = null,
)
