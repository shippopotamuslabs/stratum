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
    @DisplayName("weekdays")
    inner class Weekdays {
        @Test
        @DisplayName("Given no weekdays when validated then WeekdaysRequired is returned")
        fun noWeekdays() {
            val result = subject.invoke(aProfile().copy(weekdays = emptySet()))
            assertInvalid(result, ScheduleValidationError.WeekdaysRequired)
        }

        @Test
        @DisplayName("Given at least one weekday when validated then weekdays are accepted")
        fun atLeastOneWeekdayIsValid() {
            val result = subject.invoke(aProfile().copy(weekdays = setOf(Weekday.Monday)))
            assertEquals(ScheduleValidationResult.Valid, result)
        }

        @Test
        @DisplayName("Given all seven weekdays when validated then weekdays are accepted")
        fun allWeekdaysValid() {
            val result = subject.invoke(aProfile().copy(weekdays = Weekday.entries.toSet()))
            assertEquals(ScheduleValidationResult.Valid, result)
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

    private fun aProfile(): UserTrainingProfile =
        UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastMonth,
            weekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
            sessionLengthMinutes = 60,
        )

    private fun assertInvalid(result: ScheduleValidationResult, expected: ScheduleValidationError) {
        assertTrue(result is ScheduleValidationResult.Invalid, "Expected Invalid, got $result")
        assertTrue(
            (result as ScheduleValidationResult.Invalid).errors.contains(expected),
            "Expected $expected in ${result.errors}",
        )
    }
}
