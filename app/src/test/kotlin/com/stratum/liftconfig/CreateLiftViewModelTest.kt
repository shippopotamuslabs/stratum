package com.stratum.liftconfig

import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.AddCustomLiftUseCase
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class CreateLiftViewModelTest {

    private val fakeLiftRepository = FakeLiftRepository()
    private lateinit var subject: CreateLiftViewModel

    @BeforeEach
    fun setup() {
        fakeLiftRepository.seedLifts(emptyList())
        subject =
            CreateLiftViewModel(
                addCustomLift = AddCustomLiftUseCase(fakeLiftRepository, FakeLoggingPort()),
            )
    }

    @Test
    @DisplayName("Given empty draft when observed then Confirm is disabled")
    fun confirmDisabledOnEmptyDraft() = runTest {
        subject.uiState.test {
            val state = awaitItem()
            assertFalse(state.canConfirm)
            assertEquals("", state.name)
            assertTrue(state.details.canEdit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given name primary and patterns when set then Confirm is enabled")
    fun confirmEnabledWhenValid() = runTest {
        subject.uiState.test {
            awaitItem()

            subject.onNameChange("Landmine Press")
            subject.onAddMuscle(MuscleGroup.Chest)
            subject.onOpenDetailEditor(LiftDetailEditField.MovementPatterns)
            subject.onToggleDraftMovementPattern(MovementPattern.HorizontalPress.name)
            subject.onConfirmMovementPatterns()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertTrue(state.canConfirm)
            assertEquals("Landmine Press", state.name)
            assertEquals(listOf(MuscleGroup.Chest), state.details.primaryMuscles.map { it.muscle })
            assertTrue(state.details.movementPatternLabels.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given valid draft when confirmed then lift is persisted and navigate-back emits")
    fun confirmPersistsAndNavigatesBack() = runTest {
        subject.onNameChange("Cable Fly")
        subject.onAddMuscle(MuscleGroup.Chest)
        subject.onOpenDetailEditor(LiftDetailEditField.MovementPatterns)
        subject.onToggleDraftMovementPattern(MovementPattern.ShoulderAdduction.name)
        subject.onConfirmMovementPatterns()
        subject.onSelectLengthBias(MuscleLengthBias.Lengthened.name)
        subject.onSelectKinematic(KinematicProfile.Isolation.name)
        subject.onSelectLoadType(LoadType.Weighted.name)
        subject.onOpenDetailEditor(LiftDetailEditField.Equipment)
        subject.onEquipmentDraftChange("cable")
        subject.onSaveEquipment()
        advanceUntilIdle()

        subject.events.test {
            subject.onConfirm()
            advanceUntilIdle()

            assertEquals(CreateLiftEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val lift = fakeLiftRepository.currentLifts.single()
        assertEquals("Cable Fly", lift.name)
        assertTrue(lift.isCustom)
        assertTrue(lift.enabled)
        assertEquals(setOf(MuscleGroup.Chest), lift.primaryMuscles)
        assertEquals(setOf(MovementPattern.ShoulderAdduction), lift.movementPatterns)
        assertEquals(MuscleLengthBias.Lengthened, lift.muscleLengthBias)
        assertEquals("cable", lift.equipment)
    }

    @Test
    @DisplayName("Given incomplete draft when confirm tapped then no lift is written")
    fun confirmNoOpWhenInvalid() = runTest {
        subject.onNameChange("Incomplete")
        subject.onConfirm()
        advanceUntilIdle()

        assertTrue(fakeLiftRepository.currentLifts.isEmpty())
    }
}
