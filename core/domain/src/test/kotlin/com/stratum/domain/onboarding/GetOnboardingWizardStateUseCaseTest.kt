package com.stratum.domain.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveSelectedPresetUseCase
import com.stratum.domain.lift.ObserveUsedHistoricalImportUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetOnboardingWizardStateUseCaseTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeProfile = FakeUserTrainingProfileRepository()
    private val fakeSpecialization = FakeSpecializationPreferenceRepository()
    private val fakeLift = FakeLiftRepository()
    private lateinit var subject: GetOnboardingWizardStateUseCase

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null),
        )
        fakeProfile.seed(null)
        fakeSpecialization.seed(SpecializationPreference.Empty)
        fakeLift.seedLifts(emptyList())
        fakeLift.seedPreset(null)
        subject = GetOnboardingWizardStateUseCase(
            getOnboardingState = GetOnboardingStateUseCase(fakeOnboarding, fakeProfile),
            observeSpecialization = ObserveSpecializationUseCase(fakeSpecialization),
            observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLift),
            observeSelectedPreset = ObserveSelectedPresetUseCase(fakeLift),
            observeUsedHistoricalImport = ObserveUsedHistoricalImportUseCase(fakeLift),
        )
    }

    @Test
    @DisplayName("Given all repos at defaults when invoked then wizard state composes all streams")
    fun composesAllStreams() = runTest {
        subject().test {
            val state = awaitItem()
            assertEquals(OnboardingStep.Welcome, state.currentStep)
            assertNull(state.profile)
            assertEquals(SpecializationPreference.Empty, state.specialization)
            assertEquals(emptyList<Lift>(), state.lifts)
            assertNull(state.selectedPreset)
            assertEquals(false, state.isComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given progress profile specialization and preset when invoked then all are present")
    fun composesPopulatedState() = runTest {
        val profile = UserTrainingProfile(trainingAge = TrainingAge.Advanced)
        val spec = SpecializationPreference(
            levels = mapOf(MuscleGroup.Chest to SpecializationLevel.Specialize),
        )
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Schedule, startedAt = 1L, completedAt = null),
        )
        fakeProfile.seed(profile)
        fakeSpecialization.seed(spec)
        fakeLift.seedPreset(GymPreset.CommercialGym)

        subject().test {
            val state = awaitItem()
            assertEquals(OnboardingStep.Schedule, state.currentStep)
            assertEquals(profile, state.profile)
            assertEquals(spec, state.specialization)
            assertEquals(GymPreset.CommercialGym, state.selectedPreset)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
