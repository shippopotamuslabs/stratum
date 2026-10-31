package com.stratum.core.infrastructure.db

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EnumConvertersTest {

    private val converters = EnumConverters()

    @Test
    @DisplayName("FPR-S2-001 FatigueProfile High round-trips")
    fun fprS2001_highRoundTrip() {
        assertEquals("High", converters.fromFatigueProfile(FatigueProfile.High))
        assertEquals(FatigueProfile.High, converters.toFatigueProfile("High"))
    }

    @Test
    @DisplayName("FPR-S2-002 FatigueProfile Medium round-trips")
    fun fprS2002_mediumRoundTrip() {
        assertEquals("Medium", converters.fromFatigueProfile(FatigueProfile.Medium))
        assertEquals(FatigueProfile.Medium, converters.toFatigueProfile("Medium"))
    }

    @Test
    @DisplayName("FPR-S2-003 FatigueProfile Low round-trips")
    fun fprS2003_lowRoundTrip() {
        assertEquals("Low", converters.fromFatigueProfile(FatigueProfile.Low))
        assertEquals(FatigueProfile.Low, converters.toFatigueProfile("Low"))
    }

    @Test
    @DisplayName("FPR-S2-004 unknown fatigue string decodes to null")
    fun fprS2004_unknownDecodesNull() {
        assertNull(converters.toFatigueProfile("NotARealProfile"))
    }

    @Test
    @DisplayName("FPR-S2-M001 legacy AxialCompound does not decode to High")
    fun fprS2M001_legacyNamesDoNotAlias() {
        assertNull(converters.toFatigueProfile("AxialCompound"))
        assertNull(converters.toFatigueProfile("NonAxialCompound"))
        assertNull(converters.toFatigueProfile("Isolation"))
    }


    @Test
    @DisplayName("Given a known enum value when round-tripped through a converter then it is preserved")
    fun knownValuesRoundTrip() {
        assertEquals(MuscleGroup.LateralDeltoid, converters.toMuscleGroup(converters.fromMuscleGroup(MuscleGroup.LateralDeltoid)))
        assertEquals(Weekday.Monday, converters.toWeekday(converters.fromWeekday(Weekday.Monday)))
        assertEquals(LiftId("bench-press"), converters.toLiftId(converters.fromLiftId(LiftId("bench-press"))))
        assertEquals(
            LoggedSetId("set-1"),
            converters.toLoggedSetId(converters.fromLoggedSetId(LoggedSetId("set-1"))),
        )
        assertEquals(EpochId("epoch-1"), converters.toEpochId(converters.fromEpochId(EpochId("epoch-1"))))
        assertEquals(
            ExperimentId("exp-1"),
            converters.toExperimentId(converters.fromExperimentId(ExperimentId("exp-1"))),
        )
    }

    @Test
    @DisplayName("Given null when encoded then it round-trips as null")
    fun nullRoundTripsAsNull() {
        assertNull(converters.toMuscleGroup(converters.fromMuscleGroup(null)))
        assertNull(converters.toWeekday(converters.fromWeekday(null)))
        assertNull(converters.toLiftId(converters.fromLiftId(null)))
        assertNull(converters.toLoggedSetId(converters.fromLoggedSetId(null)))
        assertNull(converters.toEpochId(converters.fromEpochId(null)))
        assertNull(converters.toExperimentId(converters.fromExperimentId(null)))
    }

    @Test
    @DisplayName("Given an unknown enum name when decoded then it yields null rather than throwing")
    fun unknownNameYieldsNull() {
        assertNull(converters.toMuscleGroup("NotAMuscle"))
        assertNull(converters.toWeekday("Funday"))
    }

    @Test
    @DisplayName("Given legacy Generate onboarding step when decoded then it maps to Review")
    fun legacyGenerateStepMapsToReview() {
        assertEquals(OnboardingStep.Review, converters.toOnboardingStep("Generate"))
    }

    @Test
    @DisplayName("Given a MovementPattern when round-tripped through the single-value converter then it is preserved")
    fun movementPatternRoundTripsSingleValue() {
        val pattern = com.stratum.domain.lift.MovementPattern.SquatPattern
        assertEquals(pattern, converters.toMovementPattern(converters.fromMovementPattern(pattern)))
    }

    @Test
    @DisplayName("Given a blank LiftId when decoded then it yields null")
    fun blankLiftIdYieldsNull() {
        assertNull(converters.toLiftId(""))
        assertNull(converters.toLiftId("   "))
    }

    @Test
    @DisplayName("WL-003 EnumConverters round-trip TrainingSessionStatus Kind and Origin")
    fun wl003_trainingSessionEnumRoundTrip() {
        assertEquals(
            com.stratum.domain.training.TrainingSessionStatus.IN_PROGRESS,
            converters.toTrainingSessionStatus(
                converters.fromTrainingSessionStatus(
                    com.stratum.domain.training.TrainingSessionStatus.IN_PROGRESS,
                ),
            ),
        )
        assertEquals(
            "REST_DAY",
            converters.fromTrainingSessionKind(com.stratum.domain.training.TrainingSessionKind.REST_DAY),
        )
        assertEquals(
            com.stratum.domain.training.TrainingSessionKind.REST_DAY,
            converters.toTrainingSessionKind("REST_DAY"),
        )
        assertEquals(
            "LIVE",
            converters.fromTrainingSessionOrigin(com.stratum.domain.training.TrainingSessionOrigin.LIVE),
        )
        assertEquals(
            com.stratum.domain.training.TrainingSessionOrigin.LIVE,
            converters.toTrainingSessionOrigin("LIVE"),
        )
        assertNull(converters.toTrainingSessionStatus("UNKNOWN"))
    }
}
