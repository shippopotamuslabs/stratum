package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.AddCustomLiftUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
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
    private val fakeEquipmentRepository =
        FakeEquipmentRepository(
            listOf(
                Equipment(
                    id = EquipmentId("bodyweight"),
                    name = "Bodyweight",
                    incrementTiers = emptyList(),
                ),
                Equipment(
                    id = EquipmentId("cable"),
                    name = "Cables",
                    incrementTiers =
                        listOf(
                            IncrementTier(
                                Weight(0.0, Weight.WeightUnit.Lb),
                                null,
                                Weight(5.0, Weight.WeightUnit.Lb),
                            ),
                        ),
                ),
            ),
        )
    private val equipmentPickResult = EquipmentPickResult()
    private lateinit var subject: CreateLiftViewModel

    @BeforeEach
    fun setup() {
        fakeLiftRepository.seedLifts(emptyList())
        fakeLiftRepository.equipmentNamesById =
            mapOf(
                SeedEquipmentIds.Bodyweight to "Bodyweight",
                EquipmentId("cable") to "Cables",
            )
        val logging = FakeLoggingPort()
        subject =
            CreateLiftViewModel(
                addCustomLift = AddCustomLiftUseCase(fakeLiftRepository, logging),
                observeEquipmentCatalog = ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
                equipmentPickResult = equipmentPickResult,
            )
    }

    @Test
    @DisplayName("Given empty draft when observed then Confirm is disabled")
    fun confirmDisabledOnEmptyDraft() = runTest {
        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.canConfirm)
            assertEquals("", state.name)
            assertTrue(state.details.canEdit)
            assertFalse(state.details.showCategory)
            assertEquals("Bodyweight", state.details.equipment)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given equipment bodyweight action when selected then draft keeps bodyweight id")
    fun bodyweightEquipmentAction() = runTest {
        subject.onBeginChooseEquipment()
        equipmentPickResult.publish(EquipmentId("cable"))
        advanceUntilIdle()
        subject.onClearEquipment()
        advanceUntilIdle()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("Bodyweight", state.details.equipment)
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
        subject.onBeginChooseEquipment()
        equipmentPickResult.publish(EquipmentId("cable"))
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
        assertEquals(EquipmentId("cable"), lift.equipmentId)
        assertEquals("Cables", lift.equipmentName)
    }

    @Test
    @DisplayName("Given valid draft with default equipment when confirmed then bodyweight id is persisted")
    fun confirmPersistsDefaultBodyweightEquipment() = runTest {
        subject.onNameChange("Push-Up")
        subject.onAddMuscle(MuscleGroup.Chest)
        subject.onOpenDetailEditor(LiftDetailEditField.MovementPatterns)
        subject.onToggleDraftMovementPattern(MovementPattern.HorizontalPress.name)
        subject.onConfirmMovementPatterns()
        advanceUntilIdle()

        subject.events.test {
            subject.onConfirm()
            advanceUntilIdle()
            assertEquals(CreateLiftEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val lift = fakeLiftRepository.currentLifts.single()
        assertEquals(SeedEquipmentIds.Bodyweight, lift.equipmentId)
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
