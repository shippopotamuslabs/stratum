package com.stratum.domain.onboarding



/**
 * Captured during the Profile and Schedule onboarding steps (OS-2). All fields are
 * nullable so a draft can be persisted incrementally as the user advances and navigates
 * back. A single row exists per install.
 *
 * Step-level validation (ranges the wizard enforces before advancing) lives in
 * [ValidateProfileStepUseCase] / [ValidateScheduleStepUseCase]; the shapes that are
 * impossible in any context are constructor invariants here: a present session length
 * or generate count is positive, and persisted rest indices sit inside the persisted
 * cycle length. The schedule-identity derivation (explicit generate count, else the
 * legacy weekday count) is owned here via [resolvedTrainingDayCount] so no consumer
 * re-decides it.
 */
data class UserTrainingProfile(
    val trainingAge: TrainingAge? = null,
    val lastTrained: LastTrainedBucket? = null,
    /**
     * Legacy weekday set — **not** generate schedule identity. Cleared on Generate schedule flush.
     * May still appear on old rows; path resolution prefers [generationCycleLength] / train count.
     */
    val weekdays: Set<Weekday> = emptySet(),
    val sessionLengthMinutes: Int? = null,
    /**
     * Persisted generate cycle-space (N + length + rests). Written when advancing past Schedule on
     * the Generate path so process death does not lose rest placement before Review / generate.
     */
    val generationTrainingDayCount: Int? = null,
    val generationCycleLength: Int? = null,
    val generationRestSlotIndices: Set<Int> = emptySet(),
) {
    init {
        require(sessionLengthMinutes == null || sessionLengthMinutes > 0) {
            "sessionLengthMinutes must be positive when present, was $sessionLengthMinutes"
        }
        require(generationTrainingDayCount == null || generationTrainingDayCount > 0) {
            "generationTrainingDayCount must be positive when present, was $generationTrainingDayCount"
        }
        require(generationCycleLength == null || generationCycleLength > 0) {
            "generationCycleLength must be positive when present, was $generationCycleLength"
        }
        require(generationRestSlotIndices.all { it >= 0 }) {
            "generationRestSlotIndices must be non-negative, was $generationRestSlotIndices"
        }
        val cycleLength = generationCycleLength
        require(cycleLength == null || generationRestSlotIndices.all { it < cycleLength }) {
            "generationRestSlotIndices must sit inside generationCycleLength $cycleLength, " +
                "was $generationRestSlotIndices"
        }
    }

    /** True when any persisted generate cycle-space field is set (Generate Schedule flush landed). */
    fun hasPersistedGenerateCycleSpace(): Boolean =
        generationTrainingDayCount != null ||
            generationCycleLength != null ||
            generationRestSlotIndices.isNotEmpty()

    /**
     * The profile's own training-day count: the persisted generate count when present,
     * else the legacy weekday count, else null when the profile carries no schedule
     * identity at all. Callers with live cycle-space hints decide their own precedence
     * against those hints; the profile-internal order is defined only here.
     */
    fun resolvedTrainingDayCount(): Int? =
        generationTrainingDayCount ?: weekdays.size.takeIf { it > 0 }

    /**
     * The Generate schedule flush shape: persists the cycle-space triple and clears the
     * legacy weekdays, which are not schedule identity once cycle-space exists.
     */
    fun withGenerateCycleSpace(
        trainingDayCount: Int?,
        cycleLength: Int?,
        restSlotIndices: Set<Int>,
    ): UserTrainingProfile = copy(
        weekdays = emptySet(),
        generationTrainingDayCount = trainingDayCount,
        generationCycleLength = cycleLength,
        generationRestSlotIndices = restSlotIndices,
    )

    /**
     * The Import-commit shape: committing to Import discards every trace of a prior
     * Generate-path exploration (weekdays, session length, cycle-space).
     */
    fun clearedScheduleIdentity(): UserTrainingProfile = copy(
        weekdays = emptySet(),
        sessionLengthMinutes = null,
        generationTrainingDayCount = null,
        generationCycleLength = null,
        generationRestSlotIndices = emptySet(),
    )

    companion object {
        val Empty = UserTrainingProfile()
    }
}
