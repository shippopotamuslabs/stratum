package com.stratum.application.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference

class ValidateMinimumVolumeStepUseCaseTest {

    private val useCase = ValidateMinimumVolumeStepUseCase()

    @Test
    @DisplayName("empty preference is valid")
    fun emptyIsValid() {
        assertEquals(MinimumVolumeValidationResult.Valid, useCase(SpecializationPreference.Empty))
    }

    @Test
    @DisplayName("rejects more than max minimum-volume muscles")
    fun rejectsTooMany() {
        val muscles = MuscleGroup.entries
            .filter { it != MuscleGroup.Abductor && it != MuscleGroup.Adductor }
            .take(ValidateMinimumVolumeStepUseCase.MAX_MINIMUM_VOLUME + 1)
        val result = useCase(
            SpecializationPreference(
                levels = muscles.associateWith { SpecializationLevel.MinimumVolume },
            ),
        )
        assertTrue(result is MinimumVolumeValidationResult.Invalid)
    }
}
