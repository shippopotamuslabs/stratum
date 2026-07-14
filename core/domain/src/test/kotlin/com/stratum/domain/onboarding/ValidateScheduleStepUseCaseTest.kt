package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValidateScheduleStepUseCaseTest {

    private lateinit var subject: ValidateScheduleStepUseCase

    @BeforeEach
    fun setup() {
        subject = ValidateScheduleStepUseCase()
    }

    @Nested
    @DisplayName("training day count")
    inner class TrainingDayCount {
        @Test
        @DisplayName("Given no train count when validated then WeekdaysRequired is returned")
        fun noTrainCount() {
            val result = subject.invoke(aProfile().copy(generationTrainingDayCount = null))
            assertInvalid(result, ScheduleValidationError.WeekdaysRequired)
        }

        @Test
        @DisplayName("Given generationTrainingDayCount when validated then accepted")
        fun generationCountValid() {
            val result = subject.invoke(aProfile())
            assertEquals(ScheduleValidationResult.Valid, result)
        }

        @Test
        @DisplayName("Given explicit trainingDayCount param when validated then accepted")
        fun explicitTrainCountValid() {
            val result =
                subject.invoke(
                    aProfile().copy(generationTrainingDayCount = null),
                    trainingDayCount = 3,
                )
            assertEquals(ScheduleValidationResult.Valid, result)
        }

        @Test
        @DisplayName("Given legacy weekdays when validated then accepted")
        fun legacyWeekdaysValid() {
            val result =
                subject.invoke(
                    aProfile().copy(
                        generationTrainingDayCount = null,
                        weekdays = setOf(Weekday.Monday, Weekday.Wednesday),
                    ),
                )
            assertEquals(ScheduleValidationResult.Valid, result)
        }

        @Test
        @DisplayName("Given more than max training days when validated then TooManyTrainingDays")
        fun tooManyTrainingDays() {
            val result =
                subject.invoke(
                    aProfile(),
                    trainingDayCount = ValidateScheduleStepUseCase.MAX_TRAINING_DAYS + 1,
                )
            assertInvalid(result, ScheduleValidationError.TooManyTrainingDays)
        }
    }

    @Nested
    @DisplayName("session length")
    inner class SessionLength {
        @Test
        @DisplayName("Given session length is null when validated then SessionLengthRequired is returned")
        fun sessionMissing() {
            val result = subject.invoke(aProfile().copy(sessionLengthMinutes = null))
            assertInvalid(result, ScheduleValidationError.SessionLengthRequired)
        }

        @Test
        @DisplayName("Given session length is one below minimum when validated then out-of-range is returned")
        fun sessionTooShort() {
            val result = subject.invoke(
                aProfile().copy(sessionLengthMinutes = ValidateScheduleStepUseCase.MIN_SESSION_MINUTES - 1),
            )
            assertInvalid(result, ScheduleValidationError.SessionLengthOutOfRange)
        }

        @Test
        @DisplayName("Given session length is the minimum when validated then it is accepted")
        fun sessionAtMinimumIsValid() {
            val result = subject.invoke(
                aProfile().copy(sessionLengthMinutes = ValidateScheduleStepUseCase.MIN_SESSION_MINUTES),
            )
            assertEquals(ScheduleValidationResult.Valid, result)
        }

        @Test
        @DisplayName("Given session length is the maximum when validated then it is accepted")
        fun sessionAtMaximumIsValid() {
            val result = subject.invoke(
                aProfile().copy(sessionLengthMinutes = ValidateScheduleStepUseCase.MAX_SESSION_MINUTES),
            )
            assertEquals(ScheduleValidationResult.Valid, result)
        }

        @Test
        @DisplayName("Given session length is one above maximum when validated then out-of-range is returned")
        fun sessionTooLong() {
            val result = subject.invoke(
                aProfile().copy(sessionLengthMinutes = ValidateScheduleStepUseCase.MAX_SESSION_MINUTES + 1),
            )
            assertInvalid(result, ScheduleValidationError.SessionLengthOutOfRange)
        }
    }

    @Test
    @DisplayName("Given an empty profile when validated then both required-field errors are returned")
    fun emptyProfileReportsBothErrors() {
        val result = subject.invoke(UserTrainingProfile.Empty)
        assertTrue(result is ScheduleValidationResult.Invalid)
        val errors = (result as ScheduleValidationResult.Invalid).errors
        assertEquals(
            setOf(
                ScheduleValidationError.WeekdaysRequired,
                ScheduleValidationError.SessionLengthRequired,
            ),
            errors,
        )
    }

    @Test
    @DisplayName("Given a complete schedule when validated then it is valid")
    fun completeScheduleIsValid() {
        val result = subject.invoke(aProfile())
        assertEquals(ScheduleValidationResult.Valid, result)
    }

    @Test
    @DisplayName("Given cycle-space hints matching N when validated then valid")
    fun cycleSpaceHintsValid() {
        val result =
            subject.invoke(
                aProfile().copy(generationTrainingDayCount = null),
                trainingDayCount = 4,
                cycleLength = 7,
                restSlotIndices = setOf(1, 3, 5),
            )
        assertEquals(ScheduleValidationResult.Valid, result)
    }

    private fun aProfile(): UserTrainingProfile =
        UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastThreeMonths,
            sessionLengthMinutes = 60,
            generationTrainingDayCount = 4,
        )

    private fun assertInvalid(result: ScheduleValidationResult, expected: ScheduleValidationError) {
        assertTrue(result is ScheduleValidationResult.Invalid)
        assertTrue(
            expected in (result as ScheduleValidationResult.Invalid).errors,
            "Expected $expected in ${(result as ScheduleValidationResult.Invalid).errors}",
        )
    }
}
