package com.stratum.application.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.ProfileValidationError
import com.stratum.domain.onboarding.ProfileValidationResult
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile

class ValidateProfileStepUseCaseTest {

    private lateinit var subject: ValidateProfileStepUseCase

    @BeforeEach
    fun setup() {
        subject = ValidateProfileStepUseCase()
    }

    @Nested
    @DisplayName("training age")
    inner class TrainingAgeValidation {
        @Test
        @DisplayName("Given training age is null when validated then TrainingAgeRequired is returned")
        fun trainingAgeMissing() {
            val result = subject.invoke(aProfile().copy(trainingAge = null))
            assertInvalid(result, ProfileValidationError.TrainingAgeRequired)
        }

        @Test
        @DisplayName("Given each training age when validated then each is accepted")
        fun eachTrainingAgeIsValid() {
            TrainingAge.entries.forEach { age ->
                val result = subject.invoke(aProfile().copy(trainingAge = age))
                assertEquals(ProfileValidationResult.Valid, result, "Expected $age to be valid")
            }
        }
    }

    @Nested
    @DisplayName("last trained")
    inner class LastTrained {
        @Test
        @DisplayName("Given lastTrained is null when validated then LastTrainedRequired is returned")
        fun lastTrainedMissing() {
            val result = subject.invoke(aProfile().copy(lastTrained = null))
            assertInvalid(result, ProfileValidationError.LastTrainedRequired)
        }
    }

    @Test
    @DisplayName("Given an empty profile when validated then all required-field errors are returned")
    fun emptyProfileReportsAllRequiredErrors() {
        val result = subject.invoke(UserTrainingProfile.Empty)
        assertTrue(result is ProfileValidationResult.Invalid)
        val errors = (result as ProfileValidationResult.Invalid).errors
        assertEquals(
            setOf(
                ProfileValidationError.TrainingAgeRequired,
                ProfileValidationError.LastTrainedRequired,
            ),
            errors,
        )
    }

    @Test
    @DisplayName("Given a complete profile when validated then it is valid")
    fun completeProfileIsValid() {
        val result = subject.invoke(aProfile())
        assertEquals(ProfileValidationResult.Valid, result)
    }

    private fun aProfile(): UserTrainingProfile =
        UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastThreeMonths,
            sessionLengthMinutes = 60,
        )

    private fun assertInvalid(result: ProfileValidationResult, expected: ProfileValidationError) {
        assertTrue(result is ProfileValidationResult.Invalid, "Expected Invalid, got $result")
        assertTrue(
            (result as ProfileValidationResult.Invalid).errors.contains(expected),
            "Expected $expected in ${result.errors}",
        )
    }
}
