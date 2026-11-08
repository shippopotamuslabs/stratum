package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.app.DiagnosticDrain
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.lift.ObserveLiftDetailStateUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.lift.SetLiftDefaultRestUseCase
import com.stratum.application.lift.SetLiftEquipmentUseCase
import com.stratum.application.lift.UpdateCustomLiftDetailsUseCase
import com.stratum.application.lift.UpdateLiftMusclesUseCase
import com.stratum.application.training.GetLiftHistoryUseCase
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.EquipmentPickResult
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LiftInformationViewModelTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakePrefs = FakeWeightDisplayPreferencesRepository()
    private val fakeTrainingLog = FakeTrainingLogRepository()
    private val loggingPort = FakeLoggingPort()
    private val equipmentPickResult = EquipmentPickResult()
    private lateinit var subject: LiftInformationViewModel

    @BeforeEach
    fun setup() {
        fakeLiftRepository.seedLifts(
            listOf(
                lift("lat-pulldown", "Lat pulldown", isCustom = false),
                lift("custom-row", "Custom row", isCustom = true),
            ),
        )
        fakePrefs.seed(WeightDisplayPreferences())
        loggingPort.errors.clear()
        subject = buildViewModel()
    }

    private fun buildViewModel(
        trainingLog: FakeTrainingLogRepository = fakeTrainingLog,
    ): LiftInformationViewModel =
        LiftInformationViewModel(
            observeLiftDetail =
                ObserveLiftDetailStateUseCase(
                    ObserveLiftCatalogUseCase(fakeLiftRepository),
                    ObserveWeightDisplayPreferencesUseCase(fakePrefs),
                ),
            updateLiftMuscles = UpdateLiftMusclesUseCase(fakeLiftRepository),
            updateCustomLiftDetails = UpdateCustomLiftDetailsUseCase(fakeLiftRepository),
            setLiftDefaultRest = SetLiftDefaultRestUseCase(fakeLiftRepository),
            setLiftEquipment =
                SetLiftEquipmentUseCase(fakeLiftRepository, fakeEquipmentRepository),
            getLiftHistory =
                GetLiftHistoryUseCase(
                    trainingLogRepository = trainingLog,
                    programRepository = FakeProgramRepository(),
                ),
            diagnosticDrain = DiagnosticDrain(loggingPort),
            equipmentPickResult = equipmentPickResult,
        )

    @Test
    @DisplayName("Given seeded lift when initialized then uiState carries lift identity")
    fun initializeResolvesLift() = runTest {
        subject.initialize("lat-pulldown")
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItem()
            assertEquals("lat-pulldown", state.liftId)
            assertEquals("Lat pulldown", state.liftName)
            assertFalse(state.isCustom)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given prefs unit when initialized then history weight text uses that unit")
    fun prefsUnitFlowsIntoHistory() = runTest {
        val at = Instant.parse("2026-02-01T10:00:00Z")
        val sessionId = TrainingSessionId("s-1")
        val trainingLog =
            FakeTrainingLogRepository(
                initialSessions = listOf(session(sessionId, at)),
                initialSets = listOf(set("set-1", sessionId, at)),
            )
        fakePrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))
        subject = buildViewModel(trainingLog)
        subject.initialize("lat-pulldown")
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.historySessions.size)
            assertTrue(state.historySessions.single().bestSetText.contains("kg"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given tab selection when picked then uiState reflects the tab")
    fun tabSelectionOverlay() = runTest {
        subject.initialize("lat-pulldown")
        advanceUntilIdle()
        subject.onTabSelected(LiftInfoTab.History)
        advanceUntilIdle()

        subject.uiState.test {
            assertEquals(LiftInfoTab.History, awaitItem().selectedTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given history when session toggled then expanded flag flips")
    fun historyExpandOverlay() = runTest {
        val at = Instant.parse("2026-02-01T10:00:00Z")
        val sessionId = TrainingSessionId("s-1")
        val trainingLog =
            FakeTrainingLogRepository(
                initialSessions = listOf(session(sessionId, at)),
                initialSets = listOf(set("set-1", sessionId, at)),
            )
        subject = buildViewModel(trainingLog)
        subject.initialize("lat-pulldown")
        advanceUntilIdle()
        subject.onSessionExpandedToggled("s-1")
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.historySessions.size)
            assertTrue(state.historySessions.single().expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given custom lift when sheet opened then add-muscle sheet shows")
    fun addMuscleSheetOverlay() = runTest {
        subject.initialize("custom-row")
        advanceUntilIdle()

        subject.uiState.test {
            // Drain to the loaded state: the sheet guard reads uiState.value,
            // so the pipeline must be collecting before opening the sheet.
            var state = awaitItem()
            while (state.liftId != "custom-row") {
                state = awaitItem()
            }
            subject.onShowAddMuscleSheet(true)
            assertTrue(awaitItem().details.showAddMuscleSheet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given failed save when message shown then userMessageShown drains it")
    fun userMessageDrain() = runTest {
        subject.initialize("missing-lift")
        advanceUntilIdle()
        subject.onSaveDefaultRest()
        advanceUntilIdle()

        subject.userMessage.test {
            assertNotNull(awaitItem())
            subject.userMessageShown()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun lift(id: String, name: String, isCustom: Boolean): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = openFive,
            presetTags = emptySet(),
            equipmentId = cableId,
            equipmentName = "Cables",
            enabled = true,
            isCustom = isCustom,
        )

    private fun session(id: TrainingSessionId, at: Instant): TrainingSession =
        TrainingSession(
            id = id,
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = at,
            endedAt = at,
            sourceApp = null,
        )

    private fun set(id: String, sessionId: TrainingSessionId, at: Instant): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Lat pulldown",
            liftId = LiftId("lat-pulldown"),
            setIndex = 0,
            weight = Weight(100.0, lb),
            reps = 8,
            rir = 2,
            completedAt = at,
            liftContext = LoggedLiftContext(liftName = "Lat pulldown"),
            isPersonalRecord = false,
            estimatedOneRm = null,
        )
}
