package com.stratum.domain.onboarding

import com.stratum.domain.program.schedule.GenerationCycleDefaults
import javax.inject.Inject

/**
 * Validates the Schedule step of onboarding / create-program gen preferences. Pure (no I/O).
 *
 * Training-day count [N] comes from (in order):
 * 1. Explicit [trainingDayCount]
 * 2. `cycleLength - restSlotIndices.size` when both cycle hints are set
 * 3. Legacy [UserTrainingProfile.weekdays] size
 *
 * Synthetic weekdays are **not** required. Session length and cycle-space rules still apply:
 * - N ∈ [1, 5] → cycleLength = 7, R = 7 − N
 * - N = 6 → R ∈ {1, 2}, cycleLength ∈ {7, 8}
 */
class ValidateScheduleStepUseCase @Inject constructor() {
    operator fun invoke(
        profile: UserTrainingProfile,
        cycleLength: Int? = null,
        restSlotIndices: Set<Int>? = null,
        trainingDayCount: Int? = null,
    ): ScheduleValidationResult {
        val n =
            resolveTrainingDayCount(
                trainingDayCount = trainingDayCount,
                cycleLength = cycleLength,
                restSlotIndices = restSlotIndices,
                profileGenerationTrainCount = profile.generationTrainingDayCount,
                profileWeekdayCount = profile.weekdays.size,
            )
        val errors = buildSet {
            if (n < 1) add(ScheduleValidationError.WeekdaysRequired)
            if (n > MAX_TRAINING_DAYS) {
                add(ScheduleValidationError.TooManyTrainingDays)
            }
            when {
                profile.sessionLengthMinutes == null ->
                    add(ScheduleValidationError.SessionLengthRequired)
                profile.sessionLengthMinutes < MIN_SESSION_MINUTES ||
                    profile.sessionLengthMinutes > MAX_SESSION_MINUTES ->
                    add(ScheduleValidationError.SessionLengthOutOfRange)
            }
            addAll(cycleSpaceErrors(n, cycleLength, restSlotIndices))
        }
        return if (errors.isEmpty()) {
            ScheduleValidationResult.Valid
        } else {
            ScheduleValidationResult.Invalid(errors)
        }
    }

    private fun resolveTrainingDayCount(
        trainingDayCount: Int?,
        cycleLength: Int?,
        restSlotIndices: Set<Int>?,
        profileGenerationTrainCount: Int?,
        profileWeekdayCount: Int,
    ): Int {
        // Prefer explicit N / persisted generate N / legacy weekdays over deriving from rests so
        // invalid rest patterns still validate against the user's intended train count.
        if (trainingDayCount != null && trainingDayCount > 0) return trainingDayCount
        if (profileGenerationTrainCount != null && profileGenerationTrainCount > 0) {
            return profileGenerationTrainCount
        }
        if (profileWeekdayCount > 0) return profileWeekdayCount
        if (cycleLength != null && restSlotIndices != null && cycleLength > restSlotIndices.size) {
            return cycleLength - restSlotIndices.size
        }
        return 0
    }

    private fun cycleSpaceErrors(
        trainingDayCount: Int,
        cycleLength: Int?,
        restSlotIndices: Set<Int>?,
    ): Set<ScheduleValidationError> {
        if (cycleLength == null && restSlotIndices == null) return emptySet()
        if (trainingDayCount !in 1..MAX_TRAINING_DAYS) {
            return setOf(ScheduleValidationError.InvalidCycleLength)
        }
        val errors = mutableSetOf<ScheduleValidationError>()
        val rests = restSlotIndices.orEmpty()
        val length =
            cycleLength
                ?: (trainingDayCount + rests.size)
        val expectedRestCount = length - trainingDayCount
        val allowedRests = GenerationCycleDefaults.allowedRestCounts(trainingDayCount)
        if (expectedRestCount !in allowedRests) {
            errors += ScheduleValidationError.InvalidCycleLength
        }
        val restsValid =
            rests.size == expectedRestCount &&
                expectedRestCount in allowedRests &&
                rests.all { index -> index in 0 until length } &&
                rests.size == rests.toSet().size
        if (!restsValid) {
            errors += ScheduleValidationError.InvalidRestSlotIndices
        }
        return errors
    }

    companion object {
        const val MIN_SESSION_MINUTES = 15
        const val MAX_SESSION_MINUTES = 240
        const val MAX_TRAINING_DAYS: Int = GenerationCycleDefaults.MAX_GEN_TRAINING_DAYS
    }
}
