package com.stratum.feature.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for the OS-5 Review mapping ([UserTrainingProfile.toReviewProfileSection]) and the
 * [ReviewDraftUi] presentation model. Mirrors the plain-JUnit style of the other step's
 * presentation-model tests — no Turbine needed since the mapping is a pure function.
 */
class ReviewDraftMappingTest {

    @Nested
    @DisplayName("ReviewDraftUi defaults")
    inner class Defaults {
        @Test
        @DisplayName("Given the Empty singleton when inspected then every field is blank/zero")
        fun emptyIsAllBlank() {
            val draft = ReviewDraftUi.Empty
            assertNull(draft.trainingAge)
            assertNull(draft.lastTrained)
            assertTrue(draft.weekdays.isEmpty())
            assertNull(draft.sessionMinutes)
            assertTrue(draft.specialization.isEmpty())
            assertNull(draft.presetLabel)
            assertEquals(0, draft.enabledLiftCount)
        }
    }

    @Nested
    @DisplayName("OS-5 review mapping")
    inner class ReviewMapping {

        @Test
        @DisplayName("Given a fully-answered onboarding when mapped then every section is populated")
        fun fullyAnsweredOnboardingMapsAllSections() {
            val profile = UserTrainingProfile(
                trainingAge = TrainingAge.Intermediate,
                lastTrained = LastTrainedBucket.WithinLastThreeMonths,
                weekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
                sessionLengthMinutes = 60,
            )
            val specialization = SpecializationPreference(
                levels = mapOf(
                    MuscleGroup.Quad to SpecializationLevel.Specialize,
                    MuscleGroup.Chest to SpecializationLevel.Specialize,
                ),
            )
            val lifts = listOf(
                aLift("squat", enabled = true),
                aLift("bench", enabled = true),
                aLift("curl", enabled = false),
            )

            val draft = profile.toReviewProfileSection(specialization, lifts, GymPreset.CommercialGym)

            assertEquals("4–7 years", draft.trainingAge)
            assertEquals("Within the last 3 months", draft.lastTrained)
            assertEquals(listOf("Mon", "Wed", "Fri"), draft.weekdays)
            assertEquals(60, draft.sessionMinutes)
            assertEquals(listOf("Chest", "Quads"), draft.specialization) // ordinal-sorted labels
            assertEquals("Commercial gym", draft.presetLabel)
            assertEquals(2, draft.enabledLiftCount)
        }

        @Test
        @DisplayName("Given weekdays selected out of order when mapped then they render in calendar order")
        fun weekdaysRenderInCalendarOrder() {
            val profile = UserTrainingProfile(
                weekdays = setOf(Weekday.Friday, Weekday.Monday, Weekday.Tuesday),
            )

            val draft = profile.toReviewProfileSection(
                SpecializationPreference.Empty,
                emptyList(),
                null,
            )

            assertEquals(listOf("Mon", "Tue", "Fri"), draft.weekdays)
        }

        @Test
        @DisplayName("Given a skipped specialization when mapped then the section is empty (renders Balanced)")
        fun skippedSpecializationIsEmpty() {
            val draft = UserTrainingProfile().toReviewProfileSection(
                SpecializationPreference.Empty,
                emptyList(),
                null,
            )

            assertTrue(draft.specialization.isEmpty())
        }

        @Test
        @DisplayName("Given a maintenance-only preference when mapped then no muscles are specialized")
        fun maintenanceOnlyIsNotSpecialized() {
            // Maintenance is implicit (absence); even if explicitly set it isn't a focus muscle.
            val preference = SpecializationPreference(
                levels = mapOf(MuscleGroup.Back to SpecializationLevel.Maintenance),
            )

            val draft = UserTrainingProfile().toReviewProfileSection(preference, emptyList(), null)

            assertTrue(draft.specialization.isEmpty())
        }

        @Test
        @DisplayName("Given a null profile when mapped then every field is blank (fresh-install state)")
        fun emptyProfileMapsToBlankSummary() {
            val draft = UserTrainingProfile.Empty.toReviewProfileSection(
                SpecializationPreference.Empty,
                emptyList(),
                null,
            )

            assertNull(draft.trainingAge)
            assertNull(draft.lastTrained)
            assertTrue(draft.weekdays.isEmpty())
            assertNull(draft.sessionMinutes)
            assertNull(draft.presetLabel)
            assertEquals(0, draft.enabledLiftCount)
        }

        @Test
        @DisplayName("Given mixed enabled/disabled lifts when mapped then only enabled lifts are counted")
        fun onlyEnabledLiftsCounted() {
            val lifts = listOf(
                aLift("one", enabled = true),
                aLift("two", enabled = false),
                aLift("three", enabled = true),
                aLift("four", enabled = true),
            )

            val draft = UserTrainingProfile().toReviewProfileSection(
                SpecializationPreference.Empty,
                lifts,
                GymPreset.GarageGym,
            )

            assertEquals(3, draft.enabledLiftCount)
            assertEquals("Garage gym", draft.presetLabel)
        }
    }

    private fun aLift(slug: String, enabled: Boolean): Lift = Lift(
        id = LiftId(slug),
        name = slug,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = enabled,
    )
}
