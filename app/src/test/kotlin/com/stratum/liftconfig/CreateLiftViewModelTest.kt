package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.application.lift.AddCustomLiftUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.application.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.EquipmentPickResult
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
                    id = SeedEquipmentIds.Default,
                    name = "Default",
                    incrementTiers =
                        listOf(
                            IncrementTier(
                                Weight(0.0, Weight.WeightUnit.Lb),
                                null,
                                Weight(2.5, Weight.WeightUnit.Lb),
                            ),
                        ),
                ),
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
                SeedEquipmentIds.Default to "Default",
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
            assertNull(state.details.categoryLabel)
            assertEquals("Default", state.details.equipment)
            assertEquals("Weighted", state.details.loadTypeLabel)
            assertTrue(state.details.showLoadType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("FPR-S5-003 create-lift draft defaults to Low and 120 rest")
    fun fprS5003_createDraftLowRest120() = runTest {
        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            // New contract: fatigue label Low + restDraft 120
            assertEquals("Low", state.details.fatigueLabel)
            assertEquals(120, state.details.restDraftSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("FPR-S5-004 committing a major compound pattern re-infers Medium")
    fun fprS5004_majorPatternInfersMedium() = runTest {
        subject.onNameChange("Machine Chest Press")
        subject.onOpenDetailEditor(LiftDetailEditField.MovementPatterns)
        subject.onToggleDraftMovementPattern(MovementPattern.HorizontalPress.name)
        subject.onConfirmMovementPatterns()
        advanceUntilIdle()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("Medium", state.details.fatigueLabel)
            assertEquals(120, state.details.restDraftSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("FPR-S5-005 free-weight axial name and squat pattern re-infer High and 180 rest")
    fun fprS5005_axialInfersHighRest180() = runTest {
        subject.onNameChange("Barbell Back Squat")
        subject.onOpenDetailEditor(LiftDetailEditField.MovementPatterns)
        subject.onToggleDraftMovementPattern(MovementPattern.SquatPattern.name)
        subject.onConfirmMovementPatterns()
        // Rest draft should track resolver after re-infer (Implementation).
        subject.onOpenDetailEditor(LiftDetailEditField.DefaultRest)
        advanceUntilIdle()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("High", state.details.fatigueLabel)
            assertEquals(180, state.details.restDraftSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    @DisplayName("Given equipment bodyweight action when selected then draft keeps bodyweight id")
    fun bodyweightEquipmentAction() = runTest {
        subject.onBeginChooseEquipment()
        equipmentPickResult.publish(EquipmentId("cable"))
        advanceUntilIdle()
        subject.onAssignBodyweightEquipment()
        advanceUntilIdle()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("Bodyweight only", state.details.equipment)
            assertEquals("Bodyweight", state.details.loadTypeLabel)
            assertFalse(state.details.showLoadType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given bodyweight then cable pick when observed then load type returns to Weighted")
    fun leavingBodyweightRestoresWeightedLoadType() = runTest {
        subject.onAssignBodyweightEquipment()
        advanceUntilIdle()
        subject.onBeginChooseEquipment()
        equipmentPickResult.publish(EquipmentId("cable"))
        advanceUntilIdle()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("Cables", state.details.equipment)
            assertEquals("Weighted", state.details.loadTypeLabel)
            assertTrue(state.details.showLoadType)
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
        subject.onSelectFatigue(FatigueProfile.Low.name)
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
    @DisplayName("Given valid draft with default equipment when confirmed then default id is persisted")
    fun confirmPersistsDefaultEquipment() = runTest {
        subject.onNameChange("Landmine Press")
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
        assertEquals(SeedEquipmentIds.Default, lift.equipmentId)
        assertEquals(LoadType.Weighted, lift.loadType)
    }

    @Test
    @DisplayName("Given bodyweight equipment when confirmed then LoadType.Bodyweight is persisted")
    fun confirmPersistsBodyweightLoadType() = runTest {
        subject.onNameChange("Push-Up")
        subject.onAddMuscle(MuscleGroup.Chest)
        subject.onOpenDetailEditor(LiftDetailEditField.MovementPatterns)
        subject.onToggleDraftMovementPattern(MovementPattern.HorizontalPress.name)
        subject.onConfirmMovementPatterns()
        subject.onAssignBodyweightEquipment()
        advanceUntilIdle()

        subject.events.test {
            subject.onConfirm()
            advanceUntilIdle()
            assertEquals(CreateLiftEvent.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val lift = fakeLiftRepository.currentLifts.single()
        assertEquals(SeedEquipmentIds.Bodyweight, lift.equipmentId)
        assertEquals(LoadType.Bodyweight, lift.loadType)
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
