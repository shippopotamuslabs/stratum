package com.stratum.core.data.db

import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EnumConvertersTest {

    private val converters = EnumConverters()

    @Test
    @DisplayName("Given a known enum value when round-tripped through a converter then it is preserved")
    fun knownValuesRoundTrip() {
        assertEquals(MuscleGroup.LateralDeltoid, converters.toMuscleGroup(converters.fromMuscleGroup(MuscleGroup.LateralDeltoid)))
        assertEquals(SpecializationLevel.Specialize, converters.toSpecializationLevel(converters.fromSpecializationLevel(SpecializationLevel.Specialize)))
        assertEquals(OnboardingStep.Specialization, converters.toOnboardingStep(converters.fromOnboardingStep(OnboardingStep.Specialization)))
        assertEquals(TrainingAge.LateIntermediate, converters.toTrainingAge(converters.fromTrainingAge(TrainingAge.LateIntermediate)))
        assertEquals(LastTrainedBucket.OneToSixMonthsAgo, converters.toLastTrainedBucket(converters.fromLastTrainedBucket(LastTrainedBucket.OneToSixMonthsAgo)))
        assertEquals(Weekday.Thursday, converters.toWeekday(converters.fromWeekday(Weekday.Thursday)))
    }

    @Test
    @DisplayName("Given null when encoded then it round-trips as null")
    fun nullRoundTripsAsNull() {
        assertNull(converters.toMuscleGroup(converters.fromMuscleGroup(null)))
        assertNull(converters.toSpecializationLevel(converters.fromSpecializationLevel(null)))
        assertNull(converters.toOnboardingStep(converters.fromOnboardingStep(null)))
        assertNull(converters.toTrainingAge(converters.fromTrainingAge(null)))
        assertNull(converters.toLastTrainedBucket(converters.fromLastTrainedBucket(null)))
        assertNull(converters.toWeekday(converters.fromWeekday(null)))
    }

    @Test
    @DisplayName("Given an unknown enum name when decoded then it yields null rather than throwing")
    fun unknownNameYieldsNull() {
        assertNull(converters.toMuscleGroup("NotAMuscle"))
        assertNull(converters.toSpecializationLevel("BogusLevel"))
        assertNull(converters.toOnboardingStep("FutureStep"))
        assertNull(converters.toTrainingAge("AlsoFake"))
        assertNull(converters.toLastTrainedBucket("Nope"))
        assertNull(converters.toWeekday("Funday"))
    }

    @Test
    @DisplayName("Given a renamed enum name when decoded then it yields null (no crash on downgrade)")
    fun renamedEnumYieldsNull() {
        // Simulates a value written by a newer schema version that no longer exists.
        assertNull(converters.toMuscleGroup("Obliques"))
    }
}
